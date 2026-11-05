package com.solegendary.reignofnether.barfx;

/**
 * The particle allowance of one cast (design checklist: at most 200 per cast at High; the graphics preset scales
 * the cap). Make one per cast, ask {@link #take} for what you want to spawn and spawn only what it grants.
 */
public final class ParticleBudget {
    public static final int HIGH = 200;

    private final int cap;
    private int used;

    public ParticleBudget(int cap) {
        this.cap = Math.max(0, cap);
    }

    public static ParticleBudget high() {
        return new ParticleBudget(HIGH);
    }

    /** How many of {@code want} may be spawned; the rest are dropped. */
    public int take(int want) {
        int n = Math.max(0, Math.min(want, cap - used));
        used += n;
        return n;
    }

    public int used() {
        return used;
    }

    public int left() {
        return cap - used;
    }
}
