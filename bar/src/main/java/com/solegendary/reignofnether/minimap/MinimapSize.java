package com.solegendary.reignofnether.minimap;

/**
 * The minimap's three sizes: 0 small, 1 large (corner), 2 expanded (a centred overlay square over the screen).
 * Pure rules, no Minecraft imports (MinimapSizeGameTests).
 */
public final class MinimapSize {
    private MinimapSize() { }

    public static final int SMALL = 0, LARGE = 1, EXPANDED = 2;
    /** The overlay's side as a share of the shorter screen side. */
    public static final double EXPANDED_SHARE = 0.85;

    /** M: small -> large -> expanded -> small. */
    public static int next(int level) {
        return (level + 1) % 3;
    }

    /** Saved size; the old boolean hud_minimap_large=true counts as large when no size was saved yet. */
    public static int fromConfig(int savedSize, boolean oldLargeFlag) {
        if (savedSize > 0)
            return Math.min(savedSize, EXPANDED);
        return oldLargeFlag ? LARGE : SMALL;
    }

    /** At start-up an expanded overlay comes back as the large corner map, so the game never opens covered. */
    public static int restoreLevel(int level) {
        return Math.min(level, LARGE);
    }

    /** Side in GUI pixels of the expanded square. */
    public static int expandedSide(int guiW, int guiH) {
        return (int) (Math.min(guiW, guiH) * EXPANDED_SHARE);
    }

    /** {left, top, side} of the centred expanded square. */
    public static int[] expandedRect(int guiW, int guiH) {
        int side = expandedSide(guiW, guiH);
        return new int[] { (guiW - side) / 2, (guiH - side) / 2, side };
    }

    public static boolean inside(int[] rect, double x, double y) {
        return x >= rect[0] && y >= rect[1] && x < rect[0] + rect[2] && y < rect[1] + rect[2];
    }
}
