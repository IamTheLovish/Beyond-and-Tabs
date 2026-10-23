package com.solegendary.reignofnether.hud;

/**
 * Scroll offset for a title wider than its space: hold at the start, scroll left to show the end, hold, jump back.
 * Pure (time in, pixels out) so it is unit-tested; the HUD clips the text to its space.
 */
public final class NameScroll {

    static final long HOLD_MS = 1500;
    static final float PX_PER_SECOND = 20f;

    private NameScroll() {}

    /** Pixels to shift the text left at time {@code millis}; 0 when it fits. */
    public static int offset(int textWidth, int maxWidth, long millis) {
        int overflow = textWidth - maxWidth;
        if (overflow <= 0)
            return 0;
        long scrollMs = (long) (overflow * 1000f / PX_PER_SECOND);
        long cycle = HOLD_MS + scrollMs + HOLD_MS;
        long t = Math.floorMod(millis, cycle);
        if (t < HOLD_MS)
            return 0;
        if (t < HOLD_MS + scrollMs)
            return Math.round((t - HOLD_MS) * PX_PER_SECOND / 1000f);
        return overflow;
    }
}
