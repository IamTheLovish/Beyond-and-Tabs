package com.solegendary.reignofnether.ambient.mist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the valley mist lies (ValleyField), the design lovish approved on Oct 29 18:05 ET from a paintover of the
 * Verdant Valley RTS still (mist_now_vs_concept.png): the mist gathers on the river and the low banks beside it and in
 * hollows, and leaves the rest of the lawn clear - no film over the whole map (r10b's veil). And the mist is measured
 * from a smoothed ground, so its edge no longer draws every one-block terrace step. Written before ValleyField.
 */
class ValleyFieldTest {

    private static final int N = 64, STEP = 2;

    private static int[][] flat(int y) {
        int[][] h = new int[N][N];
        for (int[] row : h)
            java.util.Arrays.fill(row, y);
        return h;
    }

    /** A straight river 3 cells wide along z at x = 30..32, water surface one block under the banks. */
    private static boolean[][] river(int[][] h, int bank) {
        boolean[][] w = new boolean[N][N];
        for (int i = 30; i <= 32; i++)
            for (int j = 0; j < N; j++) {
                w[i][j] = true;
                h[i][j] = bank - 1;
            }
        return w;
    }

    @Test
    void theRiverItselfIsFullyCovered() {
        int[][] h = flat(64);
        boolean[][] w = river(h, 64);
        ValleyField.Result r = ValleyField.compute(h, w, STEP);
        for (int j = 0; j < N; j++)
            assertEquals(1.0, r.cover[31][j], 1e-9, "mid-river at z cell " + j);
    }

    @Test
    void theBanksFadeOverAboutSevenBlocks() {
        int[][] h = flat(64);
        boolean[][] w = river(h, 64);
        ValleyField.Result r = ValleyField.compute(h, w, STEP);
        int j = N / 2;
        double at2 = r.cover[33][j], at6 = r.cover[35][j], at14 = r.cover[39][j], at30 = r.cover[47][j];
        assertTrue(at2 > at6 && at6 > at14, "fades away from the water: " + at2 + " " + at6 + " " + at14);
        assertTrue(at2 >= 0.7, "2 blocks from the water the bank is still in the mist: " + at2);
        assertTrue(at6 >= 0.3 && at6 <= 0.6, "6 blocks out about half: " + at6);
        assertTrue(at30 <= 0.03, "30 blocks out the lawn is clear: " + at30);
        // both banks alike
        assertEquals(r.cover[33][j], r.cover[29][j], 0.02);
    }

    @Test
    void openGroundAwayFromWaterIsClear() {
        // the concept's "the rest of the lawn clear": flat or gently rolling dry ground gets no mist at all
        int[][] h = flat(70);
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                h[i][j] += (int) Math.round(1.5 * Math.sin(i * 0.2) * Math.cos(j * 0.15));   // swells of +-1 block
        ValleyField.Result r = ValleyField.compute(h, new boolean[N][N], STEP);
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                assertTrue(r.cover[i][j] <= 0.1, "open ground at " + i + ", " + j + ": " + r.cover[i][j]);
    }

    @Test
    void aHollowHoldsMistEvenAwayFromWater() {
        int[][] h = flat(70);
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) {
                double d = Math.hypot(i - 20, j - 20);
                if (d < 5) h[i][j] = 66;                          // a bowl 4 blocks deep, 10 cells across
            }
        ValleyField.Result r = ValleyField.compute(h, new boolean[N][N], STEP);
        assertTrue(r.cover[20][20] >= 0.5, "the bowl's floor: " + r.cover[20][20]);
        assertTrue(r.cover[50][50] <= 0.05, "the plain beside it: " + r.cover[50][50]);
    }

    @Test
    void aRidgeStaysClearEvenBesideWater() {
        // a bank that rises 8 blocks in 3 cells: the top of the ridge is out of the mist though the water is close
        int[][] h = flat(64);
        boolean[][] w = river(h, 64);
        for (int i = 34; i < N; i++)
            for (int j = 0; j < N; j++)
                h[i][j] = 64 + Math.min(8, 3 * (i - 33));
        ValleyField.Result r = ValleyField.compute(h, w, STEP);
        int j = N / 2;
        assertTrue(r.cover[37][j] < r.cover[29][j] * 0.5, "the ridge 8 blocks up vs the flat bank across: "
            + r.cover[37][j] + " vs " + r.cover[29][j]);
    }

    @Test
    void theSmoothedGroundHasNoBlockSteps() {
        // terraces: one block up every 3 cells (6 blocks) - the raw ground jumps a whole block at each step
        int[][] h = new int[N][N];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                h[i][j] = 60 + i / 3;
        ValleyField.Result r = ValleyField.compute(h, new boolean[N][N], STEP);
        for (int i = 4; i < N - 5; i++) {
            double d = r.smooth[i + 1][N / 2] - r.smooth[i][N / 2];
            assertTrue(d >= 0 && d <= 0.6, "smoothed step at cell " + i + ": " + d);
            double trend = 60 + (i - 1) / 3.0;
            assertTrue(Math.abs(r.smooth[i][N / 2] - trend) <= 0.7, "follows the slope at " + i + ": " + r.smooth[i][N / 2]);
        }
    }

    @Test
    void theTextureCarriesHeightToAnEighthOfABlock() {
        int base = 64;
        for (double y = base - 8; y <= base + 23.8; y += 0.37) {
            int b = ValleyField.encodeHeight(y, base);
            assertTrue(b >= 0 && b <= 255);
            assertEquals(y, ValleyField.decodeHeight(b, base), 0.07, "y " + y);
        }
        assertEquals(0, ValleyField.encodeHeight(base - 30, base), "below the range clamps");
        assertEquals(255, ValleyField.encodeHeight(base + 90, base), "above the range clamps");
    }
}
