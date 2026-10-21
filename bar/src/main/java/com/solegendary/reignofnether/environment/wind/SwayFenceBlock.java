package com.solegendary.reignofnether.environment.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

/**
 * A twig: a vanilla fence (the thin wood of the bare and dead trees' outer limbs) plus the {@link SwayBlocks#SWAY}
 * tier. Joins its neighbours exactly as the vanilla fence does (a sway fence connects to a sway or vanilla fence and to
 * solid wood beside it), same shapes and collision.
 */
public class SwayFenceBlock extends FenceBlock implements EntityBlock {

    public final Block vanilla;

    public SwayFenceBlock(Properties properties, Block vanilla) {
        super(properties);
        this.vanilla = vanilla;
        registerDefaultState(defaultBlockState().setValue(SwayBlocks.SWAY, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SwayBlocks.SWAY);
    }

    // (joining is vanilla's tag rule: the sway fences are in #minecraft:fences and #minecraft:wooden_fences, data tags,
    // so a sway twig and a vanilla fence join each other exactly as two vanilla fences do)

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwayBlockEntity(pos, state);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return vanilla.defaultBlockState().getDrops(params);
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return 5;   // vanilla fence
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return 20;
    }
}
