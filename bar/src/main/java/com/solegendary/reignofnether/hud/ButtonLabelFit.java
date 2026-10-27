package com.solegendary.reignofnether.hud;

import java.util.Map;

/**
 * The themed button label's size and place (GothicText.restyleButtons), kept free of client classes so it can be
 * tested headless (ButtonLabelFitTest, ButtonThemeGameTests).
 *
 * Tester, Oct 26: on the title screen and in the lobbies the labels were not centred, overflowed their frames and came
 * in wildly different sizes. The rule now: one font (the plate capitals), one size per button height on a screen (the
 * smallest size at which every label of that height fits, never below FLOOR_CAP), a label that would still not fit at
 * that size shrinks on its own; every label sits inside the frame's padding and is centred both ways.
 */
public final class ButtonLabelFit {

    private ButtonLabelFit() {}

    /** the label's cap height may fill this share of the button's height */
    public static final float HEIGHT_SHARE = 0.60f;
    /** the smallest cap height (GUI px) a label is shrunk to for the sake of a shared size; smaller ones shrink alone */
    public static final float FLOOR_CAP = 5.5f;

    /** Shorter display labels for vanilla buttons whose text cannot fit their frame at a readable size. */
    static final Map<String, String> SHORT = Map.of("Minecraft Realms", "Realms");

    public static String display(String label) {
        return SHORT.getOrDefault(label, label);
    }

    /** The side padding inside the frame (GUI px): 7 on wide buttons, down to 3 on the tiny ones ("x", "T1"). */
    public static float padding(int bw) {
        return Math.max(3f, Math.min(7f, bw / 8f));
    }

    /** The inner width a label may use. */
    public static float innerWidth(int bw) {
        return Math.max(1f, bw - 2f * padding(bw));
    }

    /**
     * The largest scale at which a label fits its own button: {@code labelW} and {@code capH} are the label's width and
     * cap height at scale 1. Never above 1.
     */
    public static float fitScale(float labelW, float capH, int bw, int bh) {
        float byW = innerWidth(bw) / Math.max(1f, labelW);
        float byH = bh * HEIGHT_SHARE / Math.max(1f, capH);
        return Math.max(0.01f, Math.min(1f, Math.min(byW, byH)));
    }

    /**
     * The one size shared by the buttons of one height: the smallest of their fit scales that is not below the floor
     * ({@code floorScale}, the scale of FLOOR_CAP); when all are below it, the largest of them. Each button then draws
     * at {@link #scaleFor}.
     */
    public static float groupScale(float[] fits, float floorScale) {
        float best = Float.MAX_VALUE, fallback = 0f;
        for (float f : fits) {
            if (f >= floorScale)
                best = Math.min(best, f);
            fallback = Math.max(fallback, f);
        }
        return best == Float.MAX_VALUE ? fallback : best;
    }

    /** A button's scale: the shared size, or smaller when its own label would not fit at it. */
    public static float scaleFor(float fit, float group) {
        return Math.min(fit, group);
    }

    /** The left edge of a label {@code w} wide (already scaled), centred in the button. */
    public static float left(int x0, int bw, float w) {
        return x0 + (bw - w) / 2f;
    }

    /** The top of the capitals of a label {@code capH} tall (already scaled), centred in the button. */
    public static float capTop(int y0, int bh, float capH) {
        return y0 + (bh - capH) / 2f;
    }
}
