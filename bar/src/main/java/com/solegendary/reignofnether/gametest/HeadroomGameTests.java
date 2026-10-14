package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The pathfinder's head room over a raised floor (WalkabilityBuilder.raisedIntoCrown): the Sunforged keep's gate - a
 * carpet under upside-down stairs - was 1.94 blocks high for 1.95-tall units, the grid called it open, and every
 * unit trained in the yard was shut in (lovish's clip, Oct 14 16:52). Three gate columns side by side on a stone pad
 * at a height no other test uses (278): carpet + upside-down stairs over the head (shut), carpet + top slab (open:
 * the slab starts half way up), bare floor + upside-down stairs (open: exactly two empty cells).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeadroomGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void carpet_under_a_low_lintel_is_not_walkable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        int y = 278;
        BlockState stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP);
        BlockState topSlab = Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        Object[][] cases = {
            // { x offset, feet block, crown block, walkable? }
            { 2, Blocks.YELLOW_CARPET.defaultBlockState(), stairs, false },
            { 5, Blocks.YELLOW_CARPET.defaultBlockState(), topSlab, true },
            { 8, Blocks.AIR.defaultBlockState(), stairs, true },
        };
        List<String> problems = new ArrayList<>();
        for (Object[] c : cases) {
            int x = corner.getX() + (int) c[0], z = corner.getZ() + 2;
            level.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, y, z), (BlockState) c[1], 2);
            level.setBlock(new BlockPos(x, y + 1, z), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, y + 2, z), (BlockState) c[2], 2);
            byte kind = WalkabilityBuilder.classify(level, x, y, z);
            boolean walkable = kind != WalkabilityBuilder.KIND_BLOCKED;
            if (walkable != (boolean) c[3])
                problems.add(((BlockState) c[1]).getBlock().getName().getString() + " under "
                    + ((BlockState) c[2]).getBlock().getName().getString() + (c[2] == topSlab ? " (top)" : " (upside-down)")
                    + ": walkable " + walkable + ", expected " + c[3]);
        }
        for (Object[] c : cases)
            for (int dy = -1; dy <= 2; dy++)
                level.setBlock(new BlockPos(corner.getX() + (int) c[0], y + dy, corner.getZ() + 2), Blocks.AIR.defaultBlockState(), 2);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
