package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.perf.ServerTickProfile;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.TargetScan;
import com.solegendary.reignofnether.unit.TargetScansLegacy;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Target acquisition through unit/TargetScan (Oct 21 perf plan, PR 5): the per-tick grid with its side masks, the
 * lazy line-of-sight pick and the idle stagger (AttackerUnit.IDLE_AGGRO_TICKS).
 * <ul>
 *   <li>The grid answers exactly as the full entity-section scan it replaced (unit/TargetScansLegacy) on random
 *       layouts of every side, flyers, anti-air, effects, cloaks, dying units and wild mobs; an exact tie in distance
 *       is the one allowed difference (the lower entity id wins).</li>
 *   <li>An idle unit still picks an enemy that steps into range within the stagger and a margin.</li>
 *   <li>A unit with only friends and allies round it never gathers candidates (the masks stop it), and does once an
 *       enemy stands in the cells round it.</li>
 *   <li>A unit fresh from a fight looks on every 4-tick pass: the stagger never delays a unit in combat.</li>
 *   <li>A [Stress] benchmark: an idle standoff of 300 units and an 80-unit brawl, timed the old way, the new scan
 *       with the old 4-tick idle cadence, and as shipped, in alternating phases of one run.</li>
 * </ul>
 * Areas: each test's pad is 6000 blocks west and 47000 north of its arena (the stress pad 52000 north), every test a
 * further 200 blocks east of the one before - no other test goes there.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TargetScanGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int Y = 240;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_tscan_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** The chunk-aligned corner of this test's pad: 6000 west and {@code north} blocks north of its arena, plus east. */
    static int[] origin(GameTestHelper helper, int east, int north) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        return new int[] { ((base.getX() - 6000 + east) >> 4) << 4, ((base.getZ() - north) >> 4) << 4 };
    }

    static String describe(Entity e) {
        if (e == null)
            return "none";
        String owner = e instanceof Unit u ? u.getOwnerName() : "wild";
        return EntityType.getKey(e.getType()).getPath() + "#" + e.getId() + "(" + owner + ")";
    }

    /** The sort key both scans order candidates by (MiscUtil): the distance, +100 for the deprioritised kinds. */
    static double key(Mob m, LivingEntity e) {
        if (e == null)
            return Double.NaN;
        double d = e.position().distanceTo(m.position());
        return MiscUtil.isDeprioritisedTarget(e, MiscUtil.isMeleeAttacker(m)) ? d + 100 : d;
    }

    static Unit unitOf(Mob m) {
        return (Unit) m;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // equivalence on random layouts
    // ---------------------------------------------------------------------------------------------------------------

    static final int EQ_SIZE = 64;
    static final String[] LAYOUTS = { "a crowd of every side", "two camps apart", "flyers over anti-air",
        "a lattice (exact ties)", "effects, cloaks and the dying" };

    /**
     * Five layouts on a 64-block pad with stone posts in the way (line of sight decides between candidates): every
     * living mob asks both scans at its aggro range and at 6, 16 and 28 blocks, and both must name the same target -
     * or two at exactly the same sort key, the tie rule the grid changed (lower entity id). The entities are spawned
     * and discarded inside one tick callback, so none of them ever acts. Both paths of the grid must be exercised:
     * scans that stop at the masks ("two camps") and scans that gather.
     */
    @GameTest(template = ARENA, timeoutTicks = 1400, batch = "target_scans")
    public static void target_scan_matches_the_full_scan_on_random_layouts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int[] o = origin(helper, 0, 47000);
        int ox = o[0], oz = o[1];
        List<long[]> chunks = BuildingGridGameTests.chunksOf(ox, oz, ox + EQ_SIZE, oz + EQ_SIZE);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "target scan layouts");
        GameTestChunks.whenEntitiesReady(helper, level, chunks, 5, 200, 1200, "target scan layouts", () -> {
            String a = owner(helper, "a"), b = owner(helper, "b"), c = owner(helper, "c");
            String[] owners = { a, b, c, "" };   // c is a's ally; "" is neutral
            List<Entity> made = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            int[] tally = new int[3];   // comparisons, exact ties, comparisons that found a target
            int[] perLayout = new int[LAYOUTS.length];
            long skips0 = TargetScan.skips, gathers0 = TargetScan.gathers;
            try {
                AlliancesServerEvents.addAlliance(a, c);
                Random rng = new Random(29);
                BuildingGridGameTests.pad(level, ox, oz, ox + EQ_SIZE, oz + EQ_SIZE, Y, rng, 0);
                for (int i = 0; i < 48; i++) {
                    int x = ox + 2 + rng.nextInt(EQ_SIZE - 4), z = oz + 2 + rng.nextInt(EQ_SIZE - 4);
                    for (int y = Y + 1; y <= Y + 4; y++)
                        level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                }
                for (int layout = 0; layout < LAYOUTS.length; layout++) {
                    spawnLayout(level, rng, layout, ox, oz, owners, made);
                    perLayout[layout] = made.size();
                    compare(level, made, LAYOUTS[layout], problems, tally);
                    for (Entity e : made)
                        e.discard();
                    made.clear();
                }
            } finally {
                for (Entity e : made)
                    if (!e.isRemoved())
                        e.discard();
                AlliancesServerEvents.removeAlliance(a, c);
                GameTestChunks.releaseAll(level, chunks);
            }
            long skips = TargetScan.skips - skips0, gathers = TargetScan.gathers - gathers0;
            if (skips == 0)
                problems.add("no scan stopped at the masks (the two camps should): the skip path was never compared");
            if (gathers == 0)
                problems.add("no scan gathered candidates");
            if (tally[2] < tally[0] / 5)
                problems.add("only " + tally[2] + " of " + tally[0] + " scans found a target: the layouts test too little");
            ReignOfNether.LOGGER.info(String.format(Locale.ROOT,
                "[TargetScan] equivalence: entities per layout %s | %d comparisons, %d found a target, %d exact ties | "
                    + "%d scans stopped at the masks, %d gathered | %d differences",
                Arrays.toString(perLayout), tally[0], tally[2], tally[1], skips, gathers, problems.size()));
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(problems.size() + " problems; " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
        });
    }

    static Mob put(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double y, double z,
                   List<Entity> made) {
        Mob m = TerrainGameTests.spawn(level, type, owner, x, y, z, made);
        if (m != null && m.isRemoved())
            made.remove(m);
        return m;
    }

    static <T> T pick(Random rng, List<T> list) {
        return list.get(rng.nextInt(list.size()));
    }

    static void spawnLayout(ServerLevel level, Random rng, int layout, int ox, int oz, String[] owners, List<Entity> made) {
        List<EntityType<? extends Mob>> ground = List.of(EntityRegistrar.VINDICATOR_UNIT.get(),
            EntityRegistrar.PILLAGER_UNIT.get(), EntityRegistrar.SKELETON_UNIT.get(), EntityRegistrar.IRON_GOLEM_UNIT.get(),
            EntityRegistrar.VILLAGER_UNIT.get(), EntityRegistrar.WISP_CHOIR_UNIT.get(), EntityRegistrar.WIND_MONK_UNIT.get());
        List<EntityType<? extends Mob>> air = List.of(EntityRegistrar.BAT_UNIT.get(), EntityRegistrar.OWL_WATCHER_UNIT.get(),
            EntityRegistrar.GULL_SPOTTER_UNIT.get(), EntityRegistrar.BEE_UNIT.get());
        switch (layout) {
            case 0 -> {
                for (int i = 0; i < 110; i++)
                    put(level, pick(rng, rng.nextInt(5) == 0 ? air : ground), owners[rng.nextInt(owners.length)],
                        ox + rng.nextDouble() * EQ_SIZE, Y + 1 + rng.nextDouble() * (rng.nextInt(5) == 0 ? 9 : 2),
                        oz + rng.nextDouble() * EQ_SIZE, made);
                wild(level, rng, ox, oz, made);
            }
            case 1 -> {
                // a's army with c's (allied) in one corner, b's in the far one: 45 blocks apart, past every reach
                for (int i = 0; i < 50; i++)
                    put(level, pick(rng, ground), i % 5 == 0 ? owners[2] : owners[0], ox + rng.nextDouble() * 14,
                        Y + 1, oz + rng.nextDouble() * 14, made);
                for (int i = 0; i < 50; i++)
                    put(level, pick(rng, ground), owners[1], ox + EQ_SIZE - 14 + rng.nextDouble() * 14, Y + 1,
                        oz + EQ_SIZE - 14 + rng.nextDouble() * 14, made);
            }
            case 2 -> {
                List<EntityType<? extends Mob>> antiAir = List.of(EntityRegistrar.WISP_CHOIR_UNIT.get(),
                    EntityRegistrar.FLAK_SWIVEL_UNIT.get(), EntityRegistrar.WIND_MONK_UNIT.get(),
                    EntityRegistrar.FLAK_CART_UNIT.get(), EntityRegistrar.VINDICATOR_UNIT.get(),
                    EntityRegistrar.PILLAGER_UNIT.get());
                for (int i = 0; i < 50; i++)
                    put(level, pick(rng, antiAir), owners[0], ox + 8 + rng.nextDouble() * 48, Y + 1,
                        oz + 8 + rng.nextDouble() * 48, made);
                for (int i = 0; i < 35; i++)
                    put(level, pick(rng, air), rng.nextBoolean() ? owners[1] : owners[3], ox + 8 + rng.nextDouble() * 48,
                        Y + 4 + rng.nextDouble() * 8, oz + 8 + rng.nextDouble() * 48, made);
                for (int i = 0; i < 10; i++)
                    put(level, pick(rng, ground), owners[1], ox + 8 + rng.nextDouble() * 48, Y + 1,
                        oz + 8 + rng.nextDouble() * 48, made);
            }
            case 3 -> {
                // every unit at the same height on a 2-block lattice: many candidates at exactly the same distance
                for (int x = 0; x < 10; x++)
                    for (int z = 0; z < 10; z++)
                        put(level, (x + z) % 2 == 0 ? EntityRegistrar.VINDICATOR_UNIT.get() : EntityRegistrar.PILLAGER_UNIT.get(),
                            owners[(x * 3 + z) % owners.length], ox + 22.5 + 2 * x, Y + 1, oz + 22.5 + 2 * z, made);
            }
            default -> {
                // the priority kinds (they prefer the unpoisoned, the unfrightened, the withered, the grounded), their
                // targets with and without those effects, cloaked and visible Shade Rangers, dying units, wild mobs
                List<EntityType<? extends Mob>> picky = List.of(EntityRegistrar.BOGGED_UNIT.get(),
                    EntityRegistrar.WRAITH_UNIT.get(), EntityRegistrar.WITHER_SKELETON_UNIT.get(),
                    EntityRegistrar.WINDCALLER_UNIT.get(), EntityRegistrar.SHADE_RANGER_UNIT.get());
                List<MobEffect> effects = List.of(MobEffects.POISON, MobEffectRegistrar.FEARFUL.get(), MobEffects.WITHER,
                    MobEffects.LEVITATION);
                for (int i = 0; i < 100; i++) {
                    Mob m = put(level, pick(rng, rng.nextBoolean() ? picky : ground), owners[rng.nextInt(owners.length)],
                        ox + rng.nextDouble() * EQ_SIZE, Y + 1 + rng.nextDouble() * 2, oz + rng.nextDouble() * EQ_SIZE, made);
                    if (m == null)
                        continue;
                    if (rng.nextInt(3) == 0)
                        m.addEffect(new MobEffectInstance(pick(rng, effects), 400));
                    if (m instanceof com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit && rng.nextBoolean())
                        m.setInvisible(true);   // cloaked (ShadeRangerUnit.isCloaked)
                }
                wild(level, rng, ox, oz, made);
                // dying: health 0, still in the level for the death animation - the full scan returned them, so must the grid
                for (Entity e : made)
                    if (e instanceof Unit && e instanceof Mob m && rng.nextInt(10) == 0)
                        m.setHealth(0);
            }
        }
    }

    /** A few wild mobs: cows and a villager (never picked), zombies (picked under neutralAggro), an armor stand, a vex. */
    static void wild(ServerLevel level, Random rng, int ox, int oz, List<Entity> made) {
        List<EntityType<? extends Entity>> kinds = List.of(EntityType.COW, EntityType.COW, EntityType.ZOMBIE,
            EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ARMOR_STAND, EntityType.VEX, EntityType.VILLAGER);
        for (int i = 0; i < 16; i++) {
            Entity e = kinds.get(i % kinds.size()).create(level);
            if (e == null)
                continue;
            e.moveTo(ox + rng.nextDouble() * EQ_SIZE, Y + 1, oz + rng.nextDouble() * EQ_SIZE, 0, 0);
            if (level.addFreshEntity(e))
                made.add(e);
        }
    }

    static void compare(ServerLevel level, List<Entity> made, String layout, List<String> problems, int[] tally) {
        for (Entity e : made) {
            if (!(e instanceof Mob m) || !m.isAlive() || m.isRemoved())
                continue;
            float aggro = m instanceof AttackerUnit au ? au.getAggroRange() : 10f;
            for (float r : new float[] { aggro, 6f, 16f, 28f }) {
                LivingEntity full = TargetScansLegacy.findClosestAttackableEntity(m, r, level);
                LivingEntity grid = TargetScan.findClosestAttackableEntity(m, r, level);
                tally[0]++;
                if (full != null)
                    tally[2]++;
                if (full == grid)
                    continue;
                Predicate<LivingEntity> priority = MiscUtil.targetPriority(m);
                if (full != null && grid != null && key(m, full) == key(m, grid)
                        && (priority == null || priority.test(full) == priority.test(grid))) {
                    tally[1]++;
                    continue;
                }
                problems.add(String.format(Locale.ROOT, "%s: %s at %.0f blocks: full scan %s (%.3f), grid %s (%.3f)", layout,
                    describe(m), r, describe(full), key(m, full), describe(grid), key(m, grid)));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the idle stagger
    // ---------------------------------------------------------------------------------------------------------------

    static final int TRIALS = 8;
    /** Ticks past the stagger an idle unit may take to pick an enemy that stepped into range (polls, tick order). */
    static final int MARGIN = 4;

    /**
     * Eight times, a fresh idle Vindicator stands with an enemy villager 20 blocks off (out of its 10-block reach);
     * after a different settling time each trial (so the entry lands on every phase of the stagger) the villager is
     * put 4 blocks from it - stepping into range at a known tick. The Vindicator must take it within
     * IDLE_AGGRO_TICKS + MARGIN ticks every time. The delays are logged.
     */
    @GameTest(template = ARENA, timeoutTicks = 900, batch = "target_scans")
    public static void target_scan_idle_unit_takes_an_enemy_that_steps_into_range_within_the_stagger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int[] o = origin(helper, 200, 47000);
        int ox = o[0], oz = o[1];
        List<long[]> chunks = BuildingGridGameTests.chunksOf(ox, oz, ox + 32, oz + 32);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "idle stagger pad");
        String a = owner(helper, "stagger_a"), b = owner(helper, "stagger_b");
        int limit = AttackerUnit.IDLE_AGGRO_TICKS + MARGIN;
        List<Entity> made = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        boolean[] ready = { false }, done = { false };
        int[] trial = { 0 }, t = { 0 }, enteredAt = { -1 };
        Mob[] pair = new Mob[2];
        Runnable cleanUp = () -> {
            for (Entity e : made)
                if (!e.isRemoved())
                    e.discard();
            made.clear();
            GameTestChunks.releaseAll(level, chunks);
        };
        helper.onEachTick(() -> {
            if (done[0] || !ready[0])
                return;
            try {
                t[0]++;
                Mob at = pair[0], foe = pair[1];
                if (at == null) {
                    // a new trial
                    at = put(level, EntityRegistrar.VINDICATOR_UNIT.get(), a, ox + 8.5, Y + 1, oz + 16.5, made);
                    foe = put(level, EntityRegistrar.VILLAGER_UNIT.get(), b, ox + 28.5, Y + 1, oz + 16.5, made);
                    if (at == null || foe == null)
                        throw new GameTestAssertException("could not create the trial's units");
                    pair[0] = at;
                    pair[1] = foe;
                    t[0] = 0;
                    enteredAt[0] = -1;
                    return;
                }
                LivingEntity target = unitOf(at).getTargetGoal().getTarget();
                if (enteredAt[0] < 0) {
                    if (target != null)
                        throw new GameTestAssertException("trial " + trial[0] + ": the Vindicator took " + describe(target)
                            + " before anything was in its reach");
                    if (t[0] < 12 + trial[0])
                        return;
                    if (TargetScan.inCombat(at))
                        throw new GameTestAssertException("trial " + trial[0] + ": the idle Vindicator counts as in combat");
                    foe.teleportTo(at.getX() + 4, at.getY(), at.getZ());
                    enteredAt[0] = t[0];
                    return;
                }
                int delay = t[0] - enteredAt[0];
                if (target == foe) {
                    delays.add(delay);
                    at.discard();
                    foe.discard();
                    pair[0] = pair[1] = null;
                    if (++trial[0] < TRIALS)
                        return;
                    done[0] = true;
                    cleanUp.run();
                    ReignOfNether.LOGGER.info("[TargetScan] idle units took an enemy that stepped into range after {} ticks"
                        + " (stagger {}, limit {})", delays, AttackerUnit.IDLE_AGGRO_TICKS, limit);
                    helper.succeed();
                    return;
                }
                if (delay > limit)
                    throw new GameTestAssertException("trial " + trial[0] + ": an idle Vindicator (id " + at.getId()
                        + ", tickCount " + at.tickCount + ") had not taken an enemy 4 blocks off after " + delay
                        + " ticks (limit " + limit + "; target " + describe(target) + ", idle " + unitOf(at).isIdle()
                        + ", earlier trials " + delays + ")");
            } catch (RuntimeException e) {
                if (!done[0]) {
                    done[0] = true;
                    cleanUp.run();
                }
                throw e;
            }
        });
        GameTestChunks.whenEntitiesReady(helper, level, chunks, 5, 200, 600, "idle stagger pad", () -> {
            BuildingGridGameTests.pad(level, ox, oz, ox + 32, oz + 32, Y, new Random(5), 0);
            ready[0] = true;
        });
    }

    /**
     * A Vindicator among friends (its own side, and an ally's) with a cow beside it and the only enemy 50 blocks away:
     * over 48 ticks it looks for a target on its idle scans and stops at the masks every time - not one gather. With
     * the enemy moved 12 blocks off (inside the cells round it - its reach plus TargetScan.PAD - but out of its
     * 10-block reach) it gathers and still takes nothing; at 5 blocks it takes the enemy.
     */
    @GameTest(template = ARENA, timeoutTicks = 700, batch = "target_scans")
    public static void target_scan_unit_with_no_hostile_near_does_not_query(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int[] o = origin(helper, 400, 47000);
        int ox = o[0], oz = o[1];
        List<long[]> chunks = BuildingGridGameTests.chunksOf(ox, oz, ox + 64, oz + 32);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "no-hostile pad");
        String a = owner(helper, "quiet_a"), b = owner(helper, "quiet_b"), c = owner(helper, "quiet_c");
        List<Entity> made = new ArrayList<>();
        boolean[] ready = { false }, done = { false };
        int[] t = { 0 };
        Mob[] cast = new Mob[2];   // the watched unit, the enemy
        long[][] probe = new long[1][];
        long[] seen = new long[4];   // skips and gathers at the end of phase 1, gathers at the end of phase 2
        Runnable cleanUp = () -> {
            if (cast[0] != null)
                TargetScan.unwatch(cast[0]);
            for (Entity e : made)
                if (!e.isRemoved())
                    e.discard();
            AlliancesServerEvents.removeAlliance(a, c);
            GameTestChunks.releaseAll(level, chunks);
        };
        helper.onEachTick(() -> {
            if (done[0] || !ready[0])
                return;
            try {
                Mob me = cast[0], foe = cast[1];
                int now = ++t[0];
                LivingEntity target = unitOf(me).getTargetGoal().getTarget();
                String state = " (skipped " + probe[0][0] + ", gathered " + probe[0][1] + ", sides on the level "
                    + TargetScan.sides() + ", target " + describe(target) + ")";
                if (now <= 48) {
                    if (probe[0][1] > 0 || target != null)
                        throw new GameTestAssertException("with no enemy within 50 blocks the unit gathered candidates at tick "
                            + now + state);
                    if (now == 48) {
                        if (probe[0][0] < 3)
                            throw new GameTestAssertException("the idle unit hardly looked for a target in 48 ticks" + state);
                        seen[0] = probe[0][0];
                        foe.teleportTo(me.getX() + 12, me.getY(), me.getZ());
                    }
                    return;
                }
                if (now <= 80) {
                    if (target != null)
                        throw new GameTestAssertException("the unit took " + describe(target) + " 12 blocks off, past its reach"
                            + state);
                    if (now == 80) {
                        if (probe[0][1] == 0)
                            throw new GameTestAssertException("an enemy in the cells round the unit did not make it gather" + state);
                        seen[1] = probe[0][1];
                        foe.teleportTo(me.getX() + 5, me.getY(), me.getZ());
                    }
                    return;
                }
                if (target == foe) {
                    done[0] = true;
                    ReignOfNether.LOGGER.info("[TargetScan] quiet unit: {} scans stopped at the masks with no enemy near, {}"
                        + " gathered with one 12 blocks off, took it at 5 blocks after {} ticks", seen[0], seen[1], now - 80);
                    cleanUp.run();
                    helper.succeed();
                    return;
                }
                if (now > 80 + AttackerUnit.IDLE_AGGRO_TICKS + MARGIN)
                    throw new GameTestAssertException("the unit did not take an enemy 5 blocks off" + state);
            } catch (RuntimeException e) {
                if (!done[0]) {
                    done[0] = true;
                    cleanUp.run();
                }
                throw e;
            }
        });
        GameTestChunks.whenEntitiesReady(helper, level, chunks, 5, 200, 500, "no-hostile pad", () -> {
            BuildingGridGameTests.pad(level, ox, oz, ox + 64, oz + 32, Y, new Random(7), 0);
            AlliancesServerEvents.addAlliance(a, c);
            double x = ox + 8.5, z = oz + 16.5;
            // friends north, south and west only, and an allied Vindicator: none of them reaches 12 blocks east of it
            Mob me = put(level, EntityRegistrar.VINDICATOR_UNIT.get(), a, x, Y + 1, z, made);
            for (int i = 0; i < 4; i++)
                put(level, EntityRegistrar.VINDICATOR_UNIT.get(), a, x - 2 * (i % 2), Y + 1, z + (i < 2 ? -2 : 2), made);
            put(level, EntityRegistrar.VINDICATOR_UNIT.get(), c, x - 2, Y + 1, z, made);
            Entity cow = EntityType.COW.create(level);
            if (cow != null) {
                cow.moveTo(x - 4, Y + 1, z, 0, 0);
                if (level.addFreshEntity(cow))
                    made.add(cow);
            }
            Mob foe = put(level, EntityRegistrar.VILLAGER_UNIT.get(), b, x + 50, Y + 1, z, made);
            if (me == null || foe == null) {
                done[0] = true;
                cleanUp.run();
                helper.fail("could not create the units");
                return;
            }
            cast[0] = me;
            cast[1] = foe;
            probe[0] = TargetScan.watch(me);
            ready[0] = true;
        });
    }

    /**
     * Six times, a Vindicator fights an enemy villager beside it (an ordinary target, kept alive) with a second enemy
     * 3.5 blocks off; then the first one is removed at once. The Vindicator is in combat (TargetScan.inCombat), so
     * every 4-tick pass until it takes the second enemy must be a scan (TargetScan.idleScanDue) - the stagger never
     * applies - and it takes the second enemy within 4 ticks and MARGIN.
     */
    @GameTest(template = ARENA, timeoutTicks = 900, batch = "target_scans")
    public static void target_scan_unit_fresh_from_a_fight_looks_on_every_pass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int[] o = origin(helper, 600, 47000);
        int ox = o[0], oz = o[1];
        List<long[]> chunks = BuildingGridGameTests.chunksOf(ox, oz, ox + 32, oz + 32);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "fresh-from-a-fight pad");
        String a = owner(helper, "fight_a"), b = owner(helper, "fight_b");
        int trials = 6, limit = 4 + MARGIN;
        List<Entity> made = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        boolean[] ready = { false }, done = { false };
        int[] trial = { 0 }, t = { 0 }, removedAt = { -1 }, passes = { 0 };
        Mob[] cast = new Mob[3];   // the attacker, its first enemy, the second
        Runnable cleanUp = () -> {
            for (Entity e : made)
                if (!e.isRemoved())
                    e.discard();
            made.clear();
            GameTestChunks.releaseAll(level, chunks);
        };
        helper.onEachTick(() -> {
            if (done[0] || !ready[0])
                return;
            try {
                t[0]++;
                if (cast[0] == null) {
                    double x = ox + 16.5, z = oz + 16.5;
                    Mob at = put(level, EntityRegistrar.VINDICATOR_UNIT.get(), a, x, Y + 1, z, made);
                    Mob first = put(level, EntityRegistrar.VILLAGER_UNIT.get(), b, x + 1.2, Y + 1, z, made);
                    Mob second = put(level, EntityRegistrar.VILLAGER_UNIT.get(), b, x, Y + 1, z + 3.5, made);
                    if (at == null || first == null || second == null)
                        throw new GameTestAssertException("could not create the trial's units");
                    ((AttackerUnit) at).setUnitAttackTarget(first);
                    cast[0] = at;
                    cast[1] = first;
                    cast[2] = second;
                    t[0] = 0;
                    removedAt[0] = -1;
                    passes[0] = 0;
                    return;
                }
                Mob at = cast[0], first = cast[1], second = cast[2];
                for (int i = 1; i <= 2; i++)
                    if (cast[i].isAlive())
                        cast[i].setHealth(cast[i].getMaxHealth());   // the fight never ends by itself
                if (removedAt[0] < 0) {
                    if (t[0] < 14 + trial[0])
                        return;
                    if (unitOf(at).getTargetGoal().getTarget() != first)
                        throw new GameTestAssertException("trial " + trial[0] + ": the Vindicator was not fighting its first"
                            + " enemy (target " + describe(unitOf(at).getTargetGoal().getTarget()) + ")");
                    first.discard();
                    removedAt[0] = t[0];
                    return;
                }
                LivingEntity target = unitOf(at).getTargetGoal().getTarget();
                int delay = t[0] - removedAt[0];
                if (target == second) {
                    delays.add(delay);
                    at.discard();
                    second.discard();
                    cast[0] = cast[1] = cast[2] = null;
                    if (++trial[0] < trials)
                        return;
                    done[0] = true;
                    cleanUp.run();
                    ReignOfNether.LOGGER.info("[TargetScan] units fresh from a fight took the next enemy after {} ticks (limit {})",
                        delays, limit);
                    helper.succeed();
                    return;
                }
                // the attacker's own 4-tick pass: it is in combat, so its idle look is due
                if (at.tickCount % 4 == 0) {
                    passes[0]++;
                    if (!TargetScan.inCombat(at) || !TargetScan.idleScanDue(at))
                        throw new GameTestAssertException("trial " + trial[0] + ": " + delay + " ticks after its enemy was"
                            + " removed the Vindicator was on the idle stagger (in combat " + TargetScan.inCombat(at) + ")");
                }
                if (delay > limit)
                    throw new GameTestAssertException("trial " + trial[0] + ": the Vindicator had not taken the second enemy"
                        + " " + delay + " ticks after the first was removed (limit " + limit + "; target " + describe(target)
                        + ", idle " + unitOf(at).isIdle() + ", passes " + passes[0] + ", earlier " + delays + ")");
            } catch (RuntimeException e) {
                if (!done[0]) {
                    done[0] = true;
                    cleanUp.run();
                }
                throw e;
            }
        });
        GameTestChunks.whenEntitiesReady(helper, level, chunks, 5, 200, 600, "fresh-from-a-fight pad", () -> {
            BuildingGridGameTests.pad(level, ox, oz, ox + 32, oz + 32, Y, new Random(11), 0);
            ready[0] = true;
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // [Stress] benchmark
    // ---------------------------------------------------------------------------------------------------------------

    /** Per side: 15 columns x 10 rows of idle units, 2 blocks apart. */
    static final int STRESS_COLS = 15, STRESS_ROWS = 10;
    /** Between the two idle fronts: past the Pillagers' 16-block reach, inside the grid cells round them. */
    static final int STRESS_GAP = 20;
    /** The brawl: 10 x 8, the two sides on a checkerboard, 51+ blocks south of the standoff. */
    static final int BRAWL_COLS = 10, BRAWL_ROWS = 8;
    static final int STRESS_WARMUP = 100, STRESS_PHASE = 40;
    /** Phase modes in order: 0 the old way (full scan, idle every 4), 1 the new scan with idle every 4, 2 as shipped. */
    static final int[] STRESS_MODES = { 0, 1, 2, 0, 1, 2, 0, 1, 2 };
    static final int MODES = 3;
    static final int STRESS_TIMEOUT = 3600;
    /** The [Sys] sections read per mode; the first five are the targeting cost. */
    static final int[] SYS_IDS = { Sys.ENTITY_SCAN, Sys.IDLE_AGGRO, Sys.ATTACK_MOVE, Sys.RETARGET, Sys.TARGET_GRID,
        Sys.BUILDING_SCAN, Sys.ATTACKER };
    static final int TARGETING = 5;

    static void applyMode(int mode) {
        TargetScan.legacyScans = mode == 0;
        TargetScan.idleAggroTicks = mode == 2 ? AttackerUnit.IDLE_AGGRO_TICKS : 4;
    }

    static String modeName(int mode) {
        return mode == 0 ? "before" : mode == 1 ? "new scan, idle @4" : "shipped (idle @" + AttackerUnit.IDLE_AGGRO_TICKS + ")";
    }

    /**
     * lovish's late game in miniature, on a pad of its own: two idle armies of 150 (Vindicators and Pillagers) facing
     * each other 20 blocks apart - an army waiting in base, the front rows in sight of each other's cells but out of
     * reach - and, 51 blocks south, an 80-unit brawl of the same two sides kept alive (healed every tick) so every phase
     * sees the same fight. After a warm-up it runs nine phases of 40 ticks - the old way, the new scan with the old
     * 4-tick idle cadence, and as shipped, three times over - and sums per mode vanilla's own tick times and the [Sys]
     * targeting sections (entityScan, idleAggro, attackMove, retarget, targetGrid; lifetime readings, so the 10-second
     * [Sys] windows do not cut them), counting only ticks that ran wholly in one mode and none while a metrics
     * recording runs (the automatic lag capture is held off). Then one direct pass of every unit's scan each way, and
     * every unit's answer both ways (the same, or an exact tie). Logged as "[Stress] target scans" (a CI notice). It
     * fails only when the scenario is not the one described (units missing, the standoff broke, the brawl stopped,
     * idle units not scanning) or the two scans disagree - never on the timings, which a shared CI runner moves; the
     * flake-stress workflow repeats it for a spread.
     */
    @GameTest(template = ARENA, timeoutTicks = STRESS_TIMEOUT, batch = "stress_target_scans")
    public static void stress_target_scans_idle_standoff_and_a_brawl(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        int[] o = origin(helper, 0, 52000);
        int cx = o[0] + 48, cz = o[1] + 16;
        List<long[]> chunks = BuildingGridGameTests.chunksOf(cx - 48, cz - 16, cx + 48, cz + 80);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "target scan stress");
        String a = owner(helper, "stress_a"), b = owner(helper, "stress_b");
        List<Entity> made = new ArrayList<>();
        List<Mob> idle = new ArrayList<>(), brawl = new ArrayList<>();
        boolean[] armed = { false }, done = { false };
        Runnable cleanUp = () -> {
            applyMode(2);
            Sys.force(false);
            ServerTickProfile.autoCaptureHeld = false;
            for (Entity e : made)
                if (!e.isRemoved())
                    e.discard();
            GameTestChunks.releaseAll(level, chunks);
        };
        Measure measure = new Measure(server);
        // registered from the test body: a callback that registers per-tick callbacks would add to the map
        // GameTestInfo is iterating
        helper.onEachTick(() -> {
            if (done[0])
                return;
            try {
                if (helper.getTick() >= STRESS_TIMEOUT - 40) {
                    done[0] = true;
                    cleanUp.run();
                    throw new GameTestAssertException("the stress scene " + (armed[0] ? "ran out of time after "
                        + measure.t + " measured ticks" : "was never set up"));
                }
                if (!armed[0])
                    return;
                for (Mob m : brawl)
                    if (m.isAlive())
                        m.setHealth(m.getMaxHealth());   // the brawl never thins out: every phase sees the same fight
                if (measure.tick()) {
                    done[0] = true;
                    applyMode(2);
                    try {
                        stressVerdict(helper, level, idle, brawl, measure);
                    } finally {
                        cleanUp.run();
                    }
                }
            } catch (RuntimeException e) {
                if (!done[0]) {
                    done[0] = true;
                    cleanUp.run();
                }
                throw e;
            }
        });
        List<long[]> unitChunks = BuildingGridGameTests.chunksOf(cx - 40, cz - 12, cx + 40, cz + 76);
        GameTestChunks.whenEntitiesReady(helper, level, unitChunks, 5, 200, 1200, "target scan stress units", () -> {
            try {
                BuildingGridGameTests.pad(level, cx - 48, cz - 16, cx + 48, cz + 80, Y, new Random(3), 0);
                for (int side = 0; side < 2; side++)
                    for (int col = 0; col < STRESS_COLS; col++)
                        for (int row = 0; row < STRESS_ROWS; row++) {
                            double x = side == 0 ? cx - STRESS_GAP / 2.0 - 2 * col : cx + STRESS_GAP / 2.0 + 2 * col;
                            Mob m = put(level, (col + row) % 3 == 0 ? EntityRegistrar.PILLAGER_UNIT.get()
                                : EntityRegistrar.VINDICATOR_UNIT.get(), side == 0 ? a : b, x + 0.5, Y + 1,
                                cz - 9 + 2 * row + 0.5, made);
                            if (m != null)
                                idle.add(m);
                        }
                for (int col = 0; col < BRAWL_COLS; col++)
                    for (int row = 0; row < BRAWL_ROWS; row++) {
                        Mob m = put(level, row % 2 == 0 ? EntityRegistrar.VINDICATOR_UNIT.get() : EntityRegistrar.PILLAGER_UNIT.get(),
                            (col + row) % 2 == 0 ? a : b, cx - 9 + 2 * col + 0.5, Y + 1, cz + 60 + 2 * row + 0.5, made);
                        if (m != null)
                            brawl.add(m);
                    }
                int want = 2 * STRESS_COLS * STRESS_ROWS + BRAWL_COLS * BRAWL_ROWS;
                if (made.size() < want * 95 / 100) {
                    done[0] = true;
                    cleanUp.run();
                    helper.fail("stress scenario incomplete: " + made.size() + " of " + want + " units");
                    return;
                }
                ServerTickProfile.autoCaptureHeld = true;
                Sys.force(true);
                applyMode(2);
                armed[0] = true;
            } catch (RuntimeException e) {
                if (!done[0]) {
                    done[0] = true;
                    cleanUp.run();
                }
                throw e;
            }
        });
    }

    /**
     * The per-tick bookkeeping of the stress benchmark. A test callback runs after the levels have ticked: what the
     * [Sys] sections spent since the previous callback is the level work of one tick, run in the mode the previous
     * callback set; vanilla's time for the previous tick (MinecraftServer.tickTimes) is counted when the mode was the
     * same at both callbacks round it, as in the building-scan benchmark.
     */
    static final class Measure {
        final MinecraftServer server;
        final int callbacks = STRESS_WARMUP + STRESS_MODES.length * STRESS_PHASE + 1;
        final int[] phaseAt = new int[callbacks + 1];
        final long[] phaseTickNanos = new long[STRESS_MODES.length];
        final int[] phaseTicks = new int[STRESS_MODES.length];
        final long[][] sysNanos = new long[MODES][SYS_IDS.length], sysCalls = new long[MODES][SYS_IDS.length];
        final int[] sysTicks = new int[MODES];
        final long[] skips = new long[MODES], gathers = new long[MODES];
        final long[] lastNanos = new long[SYS_IDS.length], lastCalls = new long[SYS_IDS.length];
        long lastSkips, lastGathers;
        int t = 0, recordingTicks = 0, rebuildsBefore = 0;

        Measure(MinecraftServer server) {
            this.server = server;
        }

        static int phaseOf(int t) {
            if (t < STRESS_WARMUP)
                return -1;
            return Math.min(STRESS_MODES.length - 1, (t - STRESS_WARMUP) / STRESS_PHASE);
        }

        /** One callback; true when the last phase is over. */
        boolean tick() {
            int now = t++;
            boolean recording = server.isRecordingMetrics();
            if (recording)
                recordingTicks++;
            long sk = TargetScan.skips, ga = TargetScan.gathers;
            for (int i = 0; i < SYS_IDS.length; i++) {
                long[] life = Sys.lifetime(SYS_IDS[i]);
                if (now >= 1 && phaseAt[now - 1] >= 0 && !recording) {
                    int mode = STRESS_MODES[phaseAt[now - 1]];
                    sysCalls[mode][i] += life[0] - lastCalls[i];
                    sysNanos[mode][i] += life[1] - lastNanos[i];
                }
                lastCalls[i] = life[0];
                lastNanos[i] = life[1];
            }
            if (now >= 1 && phaseAt[now - 1] >= 0 && !recording) {
                int mode = STRESS_MODES[phaseAt[now - 1]];
                sysTicks[mode]++;
                skips[mode] += sk - lastSkips;
                gathers[mode] += ga - lastGathers;
            }
            lastSkips = sk;
            lastGathers = ga;
            if (now >= 2 && phaseAt[now - 2] >= 0 && phaseAt[now - 2] == phaseAt[now - 1] && !recording) {
                int p = phaseAt[now - 2];
                phaseTickNanos[p] += server.tickTimes[Math.floorMod(server.getTickCount() - 1, server.tickTimes.length)];
                phaseTicks[p]++;
            }
            if (now == 0)
                rebuildsBefore = TargetScan.rebuilds();
            if (now == callbacks - 1)
                return true;
            int p = phaseOf(now);
            phaseAt[now] = p;
            applyMode(p < 0 ? 2 : STRESS_MODES[p]);   // the warm-up runs as shipped
            return false;
        }

        double tickMs(int mode) {
            long nanos = 0;
            int ticks = 0;
            for (int p = 0; p < STRESS_MODES.length; p++)
                if (STRESS_MODES[p] == mode) {
                    nanos += phaseTickNanos[p];
                    ticks += phaseTicks[p];
                }
            return ticks == 0 ? Double.NaN : nanos / 1e6 / ticks;
        }

        String phaseMs(int mode) {
            List<String> out = new ArrayList<>();
            for (int p = 0; p < STRESS_MODES.length; p++)
                if (STRESS_MODES[p] == mode)
                    out.add(phaseTicks[p] == 0 ? "-" : String.format(Locale.ROOT, "%.1f", phaseTickNanos[p] / 1e6 / phaseTicks[p]));
            return String.join("/", out);
        }

        /** Self milliseconds per tick of the targeting sections (the first TARGETING of SYS_IDS), or of one of them. */
        double sysMs(int mode, int from, int to) {
            long nanos = 0;
            for (int i = from; i < to; i++)
                nanos += sysNanos[mode][i];
            return sysTicks[mode] == 0 ? Double.NaN : nanos / 1e6 / sysTicks[mode];
        }

        double perTick(int mode, long count) {
            return sysTicks[mode] == 0 ? Double.NaN : count / (double) sysTicks[mode];
        }
    }

    static void stressVerdict(GameTestHelper helper, ServerLevel level, List<Mob> idle, List<Mob> brawl, Measure m) {
        List<String> problems = new ArrayList<>();
        for (int p = 0; p < STRESS_MODES.length; p++)
            if (m.phaseTicks[p] < STRESS_PHASE / 2)
                problems.add("phase " + p + " counted only " + m.phaseTicks[p] + " ticks");
        // the scenario: the standoff stood (nobody idle picked anything), the brawl fought, idle units scanned
        int broke = 0, idleAlive = 0;
        for (Mob u : idle) {
            if (!u.isAlive())
                continue;
            idleAlive++;
            if (u.getTarget() != null || unitOf(u).getTargetGoal().getTarget() != null
                    || ((AttackerUnit) u).getAttackMoveTarget() != null)
                broke++;
        }
        if (broke > 0)
            problems.add(broke + " of " + idleAlive + " standoff units picked a target: the fronts must stay out of reach");
        int fighting = 0;
        for (Mob u : brawl)
            if (unitOf(u).hasLivingTarget())
                fighting++;
        if (fighting < brawl.size() / 2)
            problems.add("only " + fighting + " of " + brawl.size() + " brawl units were fighting at the end");
        for (int mode = 0; mode < MODES; mode++) {
            int every = mode == 2 ? AttackerUnit.IDLE_AGGRO_TICKS : 4;
            double scans = m.perTick(mode, m.sysCalls[mode][0]), expected = idleAlive / (double) every;
            if (scans < expected * 0.6)
                problems.add(String.format(Locale.ROOT, "%s: %.1f entity scans per tick, expected at least ~%.0f (idle units not scanning?)",
                    modeName(mode), scans, expected));
        }
        // one direct pass of every unit's scan each way, and the same answers
        List<Mob> all = new ArrayList<>();
        for (Mob u : idle)
            if (u.isAlive())
                all.add(u);
        for (Mob u : brawl)
            if (u.isAlive())
                all.add(u);
        long[] passNanos = new long[2];
        for (int way = 0; way < 2; way++)
            for (int rep = 0; rep < 3; rep++) {
                long t0 = System.nanoTime();
                for (Mob u : all) {
                    float r = ((AttackerUnit) u).getAggroRange();
                    if (way == 0)
                        TargetScansLegacy.findClosestAttackableEntity(u, r, level);
                    else
                        TargetScan.findClosestAttackableEntity(u, r, level);
                }
                if (rep > 0)
                    passNanos[way] += System.nanoTime() - t0;
            }
        int different = 0, ties = 0, found = 0;
        for (Mob u : all) {
            float r = ((AttackerUnit) u).getAggroRange();
            LivingEntity full = TargetScansLegacy.findClosestAttackableEntity(u, r, level);
            LivingEntity grid = TargetScan.findClosestAttackableEntity(u, r, level);
            if (full != null)
                found++;
            if (full == grid)
                continue;
            if (full != null && grid != null && key(u, full) == key(u, grid))
                ties++;
            else {
                if (different++ < 3)
                    problems.add("the scans disagree for " + describe(u) + ": full " + describe(full) + ", grid " + describe(grid));
            }
        }
        if (different > 3)
            problems.add((different - 3) + " more units where the scans disagree");
        StringBuilder modes = new StringBuilder();
        for (int mode = 0; mode < MODES; mode++)
            modes.append(String.format(Locale.ROOT, " | %s: %.2f ms/tick (%s), targeting %.2f ms/tick [entityScan %.2f, %.0f/t;"
                    + " idleAggro %.2f; attackMove %.2f; retarget %.2f; targetGrid %.3f], buildingScan %.2f, attacker %.2f,"
                    + " masks stopped %.1f/t, gathered %.1f/t",
                modeName(mode), m.tickMs(mode), m.phaseMs(mode), m.sysMs(mode, 0, TARGETING), m.sysMs(mode, 0, 1),
                m.perTick(mode, m.sysCalls[mode][0]), m.sysMs(mode, 1, 2), m.sysMs(mode, 2, 3), m.sysMs(mode, 3, 4),
                m.sysMs(mode, 4, 5), m.sysMs(mode, 5, 6), m.sysMs(mode, 6, 7), m.perTick(mode, m.skips[mode]),
                m.perTick(mode, m.gathers[mode])));
        double before = m.tickMs(0), shipped = m.tickMs(2);
        ReignOfNether.LOGGER.info(String.format(Locale.ROOT,
            "[Stress] target scans: %d units (%d idle in a standoff %d apart, %d in a brawl)%s | shipped vs before: tick %+.2f ms"
                + " (x%.3f), targeting %+.2f ms | direct pass of %d scans: full %.2f ms, grid %.2f ms | same answers %d of %d"
                + " (%d exact ties, %d found a target) | grid rebuilds %d, %d entries | ticks under a metrics recording %d",
            all.size(), idleAlive, STRESS_GAP, brawl.size(), modes, shipped - before, before > 0 ? shipped / before : Double.NaN,
            m.sysMs(2, 0, TARGETING) - m.sysMs(0, 0, TARGETING), all.size(), passNanos[0] / 2e6, passNanos[1] / 2e6,
            all.size() - different, all.size(), ties, found, TargetScan.rebuilds() - m.rebuildsBefore, TargetScan.cellEntries(),
            m.recordingTicks));
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
