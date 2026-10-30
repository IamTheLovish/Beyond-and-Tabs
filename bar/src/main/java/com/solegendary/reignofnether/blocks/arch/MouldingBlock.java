package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * A moulding that hangs on a wall face: FACING is the way it looks (away from the wall). Its shape is the model's
 * own boxes (ArchShapes, generated with the models by tools/gen_arch_blocks.py) turned to the facing; it never
 * occludes (Properties.noOcclusion), so the wall face behind it is drawn and nothing is seen through the wall.
 */
public abstract class MouldingBlock extends HorizontalDirectionalBlock {
    final String kind;

    protected MouldingBlock(String kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    /** "kind/<values of the other properties in name order>", ArchShapes' key (tools/gen_arch_blocks.shape_key). */
    String shapeKey(BlockState s) {
        List<Property<?>> props = new ArrayList<>(s.getProperties());
        props.sort((a, b) -> a.getName().compareTo(b.getName()));
        List<String> values = new ArrayList<>();
        for (Property<?> p : props)
            if (p != FACING)
                values.add(valueName(s, p));
        return kind + "/" + String.join("/", values);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String valueName(BlockState s, Property p) {
        return p.getName(s.getValue(p));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return ArchShapes.get(shapeKey(state), state.getValue(FACING));
    }

    /** Clicked on a wall's side: it faces out of that side; on a floor or ceiling: it faces the player. */
    static Direction placedFacing(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, placedFacing(ctx));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
