package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.blocks.BlockUtils;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Log felling (ResourcesServerEvents.fellAdjacentLogs, gamerule doLogFalling): cutting a log turns every log it holds
 * up - its trunk, a touching trunk, a branch leaning off the top - into the falling block of its own wood, keeping
 * the log's axis; nothing below the cut and nothing out of reach is touched.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class LogFellingGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void cutting_a_log_fells_every_log_it_holds_up_and_nothing_else(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRuleRegistrar.LOG_FALLING).set(true, level.getServer());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8)).atY(170);
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlock(base.offset(dx, -1, dz), Blocks.DIRT.defaultBlockState(), 2);
                for (int dy = 0; dy <= 9; dy++)
                    level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        BlockState oak = Blocks.OAK_LOG.defaultBlockState();
        BlockState birchX = Blocks.BIRCH_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        // the cut tree: a 4-high oak trunk, a sideways birch branch off its top (reached through the diagonal above)
        for (int i = 0; i < 4; i++)
            level.setBlock(base.above(i), oak, 2);
        BlockPos branch = base.offset(1, 4, 0);
        level.setBlock(branch, birchX, 2);
        // a spruce trunk touching the oak's side: felled with it (one tree to the walk)
        BlockPos spruce = base.offset(0, 1, 1);
        level.setBlock(spruce, Blocks.SPRUCE_LOG.defaultBlockState(), 2);
        level.setBlock(spruce.above(), Blocks.SPRUCE_LOG.defaultBlockState(), 2);
        // a log BELOW the cut (the walk never goes down) and a log two blocks away: both stay
        BlockPos below = base.below();
        level.setBlock(below, oak, 2);
        BlockPos far = base.offset(3, 0, 0);
        level.setBlock(far, oak, 2);

        ResourcesServerEvents.fellAdjacentLogs(base, level);

        List<BlockPos> felled = List.of(base.above(1), base.above(2), base.above(3), branch, spruce, spruce.above());
        for (BlockPos p : felled) {
            BlockState bs = level.getBlockState(p);
            if (!BlockUtils.isFallingLogBlock(bs)) {
                helper.fail(bs.getBlock() + " at " + p.toShortString() + " is still a standing log after the cut at " + base.toShortString());
                return;
            }
        }
        if (!level.getBlockState(branch).is(BlockRegistrar.FALLING_BIRCH_LOG.get())
                || level.getBlockState(branch).getValue(RotatedPillarBlock.AXIS) != Direction.Axis.X) {
            helper.fail("the sideways birch branch should fall as a falling birch log on the X axis, is " + level.getBlockState(branch));
            return;
        }
        if (!level.getBlockState(spruce).is(BlockRegistrar.FALLING_SPRUCE_LOG.get())) {
            helper.fail("the spruce trunk should fall as falling spruce, is " + level.getBlockState(spruce));
            return;
        }
        if (!level.getBlockState(below).is(Blocks.OAK_LOG)) {
            helper.fail("the log below the cut was felled: " + level.getBlockState(below));
            return;
        }
        if (!level.getBlockState(far).is(Blocks.OAK_LOG)) {
            helper.fail("a log two blocks from the tree was felled: " + level.getBlockState(far));
            return;
        }
        // the round trip BuildingBlockData takes for a saved falling log keeps the wood and the axis
        BlockState back = BlockUtils.getNonFallingLog(level.getBlockState(branch));
        if (!back.is(Blocks.BIRCH_LOG) || back.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.X) {
            helper.fail("getNonFallingLog of the fallen branch should be a birch log on X, is " + back);
            return;
        }
        helper.succeed();
    }
}
