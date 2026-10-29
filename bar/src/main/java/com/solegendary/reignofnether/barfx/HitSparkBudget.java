package com.solegendary.reignofnether.barfx;

/**
 * Combat feel, part 2 (lovish approved Oct 28): hard caps on hit sparks, so a 4v4 battle with hundreds of units never
 * floods particles while a lone hit always gets its burst. Pure (tested headless in HitSparkBudgetTest), allocation-free.
 *
 * Two token buckets: one for the whole screen ({@link #GLOBAL_PER_SECOND}) and one per chunk
 * ({@link #CHUNK_PER_SECOND}), each holding a quarter of its second's budget and refilling the other three quarters
 * over the second - so no window of one second ever grants more than the budget (bucket + one second of refill), and a
 * burst is limited to the bucket. One hit takes at most {@link #MAX_PER_HIT}. The per-chunk buckets live in a small
 * fixed table; when it is full the bucket that has refilled the most goes - a full bucket is exactly a fresh one, so
 * nothing is lost unless {@link #TABLE} chunks are all hot at once, and the global bucket still caps that.
 */
public final class HitSparkBudget {

    public static final int GLOBAL_PER_SECOND = 240, CHUNK_PER_SECOND = 32, MAX_PER_HIT = 6, TABLE = 64;
    private static final float BURST_SHARE = 0.25f;
    private static final float GLOBAL_CAP = GLOBAL_PER_SECOND * BURST_SHARE, GLOBAL_RATE = GLOBAL_PER_SECOND - GLOBAL_CAP;
    private static final float CHUNK_CAP = CHUNK_PER_SECOND * BURST_SHARE, CHUNK_RATE = CHUNK_PER_SECOND - CHUNK_CAP;

    private float globalTokens = GLOBAL_CAP, globalTime = Float.NaN;
    private final long[] keys = new long[TABLE];
    private final float[] tokens = new float[TABLE], times = new float[TABLE];
    private final boolean[] used = new boolean[TABLE];

    /** Grants up to {@code want} sparks (at most {@link #MAX_PER_HIT}) for a hit in chunk (x, z) at {@code now} seconds. */
    public int take(int chunkX, int chunkZ, float now, int want) {
        if (want <= 0)
            return 0;
        if (Float.isFinite(now)) {
            globalTokens = refill(globalTokens, globalTime, now, GLOBAL_CAP, GLOBAL_RATE);
            if (!(globalTime >= now))
                globalTime = now;
        }
        int slot = slot(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL), now);
        if (Float.isFinite(now)) {
            tokens[slot] = refill(tokens[slot], times[slot], now, CHUNK_CAP, CHUNK_RATE);
            if (!(times[slot] >= now))
                times[slot] = now;
        }
        int n = Math.min(Math.min(want, MAX_PER_HIT), (int) Math.min(globalTokens, tokens[slot]));
        if (n <= 0)
            return 0;
        globalTokens -= n;
        tokens[slot] -= n;
        return n;
    }

    public void reset() {
        globalTokens = GLOBAL_CAP;
        globalTime = Float.NaN;
        java.util.Arrays.fill(used, false);
    }

    /** Tokens after refilling from {@code then} to {@code now}; time that runs backwards (or a first call) adds none. */
    private static float refill(float have, float then, float now, float cap, float rate) {
        if (!Float.isFinite(then) || !(now > then))
            return have;
        return Math.min(cap, have + (now - then) * rate);
    }

    /** The table slot of this chunk: found, else a free one, else the fullest bucket's (refilled to now) - taken fresh. */
    private int slot(long key, float now) {
        int free = -1, fullest = 0;
        float best = -1f;
        for (int i = 0; i < TABLE; i++) {
            if (!used[i]) {
                if (free < 0)
                    free = i;
                continue;
            }
            if (keys[i] == key)
                return i;
            float t = refill(tokens[i], times[i], now, CHUNK_CAP, CHUNK_RATE);
            if (t > best) {
                best = t;
                fullest = i;
            }
        }
        int s = free >= 0 ? free : fullest;
        used[s] = true;
        keys[s] = key;
        tokens[s] = CHUNK_CAP;
        times[s] = Float.isFinite(now) ? now : Float.NaN;
        return s;
    }
}
