package com.solegendary.reignofnether.postfx;

/** Grass tint noise (lovish Oct 22 20:23) - STUB: the test (GrassTintTest) is written first and must fail here. */
public final class GrassTint {

    public static final int GRASS = 1;
    public static final int FOLIAGE = 2;

    private GrassTint() {
    }

    /** Low-frequency noise in [-1, 1] at block (x, z). Not implemented yet. */
    public static double field(int x, int z, int salt) {
        return 0.0;
    }

    /** The colour with the field's variation applied. Not implemented yet. */
    public static int apply(int rgb, int x, int z, int salt) {
        return rgb;
    }
}
