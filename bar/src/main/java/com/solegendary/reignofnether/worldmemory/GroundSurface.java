package com.solegendary.reignofnether.worldmemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
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
     * The Y of the highest block of a column that blocks motion (or holds a fluid) and is not leaves: the top block
     * vanilla's MOTION_BLOCKING_NO_LEAVES heightmap points at, found WITHOUT that map. The server sends a client only
     * WORLD_SURFACE and MOTION_BLOCKING; the client's own NO_LEAVES map starts all zero and is only touched by later
     * block changes, so a column whose blocks never changed since the chunk arrived reads as bedrock (eyes run
     * 39664003268: decals on the steps and slabs, which were placed, and on none of the untouched flat grass). The
     * MOTION_BLOCKING map is sent and kept, and differs from NO_LEAVES only where leaves are on top: walk down through
     * those (and any air under a canopy). Server and client both use this, so what is stamped is what is drawn.
     * Returns the chunk's minimum build height - 1 for an empty column.
     */
    public static int topBlockY(LevelChunk chunk, int localX, int localZ) {
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ);   // the top block's Y
        int min = chunk.getMinBuildHeight();
        if (y < min)
            return min - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(chunk.getPos().getMinBlockX() + localX, y, chunk.getPos().getMinBlockZ() + localZ);
        while (y >= min) {
            BlockState s = chunk.getBlockState(p.setY(y));
            if (!(s.getBlock() instanceof LeavesBlock) && (s.blocksMotion() || !s.getFluidState().isEmpty()))
                return y;
            y--;
        }
        return min - 1;
    }

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
            // the DRAWN top, not the collision top (review pass b, steps and slabs): three layers of snow collide at
            // 4/16 but are drawn at 6/16, and soul sand and mud collide at 14/16 but are drawn full - a decal at the
            // collision height sits inside the block and is hidden by it. The outline is the drawn shape for every
            // ground block we meet (slab 0.5, path 15/16, snow k/8, stairs and full blocks 1); a block with no
            // outline falls back to its collision
            shape = state.getShape(level, topBlock);
            if (shape.isEmpty())
                shape = state.getCollisionShape(level, topBlock);
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
