package com.solegendary.reignofnether.building;

/**
 * The builders actually at work on a rising site, for its tools (claude/faction-construction-oct29.md). Round 14
 * critic: an opaque hammer floated horizontally in mid-air over the roof with no worker under it, and a soft orb sat
 * on a ghost face - the tools were placed on the WALL (any standing face under a missing block), not on anyone. Now a
 * tool belongs to a worker: each nanolathe report (BarFx.nano: the worker's hands, the block it aims at, sent every
 * 8 ticks while it builds) keeps that worker's slot alive, and the tool sits {@link #REACH} in front of the hands at
 * hand height, toward the block. No report for {@link #KEEP} seconds: the worker stopped, its tool goes.
 * <p>Pure - no Minecraft types - so ConstructionCrewTest covers it; fixed arrays, nothing allocated per report or
 * frame.
 */
public final class ConstructionCrew {

    /** Tools (and so workers) shown per site at most. */
    public static final int SLOTS = 3;
    /** The tool's working end this far in front of the hands (blocks); a report within SAME of a slot's hands is that
     *  slot's worker (workers walk slowly, and their beams come 0.4 s apart); a slot lives KEEP seconds after its last
     *  report (two beams missed, then it is gone). */
    public static final float REACH = 0.3f, SAME = 1.5f, KEEP = 1.0f;

    private static final int F = 9;                       // per slot: hx, hy, hz, dx, dz, seen, tx, ty, tz
    private final float[] slot = new float[SLOTS * F];

    public ConstructionCrew() {
        clear();
    }

    /** Nobody at work. */
    public void clear() {
        for (int i = 0; i < SLOTS; i++)
            slot[i * F + 5] = Float.NEGATIVE_INFINITY;
    }

    /**
     * A worker's report at time {@code now}: its hands at (hx, hy, hz), building at (tx, ty, tz). It updates the live
     * slot whose hands are nearest within SAME, else takes the stalest slot.
     */
    public void report(float hx, float hy, float hz, float tx, float ty, float tz, float now) {
        int pick = -1;
        float best = SAME * SAME;
        for (int i = 0; i < SLOTS; i++) {
            int a = i * F;
            if (!(now - slot[a + 5] <= KEEP))
                continue;
            float ex = slot[a] - hx, ey = slot[a + 1] - hy, ez = slot[a + 2] - hz, d = ex * ex + ey * ey + ez * ez;
            if (d <= best) { best = d; pick = i; }
        }
        if (pick < 0) {
            pick = 0;
            for (int i = 1; i < SLOTS; i++)
                if (slot[i * F + 5] < slot[pick * F + 5])
                    pick = i;
        }
        float dx = tx - hx, dz = tz - hz, l = (float) Math.sqrt(dx * dx + dz * dz);
        int a = pick * F;
        if (l > 1e-3f) { dx /= l; dz /= l; }
        else if (now - slot[a + 5] <= KEEP) { dx = slot[a + 3]; dz = slot[a + 4]; }   // straight down: keep its facing
        else { dx = 0; dz = 1; }
        slot[a] = hx; slot[a + 1] = hy; slot[a + 2] = hz; slot[a + 3] = dx; slot[a + 4] = dz; slot[a + 5] = now;
        slot[a + 6] = tx; slot[a + 7] = ty; slot[a + 8] = tz;
    }

    /**
     * Slot {@code i}'s worker at {@code now}: the block it last aimed at ({x, y, z}, the block's centre, into
     * {@code target}) and its hands ({@code hands}). False (both untouched) when no worker is building in that slot.
     * Round 16 critic: the next block to be placed flashes (ConstructionGrid.nextBlock).
     */
    public boolean target(int i, float now, float[] target, float[] hands) {
        if (i < 0 || i >= SLOTS)
            return false;
        int a = i * F;
        if (!(now - slot[a + 5] <= KEEP))
            return false;
        target[0] = slot[a + 6]; target[1] = slot[a + 7]; target[2] = slot[a + 8];
        hands[0] = slot[a]; hands[1] = slot[a + 1]; hands[2] = slot[a + 2];
        return true;
    }

    /** How many slots have a worker at work at {@code now}. */
    public int active(float now) {
        int n = 0;
        for (int i = 0; i < SLOTS; i++)
            if (now - slot[i * F + 5] <= KEEP)
                n++;
        return n;
    }

    /**
     * Slot {@code i}'s tool at {@code now} into {@code out}: {x, y, z} the working end (REACH in front of the hands,
     * at hand height), {dx, dz} the horizontal direction to the block. False (and {@code out} untouched) when no worker
     * is building in that slot.
     */
    public boolean anchor(int i, float now, float[] out) {
        if (i < 0 || i >= SLOTS)
            return false;
        int a = i * F;
        if (!(now - slot[a + 5] <= KEEP))
            return false;
        float dx = slot[a + 3], dz = slot[a + 4];
        out[0] = slot[a] + dx * REACH; out[1] = slot[a + 1]; out[2] = slot[a + 2] + dz * REACH;
        out[3] = dx; out[4] = dz;
        return true;
    }
}
