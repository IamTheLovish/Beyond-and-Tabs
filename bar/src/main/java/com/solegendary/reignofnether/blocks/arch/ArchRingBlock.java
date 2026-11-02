package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * One cell of a segmental arch ring over a 3-wide opening, in the row above the opening's top: springers over the
 * jambs, haunches and the crown (with its keystone) over the opening; left / right as you face the wall.
 */
public class ArchRingBlock extends MouldingBlock {
    public enum Piece implements StringRepresentable {
        LEFT_SPRINGER("left_springer"), LEFT_HAUNCH("left_haunch"), CROWN("crown"), RIGHT_HAUNCH("right_haunch"),
        RIGHT_SPRINGER("right_springer");

        private final String name;

        Piece(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Piece> PIECE = EnumProperty.create("piece", Piece.class);

    public ArchRingBlock(String kind, Properties props) {
        super(kind, props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PIECE, Piece.CROWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PIECE);
    }
}
