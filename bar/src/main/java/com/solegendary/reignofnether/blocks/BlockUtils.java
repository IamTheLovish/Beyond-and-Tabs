package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.Map;
import java.util.Random;

public class BlockUtils {

    private static final Random RANDOM = new Random();

    /**
     * A tree's wood: every block tagged {@code minecraft:logs} (logs, wood, stripped, stems, hyphae) plus the huge
     * mushrooms' stem and caps. Tag check only - this runs per client tick on the RTS camera's ground probe
     * (OrthoviewClientEvents), per block of a flyer's descent (FlyingMoveToTargetGoal) and per neighbour of a felled
     * log; it used to build a 23-block list and collect the state's tag stream into a set on every call.
     */
    public static boolean isLogBlock(BlockState bs) {
        return bs.is(BlockTags.LOGS) || bs.is(Blocks.MUSHROOM_STEM)
            || bs.is(Blocks.RED_MUSHROOM_BLOCK) || bs.is(Blocks.BROWN_MUSHROOM_BLOCK);
    }

    /** Our falling logs and stems (BlockRegistrar.fallingLog / fallingNetherStem) are the only FallingRotatedPillarBlocks. */
    public static boolean isFallingLogBlock(BlockState bs) {
        return bs.getBlock() instanceof FallingRotatedPillarBlock;
    }

    /**
     * The falling log that stands in for a vanilla log when a tree is cut (ResourcesServerEvents.fellAdjacentLogs),
     * or null for wood that has none (sway limbs, mushroom blocks). One table, read both ways by {@link #getFallingLog}
     * and {@link #getNonFallingLog}; built on first use because the registry objects resolve after registration.
     */
    private static final class FallingLogs {
        static final Map<Block, Block> FALLING_OF = Map.ofEntries(
            Map.entry(Blocks.OAK_LOG, BlockRegistrar.FALLING_OAK_LOG.get()),
            Map.entry(Blocks.OAK_WOOD, BlockRegistrar.FALLING_OAK_LOG.get()),
            Map.entry(Blocks.SPRUCE_LOG, BlockRegistrar.FALLING_SPRUCE_LOG.get()),
            Map.entry(Blocks.SPRUCE_WOOD, BlockRegistrar.FALLING_SPRUCE_LOG.get()),
            Map.entry(Blocks.BIRCH_LOG, BlockRegistrar.FALLING_BIRCH_LOG.get()),
            Map.entry(Blocks.BIRCH_WOOD, BlockRegistrar.FALLING_BIRCH_LOG.get()),
            Map.entry(Blocks.JUNGLE_LOG, BlockRegistrar.FALLING_JUNGLE_LOG.get()),
            Map.entry(Blocks.JUNGLE_WOOD, BlockRegistrar.FALLING_JUNGLE_LOG.get()),
            Map.entry(Blocks.ACACIA_LOG, BlockRegistrar.FALLING_ACACIA_LOG.get()),
            Map.entry(Blocks.ACACIA_WOOD, BlockRegistrar.FALLING_ACACIA_LOG.get()),
            Map.entry(Blocks.DARK_OAK_LOG, BlockRegistrar.FALLING_DARK_OAK_LOG.get()),
            Map.entry(Blocks.DARK_OAK_WOOD, BlockRegistrar.FALLING_DARK_OAK_LOG.get()),
            Map.entry(Blocks.MANGROVE_LOG, BlockRegistrar.FALLING_MANGROVE_LOG.get()),
            Map.entry(Blocks.MANGROVE_WOOD, BlockRegistrar.FALLING_MANGROVE_LOG.get()),
            Map.entry(Blocks.CHERRY_LOG, BlockRegistrar.FALLING_CHERRY_LOG.get()),
            Map.entry(Blocks.CHERRY_WOOD, BlockRegistrar.FALLING_CHERRY_LOG.get()),
            Map.entry(Blocks.WARPED_STEM, BlockRegistrar.FALLING_WARPED_STEM.get()),
            Map.entry(Blocks.WARPED_HYPHAE, BlockRegistrar.FALLING_WARPED_STEM.get()),
            Map.entry(Blocks.CRIMSON_STEM, BlockRegistrar.FALLING_CRIMSON_STEM.get()),
            Map.entry(Blocks.CRIMSON_HYPHAE, BlockRegistrar.FALLING_CRIMSON_STEM.get()));
        // the plain log each falling block stands for (the _LOG, not the _WOOD, as before)
        static final Map<Block, Block> LOG_OF = Map.of(
            BlockRegistrar.FALLING_OAK_LOG.get(), Blocks.OAK_LOG,
            BlockRegistrar.FALLING_SPRUCE_LOG.get(), Blocks.SPRUCE_LOG,
            BlockRegistrar.FALLING_BIRCH_LOG.get(), Blocks.BIRCH_LOG,
            BlockRegistrar.FALLING_JUNGLE_LOG.get(), Blocks.JUNGLE_LOG,
            BlockRegistrar.FALLING_ACACIA_LOG.get(), Blocks.ACACIA_LOG,
            BlockRegistrar.FALLING_DARK_OAK_LOG.get(), Blocks.DARK_OAK_LOG,
            BlockRegistrar.FALLING_MANGROVE_LOG.get(), Blocks.MANGROVE_LOG,
            BlockRegistrar.FALLING_CHERRY_LOG.get(), Blocks.CHERRY_LOG,
            BlockRegistrar.FALLING_WARPED_STEM.get(), Blocks.WARPED_STEM,
            BlockRegistrar.FALLING_CRIMSON_STEM.get(), Blocks.CRIMSON_STEM);
    }

