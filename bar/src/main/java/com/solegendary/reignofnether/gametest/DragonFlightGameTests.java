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
import net.minecraft.world.entity.Entity;
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
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
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
        final double startX = dragon.getX();
        final float[] last = { 90f };
        final double[] worst = { 0 };
        final String[] jump = { "" };
        final List<String> trace = new ArrayList<>();
        for (int t = 1; t <= 40; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
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
            });
        }
        helper.runAfterDelay(42, () -> {
            try {
                if (worst[0] > com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW + 0.5)
                    helper.fail(String.format("the yaw jumped %.1f deg in one tick (cap %.1f)%s", worst[0],
                        com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW, jump[0]));
                else if (startX - dragon.getX() < 2)
                    helper.fail(String.format("it did not fly off on its order: %.1f blocks along -x in 40 ticks",
                        startX - dragon.getX()));
                else if (Math.abs(net.minecraft.util.Mth.wrapDegrees(dragon.getYRot() - 90f)) > 10)
                    helper.fail(String.format("flying straight ahead it ended facing %.1f, not 90", dragon.getYRot()));
                else
                    helper.succeed();
            } finally {
                dragon.discard();
            }
        });
    }

    /**
     * The real way a dragon gets ordered before its first tick: a building produces it with a rally point.
     * Production runs in BuildingServerEvents' LevelTickEvent END (after the level's entities ticked), EntityType.spawn
     * gives it a random yaw, and ProductionPlacement.setDelayedRally's move (a 0-tick TaskScheduler task) lands at the
     * same tick's ServerTickEvent END - so the dragon's first tick already has the move. A GameTest runs in the same
     * slot (GameTestTicker, after the level ticks), so produceUnit here reproduces that order exactly. The random yaw
     * is fixed here to the way to the rally point, so the flight needs no turn and any one-tick swing is the bug.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
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
        Entity produced = pp.produceUnit(level, EntityRegistrar.BONE_DRAGON_UNIT.get(), owner, false);
        if (!(produced instanceof Mob dragon) || !(produced instanceof Unit unit)) {
            RegressionGameTests.clear(level, bp);
            helper.fail("the Barracks produced no Bone Dragon: " + produced);
            return;
        }
        final float spawnYaw = (float) Math.toDegrees(-Math.atan2(rally.getX() + 0.5 - dragon.getX(), rally.getZ() + 0.5 - dragon.getZ()));
        dragon.setYRot(spawnYaw);
        dragon.yBodyRot = spawnYaw;
        // queued after the rally's own 0-tick task, so it runs right after it, still before the dragon's first tick
        final int[] ageAtOrder = { -2 };
        final BlockPos[] ordered = { null };
        TaskSchedulerServerEvents.schedule(0, () -> {
            ageAtOrder[0] = dragon.tickCount;
            ordered[0] = unit.getMoveGoal() == null ? null : unit.getMoveGoal().getMoveTarget();
        });
        final double sx = dragon.getX(), sy = dragon.getY(), sz = dragon.getZ();
        final float[] last = { spawnYaw };
        final double[] worst = { 0 };
        final String[] jump = { "" };
        final List<String> trace = new ArrayList<>();
        boolean[] cleaned = { false };
        Runnable cleanup = () -> {
            if (cleaned[0])
                return;
            cleaned[0] = true;
            UnitServerEvents.clearQueuedActions(dragon.getId());
            dragon.discard();
            RegressionGameTests.clear(level, bp);
        };
        helper.runAfterDelay(95, cleanup);   // a timeout must not leave a registered Barracks behind
        for (int t = 1; t <= 40; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
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
            });
        }
        helper.runAfterDelay(42, () -> {
            try {
                double moved = Math.sqrt((dragon.getX() - sx) * (dragon.getX() - sx) + (dragon.getZ() - sz) * (dragon.getZ() - sz));
                String setup = String.format(" (spawned at %.1f %.1f %.1f facing %.1f; the rally move reached it at its age %d, target %s)",
                    sx, sy, sz, spawnYaw, ageAtOrder[0], ordered[0]);
                if (ordered[0] == null || ageAtOrder[0] != 0)
                    helper.fail("setup: the rally move did not reach the dragon before its first tick, so this does not"
                        + " test production's order" + setup);
                else if (worst[0] > com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW + 0.5)
                    helper.fail(String.format("the yaw jumped %.1f deg in one tick (cap %.1f)%s%s", worst[0],
                        com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW, jump[0], setup));
                else if (moved < 1)
                    helper.fail(String.format("it did not fly off to the rally point: %.1f blocks in 40 ticks%s", moved, setup));
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
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
