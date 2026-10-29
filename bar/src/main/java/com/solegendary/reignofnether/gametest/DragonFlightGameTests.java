package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
        BlockPos target = new BlockPos(pad.getX() + 13, gy - 1, pad.getZ() + 8);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
        helper.runAfterDelay(3, () -> dragon.getMoveGoal().setMoveTarget(target));
        final float[] last = { Float.NaN };
        final double[] lastPos = { dragon.getX(), dragon.getZ() };
        final double[] worst = { 0, 0, -1e9 };   // the largest yaw step, the largest slip, the farthest past the point (x)
        final String[] jump = { "" };
        for (int t = 4; t <= 240; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                float yaw = dragon.getYRot();
                if (!Float.isNaN(last[0])) {
                    double step = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - last[0]));
                    if (step > worst[0])
                        jump[0] = String.format(" at tick %d, %.1f -> %.1f", tick, last[0], yaw);
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
     * A dragon ordered somewhere in the tick it spawns (a building's rally point) flies off from the yaw it was spawned
     * with: r13's flight kept its own copy of the heading, taken in the constructor (yaw 0) and only caught up on a
     * hover tick, so the first flying tick swung the nose 90 deg in one tick (stress 40686491320, 1 in 8).
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
        dragon.moveTo(pad.getX() + 3.5, gy + 8, pad.getZ() + 8.5, 90, 0);    // facing -x
        dragon.setOwnerName("gametest_dragon_flight");
        // ordered before it ever ticks, to a point straight ahead
        dragon.getMoveGoal().setMoveTarget(new BlockPos(pad.getX() - 20, gy + 8, pad.getZ() + 8));
        level.addFreshEntity(dragon);
        final float[] last = { 90f };
        final double[] worst = { 0 };
        final String[] jump = { "" };
        for (int t = 1; t <= 40; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                float yaw = dragon.getYRot();
                double step = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - last[0]));
                if (step > worst[0])
                    jump[0] = String.format(" at tick %d, %.1f -> %.1f", tick, last[0], yaw);
                worst[0] = Math.max(worst[0], step);
                last[0] = yaw;
            });
        }
        helper.runAfterDelay(42, () -> {
            try {
                if (worst[0] > com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW + 0.5)
                    helper.fail(String.format("the yaw jumped %.1f deg in one tick (cap %.1f)%s", worst[0],
                        com.solegendary.reignofnether.unit.controls.DragonFlight.TURN_SLOW, jump[0]));
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
