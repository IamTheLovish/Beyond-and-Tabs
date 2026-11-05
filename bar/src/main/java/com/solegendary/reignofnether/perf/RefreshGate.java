package com.solegendary.reignofnether.perf;

/**
 * "Rebuild this cached list now?" for per-frame draws that only need to know a slowly changing subset (which of 1,700
 * buildings and 500 units carry a range indicator). Due when never built, when {@code interval} ticks have passed,
 * when the clock went backwards (a new world), or when the source list's size changed (something was added or died).
 * Pure Java.
 */
public final class RefreshGate {
    private final long interval;
    private long at = Long.MIN_VALUE;
    private int key = Integer.MIN_VALUE;

    public RefreshGate(long interval) {
        this.interval = interval;
    }

    /** True (and the gate re-arms) when the cache must be rebuilt for this tick and source key (usually sizes). */
    public boolean due(long now, int sourceKey) {
        if (at == Long.MIN_VALUE || now - at >= interval || now < at || sourceKey != key) {
            at = now;
            key = sourceKey;
            return true;
        }
        return false;
    }
}
