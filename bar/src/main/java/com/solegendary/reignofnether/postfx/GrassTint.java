package com.solegendary.reignofnether.postfx;

/**
 * Grass tint noise (lovish Oct 22 20:23, "yes this sounds good"): the biome colour of grass and foliage varies
 * slowly across the world - meadow-sized patches a few percent lighter or darker, drier (straw) or lusher
 * (blue-green) - so a big grass field is not one flat green from the RTS camera. A block texture cannot do this: the
 * pack's grass is one tile per block, and any patch drawn into it repeats on every block (tools/gen_blocks.py, the
 * SCAN section); a field's large-scale variation has to come from the colour the game multiplies the texture by.
 *
 * Cost: a few hash lookups per call, only where the game asks for a biome colour - while a chunk section is meshed
 * (BiomeColorsMixin), never per frame. Deterministic: the same block always gets the same colour, on every client.
 * Continuous: two-octave value noise with a quintic fade on a lattice of 48 and 16 blocks, so neighbouring blocks
 * differ by a fraction of a percent and no block grid shows (GrassTintTest).
 */
public final class GrassTint {

    /** Salts: grass and foliage vary on their own fields (a tree is not always as dry as the grass under it). */
    public static final int GRASS = 1;
    public static final int FOLIAGE = 2;

    static final double CELL_LARGE = 48.0;   // blocks: meadow-sized patches, one or two across the RTS view
    static final double CELL_SMALL = 16.0;   // blocks: drifts inside them
    static final double SMALL_SHARE = 0.25;
    static final double VALUE = 0.045;      // +-4.5% brightness
    static final double DRY = 0.035;        // toward straw: red +3.5%, blue -4.9%; toward lush the other way

    private GrassTint() {
    }

    /** Low-frequency noise in [-1, 1] at block (x, z), its own field per salt. */
    public static double field(int x, int z, int salt) {
        double a = valueNoise(x / CELL_LARGE, z / CELL_LARGE, salt * 2);
        double b = valueNoise(x / CELL_SMALL, z / CELL_SMALL, salt * 2 + 1);
        return (a + SMALL_SHARE * b) / (1 + SMALL_SHARE);
    }

    /** rgb (0xRRGGBB) with the field's variation in value and in hue (dry / lush) applied at block (x, z). */
    public static int apply(int rgb, int x, int z, int salt) {
        double v = 1 + VALUE * field(x, z, salt);
        double dry = DRY * field(x + 7919, z - 4583, salt + 101);    // its own field: hue and value drift apart
        int r = channel(((rgb >> 16) & 0xFF) * v * (1 + dry));
        int g = channel(((rgb >> 8) & 0xFF) * v * (1 + 0.15 * dry));
        int b = channel((rgb & 0xFF) * v * (1 - 1.4 * dry));
        return (r << 16) | (g << 8) | b;
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