    /** The falling block for a log state, keeping its axis; null when that wood does not fall. */
    public static BlockState getFallingLog(BlockState bs) {
        Block falling = FallingLogs.FALLING_OF.get(bs.getBlock());
        return falling == null ? null : withAxisOf(falling.defaultBlockState(), bs);
    }

    /** The plain log a falling log stands for, keeping its axis; any other state comes back unchanged. */
    public static BlockState getNonFallingLog(BlockState bs) {
        Block log = FallingLogs.LOG_OF.get(bs.getBlock());
        return log == null ? bs : withAxisOf(log.defaultBlockState(), bs);
    }

    private static BlockState withAxisOf(BlockState state, BlockState from) {
        if (state.hasProperty(BlockStateProperties.AXIS) && from.hasProperty(BlockStateProperties.AXIS))
            return state.setValue(BlockStateProperties.AXIS, from.getValue(BlockStateProperties.AXIS));
        return state;
    }

    /** Leaves: the {@code minecraft:leaves} tag, our decayable wart blocks and the huge mushrooms' caps. */
    public static boolean isLeafBlock(BlockState bs) {
        return bs.is(BlockTags.LEAVES)
            || bs.is(BlockRegistrar.DECAYABLE_NETHER_WART_BLOCK.get()) || bs.is(BlockRegistrar.DECAYABLE_WARPED_WART_BLOCK.get())
            || bs.is(Blocks.RED_MUSHROOM_BLOCK) || bs.is(Blocks.BROWN_MUSHROOM_BLOCK);
    }

    public static boolean isBottomSlab(BlockState bs) {
        if (bs.getBlock() instanceof SlabBlock) {
            SlabType type = bs.getValue(SlabBlock.TYPE);
            return type == SlabType.BOTTOM;
        }
        return false;
    }


    private static boolean isWraithSnow(BlockState bs) {
        return bs.getBlock() == BlockRegistrar.WRAITH_SNOW_LAYER.get();
    }
    private static boolean isVanillaSnow(BlockState bs) {
        return bs.getBlock() == Blocks.SNOW;
    }
    public static int getWraithSnowLayers(BlockState bs) {
        return isWraithSnow(bs) ? bs.getValue(BlockStateProperties.LAYERS) : 0;
    }
    public static int getSnowLayers(BlockState bs) {
        return isWraithSnow(bs) || isVanillaSnow(bs) ? bs.getValue(BlockStateProperties.LAYERS) : 0;
    }
    public static boolean canPlaceSnow(Level level, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        BlockState bsBelow = level.getBlockState(pos.below());
        return !MiscUtil.isSolidBlocking(level, pos) &&
                MiscUtil.isSolidBlocking(level, pos.below()) &&
                !isBottomSlab(bsBelow) &&
                !(bsBelow.getBlock() instanceof FenceBlock) &&
                !(bsBelow.getBlock() instanceof WallBlock) &&
                !(bsBelow.getBlock() instanceof TrapDoorBlock) &&
                !(bsBelow.getBlock() instanceof SculkSensorBlock) &&
                !(bs.getBlock() instanceof SkullBlock) &&
                (!BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), pos.below()) || !(bs.getBlock() instanceof BushBlock)) &&
                (!BuildingUtils.isPosInsideFarm(level.isClientSide(), pos.below()) || bsBelow.getBlock().getDescriptionId().contains("_log"));
    }
}
