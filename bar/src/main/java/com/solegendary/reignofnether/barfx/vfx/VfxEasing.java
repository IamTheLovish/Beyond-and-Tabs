package com.solegendary.reignofnether.barfx.vfx;

import java.util.Locale;

/**
 * Easing curves for the effects (the textbook formulas, written out here): growth of a layer ("ease" in a layer),
 * and how a particle's size and alpha move over its life ("size_ease", "alpha_ease" in an emitter). Every curve maps
 * 0 -> 0 and 1 -> 1; back and elastic overshoot in between (a ring that snaps open past its size and settles).
 * Pure Java (JUnit-tested).
 */
public enum VfxEasing {
    LINEAR, SMOOTHSTEP,
    SINE_IN, SINE_OUT, SINE_IN_OUT,
    QUAD_IN, QUAD_OUT, QUAD_IN_OUT,
    CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
    QUART_IN, QUART_OUT, QUART_IN_OUT,
    QUINT_IN, QUINT_OUT, QUINT_IN_OUT,
    EXPO_IN, EXPO_OUT, EXPO_IN_OUT,
    CIRC_IN, CIRC_OUT, CIRC_IN_OUT,
    BACK_IN, BACK_OUT, BACK_IN_OUT,
    ELASTIC_OUT, BOUNCE_OUT;

    private static final float BACK = 1.70158f, BACK2 = BACK * 1.525f;

    /** The curve at x (clamped to 0..1). */
    public float apply(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        switch (this) {
            case LINEAR: return x;
            case SMOOTHSTEP: return x * x * (3 - 2 * x);
            case SINE_IN: return 1 - (float) Math.cos(x * Math.PI / 2);
            case SINE_OUT: return (float) Math.sin(x * Math.PI / 2);
            case SINE_IN_OUT: return (float) (-(Math.cos(Math.PI * x) - 1) / 2);
            case QUAD_IN: return pIn(x, 2);
            case QUAD_OUT: return pOut(x, 2);
            case QUAD_IN_OUT: return pInOut(x, 2);
            case CUBIC_IN: return pIn(x, 3);
            case CUBIC_OUT: return pOut(x, 3);
            case CUBIC_IN_OUT: return pInOut(x, 3);
            case QUART_IN: return pIn(x, 4);
            case QUART_OUT: return pOut(x, 4);
            case QUART_IN_OUT: return pInOut(x, 4);
            case QUINT_IN: return pIn(x, 5);
            case QUINT_OUT: return pOut(x, 5);
            case QUINT_IN_OUT: return pInOut(x, 5);
            case EXPO_IN: return (float) Math.pow(2, 10 * x - 10);
            case EXPO_OUT: return 1 - (float) Math.pow(2, -10 * x);
            case EXPO_IN_OUT: return x < 0.5f ? (float) Math.pow(2, 20 * x - 10) / 2 : (2 - (float) Math.pow(2, -20 * x + 10)) / 2;
            case CIRC_IN: return 1 - (float) Math.sqrt(1 - x * x);
            case CIRC_OUT: return (float) Math.sqrt(1 - (x - 1) * (x - 1));
            case CIRC_IN_OUT: return x < 0.5f ? (1 - (float) Math.sqrt(1 - 4 * x * x)) / 2
                : ((float) Math.sqrt(1 - (-2 * x + 2) * (-2 * x + 2)) + 1) / 2;
            case BACK_IN: return (BACK + 1) * x * x * x - BACK * x * x;
            case BACK_OUT: { float y = x - 1; return 1 + (BACK + 1) * y * y * y + BACK * y * y; }
            case BACK_IN_OUT: return x < 0.5f
                ? (4 * x * x * ((BACK2 + 1) * 2 * x - BACK2)) / 2
                : ((2 * x - 2) * (2 * x - 2) * ((BACK2 + 1) * (2 * x - 2) + BACK2) + 2) / 2;
            case ELASTIC_OUT: return (float) (Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
            case BOUNCE_OUT: return bounce(x);
            default: return x;
        }
    }

    private static float pIn(float x, int n) { return (float) Math.pow(x, n); }

    private static float pOut(float x, int n) { return 1 - (float) Math.pow(1 - x, n); }

    private static float pInOut(float x, int n) {
        return x < 0.5f ? (float) Math.pow(2, n - 1) * (float) Math.pow(x, n) : 1 - (float) Math.pow(-2 * x + 2, n) / 2;
    }

    private static float bounce(float x) {
        final float n = 7.5625f, d = 2.75f;
        if (x < 1 / d) return n * x * x;
        if (x < 2 / d) { x -= 1.5f / d; return n * x * x + 0.75f; }
        if (x < 2.5f / d) { x -= 2.25f / d; return n * x * x + 0.9375f; }
        x -= 2.625f / d;
        return n * x * x + 0.984375f;
    }

    /** By JSON name ("cubic_out", "back-out", "Smoothstep"), or null. */
    public static VfxEasing byName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
