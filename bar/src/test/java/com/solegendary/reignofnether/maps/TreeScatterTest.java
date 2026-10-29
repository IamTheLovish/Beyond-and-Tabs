package com.solegendary.reignofnether.maps;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maps spec M6: our own tree templates on the pre-made maps (TreeScatter), planned without the world.
 *
 * lovish, Oct 29: "every players side doesnt need the same amount of trees that could look weird" - then, asked how
 * strict fairness should be, "Natural, roughly fair": every side's forest looks its own (its own places, species,
 * forms and turns - no mirrored copy of a neighbour's wedge), but the wood and the blocking per side stay within about
 * 15% of each other, because trees are felled for energy and stand in the way.
 */
class TreeScatterTest {

    private static final int[][] TEAMS = { { 0, 1 }, { 0, 0, 1, 1 }, { 0, 0, 0, 0, 1, 1, 1, 1 } };
    private static final long[] SEEDS = { 0L, 7L, 123456789L };

    private static List<MapPreset> treed() {
        List<MapPreset> out = new ArrayList<>();
        for (MapPreset p : MapPreset.values())
            if (p.premade() && TreeScatter.mixFor(p) != null)
                out.add(p);
        return out;
    }

    /** What MapBuilder offers TreeScatter: every forest column inside the wall whose slope is under 2. */
    static List<int[]> candidates(MapLayout l) {
        List<int[]> out = new ArrayList<>();
        MapLayout.Sample s = new MapLayout.Sample(), t = new MapLayout.Sample();
        int r = l.radius;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r)
                    continue;
                l.sample(dx, dz, s);
                if (!s.forest)
                    continue;
                int h = s.height, slope = 0;
                int[][] nb = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                for (int[] d : nb) {
                    l.sample(dx + d[0], dz + d[1], t);
                    slope = Math.max(slope, Math.abs(t.height - h));
                }
                if (slope < 2)
                    out.add(new int[] { dx, dz });
            }
        return out;
    }

    private static List<TreeScatter.Tree> plan(MapPreset p, int[] teams, long seed) {
        MapLayout l = MapLayout.build(p, teams, seed);
        return TreeScatter.plan(l, candidates(l));
    }

    // ---------------------------------------------------------------- which maps

    @Test
    void theOverworldMapsGrowTreesAndTheRealmsDoNot() {
        assertTrue(treed().contains(MapPreset.VERDANT_VALLEY));
        assertTrue(treed().contains(MapPreset.HIGHLANDS));
        assertTrue(TreeScatter.mixFor(MapPreset.NETHER_RIFT) == null, "the realms keep their fungi and chorus");
        assertTrue(TreeScatter.mixFor(MapPreset.END_SPIRES) == null);
        for (MapPreset p : treed()) {
            double sum = 0;
            for (TreeScatter.Share s : TreeScatter.mixFor(p))
                sum += s.share();
            assertEquals(1.0, sum, 1e-9, p + "'s species shares add up to 1");
        }
    }

    @Test
    void everyPresetWithForestGetsTrees() {
        for (MapPreset p : treed())
            for (int[] teams : TEAMS) {
                MapLayout l = MapLayout.build(p, teams, 7L);
                List<int[]> c = candidates(l);
                if (c.size() < 40 * teams.length)
                    continue;   // a map with almost no forest (Archipelago) may grow none
                assertFalse(TreeScatter.plan(l, c).isEmpty(), p + " " + teams.length + " slots grew no trees");
            }
    }

    // ---------------------------------------------------------------- determinism

    @Test
    void theSameSeedPlantsTheSameForest() {
        for (MapPreset p : treed())
            for (long seed : SEEDS)
                assertEquals(plan(p, TEAMS[1], seed), plan(p, TEAMS[1], seed), p + " seed " + seed);
    }

    @Test
    void aNewSeedPlantsANewForest() {
        MapPreset p = MapPreset.VERDANT_VALLEY;
        assertFalse(plan(p, TEAMS[1], 1L).equals(plan(p, TEAMS[1], 2L)));
    }

    // ---------------------------------------------------------------- natural, roughly fair

    @Test
    void noSideIsAMirroredCopyOfAnother() {
        for (MapPreset p : treed())
            for (int[] teams : TEAMS) {
                MapLayout l = MapLayout.build(p, teams, 7L);
                List<TreeScatter.Tree> trees = TreeScatter.plan(l, candidates(l));
                Map<Integer, Set<Long>> folded = new HashMap<>();
                MapLayout.Sample s = new MapLayout.Sample();
                for (TreeScatter.Tree t : trees) {
                    l.sample(t.dx(), t.dz(), s);
                    folded.computeIfAbsent(t.slot(), k -> new HashSet<>())
                        .add(((long) Math.round(s.u) << 32) ^ (Math.round(Math.abs(s.v)) & 0xffffffffL));
                }
                for (int a = 0; a < l.n; a++)
                    for (int b = a + 1; b < l.n; b++) {
                        Set<Long> A = folded.getOrDefault(a, Set.of()), B = folded.getOrDefault(b, Set.of());
                        if (A.size() < 15 || B.size() < 15)
                            continue;   // a few oasis palms can't help landing on the same spots
                        Set<Long> both = new HashSet<>(A);
                        both.retainAll(B);
                        assertTrue(both.size() < 0.5 * Math.min(A.size(), B.size()),
                            p + " " + teams.length + " slots: sides " + a + " and " + b + " share " + both.size()
                                + " of " + Math.min(A.size(), B.size()) + " tree spots");
                    }
            }
    }

    @Test
    void everySidesWoodAndBlockingStayWithinFifteenPercent() {
        for (MapPreset p : treed())
            for (int[] teams : TEAMS)
                for (long seed : SEEDS) {
                    MapLayout l = MapLayout.build(p, teams, seed);
                    List<TreeScatter.Tree> trees = TreeScatter.plan(l, candidates(l));
                    double[] wood = new double[l.n];
                    int[] count = new int[l.n];
                    for (TreeScatter.Tree t : trees) {
                        wood[t.slot()] += t.kind().wood;
                        count[t.slot()]++;
                    }
                    double meanW = 0, meanC = 0;
                    for (int i = 0; i < l.n; i++) {
                        meanW += wood[i] / l.n;
                        meanC += (double) count[i] / l.n;
                    }
                    if (meanC < 8)
                        continue;   // too few trees for a share to mean anything
                    for (int i = 0; i < l.n; i++) {
                        String at = p + " " + teams.length + " slots seed " + seed + " side " + i;
                        assertTrue(Math.abs(wood[i] - meanW) <= 0.15 * meanW + 4,
                            at + ": wood " + wood[i] + " against a mean " + meanW);
                        assertTrue(Math.abs(count[i] - meanC) <= 0.15 * meanC + 1,
                            at + ": trees " + count[i] + " against a mean " + meanC);
                    }
                }
    }

    @Test
    void sidesDifferALittleInHowMuchTheyGrow() {
        // roughly fair, not identical: across the maps, some sides end up with a different tree count
        int differing = 0;
        for (MapPreset p : treed())
            for (long seed : SEEDS) {
                MapLayout l = MapLayout.build(p, TEAMS[2], seed);
                int[] count = new int[l.n];
                for (TreeScatter.Tree t : TreeScatter.plan(l, candidates(l)))
                    count[t.slot()]++;
                for (int i = 1; i < l.n; i++)
                    if (count[i] != count[0])
                        differing++;
            }
        assertTrue(differing > 0, "every side grew exactly the same number of trees");
    }

    // ---------------------------------------------------------------- where a tree may stand

    @Test
    void treesStandOnlyOnOfferedColumnsAndNeverCrowdEachOther() {
        for (MapPreset p : treed()) {
            MapLayout l = MapLayout.build(p, TEAMS[1], 7L);
            List<int[]> c = candidates(l);
            Set<Long> offered = new HashSet<>();
            for (int[] k : c)
                offered.add(((long) k[0] << 32) ^ (k[1] & 0xffffffffL));
            List<TreeScatter.Tree> trees = TreeScatter.plan(l, c);
            for (int i = 0; i < trees.size(); i++) {
                TreeScatter.Tree a = trees.get(i);
                assertTrue(offered.contains(((long) a.dx() << 32) ^ (a.dz() & 0xffffffffL)), p + ": " + a + " was not offered");
                for (int j = i + 1; j < trees.size(); j++) {
                    TreeScatter.Tree b = trees.get(j);
                    assertTrue(Math.hypot(a.dx() - b.dx(), a.dz() - b.dz()) >= TreeScatter.SPACING,
                        p + ": " + a + " and " + b + " crowd each other");
                }
            }
        }
    }

    @Test
    void noCrownHidesTheEconomy() {
        for (MapPreset p : treed())
            for (int[] teams : TEAMS) {
                MapLayout l = MapLayout.build(p, teams, 123456789L);
                for (TreeScatter.Tree t : TreeScatter.plan(l, candidates(l))) {
                    int r = t.kind().crown;
                    for (MapLayout.Mex m : l.mexes)
                        assertTrue(Math.hypot(t.dx() - m.dx(), t.dz() - m.dz()) >= TreeScatter.MEX_CLEAR + r,
                            p + ": " + t + " over the metal patch at " + m.dx() + ", " + m.dz());
                    for (MapLayout.Site s : l.sites)
                        assertTrue(Math.hypot(t.dx() - s.dx(), t.dz() - s.dz()) >= TreeScatter.SITE_CLEAR + r,
                            p + ": " + t + " over the capture site at " + s.dx() + ", " + s.dz());
                    for (MapLayout.Spot s : l.vents)
                        assertTrue(Math.hypot(t.dx() - s.dx(), t.dz() - s.dz()) >= TreeScatter.SPOT_CLEAR + r, p + ": vent");
                    for (MapLayout.Spot s : l.pillars)
                        assertTrue(Math.hypot(t.dx() - s.dx(), t.dz() - s.dz()) >= TreeScatter.SPOT_CLEAR + r, p + ": pillar");
                    for (MapLayout.Slot s : l.slots)
                        assertTrue(Math.hypot(t.dx() - s.dx(), t.dz() - s.dz()) >= TreeScatter.START_CLEAR,
                            p + ": " + t + " on a start");
                }
            }
    }

    // ---------------------------------------------------------------- what grows

    @Test
    void theSpeciesFollowTheMapsMix() {
        for (MapPreset p : treed()) {
            Map<TreeScatter.Kind, Integer> n = new HashMap<>();
            int all = 0;
            for (long seed : SEEDS)
                for (TreeScatter.Tree t : plan(p, TEAMS[2], seed)) {
                    n.merge(t.kind(), 1, Integer::sum);
                    all++;
                }
            if (all < 100)
                continue;
            for (TreeScatter.Share s : TreeScatter.mixFor(p)) {
                double got = n.getOrDefault(s.kind(), 0) / (double) all;
                assertEquals(s.share(), got, 0.12, p + " " + s.kind() + " share");
            }
        }
    }

    @Test
    void formsAndTurnsAreVaried() {
        List<TreeScatter.Tree> trees = plan(MapPreset.VERDANT_VALLEY, TEAMS[2], 7L);
        Set<Integer> rot = new HashSet<>(), variants = new HashSet<>();
        Set<Boolean> mirror = new HashSet<>();
        for (TreeScatter.Tree t : trees) {
            rot.add(t.rotation());
            mirror.add(t.mirror());
            if (t.kind() == TreeScatter.Kind.OAK)
                variants.add(t.variant());
            assertTrue(t.variant() >= 1 && t.variant() <= t.kind().variants, "variant " + t.variant());
            assertTrue(t.rotation() >= 0 && t.rotation() < 4);
        }
        assertEquals(4, rot.size(), "all four turns");
        assertEquals(2, mirror.size(), "mirrored and not");
        assertTrue(variants.size() >= 3, "oak forms " + variants);
    }

    @Test
    void theTemplateNameIsTheFileUnderStructuresTrees() {
        TreeScatter.Tree t = new TreeScatter.Tree(0, 0, TreeScatter.Kind.OAK, 3, 0, false, 0);
        assertEquals("trees/oak_3", t.template());
    }

    // ---------------------------------------------------------------- turning a template

    @Test
    void aTurnMapsTheTemplateRoundItsTrunk() {
        // a 15 x 15 template turns about its centre (7, 7): the trunk stays put, a branch tip goes round
        int[] p = { 12, 3, 7 };   // 5 blocks east of the trunk, 3 up
        assertArrayEq(new int[] { 0, 3, 0 }, TreeScatter.offset(new int[] { 7, 3, 7 }, 15, 15, 1, false));
        assertArrayEq(new int[] { 5, 3, 0 }, TreeScatter.offset(p, 15, 15, 0, false));
        assertArrayEq(new int[] { 0, 3, 5 }, TreeScatter.offset(p, 15, 15, 1, false));    // clockwise from above: east -> south
        assertArrayEq(new int[] { -5, 3, 0 }, TreeScatter.offset(p, 15, 15, 2, false));
        assertArrayEq(new int[] { 0, 3, -5 }, TreeScatter.offset(p, 15, 15, 3, false));
        assertArrayEq(new int[] { -5, 3, 0 }, TreeScatter.offset(p, 15, 15, 0, true));    // mirrored: east <-> west
        assertArrayEq(new int[] { 2, 0, -3 }, TreeScatter.offset(new int[] { 6, 0, 1 }, 9, 9, 0, false));   // a 9 x 9 bush
    }

    private static void assertArrayEq(int[] want, int[] got) {
        assertEquals(java.util.Arrays.toString(want), java.util.Arrays.toString(got));
    }
}
