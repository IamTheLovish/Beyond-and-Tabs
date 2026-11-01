package com.solegendary.reignofnether.postfx;

/**
 * Canopy shade (critic r40, Oct 29: "plain lane: trees float" - the plain lane has no shadow under a crown, so a tree
 * stands on a lawn as bright as open meadow; target: lawn 15-20% darker in a ~2-block ring under crowns). The grass
 * colour under and round a crown is darkened by how much canopy covers it, on the same per-position colour path as
 * {@link GrassTint} (so Embeddium blends it per vertex corner: a soft edge, no block steps).
 *
 * <p>Coverage is a Gaussian-weighted disc of 21 taps (every block within ~2 of this one), each tap 1 where something
 * stands over that column ({@link #covered}: the cell over the ground has less than full sky light - leaves take one
 * level off, anything solid more). Full cover darkens by {@link #DARK}; the edge of a crown
 * fades out over the 2-block ring. Only without a shader pack - a pack draws real shadows there, and both would be
 * twice as dark (critic r40: the shader lane's contact shadows already sell the canopy).
 *
 * <p>Sky light, not the heightmap and the leaf block (Oct 31, lovish's crash 03:07: those were read from the client
 * level on an Embeddium meshing worker while the render thread wrote that chunk's palette - "Missing Palette entry").
 * The colour call's own level is the meshing region: Embeddium's slice unpacks blocks only within 2 of the meshed
 * section (a crown 4-7 blocks over sea-level grass is outside it) but holds the sections' whole light, and the cell
 * over the ground is always inside it; vanilla's chunk region reads light as vanilla's own meshing does. So a column
 * is judged by one light read there (postfx/CanopyRegion). Unlike the leaf test before, a roof or an overhang over
 * the grass shades it too - the same contact darkness a crown gives.
 *
 * <p>Cost: per colour sample 21 sky-light reads, only while a chunk section is meshed. Known limit: a crown that is
 * cut down leaves its shade until the grass's section is meshed again (a light change re-meshes it).
 */
public final class CanopyShade {

    /** Leaves over (x, z) within reach of the ground at y. */
    @FunctionalInterface
    public interface Canopy {
        boolean over(int x, int y, int z);
    }

    // r3 (eyes r43, 40873305298): the round pool's visible rim beside a trunk measured 10% (r1's diamond 21%) - the
    // full-strength centre sits under the crown, out of the RTS camera's sight. 0.25 puts the rim back near 15-20%
    static final double DARK = 0.25;
    static final int REACH = 16;   // the old leaf test's reach (leavesOver), kept for its tests
    /**
     * r2 (eyes r42, tree_sampler_a plain: 21% darker under a crown, on target, but the 9 axis taps drew a stepped
     * DIAMOND round each trunk): every block within 2.24 of the centre (21 taps, a disc), Gaussian-weighted (sigma
     * 1.3), so the pool is round and its rim fades instead of stepping.
     */
    static final double SIGMA = 1.3;
    static final int[][] TAPS;
    static final double[] WEIGHTS;

    static {
        java.util.List<int[]> taps = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (dx * dx + dz * dz <= 5)
                    taps.add(new int[]{dx, dz});
        TAPS = taps.toArray(new int[0][]);
        WEIGHTS = new double[TAPS.length];
        double sum = 0;
        for (int i = 0; i < TAPS.length; i++) {
            double d2 = TAPS[i][0] * TAPS[i][0] + TAPS[i][1] * TAPS[i][1];
            WEIGHTS[i] = Math.exp(-d2 / (2 * SIGMA * SIGMA));
            sum += WEIGHTS[i];
        }
        for (int i = 0; i < WEIGHTS.length; i++)
            WEIGHTS[i] /= sum;
    }

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

    /** The canopy test from the cell over the ground: anything over the column takes sky light off it. */
    public static boolean covered(int skyLightOverGround) {
        return skyLightOverGround < 15;
    }

    /**
     * The canopy test before Oct 31 (heightmap and top block, read from the client level): the highest motion-blocking block (heightmap top - 1) is a leaf and stands above
     * the ground at y, no more than REACH blocks up. A trunk's log tops no column under the crown, and grass plants
     * and flowers are not motion-blocking, so they never count.
     */
    public static boolean leavesOver(int top, boolean topIsLeaves, int y) {
        int h = top - 1;
        return topIsLeaves && h > y && h - y <= REACH;
    }
}
