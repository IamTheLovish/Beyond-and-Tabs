package com.solegendary.reignofnether.sounds;

/**
 * The mod Sfx throttles (SfxClient, design/audio.md), pure: a minimum gap between plays of each sound, a budget of
 * new sounds per category per tick, and a smoothed "asked per tick" rate per category (busy) - the busier a category,
 * the quieter each of its sounds plays and the more of its far reach is skipped. It counts in whatever tick
 * {@link #clock} gives SfxClient; nothing here reads a clock.
 */
final class SfxBudget {

    /** Above this smoothed request rate a category plays each sound quieter (1/sqrt of the excess). */
    static final float CALM_RATE = 1.5f;
    /** How much of the busy rate each tick keeps. */
    static final float BUSY_KEEP = 0.9f;
    /** Ticks elapsed that the busy rate decays for at most (a long silence clears it anyway). */
    static final int MAX_DECAY_STEPS = 40;

    private final long[] lastPlay;
    private final int[] budget, played, asked;
    private final float[] busy;
    private long tick = Long.MIN_VALUE;

    /** @param budget the most new sounds each category may start in one tick */
    SfxBudget(int sounds, int[] budget) {
        this.lastPlay = new long[sounds];
        this.budget = budget.clone();
        this.played = new int[budget.length];
        this.asked = new int[budget.length];
        this.busy = new float[budget.length];
    }

    /**
     * The tick the throttles count in: real time (SoundTicks), whatever the level's game time does. On the client that
     * is the time of day (SoundTicks): it moves one tick in three at the default Slow day and stands still with the
     * day, and a budget counted in it was renewed only when the day moved.
     */
    static long clock(long levelGameTime, long millis) {
        return SoundTicks.of(millis);
    }

    /** Rolls the per-tick counts over when the tick changed; the busy rate decays once per tick elapsed. */
    void roll(long now) {
        if (now == tick)
            return;
        long steps = tick == Long.MIN_VALUE ? 1 : Math.max(1, Math.min(MAX_DECAY_STEPS, now - tick));
        for (int c = 0; c < busy.length; c++) {
            float b = busy[c] * BUSY_KEEP + asked[c] * (1f - BUSY_KEEP);
            for (long i = 1; i < steps; i++)
                b *= BUSY_KEEP;
            busy[c] = b;
            asked[c] = 0;
            played[c] = 0;
        }
        tick = now;
    }

    /**
     * Gap, budget and, while the category is busy, the far half of the reach: true if the sound may play now (and
     * books it). Call {@link #roll} with the same tick first.
     */
    boolean admit(int sound, int category, int minGapTicks, long now, boolean far) {
        asked[category]++;
        if (inGap(sound, minGapTicks, now))
            return false;
        if (played[category] >= budget[category])
            return false;
        if (busy[category] > CALM_RATE * 2 && far)
            return false;
        played[category]++;
        book(sound, now);
        return true;
    }

    /** The gap alone (a sound for this player only, without position): true if it may play now (and books it). */
    boolean admitGapOnly(int sound, int minGapTicks, long now) {
        if (inGap(sound, minGapTicks, now))
            return false;
        book(sound, now);
        return true;
    }

    /** 1, or 1/sqrt of how far the category's busy rate is over the calm rate. */
    float crowdScale(int category) {
        float b = busy[category];
        return b <= CALM_RATE ? 1f : (float) Math.sqrt(CALM_RATE / b);
    }

    float busy(int category) {
        return busy[category];
    }

    /** Forgets gaps, counts and busy rates. */
    void reset() {
        java.util.Arrays.fill(lastPlay, 0);
        java.util.Arrays.fill(played, 0);
        java.util.Arrays.fill(asked, 0);
        java.util.Arrays.fill(busy, 0);
        tick = Long.MIN_VALUE;
    }

    private boolean inGap(int sound, int minGapTicks, long now) {
        long last = lastPlay[sound];
        return last != 0 && now >= last && now - last < minGapTicks;
    }

    private void book(int sound, long now) {
        lastPlay[sound] = now == 0 ? 1 : now;
    }
}
