package com.solegendary.reignofnether.barfx;

/** Hit spark budgets (stub: the tests come first). */
public final class HitSparkBudget {
    public static final int GLOBAL_PER_SECOND = 240, CHUNK_PER_SECOND = 32, MAX_PER_HIT = 6, TABLE = 64;

    public int take(int chunkX, int chunkZ, float now, int want) { return want; }
    public void reset() { }
}
