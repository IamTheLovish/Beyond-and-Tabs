package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A flat lintel over an opening, with or without a tapered keystone at its middle. */
public class LintelBlock extends MouldingBlock {
    public static final BooleanProperty KEYSTONE = BooleanProperty.create("keystone");

    public LintelBlock(String kind, Properties props) {
        super(kind, props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KEYSTONE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, KEYSTONE);
    }
}
