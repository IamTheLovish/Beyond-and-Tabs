package com.solegendary.reignofnether.unit.modelling.motion;

import java.util.Arrays;

/**
 * The slots of the death collapses running at once (DeathCollapse): one {@link DeathCollapse#STRIDE}-float slot per
 * entity id in one slab, at most {@code cap} - past it {@link #claim} says no and the plain death plays. Pure, no
 * allocation after construction; render thread only.
 */
public final class DeathCollapsePool {

    private final int cap;
    private final float[] slab;
    private final int[] ids;
    private final boolean[] used;
    private int size = 0;

    public DeathCollapsePool(int cap) {
        this.cap = Math.max(0, cap);
        this.slab = new float[this.cap * DeathCollapse.STRIDE];
        this.ids = new int[this.cap];
        this.used = new boolean[this.cap];
    }

    /** This id's slot base: its live one, else a free one, else -1 (the cap is reached). */
    public int claim(int id) {
        int live = base(id);
        if (live >= 0)
            return live;
        for (int i = 0; i < cap; i++)
            if (!used[i]) {
                used[i] = true;
                ids[i] = id;
                size++;
                return i * DeathCollapse.STRIDE;
            }
        return -1;
    }

    /** This id's slot base, or -1. */
    public int base(int id) {
        for (int i = 0; i < cap; i++)
            if (used[i] && ids[i] == id)
                return i * DeathCollapse.STRIDE;
        return -1;
    }

    public void release(int id) {
        for (int i = 0; i < cap; i++)
            if (used[i] && ids[i] == id) {
                used[i] = false;
                size--;
            }
    }

    /** The id in slot {@code i} (0..cap-1), or Integer.MIN_VALUE for a free slot. */
    public int idAt(int i) {
        return used[i] ? ids[i] : Integer.MIN_VALUE;
    }

    public int cap() {
        return cap;
    }

    public int size() {
        return size;
    }

    public float[] slab() {
        return slab;
    }

    public void clear() {
        Arrays.fill(used, false);
        size = 0;
    }
}
