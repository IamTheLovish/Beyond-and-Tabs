package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.ExtractorClearance;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated maps' metal laid out like Beyond All Reason's (lovish Oct 27, after the random spread: "design them like
 * beyond all reason and put clumps in strategic positions all around the map including the back rear sides ... clump
 * them, have stranded ones alone. Maybe a path or cluster."). Read off the finished layout (MapLayout, as the game
 * reads it), over 2, 4 and 8 seats, round and square:
 * <ul>
 * <li>metal stands in groups: clumps of 2-4 close together, paths or clusters of up to 6, and a minority of lone,
 *     stranded patches - not an even scatter;</li>
 * <li>every seat has a clump on its rear side (away from the enemy), and the midfield holds contested clumps;</li>
 * <li>home metal is one standard set: the same patches at the same places round every start (and still outside the
 *     build ground: GeneratedMetalTest keeps "not connected to where the townhall spawns");</li>
 * <li>the seats stay within the fairness tolerance, and every patch stands on buildable ground.</li>
 * </ul>
 * Patches closer than LINK are one group; the plaza's 2.0x patches are the middle's own cluster and not counted.
 */
class MetalClumpsTest {

    /** Two patches this close are one group (a clump's patches stand 10-14 apart, the groups much further). */
    static final double LINK = 16;
    /** A group larger than this is a sprawl, not a clump, path or cluster. */
    static final int MAX_GROUP = 6;
    /** A clump of 2-4 fits in this (largest distance between two of its patches). */
    static final double CLUMP_SPAN = 32;
    /** Lone patches are a minority: at most this share of the patches outside the home sets and the plaza. */
    static final double LONE_MAX = 0.30;
    /**
     * Home patches a seat, exactly (design #310 section 4.2, lovish Oct 28: "7 home patches", generated and pre-made
     * alike). Written from the doc, not read from the code, so the code cannot drift from it and still pass.
     */
    static final int HOME_COUNT = 7;

    static final int[] COUNTS = { 2, 4, 8 };
    /** Round and square (GenSeed shape 1 / 2), modifiers on, as MapGenGameTests. */
    static final long[] SEEDS = {
        GenSeed.pack(0x5EED0001L, 1, 0), GenSeed.pack(0x5EED0002L, 2, 0), GenSeed.pack(0x5EED0003L, 1, 0),
        GenSeed.pack(0x5EED0004L, 2, 0), GenSeed.pack(0x5EED0005L, 1, 0), GenSeed.pack(0x5EED0006L, 2, 0) };

    private static final Map<String, MapLayout> BUILT = new HashMap<>();

