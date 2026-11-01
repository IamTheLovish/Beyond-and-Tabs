package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A dressed corner stone: a full stone cube that REPLACES the wall's corner block (lovish Oct 30 2026) - it collides
 * and occludes like it (registered with ArchBlocks.solidProps) - with stones 2 px proud on two faces: its FACING
 * (front) and the counter-clockwise turn of it (side). long_side says which face carries the long stone (it runs 8 px
 * over the next block of wall); the other carries the short one. The model is one-handed: a corner of the other hand
 * takes the other facing. Placed by hand it alternates by course (the block's y), so a column of them zips up the
 * corner; structures set it explicitly.
 */
public class QuoinBlock extends MouldingBlock {
    public enum LongSide implements StringRepresentable {
        FRONT("front"), SIDE("side");

        private final String name;

        LongSide(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<LongSide> LONG_SIDE = EnumProperty.create("long_side", LongSide.class);

    public QuoinBlock(String kind, Properties props) {
        super(kind, props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LONG_SIDE, LongSide.FRONT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LONG_SIDE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        LongSide side = (ctx.getClickedPos().getY() & 1) == 0 ? LongSide.FRONT : LongSide.SIDE;
        return defaultBlockState().setValue(FACING, placedFacing(ctx)).setValue(LONG_SIDE, side);
    }
}
