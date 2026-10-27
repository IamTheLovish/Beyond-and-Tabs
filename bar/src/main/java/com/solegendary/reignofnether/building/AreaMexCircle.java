package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * BAR's area mex, the pure part: a circle dragged out from its centre picks every free metal patch inside it, ordered
 * nearest-first from where the builders stand. No client classes, so AreaMexDragGameTests checks it headless;
 * PatchQuickBuildClientEvents does the mouse, the circle drawing and the orders.
 */
public final class AreaMexCircle {

    private AreaMexCircle() { }

    /** A press-release closer than this (GUI pixels) is a plain click: one extractor, as before. */
    public static final double CLICK_PIXELS = 5.0;
    /** The largest circle (blocks). */
    public static final int MAX_RADIUS = 64;

    public static boolean isClick(double dxPixels, double dyPixels) {
        return dxPixels * dxPixels + dyPixels * dyPixels < CLICK_PIXELS * CLICK_PIXELS;
    }

    /** Horizontal distance from the circle's centre to the cursor, clamped to {@link #MAX_RADIUS}. */
    public static double radius(BlockPos centre, BlockPos cursor) {
        double dx = cursor.getX() - centre.getX(), dz = cursor.getZ() - centre.getZ();
        return Math.min(MAX_RADIUS, Math.sqrt(dx * dx + dz * dz));
    }

    /** Patch centres inside the circle (the edge counts) that are not taken, in input order. */
    public static List<BlockPos> patchesIn(Collection<BlockPos> centres, Predicate<BlockPos> taken, BlockPos centre,
                                           double radius) {
        List<BlockPos> out = new ArrayList<>();
        double r2 = radius * radius;
        for (BlockPos c : centres) {
            double dx = c.getX() - centre.getX(), dz = c.getZ() - centre.getZ();
            if (dx * dx + dz * dz <= r2 && !taken.test(c) && !out.contains(c))
                out.add(c);
        }
        return out;
    }

    /**
     * The build order of one area-mex command, as BAR orders it: a greedy nearest-neighbour tour from (fromX, fromZ) -
     * each next site is the one nearest the previous site. Sorting by distance from the start ({@link #nearestFirst})
     * zig-zagged across a ring of patches (tester, Oct 26: "this pathing doesn't make sense"). Ties go by x then z so
     * the order never flickers. The input is not changed.
     */
    public static List<BlockPos> tour(List<BlockPos> patches, double fromX, double fromZ) {
        List<BlockPos> left = new ArrayList<>(patches);
        List<BlockPos> out = new ArrayList<>(left.size());
        double x = fromX, z = fromZ;
        while (!left.isEmpty()) {
            final double px = x, pz = z;
            BlockPos next = left.stream().min(Comparator
                .comparingDouble((BlockPos p) -> (p.getX() - px) * (p.getX() - px) + (p.getZ() - pz) * (p.getZ() - pz))
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).get();
            left.remove(next);
            out.add(next);
            x = next.getX();
            z = next.getZ();
        }
        return out;
    }

    /**
     * Where an area-mex tour starts, as BAR's getAvgPositionOfValidBuilders: the average over the builders of where
     * each one stands, or - with Shift (the new sites join the end of their queues) - where its queue ends, if it has
     * one. {@code here} and {@code queueEnd} are per builder, x/z; a null queue end falls back to where it stands.
     * Null when there is no builder.
     */
    public static double[] tourStart(List<double[]> here, List<int[]> queueEnd, boolean shift) {
        if (here.isEmpty())
            return null;
        double sx = 0, sz = 0;
        for (int i = 0; i < here.size(); i++) {
            int[] end = shift && i < queueEnd.size() ? queueEnd.get(i) : null;
            sx += end != null ? end[0] : here.get(i)[0];
            sz += end != null ? end[1] : here.get(i)[1];
        }
        return new double[] { sx / here.size(), sz / here.size() };
    }

    /** Horizontal length of the walk from (fromX, fromZ) through {@code sites} in order. */
    public static double pathLength(List<BlockPos> sites, double fromX, double fromZ) {
        double len = 0, x = fromX, z = fromZ;
        for (BlockPos p : sites) {
            len += Math.hypot(p.getX() - x, p.getZ() - z);
            x = p.getX();
            z = p.getZ();
        }
        return len;
    }

    /** Nearest first from (fromX, fromZ); equal distances go by x then z so the order never flickers. */
    public static List<BlockPos> nearestFirst(List<BlockPos> patches, double fromX, double fromZ) {
        List<BlockPos> out = new ArrayList<>(patches);
        out.sort(Comparator
            .comparingDouble((BlockPos p) -> (p.getX() - fromX) * (p.getX() - fromX) + (p.getZ() - fromZ) * (p.getZ() - fromZ))
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return out;
    }
}
