package com.solegendary.reignofnether.maps;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "More grass" on the maps (lovish, Oct 28: from the RTS camera the grass reads as one bland colour and the plant
 * cover is sparse). Written before the change, from his words: the share of grass-topped ground that carries a plant
 * rises to 1.5-2.3x what it was (the fractions below are this test's own reading of the cover as it stood on Oct 28, before the change), open ground is
 * never completely bare, tall grass and ferns stay in a meadow's middle so units stay readable, the keep-outs hold and
 * the cover is the same in every wedge. The arithmetic is PlantCover's (no world needed); the columns are the real
 * terrain of the real layouts.
 */
class PlantCoverTest {

    /** The share of eligible columns that carried a plant before the change, averaged over the seeds and team sizes. */
    private static final double BEFORE_VERDANT = 0.365, BEFORE_HIGHLANDS = 0.258, BEFORE_ARCHIPELAGO = 0.250;
    private static final long[] SEEDS = { 42L, 4242424242L };
    private static final int[][] TEAMS = { { 0, 1 }, { 0, 0, 1, 1 } };

    /** A column the builder would plant on: grass-topped, flat enough (slope < 2), not forest, not in a keep-out. */
    private record Col(MapLayout layout, double u, double v, int slope, double meadow) { }

    private static final java.util.Map<String, List<Col>> COLS = new java.util.HashMap<>();

    private static List<Col> columns(MapPreset preset) {
        return COLS.computeIfAbsent(preset.name(), k -> {
            List<Col> out = new ArrayList<>();
            for (int[] teams : TEAMS)
                for (long seed : SEEDS)
                    collect(MapLayout.build(preset, teams, seed), out);
            return out;
        });
    }

    private static void collect(MapLayout l, List<Col> out) {
        int step = 3, r = l.radius + 3;
        List<int[]> keep = PlantCover.keepOuts(l);
        MapLayout.Sample c = new MapLayout.Sample(), s = new MapLayout.Sample();
        for (int dx = -r; dx <= r; dx += step)
            for (int dz = -r; dz <= r; dz += step) {
                if (dx * dx + dz * dz > r * r)
                    continue;
                l.sample(dx, dz, c);
                if (c.forest || !(c.surface == MapLayout.Surface.GRASS || c.surface == MapLayout.Surface.LAND))
                    continue;
                int slope = 0;
                int[][] nb = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                for (int[] d : nb) {
                    l.sample(dx + d[0], dz + d[1], s);
                    slope = Math.max(slope, Math.abs(s.height - c.height));
                }
                if (slope >= 2 || PlantCover.keptClear(keep, dx, dz))
                    continue;
                out.add(new Col(l, c.u, c.v, slope, PlantCover.meadowNoise(l, c.u, c.v)));
            }
    }

    private static MapLayout anyLayout() {
        return MapLayout.build(MapPreset.VERDANT_VALLEY, new int[] { 0, 1 }, 42L);
    }

    private static double coverage(MapPreset preset) {
        PlantCover.Mix mix = PlantCover.mixFor(preset);
        List<Col> cols = columns(preset);
        int planted = 0;
        for (Col c : cols)
            if (PlantCover.kindAt(mix, c.layout, c.u, c.v, c.slope) >= 0)
                planted++;
        assertTrue(cols.size() > 5000, preset + ": too few columns to judge (" + cols.size() + ")");
        return planted / (double) cols.size();
    }

    // ---------------------------------------------------------------- more grass

    private static void assertDenser(MapPreset preset, double before) {
        double now = coverage(preset);
        assertTrue(now >= before * 1.5, preset + ": plant cover " + now + " is under 1.5x the " + before + " it was");
        assertTrue(now <= before * 2.3 && now <= 0.8, preset + ": plant cover " + now + " is past 2.3x the " + before
            + " it was (a cost and a readability risk)");
    }

    @Test
    void verdantValleyGrowsAtLeastOneAndAHalfTimesTheCover() {
        assertDenser(MapPreset.VERDANT_VALLEY, BEFORE_VERDANT);
    }

    @Test
    void highlandsGrowsAtLeastOneAndAHalfTimesTheCover() {
        assertDenser(MapPreset.HIGHLANDS, BEFORE_HIGHLANDS);
    }

    @Test
    void archipelagoGrowsAtLeastOneAndAHalfTimesTheCover() {
        assertDenser(MapPreset.ARCHIPELAGO, BEFORE_ARCHIPELAGO);
    }

    // ---------------------------------------------------------------- open ground is never bare

    /** (u, v) points in the open, where no meadow reaches: the meadow noise under 0.2. */
    private static List<double[]> openGround(MapLayout l) {
        List<double[]> out = new ArrayList<>();
        for (int u = 0; u < 300; u++)
            for (int v = 0; v < 150; v++)
                if (PlantCover.meadowNoise(l, u, v) < 0.2)
                    out.add(new double[] { u, v });
        assertTrue(out.size() > 500, "too few open-ground points to judge: " + out.size());
        return out;
    }

    private static double openShare(MapPreset preset, int slope) {
        MapLayout l = anyLayout();
        PlantCover.Mix mix = PlantCover.mixFor(preset);
        List<double[]> pts = openGround(l);
        int n = 0;
        for (double[] p : pts)
            if (PlantCover.kindAt(mix, l, p[0], p[1], slope) == PlantCover.GRASS)
                n++;
        return n / (double) pts.size();
    }

    @Test
    void openGroundGetsALowScatterOfShortGrass() {
        for (MapPreset p : new MapPreset[] { MapPreset.VERDANT_VALLEY, MapPreset.HIGHLANDS, MapPreset.ARCHIPELAGO }) {
            double share = openShare(p, 0);
            assertTrue(share >= 0.07 && share <= 0.16, p + ": open ground carries " + share + " short grass, want 0.07-0.16");
        }
    }

    @Test
    void slopesGetLessScatterThanFlatGround() {
        for (MapPreset p : new MapPreset[] { MapPreset.VERDANT_VALLEY, MapPreset.HIGHLANDS, MapPreset.ARCHIPELAGO }) {
            double flat = openShare(p, 0), slope = openShare(p, 1);
            assertTrue(slope < flat * 0.75, p + ": slope 1 carries " + slope + " against " + flat + " on the flat");
            assertTrue(slope > 0, p + ": a gentle slope should still get some scatter");
        }
    }

    @Test
    void theFringeOfAMeadowIsDenserThanOpenGroundAndThinnerThanItsMiddle() {
        MapLayout l = anyLayout();
        PlantCover.Mix mix = PlantCover.mixFor(MapPreset.VERDANT_VALLEY);
        int[] n = new int[3], planted = new int[3];
        for (int u = 0; u < 300; u++)
            for (int v = 0; v < 150; v++) {
                double m = PlantCover.meadowNoise(l, u, v);
                int band = m < 0.2 ? 0 : m < 0.38 ? 1 : m > 0.6 ? 2 : -1;
                if (band < 0)
                    continue;
                n[band]++;
                if (PlantCover.kindAt(mix, l, u, v, 0) >= 0)
                    planted[band]++;
            }
        double open = planted[0] / (double) n[0], fringe = planted[1] / (double) n[1], core = planted[2] / (double) n[2];
        assertTrue(open < fringe && fringe < core, "open " + open + ", fringe " + fringe + ", middle " + core);
        assertTrue(core >= 0.6, "a meadow's middle should be mostly covered, got " + core);
    }

    // ---------------------------------------------------------------- tall plants stay in the middle

    @Test
    void tallGrassAndFernsStayInMeadowCoresAndStayRare() {
        for (MapPreset preset : new MapPreset[] { MapPreset.VERDANT_VALLEY, MapPreset.HIGHLANDS, MapPreset.ARCHIPELAGO }) {
            PlantCover.Mix mix = PlantCover.mixFor(preset);
            List<Col> cols = columns(preset);
            int tall = 0, planted = 0;
            for (Col c : cols) {
                int k = PlantCover.kindAt(mix, c.layout, c.u, c.v, c.slope);
                if (k < 0)
                    continue;
                planted++;
                boolean twoHigh = k == PlantCover.TALL || k == PlantCover.LARGE_FERN;
                if (twoHigh) {
                    tall++;
                    assertTrue(c.meadow > 0.68, preset + ": a two-block plant outside a meadow's middle (noise " + c.meadow + ")");
                }
                if (k == PlantCover.FERN || k == PlantCover.LARGE_FERN)
                    assertTrue(c.meadow > 0.55, preset + ": a fern outside a meadow's middle (noise " + c.meadow + ")");
            }
            assertTrue(tall / (double) cols.size() <= 0.012, preset + ": two-block plants on " + tall + " of " + cols.size() + " columns");
            assertTrue(tall / (double) planted <= 0.04, preset + ": two-block plants are " + tall + " of " + planted + " plants");
        }
    }

    // ---------------------------------------------------------------- the same in every wedge

    @Test
    void theCoverIsTheSameInEveryWedge() {
        MapLayout l = MapLayout.build(MapPreset.VERDANT_VALLEY, new int[] { 0, 0, 1, 1 }, 42L);
        PlantCover.Mix mix = PlantCover.mixFor(MapPreset.VERDANT_VALLEY);
        double turn = Math.PI * 2 / l.n;
        MapLayout.Sample a = new MapLayout.Sample(), b = new MapLayout.Sample();
        int compared = 0, planted = 0, tried = 0;
        for (int dx = -200; dx <= 200; dx += 7)
            for (int dz = -200; dz <= 200; dz += 7) {
                tried++;
                double rx = dx * Math.cos(turn) - dz * Math.sin(turn), rz = dx * Math.sin(turn) + dz * Math.cos(turn);
                l.sample(dx, dz, a);
                l.sample(rx, rz, b);
                if (Math.abs(a.u - b.u) > 1e-6 || Math.abs(a.v - b.v) > 1e-6)
                    continue;   // a point on a wedge boundary folds either way; nothing to compare
                compared++;
                int ka = PlantCover.kindAt(mix, l, a.u, a.v, 0), kb = PlantCover.kindAt(mix, l, b.u, b.v, 0);
                assertEquals(ka, kb, "the cover at (" + dx + ", " + dz + ") and its image one wedge on");
                if (ka >= 0)
                    planted++;
            }
        assertTrue(compared > tried * 0.9, "only " + compared + " of " + tried + " points folded to the same (u, v)");
        assertTrue(planted > 100, "the check should meet plants, met " + planted);
    }

    // ---------------------------------------------------------------- keep-outs and the oases

    @Test
    void everyEconomySiteKeepsItsSquareClear() {
        for (MapPreset preset : new MapPreset[] { MapPreset.VERDANT_VALLEY, MapPreset.HIGHLANDS, MapPreset.ARCHIPELAGO }) {
            MapLayout l = MapLayout.build(preset, new int[] { 0, 1 }, 42L);
            List<int[]> keep = PlantCover.keepOuts(l);
            assertEquals(l.mexes.size() + l.sites.size() + l.vents.size() + l.pillars.size(), keep.size());
            for (MapLayout.Mex m : l.mexes) {
                assertTrue(PlantCover.keptClear(keep, m.dx(), m.dz()), preset + ": a patch pad centre");
                assertTrue(PlantCover.keptClear(keep, m.dx() + 4, m.dz() - 4), preset + ": the pad square's corner");
            }
            for (MapLayout.Site s : l.sites) {
                assertTrue(PlantCover.keptClear(keep, s.dx() - 6, s.dz() + 6), preset + ": a capture site's corner");
            }
            for (MapLayout.Spot v : l.vents)
                assertTrue(PlantCover.keptClear(keep, v.dx() + 3, v.dz()), preset + ": a vent");
            for (MapLayout.Spot v : l.pillars)
                assertTrue(PlantCover.keptClear(keep, v.dx(), v.dz() - 3), preset + ": a pillar");
        }
    }

    @Test
    void desertOasesKeepTheirCoverAndGetNoScatter() {
        MapLayout l = anyLayout();
        PlantCover.Mix mix = PlantCover.mixFor(MapPreset.DESERT_MESA);
        assertNotNull(mix);
        int[] n = new int[2], planted = new int[2];
        for (int u = 0; u < 300; u++)
            for (int v = 0; v < 150; v++) {
                double m = PlantCover.meadowNoise(l, u, v);
                int band = m < 0.2 ? 0 : m > 0.5 ? 1 : -1;
                if (band < 0)
                    continue;
                n[band]++;
                if (PlantCover.kindAt(mix, l, u, v, 0) >= 0)
                    planted[band]++;
            }
        assertTrue(planted[0] / (double) n[0] < 0.02, "the desert's open sand stays bare, got " + planted[0] / (double) n[0]);
        // an oasis's middle was ~0.45 covered on Oct 28; proportional tweaks only
        double core = planted[1] / (double) n[1];
        assertTrue(core > 0.3 && core < 0.6, "an oasis's cover moved from about 0.4: " + core);
    }

    @Test
    void realmMapsHaveNoMeadow() {
        assertNull(PlantCover.mixFor(MapPreset.NETHER_RIFT));
        assertNull(PlantCover.mixFor(MapPreset.END_SPIRES));
        assertNull(PlantCover.mixFor(MapPreset.SOULFIRE_DELTAS));
        assertNull(PlantCover.mixFor(MapPreset.CHORUS_ISLES));
        assertNull(PlantCover.mixFor(MapPreset.RANDOM));
    }
}
