package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A building is built when its placed count reaches its total. The bot soak on #55 (Oct 21) left a Heartwood Hall at
 * 1272/1273 with an empty queue and no unbuilt block: a block standing in the world that was never counted (it got
 * there after the placement counted its blocks) is never queued either, so the count could never close.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildingCountGameTests {

    /** Every block already standing when a builder asks for work: the placement counts them all and is built. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void blocks_standing_before_the_count_still_finish_the_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos origin = new BlockPos(base.getX() + 240, 228, base.getZ() + 160);
        BuildingPlacement placement = BuildingUtils.getNewBuildingPlacement(Buildings.HEARTWOOD_HALL, level, origin,
            Rotation.NONE, "gametest_count", false);
        List<BuildingBlock> blocks = new ArrayList<>();
        int minY = Integer.MAX_VALUE;
        for (BuildingBlock b : placement.getBlocks())
            if (!b.getBlockState().isAir()) {
                blocks.add(b);
                minY = Math.min(minY, b.getBlockPos().getY());
            }
        for (BuildingBlock b : blocks)
            level.setBlock(new BlockPos(b.getBlockPos().getX(), minY - 1, b.getBlockPos().getZ()),
                Blocks.STONE.defaultBlockState(), 2);
        blocks.sort(java.util.Comparator.comparingInt(b -> b.getBlockPos().getY()));
        for (BuildingBlock b : blocks)
            level.setBlock(b.getBlockPos(), b.getBlockState(), 2);   // no neighbour updates: nothing drifts
        int before = placement.getBlocksPlaced();
        placement.queueNextBlock(level, "gametest_count");
        int after = placement.getBlocksPlaced(), total = placement.getBlocksTotal();
        int standing = 0;
        for (BuildingBlock b : blocks)
            if (b.isPlaced(level))
                standing++;
        if (after < total)
            helper.fail("counted " + after + " of " + total + " (" + before + " before asking for work, "
                + standing + " of " + blocks.size() + " list entries standing)");
        else
            helper.succeed();
    }
}
