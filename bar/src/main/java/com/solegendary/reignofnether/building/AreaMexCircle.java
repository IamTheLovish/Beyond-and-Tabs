package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * BAR's area mex, the pure part: a circle dragged out from its centre picks every free metal patch inside it, ordered
 * as one nearest-neighbour chain from where the builders start. No client classes, so AreaMexDragGameTests checks it
 * headless; PatchQuickBuildClientEvents does the mouse, the circle drawing and the orders.
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
     * The build order of one dragged area-mex command: the free patches {@code inside} the circle as one
     * nearest-neighbour {@link AreaMex#chain} that the whole selection shares and builds site by site together. It
     * starts at the builders' average start ({@link AreaMex.Builder#of}: where each queue ends with Shift, else where
     * each stands), or at {@code fallback} with no builder. PatchQuickBuildClientEvents.patchesInDrag sends this order.
     */
    public static List<BlockPos> order(List<BlockPos> inside, List<AreaMex.Builder> builders, BlockPos fallback) {
        if (builders.isEmpty())
            return AreaMex.chain(inside, fallback.getX(), fallback.getZ());
        double sx = 0, sz = 0;
        for (AreaMex.Builder b : builders) {
            sx += b.x();
            sz += b.z();
        }
        return AreaMex.chain(inside, sx / builders.size(), sz / builders.size());
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
}
