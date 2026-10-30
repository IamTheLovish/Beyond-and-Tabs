package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A unit walks a straight line straight (troop animation plan, gain 2; #412's real-client yaw trace, Oct 30: walking,
 * a unit's body yaw wobbled 40-130 degrees within ~15 ticks). The vanilla path is a staircase of grid nodes on any line
 * off the axes and diagonals, and the unit aims at each node's centre in turn, so its heading swings between the axis
 * and the diagonal every few blocks - drawn, the body rocks from side to side all the way. On an open glass pad far from
 * the test grid one unit is ordered 32 blocks east and 7 south (12 degrees off the axis). Over the walk - from 10 ticks
 * after the order (the first turn is #412's) until 4 blocks short of the goal - its body yaw (what every frame draws)
 * may turn the net way it has to, plus at most {@link #MAX_ROCK} degrees: the excess of all its turning over the net turn
 * is the rocking (a clean corner adds nothing to it, a swing out and back adds both halves). Logs a "[Walk]" line with
 * the measured rocking, the largest swing off the bearing and the yaw every 4 ticks.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WalkLineGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_Y = 205;
    static final int SETUP = 20;   // ticks between laying the pad and spawning (chunk sends, entity sections)
    /** Degrees of turning the body may do over the walk beyond the net turn (side-to-side rocking). */
    static final float MAX_ROCK = 10f;
    static final int W = 40, D = 16;

    @GameTest(template = ARENA, timeoutTicks = SETUP + 400, batch = "walk_line")
    public static void a_unit_walks_an_off_axis_line_without_rocking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        // its own pad 21000 west and north of the grid (the combat pads are 9000 out, the stress 3000, the big bodies'
        // pad 36000 south)
        int x0 = (((base.getX() - 21000) >> 4) << 4) + 2, z0 = (((base.getZ() - 21000) >> 4) << 4) + 2;
        List<long[]> chunks = new ArrayList<>();
        for (int cx = (x0 - 16) >> 4; cx <= (x0 + W + 16) >> 4; cx++)
            for (int cz = (z0 - 16) >> 4; cz <= (z0 + D + 16) >> 4; cz++)
                chunks.add(new long[] { cx, cz });
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "walk line pad");
        GameTestChunks.awaitRingOrFail(level, chunks, "walk line pad");
        BlockState glass = Blocks.GLASS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < W; dx++)
            for (int dz = 0; dz < D; dz++) {
                boolean rim = dx == 0 || dz == 0 || dx == W - 1 || dz == D - 1;
                level.setBlock(p.set(x0 + dx, PAD_Y, z0 + dz), glass, 2);
                for (int dy = 1; dy <= 6; dy++) {
                    p.set(x0 + dx, PAD_Y + dy, z0 + dz);
                    if (rim && dy <= 3)
                        level.setBlock(p, glass, 2);
                    else if (!level.getBlockState(p).isAir())
                        level.setBlock(p, air, 2);
                }
            }
        String owner = "gametest_walk_line_" + base.asLong();
        List<Mob> made = new ArrayList<>();
        TestCleanup.atEnd(helper, () -> {
            for (Mob m : made) {
                UnitServerEvents.clearQueuedActions(m.getId());
                m.discard();
            }
            GameTestChunks.releaseAll(level, chunks);
        });
        final double sx = x0 + 3.5, sz = z0 + 4.5, gx = x0 + 35.5, gz = z0 + 11.5;
        helper.runAfterDelay(SETUP, () -> {
            Mob m = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
            if (m == null)
                throw new GameTestAssertException("could not create a Vindicator");
            m.moveTo(sx, PAD_Y + 1, sz, -90, 0);   // facing east, near enough the line that the first turn is small
            ((Unit) m).setOwnerName(owner);
            level.addFreshEntity(m);
            made.add(m);
        });
        // the walk: from the order, every tick
        int[] ordered = { -1 };
        // [0] all the turning, [1] the yaw judged from (unwrapped), [2] the yaw now (unwrapped), [3] the largest swing
        // off the bearing; NaN until the judged window starts
        float[] turn = { 0f, Float.NaN, Float.NaN, 0f };
        int[] worstTick = { -1 };
        StringBuilder trace = new StringBuilder();
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0] || made.isEmpty())
                return;
            Mob m = made.get(0);
            if (ordered[0] < 0) {
                if (m.tickCount < ArenaReady.TICKS) {
                    if (helper.getTick() > SETUP + 200)
                        throw new GameTestAssertException("the unit was not ticking: " + ArenaReady.describe(level, made));
                    return;
                }
                ordered[0] = (int) helper.getTick();
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, new int[] { m.getId() },
                    BlockPos.containing(gx, PAD_Y + 1, gz), BlockPos.ZERO);
                return;
            }
            int t = (int) helper.getTick() - ordered[0];
            double dx = gx - m.getX(), dz = gz - m.getZ();
            double left = Math.sqrt(dx * dx + dz * dz);
            // MC yaw: 0 = south (+z), -90 = east (+x)
            float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float off = Mth.wrapDegrees(m.yBodyRot - bearing);
            if (t % 4 == 0)
                trace.append(String.format(Locale.ROOT, " t%d:%.0f/%.0f", t, m.yBodyRot, bearing));
            // judged from 10 ticks after the order (the first turn is #412's) until 4 blocks short of the goal (the
            // stop and the last node are not the line)
            if (t >= 10 && left > 4) {
                if (Float.isNaN(turn[1])) {
                    turn[1] = turn[2] = m.yBodyRot;
                } else {
                    float step = Mth.wrapDegrees(m.yBodyRot - turn[2]);
                    turn[0] += Math.abs(step);
                    turn[2] += step;
                }
                if (Math.abs(off) > turn[3]) {
                    turn[3] = Math.abs(off);
                    worstTick[0] = t;
                }
            }
            if (left > 2.5) {
                if (t > 300)
                    throw new GameTestAssertException("the unit did not reach the goal in 300 ticks (" + String.format(
                        Locale.ROOT, "%.1f", left) + " blocks left):" + trace);
                return;
            }
            done[0] = true;
            float net = Float.isNaN(turn[1]) ? 0f : Math.abs(turn[2] - turn[1]);
            float rock = turn[0] - net;
            String line = String.format(Locale.ROOT, "[Walk] off-axis line, Vindicator: arrived in %d ticks, turned %.1f"
                + " degrees for a net %.1f - rocking %.1f (bound %.0f); largest swing off the bearing %.1f (t%d);"
                + " yaw/bearing:%s", t, turn[0], net, rock, MAX_ROCK, turn[3], worstTick[0], trace);
            ReignOfNether.LOGGER.info(line);
            if (Float.isNaN(turn[1]))
                throw new GameTestAssertException("the walk was too short to judge: " + line);
            if (rock > MAX_ROCK)
                throw new GameTestAssertException("the body rocks from side to side on a straight line: " + line);
            helper.succeed();
        });
    }
}
