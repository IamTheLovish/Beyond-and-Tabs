package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.environment.wind.WindField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Living crowns, the rules (lovish, Nov 4: "ardent [Verdant] buildings can have trees growing out of them with
 * animations"; he picked "Living crowns" with a "Gentle" sway). Every Verdant building already carries a leaf crown
 * as plain blocks (Heartwood Hall 529 cells, the Grove 50-135, Heart of the Wild 569...). CrownRenderClientEvents
 * takes those leaf cells out of the chunk mesh near the camera and draws them itself, moved by these functions:
 * <ul>
 *   <li><b>crowns</b>: the leaf cells of a building fall into crowns, each a 26-connected group, with a pivot at the
 *       middle of its base - where it sits on its trunk;</li>
 *   <li><b>sway</b>: a crown leans with the shared map wind ({@link WindField}: the same gust crosses the map trees and
 *       the buildings), the top furthest, the base not at all (it stays on its trunk); "Gentle": the top moves
 *       {@link #SWAY_PER_BLOCK} of the crown's radius at a full gust of the average wind - ~0.19 block on the
 *       Grove's, ~0.31 on Heartwood Hall's - up to {@link WindField#MAX_WIND_MUL} times that in the strongest wind; a
 *       small flutter rides on it. Both are smooth functions of the vertex's own position, so two cells' shared corner
 *       moves as one and the crown never cracks apart;</li>
 *   <li><b>grow</b>: a leaf cell the builders lay grows out of its middle over {@link #GROW_TICKS} (a little past full
 *       size, then settles), so a crown under construction visibly grows;</li>
 *   <li><b>swell</b>: when the building completes or levels up, the whole crown swells {@link #SWELL} about its pivot
 *       and settles over {@link #SWELL_TICKS} (with a burst of petals, drawn by the renderer).</li>
 * </ul>
 * Pure: no Minecraft types, so JUnit pins the numbers (CrownMotionTest).
 */
public final class CrownMotion {
    private CrownMotion() { }

    /** The crown's top moves this much per block of crown radius at a full gust of the average wind ("Gentle"). */
    public static final float SWAY_PER_BLOCK = 0.04f;
    /** A crown's sway is kept between these (blocks at the top, average wind, full gust): a lone leaf on a wall still
     *  stirs; the biggest crown never sails. */
    public static final float MIN_SWAY = 0.03f, MAX_SWAY = 0.32f;
    /** The flutter on top of the lean at the crown's top (blocks at the average wind) and its rate. */
    public static final float FLUTTER = 0.025f;
    public static final double FLUTTER_HZ = 0.9;
    /** A pushed crown sinks this fraction of its lean (as the map trees' limbs do). */
    public static final float SAG = 0.2f;
    /** A laid leaf cell grows to full size over this many ticks. */
    public static final int GROW_TICKS = 12;
    /** The overshoot of the grow-in ease (easeOutBack's constant): the cell peaks ~4% past full size. */
    static final float GROW_BACK = 1.0f;
    /** On completion and on each level-up the crown swells this much (a scale) and settles over SWELL_TICKS. */
    public static final float SWELL = 0.06f;
    public static final int SWELL_TICKS = 60;
    /** Rough vertices a leaf cell costs when drawn (fancy leaves draw the faces between leaves too): the budget's
     *  estimate before a crown is captured. */
    public static final int VERTS_PER_CELL = 20;

    static final double TWO_PI = Math.PI * 2;

    /**
     * The buildings whose crowns live. Pilot (the brief: one building before the rest): Heartwood Hall and its levels
     * only; {@code verdant} is the building's faction (FactionTraits.VERDANT) or a shared building's Verdant skin.
     */
    public static boolean animates(String structure, boolean verdant) {
        return verdant && structure != null && structure.startsWith("heartwood_hall");
    }

    /**
     * A crown: one 26-connected group of leaf cells.
     *
     * @param cx     the pivot: the middle of the crown's cells (x, z: a cell's middle is x + 0.5) ...
     * @param cz     ...
     * @param baseY  ... at the bottom face of its lowest cells (where it sits on its trunk)
     * @param height the block faces' span: top face of the highest cells minus baseY
     * @param radius the furthest cell middle from the pivot, horizontally
     * @param sway   how far the top moves at a full gust of the average wind ({@link #swayOf})
     * @param cells  how many cells
     */
    public record Crown(float cx, float cz, int baseY, int height, float radius, float sway, int cells) { }

    /** The crown's sway at the top from its radius: SWAY_PER_BLOCK a block, kept within MIN_SWAY..MAX_SWAY. */
    public static float swayOf(float radius) {
        return Math.max(MIN_SWAY, Math.min(MAX_SWAY, radius * SWAY_PER_BLOCK));
    }

    /**
     * Groups leaf cells into crowns. {@code crownOf} (same length as the cells) receives each cell's crown index into
     * the returned list; crowns come biggest first.
     */
    public static List<Crown> crowns(int[] xs, int[] ys, int[] zs, int[] crownOf) {
        int n = xs.length;
        int[] parent = new int[n];
        Map<Long, Integer> at = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            at.put(key(xs[i], ys[i], zs[i]), i);
        }
        for (int i = 0; i < n; i++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        Integer j = at.get(key(xs[i] + dx, ys[i] + dy, zs[i] + dz));
                        if (j != null)
                            union(parent, i, j);
                    }
        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < n; i++)
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        List<List<Integer>> sorted = new ArrayList<>(groups.values());
        // biggest first; ties by the lowest cell index, so the order never depends on hashing
        sorted.sort((a, b) -> a.size() != b.size() ? b.size() - a.size() : Integer.compare(a.get(0), b.get(0)));
        List<Crown> out = new ArrayList<>(sorted.size());
        for (List<Integer> g : sorted) {
            double sx = 0, sz = 0;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int i : g) {
                sx += xs[i] + 0.5;
                sz += zs[i] + 0.5;
                lo = Math.min(lo, ys[i]);
                hi = Math.max(hi, ys[i]);
            }
            float cx = (float) (sx / g.size()), cz = (float) (sz / g.size());
            float r = 0;
            for (int i : g) {
                float dx = xs[i] + 0.5f - cx, dz = zs[i] + 0.5f - cz;
                r = Math.max(r, (float) Math.sqrt(dx * dx + dz * dz));
            }
            int index = out.size();
            for (int i : g)
                crownOf[i] = index;
            out.add(new Crown(cx, cz, lo, hi + 1 - lo, r, swayOf(r), g.size()));
        }
        return out;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb)
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
    }

    /**
     * How much of the crown's lean a point at height y takes: 0 at the base (it stays on its trunk), 1 at the top,
     * quadratic between (a bough bends most toward its tip).
     */
    public static float heightWeight(float y, int baseY, int height) {
        float u = (y - baseY) / Math.max(1, height);
        u = Math.max(0f, Math.min(1f, u));
        return u * u;
    }

    /** The flutter's phase at a vertex: smooth in position, so a corner two cells share has one phase. */
    public static double flutterPhase(double x, double y, double z) {
        return 0.83 * x + 0.61 * y + 1.27 * z;
    }

    /** The flutter's clock term at a time in seconds (the phase moves with it). */
    public static double flutterClock(double seconds) {
        return TWO_PI * FLUTTER_HZ * seconds;
    }

    /**
     * The downwind push of a point, in blocks along the wind heading: the crown's lean at this gust plus the flutter,
     * both scaled by the point's height weight and the match wind.
     *
     * @param sway       the crown's sway ({@link Crown#sway})
     * @param windMul    the match wind multiplier (1 = average), clamped to WindField.MAX_WIND_MUL
     * @param gust       WindField.gust at the crown's pivot (-0.4 .. 1)
     * @param weight     {@link #heightWeight} of the point
     * @param flutterSin sin(flutterPhase + flutterClock) of the point
     */
    public static float push(float sway, double windMul, double gust, float weight, double flutterSin) {
        double w = Math.max(0, Math.min(windMul, WindField.MAX_WIND_MUL));
        return (float) (w * weight * (sway * gust + FLUTTER * flutterSin));
    }

    /** How far a pushed point sinks (a negative y), from the lean part of its push only. */
    public static float sag(float sway, double windMul, double gust, float weight) {
        double w = Math.max(0, Math.min(windMul, WindField.MAX_WIND_MUL));
        return (float) (-SAG * Math.abs(w * weight * sway * gust));
    }

    /** The largest push any point of a crown can get: the top, the strongest wind, gust and flutter at their peaks. */
    public static float maxPush(float sway) {
        return (float) (WindField.MAX_WIND_MUL * (sway + FLUTTER));
    }

    // ---- hanging lanterns (lovish's Verdant reference, Nov 4: lanterns hang on long chains from the branches of a
    //      tree grown round a tower, with a purple-pink glow - "sway and glow are natural animation channels") ----

    /** Lanterns swing at most this far (degrees) in the strongest wind, with this pendulum period. */
    public static final float LANTERN_MAX_DEG = 12f;
    public static final float LANTERN_PERIOD_TICKS = 44f;
    /** The chain's links (cells) between the bough and the lantern: the reference's "long chains" drop the lantern
     *  below the crown's rim, where the RTS camera sees it (one link hid it under the rim: eyes run 41813334313). */
    public static final int LANTERN_CHAIN = 2;
    /** Cells under the hang point that must be open: the chain, the lantern, and room to swing. */
    public static final int LANTERN_CLEAR = LANTERN_CHAIN + 2;

    /** How many lanterns a crown of this radius carries: none under a small crown, one per 2 blocks, at most 6. */
    public static int lanternCount(float radius) {
        return radius < 3f ? 0 : Math.min(6, Math.round(radius / 2f));
    }

    /** A cell's key for {@link #lanterns}' {@code solid} set. */
    public static long cellKey(int x, int y, int z) {
        return key(x, y, z);
    }

    /**
     * Where a building's lanterns hang: under leaf cells of the outer half of each crown whose underside is open - no
     * block of the structure in the {@link #LANTERN_CLEAR} cells below and no leaf anywhere under it (so a lantern
     * never hangs inside the crown, where no camera sees it) - one per angular sector round the pivot, the furthest
     * out in each. Returns {x, y, z, crown} of the leaf cell each hangs from; deterministic.
     *
     * @param solid every non-air cell of the structure ({@link #cellKey})
     */
    public static List<int[]> lanterns(int[] xs, int[] ys, int[] zs, int[] crownOf, List<Crown> crowns,
                                       java.util.Set<Long> solid) {
        java.util.Set<Long> leaves = new java.util.HashSet<>();
        for (int i = 0; i < xs.length; i++)
            leaves.add(key(xs[i], ys[i], zs[i]));
        List<int[]> out = new ArrayList<>();
        for (int c = 0; c < crowns.size(); c++) {
            Crown cr = crowns.get(c);
            int count = lanternCount(cr.radius());
            if (count == 0)
                continue;
            int[] best = new int[count];
            float[] bestR = new float[count];
            java.util.Arrays.fill(best, -1);
            for (int i = 0; i < xs.length; i++) {
                if (crownOf[i] != c)
                    continue;
                float dx = xs[i] + 0.5f - cr.cx(), dz = zs[i] + 0.5f - cr.cz();
                float r = (float) Math.sqrt(dx * dx + dz * dz);
                if (r < 0.5f * cr.radius() || !openBelow(xs[i], ys[i], zs[i], cr.baseY(), solid, leaves))
                    continue;
                int sector = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / TWO_PI * count) % count;
                int b = best[sector];
                if (b < 0 || r > bestR[sector] || r == bestR[sector] && (ys[i] < ys[b] || ys[i] == ys[b] && i < b)) {
                    best[sector] = i;
                    bestR[sector] = r;
                }
            }
            for (int s = 0; s < count; s++)
                if (best[s] >= 0)
                    out.add(new int[] { xs[best[s]], ys[best[s]], zs[best[s]], c });
        }
        return out;
    }

    private static boolean openBelow(int x, int y, int z, int baseY, java.util.Set<Long> solid, java.util.Set<Long> leaves) {
        for (int k = 1; k <= LANTERN_CLEAR; k++)
            if (solid.contains(key(x, y - k, z)))
                return false;
        for (int yy = y - 1; yy >= baseY - LANTERN_CLEAR; yy--)
            if (leaves.contains(key(x, yy, z)))
                return false;
        return true;
    }

    /**
     * A lantern's swing (degrees, positive = downwind) at a time: pushed by the gust and swinging on its own period,
     * scaled by the match wind; never past LANTERN_MAX_DEG.
     *
     * @param phase the lantern's own phase (radians), so a row of lanterns never swings in lockstep
     */
    public static float lanternSwing(double gust, double windMul, double seconds, double phase) {
        double w = Math.max(0, Math.min(windMul, WindField.MAX_WIND_MUL)) / WindField.MAX_WIND_MUL;
        double own = Math.sin(TWO_PI * seconds * 20.0 / LANTERN_PERIOD_TICKS + phase);
        double g = Math.max(-1, Math.min(1, gust));
        return (float) (LANTERN_MAX_DEG * w * (0.55 * g + 0.45 * own));
    }

    /** A laid cell's scale {@code age} ticks after it appeared: 0 .. a little past 1 .. 1 (easeOutBack), 1 once grown. */
    public static float growScale(float age) {
        if (age >= GROW_TICKS)
            return 1f;
        if (age <= 0)
            return 0f;
        float u = age / GROW_TICKS - 1f;
        return 1f + (GROW_BACK + 1f) * u * u * u + GROW_BACK * u * u;
    }

    /** The crown's scale {@code since} ticks after the building completed or levelled up: 1 .. 1 + SWELL .. 1. */
    public static float swellScale(float since) {
        if (since <= 0 || since >= SWELL_TICKS)
            return 1f;
        double s = Math.sin(Math.PI * since / SWELL_TICKS);
        return (float) (1 + SWELL * s * s);
    }

    /**
     * How many of the candidates (nearest first, each with its vertex count) the renderer takes this refresh: nearest
     * first while the total stays within the budget; the nearest is always taken.
     */
    public static int takeCount(int[] vertsNearestFirst, int count, int budget) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += vertsNearestFirst[i];
            if (i > 0 && total > budget)
                return i;
        }
        return count;
    }
}
