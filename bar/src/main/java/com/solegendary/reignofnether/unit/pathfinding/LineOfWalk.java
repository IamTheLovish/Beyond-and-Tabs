package com.solegendary.reignofnether.unit.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntFunction;

/**
 * Walking a path in straight lines (troop animation plan, gain 2; lovish Oct 30: "Smooth the real path"). A path is a
 * chain of grid nodes, and on any line off the axes and diagonals it is a staircase: the unit aims at each node in turn,
 * so it zig-zags and its body rocks from side to side the whole way (WalkLineGameTests) - on vanilla's pathfinder and on
 * the RTS grid's alike. Here the unit aims instead at the farthest of the next {@link #LOOKAHEAD} nodes it can walk to
 * in a straight line: every grid cell its body would cross on the way to where it really walks at that node
 * ({@link Path#getEntityPosAtNode}: a wide body's box, not the node's cell) - its full width, not a point - must be
 * ground by the units' own rules (the RTS grid's {@link WalkabilityBuilder#classify}: no bush or lantern in the way, no
 * cauldron rim, leaf or fence floor) and vanilla's (no ledge, hole, water, lava or fire edge, door), at the same height,
 * with room for its height above. Anything else (a step, a slope, a drop, a narrow gap, a non-full floor such as a path
 * block or a slab under part of the line) keeps the node-by-node walk. The check runs when the path changes and every
 * {@link #REAIM_TICKS} ticks (PathNavigationMixin), not every tick, and the cells are read once a tick for every unit.
 */
public final class LineOfWalk {
    private LineOfWalk() {}

    /**
     * How many nodes ahead a straight line may reach: far, so the node it aims at - on the staircase, up to half a block
     * off the true line - turns it by under a degree (6 nodes would be 5, and every re-aim a fresh swing), and so a path
     * that runs straight along an axis first and takes all its diagonal steps later (A* breaks its ties that way: the
     * RTS grid's 2x2 path for a Siege Ox, Oct 30) is cut across while the turn is still ahead - at 16 the unit walked 5
     * blocks east before the diagonals came within reach, 16 degrees off its line.
     */
    public static final int LOOKAHEAD = 32;
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

    // [0] lines judged for a unit, [1] cells read from the level, [2] nanoseconds in farthest(Mob, ...) (the cost
    // WalkLineGameTests reports); server thread only
    private static final long[] COUNTS = new long[3];

    /** Lines judged, cells read and nanoseconds spent so far (a copy). */
    public static long[] counts() {
        return COUNTS.clone();
    }

    // the cells read this tick on one level, per body height (1 standable, 2 not): a crowd walking one way reads its
    // ground once, not once per unit. A block changed later in the same tick is seen the next tick
    private static Level cachedLevel = null;
    private static long cachedTick = Long.MIN_VALUE;
    private static final Long2ByteOpenHashMap[] READ = new Long2ByteOpenHashMap[4];

