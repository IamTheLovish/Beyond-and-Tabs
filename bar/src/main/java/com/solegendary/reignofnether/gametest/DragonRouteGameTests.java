package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
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
import net.minecraft.world.level.levelgen.Heightmap;
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
 * lovish (Oct 30) on how: "increase max flying high by a bit then go around if it cant fly over that new max", and
 * then "Ground under the dragon": a dragon may climb to {@link #CEILING} over whatever is below it right now (it climbs
 * over hills and towers on the way) and flies round only what stands taller than that; and "can be higher if it needs
 * to fly over hills or mountains": rising ground is flown over however high it goes.
 *
 * Each test flies one dragon down a 56 x 24 glass course far from the grid (its chunks held): sent over a wall under
 * that ceiling it must arrive in time, never touch it, keep its way on (no stop-go) and never rise past the ceiling;
 * sent at a tower far taller it must fly round it under the ceiling; boxed under a roof it must get out and arrive.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DragonRouteGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "dragon_routes";   // far courses, chunks held: one batch of their own
    /** The course floor's height: above any natural peak, with room for the wall and the climb under the build cap. */
    static final int PAD_Y = 250;
    static final int W = 56, D = 24;
    /** The wall's top surface above the floor (the dragon starts at its hover height, ten over the floor): its feet
     *  must rise 5 blocks to cross it, under the ceiling. */
    static final int WALL_TOP = 14;
    /** How high over the ground right under it (the MOTION_BLOCKING heightmap of its column) a dragon may fly. */
    static final double CEILING = 16.0;
    /** How far past its ceiling a sample may read (float noise only: the climb must stop AT it, not a block over). */
    static final double OVERSHOOT = 0.25;

    /**
     * How far the dragon's feet are over its ceiling right now while it CLIMBS (negative: under it, or not climbing: a
     * dragon that flies off a cliff is over its new ceiling without having climbed past it).
     */
    static double overCeiling(Mob dragon) {
        if (dragon.getDeltaMovement().y <= 0.005)
            return -1e9;
        int ground = dragon.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(dragon.getX()),
            (int) Math.floor(dragon.getZ()));
        return dragon.getY() - (ground + CEILING);
    }
    /** The tower's top surface above the floor: far over the ceiling (it must go round). */
    static final int TOWER_TOP = 40;
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
     * Sent 42 blocks down the course over a wall (the course's whole width) whose top its feet must rise 5 blocks to
     * clear - under its ceiling - the dragon must be over its point by {@link #ARRIVE_BY} ticks, never touch the wall,
     * never climb past its ceiling, and - once it is up to speed and until the last ten blocks - never fall under 0.6 x
     * its cruise speed over the ground: it rises over the wall in its stride, not by stopping at it and climbing.
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
        final double[] highest = { -1e9 };
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
                highest[0] = Math.max(highest[0], overCeiling(dragon));
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
                if (highest[0] > OVERSHOOT)
                    problems.add(String.format("climbed %.2f blocks past its ceiling (%.0f over the ground under it)",
                        highest[0], CEILING));
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

    // ------------------------------------------------------------------------------------------------ round a tower

    /**
     * A 6 x 6 tower {@link #TOWER_TOP} high stands across the line to the point, far above the ceiling: the Bone Dragon
     * must fly round it - over its point by {@link #ARRIVE_BY} + 80 ticks, never touching it, never higher than its
     * ceiling. It used to see the tower a dozen blocks out and climb its face until the way was clear.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + ARRIVE_BY + 100, batch = BATCH)
    public static void bone_dragon_flies_round_a_tower_too_tall_to_cross(GameTestHelper helper) {
        Course c = course(helper, 4);
        stone(helper, c, 24, 1, 9, 29, TOWER_TOP - 1, 15);
        Mob dragon = spawn(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), c.x0() + 6.5, PAD_Y + 10.5, c.z0() + 12.5, -90f);
        BlockPos target = c.at(48, 0, 12);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
        helper.runAfterDelay(SETUP, () -> ((Unit) dragon).getMoveGoal().setMoveTarget(target));
        final int[] arrived = { -1 }, touched = { 0 };
        final double[] highest = { -1e9 };
        final String[] touchedAt = { "" };
        final List<String> trace = new ArrayList<>();
        for (int t = SETUP + 1; t <= SETUP + ARRIVE_BY + 80; t++) {
            final int tick = t - SETUP;
            helper.runAfterDelay(t, () -> {
                double x = dragon.getX(), z = dragon.getZ();
                if (tick % 10 == 0)
                    trace.add(String.format("t%d %.1f %.1f %.1f", tick, x - c.x0(), dragon.getY() - PAD_Y, z - c.z0()));
                if (dragon.horizontalCollision || (dragon.verticalCollision && !dragon.onGround())) {
                    if (touched[0]++ == 0)
                        touchedAt[0] = String.format("t%d %.1f %.1f %.1f", tick, x - c.x0(), dragon.getY() - PAD_Y, z - c.z0());
                }
                highest[0] = Math.max(highest[0], overCeiling(dragon));
                if (arrived[0] < 0 && Math.hypot(tx - x, tz - z) < 3.0)
                    arrived[0] = tick;
            });
        }
        helper.runAfterDelay(SETUP + ARRIVE_BY + 82, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (arrived[0] < 0)
                    problems.add("not over its point " + (ARRIVE_BY + 80) + " ticks after the order (" + tail(trace) + ")");
                if (touched[0] > 0)
                    problems.add(touched[0] + " ticks against the tower, first " + touchedAt[0]);
                if (highest[0] > OVERSHOOT)
                    problems.add(String.format("climbed %.2f blocks past its ceiling (%.0f over the ground under it) "
                        + "instead of going round", highest[0], CEILING));
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                dragon.discard();
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ from high ground

    /**
     * lovish's rule is the ground UNDER the dragon, not at its point: from a plateau 6 blocks over its point's ground, a
     * tower 12 over the plateau (19 over the point's ground: past a ceiling counted from the point) is under its
     * ceiling, so the Bone Dragon climbs over it in its stride - no detour (never 1.5 blocks off the line), never
     * touching it, never under 0.6 x cruise once up to speed, never past its ceiling - and is over its point in time.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + ARRIVE_BY + 20, batch = BATCH)
    public static void bone_dragon_climbs_a_tower_under_its_ceiling_from_high_ground(GameTestHelper helper) {
        Course c = course(helper, 5);
        stone(helper, c, 0, 1, 0, 39, 6, D - 1);              // the plateau: its top surface 7 over the floor
        stone(helper, c, 24, 7, 9, 29, 18, 15);              // the tower on it: its top 12 over the plateau
        Mob dragon = spawn(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), c.x0() + 6.5, PAD_Y + 17.5, c.z0() + 12.5, -90f);
        BlockPos target = c.at(48, 0, 12);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5, lineZ = c.z0() + 12.5;
        var ms = dragon.getAttribute(Attributes.MOVEMENT_SPEED);
        double cruise = DragonFlight.cruiseSpeed(ms == null ? 0.0 : ms.getValue());
        helper.runAfterDelay(SETUP, () -> ((Unit) dragon).getMoveGoal().setMoveTarget(target));
        final double[] last = { Double.NaN, Double.NaN }, worst = { Double.MAX_VALUE, 0, -1e9 };   // slowest, off, over
        final int[] arrived = { -1 }, touched = { 0 }, upToSpeed = { 0 };
        final List<String> trace = new ArrayList<>();
        for (int t = SETUP + 1; t <= SETUP + ARRIVE_BY; t++) {
            final int tick = t - SETUP;
            helper.runAfterDelay(t, () -> {
                double x = dragon.getX(), z = dragon.getZ();
                double flat = Math.hypot(tx - x, tz - z);
                if (tick % 10 == 0)
                    trace.add(String.format("t%d %.1f %.1f %.1f", tick, x - c.x0(), dragon.getY() - PAD_Y, z - c.z0()));
                if (dragon.horizontalCollision || (dragon.verticalCollision && !dragon.onGround()))
                    touched[0]++;
                worst[1] = Math.max(worst[1], Math.abs(z - lineZ));
                worst[2] = Math.max(worst[2], overCeiling(dragon));
                if (!Double.isNaN(last[0])) {
                    double speed = Math.hypot(x - last[0], z - last[1]);
                    if (speed >= 0.8 * cruise)
                        upToSpeed[0] = 1;
                    if (upToSpeed[0] == 1 && flat > 10)
                        worst[0] = Math.min(worst[0], speed);
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
                    problems.add(touched[0] + " ticks against the plateau or the tower");
                if (worst[1] > 1.5)
                    problems.add(String.format("went %.1f blocks off the line: a detour round a tower under its ceiling (%s)",
                        worst[1], tail(trace)));
                if (upToSpeed[0] == 0)
                    problems.add(String.format("never reached 0.8 x cruise (%.3f b/t)", cruise));
                else if (worst[0] < 0.6 * cruise)
                    problems.add(String.format("slowed to %.3f b/t on the way (under 0.6 x cruise %.3f)", worst[0], cruise));
                if (worst[2] > OVERSHOOT)
                    problems.add(String.format("climbed %.2f blocks past its ceiling", worst[2]));
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                dragon.discard();
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ over a hill

    /**
     * lovish: "can be higher if it needs to fly over hills or mountains". A ridge 13 wide across the line rises a block
     * per block to 18 over the floor and falls again: its top is past a ceiling counted from the floor the dragon starts
     * over, but it is rising ground, not a tower, so the Bone Dragon flies straight over it - never 1.5 blocks off the
     * line, never touching it - and is over its point in time.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + ARRIVE_BY + 20, batch = BATCH)
    public static void bone_dragon_flies_over_a_hill_higher_than_its_ceiling(GameTestHelper helper) {
        Course c = course(helper, 6);
        for (int dx = 14; dx <= 47; dx++) {
            int h = Math.max(1, 18 - Math.abs(dx - 31));
            stone(helper, c, dx, 1, 6, dx, h, 18);
        }
        Mob dragon = spawn(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), c.x0() + 6.5, PAD_Y + 10.5, c.z0() + 12.5, -90f);
        BlockPos target = c.at(52, 0, 12);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5, lineZ = c.z0() + 12.5;
        helper.runAfterDelay(SETUP, () -> ((Unit) dragon).getMoveGoal().setMoveTarget(target));
        final double[] off = { 0 };
        final int[] arrived = { -1 }, touched = { 0 };
        final List<String> trace = new ArrayList<>();
        for (int t = SETUP + 1; t <= SETUP + ARRIVE_BY; t++) {
            final int tick = t - SETUP;
            helper.runAfterDelay(t, () -> {
                double x = dragon.getX(), z = dragon.getZ();
                if (tick % 10 == 0)
                    trace.add(String.format("t%d %.1f %.1f %.1f", tick, x - c.x0(), dragon.getY() - PAD_Y, z - c.z0()));
                if (dragon.horizontalCollision || (dragon.verticalCollision && !dragon.onGround()))
                    touched[0]++;
                off[0] = Math.max(off[0], Math.abs(z - lineZ));
                if (arrived[0] < 0 && Math.hypot(tx - x, tz - z) < 3.0)
                    arrived[0] = tick;
            });
        }
        helper.runAfterDelay(SETUP + ARRIVE_BY + 2, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (arrived[0] < 0)
                    problems.add("not over its point " + ARRIVE_BY + " ticks after the order (" + tail(trace) + ")");
                if (touched[0] > 0)
                    problems.add(touched[0] + " ticks against the hill");
                if (off[0] > 1.5)
                    problems.add(String.format("went %.1f blocks off the line: round a hill it should fly over (%s)",
                        off[0], tail(trace)));
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                dragon.discard();
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ over the max

    /** Its own batch: it lowers the world's flyingMaxYLevel rule while it runs, which no other test may see. */
    static final String MAX_BATCH = "dragon_routes_max";

    /**
     * A hill it could climb (the ridge of {@link #bone_dragon_flies_over_a_hill_higher_than_its_ceiling}) whose top is
     * past the flyingMaxYLevel rule (lowered to 15 over the floor for this test): it cannot go over, so the Bone Dragon
     * must fly round it - never touching it, never past the rule - and be over its point in time. Held at the rule's
     * height with the way ahead shut, it used to press on into the slope at the blocked crawl, for good.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + ARRIVE_BY + 100, batch = MAX_BATCH)
    public static void bone_dragon_flies_round_a_mountain_past_the_flying_max(GameTestHelper helper) {
        var rule = helper.getLevel().getGameRules().getRule(GameRuleRegistrar.FLYING_MAX_Y_LEVEL);
        int was = rule.get();
        TestCleanup.atEnd(helper, () -> rule.set(was, helper.getLevel().getServer()));
        Course c = course(helper, 7);
        rule.set(PAD_Y + 15, helper.getLevel().getServer());
        for (int dx = 14; dx <= 47; dx++) {
            int h = Math.max(1, 18 - Math.abs(dx - 31));
            stone(helper, c, dx, 1, 6, dx, h, 18);
        }
        Mob dragon = spawn(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), c.x0() + 6.5, PAD_Y + 10.5, c.z0() + 12.5, -90f);
        BlockPos target = c.at(52, 0, 12);
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
        helper.runAfterDelay(SETUP, () -> ((Unit) dragon).getMoveGoal().setMoveTarget(target));
        final int[] arrived = { -1 }, touched = { 0 };
        final double[] highest = { -1e9 };
        final List<String> trace = new ArrayList<>();
        for (int t = SETUP + 1; t <= SETUP + ARRIVE_BY + 80; t++) {
            final int tick = t - SETUP;
            helper.runAfterDelay(t, () -> {
                double x = dragon.getX(), z = dragon.getZ();
                if (tick % 10 == 0)
                    trace.add(String.format("t%d %.1f %.1f %.1f", tick, x - c.x0(), dragon.getY() - PAD_Y, z - c.z0()));
                if (dragon.horizontalCollision || (dragon.verticalCollision && !dragon.onGround()))
                    touched[0]++;
                highest[0] = Math.max(highest[0], dragon.getY() - PAD_Y);
                if (arrived[0] < 0 && Math.hypot(tx - x, tz - z) < 3.0)
                    arrived[0] = tick;
            });
        }
        helper.runAfterDelay(SETUP + ARRIVE_BY + 82, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (arrived[0] < 0)
                    problems.add("not over its point " + (ARRIVE_BY + 80) + " ticks after the order (" + tail(trace) + ")");
                if (touched[0] > 0)
                    problems.add(touched[0] + " ticks against the mountain");
                if (highest[0] > 15 + OVERSHOOT)
                    problems.add(String.format("flew %.2f over the floor, past the flyingMaxYLevel rule (15)", highest[0]));
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                dragon.discard();
                rule.set(was, helper.getLevel().getServer());
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
