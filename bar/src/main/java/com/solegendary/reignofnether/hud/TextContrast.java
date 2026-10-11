package com.solegendary.reignofnether.hud;

/**
 * WCAG contrast maths for HUD text on its dark backings (side-neutral, no Minecraft). lovish's clip: kill-feed names
 * drawn in the owner's colour - dark blue for him - on a dark-green world could not be read. Text colours that carry
 * an owner now go through {@link #readable}: the owner hue, lightened towards white until it reaches
 * {@link #MIN_RATIO} against the backing it sits on, composited over a white world (the worst case for a
 * translucent dark backing). The true owner colour moves to a small swatch beside the name.
 */
public final class TextContrast {

    public static final double MIN_RATIO = 4.5;

    private TextContrast() { }

    private static double channel(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    public static double luminance(int rgb) {
        return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF) + 0.0722 * channel(rgb & 0xFF);
    }

    public static double ratio(int rgbA, int rgbB) {
        double a = luminance(rgbA), b = luminance(rgbB);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** A backing colour of the given alpha (0..255) composited over a white world: the lightest it can look. */
    public static int worstCaseBacking(int backingRgb, int alpha) {
        double t = alpha / 255.0;
        int r = (int) Math.round(((backingRgb >> 16) & 0xFF) * t + 255 * (1 - t));
        int g = (int) Math.round(((backingRgb >> 8) & 0xFF) * t + 255 * (1 - t));
        int b = (int) Math.round((backingRgb & 0xFF) * t + 255 * (1 - t));
        return (r << 16) | (g << 8) | b;
    }

    /** rgb mixed towards white by f (0..1). */
    public static int lighten(int rgb, double f) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = (int) Math.round(r + (255 - r) * f);
        g = (int) Math.round(g + (255 - g) * f);
        b = (int) Math.round(b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * The colour lightened in 10% steps until it reads at MIN_RATIO on the backing (white always does on the dark
     * backings the HUD uses). Cheap enough to call once per laid-out line, not per frame.
     */
    public static int readable(int rgb, int backingRgb) {
        rgb &= 0xFFFFFF;
        for (int step = 0; step <= 10; step++) {
            int c = step == 0 ? rgb : lighten(rgb, step / 10.0);
            if (ratio(c, backingRgb) >= MIN_RATIO)
                return c;
        }
        return 0xFFFFFF;
    }
}
