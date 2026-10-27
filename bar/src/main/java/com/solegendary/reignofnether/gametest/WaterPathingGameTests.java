package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.pathfinding.ChunkSnapshot;
import com.solegendary.reignofnether.unit.pathfinding.GridAStar;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import com.solegendary.reignofnether.unit.pathfinding.PathConverter;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig;
import com.solegendary.reignofnether.unit.pathfinding.RtsPathfinder;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Water pathing on the RTS grid (WalkabilityBuilder.KIND_SHALLOW_WATER, PathfinderConfig water costs). Until the
 * shallow kind every wet cell cost a land unit 25x, a ford as much as the deep channel beside it, so A* crossed a
 * river wherever it was narrowest and swam the deep part instead of using the ford a few blocks along.
 * <p>
 * One stone basin, in this test's own arena column at a height no other pad uses (ground block at {@link #G}), split by
 * a wall into two halves, each with a river across it: the east half's river has a 3-wide ford (one block of water
 * on stone, as the generated maps' fords) beside a deep channel, plus a deep pool on the near bank; the west half's
 * river is deep all the way, with no land way round. Verdicts: a land unit's path in the east crosses at the ford and
 * never enters deep water (pool or channel); in the west it still swims (deep water is a cost, never a wall); and a
 * Vindicator given the east path really walks it, through the ford, to the far bank. The path is computed with the
 * grid A* directly (as BigUnitFeelGameTests does) and handed to the unit's navigation, so the verdict holds whatever
 * the rtsPathfinding gamerule says on this server (off in CI). One test, one build, one snapshot: a second test
 * sharing these chunks could read a walkability chunk cached before its own blocks were placed.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WaterPathingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** The ground block's absolute height (feet at G + 1). The basin uses G - 3 .. G + 6. */
    static final int G = 135;
    /** River rows (template z), deep channel / ford columns of the east half, the divider and outer walls. */
    static final int RIVER_Z0 = 6, RIVER_Z1 = 9, FORD_X0 = 12, FORD_X1 = 14, DIVIDER_X = 9;
    /** The deep pool on the east half's near bank. */
    static final int POOL_X0 = 11, POOL_X1 = 12, POOL_Z0 = 2, POOL_Z1 = 3;
    /** Ticks the Vindicator gets to reach the far bank once ticking (about 17 blocks, 4 of them wading). */
    static final int WALK_LIMIT = 500;
    /** How close (horizontally, blocks) to the goal cell's centre counts as arrived. */
    static final double ARRIVED = 1.5;

    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void land_unit_crosses_a_river_at_the_ford_not_the_deep_channel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        final int cx = corner.getX(), cz = corner.getZ();
        build(level, cx, cz);
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();

        // the cells: a ford is wading water, the channel and the pool are deep, the bank is land
        expectKind(level, problems, "a ford cell", cx + 13, G, cz + 7, WalkabilityBuilder.KIND_SHALLOW_WATER);
        expectKind(level, problems, "the deep channel's surface", cx + 10, G, cz + 7, WalkabilityBuilder.KIND_WATER);
        expectKind(level, problems, "a submerged channel cell", cx + 10, G - 1, cz + 7, WalkabilityBuilder.KIND_WATER);
        expectKind(level, problems, "the deep pool", cx + 11, G, cz + 2, WalkabilityBuilder.KIND_WATER);
        expectKind(level, problems, "the near bank", cx + 10, G + 1, cz + 1, WalkabilityBuilder.KIND_LAND);

        BlockPos eastStart = new BlockPos(cx + 10, G + 1, cz + 1), eastGoal = new BlockPos(cx + 10, G + 1, cz + 13);
        BlockPos westStart = new BlockPos(cx + 4, G + 1, cz + 2), westGoal = new BlockPos(cx + 4, G + 1, cz + 13);
        ChunkSnapshot east = snapshot(level, eastStart, eastGoal);
        GridAStar.Result e = GridAStar.search(east, eastStart, eastGoal, 0, PathfinderConfig.MAX_RADIUS, 60000);
        int shallowNodes = 0;
        for (BlockPos p : e.waypoints) {
            byte kind = east.kindAt(p.getX(), p.getY(), p.getZ());
            if (kind == WalkabilityBuilder.KIND_WATER)
                problems.add("the east path enters deep water at " + rel(p, cx, cz) + ": " + trace(e.waypoints, cx, cz));
            if (kind == WalkabilityBuilder.KIND_SHALLOW_WATER)
                shallowNodes++;
            int tz = p.getZ() - cz, tx = p.getX() - cx;
            if (tz >= RIVER_Z0 && tz <= RIVER_Z1 && (tx < FORD_X0 || tx > FORD_X1))
                problems.add("the east path is in the river outside the ford at " + rel(p, cx, cz));
            if (problems.size() > 4)
                break;
        }
        if (!e.reached)
            problems.add("the east path did not reach the far bank: " + trace(e.waypoints, cx, cz));
        if (shallowNodes == 0)
            problems.add("the east path never wades the ford: " + trace(e.waypoints, cx, cz));

        ChunkSnapshot west = snapshot(level, westStart, westGoal);
        GridAStar.Result w = GridAStar.search(west, westStart, westGoal, 0, PathfinderConfig.MAX_RADIUS, 60000);
        boolean swam = false;
        for (BlockPos p : w.waypoints)
            swam |= west.kindAt(p.getX(), p.getY(), p.getZ()) == WalkabilityBuilder.KIND_WATER;
        if (!w.reached || !swam)
            problems.add("with no ford and no land way round, a land unit should still swim the river (reached " + w.reached
                + ", swam " + swam + "): " + trace(w.waypoints, cx, cz));

        if (!problems.isEmpty()) {
            clear(level, cx, cz);
            helper.fail(String.join("; ", problems));
            return;
        }

        // the walk: a Vindicator given the east path reaches the far bank, crossing the river inside the ford
        VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (v == null) {
            clear(level, cx, cz);
            helper.fail("could not create the Vindicator");
            return;
        }
        v.moveTo(eastStart.getX() + 0.5, eastStart.getY(), eastStart.getZ() + 0.5, 0, 0);
        v.setOwnerName("gametest_waterpath_" + corner.asLong());
        level.addFreshEntity(v);
        made.add(v);
        EngagementStance.set(v, EngagementStance.HOLD_FIRE);
        final double gx = eastGoal.getX() + 0.5, gz = eastGoal.getZ() + 0.5, midZ = cz + (RIVER_Z0 + RIVER_Z1 + 1) / 2.0;
        final int[] orderedAt = { -1 };
        final double[] crossX = { Double.NaN };
        final double[] lastZ = { Double.NaN };
        final boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 200, tick -> {
            orderedAt[0] = tick;
            if (MobilityClass.of(v) != MobilityClass.HUMANOID)
                ReignOfNether.LOGGER.info("[Water] the Vindicator is mobility {}, expected HUMANOID", MobilityClass.of(v));
            int clearance = Math.max(2, Mth.ceil(v.getBbHeight()));
            ChunkSnapshot view = ChunkSnapshot.capture(level, eastStart, eastGoal, 16, MobilityClass.HUMANOID, clearance,
                RtsPathfinder.footprintRadiusFor(v), 1.0f, false);
            GridAStar.Result r = GridAStar.search(view, eastStart, eastGoal, 0, PathfinderConfig.MAX_RADIUS, 60000);
            Path path = PathConverter.toMcPath(r.waypoints, eastGoal, r.reached, view);
            // what MoveToTargetBlockGoal does before following an RTS path
            v.setMaxUpStep(1.15f);
            if (v.getNavigation() instanceof GroundPathNavigation gpn)
                gpn.setCanFloat(true);
            v.getNavigation().moveTo(path, 1.0);
        });
        helper.onEachTick(() -> {
            if (orderedAt[0] < 0 || done[0])
                return;
            // where the body crosses the river's middle line, on the tick it crosses
            if (!Double.isNaN(lastZ[0]) && Double.isNaN(crossX[0]) && lastZ[0] < midZ && v.getZ() >= midZ)
                crossX[0] = v.getX() - cx;
            lastZ[0] = v.getZ();
            int since = (int) helper.getTick() - orderedAt[0];
            double dx = v.getX() - gx, dz = v.getZ() - gz;
            boolean arrived = Math.sqrt(dx * dx + dz * dz) <= ARRIVED && v.getY() >= G + 0.9;
            if (!arrived && since < WALK_LIMIT)
                return;
            done[0] = true;
            try {
                String state = String.format(Locale.ROOT, "after %d ticks at %.2f/%.2f/%.2f (template), crossed the river at x %.2f; %s",
                    since, v.getX() - cx, v.getY() - G, v.getZ() - cz, crossX[0], ArenaReady.around(v, gx, gz));
                ReignOfNether.LOGGER.info("[Water] ford walk: {}", state);
                List<String> walk = new ArrayList<>();
                if (!arrived)
                    walk.add("the Vindicator did not reach the far bank within " + WALK_LIMIT + " ticks: " + state);
                // the ford spans template x 12..15 (cells 12-14); a body crossing at the deep channel reads 10-12
                if (Double.isNaN(crossX[0]) || crossX[0] < FORD_X0 - 0.3 || crossX[0] > FORD_X1 + 1.0)
                    walk.add("the Vindicator did not cross inside the ford (x " + FORD_X0 + ".." + (FORD_X1 + 1) + "): " + state);
                if (walk.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", walk));
            } finally {
                for (Entity m : made)
                    m.discard();
                clear(level, cx, cz);
            }
        });
    }

    static ChunkSnapshot snapshot(ServerLevel level, BlockPos start, BlockPos goal) {
        return ChunkSnapshot.capture(level, start, goal, 16, MobilityClass.HUMANOID, 2, 0, 1.0f, false);
    }

    static void expectKind(ServerLevel level, List<String> problems, String what, int x, int y, int z, byte want) {
        byte got = WalkabilityBuilder.classify(level, x, y, z);
        if (got != want)
            problems.add(what + " classifies as kind " + got + ", expected " + want + " (" + level.getBlockState(new BlockPos(x, y, z)) + ")");
    }

    /** Stone from G - 3 up to the ground at G, air above; walls round and down the middle; then the water. */
    static void build(ServerLevel level, int cx, int cz) {
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                boolean wall = x == 0 || x == 15 || x == DIVIDER_X;
                for (int y = G - 3; y <= G + 6; y++)
                    level.setBlock(new BlockPos(cx + x, y, cz + z), y <= G || (wall && y <= G + 3) ? stone : air, 2);
            }
        for (int x = 1; x < 15; x++) {
            if (x == DIVIDER_X)
                continue;
            boolean ford = x >= FORD_X0 && x <= FORD_X1;
            for (int z = RIVER_Z0; z <= RIVER_Z1; z++)
                for (int y = ford ? G : G - 2; y <= G; y++)
                    level.setBlock(new BlockPos(cx + x, y, cz + z), water, 2);
        }
        for (int x = POOL_X0; x <= POOL_X1; x++)
            for (int z = POOL_Z0; z <= POOL_Z1; z++)
                for (int y = G - 1; y <= G; y++)
                    level.setBlock(new BlockPos(cx + x, y, cz + z), water, 2);
    }

    /** Water out first (so nothing flows), then the stone. */
    static void clear(ServerLevel level, int cx, int cz) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int pass = 0; pass < 2; pass++)
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    for (int y = G - 3; y <= G + 6; y++) {
                        BlockPos p = new BlockPos(cx + x, y, cz + z);
                        if (pass == 1 || !level.getFluidState(p).isEmpty())
                            level.setBlock(p, air, 2);
                    }
    }

    static String rel(BlockPos p, int cx, int cz) {
        return (p.getX() - cx) + "/" + (p.getY() - G) + "/" + (p.getZ() - cz);
    }

    static String trace(List<BlockPos> path, int cx, int cz) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : path)
            sb.append(rel(p, cx, cz)).append(' ');
        return sb.toString().trim();
    }
}
