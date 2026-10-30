package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.LineOfWalk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A unit walks a straight line straight (troop animation plan, gain 2; #412's real-client yaw trace, Oct 30: walking,
 * a unit's body yaw wobbled 40-130 degrees within ~15 ticks). The path is a staircase of grid nodes on any line off the
 * axes and diagonals, and the unit aims at each node in turn, so its heading swings between the axis and the diagonal
 * every few blocks - drawn, the body rocks from side to side all the way. On an open glass pad far from the test grid
 * one unit is ordered 32 blocks east and 7 south (12 degrees off the axis). Over the walk - from 10 ticks after the order
 * (the first turn is #412's) until 4 blocks short of the goal - its body yaw (what every frame draws) must stay within
 * {@link #MAX_OFF} of the bearing to the goal, and may turn the net way it has to plus at most {@link #MAX_ROCK}
 * degrees: the excess of all its turning over the net turn is the rocking (a clean corner adds nothing to it, a swing
 * out and back adds both halves; the bearing bound catches a path that bunches its diagonal steps into one corner).
 * <p>
 * Both pathfinders: vanilla's (the walk_line batch) and the RTS grid's (walk_line_rts - every battlefield match turns
 * it on, ArenaPathing.onArena), each with a narrow Vindicator and a 1.95-wide Siege Ox beside a wall it must never
 * touch. And the straight line keeps to the units' own ground: an azalea bush on the line (non-solid to vanilla, a wall
 * to the units' pathfinder) is walked round, never pushed at, and LineOfWalk's cells read real blocks by the units'
 * rules. Each walk logs a "[Walk]" line (WARN, so CI shows the margins) with the yaw every 4 ticks.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WalkLineGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "walk_line";
    // its own batch: it switches the server-wide rtsPathfinding flag on for its run (batches run one at a time)
    static final String BATCH_RTS = "walk_line_rts";
    static final int PAD_Y = 205;
    static final int SETUP = 20;   // ticks between laying the pad and spawning (chunk sends, entity sections)
    /** Degrees of turning the body may do over the walk beyond the net turn (side-to-side rocking). */
    static final float MAX_ROCK = 10f;
    /** Degrees the body may face away from the straight bearing to the goal while walking. */
    static final float MAX_OFF = 8f;
    /** Ticks the body may spend touching a block it must keep off (brushing past is a tick or two; pushing is dozens). */
    static final int MAX_TOUCH = 4;
    static final int W = 48, D = 24;

    @GameTest(template = ARENA, timeoutTicks = SETUP + 400, batch = BATCH)
    public static void a_unit_walks_an_off_axis_line_without_rocking(GameTestHelper helper) {
        walk(helper, "off-axis line, Vindicator", EntityRegistrar.VINDICATOR_UNIT::get, 0, false, pad -> { });
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + 400, batch = BATCH_RTS)
    public static void a_unit_walks_an_off_axis_line_without_rocking_on_rts_pathing(GameTestHelper helper) {
        TestCleanup.rtsPathfinding(helper);
        walk(helper, "off-axis line, Vindicator, RTS pathing", EntityRegistrar.VINDICATOR_UNIT::get, 1, true, pad -> { });
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + 500, batch = BATCH)
    public static void a_wide_body_walks_an_off_axis_line_past_a_wall_without_rocking_or_touching_it(GameTestHelper helper) {
        walk(helper, "off-axis line, Siege Ox, wall beside", EntityRegistrar.RAVAGER_UNIT::get, 2, false,
            WalkLineGameTests::wallBeside);
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + 500, batch = BATCH_RTS)
    public static void a_wide_body_walks_an_off_axis_line_past_a_wall_without_rocking_or_touching_it_on_rts_pathing(GameTestHelper helper) {
        TestCleanup.rtsPathfinding(helper);
        walk(helper, "off-axis line, Siege Ox, wall beside, RTS pathing", EntityRegistrar.RAVAGER_UNIT::get, 3, true,
            WalkLineGameTests::wallBeside);
    }

    /**
     * An azalea bush - 2x2, square on the line: vanilla calls it open (not a full collision box) but it is a wall to
     * the units' pathfinder (WalkNodeEvaluatorMixin, WalkabilityBuilder.blocksBody), so the nodes go round it. A
     * straight line judged by vanilla's rule cuts across it and the unit pushes at the bush until it gives up (the
     * Heartwood Hall's bushes, once). Only the rocking bound is waived: the walk bends round the bush.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + 400, batch = BATCH)
    public static void a_unit_walks_round_an_azalea_on_its_line_and_never_pushes_into_it(GameTestHelper helper) {
        walk(helper, "off-axis line, Vindicator, azalea on the line", EntityRegistrar.VINDICATOR_UNIT::get, 4, false, pad -> {
            for (int dx = 20; dx <= 21; dx++)
                for (int dz = 7; dz <= 8; dz++) {
                    pad.set(dx, 0, dz, Blocks.DIRT.defaultBlockState());
                    pad.set(dx, 1, dz, Blocks.AZALEA.defaultBlockState());
                    pad.keepOff(dx, dz);
                }
            pad.bends = true;
        });
    }

    /**
     * LineOfWalk's cells on real blocks: plain floor is standable; a bush in the feet cell, a cauldron's or composter's
     * rim and a leaf floor (the units' own rules - not vanilla's), a fence and water are not.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void the_line_reads_real_blocks_by_the_units_rules(GameTestHelper helper) {
        Pad pad = Pad.lay(helper, 5);
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        Object[][] cells = {
            // name, floor block, feet block, standable
            { "stone floor", Blocks.STONE.defaultBlockState(), null, true },
            { "an azalea bush in the feet", Blocks.DIRT.defaultBlockState(), Blocks.AZALEA.defaultBlockState(), false },
            { "a flowering azalea in the feet", Blocks.DIRT.defaultBlockState(), Blocks.FLOWERING_AZALEA.defaultBlockState(), false },
            { "a cauldron floor", Blocks.CAULDRON.defaultBlockState(), null, false },
            { "a composter floor", Blocks.COMPOSTER.defaultBlockState(), null, false },
            { "a leaf floor", leaf, null, false },
            { "a fence in the feet", Blocks.STONE.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(), false },
            { "water in the feet", Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(), false },
        };
        for (int i = 0; i < cells.length; i++) {
            pad.set(4 + 4 * i, 0, 6, (BlockState) cells[i][1]);
            if (cells[i][2] != null)
                pad.set(4 + 4 * i, 1, 6, (BlockState) cells[i][2]);
        }
        helper.runAfterDelay(SETUP, () -> {
            LineOfWalk.Cells read = LineOfWalk.of(pad.level, 2);
            List<String> wrong = new ArrayList<>();
            StringBuilder all = new StringBuilder();
            for (int i = 0; i < cells.length; i++) {
                boolean got = read.standable(pad.x0 + 4 + 4 * i, PAD_Y + 1, pad.z0 + 6);
                all.append(" ").append(cells[i][0]).append(": ").append(got).append(";");
                if (got != (boolean) cells[i][3])
                    wrong.add(cells[i][0] + " read " + (got ? "standable" : "not standable"));
            }
            ReignOfNether.LOGGER.warn("[Walk] the line's cells on real blocks:{}", all);
            if (!wrong.isEmpty())
                throw new GameTestAssertException("the line reads cells by other rules than the units': " + wrong);
            helper.succeed();
        });
    }

    // a staircase of stone 3 high, 3 cells south of the straight line from the start to the goal, over its middle
    // stretch: off every route a wide body takes, but where a line checked to the wrong end point would brush it
    private static void wallBeside(Pad pad) {
        for (int dx = 12; dx <= 32; dx++) {
            int dz = (int) Math.round(START_Z + (GOAL_Z - START_Z) * (dx + 0.5 - START_X) / (GOAL_X - START_X)) + 3;
            for (int dy = 1; dy <= 3; dy++)
                pad.set(dx, dy, dz, Blocks.STONE.defaultBlockState());
            pad.keepOff(dx, dz);
        }
    }

    static final double START_X = 3.5, START_Z = 4.5, GOAL_X = 35.5, GOAL_Z = 11.5;

    /** A test's own open glass pad, rimmed, `index` pads along from the others. */
    static final class Pad {
        final GameTestHelper helper;
        final ServerLevel level;
        final int x0, z0;
        final List<long[]> chunks = new ArrayList<>();
        final List<AABB> keepOff = new ArrayList<>();
        boolean bends = false;

        private Pad(GameTestHelper helper, int index) {
            this.helper = helper;
            this.level = helper.getLevel();
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            // 21000 west and north of the grid (the combat pads are 9000 out, the stress 3000, the big bodies' pad
            // 36000 south), each test 128 blocks along
            this.x0 = (((base.getX() - 21000 - 128 * index) >> 4) << 4) + 2;
            this.z0 = (((base.getZ() - 21000) >> 4) << 4) + 2;
        }

        static Pad lay(GameTestHelper helper, int index) {
            Pad pad = new Pad(helper, index);
            ServerLevel level = pad.level;
            for (int cx = (pad.x0 - 16) >> 4; cx <= (pad.x0 + W + 16) >> 4; cx++)
                for (int cz = (pad.z0 - 16) >> 4; cz <= (pad.z0 + D + 16) >> 4; cz++)
                    pad.chunks.add(new long[] { cx, cz });
            GameTestChunks.holdAndAwaitOrFail(level, pad.chunks, "walk line pad");
            GameTestChunks.awaitRingOrFail(level, pad.chunks, "walk line pad");
            BlockState glass = Blocks.GLASS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx < W; dx++)
                for (int dz = 0; dz < D; dz++) {
                    boolean rim = dx == 0 || dz == 0 || dx == W - 1 || dz == D - 1;
                    level.setBlock(p.set(pad.x0 + dx, PAD_Y, pad.z0 + dz), glass, 2);
                    for (int dy = 1; dy <= 6; dy++) {
                        p.set(pad.x0 + dx, PAD_Y + dy, pad.z0 + dz);
                        if (rim && dy <= 3)
                            level.setBlock(p, glass, 2);
                        else if (!level.getBlockState(p).isAir())
                            level.setBlock(p, air, 2);
                    }
                }
            TestCleanup.atEnd(helper, () -> GameTestChunks.releaseAll(level, pad.chunks));
            return pad;
        }

        /** Sets the block `dy` above the pad's floor (0 = the floor itself) at pad cell (dx, dz). */
        void set(int dx, int dy, int dz, BlockState state) {
            level.setBlock(new BlockPos(x0 + dx, PAD_Y + dy, z0 + dz), state, 2);
        }

        /** The body must not touch pad cell (dx, dz) (feet and head height). */
        void keepOff(int dx, int dz) {
            keepOff.add(new AABB(x0 + dx, PAD_Y + 1, z0 + dz, x0 + dx + 1, PAD_Y + 3, z0 + dz + 1).inflate(0.02));
        }
    }

    // one walk on its own pad: a unit of `type` ordered from START to GOAL, judged as above
    private static void walk(GameTestHelper helper, String what, Supplier<? extends EntityType<? extends Mob>> type,
                             int index, boolean rts, Consumer<Pad> build) {
        Pad pad = Pad.lay(helper, index);
        build.accept(pad);
        ServerLevel level = pad.level;
        String owner = "gametest_walk_line_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        TestCleanup.atEnd(helper, () -> {
            for (Mob m : made) {
                UnitServerEvents.clearQueuedActions(m.getId());
                m.discard();
            }
        });
        final double sx = pad.x0 + START_X, sz = pad.z0 + START_Z, gx = pad.x0 + GOAL_X, gz = pad.z0 + GOAL_Z;
        helper.runAfterDelay(SETUP, () -> {
            Mob m = type.get().create(level);
            if (m == null)
                throw new GameTestAssertException("could not create the " + what + " unit");
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
        int[] touch = { 0, -1 };   // ticks touching a keep-off cell, the first of them
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
            AABB body = m.getBoundingBox();
            for (AABB k : pad.keepOff)
                if (k.intersects(body)) {
                    if (touch[0]++ == 0)
                        touch[1] = t;
                    break;
                }
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
                if (t > 360)
                    throw new GameTestAssertException(String.format(Locale.ROOT, "the unit did not reach the goal in 360"
                        + " ticks (%.1f blocks left, touched a keep-off cell %d ticks from t%d):%s", left, touch[0], touch[1],
                        trace));
                return;
            }
            done[0] = true;
            float net = Float.isNaN(turn[1]) ? 0f : Math.abs(turn[2] - turn[1]);
            float rock = turn[0] - net;
            String line = String.format(Locale.ROOT, "[Walk] %s (%s pathing): arrived in %d ticks, turned %.1f degrees for"
                + " a net %.1f - rocking %.1f (bound %.0f%s); largest swing off the bearing %.1f (t%d; bound %.0f%s);"
                + " touched a keep-off cell %d ticks (bound %d); yaw/bearing:%s", what, rts ? "RTS" : "vanilla", t,
                turn[0], net, rock, MAX_ROCK, pad.bends ? ", waived: the walk bends" : "", turn[3], worstTick[0], MAX_OFF,
                pad.bends ? ", waived" : "", touch[0], MAX_TOUCH, trace);
            ReignOfNether.LOGGER.warn(line);
            if (Float.isNaN(turn[1]))
                throw new GameTestAssertException("the walk was too short to judge: " + line);
            if (touch[0] > MAX_TOUCH)
                throw new GameTestAssertException("the body pushed into a block it must keep off: " + line);
            if (!pad.bends && rock > MAX_ROCK)
                throw new GameTestAssertException("the body rocks from side to side on a straight line: " + line);
            if (!pad.bends && turn[3] > MAX_OFF)
                throw new GameTestAssertException("the body faces off the straight line to the goal: " + line);
            helper.succeed();
        });
    }
}
