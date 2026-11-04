package com.solegendary.reignofnether.maps;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pre-made maps' meadow patches (concept C, lovish Nov 4 2026: "a low thick clover carpet everywhere ... with soft
 * patches of taller meadow grass and daisies; open clover keeps soldiers readable" and "more blending"): the patches
 * cover a quarter to a third of the open ground in patches 3-8 blocks across, their cover falls off over 1-2 blocks
 * with single tufts beyond, flowers sit in and round them (daisies most, then dandelions), tall grass keeps off the
 * ground units must read on, and the Python mirror the offline tools use grows exactly the same plants.
 */
class MeadowPatchesTest {

    private static final int[] TEAMS = { 0, 1 };
    private static final MeadowPatches.Mix VERDANT = MeadowPatches.mixFor(MapPreset.VERDANT_VALLEY);
    private static final int N = 240;
    private static final double U0 = 20.25, V0 = 10.5;

    private static boolean isFlower(int k) {
        return k >= MeadowPatches.POPPY;
    }

    private static boolean isTall(int k) {
        return k == MeadowPatches.TALL || k == MeadowPatches.LARGE_FERN;
    }

    /** The patch field and the plants on an N x N stretch of open ground (no readability zone near). */
    private record Plane(double[][] p, int[][] kind) { }

    private static Plane plane(MapPreset preset, long seed) {
        MapLayout l = MapLayout.build(preset, TEAMS, seed);
        MeadowPatches.Mix mix = MeadowPatches.mixFor(preset);
        double[][] p = new double[N][N];
        int[][] kind = new int[N][N];
        for (int i = 0; i < N; i++)
            for (int k = 0; k < N; k++) {
                p[i][k] = MeadowPatches.field(l, U0 + i, V0 + k);
                kind[i][k] = MeadowPatches.kindAt(mix, l, U0 + i, V0 + k, Double.POSITIVE_INFINITY);
            }
        return new Plane(p, kind);
    }

