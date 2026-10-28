package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.debug.RtsDebugServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitPathClientboundPacket;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig;
import com.solegendary.reignofnether.unit.pathfinding.RtsPathfinder;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class MoveToTargetBlockGoal extends Goal {

    protected final Mob mob;
    @Nullable protected BlockPos moveTarget = null;
    protected boolean persistent; // will keep trying to move back to the target if moved externally
    protected int moveReachRange = 0; // how far away from the target block to stop moving (manhattan distance)

    protected final int RECALC_COOLDOWN_MAX = 20;
    protected static final int RECALC_COOLDOWN_CAP = 200; // ~10s cap for exponential backoff on stuck units
    protected int currentRecalcCooldown = RECALC_COOLDOWN_MAX;
    protected void backoffRecalcCooldown() {
        currentRecalcCooldown = Math.min(currentRecalcCooldown * 2, RECALC_COOLDOWN_CAP);
    }
    protected void resetRecalcBackoff() { currentRecalcCooldown = RECALC_COOLDOWN_MAX; stuckRepaths = 0; }

    // A plain move order to a spot the unit can't reach used to repath forever (backing off to every 10 s), so the
    // unit never went idle - no idle-worker alert, and a Seedshaper sent to plant on an unreachable spot just stood
    // there. Like BAR, the unit now walks as close as it can and stops: after this many repaths in a row that make no
    // progress (repathVerdict) or find no path, a plain move goal drops its target: ~12 s after it stops, as the
    // goal is checked every other tick and the cooldowns between those repaths are 40 and 80 checks.
    public static final int GIVE_UP_AFTER_STUCK_REPATHS = 3;
    protected int stuckRepaths = 0;
    // The target most recently dropped as unreachable (for errands such as Plant Thicket to tell "gave up" apart
    // from "was told to stop"). Cleared by the next order.
    @Nullable protected BlockPos lastUnreachableTarget = null;
    @Nullable public BlockPos getLastUnreachableTarget() { return lastUnreachableTarget; }

    // Only the units' plain move goal gives up: the subclasses (build, gather, attack-building, garrison...) are
    // persistent by design or manage their own targets, and must keep their current behaviour.
    protected boolean givesUpWhenUnreachable() {
        return !persistent && getClass() == MoveToTargetBlockGoal.class;
    }

    // How a repath reads (RepathProgress.judge): progress only if the unit itself got REPATH_WALKED_MIN closer to its
    // target since the repath before; a path ending REPATH_PROGRESS_MIN closer that the unit has not walked yet holds
    // the count; anything else is stuck. "A different final node" was not enough: for a target in unloaded land the
    // path aims at RtsPathfinder.lastLoadedToward from the unit's own block, so a unit pressed against a wall that
    // drifted one block sideways got a path ending one block over, every repath read as progress, and it stood with
    // its order forever (CI, a_move_into_unloaded_chunks..., runs 40572488789, 40577575656). Nor was "a closer end"
    // or "walked 2 blocks" (any way): a partial path out of a closed pen ended outside it, the unit walked off along
    // the wall trying to follow it, and both reset the give-up (stress 40748556949).
    public static final double REPATH_PROGRESS_MIN = RepathProgress.END_CLOSER_MIN;
    public static final double REPATH_WALKED_MIN = RepathProgress.ADVANCED_MIN;
    // where the unit stood at the last repath (or the order) and its horizontal distance to its target there, the same
    // for the repath before (restored when the pool answers busy: that repath computed nothing, so the walk it was owed
    // carries over to the retry), and how much closer / how far the unit got between them, kept for onPathReady while
    // an async repath is in flight
    private double repathFromX = Double.NaN, repathFromZ = Double.NaN, repathFromDist = Double.NaN;
    private double repathPrevFromX = Double.NaN, repathPrevFromZ = Double.NaN, repathPrevFromDist = Double.NaN;
    private double repathAdvanced = 0, repathWalked = 0;
    // the end node of the last path this goal handed to the navigation (cleared by a new order or a stop)
    @Nullable private BlockPos lastPathEnd = null;
    // the repath in flight was forceRepath's (an external rescue): it may count as stuck but never gives the order up
    private boolean pendingRepathForced = false;

    // Before a repath: note where the unit stands now, and how much closer to its target / how far it got since the last.
    private void markRepathFrom() {
        double x = this.mob.getX(), z = this.mob.getZ();
        double now = moveTarget == null ? Double.NaN : horizontalDist(x, z, moveTarget);
        repathAdvanced = Double.isNaN(repathFromDist) || Double.isNaN(now) ? Double.POSITIVE_INFINITY : repathFromDist - now;
        repathWalked = Double.isNaN(repathFromX) ? Double.POSITIVE_INFINITY
            : Math.sqrt((x - repathFromX) * (x - repathFromX) + (z - repathFromZ) * (z - repathFromZ));
        repathPrevFromX = repathFromX;
        repathPrevFromZ = repathFromZ;
        repathPrevFromDist = repathFromDist;
        repathFromX = x;
        repathFromZ = z;
        repathFromDist = now;
    }

    // The verdict on a repath from a path ending at oldEnd to one ending at newEnd. Only the goal that gives up judges
    // by distance: the subclasses' targets (garrison, return resources, call to arms) follow the unit's own position,
    // so for them a changed end node is still progress, as before.
    private RepathProgress.Verdict repathVerdict(@Nullable BlockPos oldEnd, BlockPos newEnd) {
        if (!givesUpWhenUnreachable())
            return newEnd.equals(oldEnd) ? RepathProgress.Verdict.STUCK : RepathProgress.Verdict.PROGRESS;
        if (moveTarget == null)
            return RepathProgress.Verdict.PROGRESS;
        return RepathProgress.judge(oldEnd == null ? Double.NaN : horizontalDist(oldEnd, moveTarget),
            horizontalDist(newEnd, moveTarget), repathAdvanced, repathWalked);
    }

    private void applyVerdict(RepathProgress.Verdict v, boolean forced) {
        if (v == RepathProgress.Verdict.STUCK)
            onStuckRepath(forced);
        else if (v == RepathProgress.Verdict.PROGRESS)
            resetRecalcBackoff();
    }

    private static double horizontalDist(double x, double z, BlockPos b) {
        double dx = x - (b.getX() + 0.5), dz = z - (b.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // Called where a repath made no progress (no closer final node, or no path at all). A forced repath (a rescue)
    // counts, but stops one short of the give-up: a unit just rescued out of a block must get its own repath to show
    // whether it can move now, or one at count 2 gave its order up the moment it was freed.
    private void onStuckRepath(boolean forced) {
        backoffRecalcCooldown();
        stuckRepaths++;
        if (forced && stuckRepaths >= GIVE_UP_AFTER_STUCK_REPATHS)
            stuckRepaths = GIVE_UP_AFTER_STUCK_REPATHS - 1;
        if (stuckRepaths >= GIVE_UP_AFTER_STUCK_REPATHS && givesUpWhenUnreachable() && moveTarget != null
                && !this.mob.level().isClientSide() && !(this.mob instanceof Unit u && u.getHoldPosition())) {
            lastUnreachableTarget = moveTarget;
            stuckRepaths = 0;
            moveTarget = null;
            this.mob.getNavigation().stop();
        }
    }

    /**
     * Repath now from wherever the unit is, without any backoff - for when it was moved externally (UnitUnstick
     * teleporting it out of a block), so it doesn't sit out up to 10 s of stuck-backoff earned while trapped.
     * The backoff only: the stuck count is kept, and this repath is judged like any other (judgedRepath), except that
     * it never gives the order up itself (onStuckRepath). Resetting the count here, and a bare start() whose path was
     * never judged, meant a unit UnitUnstick rescued in place at a wall (a move into unloaded land) never reached its
     * give-up and stood with its order forever (CI stress 40675281944, 40720999152).
     */
    public void forceRepath() {
        currentRecalcCooldown = RECALC_COOLDOWN_MAX;
        recalcCooldown = 0;
        if (moveTarget != null && isRunning && !this.mob.level().isClientSide())
            judgedRepath(true);
    }
    public boolean isInBackoff() { return currentRecalcCooldown > RECALC_COOLDOWN_MAX && moveTarget != null; }
    protected int recalcCooldown = 0; // limit start() used by canContinueToUse
    protected boolean pathPending = false; // true while an async RTS path request is in flight
    // Mirrors the goal's active state (set in start(), cleared in stop()). The GoalSelector calls
    // start() itself on the idle -> active transition, so setMoveTarget only needs to fire start()
    // manually while already running - otherwise an idle unit would path twice for one order.
    protected boolean isRunning = false;
    // Bumped on every new request/order/stop. The async callback captures it at request time and drops its
    // result if it no longer matches, so a slow stale path can't overwrite a newer order.
    protected int pathRequestSeq = 0;
    // Set when the in-flight async request is a repath (not a fresh order). The async path isn't ready when
    // start() fires, so the stuck-backoff decision (same final node = still stuck -> grow; changed = progress
    // -> reset) is deferred to onPathReady, which compares the new path against repathFromFinalNode.
    protected boolean pendingRepath = false;
    @Nullable protected BlockPos repathFromFinalNode = null;

    // ---- progress, for "is a re-click of this spot redundant?" (UnitActionItem.isRedundantMove) ----
    // A re-click of the spot a unit is already heading to used to be skipped outright, so a unit stalled in a path
    // loop (or standing on a no-path retry) could not be rescued by clicking again - "units sometimes don't listen".
    // Now a re-click is skipped only while the unit is really getting there: a path request in flight that was
    // issued in the last PROGRESS_WINDOW ticks, or a live path along which it got closer to the target than ever
    // before within the last PROGRESS_WINDOW ticks. Sampled every PROGRESS_SAMPLE ticks from Unit.tick.
    public static final int PROGRESS_SAMPLE = 10;
    public static final int PROGRESS_WINDOW = 25;
    private long lastRequestTime = Long.MIN_VALUE / 2;   // game time of the last path request
    private long lastProgressTime = Long.MIN_VALUE / 2;  // game time the unit last got closer than ever to moveTarget
    private double bestDistSq = Double.MAX_VALUE;        // closest it has been to moveTarget (horizontal, squared)
    // headway, for the contact rule (ticksWithoutProgress): the last time it got HEADWAY blocks closer than at the
    // previous mark. Stricter than the 0.3-block progress above, which a unit squeezing through an enemy army inches
    // out every few samples (CI, CombatEngageGameTests: 43 of 150 movers stood in contact with no target at 15 s)
    public static final double HEADWAY = 1.0;
    private double headwayMarkDist = Double.MAX_VALUE;
    private long lastHeadwayTime = Long.MIN_VALUE / 2;

    // Async requests answered "busy" (pool/queue saturated) in a row. Each busy answer used to retry in 20 ticks with
    // the unit stood still - forever while the pool stayed saturated. After BUSY_FALLBACK_AFTER in a row, the unit
    // takes one vanilla path instead (at most SYNC_FALLBACKS_PER_TICK units per tick, so a saturated pool cannot turn
    // into a main-thread freeze).
    public static final int BUSY_FALLBACK_AFTER = 3;
    public static final int SYNC_FALLBACKS_PER_TICK = 4;
    private static long syncFallbackTick = Long.MIN_VALUE;
    private static int syncFallbacksThisTick = 0;
    private int busyStreak = 0;

    /** Diagnostics for tests: an async path request is in flight / the goal is active. */
    public boolean isPathPending() { return pathPending; }
    public boolean isGoalRunning() { return isRunning; }

    /** The game time of this goal's last path request (tests: did an order re-path the unit?). */
    public long getLastRequestTime() { return lastRequestTime; }

    /** Server side, every tick from Unit.tick; acts every {@link #PROGRESS_SAMPLE} ticks. */
    public void sampleProgress() {
        if (moveTarget == null || (this.mob.tickCount + this.mob.getId()) % PROGRESS_SAMPLE != 0)
            return;
        double dx = moveTarget.getX() + 0.5 - this.mob.getX(), dz = moveTarget.getZ() + 0.5 - this.mob.getZ();
        double d = dx * dx + dz * dz;
        // "closer than ever" by at least ~0.3 blocks: an oscillating unit (the path loop) never counts as progress
        double best = Math.sqrt(bestDistSq);
        if (bestDistSq == Double.MAX_VALUE || Math.sqrt(d) < best - 0.3) {
            bestDistSq = d;
            lastProgressTime = this.mob.level().getGameTime();
        }
        double dist = Math.sqrt(d);
        if (headwayMarkDist == Double.MAX_VALUE || dist < headwayMarkDist - HEADWAY) {
            headwayMarkDist = dist;
            lastHeadwayTime = this.mob.level().getGameTime();
        }
    }

    /**
     * Is this unit really on its way to its move target right now? A request in flight issued in the last
     * {@link #PROGRESS_WINDOW} ticks, or a live path along which it got closer within that window.
     */
    public boolean isMakingProgress() {
        if (moveTarget == null)
            return false;
        long now = this.mob.level().getGameTime();
        if (pathPending)
            return now - lastRequestTime <= PROGRESS_WINDOW;
        if (this.mob.getNavigation().isDone())
            return false;
        return now - lastProgressTime <= PROGRESS_WINDOW;
    }

    /**
     * How long this unit has held a move target without real headway: ticks since it last got {@link #HEADWAY} blocks
     * closer (or since the order, or since its last path request while one is in flight). 0 with no move target.
     * AttackerUnit's contact rule reads it: a unit blocked on a move order fights what it touches.
     */
    public long ticksWithoutProgress() {
        if (moveTarget == null)
            return 0;
        long since = pathPending ? Math.max(lastHeadwayTime, lastRequestTime) : lastHeadwayTime;
        return Math.max(0, this.mob.level().getGameTime() - since);
    }

    public MoveToTargetBlockGoal(Mob mob, boolean persistent, int reachRange) {
        this.mob = mob;
        this.persistent = persistent;
        this.moveReachRange = reachRange;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean isAtDestination() {
        if (moveTarget == null)
            return true;
        return mob.getNavigation().isDone();
    }

    public double getMinDistToRecalculateSqr() {
        // Floor the recalc radius at the separation settle distance, else there's a band where a unit settles
        // via separation but still triggers a recalc every tick - that mismatch is what makes arrived units dance.
        double dist = Math.max(Math.sqrt(PathfinderConfig.ARRIVAL_SETTLE_SQ), moveReachRange);
        return dist * dist;
    }

    // Whether this unit routes through the async RTS grid pathfinder. Overridden to false for units the grid
    // path doesn't suit (eg. jump-based slimes), keeping them on vanilla pathfinding.
    protected boolean useRtsPathfinding() {
        return UnitServerEvents.rtsPathfinding;
    }

    public boolean canUse() {
        if (this.mob instanceof Unit unit && unit.isFlyingUnit())
            return false;
        return moveTarget != null;
    }

    public boolean canContinueToUse() {
        // Keep the goal active (without recalculating) while an async RTS path is being computed.
        if (pathPending)
            return moveTarget != null;
        if (recalcCooldown > 0) {
            recalcCooldown -= 1;
            return true;
        }
        // PathNavigation has a max length, so once the unit finishes its current path but still isn't at the
        // target, restart to continue the journey (RTS delivers long routes in segments) or retry.
        if (this.mob.getNavigation().isDone() && moveTarget != null &&
            this.mob.getOnPos().distSqr(moveTarget) > getMinDistToRecalculateSqr()) {
            // start() is expensive and repeats every tick on a stuck mob (eg. targeting over water), so it must
            // be throttled. The synchronous vanilla path has its new final node ready, so decide the backoff
            // and arm the cooldown gate right here. The async RTS path isn't ready yet (start() just fired the
            // request), so flag it and let onPathReady do the same once the new path is in.
            judgedRepath(false);
            return true;
        }
        else if (moveTarget == null)
            return false;
        else if (this.mob.getNavigation().isDone()) {
            if (!persistent && !((Unit) this.mob).getHoldPosition()) {
                moveTarget = null;
            }
            return false;
        }
        return true;
    }

    // A repath whose new path is judged against the old one (repathVerdict): no progress or no path grows the
    // backoff and counts toward the give-up. The vanilla path is judged here; the async RTS one in onPathReady.
    private void judgedRepath(boolean forced) {
        // a path stopped from outside (StuckNudge, UnitUnstick) leaves no end to compare: the plain move goal
        // compares against the last end it was given, else every such stop read as progress and reset the give-up
        BlockPos oldFinalNode = getFinalNodePos();
        if (oldFinalNode == null && givesUpWhenUnreachable())
            oldFinalNode = lastPathEnd;
        markRepathFrom();
        this.start();
        // start() is expensive and repeats every tick on a stuck mob (eg. targeting over water). Only the
        // synchronous vanilla path has a final node ready to compare for the backoff; the async RTS path
        // isn't ready yet (start() just fired the request), so it simply repaths next time it's done.
        if (!pathPending) {
            BlockPos newFinalNode = getFinalNodePos();
            // No path at all is "still stuck" too. Before, two nulls in a row (a target in an unloaded chunk:
            // RtsPathfinder answers null at once, inside start(), so this branch runs; or vanilla finding
            // nothing) read as progress and reset the count, so the unit retried every 20 ticks forever,
            // standing still and counted busy. Now it backs off and gives up like any other stuck repath.
            // (A unit in the air - a jump, a knockback - gets null from vanilla's canUpdatePath for that moment:
            // that is neither progress nor stuck, it just retries.)
            boolean airborne = !this.mob.onGround() && !this.mob.isInWater() && !this.mob.isInLava() && !this.mob.isPassenger();
            if (newFinalNode != null)
                applyVerdict(repathVerdict(oldFinalNode, newFinalNode), forced);
            else if (!airborne)
                onStuckRepath(forced);
            recalcCooldown = currentRecalcCooldown;
        } else {
            pendingRepath = true;
            pendingRepathForced = forced;
            repathFromFinalNode = oldFinalNode;
        }
    }

    public void start() {
        isRunning = true;
        lastRequestTime = this.mob.level().getGameTime();
        // Cleared on every start(); canContinueToUse re-sets it after this call when it fires an async repath,
        // so a fresh order (started directly here) never inherits a previous repath's deferred backoff.
        pendingRepath = false;
        repathFromFinalNode = null;
        if (moveTarget == null) {
            this.mob.getNavigation().stop();
            return;
        }
        if (!(this.mob instanceof Unit u)) {
            this.mob.getNavigation().stop();
            return;
        }

        // When the rtsPathfinding gamerule is on, route through the async grid A* pathfinder.
        if (useRtsPathfinding()) {
            this.mob.setMaxUpStep(1.15f);
            if (this.mob.getNavigation() instanceof GroundPathNavigation gpn) gpn.setCanFloat(true);
            this.mob.getNavigation().stop();
            MobilityClass mobility = MobilityClass.of(u);
            pathPending = true;
            final int seq = ++pathRequestSeq;
            // Validity check run on the main thread before this request warms its chunks: drop it if the
            // mob died or a newer order bumped pathRequestSeq past this seq, so superseded requests don't
            // burn chunk-build budget (pathRequestSeq is only read/written on the main thread).
            final BooleanSupplier valid = () -> this.mob.isAlive() && this.pathRequestSeq == seq;
            // Count the async request too, so the F7 "Paths/s" meter reflects RTS pathfinding load (the
            // vanilla branch below already counts createPath calls); else the meter reads ~0 under
            // rtsPathfinding while async repaths flood.
            if (!this.mob.level().isClientSide()) RtsDebugServerEvents.debugPathCalcsThisSecond += 1;
            RtsPathfinder.requestPath(this.mob, moveTarget, moveReachRange, mobility, valid,
                    (path, busy) -> onPathReady(path, busy, seq));
            return;
        }

        // Vanilla pathfinding: one createPath call. If the path ends up suboptimal, the backoff in
        // canContinueToUse handles retries / give-up.
        Path path = mob.getNavigation().createPath(moveTarget.getX(), moveTarget.getY(), moveTarget.getZ(), moveReachRange);
        if (!this.mob.level().isClientSide()) RtsDebugServerEvents.debugPathCalcsThisSecond += 1;
        /*
        if (path == null) {
            AttributeInstance ai = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (ai != null && ai.getBaseValue() == FOLLOW_RANGE_IMPROVED) {
                // Fallback: long-range search bailed (eg. hit maxVisitedNodes). Retry with the short
                // range — vanilla A* may give up sooner and return a useful partial path.
                ai.setBaseValue(FOLLOW_RANGE);
                path = mob.getNavigation().createPath(moveTarget.getX(), moveTarget.getY(), moveTarget.getZ(), moveReachRange);
                if (!this.mob.level().isClientSide()) RtsDebugServerEvents.debugPathCalcsThisSecond += 1;
                ai.setBaseValue(FOLLOW_RANGE_IMPROVED);
            }
        }
         */
        float speedMod = u.getSpeedModifier();
        if (speedMod < 1.0f) speedMod = (float) Math.sqrt(speedMod);
        this.mob.getNavigation().moveTo(path, speedMod);
        lastPathEnd = getFinalNodePos();
        // Broadcast the path so clients can render it briefly. Server-only — clients
        // that received the packet decide whether to render based on ownership/FOW.
        if (!this.mob.level().isClientSide()) {
            byte type = (path != null && !path.canReach()) ? RtsPathfinder.TYPE_FAILED : RtsPathfinder.TYPE_VANILLA;
            UnitPathClientboundPacket.sendPath(this.mob, path, type);
        }
    }

    // Callback for the async RTS pathfinder. Runs on the server thread once a path is ready.
    protected void onPathReady(@Nullable Path path, boolean busy, int seq) {
        // A newer order/stop superseded this request - drop the result. Leave pathPending alone; the newer
        // request is still in flight and owns it.
        if (seq != pathRequestSeq) return;
        pathPending = false;
        // Request was dropped under load (queue/pool saturated), not computed. Keep the current nav path
        // and retry after a short cooldown - do NOT stop() or back off. Stopping here is exactly what made
        // units shuffle back and forth: they'd stop, re-submit next tick, get dropped again, repeat.
        if (busy) {
            if (pendingRepath) {
                repathFromX = repathPrevFromX;
                repathFromZ = repathPrevFromZ;
                repathFromDist = repathPrevFromDist;
            }
            pendingRepath = false;
            repathFromFinalNode = null;
            recalcCooldown = RECALC_COOLDOWN_MAX;
            if (++busyStreak >= BUSY_FALLBACK_AFTER && moveTarget != null && this.mob instanceof Unit u
                    && !this.mob.level().isClientSide() && takeSyncFallback(this.mob.level().getGameTime())) {
                busyStreak = 0;
                Path fallback = this.mob.getNavigation().createPath(moveTarget.getX(), moveTarget.getY(), moveTarget.getZ(), moveReachRange);
                RtsDebugServerEvents.debugPathCalcsThisSecond += 1;
                float speedMod = u.getSpeedModifier();
                if (speedMod < 1.0f) speedMod = (float) Math.sqrt(speedMod);
                this.mob.getNavigation().moveTo(fallback, speedMod);
                lastPathEnd = getFinalNodePos();
            }
            return;
        }
        busyStreak = 0;
        // The deferred half of the repath throttle (see canContinueToUse): now that the async path is in we
        // can compare its final node to the pre-repath one. No progress (repathVerdict) or no path at all = still
        // stuck, grow the backoff; progress = reset it. Then arm the cooldown gate so the next repath is throttled.
        // wasRepath is false for a fresh order, which must never back off.
        boolean wasRepath = pendingRepath, forced = pendingRepathForced;
        pendingRepath = false;
        if (moveTarget == null) { repathFromFinalNode = null; return; }
        if (!(this.mob instanceof Unit u)) {
            this.mob.getNavigation().stop();
            return;
        }
        if (path == null) {
            this.mob.getNavigation().stop();
            if (wasRepath) {
                onStuckRepath(forced);
                recalcCooldown = currentRecalcCooldown;
            }
            repathFromFinalNode = null;
            return;
        }
        // Follow even a partial (unreachable) path so the unit still makes progress toward the target.
        float speedMod = u.getSpeedModifier();
        if (speedMod < 1.0f) speedMod = (float) Math.sqrt(speedMod);
        this.mob.getNavigation().moveTo(path, speedMod);
        lastPathEnd = getFinalNodePos();
        // The RTS pathfinder is async: node 0 is the unit's position when the request was SUBMITTED, a few
        // ticks ago. By delivery the unit has often drifted off it (crowd/formation separation, momentum),
        // and vanilla navigation always starts following at node 0 - so it would steer the unit BACK to the
        // stale start node before going forward, the "snap back to start" oscillation. Drop the leading nodes
        // already behind the unit by advancing to the node nearest its current position.
        snapPathToMob();
        if (wasRepath) {
            BlockPos newFinalNode = getFinalNodePos();
            // no end node at all: stuck, except for a subclass whose last path had an end (as before)
            if (newFinalNode != null)
                applyVerdict(repathVerdict(repathFromFinalNode, newFinalNode), forced);
            else if (repathFromFinalNode == null || givesUpWhenUnreachable())
                onStuckRepath(forced);
            else
                resetRecalcBackoff();
            recalcCooldown = currentRecalcCooldown;
        }
        repathFromFinalNode = null;
        if (!this.mob.level().isClientSide()) {
            byte type = path.canReach() ? RtsPathfinder.TYPE_ASTAR : RtsPathfinder.TYPE_FAILED;
            UnitPathClientboundPacket.sendPath(this.mob, path, type);
        }
    }

    // One of this tick's SYNC_FALLBACKS_PER_TICK vanilla paths for units the saturated async pool kept answering busy.
    private static boolean takeSyncFallback(long gameTime) {
        if (gameTime != syncFallbackTick) {
            syncFallbackTick = gameTime;
            syncFallbacksThisTick = 0;
        }
        if (syncFallbacksThisTick >= SYNC_FALLBACKS_PER_TICK)
            return false;
        syncFallbacksThisTick++;
        return true;
    }

    // Advance the active nav path's next-node pointer to the node closest to the mob's CURRENT position,
    // trimming leading nodes it has already passed (or drifted away from). Reads the path back from the
    // navigation rather than trusting the passed-in reference, since vanilla moveTo can keep the prior path
    // when the new one is sameAs() it. Only searches forward from the current index, so the unit is never
    // rewound and a cell legitimately revisited later in the route isn't skipped.
    private void snapPathToMob() {
        Path p = this.mob.getNavigation().getPath();
        if (p == null || p.isDone())
            return;
        int best = p.getNextNodeIndex();
        double bestDistSqr = Double.MAX_VALUE;
        for (int i = best; i < p.getNodeCount(); i++) {
            Node n = p.getNode(i);
            double dx = (n.x + 0.5) - this.mob.getX();
            double dy = n.y - this.mob.getY();
            double dz = (n.z + 0.5) - this.mob.getZ();
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = i;
            }
        }
        p.setNextNodeIndex(best);
    }

    public void setMoveTarget(@Nullable BlockPos bp) {
        if (bp != null) {
            MiscUtil.addUnitCheckpoint((Unit) mob, bp, true);
        }
        // Only fire a fresh path on an actual target change
        boolean changed = !Objects.equals(bp, this.moveTarget);
        if (bp != null)
            lastUnreachableTarget = null;
        if (changed) {
            resetRecalcBackoff();
            recalcCooldown = 0;
            busyStreak = 0;
            // a new target starts with a fresh progress record (a grace window: a spam-click right after an order
            // is still redundant while the first path comes in)
            bestDistSq = Double.MAX_VALUE;
            lastProgressTime = this.mob.level().getGameTime();
            // the walk from here toward the target counts toward the first repath
            repathFromX = repathPrevFromX = this.mob.getX();
            repathFromZ = repathPrevFromZ = this.mob.getZ();
            repathFromDist = repathPrevFromDist = bp == null ? Double.NaN : horizontalDist(this.mob.getX(), this.mob.getZ(), bp);
            lastPathEnd = null;
            headwayMarkDist = Double.MAX_VALUE;
            lastHeadwayTime = lastProgressTime;
        }
        this.moveTarget = bp;

        if (changed && !this.mob.level().isClientSide())
            this.start();
    }

    public BlockPos getMoveTarget() {
        return this.moveTarget;
    }

    @Nullable public BlockPos getFinalNodePos() {
        Path path = this.mob.getNavigation().getPath();
        if (path != null && !path.nodes.isEmpty())
            return path.nodes.get(path.nodes.size() - 1).asBlockPos();
        return null;
    }

    @Override
    public void stop() {
        isRunning = false;
        // A goal deactivated with a request still in flight (eg. the attack goal preempting this one via
        // Flag.MOVE) must not have that path delivered later: by then another goal owns the navigation and
        // the stale moveTo would steer the unit back toward this goal's old objective. Bump the seq so the
        // delivery is dropped; moveTarget is kept so a persistent goal resumes via start() with a fresh request.
        if (pathPending) {
            pathRequestSeq++;
            pathPending = false;
            pendingRepath = false;
            repathFromFinalNode = null;
        }
    }

    public void stopMoving() {
        recalcCooldown = 0;
        pathPending = false;
        pendingRepath = false;
        repathFromFinalNode = null;
        lastPathEnd = null;
        pathRequestSeq++; // cancel any in-flight path so a late result can't restart movement after a stop.
        this.moveTarget = null;
        this.mob.getNavigation().stop();
        if (this.mob.isVehicle() && this.mob.getPassengers().get(0) instanceof Unit unit)
            unit.getMoveGoal().stopMoving();
    }
}
