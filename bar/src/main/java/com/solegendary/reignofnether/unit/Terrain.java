package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Terrain as a decision (design/terrain_intelligence.md). Oct 13's 2v2 and 3v3 clips had whole armies fighting
 * chest-deep in rivers: water cost nothing but the crawl vanilla gives every mob, so nobody avoided it and nobody was
 * punished for standing in it. Beyond All Reason, the game this mod models, keeps most land units out of water and
 * slows the ones that can enter. This is the one place that says what water does to a unit; the mixins, goals and
 * the unit card read it, no unit class carries its own water rule.
 * <ul>
 *   <li><b>Wading</b>: a ground unit with its body in water (vanilla's own {@code isInWater}, the flag its travel
 *       physics use) that is neither flying, nor AQUATIC (Drowned, dogs, cats, water animals), nor TIDAL (the
 *       Tidewrought) moves at {@link #WADING_SPEED} of its land speed and hits for {@link #WADING_DAMAGE} of its damage.
 *       The exempt classes come from {@link MobilityClass}, the same table the pathfinder prices water with.</li>
 *   <li>The speed is set through the water friction ({@link #waterFriction}) rather than a speed-attribute modifier,
 *       because vanilla's water branch of {@code LivingEntity.travel} already makes a mob 5-7x slower than on land
 *       (acceleration {@code 0.02 * zza} against friction 0.8); halving the attribute on top of that would turn a
 *       4-wide ford into a 20 s crawl, which is exactly where the clips showed units piling up. Half of LAND speed
 *       is a speed-up for a crossing and still a real cost.</li>
 *   <li>Cell tests for the chase goals: {@link #canStand} is the pathfinder's own classification, so a goal cell
 *       the chase picks is never one the pathfinder would refuse.</li>
 * </ul>
 */
public final class Terrain {
    private Terrain() { }

    /** A wading unit's speed as a fraction of its land speed. */
    public static final float WADING_SPEED = 0.5f;
    /** A wading unit's damage as a fraction of its normal damage (melee and projectiles alike). */
    public static final float WADING_DAMAGE = 0.75f;
    /** Ticks between the foam particles over a wading unit. */
    public static final int SPLASH_INTERVAL = 40;

    public enum WadingState {
        /** Dry, or not a unit at all. */
        NONE,
        /** A ground unit with its body in water: slowed and weakened. */
        WADING,
        /** In water but built for it (flying, AQUATIC, TIDAL): full speed and damage. */
        EXEMPT;

        public boolean isWading() { return this == WADING; }
    }

    /** The entity's state right now. Cheap: a cached flag and two instanceof checks before the mobility table. */
    public static WadingState of(LivingEntity le) {
        if (!(le instanceof Unit unit) || !le.isInWater())
            return WadingState.NONE;
        return isExempt(unit) ? WadingState.EXEMPT : WadingState.WADING;
    }

    /** Units water does not slow: flyers (never in it), AQUATIC and TIDAL bodies (built for it). */
    public static boolean isExempt(Unit unit) {
        if (unit.isFlyingUnit())
            return true;
        MobilityClass mobility = MobilityClass.of(unit);
        return mobility == MobilityClass.AQUATIC || mobility == MobilityClass.TIDAL;
    }

    public static boolean isWading(LivingEntity le) {
        return of(le).isWading();
    }

    /** The multiplier on damage {@code attacker} deals right now (0.75 while wading). */
    public static float damageMultiplier(Entity attacker) {
        return attacker instanceof LivingEntity le && isWading(le) ? WADING_DAMAGE : 1.0f;
    }

    /**
     * The water friction that gives a wading unit {@link #WADING_SPEED} of its land speed, or {@code vanilla} when the
     * unit is not wading. Derived from {@code LivingEntity.travel}: on ground a mob's terminal speed is about
     * {@code 1.6 * a^2} blocks/tick for movement speed {@code a} (per-tick push {@code 1.33 a * zza}, {@code zza = a},
     * friction 0.546); in water it is {@code 0.02 a * f / (1 - f)} for water friction {@code f}. Setting the two equal
     * at half speed gives {@code f / (1 - f) = 40 a}. Clamped so a very slow or very fast unit never gets vanilla's
     * crawl (0.8) or an ice-rink (0.98).
     */
    public static float waterFriction(LivingEntity le, float vanilla) {
        if (!isWading(le))
            return vanilla;
        double a = le.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (le instanceof Unit u)
            a *= u.getSpeedModifier();
        double k = 80.0 * a * WADING_SPEED;
        float f = (float) (k / (1.0 + k));
        return Math.max(0.8f, Math.min(0.98f, f));
    }

    /** Server side, every tick from Unit.tick: the foam over a wading unit so the player can see the state. */
    public static void tick(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || (mob.tickCount + mob.getId()) % SPLASH_INTERVAL != 0)
            return;
        if (!isWading(mob))
            return;
        level.sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY() + 0.35, mob.getZ(), 6,
            mob.getBbWidth() * 0.4, 0.05, mob.getBbWidth() * 0.4, 0.0);
    }

    // ---- cells, for the chase goals (Engage) ----

    /** Whether the cell's feet block holds water (source, flowing or waterlogged). */
    public static boolean isWet(Level level, BlockPos cell) {
        return level.getFluidState(cell).is(FluidTags.WATER);
    }

    /** Whether the entity's own feet cell holds water (the body may still be touching water from a bank cell). */
    public static boolean feetInWater(LivingEntity le) {
        return isWet(le.level(), BlockPos.containing(le.getX(), le.getY() + 0.01, le.getZ()));
    }

    /**
     * Whether a unit of {@code mobility} can stand in {@code cell} by the pathfinder's rules: the cell is open with
     * a floor, and if it is water the class is one that may enter water. Land, tidepools, fire and slime count
     * (the chase prices them like the pathfinder: a cost, not a wall); lava and blocked cells never.
     */
    public static boolean canStand(Level level, BlockPos cell, MobilityClass mobility) {
        byte kind = classify(level, cell);
        if (kind == WalkabilityBuilder.KIND_WATER)
            return mobility == MobilityClass.AQUATIC || mobility == MobilityClass.TIDAL;
        if (kind == WalkabilityBuilder.KIND_LAVA)
            return mobility == MobilityClass.FIRE_IMMUNE;
        return kind != WalkabilityBuilder.KIND_BLOCKED;
    }

    /** {@link #canStand} on a dry cell only: where a chase waits for a target it must not follow into water. */
    public static boolean isLand(Level level, BlockPos cell) {
        byte kind = classify(level, cell);
        return kind != WalkabilityBuilder.KIND_BLOCKED && kind != WalkabilityBuilder.KIND_WATER
            && kind != WalkabilityBuilder.KIND_LAVA;
    }

    // ---- the per-tick classify memo ----
    // Engage.melee asks standCell for up to 8 ring cells (and 40 more on the outer rings), 1-3 classify calls each,
    // for every chaser on every repath; in a scrum the chasers of one target ask about the same few dozen cells, and
    // each classify reads 3+ block states through the chunk lookup (the Oct 22 match: the "unspecified" Engage
    // getChunk storm under tinker, 2.35% of the tick). The answer is the same for the rest of the tick unless a block
    // changes, so the server thread memoises it: cleared when the game time or the level changes, and on any block
    // change (LevelChunkMixin bumps blockChanges). The client and other threads read the level directly.
    private static final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap MEMO = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
    private static Level memoLevel = null;
    private static long memoTime = Long.MIN_VALUE;
    private static int memoChanges = 0;
    private static volatile int blockChanges = 0;
    private static volatile Thread memoThread = null;
    /** Calls answered from the memo / read from the level (tests, perf notes). */
    public static long memoHits = 0, memoMisses = 0;

    /** Any block change in a loaded chunk (LevelChunkMixin): what was memoised this tick may be stale now. */
    public static void onBlockChanged() {
        blockChanges++;
    }

    /** WalkabilityBuilder.classify, memoised per tick on the server thread. */
    public static byte classify(Level level, BlockPos cell) {
        if (level.isClientSide() || !(level instanceof ServerLevel server) || Thread.currentThread() != serverThread(server))
            return WalkabilityBuilder.classify(level, cell.getX(), cell.getY(), cell.getZ());
        long now = level.getGameTime();
        int changes = blockChanges;
        if (level != memoLevel || now != memoTime || changes != memoChanges) {
            MEMO.clear();
            memoLevel = level;
            memoTime = now;
            memoChanges = changes;
        }
        long key = cell.asLong();
        byte kind = MEMO.getOrDefault(key, (byte) -1);
        if (kind != (byte) -1) {
            memoHits++;
            return kind;
        }
        memoMisses++;
        kind = WalkabilityBuilder.classify(level, cell.getX(), cell.getY(), cell.getZ());
        MEMO.put(key, kind);
        return kind;
    }

    private static Thread serverThread(ServerLevel level) {
        Thread t = memoThread;
        if (t == null) {
            t = level.getServer().getRunningThread();
            memoThread = t;
        }
        return t;
    }

    /** The server stopped: drop the memo (and its level) so an integrated server's next world starts clean. */
    public static void resetMemo() {
        MEMO.clear();
        memoLevel = null;
        memoTime = Long.MIN_VALUE;
        memoThread = null;
    }
}
