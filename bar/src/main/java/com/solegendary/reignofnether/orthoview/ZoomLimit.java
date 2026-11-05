package com.solegendary.reignofnether.orthoview;

/**
 * Pure rule for the furthest zoom-out. Placement at the screen edge failed (ghost red, ground not loaded) because the
 * limit allowed the view 1.5x past the loaded chunk radius; the limit is now exactly the height at which the far
 * corners of the view reach the loaded radius.
 */
public final class ZoomLimit {
    private ZoomLimit() { }

    /** Old rule, kept for comparison in tests: 1.5x past the loaded radius. */
    public static float oldMax(double loadedFitHeight, float floor, float hard) {
        return (float) Math.max(floor * 1.5f, Math.min(hard, loadedFitHeight * 1.5));
    }

    /** New rule: the loaded-radius fit height, never below floor, never above hard. */
    public static float max(double loadedFitHeight, float floor, float hard) {
        return (float) Math.max(floor, Math.min(hard, loadedFitHeight));
    }
}
