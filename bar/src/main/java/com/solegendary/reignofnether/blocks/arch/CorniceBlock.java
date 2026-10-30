package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A cornice or string course run: straight, or a mitred corner. The models are one-handed (tools/gen_arch_blocks.py):
 * an OUTER corner facing F joins a run facing F on F's counter-clockwise side and a run facing F's clockwise turn
 * behind it; an INNER corner facing F joins a run facing F on its clockwise side and a run facing F's clockwise turn
 * in front of it. The mirrored corners take the other facing, so any corner is one block. Picked when placed (from
 * the neighbouring runs); structures set it explicitly.
 */
public class CorniceBlock extends MouldingBlock {
    public enum Shape implements StringRepresentable {
        STRAIGHT("straight"), OUTER("outer"), INNER("inner");

        private final String name;

        Shape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public CorniceBlock(String kind, Properties props) {
        super(kind, props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.STRAIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, SHAPE);
    }

    private boolean runFacing(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof CorniceBlock && s.getValue(FACING) == facing;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockGetter level = ctx.getLevel();
        BlockPos p = ctx.getClickedPos();
        Direction f = placedFacing(ctx), cw = f.getClockWise(), ccw = f.getCounterClockWise();
        BlockState s = defaultBlockState().setValue(FACING, f);
        if (runFacing(level, p.relative(ccw), f) && runFacing(level, p.relative(f.getOpposite()), cw))
            return s.setValue(SHAPE, Shape.OUTER);
        if (runFacing(level, p.relative(cw), f) && runFacing(level, p.relative(f.getOpposite()), ccw))
            return s.setValue(FACING, ccw).setValue(SHAPE, Shape.OUTER);
        if (runFacing(level, p.relative(cw), f) && runFacing(level, p.relative(f), cw))
            return s.setValue(SHAPE, Shape.INNER);
        if (runFacing(level, p.relative(ccw), f) && runFacing(level, p.relative(f), ccw))
            return s.setValue(FACING, ccw).setValue(SHAPE, Shape.INNER);
        return s;
    }
}
