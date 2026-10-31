package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityGrid;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityGridChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The walkability cache must not keep the world as it was when the rtsPathfinding flag was last on. Block changes made
 * while the flag is off used to be ignored (LevelChunkMixin marked chunks dirty only with the flag on, and nothing
 * drains them with it off), so a chunk cached earlier was served stale the moment the flag came back on: the graveyard
 * probe (flake-stress 40996361532) saw the cells past an open gate cached as blocked while the world was open.
 * Runs in one tick, so nothing else sees the flag change; it is put back however the test ends.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WalkabilityGridFlagGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void a_cached_cell_opened_while_rts_pathfinding_is_off_reads_walkable_when_it_is_back_on(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos feet = floor.above();
        int bandMin = Math.max(level.getMinBuildHeight(), feet.getY() - 4);
        int bandMax = Math.min(level.getMaxBuildHeight(), feet.getY() + 6);
        WalkabilityGrid grid = WalkabilityGrid.get(level);
        boolean wasOn = UnitServerEvents.rtsPathfinding;
        try {
            // a stone cell on a stone floor, built with the flag on and cached; the drain is run by hand (it counts calls
            // as ticks) so the cache surely holds the wall, whether the chunk was cached before or not
            UnitServerEvents.rtsPathfinding = true;
            helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
            helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
            helper.setBlock(new BlockPos(4, 3, 4), Blocks.AIR);
            cachedKind(grid, level, feet, bandMin, bandMax);
            for (int i = 0; i <= PathfinderConfig.WALKABILITY_MAX_DEFER_TICKS + 1; i++)
                WalkabilityGrid.drainDirtyChunks(PathfinderConfig.MAX_CHUNK_RECLASSIFY_PER_TICK);
            byte before = cachedKind(grid, level, feet, bandMin, bandMax);
            if (before != WalkabilityBuilder.KIND_BLOCKED)
                helper.fail("set-up: the cache should read the wall at " + feet.toShortString() + " as blocked, it reads kind " + before);

            // the wall comes out while the flag is off, then the flag comes back on
            UnitServerEvents.rtsPathfinding = false;
            helper.setBlock(new BlockPos(4, 2, 4), Blocks.AIR);
            UnitServerEvents.rtsPathfinding = true;
            byte after = cachedKind(grid, level, feet, bandMin, bandMax);
            byte live = WalkabilityBuilder.classify(level, feet.getX(), feet.getY(), feet.getZ());

            if (after == WalkabilityBuilder.KIND_BLOCKED || after != live)
                helper.fail("the cell at " + feet.toShortString() + " was opened while rtsPathfinding was off, but the cache reads kind "
                    + after + " (live " + live + "; cached as " + before + " while it was a wall) after it came back on");
            helper.succeed();
        } finally {
            UnitServerEvents.rtsPathfinding = wasOn;
        }
    }

    private static byte cachedKind(WalkabilityGrid grid, ServerLevel level, BlockPos p, int bandMin, int bandMax) {
        WalkabilityGridChunk chunk = grid.getOrBuild(level, p.getX() >> 4, p.getZ() >> 4, bandMin, bandMax);
        return chunk.kindAt(p.getX(), p.getY(), p.getZ());
    }
}
