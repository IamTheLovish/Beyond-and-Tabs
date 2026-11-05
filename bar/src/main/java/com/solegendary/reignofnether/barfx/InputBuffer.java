package com.solegendary.reignofnether.barfx;

/**
 * Holds one ability press that arrives during another action's recovery, for up to {@link #WINDOW_MS} (game feel
 * checklist, "Cooldown" row: a press just before ready still casts). Pure and allocation-free; the caller passes the
 * clock in, so tests drive time by hand. A newer press replaces an older buffered one.
 */
public final class InputBuffer {
    public static final long WINDOW_MS = 150;

    private int slot = -1;
    private long pressedAtMs;

    /**
     * An ability was pressed. Returns true when it should cast now (not busy); false when it was buffered (busy) -
     * ask {@link #take} when the recovery ends.
     */
    public boolean press(int abilitySlot, long nowMs, boolean busy) {
        if (!busy) {
            slot = -1;
            return true;
        }
        slot = abilitySlot;
        pressedAtMs = nowMs;
        return false;
    }

    /** The recovery has ended: the buffered slot if its press is no older than the window, else -1. Clears it. */
    public int take(long nowMs) {
        int s = slot;
        slot = -1;
        return s >= 0 && nowMs - pressedAtMs <= WINDOW_MS ? s : -1;
    }

    public boolean hasPending() {
        return slot >= 0;
    }

    public void clear() {
        slot = -1;
    }
}
