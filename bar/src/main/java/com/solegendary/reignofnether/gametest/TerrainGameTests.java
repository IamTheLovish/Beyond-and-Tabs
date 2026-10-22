package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.Engage;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.StuckNudge;
import com.solegendary.reignofnether.unit.Terrain;
import com.solegendary.reignofnether.unit.UnitFormations;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Headless tests of terrain intelligence (design/terrain_intelligence.md): wading slows and weakens a unit; a chase
 * never follows a target into water; melee attackers ring their target; shooters do not stack; a move formation is a
 * line facing the move; a chaser that makes no progress is nudged and then gives up. Each test runs on open ground
 * in its own arena column (SkirmishGameTests.openGround) with unique owners; the formation test, which needs 24 blocks
 * of run, lays its own glass pad at a height no other test uses. Every unit is discarded in a finally.
 * Diagnostics go out as "[Melee]" lines, which CI folds into one readable notice.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerrainGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_terrain_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double y, double z,
                     List<Entity> made) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(x, y, z, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        if (m instanceof Unit u)
            AutocastState.setAll(u, false);
        made.add(m);
        return m;
    }

    /** A target that stands still and cannot die: no AI, invulnerable. */
    static Mob dummy(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double y, double z,
                     List<Entity> made) {
        Mob m = spawn(level, type, owner, x, y, z, made);
        if (m != null) {
            m.setNoAi(true);
            m.setInvulnerable(true);
        }
        return m;
    }

    static void cleanUp(List<Entity> made) {
        for (Entity e : made)
            if (!e.isRemoved())
                e.discard();
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static String f(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    static double horizontal(Entity a, Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A 1-deep pool: stone at template y=0, water at y=1, air above (the arena's standing height is y=2). */
    static void pool(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        ServerLevel level = helper.getLevel();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.WATER.defaultBlockState(), 3);
                for (int y = 2; y <= 4; y++)
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 3);
            }
    }

    /**
     * Whether the pool's water is really there (CI 38512850549 ran the bank-hold test on a dry pad: every setBlock of
     * the pool read back as stone, the same run that lost a freshly added entity in another test - a chunk-state
     * hiccup under eight parallel runs). A dry pool fails the test with a line that says so, not with a wrong verdict.
     */
    static String poolMissing(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        ServerLevel level = helper.getLevel();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                BlockPos p = helper.absolutePos(new BlockPos(x, 1, z));
                if (!Terrain.isWet(level, p))
                    return "the pool did not take: " + level.getBlockState(p) + " at template " + x + "/1/" + z
                        + " (chunk loaded " + level.hasChunkAt(p) + ")";
            }
        return null;
    }

    static double x(GameTestHelper helper, int tx) { return helper.absolutePos(new BlockPos(tx, 0, 0)).getX() + 0.5; }
    static double z(GameTestHelper helper, int tz) { return helper.absolutePos(new BlockPos(0, 0, tz)).getZ() + 0.5; }
    static int y(GameTestHelper helper, int ty) { return helper.absolutePos(new BlockPos(0, ty, 0)).getY(); }

    // ------------------------------------------------------------------------------------------- 1. water is a decision

    /**
     * A Pillager wading a 1-deep pool covers between a quarter and three quarters of what one on land covers in 60
     * ticks (half its land speed once up to speed; vanilla's own water crawl would be about a fifth), and its hit
     * lands for 75% of the land Pillager's on a plain zombie.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void wading_unit_moves_at_half_speed_and_hits_for_three_quarters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        pool(helper, 1, 2, 14, 4);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "wade");
        Mob wader = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), owner, x(helper, 2), y(helper, 1), z(helper, 3), made);
        Mob walker = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), owner, x(helper, 2), y(helper, 2), z(helper, 9), made);
        // husks: a plain zombie body that does not burn in daylight, so its health only moves when hit
        Mob zombieA = spawn(level, EntityType.HUSK, owner, x(helper, 14), y(helper, 2), z(helper, 13), made);
        Mob zombieB = spawn(level, EntityType.HUSK, owner, x(helper, 11), y(helper, 2), z(helper, 13), made);
        if (wader == null || walker == null || zombieA == null || zombieB == null) {
            cleanUp(made);
            helper.fail("could not create the pillagers and zombies");
            return;
        }
        for (Mob z : List.of(zombieA, zombieB)) {
            z.setNoAi(true);
            z.setHealth(z.getMaxHealth());
        }
        // hold fire: the pillagers must walk, not stop to shoot the husks they are in range of
        EngagementStance.set(wader, EngagementStance.HOLD_FIRE);
        EngagementStance.set(walker, EngagementStance.HOLD_FIRE);
        double startWader = wader.getX(), startWalker = walker.getX();
        helper.runAfterDelay(2, () -> {
            String dry = poolMissing(helper, 1, 2, 14, 4);
            if (dry != null)
                problems.add(dry);
            if (!Terrain.isWading(wader))
                problems.add("the pillager in the pool is not wading (inWater " + wader.isInWater() + ")");
            if (Terrain.isWading(walker))
                problems.add("the pillager on land is wading");
            ((Unit) wader).getMoveGoal().setMoveTarget(helper.absolutePos(new BlockPos(13, 1, 3)));
            ((Unit) walker).getMoveGoal().setMoveTarget(helper.absolutePos(new BlockPos(13, 2, 9)));
        });
        helper.runAfterDelay(62, () -> {
            try {
                double wade = wader.getX() - startWader, walk = walker.getX() - startWalker;
                double ratio = walk > 0.01 ? wade / walk : -1;
                ReignOfNether.LOGGER.info("[Melee] terrain: 60 ticks on land {} blocks, wading {} blocks (ratio {}), friction {}",
                    f(walk), f(wade), f(ratio), f(Terrain.waterFriction(wader, 0.8f)));
                if (walk < 2.0)
                    problems.add("the land pillager only moved " + f(walk) + " blocks in 60 ticks");
                else if (ratio < 0.25 || ratio > 0.75)
                    problems.add("the wading pillager moved " + f(wade) + " blocks to the land one's " + f(walk) + " (ratio "
                        + f(ratio) + ", expected 0.25-0.75)");
                // damage: one hit each on a plain zombie (no armour, not a unit, not an animal). The rule is "while
                // wading", so the wader must still be in the pool when it swings: its path sometimes climbs out
                // over the bank inside the 60 ticks (CI 38598416760: "7.00 to the land one's 7.00") - put it back
                Runnable hit = () -> {
                    try {
                        float hpA = zombieA.getHealth(), hpB = zombieB.getHealth();
                        zombieA.hurt(level.damageSources().mobAttack(walker), 1f);
                        zombieB.hurt(level.damageSources().mobAttack(wader), 1f);
                        float land = hpA - zombieA.getHealth(), water = hpB - zombieB.getHealth();
                        if (land <= 0)
                            problems.add("the land pillager's hit did not land");
                        else if (Math.abs(water / land - Terrain.WADING_DAMAGE) > 0.03)
                            problems.add("the wading pillager hit for " + f(water) + " to the land one's " + f(land)
                                + " (expected " + Terrain.WADING_DAMAGE + "x)");
                        finish(helper, problems);
                    } finally {
                        cleanUp(made);
                    }
                };
                if (Terrain.isWading(wader)) {
                    hit.run();
                    return;
                }
                ReignOfNether.LOGGER.info("[Melee] terrain: the wader left the pool at {} - back in for the hit", wader.blockPosition().toShortString());
                // drop its order too: with the move target and any path still in flight the wader can be steered
                // straight back out (build of 9dc330d failed here after the 3-tick wait of 1af1628)
                ((Unit) wader).getMoveGoal().stopMoving();
                wader.teleportTo(x(helper, 2), y(helper, 1), z(helper, 3));
                wader.setDeltaMovement(0, 0, 0);
                // an entity's in-water flag is refreshed by its own next tick, not by a teleport (build of 0e6f342:
                // "put back in the pool is not wading (inWater false)", then a dry 7.00 hit) - poll until it is
                // wading (bounded), and say where it stands if it never is
                awaitWading(helper, wader, 20, () -> hit.run(), () -> {
                    problems.add("the pillager put back in the pool is not wading after 20 ticks: " + whereIs(wader));
                    hit.run();
                });
            } catch (RuntimeException e) {
                cleanUp(made);
                throw e;
            }
        });
    }

    /** Runs {@code then} on the first tick {@code mob} is wading, or {@code otherwise} after {@code ticksLeft} ticks. */
    static void awaitWading(GameTestHelper helper, Mob mob, int ticksLeft, Runnable then, Runnable otherwise) {
        helper.runAfterDelay(1, () -> {
            if (Terrain.isWading(mob))
                then.run();
            else if (ticksLeft <= 1)
                otherwise.run();
            else
                awaitWading(helper, mob, ticksLeft - 1, then, otherwise);
        });
    }

    /** Where a mob stands, for a failure message: position, the block and fluid at its feet, ground, vehicle. */
    static String whereIs(Mob mob) {
        BlockPos feet = mob.blockPosition();
        return String.format("at %.2f %.2f %.2f, feet %s, fluid %s, inWater %s, onGround %s, vehicle %s, moving %s",
            mob.getX(), mob.getY(), mob.getZ(), mob.level().getBlockState(feet).getBlock().getDescriptionId(),
            mob.level().getFluidState(feet).getType(), mob.isInWater(), mob.onGround(), mob.getVehicle(),
            mob.getDeltaMovement());
    }

    // --------------------------------------------------------------------------------------- 2. chases respect terrain

    /**
     * A Vindicator attack-moved to the bank of a 4-wide river acquires the enemy standing two blocks into the water
     * and waits on the bank - feet dry, within a few blocks - instead of wading in after it; when the enemy steps
     * out onto the bank the Vindicator engages it.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void melee_chase_holds_the_bank_for_a_wading_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        // the river: x 8..11, one block in from the arena's edges - at z 0 and 15 the water touched whatever lies
        // beyond the template; when that is air it flows out and the current carries the no-AI villager off its
        // cell (CI 38562424223: "3.20 from the foe, foe wading false" after 120 ticks)
        pool(helper, 8, 1, 11, 14);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "bank_a"), theirs = owner(helper, "bank_b");
        Mob vind = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, x(helper, 2), y(helper, 2), z(helper, 7), made);
        Mob foe = dummy(level, EntityRegistrar.VILLAGER_UNIT.get(), theirs, x(helper, 9), y(helper, 1), z(helper, 7), made);
        if (vind == null || foe == null) {
            cleanUp(made);
            helper.fail("could not create the vindicator and villager");
            return;
        }
        foe.setInvulnerable(false);
        foe.setHealth(foe.getMaxHealth());
        helper.runAfterDelay(2, () -> {
            String dry = poolMissing(helper, 8, 1, 11, 14);
            if (dry != null)
                problems.add(dry);
            ((AttackerUnit) vind).setAttackMoveTarget(helper.absolutePos(new BlockPos(6, 2, 7)));
        });
        helper.runAfterDelay(120, () -> {
            String where = "vindicator at " + f(vind.getX() - x(helper, 0) + 0.5) + "/" + f(vind.getZ() - z(helper, 0) + 0.5)
                + " (template x/z), feet in water " + Terrain.feetInWater(vind) + ", target "
                + (vind.getTarget() == null ? "none" : vind.getTarget().getType().toShortString())
                + ", " + f(horizontal(vind, foe)) + " from the foe, foe wading " + Terrain.isWading(foe)
                // CI 38572219723 had it dry but at 7/10 (3.58 off) after passing the run before: say what the chase and
                // the stuck nudge did so the next such run explains itself
                + "; approach " + Engage.lastApproach(vind) + ", settled " + Engage.settled(vind)
                + ", nav done " + vind.getNavigation().isDone() + ", last nudge " + StuckNudge.lastNudge(vind)
                + " (now " + level.getGameTime() + "), give-ups " + StuckNudge.giveUps(vind);
            ReignOfNether.LOGGER.info("[Melee] terrain: bank hold after 120 ticks: {}", where);
            if (vind.getTarget() != foe)
                problems.add("the vindicator did not acquire the wading villager: " + where);
            if (Terrain.feetInWater(vind))
                problems.add("the vindicator followed the villager into the river: " + where);
            if (horizontal(vind, foe) > 3.0)   // the bank cell is 2 blocks from the foe; 3 would be the cell behind it
                problems.add("the vindicator is not waiting at the water's edge: " + where);
            if (foe.getHealth() < foe.getMaxHealth())
                problems.add("the wading villager was hit from the bank: " + where);
            // the villager steps out onto the west bank, 3 blocks from the river
            foe.teleportTo(x(helper, 5), y(helper, 2), z(helper, 9));
        });
        helper.runAfterDelay(220, () -> {
            try {
                String where = "vindicator " + f(horizontal(vind, foe)) + " from the foe, foe health " + f(foe.getHealth())
                    + "/" + f(foe.getMaxHealth()) + ", feet in water " + Terrain.feetInWater(vind);
                ReignOfNether.LOGGER.info("[Melee] terrain: after the foe stepped out: {}", where);
                if (foe.getHealth() >= foe.getMaxHealth())
                    problems.add("the vindicator did not engage the villager once it stepped out: " + where);
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    // ------------------------------------------------------------------------------------------- 3. engage with spacing

    /**
     * Six Vindicators ordered onto one target stand round it, not in it: by 100 ticks (up to 180 on a slow server) their
     * mean pairwise distance is above 1.0 block (it was ~0.3 when every attacker pathed to the target's own cell) and
     * at least five of the six are within melee reach of it. The ticks count from the order, which is given once every
     * unit has run its own first ticks (ArenaReady); the six are invulnerable, so a neighbouring test's stray blow cannot
     * knock one out of the ring.
     *
     * The first version of the retry (build 957db3b: two of six still walking in at 100 ticks) re-scheduled ONE Runnable
     * from inside itself with runAfterDelay. GameTestInfo runs a due Runnable and then removes its entry from the same
     * map the re-schedule just wrote to, so the retry never came: every red run of this test (Oct 15 on) was the plain
     * timeout "Didn't succeed or fail within 160 ticks", none a verdict. It now polls from onEachTick.
     */
    @GameTest(template = ARENA, timeoutTicks = 420)
    public static void six_melee_attackers_ring_their_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "ring_a"), theirs = owner(helper, "ring_b");
        Mob foe = dummy(level, EntityRegistrar.VILLAGER_UNIT.get(), theirs, x(helper, 8), y(helper, 2), z(helper, 10), made);
        List<Mob> attackers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Mob m = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, x(helper, 3 + i * 2), y(helper, 2), z(helper, 3), made);
            if (m != null) {
                m.setInvulnerable(true);
                attackers.add(m);
            }
        }
        if (foe == null || attackers.size() != 6) {
            cleanUp(made);
            helper.fail("could not create the vindicators and villager");
            return;
        }
        int[] ordered = { -1 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 200, tick -> {
            ordered[0] = tick;
            for (Mob m : attackers)
                ((AttackerUnit) m).setUnitAttackTargetForced(foe);
        });
        // judged 100 ticks after the order and, while not yet ringed, on every tick up to 180: on a loaded CI server
        // two of six were still walking in at 100 ticks (4.85 and 2.98 blocks out) - the claim is where they end up,
        // not how fast a slow server moves them
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            int since = (int) helper.getTick() - ordered[0];
            if (since < 100)
                return;
            double sum = 0;
            int pairs = 0, inReach = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < attackers.size(); i++) {
                Mob a = attackers.get(i);
                double d = horizontal(a, foe);
                if (d <= 1.8)
                    inReach++;
                sb.append(i > 0 ? ", " : "").append(f(d));
                for (int j = i + 1; j < attackers.size(); j++) {
                    sum += horizontal(a, attackers.get(j));
                    pairs++;
                }
            }
            double mean = sum / pairs;
            if ((mean <= 1.0 || inReach < 5) && since < 180)
                return;
            done[0] = true;
            try {
                ReignOfNether.LOGGER.info("[Melee] terrain: 6 vs 1 after {} ticks (ordered at test tick {}): mean pairwise {} blocks, distances to the target {}, slot claims {}",
                    since, ordered[0], f(mean), sb, Engage.claims(foe));
                if (mean <= 1.0)
                    problems.add("the six attackers stand " + f(mean) + " apart on average (distances to the target " + sb + ")");
                if (inReach < 5)
                    problems.add("only " + inReach + " of 6 attackers are within 1.8 blocks of the target (" + sb + "); "
                        + ArenaReady.describe(level, attackers));
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /** Two crossbows spawned on one block, both in range of a target, end up at least 0.8 blocks apart: one stepped aside. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void stacked_shooters_spread_sideways(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "spread_a"), theirs = owner(helper, "spread_b");
        Mob foe = dummy(level, EntityRegistrar.VILLAGER_UNIT.get(), theirs, x(helper, 13), y(helper, 2), z(helper, 8), made);
        Mob a = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), mine, x(helper, 3), y(helper, 2), z(helper, 8), made);
        Mob b = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), mine, x(helper, 3) + 0.1, y(helper, 2), z(helper, 8), made);
        if (foe == null || a == null || b == null) {
            cleanUp(made);
            helper.fail("could not create the pillagers and villager");
            return;
        }
        helper.runAfterDelay(2, () -> {
            ((AttackerUnit) a).setUnitAttackTargetForced(foe);
            ((AttackerUnit) b).setUnitAttackTargetForced(foe);
        });
        helper.runAfterDelay(82, () -> {
            try {
                double d = horizontal(a, b);
                ReignOfNether.LOGGER.info("[Melee] terrain: two stacked crossbows after 80 ticks: {} apart, {} and {} from the target",
                    f(d), f(horizontal(a, foe)), f(horizontal(b, foe)));
                if (d < 0.8)
                    problems.add("the two crossbows still stand " + f(d) + " apart");
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    // ----------------------------------------------------------------------------------------------- 4. formation spacing

    /** A height no other test builds at (SkirmishGameTests' sky pads run 109-312 with gaps; 279 sits in one). */
    static final int FORMATION_PAD_Y = 279;

    /**
     * Twelve Vindicators moved 24 blocks east arrive in a block wider across the move than deep along it, no two
     * closer than a body (0.55) and the mean nearest-neighbour distance at least 0.8: a line facing the move, not a
     * spiral they stack inside. The pad runs 8 blocks west and 24 east of the arena column at its own height.
     */
    @GameTest(template = ARENA, timeoutTicks = 520)
    public static void formation_move_spreads_into_a_line_facing_the_move(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        int cx0 = corner.getX() - 8, cx1 = corner.getX() + 40, cz0 = corner.getZ(), cz1 = corner.getZ() + 15;
        List<long[]> chunks = new ArrayList<>();
        for (int cx = cx0 >> 4; cx <= cx1 >> 4; cx++)
            for (int cz = cz0 >> 4; cz <= cz1 >> 4; cz++)
                chunks.add(new long[] { cx, cz });
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "formation pad");
        for (int x = cx0; x <= cx1; x++)
            for (int z = cz0; z <= cz1; z++) {
                level.setBlock(new BlockPos(x, FORMATION_PAD_Y, z), Blocks.GLASS.defaultBlockState(), 2);
                for (int y = 1; y <= 3; y++)
                    level.setBlock(new BlockPos(x, FORMATION_PAD_Y + y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        int standY = FORMATION_PAD_Y + 1;
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "line");
        List<LivingEntity> units = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Mob m = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, corner.getX() + 2 + (i % 4) * 1.5, standY,
                corner.getZ() + 5 + (i / 4) * 2.0, made);
            if (m != null)
                units.add(m);
        }
        if (units.size() != 12) {
            cleanUp(made);
            GameTestChunks.releaseAll(level, chunks);
            helper.fail("could not create the twelve vindicators");
            return;
        }
        BlockPos target = new BlockPos(corner.getX() + 28, standY, corner.getZ() + 7);
        helper.runAfterDelay(2, () -> {
            List<Pair<LivingEntity, BlockPos>> formation = UnitFormations.getMoveFormation(level, new ArrayList<>(units), target);
            for (Pair<LivingEntity, BlockPos> p : formation)
                ((Unit) p.getFirst()).getMoveGoal().setMoveTarget(p.getSecond());
        });
        helper.runAfterDelay(420, () -> {
            try {
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                double closest = Double.MAX_VALUE, nnSum = 0;
                int arrived = 0;
                for (LivingEntity a : units) {
                    minX = Math.min(minX, a.getX());
                    maxX = Math.max(maxX, a.getX());
                    minZ = Math.min(minZ, a.getZ());
                    maxZ = Math.max(maxZ, a.getZ());
                    if (a.getX() >= corner.getX() + 22)
                        arrived++;
                    double nn = Double.MAX_VALUE;
                    for (LivingEntity b : units)
                        if (b != a)
                            nn = Math.min(nn, horizontal(a, b));
                    closest = Math.min(closest, nn);
                    nnSum += nn;
                }
                double depth = maxX - minX, width = maxZ - minZ, nnMean = nnSum / units.size();
                String shape = "arrived " + arrived + "/12, extent along the move " + f(depth) + " x across " + f(width)
                    + ", closest pair " + f(closest) + ", mean nearest neighbour " + f(nnMean);
                ReignOfNether.LOGGER.info("[Melee] terrain: 12 units moved 24 blocks: {}", shape);
                if (arrived < 12)
                    problems.add("not everyone arrived: " + shape);
                if (width <= depth)
                    problems.add("the group is not wider than deep: " + shape);
                if (closest < 0.55)
                    problems.add("two units stand inside each other: " + shape);
                if (nnMean < 0.8)
                    problems.add("the units are packed too tightly: " + shape);
                finish(helper, problems);
            } finally {
                cleanUp(made);
                GameTestChunks.releaseAll(level, chunks);
            }
        });
    }

    // ----------------------------------------------------------------------------------------------------- 5. stuck nudge

    /**
     * A Vindicator that cannot move (movement speed 0) chasing a target six blocks away is nudged - a re-path, a
     * sidestep - and then drops the target, once, within 200 ticks; on hold fire it does not pick it up again. A
     * second Vindicator that can move reaches its own target and is never nudged.
     *
     * The 300 ticks count from the order, which is given once all four bodies have run their own first ticks
     * (ArenaReady), and both vindicators are invulnerable so a neighbouring test's stray blow cannot knock one off its
     * line. A red run says where each body stood, whether it ticked and what its chase was doing ({@link #chaseState}).
     */
    @GameTest(template = ARENA, timeoutTicks = 560)
    public static void stuck_chaser_is_nudged_then_drops_its_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int padY = ArenaReady.pad(helper, 135).getY();   // its own floor: no neighbouring test's unit may reach it
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "stuck_a"), theirs = owner(helper, "stuck_b");
        Mob stuck = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, x(helper, 2), padY, z(helper, 3), made);
        Mob foeA = dummy(level, EntityRegistrar.VILLAGER_UNIT.get(), theirs, x(helper, 8), padY, z(helper, 3), made);
        Mob free = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, x(helper, 2), padY, z(helper, 12), made);
        Mob foeB = dummy(level, EntityRegistrar.VILLAGER_UNIT.get(), theirs, x(helper, 8), padY, z(helper, 12), made);
        if (stuck == null || foeA == null || free == null || foeB == null) {
            cleanUp(made);
            helper.fail("could not create the vindicators and villagers");
            return;
        }
        stuck.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        stuck.setInvulnerable(true);
        free.setInvulnerable(true);
        EngagementStance.set(stuck, EngagementStance.HOLD_FIRE);
        EngagementStance.set(free, EngagementStance.HOLD_FIRE);
        ArenaReady.whenTicking(helper, made, 150, orderedAt -> {
            // a target the unit picked on its own (not forced): a player-ordered target is never dropped by the nudge
            // (OrdersObeyGameTests.forced_target_survives_a_stuck_nudge_give_up)
            ((AttackerUnit) stuck).setUnitAttackTarget(foeA);
            ((AttackerUnit) free).setUnitAttackTargetForced(foeB);
            helper.runAfterDelay(300, () -> {
                try {
                    String state = "stuck: give-ups " + StuckNudge.giveUps(stuck) + ", target "
                        + (stuck.getTarget() == null ? "none" : "kept") + ", moved to " + f(stuck.getX() - x(helper, 0) + 0.5)
                        + "; free: give-ups " + StuckNudge.giveUps(free) + ", " + f(horizontal(free, foeB)) + " from its target";
                    ReignOfNether.LOGGER.info("[Melee] terrain: stuck nudge after 300 ticks (ordered at test tick {}): {}", orderedAt, state);
                    String why = " [ordered at test tick " + orderedAt + "; stuck " + chaseState(stuck, foeA) + "; free "
                        + chaseState(free, foeB) + "; " + ArenaReady.describe(level, made) + "]";
                    if (StuckNudge.giveUps(stuck) < 1)
                        problems.add("the immobile vindicator never gave up its target: " + state + why);
                    if (StuckNudge.giveUps(stuck) > 1)
                        problems.add("the immobile vindicator gave up more than once (re-acquired on hold fire?): " + state);
                    if (stuck.getTarget() != null)
                        problems.add("the immobile vindicator still has a target: " + state);
                    if (StuckNudge.giveUps(free) > 0)
                        problems.add("the free vindicator was nudged into giving up: " + state);
                    if (horizontal(free, foeB) > 2.0)
                        problems.add("the free vindicator did not reach its target: " + state + why);
                    finish(helper, problems);
                } finally {
                    cleanUp(made);
                }
            });
        });
    }

    /** What a chaser's chase is doing, for a failure message: target, navigation, whether it settled, the last nudge. */
    static String chaseState(Mob chaser, Mob foe) {
        return "{tickCount " + chaser.tickCount + ", target "
            + (chaser.getTarget() == null ? "none" : chaser.getTarget() == foe ? "its foe" : "another")
            + ", nav done " + chaser.getNavigation().isDone() + ", settled " + Engage.settled(chaser)
            + ", last nudge " + StuckNudge.lastNudge(chaser) + " (now " + chaser.level().getGameTime() + ")"
            + ", on ground " + chaser.onGround() + ", " + f(horizontal(chaser, foe)) + " from its foe, "
            + ArenaReady.around(chaser, foe) + "}";
    }
}
