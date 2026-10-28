package com.solegendary.reignofnether.postfx;

/**
 * Grass tint noise (lovish Oct 22 20:23, "yes this sounds good"; Oct 28 "the grass needs a lot more work, looks like
 * 1 bland color from rts view"): the biome colour of grass and foliage varies across the world the way a real meadow
 * does seen from above, so a big grass field is not one flat green from the RTS camera. A block texture cannot do
 * this: the pack's grass is one tile per block, and any patch drawn into it repeats on every block (tools/gen_blocks.py,
 * the SCAN section); a field's large-scale variation has to come from the colour the game multiplies the texture by.
 *
 * <p>Three things, each on its own deterministic field:
 * <ul>
 *   <li><b>Value</b> (+-12%): three octaves (96, 28 and 7 blocks) through a soft contrast curve, so patches have
 *       shapes rather than one smooth blur.</li>
 *   <li><b>Dry / lush</b> (+-10%): toward straw-olive (red up, blue down) or toward deep blue-green.</li>
 *   <li><b>Lush clumps</b>: 12-block clumps of darker, bluer grass that gather in the lush zones and fade out in the
 *       dry ones (clumps scattered evenly everywhere read as camouflage - preview round 2, Oct 28).</li>
 * </ul>
 * Tuned on tools/grass_tint_preview.py (a 96 x 96 block field at RTS zoom with the pack's grass texture): the patch
 * scale luma std went 0.003 (Oct 22 version) -> 0.019; a flat field is 0.0004.
 *
 * <p>Cost: a few hash lookups per call, only where the game asks for a biome colour - while a chunk section is meshed
 * (BiomeColorsMixin), never per frame. Deterministic: the same block always gets the same colour, on every client.
 * Continuous: value noise with a quintic fade, so neighbouring blocks differ by a shade and, under Embeddium (in the
 * pack), the colour is blended per vertex corner - a smooth gradient, no block steps (GrassTintTest).
 */
public final class GrassTint {

    /** Salts: grass and foliage vary on their own fields (a tree is not always as dry as the grass under it). */
    public static final int GRASS = 1;
    public static final int FOLIAGE = 2;

    // value field: meadow moods, patches inside them, drifts inside those (blocks per lattice cell, weight)
    static final double[] CELLS = {96.0, 28.0, 7.0};
    static final double[] WEIGHTS = {1.0, 0.7, 0.3};
    static final double CONTRAST = 2.0;     // tanh shaping: patches get edges instead of one smooth blur
    static final double VALUE = 0.12;       // +-12% brightness
    static final double DRY = 0.10;         // toward straw: red +10%, blue -14%; toward lush the other way
    static final double DRY_GREEN = -0.10;  // straw is a touch less green, lush a touch more

    // lush clumps: darker, bluer grass gathered where the field is lush
    static final double[] CLUMP_CELLS = {12.0, 5.0};
    static final double[] CLUMP_WEIGHTS = {1.0, 0.2};
    static final double CLUMP_AT = 0.10;    // clump noise above this starts a clump ...
    static final double CLUMP_SOFT = 0.9;   // ... and is full this much above it (soft: value noise is steep)
    static final double CLUMP_DARK = 0.09;  // a full clump is 9% darker
    static final double CLUMP_HUE = 0.5;    // and pushed this far toward lush
    static final double GATE_AT = -0.2;     // clumps appear where dryness < GATE_AT + 0.5, fully below GATE_AT

    private GrassTint() {
    }

    /** The shaped value field in [-1, 1] at block (x, z), its own field per salt. */
    public static double field(int x, int z, int salt) {
        return shape(octaves(x, z, salt * 8, CELLS, WEIGHTS));
    }

    /** rgb (0xRRGGBB) with the variation in value, in hue (dry / lush) and the lush clumps applied at block (x, z). */
    public static int apply(int rgb, int x, int z, int salt) {
        double v = 1 + VALUE * field(x, z, salt);
        double dry = shape(octaves(x + 7919, z - 4583, (salt + 101) * 8, CELLS, WEIGHTS));
        double clump = octaves(x - 3001, z + 1777, (salt + 202) * 8, CLUMP_CELLS, CLUMP_WEIGHTS);
        double gate = clamp01((-dry - GATE_AT) / 0.5);
        double m = smooth(clamp01((clump - CLUMP_AT) / CLUMP_SOFT)) * gate;
        v *= 1 - CLUMP_DARK * m;
        double d = DRY * (dry - CLUMP_HUE * m);
        int r = channel(((rgb >> 16) & 0xFF) * v * (1 + d));
        int g = channel(((rgb >> 8) & 0xFF) * v * (1 + DRY_GREEN * d));
        int b = channel((rgb & 0xFF) * v * (1 - 1.4 * d));
        return (r << 16) | (g << 8) | b;
    }

    private static double octaves(int x, int z, int seed, double[] cells, double[] weights) {
        double s = 0, w = 0;
        for (int i = 0; i < cells.length; i++) {
            s += weights[i] * valueNoise(x / cells[i], z / cells[i], seed + i);
            w += weights[i];
        }
        return s / w;
    }

    private static double shape(double f) {
        return Math.tanh(CONTRAST * f) / Math.tanh(CONTRAST);
    }

    private static double clamp01(double t) {
        return Math.max(0, Math.min(1, t));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static int channel(double c) {
        return (int) Math.max(0, Math.min(255, Math.round(c)));
    }

    private static double valueNoise(double x, double z, int seed) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double u = fade(x - ix);
        double v = fade(z - iz);
        double a = lattice(ix, iz, seed);
        double b = lattice(ix + 1, iz, seed);
        double c = lattice(ix, iz + 1, seed);
        double d = lattice(ix + 1, iz + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** A lattice point's value in [-1, 1): a 64-bit mix of its coordinates and seed (splitmix64's finaliser). */
    private static double lattice(int ix, int iz, int seed) {
        long h = ix * 0x9E3779B97F4A7C15L ^ iz * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
