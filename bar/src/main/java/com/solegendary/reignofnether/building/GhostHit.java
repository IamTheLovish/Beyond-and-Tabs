package com.solegendary.reignofnether.building;

/**
 * The cursor hit test for blueprint ghosts (BlueprintClientEvents.ghostUnderCursor), pure so JUnit can check it.
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
}
