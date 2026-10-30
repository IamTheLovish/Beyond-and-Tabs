package com.solegendary.reignofnether.unit.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntFunction;

/**
 * Walking a path in straight lines (troop animation plan, gain 2; lovish Oct 30: "Smooth the real path"). A path is a
 * chain of grid nodes, and on any line off the axes and diagonals it is a staircase: vanilla aims the unit at each
 * node's centre in turn, so it zig-zags and its body rocks from side to side the whole way (WalkLineGameTests). Here
 * the unit aims instead at the farthest of the next {@link #LOOKAHEAD} nodes it can walk to in a straight line: every
 * grid cell its body would cross on the way - its full width, not a point - must be plain walkable floor (no ledge,
 * hole, water, lava or fire edge, door, fence, leaves) at the same height, with room for its height above. Anything
 * else (a step, a slope, a drop, a narrow gap) keeps the node-by-node walk. The check runs when the path's next node
 * changes and every {@link #REAIM_TICKS} ticks (PathNavigationMixin), not every tick.
 */
public final class LineOfWalk {
    private LineOfWalk() {}

    /**
     * How many nodes ahead a straight line may reach: far, so the node it aims at - on the staircase, up to half a block
     * off the true line - turns it by under 2 degrees (6 nodes would be 5, and every re-aim a fresh swing).
     */
    public static final int LOOKAHEAD = 16;
    /** Ticks between re-aims while the node it aims at is still ahead (the line is judged again from where it is). */
    public static final int REAIM_TICKS = 10;
    /** The spacing of the samples along the line, in blocks (a cell is never skipped at this width). */
    static final double STEP = 0.25;
    /** The body's half-width is widened by this much, so a line never shaves a wall's corner. */
    static final double MARGIN = 0.1;

    /** Whether a unit can stand in a cell: walkable floor at that height and room above for its body. */
    public interface Cells {
        boolean standable(int x, int y, int z);
    }

    /** The cells of a level for a body `height` blocks tall: vanilla's own path type for the feet, collision above. */
    public static Cells of(BlockGetter level, int height) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            p.set(x, y, z);
            if (WalkNodeEvaluator.getBlockPathTypeStatic(level, p) != BlockPathTypes.WALKABLE)
                return false;
            for (int k = 1; k < height; k++) {
                p.set(x, y + k, z);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty())
                    return false;
            }
            return true;
        };
    }

    // the cells judged in one call (1 standable, 2 not): the lines from one spot overlap, each cell is read once.
    // Server thread only, as the navigation is
    private static final Long2ByteOpenHashMap SEEN = new Long2ByteOpenHashMap();

    /** {@link #farthest(double, double, int, double, Path, Cells)} for a mob on its own level. */
    public static int farthest(Mob mob, Path path) {
        return farthest(mob.getX(), mob.getZ(), mob.blockPosition().getY(), mob.getBbWidth() / 2.0, path,
            of(mob.level(), Math.max(1, (int) Math.ceil(mob.getBbHeight()))));
    }

    /**
     * The index of the farthest node from the path's next one, at most {@link #LOOKAHEAD} on, that a body of
     * `halfWidth` standing at (x, z) on floor height `y` can walk to in a straight line - or the next node's own index
     * when there is none (or the nodes ahead already run in one straight direction, as vanilla walks them anyway).
     */
    public static int farthest(double x, double z, int y, double halfWidth, Path path, Cells cells) {
        return farthest(x, z, y, halfWidth, path, cells, j -> new Vec3(path.getNode(j).x + 0.5, y, path.getNode(j).z + 0.5));
    }

    /**
     * As {@link #farthest(double, double, int, double, Path, Cells)}, with `aim` giving where the unit really walks to
     * at each node (its {@link Path#getEntityPosAtNode}: a wide body's box, not the node's cell). Not used yet: lines are
     * still judged to the cell's centre.
     */
    public static int farthest(double x, double z, int y, double halfWidth, Path path, Cells cells, IntFunction<Vec3> aim) {
        int next = path.getNextNodeIndex(), last = Math.min(path.getNodeCount() - 1, next + LOOKAHEAD);
        if (last <= next || path.getNode(next).y != y)
            return next;
        // the reach: nodes at the unit's own height, one after another
        int reach = next;
        while (reach < last && path.getNode(reach + 1).y == y)
            reach++;
        if (reach == next || straightAhead(path, next, reach))
            return next;
        SEEN.clear();
        double hw = halfWidth + MARGIN;
        for (int j = reach; j > next; j--) {
            Node n = path.getNode(j);
            if (clear(x, z, n.x + 0.5, n.z + 0.5, y, hw, cells))
                return j;
        }
        return next;
    }

    // every step from `from` to `to` the same: the straight line there is the node chain itself
    private static boolean straightAhead(Path path, int from, int to) {
        Node a = path.getNode(from), b = path.getNode(from + 1);
        int dx = b.x - a.x, dz = b.z - a.z;
        for (int i = from + 1; i < to; i++) {
            Node c = path.getNode(i), d = path.getNode(i + 1);
            if (d.x - c.x != dx || d.z - c.z != dz)
                return false;
        }
        return true;
    }

    /** Whether a body of half-width `hw` can walk straight from (x0, z0) to (x1, z1) on floor `y` (a fresh check). */
    static boolean clearLine(double x0, double z0, double x1, double z1, int y, double hw, Cells cells) {
        SEEN.clear();
        return clear(x0, z0, x1, z1, y, hw, cells);
    }

    // the body's footprint, sampled every STEP along the line from (x0, z0) to (x1, z1): every cell it overlaps
    private static boolean clear(double x0, double z0, double x1, double z1, int y, double hw, Cells cells) {
        double dx = x1 - x0, dz = z1 - z0;
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / STEP));
        for (int s = 0; s <= samples; s++) {
            double t = s / (double) samples, cx = x0 + dx * t, cz = z0 + dz * t;
            int ax = (int) Math.floor(cx - hw), bx = (int) Math.floor(cx + hw);
            int az = (int) Math.floor(cz - hw), bz = (int) Math.floor(cz + hw);
            for (int gx = ax; gx <= bx; gx++)
                for (int gz = az; gz <= bz; gz++) {
                    long key = BlockPos.asLong(gx, y, gz);
                    byte seen = SEEN.get(key);
                    if (seen == 0) {
                        seen = cells.standable(gx, y, gz) ? (byte) 1 : (byte) 2;
                        SEEN.put(key, seen);
                    }
                    if (seen == 2)
                        return false;
                }
        }
        return true;
    }
}
