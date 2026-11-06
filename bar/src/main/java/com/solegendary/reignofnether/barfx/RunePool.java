package com.solegendary.reignofnether.barfx;

/**
 * The client's live rune circles (RuneFxClient draws them), in fixed parallel arrays: {@link #MAX} slots made once, so
 * a 100-caster fight allocates nothing per frame or per cast. No Minecraft types, so the game test can drive it.
 * <p>
 * A circle is drawn while {@code 0 <= clock - born < life} ({@code life} = the event's ticks / 20: the cast time, the
 * same rule as RuneFx.isLive on the server), and for {@link #END_FADE} s after an early end (its caster died). When
 * full, a new circle takes the slot of the one closest to its own end. Clock = BarFxClient's seconds.
 */
public final class RunePool {
    /** The most circles the client draws at once (RuneFx.MAX_LIVE per level on the server). */
    public static final int MAX = 48;
    /** Seconds an ended circle takes to fade out. */
    public static final float END_FADE = 0.25f;

    final int[] id = new int[MAX];
    final float[] x = new float[MAX], y = new float[MAX], z = new float[MAX], radius = new float[MAX];
    final float[] born = new float[MAX], life = new float[MAX], endAt = new float[MAX];
    final byte[] style = new byte[MAX];
    final boolean[] follow = new boolean[MAX];
    int count = 0;

    /**
     * Starts circle {@code id} (or restarts it: the same id again) at {@code clock} for {@code ticks}. Returns its slot;
     * never refuses - when full, the circle closest to its end gives up its slot.
     */
    public int start(int id, float x, float y, float z, float radius, byte style, boolean follow, float clock, int ticks) {
        int i = slotOf(id);
        if (i < 0) {
            prune(clock);
            if (count < MAX)
                i = count++;
            else {
                i = 0;
                for (int k = 1; k < count; k++)
                    if (born[k] + life[k] < born[i] + life[i])
                        i = k;
            }
        }
        this.id[i] = id;
        this.x[i] = x; this.y[i] = y; this.z[i] = z;
        this.radius[i] = Math.max(0.25f, radius);
        this.style[i] = style;
        this.follow[i] = follow;
        born[i] = clock;
        life[i] = Math.max(1, ticks) / 20f;
        endAt[i] = -1;
        return i;
    }

    /** Circle {@code id} ends early at {@code clock}: it fades out over {@link #END_FADE} s. */
    public void end(int id, float clock) {
        int i = slotOf(id);
        if (i >= 0 && endAt[i] < 0)
            endAt[i] = clock;
    }

    /** Is circle {@code id} drawn at {@code clock}? */
    public boolean alive(int id, float clock) {
        int i = slotOf(id);
        return i >= 0 && aliveAt(i, clock);
    }

    boolean aliveAt(int i, float clock) {
        float age = clock - born[i];
        return age >= 0 && age < life[i] && (endAt[i] < 0 || clock - endAt[i] < END_FADE);
    }

    /** How many slots are taken (live or not yet pruned). */
    public int count() {
        return count;
    }

    /** Drops the circles no longer drawn at {@code clock}, keeping the rest packed at the front. */
    void prune(float clock) {
        int w = 0;
        for (int r = 0; r < count; r++) {
            if (!aliveAt(r, clock) && clock - born[r] >= 0)
                continue;
            if (w != r)
                move(r, w);
            w++;
        }
        count = w;
    }

    void clear() {
        count = 0;
    }

    private int slotOf(int id) {
        for (int i = 0; i < count; i++)
            if (this.id[i] == id)
                return i;
        return -1;
    }

    private void move(int from, int to) {
        id[to] = id[from];
        x[to] = x[from]; y[to] = y[from]; z[to] = z[from]; radius[to] = radius[from];
        born[to] = born[from]; life[to] = life[from]; endAt[to] = endAt[from];
        style[to] = style[from];
        follow[to] = follow[from];
    }
}
