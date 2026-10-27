package com.solegendary.reignofnether.perf;

/**
 * A cap on one-off work per frame (a plain counter, no Minecraft classes, pinned by FrameBudgetTest).
 *
 * buildingDecor spiked to 13.7 ms in lovish's Oct 23 log: a handler worked out something expensive for every new
 * building in the same frame (the banner mount walks every block of a placement; a base coming into view, a fog
 * reveal or a join hands it dozens at once). Ask {@link #tryTake} before each such job and skip the building this
 * frame when it says no: the jobs spread over the next frames, one budget each, and nothing is lost but a banner
 * appearing a few frames late.
 */
public final class FrameBudget {
    private final int perFrame;
    private int left;

    public FrameBudget(int perFrame) {
        this.perFrame = Math.max(0, perFrame);
        this.left = this.perFrame;
    }

    /** Call once at the start of each frame (or render pass) of the handler that owns this budget. */
    public void newFrame() {
        left = perFrame;
    }

    /** True (and spends one) while this frame still has budget. */
    public boolean tryTake() {
        if (left <= 0)
            return false;
        left--;
        return true;
    }
}