    /**
     * Signed distance in blocks (8-neighbour steps) from every block to the patch boundary (field above T): 1 for a
     * patch block next to open ground, 2 one further in...; -1 for an open block next to a patch, -2...
     */
    private static int[][] signedDistance(double[][] p) {
        int[][] d = new int[N][N];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int i = 0; i < N; i++)
            for (int k = 0; k < N; k++) {
                boolean in = p[i][k] > MeadowPatches.T;
                boolean edge = false;
                for (int a = -1; a <= 1 && !edge; a++)
                    for (int c = -1; c <= 1; c++) {
                        int ii = i + a, kk = k + c;
                        if (ii >= 0 && kk >= 0 && ii < N && kk < N && (p[ii][kk] > MeadowPatches.T) != in) {
                            edge = true;
                            break;
                        }
                    }
                if (edge) {
                    d[i][k] = in ? 1 : -1;
                    q.add(new int[] { i, k });
                }
            }
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int dd = d[c[0]][c[1]];
            for (int a = -1; a <= 1; a++)
                for (int b = -1; b <= 1; b++) {
                    int ii = c[0] + a, kk = c[1] + b;
                    if (ii < 0 || kk < 0 || ii >= N || kk >= N || d[ii][kk] != 0)
                        continue;
                    d[ii][kk] = dd > 0 ? dd + 1 : dd - 1;
                    q.add(new int[] { ii, kk });
                }
        }
        return d;
    }

    @Test
    void patchesCoverAQuarterToAThirdOfOpenGround() {
        for (long seed : new long[] { 0L, 42L }) {
            Plane pl = plane(MapPreset.VERDANT_VALLEY, seed);
            int in = 0, planted = 0;
            for (double[] row : pl.p())
                for (double p : row)
                    in += p > MeadowPatches.T ? 1 : 0;
            for (int[] row : pl.kind())
                for (int k : row)
                    planted += k != MeadowPatches.NONE ? 1 : 0;
            double share = in / (double) (N * N);
            System.out.printf("seed %d: patches %.1f%% of open ground, plants on %.1f%%%n", seed, 100 * share,
                100.0 * planted / (N * N));
            assertTrue(share >= 0.25 && share <= 0.35, "seed " + seed + ": patches cover " + share);
        }
    }

    @Test
    void patchesAreThreeToEightBlocksAcross() {
        Plane pl = plane(MapPreset.VERDANT_VALLEY, 0L);
        int[][] d = signedDistance(pl.p());
        // each patch's width: twice its deepest block's depth less one (a patch 1 deep is 1 across, 3 deep is 5)
        boolean[][] seen = new boolean[N][N];
        List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < N; i++)
            for (int k = 0; k < N; k++) {
                if (seen[i][k] || d[i][k] <= 0)
                    continue;
                int deepest = 0, blocks = 0;
                boolean touchesBorder = false;
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[] { i, k });
                seen[i][k] = true;
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    blocks++;
                    deepest = Math.max(deepest, d[c[0]][c[1]]);
                    touchesBorder |= c[0] == 0 || c[1] == 0 || c[0] == N - 1 || c[1] == N - 1;
                    for (int[] o : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                        int ii = c[0] + o[0], kk = c[1] + o[1];
                        if (ii >= 0 && kk >= 0 && ii < N && kk < N && !seen[ii][kk] && d[ii][kk] > 0) {
                            seen[ii][kk] = true;
                            q.add(new int[] { ii, kk });
                        }
                    }
                }
                if (blocks >= 6 && !touchesBorder)
                    widths.add(2 * deepest - 1);
            }
        int[] w = widths.stream().mapToInt(Integer::intValue).sorted().toArray();
        int median = w[w.length / 2], p90 = w[(int) (w.length * 0.9)];
        long inRange = Arrays.stream(w).filter(x -> x >= 3 && x <= 9).count();
        System.out.printf("%d patches: width median %d, p10 %d, p90 %d; %.0f%% are 3-9 across%n", w.length, median,
            w[w.length / 10], p90, 100.0 * inRange / w.length);
        assertTrue(median >= 3 && median <= 8, "median patch width " + median);
        assertTrue(p90 <= 11, "one patch in ten is wider than " + p90 + " - patches merge into fields");
        assertTrue(inRange >= 0.7 * w.length, "only " + inRange + " of " + w.length + " patches are 3-9 across");
    }

    @Test
    void theEdgeIsSoftAndSingleTuftsGrowBeyondIt() {
        Plane pl = plane(MapPreset.VERDANT_VALLEY, 0L);
        int[][] d = signedDistance(pl.p());
        Map<Integer, int[]> byDist = new TreeMap<>();   // distance -> {blocks, grass plants (not flowers), tall}
        for (int i = 0; i < N; i++)
            for (int k = 0; k < N; k++) {
                int dd = Math.max(-5, Math.min(4, d[i][k]));
                int[] c = byDist.computeIfAbsent(dd, x -> new int[3]);
                int kind = pl.kind()[i][k];
                c[0]++;
                c[1] += kind != MeadowPatches.NONE && !isFlower(kind) ? 1 : 0;
                c[2] += isTall(kind) ? 1 : 0;
            }
        double[] cover = new double[10], tall = new double[10];
        for (Map.Entry<Integer, int[]> e : byDist.entrySet()) {
            int[] c = e.getValue();
            cover[e.getKey() + 5] = c[1] / (double) c[0];
            tall[e.getKey() + 5] = c[2] / (double) c[0];
            System.out.printf("distance %+d: %5d blocks, grass cover %.2f, tall %.2f%n", e.getKey(), c[0],
                cover[e.getKey() + 5], tall[e.getKey() + 5]);
        }
        // index = distance + 5: 9 = 4+ blocks in, 6 = first block in, 4 = first block out, 0 = 5+ out
        assertTrue(cover[8] >= 0.75, "a patch's middle is not thick: " + cover[8]);
        assertTrue(cover[6] > 0.3 && cover[6] < cover[8] - 0.08, "no fall-off inside the edge: " + cover[6]);
        assertTrue(cover[4] > 0.05 && cover[4] < cover[6], "no soft outer edge: " + cover[4]);
        assertTrue(cover[3] > 0.01 && cover[3] < cover[4], "no single tufts beyond the edge: " + cover[3]);
        assertTrue(cover[0] < 0.03 && cover[1] < 0.03, "open clover is not open: " + cover[0] + " / " + cover[1]);
        // and past the fringe (the field below T - E - H) the clover carries no grass at all
        for (int i = 0; i < N; i++)
            for (int k = 0; k < N; k++)
                if (pl.p()[i][k] <= MeadowPatches.T - MeadowPatches.E - MeadowPatches.H)
                    assertTrue(pl.kind()[i][k] == MeadowPatches.NONE || isFlower(pl.kind()[i][k]), "grass on open clover");
        assertTrue(tall[8] >= 0.35, "a patch's middle is not tall meadow grass: " + tall[8]);
        assertTrue(tall[6] < tall[8], "tall grass runs right out to the edge");
        assertTrue(tall[4] < 0.03 && tall[3] < 0.001, "tall grass outside the patches: " + tall[4] + " / " + tall[3]);
    }

    @Test
    void flowersClusterInAndRoundThePatchesDaisiesFirst() {
        Plane pl = plane(MapPreset.VERDANT_VALLEY, 0L);
        int flowers = 0, near = 0;
        int[] byKind = new int[10];
        for (int i = 0; i < N; i++)
            for (int k = 0; k < N; k++) {
                int kind = pl.kind()[i][k];
                if (!isFlower(kind))
                    continue;
                flowers++;
                byKind[kind]++;
                double p = pl.p()[i][k];
                near += MeadowPatches.strength(p) > 0 || MeadowPatches.fringe(p) > 0 ? 1 : 0;
            }
        System.out.printf("%d flowers (%.1f%% of blocks), %.0f%% in or round a patch; daisy %d dandelion %d bluet %d "
                + "poppy %d cornflower %d%n", flowers, 100.0 * flowers / (N * N), 100.0 * near / flowers,
            byKind[MeadowPatches.DAISY], byKind[MeadowPatches.DANDELION], byKind[MeadowPatches.BLUET],
            byKind[MeadowPatches.POPPY], byKind[MeadowPatches.CORNFLOWER]);
        assertTrue(near >= 0.85 * flowers, "flowers are strewn over the open clover: " + near + " of " + flowers);
        assertTrue(flowers > 0.015 * N * N && flowers < 0.06 * N * N, "flower share " + flowers);
        int daisy = byKind[MeadowPatches.DAISY], dandelion = byKind[MeadowPatches.DANDELION];
        assertTrue(daisy > dandelion, "daisies " + daisy + " not the most");
        for (int k = MeadowPatches.POPPY; k <= MeadowPatches.ALLIUM; k++)
            if (k != MeadowPatches.DAISY && k != MeadowPatches.DANDELION)
                assertTrue(byKind[k] < dandelion / 2, "too many of flower " + k);
    }

    @Test
    void tallGrassKeepsOffTheGroundUnitsMustReadOn() {
        for (MapPreset preset : new MapPreset[] { MapPreset.VERDANT_VALLEY, MapPreset.HIGHLANDS,
                MapPreset.ARCHIPELAGO, MapPreset.DESERT_MESA }) {
            MapLayout l = MapLayout.build(preset, TEAMS, 7L);
            MeadowPatches.Mix mix = MeadowPatches.mixFor(preset);
            List<int[]> keep = MeadowPatches.keepOuts(l);
            MapLayout.Sample s = new MapLayout.Sample();
            int tallTotal = 0, near = 0;
            for (int dx = -l.radius; dx <= l.radius; dx++)
                for (int dz = -l.radius; dz <= l.radius; dz++) {
                    if (dx * dx + dz * dz > l.radius * l.radius)
                        continue;
                    l.sample(dx, dz, s);
                    double clear = MeadowPatches.clearance(l, s.u, s.v, dx, dz, keep);
                    int kind = MeadowPatches.kindAt(mix, l, s.u, s.v, clear);
                    if (!isTall(kind))
                        continue;
                    tallTotal++;
                    assertTrue(clear > MeadowPatches.TALL_CLEAR, preset + ": tall grass at (" + dx + ", " + dz
                        + "), " + clear + " blocks from a start, lane, site, patch, vent or pillar");
                    near += clear < MeadowPatches.TALL_CLEAR + MeadowPatches.TALL_RAMP ? 1 : 0;
                }
            System.out.printf("%s: %d tall plants, %d within the ramp%n", preset, tallTotal, near);
            assertTrue(tallTotal > 1000, preset + ": hardly any tall grass (" + tallTotal + ")");
        }
    }

    @Test
    void theClearanceSeesEveryZone() {
        MapLayout l = MapLayout.build(MapPreset.VERDANT_VALLEY, TEAMS, 0L);
        List<int[]> keep = MeadowPatches.keepOuts(l);
        assertTrue(MeadowPatches.clearance(l, l.ring, 0, 0, 0, List.of()) < -40, "the start is not a zone");
        assertTrue(MeadowPatches.clearance(l, l.ring / 2.0, 3, 0, 0, List.of()) < 0, "the start's lane is not a zone");
        assertTrue(MeadowPatches.clearance(l, l.ring / 2.0, 30, 0, 0, List.of()) > 20, "open ground is a zone");
        int[] site = keep.get(keep.size() - 1);
        assertTrue(MeadowPatches.clearance(l, -500, 500, site[0] + 1, site[1], keep) < 0, "a keep-out is not a zone");
        assertTrue(MeadowPatches.keptClear(keep, site[0] + site[2], site[1]));
        assertTrue(!MeadowPatches.keptClear(keep, site[0] + site[2] + 1 + 1000, site[1]));
    }

    @Test
    void theCoverIsAFunctionOfTheSeedAndThePosition() {
        Plane a = plane(MapPreset.VERDANT_VALLEY, 42L), b = plane(MapPreset.VERDANT_VALLEY, 42L);
        Plane c = plane(MapPreset.VERDANT_VALLEY, 43L);
        int differ = 0;
        for (int i = 0; i < N; i++) {
            assertTrue(Arrays.equals(a.kind()[i], b.kind()[i]), "the same seed grew different plants");
            for (int k = 0; k < N; k++)
                differ += a.kind()[i][k] != c.kind()[i][k] ? 1 : 0;
        }
        assertTrue(differ > 0.2 * N * N, "seed 43 grows nearly seed 42's meadow (" + differ + " differ)");
    }

    @Test
    void everyGreenMapHasAMixAndTheOthersNone() {
        for (MapPreset p : MapPreset.values()) {
            boolean green = p == MapPreset.VERDANT_VALLEY || p == MapPreset.HIGHLANDS || p == MapPreset.ARCHIPELAGO
                || p == MapPreset.DESERT_MESA;
            if (green)
                assertNotNull(MeadowPatches.mixFor(p), p.name());
            else
                assertNull(MeadowPatches.mixFor(p), p.name());
        }
        int daisy = 0, dandelion = 0;
        for (int k : VERDANT.flowers()) {
            daisy += k == MeadowPatches.DAISY ? 1 : 0;
            dandelion += k == MeadowPatches.DANDELION ? 1 : 0;
        }
        assertTrue(daisy > dandelion && dandelion > VERDANT.flowers().length - daisy - dandelion);
    }

    /**
     * tools/meadow_patches.py (the offline sampler's and the patch map's rule) grows exactly these plants:
     * tools/test_meadow_patches.py checks its side against the same file. After changing the rule, change the mirror
     * too and rewrite the file with {@code python3 tools/meadow_patches.py --golden}.
     */
    @Test
    void thePythonMirrorGrowsTheSamePlants() throws Exception {
        InputStream in = MeadowPatchesTest.class.getResourceAsStream("/maps/meadow_patch_golden.txt");
        assertNotNull(in, "maps/meadow_patch_golden.txt is missing from the test resources");
        String chars = ".gTfFpydbca";
        MapPreset[] presets = MapPreset.values();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int windows = 0;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("#"))
                    continue;
                String[] h = line.substring(2).trim().split(" ");
                MapPreset preset = presets[Integer.parseInt(h[0])];
                MapLayout l = MapLayout.build(preset, TEAMS, Long.parseLong(h[1]));
                MeadowPatches.Mix mix = MeadowPatches.mixFor(preset);
                int u0 = Integer.parseInt(h[2]), v0 = Integer.parseInt(h[3]), size = Integer.parseInt(h[4]);
                for (int k = 0; k < size; k++) {
                    String row = r.readLine();
                    StringBuilder got = new StringBuilder();
                    for (int i = 0; i < size; i++)
                        got.append(chars.charAt(MeadowPatches.kindAt(mix, l, u0 + i + 0.25, v0 + k + 0.5,
                            Double.POSITIVE_INFINITY) + 1));
                    assertEquals(row, got.toString(), preset + " seed " + h[1] + " row " + k);
                }
                windows++;
            }
            assertEquals(2, windows);
        }
    }
}
