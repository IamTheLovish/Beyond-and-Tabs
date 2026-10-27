package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.hero.PossessionServer;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skips vanilla work for bodies that have nothing to do (Oct 26 perf plan; vanilla's entity tick was 88.7% of a
 * 300-unit tick). Three gates, all server side, all called from mixins:
 * <ul>
 *   <li>{@link #skipTravel}: a Unit standing still on the ground with no input skips LivingEntity.travel (its
 *       collision query is the costliest part of the tick), except every {@link #FULL_TRAVEL_EVERY}th tick, so a unit
 *       whose floor was broken still falls;</li>
 *   <li>{@link #skipIdleAi}: an idle Unit (Unit.isIdle, no target, no move order, navigation done, not hurt) runs
 *       serverAiStep only every {@link #IDLE_AI_EVERY}th tick;</li>
 *   <li>{@link #skipWildAi}: a wild Animal (not a Unit, not a pet) with no unit or player within 32 blocks skips
 *       serverAiStep; the awake area is rebuilt once a second ({@link #refreshAwake}).</li>
 * </ul>
 * Off switch: start the server with {@code -Dbat.tickGates=false} or the environment variable BAT_TICK_GATES=false (all
 * gates, and the units' skipped entity-collision query in EntityMixin, then do nothing). Manual steps if
 * a gate misbehaves: add that flag to the server's JVM arguments and restart; nothing else depends on the gates.
 */
public final class TickGates {
    private TickGates() { }

    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("bat.tickGates",
        System.getenv().getOrDefault("BAT_TICK_GATES", "true")));
    public static final int FULL_TRAVEL_EVERY = 10;
    public static final int IDLE_AI_EVERY = 4;
    /** Cell size of the awake area: a body is awake when a unit or player is in its cell or one of the 8 around it. */
    static final int CELL_SHIFT = 5;   // 32 blocks: the 3x3 cells around a unit hold every point within 32 blocks of it
    /** The awake area is trusted for this many game ticks after a rebuild; an older one (rebuild stopped) skips nothing. */
    static final long AWAKE_STALE_TICKS = 40;

    private static volatile LongOpenHashSet awake = null;
    private static volatile long awakeGameTime = Long.MIN_VALUE;

    /** Moves counted per watched entity (tests): Entity.move calls on the server. */
    private static final Map<Entity, int[]> WATCHED = new ConcurrentHashMap<>();

    /** A Unit at rest: not moving, on the ground, no input, no order. The mixin adds the jump flag (protected). */
    public static boolean skipTravel(LivingEntity le) {
        if (!ENABLED || !(le instanceof Unit unit) || !(le instanceof Mob mob) || le.level().isClientSide())
            return false;
        if (le.tickCount % FULL_TRAVEL_EVERY == 0 || !le.onGround() || le.hurtTime > 0 || le.zza != 0 || le.xxa != 0
                || le.isInWater() || le.isInLava() || le.isPassenger() || le.isVehicle() || le.isNoGravity())
            return false;
        Vec3 dm = le.getDeltaMovement();
        if (dm.x * dm.x + dm.z * dm.z >= 1.0e-6)
            return false;
        if (unit.getMoveGoal() != null && unit.getMoveGoal().getMoveTarget() != null)
            return false;
        if (!mob.getNavigation().isDone() || mob.getMoveControl().hasWanted())
            return false;
        return !PossessionServer.isPossessed(le);
    }

    /** An idle Unit thinks every IDLE_AI_EVERY ticks. Called after the possession check. */
    public static boolean skipIdleAi(Mob mob) {
        if (!ENABLED || !(mob instanceof Unit unit) || mob.level().isClientSide())
            return false;
        if (mob.tickCount % IDLE_AI_EVERY == 0 || mob.hurtTime > 0 || mob.getTarget() != null)
            return false;
        if (unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null)
            return false;
        if (unit.getMoveGoal() == null || unit.getMoveGoal().getMoveTarget() != null)
            return false;
        if (!mob.getNavigation().isDone() || mob.getMoveControl().hasWanted())
            return false;
        return unit.isIdle();
    }

    /** A wild animal far from every unit and player does not think. */
    public static boolean skipWildAi(Mob mob) {
        if (!ENABLED || !(mob instanceof Animal) || mob instanceof Unit || !(mob.level() instanceof ServerLevel level))
            return false;
        LongOpenHashSet cells = awake;
        if (cells == null || level.dimension() != Level.OVERWORLD || level.getGameTime() - awakeGameTime > AWAKE_STALE_TICKS)
            return false;
        if (mob.hurtTime > 0 || mob.isLeashed() || mob.isPassenger() || mob.isVehicle()
                || (mob instanceof TamableAnimal pet && pet.isTame()))
            return false;
        return !cells.contains(cell(mob.getBlockX() >> CELL_SHIFT, mob.getBlockZ() >> CELL_SHIFT));
    }

    /** Rebuilds the awake area of the overworld from its units and players (NonUnitServerEvents, once a second). */
    public static void refreshAwake(ServerLevel level, Collection<? extends LivingEntity> units) {
        LongOpenHashSet cells = new LongOpenHashSet();
        for (LivingEntity u : units)
            if (u.level() == level && !u.isRemoved())
                mark(cells, u);
        for (Player p : level.players())
            if (!p.isSpectator())
                mark(cells, p);
        awake = cells;
        awakeGameTime = level.getGameTime();
    }

    private static void mark(LongOpenHashSet cells, Entity e) {
        int cx = e.getBlockX() >> CELL_SHIFT, cz = e.getBlockZ() >> CELL_SHIFT;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                cells.add(cell(cx + dx, cz + dz));
    }

    private static long cell(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    // ---- move counting (tests) ----

    /** Entity.move HEAD (EntityMixin). One empty-map check unless a test watches. */
    public static void onMove(Entity e) {
        if (WATCHED.isEmpty())
            return;
        int[] c = WATCHED.get(e);
        if (c != null && !e.level().isClientSide())
            c[0]++;
    }

    public static void watchMoves(Collection<? extends Entity> entities) {
        for (Entity e : entities)
            WATCHED.put(e, new int[1]);
    }

    /** Total moves of the given watched entities; stops watching them. */
    public static int unwatchMoves(Collection<? extends Entity> entities) {
        int total = 0;
        for (Entity e : entities) {
            int[] c = WATCHED.remove(e);
            if (c != null)
                total += c[0];
        }
        return total;
    }

    static void onServerStopping() {
        WATCHED.clear();
        awake = null;
        awakeGameTime = Long.MIN_VALUE;
    }
}
