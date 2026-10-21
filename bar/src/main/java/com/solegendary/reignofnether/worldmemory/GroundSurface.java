package com.solegendary.reignofnether.worldmemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a ground decal sits in a block column: on the real top face (claude/world-that-remembers.md 3.3) - a full
 * block at +1, a slab at +0.5, a snow layer at k/8, a dirt path at 15/16 - and nowhere on water, a fence post or a
 * wall top (a disc floating over a post was today's fault). One rule, shared by the server's stamp filter (no scorch
 * on a lake) and the client's mesh, and checked by the headless GameTests.
 */
public final class GroundSurface {
    private GroundSurface() { }

    /** A column without a surface a decal can sit on. */
    public static final double NONE = Double.NaN;

    /** A surface must cover at least this much of the cell in x and z (a post or a wall is narrower). */
    static final double MIN_COVER = 0.75;

    /**
     * The world height of the decal surface of the column whose highest motion-blocking block is {@code topBlock}
     * (the block at the heightmap's {@code getHeight - 1}), or {@link #NONE}: water or lava, a block without a
     * collision shape, a narrow top (post, wall, fence), or air.
     */
    public static double surfaceY(BlockGetter level, BlockPos.MutableBlockPos topBlock) {
        BlockState state = level.getBlockState(topBlock);
        if (state.isAir())
            return NONE;
        if (!state.getFluidState().isEmpty())
            return NONE;   // water (also waterlogged tops: a decal would float in the water)
        VoxelShape shape;
        try {
            shape = state.getCollisionShape(level, topBlock);
            // a one-layer snow blocks motion for the heightmap but has no collision: the decal sits on its drawn top
            // (2/16), not under it on the ground where the snow's face would hide it
            if (shape.isEmpty())
                shape = state.getShape(level, topBlock);
        } catch (Exception e) {
            return NONE;
        }
        if (shape.isEmpty())
            return NONE;
        AABB b = shape.bounds();
        if (b.maxX - b.minX < MIN_COVER || b.maxZ - b.minZ < MIN_COVER)
            return NONE;
        double top = shape.max(Direction.Axis.Y);
        if (top <= 0 || top > 1.0001)
            return NONE;   // a fence's 1.5 top, or a shape that reaches nothing
        return topBlock.getY() + Math.min(1.0, top);
    }
}
