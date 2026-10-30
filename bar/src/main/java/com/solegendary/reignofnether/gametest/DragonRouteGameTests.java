package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.controls.DragonFlight;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The dragons' way past buildings (lovish after a match, Oct 30: "gets stuck on buildings, isnt fluid at all"). A
 * dragon's flight point is ten blocks over the ground at its destination, so a keep or a tower on the way stood in the
 * line: the sweep saw it a dozen blocks out, the dragon slowed to a crawl and climbed its wall at the gentle climb's cap
 * (stop-go), and with the way up shut too (an overhang) it waited, the move goal re-issued the order next tick and it
 * pressed into the wall again - for good.
 *
 * Each test flies one dragon down a 56 x 24 glass course far from the grid (its chunks held): sent over a wall it must
 * arrive in time, never touch it, and keep its way on (no stop-go); boxed under a roof it must get out and arrive.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DragonRouteGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "dragon_routes";   // far courses, chunks held: one batch of their own
    /** The course floor's height: above any natural peak, with room for the wall and the climb under the build cap. */
    static final int PAD_Y = 250;
    static final int W = 56, D = 24;
    /** The wall's top surface above the floor (the dragon starts at its hover height, ten over the floor). */
    static final int WALL_TOP = 30;
    /** Ticks after the order by which a dragon flown over the wall must be over its point (open air: ~260). */
    static final int ARRIVE_BY = 420;
    static final int SETUP = 20;   // ticks between laying the course and the order (chunk sends, entity sections)

    record Course(int x0, int z0, List<long[]> chunks) {
        BlockPos at(int dx, int dy, int dz) {
            return new BlockPos(x0 + dx, PAD_Y + dy, z0 + dz);
        }
    }

    /** The course for {@code slot}: 12000 west and north of the grid, its chunks held and loaded, a glass floor. */
    static Course course(GameTestHelper helper, int slot) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int x0 = (((base.getX() - 12000) >> 4) << 4) + 2, z0 = (((base.getZ() - 12000 - slot * 400) >> 4) << 4) + 2;
        List<long[]> chunks = new ArrayList<>();
        for (int cx = (x0 - 16) >> 4; cx <= (x0 + W + 16) >> 4; cx++)
            for (int cz = (z0 - 16) >> 4; cz <= (z0 + D + 16) >> 4; cz++)
                chunks.add(new long[] { cx, cz });
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "dragon route course");
        GameTestChunks.awaitRingOrFail(level, chunks, "dragon route course");
        Course c = new Course(x0, z0, chunks);
        TestCleanup.atEnd(helper, () -> GameTestChunks.releaseAll(level, chunks));
        TestCleanup.BlockUndo blocks = TestCleanup.blocks(helper);
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (int dx = 0; dx < W; dx++)
            for (int dz = 0; dz < D; dz++)
                blocks.set(c.at(dx, 0, dz), glass);
        return c;
    }

    /** A stone box from (dx0, dy0, dz0) to (dx1, dy1, dz1) on the course, corners included. */
    static void stone(GameTestHelper helper, Course c, int dx0, int dy0, int dz0, int dx1, int dy1, int dz1) {
        TestCleanup.BlockUndo blocks = TestCleanup.blocks(helper);
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int dx = dx0; dx <= dx1; dx++)
            for (int dy = dy0; dy <= dy1; dy++)
                for (int dz = dz0; dz <= dz1; dz++)
                    blocks.set(c.at(dx, dy, dz), stone);
    }

    static Mob spawn(GameTestHelper helper, EntityType<? extends Mob> type, double x, double y, double z, float yaw) {
        Mob m = type.create(helper.getLevel());
        if (!(m instanceof Unit u))
            throw new net.minecraft.gametest.framework.GameTestAssertException("could not create " + EntityType.getKey(type));
        m.moveTo(x, y, z, yaw, 0);
        u.setOwnerName("gametest_dragon_route");
        helper.getLevel().addFreshEntity(m);
        TestCleanup.entities(helper, List.of(m));
        return m;
    }

    // ------------------------------------------------------------------------------------------------ over a wall

    @GameTest(template = ARENA, timeoutTicks = SETUP + ARRIVE_BY + 20, batch = BATCH)
    public static void bone_dragon_flies_over_a_wall_without_stopping(GameTestHelper helper) {
        overAWall(helper, 0, EntityRegistrar.BONE_DRAGON_UNIT.get());
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + ARRIVE_BY + 20, batch = BATCH)
    public static void celestial_dragon_flies_over_a_wall_without_stopping(GameTestHelper helper) {
        overAWall(helper, 1, EntityRegistrar.CELESTIAL_DRAGON_UNIT.get());
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + ARRIVE_BY + 20, batch = BATCH)
    public static void thunder_dragon_flies_over_a_wall_without_stopping(GameTestHelper helper) {
        overAWall(helper, 2, EntityRegistrar.THUNDER_DRAGON_UNIT.get());
    }

    /**
     * Sent 42 blocks down the course over a wall that stands 20 blocks above its flight (the course's whole width), the
     * dragon must be over its point by {@link #ARRIVE_BY} ticks, never touch the wall, and - once it is up to speed and
     * until the last ten blocks - never fall under 0.6 x its cruise speed over the ground: it rises over the wall in its
     * stride, not by stopping at it and climbing.
     */
    static void overAWall(GameTestHelper helper, int slot, EntityType<? extends Mob> type) {
        Course c = course(helper, slot);
        stone(helper, c, 26, 1, 0, 26, WALL_TOP - 1, D - 1);
        Mob dragon = spawn(helper, type, c.x0() + 6.5, PAD_Y + 10.5, c.z0() + 12.5, -90f);   // facing +x, down the course
        BlockPos target = c.at(48, 0, 12);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
        var ms = dragon.getAttribute(Attributes.MOVEMENT_SPEED);
        double cruise = DragonFlight.cruiseSpeed(ms == null ? 0.0 : ms.getValue());
        helper.runAfterDelay(SETUP, () -> ((Unit) dragon).getMoveGoal().setMoveTarget(target));
        final double[] last = { Double.NaN, Double.NaN };
        final double[] slowest = { Double.MAX_VALUE };
        final int[] arrived = { -1 }, touched = { 0 }, upToSpeed = { 0 };
        final String[] slowAt = { "" }, touchedAt = { "" };
        final List<String> trace = new ArrayList<>();
        for (int t = SETUP + 1; t <= SETUP + ARRIVE_BY; t++) {
            final int tick = t - SETUP;
            helper.runAfterDelay(t, () -> {
                double x = dragon.getX(), z = dragon.getZ();
                double flat = Math.sqrt((tx - x) * (tx - x) + (tz - z) * (tz - z));
                trace.add(String.format("t%d %.1f %.1f %.1f v %.3f%s", tick, x - c.x0(), dragon.getY() - PAD_Y, z - c.z0(),
                    Double.isNaN(last[0]) ? 0.0 : Math.hypot(x - last[0], z - last[1]),
                    dragon.horizontalCollision ? " BUMP" : ""));
                if (dragon.horizontalCollision || (dragon.verticalCollision && !dragon.onGround())) {
                    if (touched[0]++ == 0)
                        touchedAt[0] = trace.get(trace.size() - 1);
                }
                if (!Double.isNaN(last[0])) {
                    double speed = Math.hypot(x - last[0], z - last[1]);
                    if (speed >= 0.8 * cruise)
                        upToSpeed[0] = 1;
                    if (upToSpeed[0] == 1 && flat > 10 && speed < slowest[0]) {
                        slowest[0] = speed;
                        slowAt[0] = String.join(" | ", trace.subList(Math.max(0, trace.size() - 3), trace.size()));
                    }
                }
                last[0] = x;
                last[1] = z;
                if (arrived[0] < 0 && flat < 3.0)
                    arrived[0] = tick;
            });
        }
        helper.runAfterDelay(SETUP + ARRIVE_BY + 2, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (arrived[0] < 0)
                    problems.add("not over its point " + ARRIVE_BY + " ticks after the order (" + tail(trace) + ")");
                if (touched[0] > 0)
                    problems.add(touched[0] + " ticks against the wall, first " + touchedAt[0]);
                if (upToSpeed[0] == 0)
                    problems.add(String.format("never reached 0.8 x cruise (%.3f b/t)", cruise));
                else if (slowest[0] < 0.6 * cruise)
                    problems.add(String.format("slowed to %.3f b/t on the way (under 0.6 x cruise %.3f): %s", slowest[0],
                        cruise, slowAt[0]));
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(EntityType.getKey(type) + ": " + String.join("; ", problems));
            } finally {
                dragon.discard();
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ under a roof

    /**
     * A Bone Dragon hovering low under a porch roof (the roof a block over its back, so it cannot rise even one block)
     * with a wall in front of it is sent to a point beyond the wall. It used to sweep ahead (shut), try one block up
     * (shut), wait, be re-ordered by its move goal the next tick and press into the wall again, for good; it must side
     * out from under the roof and arrive.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + 520, batch = BATCH)
    public static void bone_dragon_boxed_under_a_roof_gets_out(GameTestHelper helper) {
        Course c = course(helper, 3);
        // the dragon's body (3.15 wide, 1.75 high) from floor + 1.5: the roof's underside at floor + 4
        stone(helper, c, 1, 4, 7, 12, 4, 17);          // the roof, 12 x 11
        stone(helper, c, 11, 1, 5, 11, 12, 19);       // the wall in front, under the roof and on up past it
        Mob dragon = spawn(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), c.x0() + 6.5, PAD_Y + 1.5, c.z0() + 12.5, -90f);
        BlockPos target = c.at(36, 0, 12);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
        helper.runAfterDelay(SETUP, () -> ((Unit) dragon).getMoveGoal().setMoveTarget(target));
        final int[] arrived = { -1 };
        final List<String> trace = new ArrayList<>();
        for (int t = SETUP + 1; t <= SETUP + 500; t++) {
            final int tick = t - SETUP;
            helper.runAfterDelay(t, () -> {
                double x = dragon.getX(), z = dragon.getZ();
                if (tick % 10 == 0)
                    trace.add(String.format("t%d %.1f %.1f %.1f", tick, x - c.x0(), dragon.getY() - PAD_Y, z - c.z0()));
                if (arrived[0] < 0 && Math.hypot(tx - x, tz - z) < 3.0)
                    arrived[0] = tick;
            });
        }
        helper.runAfterDelay(SETUP + 502, () -> {
            try {
                if (arrived[0] >= 0)
                    helper.succeed();
                else
                    helper.fail("the Bone Dragon is still boxed in 500 ticks after the order: " + tail(trace));
            } finally {
                dragon.discard();
            }
        });
    }

    private static String tail(List<String> trace) {
        return trace.isEmpty() ? "no samples" : String.join(" | ", trace.subList(Math.max(0, trace.size() - 4), trace.size()));
    }
}
