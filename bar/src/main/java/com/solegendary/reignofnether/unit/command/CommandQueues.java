package com.solegendary.reignofnether.unit.command;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.GuardServerEvents;
import com.solegendary.reignofnether.unit.PatrolServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.SelectedTargetGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Every unit's command queue (design/ron_rewrite_plan.md, rewrite 2). Replaces Reign of Nether's two global action
 * lists in UnitServerEvents: a "fast" list run at the end of the overworld tick - every order took effect a tick after
 * it arrived - and a "slow" list of shift-queued orders, scanned from the top every tick for the first item whose unit
 * was idle, so the whole server started ONE queued order per tick (at 8v8, a few hundred units reaching their
 * waypoints together waited seconds for their turn), every per-unit question (depth, de-dup, queue lines) was a scan of
 * everybody's orders, and an idle unit's queued order could jump ahead of the plain order given with it.
 * <p>
 * Modelled on Spring / Recoil's CCommandAI, which BAR runs on:
 * <ul>
 *   <li>a plain order clears the unit's queue and starts at once (Spring's GiveAllowedCommand clears commandQue
 *       without SHIFT_KEY and calls SlowUpdate when the new command is the only one);</li>
 *   <li>Shift appends; on a unit with nothing to do the order starts at once, as above;</li>
 *   <li>Ctrl inserts at the front (CMD_INSERT at position 0): the new order starts and the one it interrupted goes back
 *       to the head of the queue, to resume afterwards;</li>
 *   <li>when the current order is finished the next starts (FinishCommand), every unit in the same tick, but never a
 *       second one for the same unit in the tick an order started (Spring allows one FinishCommand-driven SlowUpdate
 *       per frame "to avoid infinite loops"; here the unit's goals also get one tick to act on an order before it can
 *       be judged done);</li>
 *   <li>an order whose target died or was removed is skipped when it comes up, and the next one starts in the same
 *       pass (Spring finishes such a command at once);</li>
 *   <li>state commands (fire state, autocast) are applied at once and never touch the queue (CommandKind.STATE).</li>
 * </ul>
 * Reign of Nether's goals don't report when an order is done, so "finished" means {@link Unit#isIdle}, looked at once
 * per tick at the end of the overworld tick - after every unit's goals ran - and only for units that have orders
 * waiting. What an order does to a unit is still Reign of Nether's dispatch ({@link UnitActionItem#action}); this
 * class decides when, and for whom.
 * <p>
 * Server thread only (orders from another thread are logged and moved onto it). Queues live as long as their unit is
 * in the level ({@link #forget} on leave, {@link #clearAll} on server stop) and are not saved: the old lists weren't
 * either, and their entity ids would not survive a reload.
 */
public final class CommandQueues {

    private CommandQueues() { }

    /** Orders one unit may have waiting; past it an order is refused for that unit ("Queue full"), BAR-ish. */
    public static final int MAX_QUEUED = 32;

    // With vanilla pathfinding (the rtsPathfinding gamerule off) starting a move runs a synchronous A* on the server
    // thread, so a burst of units finishing their orders together is spread over ticks, like the formation dispatch:
    // at least MIN_STARTS_PER_TICK orders, then as many as fit in START_BUDGET_NANOS. The RTS pathfinder queues its
    // requests off-thread, so with it on every due order starts at once.
    static final int MIN_STARTS_PER_TICK = 10;
    static final long START_BUDGET_NANOS = 4_000_000L;

    private static final Int2ObjectOpenHashMap<CommandQueue> QUEUES = new Int2ObjectOpenHashMap<>();
    // the queues with orders waiting: only these are looked at each tick (a unit merely carrying out an order costs
    // nothing). Entries are only removed by the executor's compaction, so issue() and forget() - which can run in the
    // middle of a pass, from an order the pass started - never shift the list under it
    private static final ArrayList<CommandQueue> LISTED = new ArrayList<>();
    private static int cursor = 0;   // where a budget-limited pass stopped: the next one starts there
    private static final int[] NO_IDS = new int[0];

    // ------------------------------------------------------------------ giving orders

    /**
     * Gives an order to units, the way Spring's CommandAI takes a command ({@link QueueMode}): REPLACE empties each
     * unit's queue and starts the order now; APPEND queues it behind the unit's orders, or starts it now on a unit
     * with nothing to do; INSERT_FRONT starts it now and hands the interrupted order back to the head of the queue.
     * State commands apply at once in every mode and touch no queue.
     * <p>
     * The units starting the order now run it together as one group order, exactly as a plain order always ran - a move
     * spreads them in formation, a one-click ability is cast once - and two things that are not the units' to queue
     * run once, now, whatever the mode: mobs under the control-all-mobs cheat (they have no queue) and a building
     * ability riding on {@code buildingPos}. Units the order may not command (UnitActionItem.mayCommand) are left
     * alone; serverbound orders were already filtered by PacketAuth.
     *
     * @return how many units refused the order because their queue was full
     */
    public static int issue(ServerLevel level, @Nullable String owner, UnitAction action, int targetId, int[] unitIds,
                            BlockPos pos, BlockPos buildingPos, QueueMode mode) {
        if (level == null || action == null || mode == null) {
            ReignOfNether.LOGGER.error("Unit order dropped: level {}, action {}, mode {}", level, action, mode);
            return 0;
        }
        int[] ids = unitIds == null ? NO_IDS : unitIds;
        if (!level.getServer().isSameThread()) {
            // the old global lists were synchronized for this; game state is touched on the server thread only now
            ReignOfNether.LOGGER.error("Unit order {} given off the server thread ({}): moved onto it", action,
                Thread.currentThread().getName());
            final int[] copy = ids.clone();
            TaskSchedulerServerEvents.schedule(0, () -> issue(level, owner, action, targetId, copy, pos, buildingPos, mode));
            return 0;
        }
        BlockPos at = pos == null ? BlockPos.ZERO : pos;
        BlockPos building = buildingPos == null ? BlockPos.ZERO : buildingPos;
        CommandKind kind = CommandKind.of(action);
        if (kind == CommandKind.STATE) {
            new UnitActionItem(owner, action, targetId, ids, at, building).action(level);
            return 0;
        }
        // Stop and Hold put in front of an interrupted order would last one tick before it resumed (Ctrl+S would be a
        // pause): with Ctrl they act as the plain orders they are
        QueueMode queueMode = mode == QueueMode.INSERT_FRONT && (kind == CommandKind.STOP || kind == CommandKind.HOLD)
            ? QueueMode.REPLACE : mode;
        long now = level.getGameTime();
        boolean sandbox = UnitActionItem.isSandboxOwner(level, owner);
        UnitCommand cmd = new UnitCommand(action, owner, targetId, at);

        if (queueMode == QueueMode.REPLACE) {
            for (int id : ids) {
                Entity e = level.getEntity(id);
                if (e instanceof Unit unit && e instanceof LivingEntity le && UnitActionItem.mayCommand(level, owner, unit, sandbox)) {
                    CommandQueue q = obtain(le);
                    q.clearWaiting();
                    q.start(cmd, now);
                }
            }
            new UnitActionItem(owner, action, targetId, ids, at, building).action(level);
            return 0;
        }

        int[] startNow = new int[ids.length];
        int n = 0, full = 0;
        for (int id : ids) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Unit unit) || !(e instanceof LivingEntity le)) {
                if (e != null)
                    startNow[n++] = id;   // a mob under the control-all-mobs cheat: no queue, it obeys now
                continue;
            }
            if (!UnitActionItem.mayCommand(level, owner, unit, sandbox))
                continue;
            CommandQueue q = obtain(le);
            // nothing to do (and no order started this tick, whose goals haven't acted on it yet)
            boolean free = q.currentTick != now && unit.isIdle();
            if (queueMode == QueueMode.APPEND) {
                if (free && q.isEmpty()) {
                    q.start(cmd, now);
                    startNow[n++] = id;
                } else if (q.size() >= MAX_QUEUED) {
                    full++;
                } else {
                    q.addLast(cmd);
                    list(q);
                }
            } else {   // INSERT_FRONT
                if (q.size() >= MAX_QUEUED) {
                    full++;
                    continue;
                }
                UnitCommand interrupted = q.current;
                if (!free && interrupted != null && stillCarriedOut(le, unit, interrupted)) {
                    q.addFirst(interrupted);
                    list(q);
                }
                q.start(cmd, now);
                startNow[n++] = id;
            }
        }
        if (n > 0 || !building.equals(BlockPos.ZERO))
            new UnitActionItem(owner, action, targetId, n == ids.length ? ids : Arrays.copyOf(startNow, n), at, building)
                .action(level);
        if (full > 0 && owner != null && !owner.isEmpty())
            HudClientboundPacket.showTempMessageI18n(owner, "hud.reignofnether.queue_full");
        return full;
    }

    // ------------------------------------------------------------------ editing the queue (BAR N / Ctrl+N)

    /**
     * BAR's command_skip_current (N): each unit drops the order it is carrying out and starts its next one now. A
     * builder's next site in its build queue (BuildRepairGoal.queuedBuildings, which runs before any order waiting
     * here: the builder is busy until its sites are done) comes first; then the next order in this queue; a unit with
     * nothing queued stops, exactly as after Stop. Units the owner may not command are left alone.
     */
    public static void skipCurrent(ServerLevel level, @Nullable String owner, int[] unitIds) {
        if (level == null || unitIds == null)
            return;
        long now = level.getGameTime();
        boolean sandbox = UnitActionItem.isSandboxOwner(level, owner);
        int[] stop = new int[unitIds.length];
        int n = 0;
        for (int id : unitIds) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Unit unit) || !(e instanceof LivingEntity le)
                    || !UnitActionItem.mayCommand(level, owner, unit, sandbox))
                continue;
            if (e instanceof WorkerUnit wu && skipBuildSite(wu))
                continue;
            CommandQueue q = get(id);
            if (q != null && !q.isEmpty()) {
                // the order being dropped ends here, whatever kind it is (an ability order may not reset on its own)
                Unit.fullResetBehaviours(unit);
                UnitServerEvents.cancelQueuedFormationMove(id);
                PatrolServerEvents.clear(id);
                GuardServerEvents.clear(id);
                if (startNext(level, q, now))
                    continue;
            }
            stop[n++] = id;
        }
        if (n > 0)
            issue(level, owner, UnitAction.STOP, -1, Arrays.copyOf(stop, n), BlockPos.ZERO, BlockPos.ZERO, QueueMode.REPLACE);
    }

    /**
     * BAR's command_cancel_last (Ctrl+N): each unit drops the LAST order in its queue - the newest order waiting here,
     * else a builder's last queued site, else (nothing queued) the order it is carrying out, as {@link #skipCurrent}.
     */
    public static void cancelLast(ServerLevel level, @Nullable String owner, int[] unitIds) {
        if (level == null || unitIds == null)
            return;
        boolean sandbox = UnitActionItem.isSandboxOwner(level, owner);
        int[] current = new int[unitIds.length];
        int n = 0;
        for (int id : unitIds) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Unit unit) || !(e instanceof LivingEntity)
                    || !UnitActionItem.mayCommand(level, owner, unit, sandbox))
                continue;
            CommandQueue q = get(id);
            if (q != null && q.pollLast() != null)
                continue;   // an emptied queue is dropped from the executor's list on its next pass
            if (e instanceof WorkerUnit wu && dropLastBuildSite(wu))
                continue;
            current[n++] = id;
        }
        if (n > 0)
            skipCurrent(level, owner, Arrays.copyOf(current, n));
    }

    /** A builder on a site with more sites queued: drops the current site and starts the next; false otherwise. */
    private static boolean skipBuildSite(WorkerUnit wu) {
        var brg = wu.getBuildRepairGoal();
        if (brg == null || brg.getBuildingTarget() == null)
            return false;
        var cur = brg.getBuildingTarget();
        brg.queuedBuildings.remove(cur);
        if (brg.queuedBuildings.isEmpty())
            return false;
        return brg.startNextQueuedBuilding();
    }

    /** Drops the last queued site that is not the one being built; false if there is none. */
    private static boolean dropLastBuildSite(WorkerUnit wu) {
        var brg = wu.getBuildRepairGoal();
        if (brg == null)
            return false;
        for (int i = brg.queuedBuildings.size() - 1; i >= 0; i--)
            if (brg.queuedBuildings.get(i) != brg.getBuildingTarget()) {
                brg.queuedBuildings.remove(i);
                return true;
            }
        return false;
    }

    /**
     * Puts an order the server adds by itself at the head of the unit's queue, past the cap (a hunting worker's
     * drop-off, UnitActionItem): it runs once the unit finishes what it is doing. Server thread only.
     */
    public static void pushFront(LivingEntity unit, UnitCommand c) {
        if (!(unit instanceof Unit) || unit.isRemoved())
            return;
        CommandQueue q = obtain(unit);
        q.addFirst(c);
        list(q);
    }

    // ------------------------------------------------------------------ the executor

    /**
     * Once per server tick at the end of the overworld tick (UnitServerEvents.onWorldTick), after every unit's goals
     * ran: every unit with orders waiting that has finished its current one starts the next. Allocation-free unless an
     * order starts: one pass over the units with orders waiting, a field read or two for each busy one (Unit.isIdle
     * answers cheapest-first); starting an order runs Reign of Nether's dispatch, which allocates as it always did.
     */
    public static void tick(ServerLevel level) {
        int size = LISTED.size();
        if (size == 0)
            return;
        long now = level.getGameTime();
        boolean budgeted = !UnitServerEvents.rtsPathfinding;
        long t0 = budgeted ? System.nanoTime() : 0L;
        int started = 0;
        boolean compact = false;
        int first = cursor < size ? cursor : 0;
        cursor = 0;
        for (int k = 0; k < size; k++) {
            int i = first + k < size ? first + k : first + k - size;
            CommandQueue q = LISTED.get(i);
            LivingEntity le = q.unit;
            if (q.dead || q.isEmpty() || le.isRemoved()) {
                compact = true;
                continue;
            }
            if (q.currentTick == now || !((Unit) le).isIdle())
                continue;   // busy, or its order started this tick and its goals haven't acted on it yet
            if (!le.isAlive()) {   // dying: its orders die with it
                q.clearWaiting();
                compact = true;
                continue;
            }
            if (budgeted && started >= MIN_STARTS_PER_TICK && System.nanoTime() - t0 > START_BUDGET_NANOS) {
                cursor = i;
                break;
            }
            if (startNext(level, q, now))
                started++;
            if (q.isEmpty())
                compact = true;
        }
        if (compact)
            compactListed();
    }

    /** Starts the unit's next order whose target is still there; false if none was left. */
    private static boolean startNext(ServerLevel level, CommandQueue q, long now) {
        UnitCommand c;
        while ((c = q.pollFirst()) != null) {
            if (targetGone(level, c))
                continue;
            q.start(c, now);
            new UnitActionItem(c.owner(), c.action(), c.targetId(), new int[] { q.unit.getId() }, c.pos(), BlockPos.ZERO)
                .action(level);
            return true;
        }
        q.current = null;
        return false;
    }

    /** True when the order is about an entity (CommandKind.needsLiveEntity) that died or was removed since. */
    public static boolean targetGone(ServerLevel level, UnitCommand c) {
        if (!c.kind().needsLiveEntity(c.targetId()))
            return false;
        Entity e = c.targetId() >= 0 ? level.getEntity(c.targetId()) : null;
        return e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive());
    }

    /**
     * Whether a busy unit is still carrying out {@code c}, the order its queue last started - so that a Ctrl order
     * interrupting it should hand it back. The queue only knows what it started; the unit's own state says whether
     * that is still going: a finished move or a dead attack target is not resumed, and neither is anything once the
     * unit has moved on to a fight of its own choosing (an auto-acquired target). Public for the hero AI's OrderLock:
     * one rule for "is the player's order still going".
     */
    public static boolean stillCarriedOut(LivingEntity le, Unit unit, UnitCommand c) {
        CommandKind kind = c.kind();
        if (!kind.resumable)
            return false;
        SelectedTargetGoal<?> targeting = unit.getTargetGoal();
        LivingEntity target = targeting != null ? targeting.getTarget() : null;
        boolean ownFight = target != null && !targeting.forced;
        return switch (kind) {
            case ATTACK -> target != null && target.getId() == c.targetId();
            case FOLLOW -> unit.getFollowTarget() != null && unit.getFollowTarget().getId() == c.targetId();
            case FIGHT -> le instanceof AttackerUnit attacker ? attacker.getAttackMoveTarget() != null
                : unit.getMoveGoal().getMoveTarget() != null;
            case PATROL -> PatrolServerEvents.isPatrolling(le.getId());
            case GUARD -> GuardServerEvents.isGuarding(le.getId());
            case MOVE -> !ownFight && (unit.getMoveGoal().getMoveTarget() != null || le instanceof WorkerUnit);
            default -> !ownFight;   // build / repair, reclaim, garrison, attack a building, a worker's errand
        };
    }

    private static void compactListed() {
        int w = 0;
        for (int r = 0, n = LISTED.size(); r < n; r++) {
            CommandQueue q = LISTED.get(r);
            if (!q.dead && !q.isEmpty() && !q.unit.isRemoved())
                LISTED.set(w++, q);
            else
                q.listed = false;
        }
        for (int r = LISTED.size() - 1; r >= w; r--)
            LISTED.remove(r);
    }

    // ------------------------------------------------------------------ queues

    private static CommandQueue obtain(LivingEntity unit) {
        CommandQueue q = QUEUES.get(unit.getId());
        if (q == null || q.unit != unit || q.dead) {
            q = new CommandQueue(unit);
            QUEUES.put(unit.getId(), q);
        }
        return q;
    }

    private static void list(CommandQueue q) {
        if (!q.listed) {
            q.listed = true;
            LISTED.add(q);
        }
    }

    /** The unit's queue, or null if it was never given an order. Read-only outside this package. */
    @Nullable
    public static CommandQueue get(int unitId) {
        CommandQueue q = QUEUES.get(unitId);
        return q == null || q.dead ? null : q;
    }

    /** Orders waiting behind the unit's current one. */
    public static int queuedCount(int unitId) {
        CommandQueue q = get(unitId);
        return q == null ? 0 : q.size();
    }

    public static boolean hasQueued(int unitId) {
        return queuedCount(unitId) > 0;
    }

    /** True if one of the unit's waiting orders is {@code a} or {@code b}. */
    public static boolean hasQueued(int unitId, UnitAction a, UnitAction b) {
        CommandQueue q = get(unitId);
        if (q != null)
            for (int i = 0; i < q.size(); i++) {
                UnitAction action = q.get(i).action();
                if (action == a || action == b)
                    return true;
            }
        return false;
    }

    /** A copy of the unit's waiting orders, next first. */
    public static List<UnitCommand> queued(int unitId) {
        CommandQueue q = get(unitId);
        if (q == null)
            return List.of();
        List<UnitCommand> out = new ArrayList<>(q.size());
        for (int i = 0; i < q.size(); i++)
            out.add(q.get(i));
        return out;
    }

    /** Forgets the unit's waiting orders and its current one (the caller is replacing what the unit does). */
    public static void clear(int unitId) {
        CommandQueue q = get(unitId);
        if (q != null) {
            q.clearWaiting();
            q.current = null;
        }
    }

    /** The unit left the level (died, unloaded, changed dimension): its queue goes with it. */
    public static void forget(int unitId) {
        CommandQueue q = QUEUES.remove(unitId);
        if (q != null) {
            q.dead = true;
            q.clearWaiting();
            q.current = null;
        }
    }

    /** Server stopping: a queue must never outlive its world (singleplayer opens the next one in the same JVM). */
    public static void clearAll() {
        for (CommandQueue q : LISTED)
            q.listed = false;
        LISTED.clear();
        QUEUES.clear();
        cursor = 0;
    }

    /** Every unit's queue, for the legacy snapshot (UnitServerEvents.getUnitActionSlowQueue). */
    public static Collection<CommandQueue> all() {
        return Collections.unmodifiableCollection(QUEUES.values());
    }

    /** Queues the executor looks at each tick (for tests and the benchmark). */
    public static int listedCount() {
        return LISTED.size();
    }
}
