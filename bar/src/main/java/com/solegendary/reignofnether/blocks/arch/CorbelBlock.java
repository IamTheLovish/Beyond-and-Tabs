package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** An eave bracket under a cornice: four steps out, then up into the cornice's cell to the corona's soffit. */
public class CorbelBlock extends MouldingBlock {
    public CorbelBlock(String kind, Properties props) {
        super(kind, props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }
}
