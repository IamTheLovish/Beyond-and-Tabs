package com.solegendary.reignofnether.environment.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A tree limb: a vanilla wood block (bark on all six faces, axis) plus the {@link SwayBlocks#SWAY} tier, drawn by
 * {@link BranchWindRenderer} under the wind. Everything a wood block is - shape, collision, light, flammability,
 * what it drops - it still is; {@link #vanilla} is the block it stands in for.
 */
public class SwayWoodBlock extends RotatedPillarBlock implements EntityBlock {

    /** The vanilla wood this block stands in for (its model is drawn, its drops are dropped). */
    public final Block vanilla;

    public SwayWoodBlock(Properties properties, Block vanilla) {
        super(properties);
        this.vanilla = vanilla;
        registerDefaultState(defaultBlockState().setValue(SwayBlocks.SWAY, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SwayBlocks.SWAY);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwayBlockEntity(pos, state);
    }

    /** Drops what the vanilla wood drops (the block has no loot table of its own). */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return vanilla.defaultBlockState().setValue(AXIS, state.getValue(AXIS)).getDrops(params);
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction face) {
        return 5;   // vanilla wood
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction face) {
        return 5;
    }
}
