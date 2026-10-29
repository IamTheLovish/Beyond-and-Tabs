package com.solegendary.reignofnether.postfx;

/**
 * Canopy shade (critic r40, Oct 29: "plain lane: trees float" - the plain lane has no shadow under a crown, so a tree
 * stands on a lawn as bright as open meadow; target: lawn 15-20% darker in a ~2-block ring under crowns). The grass
 * colour under and round a crown is darkened by how much canopy covers it, on the same per-position colour path as
 * {@link GrassTint} (so Embeddium blends it per vertex corner: a soft edge, no block steps).
 *
 * <p>Coverage is a weighted 9-tap sample: the block itself and the blocks 1 and 2 away on each axis, each tap 1 where
 * leaves stand over that column within {@link #REACH} blocks. Full cover darkens by {@link #DARK}; the edge of a crown
 * fades out over the 2-block ring. Only without a shader pack - a pack draws real shadows there, and both would be
 * twice as dark (critic r40: the shader lane's contact shadows already sell the canopy).
 *
 * <p>Cost: per colour sample 9 heightmap reads and up to 9 block reads (the column's top block), only while a chunk
 * section is meshed. Known limit: a crown that is cut down leaves its shade until the grass's section is meshed again.
 */
public final class CanopyShade {

    /** Leaves over (x, z) within reach of the ground at y. */
    @FunctionalInterface
    public interface Canopy {
        boolean over(int x, int y, int z);
    }

    static final double DARK = 0.20;
    static final int REACH = 16;
    static final int[][] TAPS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
    static final double[] WEIGHTS = {0.28, 0.09, 0.09, 0.09, 0.09, 0.09, 0.09, 0.09, 0.09};

    private CanopyShade() {
    }

    /** Canopy cover in [0, 1] at ground (x, y, z). */
    public static double cover(Canopy canopy, int x, int y, int z) {
        double c = 0;
        for (int i = 0; i < TAPS.length; i++) {
            if (canopy.over(x + TAPS[i][0], y, z + TAPS[i][1]))
                c += WEIGHTS[i];
        }
        return Math.min(1.0, c);
    }

    /** rgb (0xRRGGBB) darkened by the canopy cover at (x, y, z); the hue is kept. */
    public static int apply(int rgb, Canopy canopy, int x, int y, int z) {
        double c = cover(canopy, x, y, z);
        if (c <= 0)
            return rgb;
        double k = 1 - DARK * c;
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * k);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * k);
        int b = (int) Math.round((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * The canopy test for a column: the highest motion-blocking block (heightmap top - 1) is a leaf and stands above
     * the ground at y, no more than REACH blocks up. A trunk's log tops no column under the crown, and grass plants
     * and flowers are not motion-blocking, so they never count.
     */
    public static boolean leavesOver(int top, boolean topIsLeaves, int y) {
        int h = top - 1;
        return topIsLeaves && h > y && h - y <= REACH;
    }
}