    /**
     * The cells of a level for a body `height` blocks tall, by the units' own rules and vanilla's both: the RTS grid's
     * {@link WalkabilityBuilder#classify} must call it plain land (it reads the feet, head and crown cells and the floor
     * - a bush or lantern in the way, a cauldron or composter rim, a leaf or fence floor are not; WalkNodeEvaluatorMixin
     * keeps the same rules for vanilla's pathfinder) and vanilla's path type must be WALKABLE (a fire, lava or cactus
     * beside it, a door, a trapdoor are not), with nothing in the way above the head cell for a taller body.
     */
    public static Cells of(Level level, int height) {
        long tick = level.getGameTime();
        if (level != cachedLevel || tick != cachedTick) {
            cachedLevel = level;
            cachedTick = tick;
            for (Long2ByteOpenHashMap m : READ)
                if (m != null)
                    m.clear();
        }
        int h = Mth.clamp(height, 1, READ.length) - 1;
        Long2ByteOpenHashMap read = READ[h] != null ? READ[h] : (READ[h] = new Long2ByteOpenHashMap());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            long key = BlockPos.asLong(x, y, z);
            byte got = read.get(key);
            if (got == 0) {
                COUNTS[1]++;
                got = standable(level, p, x, y, z, height) ? (byte) 1 : (byte) 2;
                read.put(key, got);
            }
            return got == 1;
        };
    }

    private static boolean standable(Level level, BlockPos.MutableBlockPos p, int x, int y, int z, int height) {
        if (WalkabilityBuilder.classify(level, x, y, z) != WalkabilityBuilder.KIND_LAND)
            return false;
        if (WalkNodeEvaluator.getBlockPathTypeStatic(level, p.set(x, y, z)) != BlockPathTypes.WALKABLE)
            return false;
        // classify read the feet and head cells (and the crown over a raised floor): room above them for a taller body
        for (int k = 2; k < height; k++) {
            p.set(x, y + k, z);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty())
                return false;
        }
        return true;
    }

    // the cells judged in one call (1 standable, 2 not): the lines from one spot overlap, each cell is asked once.
    // Server thread only, as the navigation is
    private static final Long2ByteOpenHashMap SEEN = new Long2ByteOpenHashMap();

    /** The floor height a mob stands at, as vanilla's own path start takes it (a path block or slab floor included). */
    public static int floorOf(Mob mob) {
        return Mth.floor(mob.getY() + 0.5);
    }

    /** The cells a mob's body needs, on its own level. */
    public static Cells cellsFor(Mob mob) {
        return of(mob.level(), Math.max(1, Mth.ceil(mob.getBbHeight())));
    }

    /** {@link #farthest(double, double, int, double, Path, Cells, IntFunction)} for a mob on its own level. */
    public static int farthest(Mob mob, Path path) {
        long t0 = System.nanoTime();
        COUNTS[0]++;
        int to = farthest(mob.getX(), mob.getZ(), floorOf(mob), mob.getBbWidth() / 2.0, path, cellsFor(mob),
            j -> path.getEntityPosAtNode(mob, j));
        COUNTS[2] += System.nanoTime() - t0;
        return to;
    }

    /**
     * Whether a mob can still walk straight from where it is to node `j` of its path (it may have been pushed off the
     * line it was aiming down: a crowd, a knockback).
     */
    public static boolean canWalkTo(Mob mob, Path path, int j) {
        long t0 = System.nanoTime();
        Vec3 a = path.getEntityPosAtNode(mob, j);
        SEEN.clear();
        boolean ok = path.getNode(j).y == floorOf(mob)
            && clear(mob.getX(), mob.getZ(), a.x, a.z, floorOf(mob), mob.getBbWidth() / 2.0 + MARGIN, cellsFor(mob));
        COUNTS[2] += System.nanoTime() - t0;
        return ok;
    }

    /** The node from `from` to `to` (inclusive) nearest a mob: where it walks on from when its line is lost. */
    public static int nearest(Mob mob, Path path, int from, int to) {
        int best = to;
        double bestD = Double.MAX_VALUE;
        for (int i = Math.max(0, from); i <= to && i < path.getNodeCount(); i++) {
            Vec3 a = path.getEntityPosAtNode(mob, i);
            double d = mob.distanceToSqr(a.x, mob.getY(), a.z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * The index of the farthest node from the path's next one, at most {@link #LOOKAHEAD} on, that a body of
     * `halfWidth` standing at (x, z) on floor height `y` can walk to in a straight line, ending at each node's cell
     * centre - or the next node's own index when there is none (or the nodes ahead already run in one straight
     * direction, as the unit walks them anyway).
     */
    public static int farthest(double x, double z, int y, double halfWidth, Path path, Cells cells) {
        return farthest(x, z, y, halfWidth, path, cells, j -> new Vec3(path.getNode(j).x + 0.5, y, path.getNode(j).z + 0.5));
    }

    /**
     * As {@link #farthest(double, double, int, double, Path, Cells)}, each line ending where `aim` says the unit really
     * walks to at that node (its {@link Path#getEntityPosAtNode}).
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
            Vec3 a = aim.apply(j);
            if (clear(x, z, a.x, a.z, y, hw, cells))
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