    static int[] teams(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++)
            t[i] = i < (n + 1) / 2 ? 0 : 1;
        return t;
    }

    static MapLayout layout(int n, long seed) {
        return BUILT.computeIfAbsent(n + "/" + seed, k -> MapLayout.build(MapPreset.GENERATED, teams(n), seed));
    }

    static String tag(int n, long seed) {
        return "n=" + n + " seed " + Long.toHexString(seed) + ": ";
    }

    /** The plaza's 2.0x patches (GenEconomy.centre: 20-25 from the middle). */
    static boolean plaza(MapLayout.Mex m) {
        return m.value() >= 2.0f && Math.hypot(m.dx(), m.dz()) <= 32;
    }

    /** Groups of patches (indices into l.mexes) linked by LINK, the plaza's patches left out. */
    static List<List<Integer>> groups(MapLayout l) {
        int k = l.mexes.size();
        int[] parent = new int[k];
        for (int i = 0; i < k; i++)
            parent[i] = i;
        for (int i = 0; i < k; i++)
            for (int j = i + 1; j < k; j++) {
                MapLayout.Mex a = l.mexes.get(i), b = l.mexes.get(j);
                if (!plaza(a) && !plaza(b) && Math.hypot(a.dx() - b.dx(), a.dz() - b.dz()) < LINK)
                    parent[find(parent, i)] = find(parent, j);
            }
        Map<Integer, List<Integer>> by = new HashMap<>();
        for (int i = 0; i < k; i++)
            if (!plaza(l.mexes.get(i)))
                by.computeIfAbsent(find(parent, i), x -> new ArrayList<>()).add(i);
        return new ArrayList<>(by.values());
    }

    private static int find(int[] p, int i) {
        while (p[i] != i)
            i = p[i] = p[p[i]];
        return i;
    }

    static double[] centroid(MapLayout l, List<Integer> g) {
        double x = 0, z = 0;
        for (int i : g) {
            x += l.mexes.get(i).dx();
            z += l.mexes.get(i).dz();
        }
        return new double[] { x / g.size(), z / g.size() };
    }

    static double span(MapLayout l, List<Integer> g) {
        double best = 0;
        for (int i : g)
            for (int j : g)
                best = Math.max(best, Math.hypot(l.mexes.get(i).dx() - l.mexes.get(j).dx(),
                    l.mexes.get(i).dz() - l.mexes.get(j).dz()));
        return best;
    }

    /** A path: three or more patches strung along a line (each within 6 of the line through the two farthest apart). */
    static boolean path(MapLayout l, List<Integer> g) {
        if (g.size() < 3)
            return false;
        MapLayout.Mex a = null, b = null;
        double far = -1;
        for (int i : g)
            for (int j : g) {
                MapLayout.Mex p = l.mexes.get(i), q = l.mexes.get(j);
                double d = Math.hypot(p.dx() - q.dx(), p.dz() - q.dz());
                if (d > far) {
                    far = d;
                    a = p;
                    b = q;
                }
            }
        if (far < 24)
            return false;
        for (int i : g) {
            MapLayout.Mex p = l.mexes.get(i);
            double cross = Math.abs((b.dx() - a.dx()) * (p.dz() - a.dz()) - (b.dz() - a.dz()) * (p.dx() - a.dx())) / far;
            if (cross > 6)
                return false;
        }
        return true;
    }

    /** Away from the enemy: from the enemy starts' centroid through this start. */
    static double rearAngle(MapLayout l, MapLayout.Slot s) {
        double ex = 0, ez = 0;
        int k = 0;
        for (MapLayout.Slot o : l.slots)
            if (o.team() != s.team()) {
                ex += o.dx();
                ez += o.dz();
                k++;
            }
        return Math.atan2(s.dz() - ez / k, s.dx() - ex / k);
    }

    static double angleDiff(double a, double b) {
        double d = Math.abs(a - b) % (2 * Math.PI);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    /** Straight-line exposure of a spot: own nearest start's distance over that plus the nearest enemy start's. */
    static double exposure(MapLayout l, double x, double z) {
        double own = Double.MAX_VALUE, enemy = Double.MAX_VALUE;
        MapLayout.Slot nearest = null;
        for (MapLayout.Slot s : l.slots) {
            double d = Math.hypot(x - s.dx(), z - s.dz());
            if (d < own) {
                own = d;
                nearest = s;
            }
        }
        for (MapLayout.Slot s : l.slots)
            if (s.team() != nearest.team())
                enemy = Math.min(enemy, Math.hypot(x - s.dx(), z - s.dz()));
        return own / (own + enemy);
    }

    /**
     * Clumps, paths and stranded patches, not a scatter: no group is larger than MAX_GROUP, a group of 2-4 is compact,
     * lone patches are at most LONE_MAX of the patches outside the home sets and the plaza (and every map has some -
     * "have stranded ones alone"), and paths turn up on at least half the maps ("maybe a path or cluster").
     */
    @Test
    void metal_stands_in_clumps_with_a_few_stranded_patches() {
        List<String> problems = new ArrayList<>();
        int maps = 0, withPath = 0;
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = layout(n, seed);
                String tag = tag(n, seed);
                maps++;
                int lone = 0, counted = 0;
                boolean anyPath = false;
                for (List<Integer> g : groups(l)) {
                    int outside = 0;
                    for (int i : g)
                        if (i >= l.homeEnd())
                            outside++;
                    counted += outside;
                    if (g.size() == 1)
                        lone += outside;
                    if (g.size() > MAX_GROUP)
                        problems.add(tag + "a sprawl of " + g.size() + " patches round " + Arrays.toString(centroid(l, g)));
                    else if (g.size() >= 2 && g.size() <= 4 && span(l, g) > CLUMP_SPAN && !path(l, g))
                        problems.add(tag + "a loose group of " + g.size() + " spanning " + Math.round(span(l, g)));
                    anyPath |= path(l, g);
                }
                if (anyPath)
                    withPath++;
                if (counted == 0 || lone > LONE_MAX * counted)
                    problems.add(tag + lone + " of " + counted + " patches stand alone (at most " + (int) (LONE_MAX * 100)
                        + "% may)");
                if (lone == 0)
                    problems.add(tag + "no stranded lone patch");
            }
        if (withPath * 2 < maps)
            problems.add("a path of patches on only " + withPath + " of " + maps + " maps");
        assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ",
            problems.subList(0, Math.min(10, problems.size()))));
    }

    /**
     * Strategic places all round the map: every seat has a clump on its rear side (within 60 degrees of straight away
     * from the enemy, within 130 of its start - "including the back rear sides"), and the midfield (straight-line
     * exposure 0.35-0.65) holds at least max(2, seats / 2) clumps outside the home sets.
     */
    @Test
    void clumps_stand_behind_every_base_and_in_the_midfield() {
        List<String> problems = new ArrayList<>();
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = layout(n, seed);
                String tag = tag(n, seed);
                List<List<Integer>> gs = groups(l);
                for (MapLayout.Slot s : l.slots) {
                    double rear = rearAngle(l, s);
                    boolean found = false;
                    for (List<Integer> g : gs) {
                        if (g.size() < 2)
                            continue;
                        double[] c = centroid(l, g);
                        double d = Math.hypot(c[0] - s.dx(), c[1] - s.dz());
                        if (d <= 130 && angleDiff(Math.atan2(c[1] - s.dz(), c[0] - s.dx()), rear) <= Math.toRadians(60))
                            found = true;
                    }
                    if (!found)
                        problems.add(tag + "seat " + s.index() + " has no clump behind its base");
                }
                int mid = 0;
                for (List<Integer> g : gs) {
                    if (g.size() < 2 || g.get(0) < l.homeEnd())
                        continue;
                    double[] c = centroid(l, g);
                    double x = exposure(l, c[0], c[1]);
                    if (x >= 0.35 && x <= 0.65)
                        mid++;
                }
                if (mid < Math.max(2, n / 2))
                    problems.add(tag + "only " + mid + " clumps in the midfield");
            }
        assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ",
            problems.subList(0, Math.min(10, problems.size()))));
    }

    /**
     * Home metal is standard: the first homeEnd() patches are every seat's set - exactly HOME_COUNT (7) each, the one
     * count every map gives (design #310 section 4.2: "7 home patches", pre-made and generated alike; a clump the ground
     * refuses is never quietly left out) - with the same values and places relative to the start (distance within 1.5
     * blocks, the same bearing from the start's outward direction within 2 blocks of arc) for every seat, whatever its
     * character or ground.
     */
    @Test
    void home_metal_is_the_same_for_every_seat() {
        List<String> problems = new ArrayList<>();
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = layout(n, seed);
                String tag = tag(n, seed);
                int h = l.homeEnd();
                if (h != HOME_COUNT * n) {
                    problems.add(tag + h + " home patches for " + n + " seats (want exactly " + HOME_COUNT + " each)");
                    continue;
                }
                List<double[]> first = null;
                for (MapLayout.Slot s : l.slots) {
                    double out = Math.atan2(s.dz(), s.dx());
                    List<double[]> mine = new ArrayList<>();
                    for (int i = 0; i < h; i++) {
                        MapLayout.Mex m = l.mexes.get(i);
                        if (m.slot() != s.index())
                            continue;
                        double d = Math.hypot(m.dx() - s.dx(), m.dz() - s.dz());
                        double a = Math.atan2(m.dz() - s.dz(), m.dx() - s.dx()) - out;
                        a = Math.atan2(Math.sin(a), Math.cos(a));
                        mine.add(new double[] { a, d, m.value() });
                    }
                    mine.sort((p, q) -> Double.compare(p[0], q[0]));
                    if (mine.size() != HOME_COUNT) {
                        problems.add(tag + "seat " + s.index() + " owns " + mine.size() + " home patches, not " + HOME_COUNT);
                        continue;
                    }
                    if (first == null) {
                        first = mine;
                        continue;
                    }
                    for (int k = 0; k < mine.size(); k++) {
                        double[] p = first.get(k), q = mine.get(k);
                        if (Math.abs(p[1] - q[1]) > 1.5 || angleDiff(p[0], q[0]) * p[1] > 2 || p[2] != q[2]) {
                            problems.add(tag + "seat " + s.index() + "'s home patch " + k + " differs from seat 0's (bearing "
                                + Math.round(Math.toDegrees(q[0])) + " vs " + Math.round(Math.toDegrees(p[0])) + ", distance "
                                + Math.round(q[1]) + " vs " + Math.round(p[1]) + ", value " + q[2] + " vs " + p[2] + ")");
                            break;
                        }
                    }
                }
            }
        assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ",
            problems.subList(0, Math.min(10, problems.size()))));
    }

    /** The clumps change nothing about the promise: seats and teams within the tolerance, the map valid. */
    @Test
    void seats_stay_within_the_fairness_tolerance() {
        List<String> problems = new ArrayList<>();
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = layout(n, seed);
                String tag = tag(n, seed);
                if (!l.gen.valid)
                    problems.add(tag + "invalid: " + l.gen.invalidWhy);
                if (l.gen.report.spread() > GenOptions.DEFAULT_TOLERANCE + 1e-9)
                    problems.add(tag + String.format("seat spread %.1f%%", 100 * l.gen.report.spread()));
                if (l.gen.report.teamSpread() > GenOptions.DEFAULT_TOLERANCE + 1e-9)
                    problems.add(tag + String.format("team spread %.1f%%", 100 * l.gen.report.teamSpread()));
            }
        assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ", problems));
    }

    /**
     * Clumped or not, every patch is buildable (design/maps.md section 2, as MapGenGameTests checks in game): a level
     * 7x7 pad of dry land, no deep water within 7, the extractor clearance from the wall, outside every start's build
     * ground, out of the jungle, and never on another patch's pad.
     */
    @Test
    void every_patch_stands_on_buildable_ground() {
        List<String> problems = new ArrayList<>();
        MapLayout.Sample s = new MapLayout.Sample();
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = layout(n, seed);
                String tag = tag(n, seed);
                for (MapLayout.Mex m : l.mexes) {
                    String at = tag + "patch " + m.dx() + "," + m.dz() + " ";
                    l.sample(m.dx(), m.dz(), s);
                    int h0 = s.height;
                    pad:
                    for (int a = -MapLayout.PAD; a <= MapLayout.PAD; a++)
                        for (int b = -MapLayout.PAD; b <= MapLayout.PAD; b++) {
                            l.sample(m.dx() + a, m.dz() + b, s);
                            if (s.surface == MapLayout.Surface.WATER || s.surface == MapLayout.Surface.LAVA
                                    || s.surface == MapLayout.Surface.VOID || s.surface == MapLayout.Surface.CLIFF
                                    || Math.abs(s.height - h0) > 1) {
                                problems.add(at + "has " + s.surface + " / a drop in its pad");
                                break pad;
                            }
                        }
                    deep:
                    for (int a = -7; a <= 7; a++)
                        for (int b = -7; b <= 7; b++) {
                            l.sample(m.dx() + a, m.dz() + b, s);
                            if (s.surface == MapLayout.Surface.WATER && s.height < -1) {
                                problems.add(at + "has deep water within 7");
                                break deep;
                            }
                        }
                    if (l.distanceToArenaEdge(m.dx(), m.dz()) < ExtractorClearance.blocks())
                        problems.add(at + "is inside the extractor clearance from the wall");
                    for (MapLayout.Slot sl : l.slots)
                        if (Math.hypot(m.dx() - sl.dx(), m.dz() - sl.dz()) < GenEconomy.START_CLEAR)
                            problems.add(at + "is on seat " + sl.index() + "'s build ground");
                    for (GenMap.Jungle j : l.gen.jungle)
                        if (j.contains(m.dx(), m.dz()))
                            problems.add(at + "is in the jungle");
                    for (MapLayout.Mex o : l.mexes)
                        if (o != m && Math.abs(o.dx() - m.dx()) <= 2 * MapLayout.PAD && Math.abs(o.dz() - m.dz()) <= 2 * MapLayout.PAD)
                            problems.add(at + "overlaps the pad of " + o.dx() + "," + o.dz());
                }
            }
        assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ",
            problems.subList(0, Math.min(10, problems.size()))));
    }
}
