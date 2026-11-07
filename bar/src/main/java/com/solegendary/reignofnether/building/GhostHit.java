package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * The cursor hit test for blueprint ghosts and half-built buildings (BlueprintClientEvents.ghostUnderCursor wraps
 * {@link #target}), free of client classes so JUnit and GameTests check the real decision.
 *
 * <p>A ghost has no blocks, so the cursor ray passes straight through it to the ground: behind it (aiming at its walls)
 * or under it, one block below its footprint (the placement's blocks start one above the ground). Real buildings are
 * found by the block the ray hits; a ghost has to be found by its box instead. The box is the one the ghost is drawn
 * in: x/z minCorner..maxCorner+1, y from minCorner-1 (drawn one lower, BlueprintClientEvents.DRAW_OFFSET) to maxCorner+1.
 */
public final class GhostHit {

    private GhostHit() {}

    /**
     * Where along the ray {@code o + t*d} it enters the ghost box of corners {@code min..max} (block coords, inclusive),
     * or -1 if it misses or the box lies wholly behind {@code o}. Slab method; a ray starting inside returns 0.
     */
    public static double rayEntry(double ox, double oy, double oz, double dx, double dy, double dz,
                                  int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        double[] lo = { minX, minY - 1, minZ };
        double[] hi = { maxX + 1, maxY + 1, maxZ + 1 };
        double[] o = { ox, oy, oz };
        double[] d = { dx, dy, dz };
        double tMin = 0, tMax = Double.MAX_VALUE;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-9) {
                if (o[a] < lo[a] || o[a] > hi[a])
                    return -1;
                continue;
            }
            double t1 = (lo[a] - o[a]) / d[a], t2 = (hi[a] - o[a]) / d[a];
            if (t1 > t2) { double s = t1; t1 = t2; t2 = s; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax)
                return -1;
        }
        return tMin;
    }

    /**
     * Does the ghost of corners {@code min..max} stand on the block the cursor picked ({@code x,y,z})? Inside the
     * footprint's columns and no lower than the ground right under it (two below minCorner covers the drawn offset).
     */
    public static boolean footprintHolds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY - 2 && y <= maxY;
    }

    /**
     * Is the ghost the thing the cursor points at? The ray must enter its box no later than the block it hit
     * ({@code hitT}, plus a block of slack for the box's own floor), so a ghost behind a hill does not steal the
     * click; or the picked block is in its footprint.
     */
    public static boolean hits(double entryT, double hitT, boolean footprintHolds) {
        return footprintHolds || (entryT >= 0 && entryT <= hitT + 1);
    }

    /**
     * Is a real building a box-tested click target (like a ghost)? Only a friendly one still under construction; a
     * finished building keeps the picked-block rule (repair when damaged, else a move), as before.
     */
    public static boolean assistTarget(boolean isBuilt, boolean friendly) {
        return friendly && !isBuilt;
    }

    /**
     * Where along the cursor ray the box of corners {@code min..max} counts as hit - the ray's entry, or {@code hitT}
     * when only the footprint rule holds - or -1 when it is not the thing the cursor points at. {@code picked} is the
     * block the cursor picked (may be null). Smaller is nearer.
     */
    public static double rank(double ox, double oy, double oz, double dx, double dy, double dz, double hitT,
                              BlockPos picked, BlockPos min, BlockPos max) {
        double t = rayEntry(ox, oy, oz, dx, dy, dz,
            min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        boolean holds = picked != null && footprintHolds(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(),
            max.getZ(), picked.getX(), picked.getY(), picked.getZ());
        if (!hits(t, hitT, holds))
            return -1;
        return t >= 0 ? t : hitT;
    }

    /**
     * The right-click target under the cursor ray (origin {@code o}, direction {@code d}, reaching the picked block at
     * {@code hitT}): the nearest friendly ghost, or friendly building still under construction ({@link #assistTarget}),
     * whose box the ray points at; null for none. Half-built buildings count because their missing upper blocks let
     * the ray through to the ground behind or under them (tester, Oct 26: "unable to assist a production in
     * progress"); on a tie the ghost wins. Runs every frame a worker is selected (BlueprintClientEvents
     * .ghostUnderCursor), so it allocates nothing and skips finished buildings before any other test.
     */
    @Nullable
    public static BuildingPlacement target(double ox, double oy, double oz, double dx, double dy, double dz, double hitT,
                                           @Nullable BlockPos picked, List<BuildingPlacement> ghosts,
                                           List<BuildingPlacement> buildings, Predicate<String> friendly) {
        BuildingPlacement best = null;
        double bestT = Double.MAX_VALUE;
        for (int i = 0, n = ghosts.size(); i < n; i++) {
            BuildingPlacement g = ghosts.get(i);
            if (!friendly.test(g.ownerName))
                continue;
            double t = rank(ox, oy, oz, dx, dy, dz, hitT, picked, g.minCorner, g.maxCorner);
            if (t >= 0 && t < bestT) {
                bestT = t;
                best = g;
            }
        }
        for (int i = 0, n = buildings.size(); i < n; i++) {
            BuildingPlacement b = buildings.get(i);
            // the cheap skip first: almost every building is finished
            if (b == null || b.isBuilt || b.ownerName == null || b.minCorner == null || b.maxCorner == null
                    || b.centrePos == null || !assistTarget(b.isBuilt, friendly.test(b.ownerName)))
                continue;
            double t = rank(ox, oy, oz, dx, dy, dz, hitT, picked, b.minCorner, b.maxCorner);
            if (t >= 0 && t < bestT) {
                bestT = t;
                best = b;
            }
        }
        return best;
    }
}
