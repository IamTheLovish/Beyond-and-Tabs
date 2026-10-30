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

    /** Nearest first from (fromX, fromZ); equal distances go by x then z so the order never flickers. */
    public static List<BlockPos> nearestFirst(List<BlockPos> patches, double fromX, double fromZ) {
        List<BlockPos> out = new ArrayList<>(patches);
        out.sort(Comparator
            .comparingDouble((BlockPos p) -> (p.getX() - fromX) * (p.getX() - fromX) + (p.getZ() - fromZ) * (p.getZ() - fromZ))
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return out;
    }
}
