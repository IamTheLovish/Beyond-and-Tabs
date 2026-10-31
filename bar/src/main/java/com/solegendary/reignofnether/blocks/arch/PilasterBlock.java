package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A pilaster on a wall face: a stack reads base / shaft / capital bottom to top, one on its own is a single (base and
 * capital in one block). The part follows the pilasters of the same facing above and below it, like a wall post: picked
 * when placed and kept up to date as they come and go (structures recompute it the same way).
 */
public class PilasterBlock extends MouldingBlock {
    public enum Part implements StringRepresentable {
        BASE("base"), SHAFT("shaft"), CAPITAL("capital"), SINGLE("single");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public PilasterBlock(String kind, Properties props) {
        super(kind, props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PART);
    }

    private static boolean pilaster(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof PilasterBlock && s.getValue(FACING) == facing;
    }

    static Part partAt(BlockGetter level, BlockPos pos, Direction facing) {
        boolean below = pilaster(level, pos.below(), facing), above = pilaster(level, pos.above(), facing);
        return below ? (above ? Part.SHAFT : Part.CAPITAL) : (above ? Part.BASE : Part.SINGLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = placedFacing(ctx);
        return defaultBlockState().setValue(FACING, f).setValue(PART, partAt(ctx.getLevel(), ctx.getClickedPos(), f));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        if (dir.getAxis() != Direction.Axis.Y)
            return state;
        return state.setValue(PART, partAt(level, pos, state.getValue(FACING)));
    }
}
