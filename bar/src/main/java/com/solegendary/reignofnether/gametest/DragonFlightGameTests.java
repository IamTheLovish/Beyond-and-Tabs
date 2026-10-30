package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.controls.DragonMoveControl;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** The Bone Dragon's flight in a real world (#145): unit/controls/DragonFlight's steering on the live entity. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DragonFlightGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** A 16x16 glass floor high in the sky over the arena (SkirmishGameTests' sky pad): empty air all round. */
    private static BlockPos skyPad(GameTestHelper helper, int y) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        // put back when the test ends, pass or fail: a pad left over another test's column is in its sky
        TestCleanup.BlockUndo pad = TestCleanup.blocks(helper);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                pad.set(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState());
        return new BlockPos(corner.getX(), y + 1, corner.getZ());
    }

    /**
     * The Bone Dragon flies with a nose (#145, lovish: the flight is wrong): sent to a point behind it, it TURNS round -
     * its yaw changing at most DragonFlight.TURN_SLOW degrees a tick, never the ghast's one-tick snap - flies where it
     * faces (no sideways slide), and brakes into the point instead of coasting through it.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void bone_dragon_turns_and_brakes_into_its_point(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        final int gy = pad.getY();
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.BONE_DRAGON_UNIT.get().create(level);
        if (dragon == null) {
            helper.fail("could not create the Bone Dragon");
            return;
        }
        dragon.moveTo(pad.getX() + 3.5, gy + 8, pad.getZ() + 8.5, 90, 0);    // facing -x; the point is behind it
        dragon.setOwnerName("gametest_dragon_flight");
        level.addFreshEntity(dragon);
        // it hovers 5-10 above the pad, past the sweep's reach: gone however the test ends
        TestCleanup.entities(helper, List.of(dragon));
        BlockPos target = new BlockPos(pad.getX() + 13, gy - 1, pad.getZ() + 8);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
        final int[] ageAtOrder = { -1 };
        helper.runAfterDelay(3, () -> {
            ageAtOrder[0] = dragon.tickCount;   // 0: it had not ticked yet, so its nose was never synced to its yaw
            dragon.getMoveGoal().setMoveTarget(target);
        });
        final float[] last = { Float.NaN };
        final double[] lastPos = { dragon.getX(), dragon.getZ() };
        final double[] worst = { 0, 0, -1e9 };   // the largest yaw step, the largest slip, the farthest past the point (x)
        final String[] jump = { "" };
        // every sample, so a failure shows the ticks round the jump (the 96 deg snap of stress 40686491320 never
        // reproduced; the next one must say what the nose, the order and the dragon's own tick count were)
        final List<String> trace = new ArrayList<>();
        for (int t = 4; t <= 240; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                float yaw = dragon.getYRot();
                trace.add(sample(tick, dragon));
                if (!Float.isNaN(last[0])) {
                    double step = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - last[0]));
                    if (step > worst[0]) {
                        int i = trace.size() - 1;
                        jump[0] = String.format(" at tick %d, %.1f -> %.1f (ordered at its age %d; %s)", tick, last[0],
                            yaw, ageAtOrder[0], String.join(" | ", trace.subList(Math.max(0, i - 3), i + 1)));
                    }
                    worst[0] = Math.max(worst[0], step);
                }
                last[0] = yaw;
                double mx = dragon.getX() - lastPos[0], mz = dragon.getZ() - lastPos[1];
                if (mx * mx + mz * mz > 0.05 * 0.05) {
                    float moveYaw = (float) Math.toDegrees(-Math.atan2(mx, mz));
                    worst[1] = Math.max(worst[1], Math.abs(net.minecraft.util.Mth.wrapDegrees(moveYaw - yaw)));
                }
                lastPos[0] = dragon.getX();
                lastPos[1] = dragon.getZ();
                worst[2] = Math.max(worst[2], dragon.getX() - tx);
            });
        }
        helper.runAfterDelay(245, () -> {
            try {
                double dx = dragon.getX() - tx, dz = dragon.getZ() - tz;
                double off = Math.sqrt(dx * dx + dz * dz);
                double speed = dragon.getDeltaMovement().horizontalDistance();
                List<String> problems = new ArrayList<>();
                if (worst[0] > com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW + 0.5)
                    problems.add(String.format("the yaw jumped %.1f deg in one tick (cap %.1f)%s", worst[0],
                        com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW, jump[0]));
                if (worst[1] > 25)
                    problems.add(String.format("it slid %.0f deg off its nose", worst[1]));
                if (worst[2] > 3)
                    problems.add(String.format("it overshot the point by %.1f blocks", worst[2]));
                if (off > 3)
                    problems.add(String.format("it did not reach the point: %.1f blocks off", off));
                if (speed > 0.05)
                    problems.add(String.format("it is still coasting at %.2f b/t", speed));
                if (off > 3 || speed > 0.05)    // stress 40797860434: late and still coasting - did it tick all along?
                    problems.add(String.format("its age %d at test tick 245, ordered at its age %d; %s", dragon.tickCount,
                        ageAtOrder[0], tail(trace, 3)));
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                dragon.discard();
            }
        });
    }

    /**
     * A dragon ordered somewhere before its first tick flies off from the yaw it was spawned with. r13's flight kept
     * its own copy of the heading, taken in the constructor (yaw 0, before the spawn's moveTo) and only caught up on a
     * hover tick, so a dragon whose first tick already had a move swung its nose from its yaw to ~0 in one tick.
     * The order is to the glass pad: FlyingMoveToTargetGoal.setMoveTarget drops an order to an air block (the first
     * version of this test ordered it to air, so the dragon never moved and the test passed on the broken code).
     */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void bone_dragon_ordered_at_spawn_keeps_its_yaw(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        final int gy = pad.getY();
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.BONE_DRAGON_UNIT.get().create(level);
        if (dragon == null) {
            helper.fail("could not create the Bone Dragon");
            return;
        }
        dragon.moveTo(pad.getX() + 14.5, gy + 8, pad.getZ() + 8.5, 90, 0);    // facing -x
        dragon.setOwnerName("gametest_dragon_flight");
        // ordered before it ever ticks, to the far side of the pad straight ahead
        dragon.getMoveGoal().setMoveTarget(new BlockPos(pad.getX() + 1, gy - 1, pad.getZ() + 8));
        if (dragon.getMoveGoal().getMoveTarget() == null) {
            helper.fail("the dragon refused the order to the pad");
            return;
        }
        level.addFreshEntity(dragon);
        // it hovers 5-10 above the pad, past the sweep's reach: gone however the test ends
        TestCleanup.entities(helper, List.of(dragon));
        final double startX = dragon.getX();
        final float[] last = { 90f };
        final double[] worst = { 0 };
        final String[] jump = { "" };
        final List<String> trace = new ArrayList<>();
        final boolean[] done = { false };
        // judged on the dragon's own 40 ticks, not the test's: stress 40797860434 had one fly 1.7 blocks in 40 test ticks
        // (a full 40 ticks' climb to speed is ~6), so the dragon's age goes in the message and gates the verdict
        for (int t = 1; t <= 75; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                float yaw = dragon.getYRot();
                trace.add(sample(tick, dragon));
                double step = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - last[0]));
                if (step > worst[0]) {
                    int i = trace.size() - 1;
                    jump[0] = String.format(" at tick %d, %.1f -> %.1f (%s)", tick, last[0], yaw,
                        String.join(" | ", trace.subList(Math.max(0, i - 2), i + 1)));
                }
                worst[0] = Math.max(worst[0], step);
                last[0] = yaw;
                if (dragon.tickCount < 40 && tick < 75)
                    return;
                done[0] = true;
                try {
                    if (worst[0] > com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW + 0.5)
                        helper.fail(String.format("the yaw jumped %.1f deg in one tick (cap %.1f)%s", worst[0],
                            com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW, jump[0]));
                    else if (startX - dragon.getX() < 2)
                        helper.fail(String.format("it did not fly off on its order: %.1f blocks along -x by its age %d"
                            + " (test tick %d; %s)", startX - dragon.getX(), dragon.tickCount, tick, tail(trace, 3)));
                    else if (Math.abs(net.minecraft.util.Mth.wrapDegrees(dragon.getYRot() - 90f)) > 10)
                        helper.fail(String.format("flying straight ahead it ended facing %.1f, not 90", dragon.getYRot()));
                    else
                        helper.succeed();
                } finally {
                    dragon.discard();
                }
            });
        }
    }

    /**
     * The real way a dragon gets ordered before its first tick: a building produces it with a rally point.
     * Production runs in BuildingServerEvents' LevelTickEvent END (after the level's entities ticked), EntityType.spawn
     * gives it a random yaw, and ProductionPlacement.setDelayedRally's move (a 0-tick TaskScheduler task) lands at the
     * same tick's ServerTickEvent END - so the dragon's first tick already has the move. A GameTest runs in the same
     * slot (GameTestTicker, after the level ticks), so produceUnit here reproduces that order exactly. The random yaw
     * is fixed here to the way to the rally point, so the flight needs no turn and any one-tick swing is the bug.
     * <p>
     * Produced only once the arena's entity sections are loaded and entity-ticking (GameTestChunks.whenEntitiesReady),
     * not at a fixed tick: the structure's FORCED tickets load blocks first and entities later, and a dragon added to a
     * section still loading stays out of level.getEntity, so CommandQueues drops its rally move. Stress 40825509632 (3
     * of 7, at test tick 0) and 40871732997 (1 of 8, at tick 5: "found by id false, entity ticking false") on the slower
     * east-region runners. Production is still in the GameTestTicker slot after the level ticks.
     */
    @GameTest(template = ARENA, timeoutTicks = CHUNK_CAP + 100)
    public static void bone_dragon_produced_to_a_rally_point_keeps_its_yaw(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        final int gy = pad.getY();
        String owner = "dragonrally_" + Long.toString(Math.abs(helper.absolutePos(BlockPos.ZERO).asLong()) % 1679616L, 36);
        BuildingPlacement bp = RegressionGameTests.stamp(level, Buildings.BARRACKS, new BlockPos(pad.getX() + 1, gy, pad.getZ() + 1), owner);
        if (!(bp instanceof ProductionPlacement pp)) {
            RegressionGameTests.clear(level, bp);
            helper.fail("could not stamp a Barracks: " + bp);
            return;
        }
        BlockPos rally = new BlockPos(pad.getX() + 14, gy - 1, pad.getZ() + 14);   // the glass, past the Barracks' corner
        pp.setRallyPoint(rally);
        if (!pp.getRallyPoints().equals(List.of(rally))) {
            RegressionGameTests.clear(level, bp);
            helper.fail("the Barracks refused its rally point " + rally + ": " + pp.getRallyPoints());
            return;
        }
        final Mob[] made = { null };
        boolean[] cleaned = { false };
        Runnable cleanup = () -> {
            if (cleaned[0])
                return;
            cleaned[0] = true;
            if (made[0] != null) {
                UnitServerEvents.clearQueuedActions(made[0].getId());
                made[0].discard();
            }
            RegressionGameTests.clear(level, bp);
        };
        TestCleanup.atEnd(helper, cleanup);   // a fail, a throw or a timeout must not leave a registered Barracks behind
        final float[] spawnYaw = { 0 };
        final double[] start = new double[3];
        final int[] ageAtOrder = { -2 };
        final BlockPos[] ordered = { null };
        final String[] atOrder = { "the check never ran" };
        final long[] producedAt = { -1 };
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        // the arena's own chunks (the pad holds the Barracks, its spawn spot and the rally point). A wider list (corner
        // -8..+24: 9 chunks, a 40-chunk halo reaching two chunks past the structure's FORCED 5x5, which only ticket
        // spread loads) failed stress 40887783493 at the cap: "1 of 9 ... never had their entities loaded", halo 39 of 40
        List<long[]> chunks = new ArrayList<>();
        for (int cx = corner.getX() >> 4; cx <= (corner.getX() + 15) >> 4; cx++)
            for (int cz = corner.getZ() >> 4; cz <= (corner.getZ() + 15) >> 4; cz++)
                chunks.add(new long[] { cx, cz });
        // the sampler below is registered at the test's start, not from this callback (see BuildingDamageGameTests)
        GameTestChunks.whenEntitiesReady(helper, level, chunks, 5, 400, CHUNK_CAP, "dragon rally arena", () -> {
            Entity produced = pp.produceUnit(level, EntityRegistrar.BONE_DRAGON_UNIT.get(), owner, false);
            if (!(produced instanceof Mob dragon) || !(produced instanceof Unit unit)) {
                helper.fail("the Barracks produced no Bone Dragon: " + produced);
                return;
            }
            made[0] = dragon;
            producedAt[0] = helper.getTick();
            spawnYaw[0] = (float) Math.toDegrees(-Math.atan2(rally.getX() + 0.5 - dragon.getX(), rally.getZ() + 0.5 - dragon.getZ()));
            dragon.setYRot(spawnYaw[0]);
            dragon.yBodyRot = spawnYaw[0];
            start[0] = dragon.getX();
            start[1] = dragon.getY();
            start[2] = dragon.getZ();
            // queued after the rally's own 0-tick task, so it runs right after it, still before the dragon's first tick
            TaskSchedulerServerEvents.schedule(0, () -> {
                ageAtOrder[0] = dragon.tickCount;
                ordered[0] = unit.getMoveGoal() == null ? null : unit.getMoveGoal().getMoveTarget();
                // what the move saw, for a setup failure: its target block, whether the level could find the dragon
                // (CommandQueues orders only units it finds), whether its block ticks entities
                atOrder[0] = String.format("rally block %s, found by id %b, entity ticking %b", level.getBlockState(rally),
                    level.getEntity(dragon.getId()) == dragon, level.isPositionEntityTicking(dragon.blockPosition()));
            });
        });
        final float[] last = { Float.NaN };
        final double[] worst = { 0 };
        final String[] jump = { "" };
        final List<String> trace = new ArrayList<>();
        final boolean[] done = { false };
        // judged on the dragon's own 40 ticks, not the test's (see bone_dragon_ordered_at_spawn_keeps_its_yaw); idles until
        // the dragon is made, then counts test ticks from the one it was made in
        helper.onEachTick(() -> {
            Mob dragon = made[0];
            if (done[0] || dragon == null || helper.getTick() <= producedAt[0])
                return;
            final int tick = (int) (helper.getTick() - producedAt[0]);
            if (Float.isNaN(last[0]))
                last[0] = spawnYaw[0];
            float yaw = dragon.getYRot();
            trace.add(sample(tick, dragon));
            double step = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - last[0]));
            if (step > worst[0]) {
                int i = trace.size() - 1;
                jump[0] = String.format(" at tick %d, %.1f -> %.1f (%s)", tick, last[0], yaw,
                    String.join(" | ", trace.subList(Math.max(0, i - 2), i + 1)));
            }
            worst[0] = Math.max(worst[0], step);
            last[0] = yaw;
            if (dragon.tickCount < 40 && tick < 90)
                return;
            done[0] = true;
            try {
                double dx = dragon.getX() - start[0], dz = dragon.getZ() - start[2];
                double moved = Math.sqrt(dx * dx + dz * dz);
                String setup = String.format(" (spawned at %.1f %.1f %.1f facing %.1f; the rally move reached it at its age %d, target %s; %s)",
                    start[0], start[1], start[2], spawnYaw[0], ageAtOrder[0], ordered[0], atOrder[0]);
                if (ordered[0] == null || ageAtOrder[0] != 0)
                    helper.fail("setup: the rally move did not reach the dragon before its first tick, so this does not"
                        + " test production's order" + setup);
                else if (worst[0] > com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW + 0.5)
                    helper.fail(String.format("the yaw jumped %.1f deg in one tick (cap %.1f)%s%s", worst[0],
                        com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW, jump[0], setup));
                else if (moved < 1)
                    helper.fail(String.format("it did not fly off to the rally point: %.1f blocks by its age %d (test tick"
                        + " %d; %s)%s", moved, dragon.tickCount, tick, tail(trace, 3), setup));
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * The Stormcallers' dragons fly with a nose too (lovish after a match, Oct 30: "looks choppy laggy ... isnt fluid at
     * all"). They flew on the ghast brain (FlyingUnitMoveControl): a shove of a fifth of the speed attribute every 2-6
     * random ticks, coasting on the drag between, and the yaw snapped to that velocity in one tick. Sent to a point
     * behind it, each must turn round at most DragonFlight.TURN_SLOW degrees a tick, its speed over the ground must
     * change smoothly (never more than MAX_SPEED_STEP b/t from one tick to the next: the steering eases by 0.008 /
     * 0.012, a ghast shove is ~0.06), and it must come to rest on the point.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void celestial_dragon_turns_and_eases_into_its_point(GameTestHelper helper) {
        fliesSmoothlyToAPointBehindIt(helper, EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), "Celestial Dragon");
    }

    /** As {@link #celestial_dragon_turns_and_eases_into_its_point}, for the Thunder Dragon. */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void thunder_dragon_turns_and_eases_into_its_point(GameTestHelper helper) {
        fliesSmoothlyToAPointBehindIt(helper, EntityRegistrar.THUNDER_DRAGON_UNIT.get(), "Thunder Dragon");
    }

    /** The largest change in speed over the ground from one tick to the next that still reads as smooth flight. */
    static final double MAX_SPEED_STEP = 0.02;

    private static void fliesSmoothlyToAPointBehindIt(GameTestHelper helper, EntityType<? extends Mob> type, String name) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        final int gy = pad.getY();
        Mob dragon = type.create(level);
        if (!(dragon instanceof Unit unit)) {
            helper.fail("could not create the " + name);
            return;
        }
        dragon.moveTo(pad.getX() + 3.5, gy + 8, pad.getZ() + 8.5, 90, 0);    // facing -x; the point is behind it
        unit.setOwnerName("gametest_dragon_flight");
        level.addFreshEntity(dragon);
        TestCleanup.entities(helper, List.of(dragon));
        BlockPos target = new BlockPos(pad.getX() + 13, gy - 1, pad.getZ() + 8);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
        helper.runAfterDelay(3, () -> unit.getMoveGoal().setMoveTarget(target));
        final float[] last = { Float.NaN };
        final double[] lastPos = { Double.NaN, Double.NaN, Double.NaN };   // x, z, last tick's speed over the ground
        final double[] worst = { 0, 0 };   // the largest yaw step, the largest speed step
        final String[] where = { "", "" };
        final List<String> trace = new ArrayList<>();
        for (int t = 4; t <= 240; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                trace.add(sample(tick, dragon));
                int i = trace.size() - 1;
                float yaw = dragon.getYRot();
                if (!Float.isNaN(last[0])) {
                    double step = Math.abs(Mth.wrapDegrees(yaw - last[0]));
                    if (step > worst[0]) {
                        worst[0] = step;
                        where[0] = String.format(" at tick %d, %.1f -> %.1f (%s)", tick, last[0], yaw,
                            String.join(" | ", trace.subList(Math.max(0, i - 3), i + 1)));
                    }
                }
                last[0] = yaw;
                if (!Double.isNaN(lastPos[0])) {
                    double mx = dragon.getX() - lastPos[0], mz = dragon.getZ() - lastPos[1];
                    double speed = Math.sqrt(mx * mx + mz * mz);
                    if (!Double.isNaN(lastPos[2])) {
                        double step = Math.abs(speed - lastPos[2]);
                        if (step > worst[1]) {
                            worst[1] = step;
                            where[1] = String.format(" at tick %d, %.3f -> %.3f b/t (%s)", tick, lastPos[2], speed,
                                String.join(" | ", trace.subList(Math.max(0, i - 3), i + 1)));
                        }
                    }
                    lastPos[2] = speed;
                }
                lastPos[0] = dragon.getX();
                lastPos[1] = dragon.getZ();
            });
        }
        helper.runAfterDelay(245, () -> {
            try {
                double dx = dragon.getX() - tx, dz = dragon.getZ() - tz;
                double off = Math.sqrt(dx * dx + dz * dz);
                double speed = dragon.getDeltaMovement().horizontalDistance();
                List<String> problems = new ArrayList<>();
                if (worst[0] > com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW + 0.5)
                    problems.add(String.format("the yaw jumped %.1f deg in one tick (cap %.1f)%s", worst[0],
                        com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW, where[0]));
                if (worst[1] > MAX_SPEED_STEP)
                    problems.add(String.format("its speed jumped %.3f b/t in one tick (cap %.3f)%s", worst[1],
                        MAX_SPEED_STEP, where[1]));
                if (off > 3)
                    problems.add(String.format("it did not reach the point: %.1f blocks off (%s)", off, tail(trace, 3)));
                if (speed > 0.05)
                    problems.add(String.format("it is still coasting at %.2f b/t", speed));
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(name + ": " + String.join("; ", problems));
            } finally {
                dragon.discard();
            }
        });
    }

    /**
     * The client sees every tick of a dragon's flight. An entity type's default tracking sends the position every 3rd
     * tick and the client walks the body there over 3 frames' ticks; packets that land a tick early or late leave a
     * big, fast flyer standing still for a tick and then jumping (the stutter lovish saw), and the rig, which reads the
     * flight from the client's own positions, flickers between its glide and its hover with it.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void dragons_send_their_position_every_tick(GameTestHelper helper) {
        List<String> slow = new ArrayList<>();
        for (EntityType<?> type : List.<EntityType<?>>of(EntityRegistrar.BONE_DRAGON_UNIT.get(),
                EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), EntityRegistrar.THUNDER_DRAGON_UNIT.get()))
            if (type.updateInterval() != 1)
                slow.add(EntityType.getKey(type) + " every " + type.updateInterval() + " ticks");
        if (slow.isEmpty())
            helper.succeed();
        else
            helper.fail("dragons tracked less than every tick: " + String.join(", ", slow));
    }

    /** Test ticks the rally-point test waits at most for its arena's entity sections before it fails on them. */
    static final int CHUNK_CAP = 1200;   // ready after 80-239 ticks on 7 of 8 runners (40887783493); Raid's pads cap at 1300

    /** The last n samples of a trace, for a failure message. */
    private static String tail(List<String> trace, int n) {
        return trace.isEmpty() ? "no samples" : String.join(" | ", trace.subList(Math.max(0, trace.size() - n), trace.size()));
    }

    /** One sampled tick for a failure message: test tick, the dragon's own age, yaw, nose, cruising, a move, speed. */
    private static String sample(int tick, Mob dragon) {
        String nose = dragon.getMoveControl() instanceof DragonMoveControl dc
            ? String.format("nose %.1f %s", dc.heading(), dc.isCruising() ? "cruise" : "hover") : "no dragon control";
        return String.format("t%d age %d yaw %.1f %s%s v %.3f at %.1f %.1f", tick, dragon.tickCount, dragon.getYRot(),
            nose, dragon.getMoveControl().hasWanted() ? " move" : "", dragon.getDeltaMovement().horizontalDistance(),
            dragon.getX(), dragon.getZ());
    }
}
