package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** A window sill under an opening: a sloped weathered top, a lip with a drip under it. */
public class SillBlock extends MouldingBlock {
    public SillBlock(String kind, Properties props) {
        super(kind, props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }
}
