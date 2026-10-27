package com.solegendary.reignofnether.resources;

/**
 * Which wrecks get a compact metal marker in the RTS view, and how it looks. Pure arithmetic with no Minecraft types, so
 * WreckClientEvents' per-frame work is a thin loop over this and the rule is unit-tested (WreckMarkerRulesTest).
 *
 * <p>Why: lovish, Oct 24 - winning a fight should pay the winner, but the wrecks the dead leave were only labelled
 * with a worker selected and the cursor on them, so players never noticed them. Now every known wreck carries a small
 * number in the RTS view; a big heap is larger and brighter, a commander's wreck (600) the biggest. The cost is bounded:
 * at most {@link #MAX_MARKERS} markers (the richest first), nothing beyond {@link #RANGE} blocks of the camera,
 * nothing outside the frustum or in the fog.
 */
public final class WreckMarkerRules {
    private WreckMarkerRules() { }

    /** A scrap heap, a big heap (a T2/T3 kill, a hero) and a huge one (a commander). */
    public enum Tier { SMALL, BIG, HUGE }

    public static final int BIG_METAL = 150;
    public static final int HUGE_METAL = 500;

    /** At most this many markers a frame (the server keeps up to 150 wrecks). */
    public static final int MAX_MARKERS = 48;
    /** Blocks from the camera beyond which no marker is drawn (the metal-patch gems use the same 260 reach). */
    public static final double RANGE = 240;
    /** Markers are fully opaque to here and fade linearly to {@link #MIN_FADE} at {@link #RANGE}. */
    public static final double FADE_START = 120;
    /** Never fainter than this inside the range: a marker you can see must stay readable. */
    public static final float MIN_FADE = 0.4f;

    public static Tier tier(int metal) {
        if (metal >= HUGE_METAL)
            return Tier.HUGE;
        return metal >= BIG_METAL ? Tier.BIG : Tier.SMALL;
    }

    /** Text scale multiplier for the tier: a big heap reads larger from further up. */
    public static float sizeMult(Tier tier) {
        return switch (tier) {
            case SMALL -> 1f;
            case BIG -> 1.4f;
            case HUGE -> 1.9f;
        };
    }

    /** Opacity (0..1) at {@code dist} blocks from the camera. */
    public static float fade(double dist) {
        if (dist <= FADE_START)
            return 1f;
        if (dist >= RANGE)
            return MIN_FADE;
        return (float) (1 - (1 - MIN_FADE) * (dist - FADE_START) / (RANGE - FADE_START));
    }

    /** The compact text: the bare number for scrap, with BAR's reclaim mark in front for a big or huge heap. */
    public static String label(int metal) {
        return metal >= BIG_METAL ? "⟲ " + metal : Integer.toString(metal);
    }

    /**
     * Picks the wrecks that get a marker out of {@code n} candidates: metal above zero, within {@link #RANGE} (given
     * as squared distance to the camera) and {@code inView} (inside the frustum and the fog's light). When more than
     * {@code cap} qualify, the richest keep their marker (nearer first among equals). The chosen candidate indices
     * go to {@code out} (at least {@code n} long), richest first; returns how many.
     */
    public static int choose(int n, double[] distSq, int[] metal, boolean[] inView, int cap, int[] out) {
        int count = 0;
        double r2 = RANGE * RANGE;
        for (int i = 0; i < n; i++)
            if (metal[i] > 0 && inView[i] && distSq[i] <= r2)
                out[count++] = i;
        if (count <= 1)
            return count;
        // insertion sort by priority (at most the server's 150 wrecks): richest first, then nearest
        for (int a = 1; a < count; a++) {
            int cur = out[a];
            int b = a - 1;
            while (b >= 0 && before(cur, out[b], distSq, metal)) {
                out[b + 1] = out[b];
                b--;
            }
            out[b + 1] = cur;
        }
        return Math.min(count, cap);
    }

    private static boolean before(int a, int b, double[] distSq, int[] metal) {
        if (metal[a] != metal[b])
            return metal[a] > metal[b];
        return distSq[a] < distSq[b];
    }
}
