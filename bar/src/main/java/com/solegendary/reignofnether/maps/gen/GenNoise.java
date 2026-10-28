package com.solegendary.reignofnether.maps.gen;

/**
 * Seeded 2D gradient noise and its fractal sums, for the generated maps. The gradients come from {@link GenRng} and
 * their directions from {@link StrictMath}, so the field is bit-identical on every JVM (Math.sin/cos may differ in the
 * last bit between a player's client and the server; one flipped rounding would move a cliff by a block between the
 * lobby preview and the map that is built).
 */
final class GenNoise {
    private final int[] perm = new int[512];
    private final double[] gx = new double[256], gz = new double[256];

    GenNoise(long seed) {
        GenRng r = new GenRng(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++)
            p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++)
            perm[i] = p[i & 255];
        for (int i = 0; i < 256; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            gx[i] = StrictMath.cos(a);
            gz[i] = StrictMath.sin(a);
        }
    }

    /** Gradient noise at (x, z) in lattice units, roughly in [-1, 1] (standard deviation about 0.3). */
    double at(double x, double z) {
        int x0 = (int) x, z0 = (int) z;
        if (x < x0)
            x0--;
        if (z < z0)
            z0--;
        double fx = x - x0, fz = z - z0;
        int xi = x0 & 255, zi = z0 & 255;
        double n00 = dot(perm[perm[xi] + zi], fx, fz);
        double n10 = dot(perm[perm[xi + 1] + zi], fx - 1, fz);
        double n01 = dot(perm[perm[xi] + zi + 1], fx, fz - 1);
        double n11 = dot(perm[perm[xi + 1] + zi + 1], fx - 1, fz - 1);
        double u = fade(fx), v = fade(fz);
        double a = n00 + (n10 - n00) * u, b = n01 + (n11 - n01) * u;
        return 1.41 * (a + (b - a) * v);
    }

    private double dot(int h, double x, double z) {
        return gx[h] * x + gz[h] * z;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /**
     * A fractal sum: {@code octaves} layers, the first with the given wavelength in blocks, each next one at half the
     * wavelength and {@code gain} times the amplitude; normalised so the result stays roughly in [-1, 1].
     */
    double fbm(double x, double z, double wavelength, int octaves, double gain) {
        double f = 1 / wavelength, amp = 1, sum = 0, norm = 0;
        for (int o = 0; o < octaves; o++) {
            // each octave is offset so the lattice points of the layers never line up (no grid-aligned artefacts)
            sum += amp * at(x * f + o * 17.31, z * f - o * 11.17);
            norm += amp;
            amp *= gain;
            f *= 2;
        }
        return sum / norm;
    }

    /** Ridged noise in [0, 1]: 1 on the zero line of the base noise, so thin crests wander across the map. */
    double ridged(double x, double z, double wavelength) {
        double n = 1 - Math.abs(fbm(x, z, wavelength, 3, 0.5) * 1.6);
        return Math.max(0, Math.min(1, n));
    }
}
