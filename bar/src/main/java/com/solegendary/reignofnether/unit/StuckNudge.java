package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The nudge for a unit that wants to be somewhere and is not getting there (design/terrain_intelligence.md): a chase
 * whose path ends at a wall the body cannot pass, a march blocked by a crowd. {@link UnitUnstick} frees units that
 * are physically trapped; this is the lighter, faster rule for a unit that is free but not making progress. Every
 * {@link #CHECK_INTERVAL} ticks (staggered by entity id) a unit following a path, or chasing a target it is not in
 * range of, that moved less than {@link #MOVED} blocks since the last check gets, in order, one re-path, one
 * {@link #SIDESTEP}-block step perpendicular to its heading, then its chase target dropped. One stage per check, so
 * at most one nudge per 2 s per unit and never an oscillation; a unit that moves resets to the first stage. A unit
 * that is where its chase put it (a bank slot beside a wading target, or a deliberate hold) is not stuck.
 * The sidestep is served to the chase goals through {@link Engage}, since their own repath would otherwise undo it
 * within five ticks. One "[Stuck]" debug line per unit per {@link #LOG_INTERVAL}. Server side.
 */
public final class StuckNudge {
    private StuckNudge() { }

    public static final int CHECK_INTERVAL = 40;
    public static final double MOVED = 0.05;
    public static final double SIDESTEP = 1.0;
    public static final int SIDESTEP_TICKS = 40;
    public static final int LOG_INTERVAL = 200;

    public static final int STAGE_REPATH = 1, STAGE_SIDESTEP = 2, STAGE_GIVE_UP = 3;

    private static final class State {
        Vec3 lastPos;
        int stage;           // the last nudge applied (0 = none yet)
        // "long ago", not Long.MIN_VALUE: `now - MIN_VALUE` overflows negative and read as "nudged a moment ago", so
        // the first CI run never nudged anyone (stuck_chaser_is_nudged_then_drops_its_target: give-ups 0)
        long lastNudge = -LOG_INTERVAL, lastLog = -LOG_INTERVAL;
        @Nullable BlockPos sidestep;
        long sidestepUntil;
    }
    private static final Map<Mob, State> STATES = new WeakHashMap<>();
    // outside the state, which is dropped as soon as the unit no longer wants to move (right after a give-up)
    private static final Map<Mob, Integer> GIVE_UPS = new WeakHashMap<>();

    /** Server side, every tick from Unit.tick: does nothing except on the unit's staggered check tick. */
    public static void tick(Mob mob) {
        if ((mob.tickCount + mob.getId()) % CHECK_INTERVAL != 0 || !(mob.level() instanceof ServerLevel level))
            return;
        if (!(mob instanceof Unit unit) || !mob.isAlive() || mob.isPassenger() || unit.isFlyingUnit()
                || unit.getHoldPosition() || GarrisonableBuildingAddon.getGarrison(unit) != null) {
            STATES.remove(mob);
            return;
        }
        if (!wantsToMove(mob, unit)) {
            STATES.remove(mob);
            return;
        }
        State s = STATES.computeIfAbsent(mob, m -> new State());
        Vec3 pos = mob.position();
        boolean stalled = s.lastPos != null && s.lastPos.distanceToSqr(pos) < MOVED * MOVED;
        s.lastPos = pos;
        if (!stalled) {
            s.stage = 0;
            return;
        }
        long now = level.getGameTime();
        if (now - s.lastNudge < CHECK_INTERVAL || s.stage >= STAGE_GIVE_UP)
            return;   // never two nudges inside one interval; after the last stage, nothing more until it moves
        s.lastNudge = now;
        int stage = s.stage + 1;
        if (stage == STAGE_REPATH) {
            repath(mob, unit);
        } else if (stage == STAGE_SIDESTEP) {
            if (!sidestep(mob, unit, now))
                stage = STAGE_GIVE_UP;   // nowhere to step: give up now rather than stand another 2 s
        }
        if (stage == STAGE_GIVE_UP && mob.getTarget() != null) {
            // a plain move order is not dropped here: MoveToTargetBlockGoal has its own give-up after three repaths
            // that end on the same node, and dropping it would also drop an attack-move's resume point
            giveUp(mob, unit);
            GIVE_UPS.merge(mob, 1, Integer::sum);
        }
        s.stage = stage;
        if (now - s.lastLog >= LOG_INTERVAL) {
            s.lastLog = now;
            ReignOfNether.LOGGER.debug("[Stuck] {}#{} ({}) stage {} at {}", mob.getType().toShortString(), mob.getId(),
                unit.getOwnerName(), stage, mob.blockPosition().toShortString());
        }
    }

    /** The sidestep cell a chase goal should path to right now, or null when no sidestep is pending. */
    @Nullable
    public static BlockPos sidestep(Mob mob) {
        State s = STATES.get(mob);
        if (s == null || s.sidestep == null)
            return null;
        if (mob.level().getGameTime() > s.sidestepUntil) {
            s.sidestep = null;
            return null;
        }
        return s.sidestep;
    }

    /** For tests: how many chase targets this unit has given up. */
    public static int giveUps(Mob mob) {
        return GIVE_UPS.getOrDefault(mob, 0);
    }

    /** For tests: the game tick of the last nudge, negative when there was none. */
    public static long lastNudge(Mob mob) {
        State s = STATES.get(mob);
        return s == null ? -1 : s.lastNudge;
    }

    // ---- internals ----

    /** Following a path, or chasing a target it is not in range of and not settled where the chase put it. */
    private static boolean wantsToMove(Mob mob, Unit unit) {
        if (!mob.getNavigation().isDone())
            return true;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !(unit instanceof AttackerUnit au))
            return false;
        double range = unit instanceof RangedAttackerUnit ? au.getAttackRange() : Math.sqrt(Engage.reachSqr(mob, target));
        if (mob.distanceToSqr(target) <= range * range)
            return false;
        return !Engage.settled(mob);
    }

    private static void repath(Mob mob, Unit unit) {
        // the chase goals repath within their next recalc (5-20 ticks) once the navigation is empty, and the move
        // goal restarts from here on its next canContinueToUse. Not forceRepath: that resets the move goal's own
        // stuck count, and a move order that keeps ending on the same node must still reach its give-up.
        mob.getNavigation().stop();
    }

    /** One block perpendicular to the heading (next path node, else the target, else the facing), onto a cell it can stand in. */
    private static boolean sidestep(Mob mob, Unit unit, long now) {
        Vec3 heading = heading(mob);
        double px = -heading.z, pz = heading.x;   // perpendicular
        MobilityClass mobility = MobilityClass.of(unit);
        BlockPos here = BlockPos.containing(mob.getX(), mob.getY() + 0.01, mob.getZ());
        // alternate the side between attempts so two stuck units beside each other do not step into each other
        int first = (mob.getId() & 1) == 0 ? 1 : -1;
        for (int k = 0; k < 2; k++) {
            int sign = k == 0 ? first : -first;
            BlockPos cell = Engage.standCell(mob.level(), BlockPos.containing(mob.getX() + px * SIDESTEP * sign,
                mob.getY() + 0.01, mob.getZ() + pz * SIDESTEP * sign), mobility);
            if (cell == null || cell.equals(here))
                continue;
            State s = STATES.get(mob);
            if (s != null) {
                s.sidestep = cell;
                s.sidestepUntil = now + SIDESTEP_TICKS;
            }
            mob.getNavigation().moveTo(mob.getNavigation().createPath(cell, 0), 1.0);
            return true;
        }
        return false;
    }

    private static Vec3 heading(Mob mob) {
        Path p = mob.getNavigation().getPath();
        Vec3 h = null;
        if (p != null && !p.isDone()) {
            BlockPos n = p.getNextNodePos();
            h = new Vec3(n.getX() + 0.5 - mob.getX(), 0, n.getZ() + 0.5 - mob.getZ());
        } else if (mob.getTarget() != null) {
            h = new Vec3(mob.getTarget().getX() - mob.getX(), 0, mob.getTarget().getZ() - mob.getZ());
        }
        if (h == null || h.lengthSqr() < 1.0E-4)
            h = Vec3.directionFromRotation(0, mob.getYRot()).multiply(1, 0, 1);
        if (h.lengthSqr() < 1.0E-4)
            h = new Vec3(1, 0, 0);
        return h.normalize();
    }

    private static void giveUp(Mob mob, Unit unit) {
        Engage.release(mob);
        mob.setTarget(null);
        if (unit.getTargetGoal() != null)
            unit.getTargetGoal().setTarget(null);
        mob.getNavigation().stop();
    }
}
