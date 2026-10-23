package com.solegendary.reignofnether.postfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grass tint noise (lovish Oct 22 20:23, "yes this sounds good"): a low-frequency world-position noise on the grass
 * and foliage colour so a big grass field is not one flat green from the RTS camera. Subtle (a few % in value and
 * hue), deterministic, continuous across block borders, and it really varies across a field. Written before the
 * implementation (rule of Oct 22 19:58).
 */
class GrassTintTest {

    private static final int PLAINS_GRASS = 0x91BD59;   // vanilla plains grass colour

    private static int r(int c) { return (c >> 16) & 0xFF; }
    private static int g(int c) { return (c >> 8) & 0xFF; }
    private static int b(int c) { return c & 0xFF; }

    private static double luma(int c) { return 0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c); }

    @Test
    void sameBlockSameColourEveryCall() {
        for (int x = -300; x < 300; x += 37) {
            for (int z = -300; z < 300; z += 41) {
                assertEquals(GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS),
                        GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS), "x " + x + " z " + z);
                assertEquals(GrassTint.field(x, z, 7), GrassTint.field(x, z, 7), 0.0);
            }
        }
    }

    @Test
    void fieldStaysWithinMinusOneToOne() {
        for (int x = -2000; x < 2000; x += 3) {
            for (int z = -2000; z < 2000; z += 29) {
                double f = GrassTint.field(x, z, 1);
                assertTrue(f >= -1.0 && f <= 1.0, "field " + f + " at " + x + ", " + z);
            }
        }
    }

    @Test
    void colourChangesOnlyAFewPercent() {
        double lumaBase = luma(PLAINS_GRASS);
        for (int x = -1000; x < 1000; x += 7) {
            for (int z = -1000; z < 1000; z += 11) {
                int c = GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS);
                assertTrue(Math.abs(luma(c) - lumaBase) <= 0.06 * lumaBase, "luma at " + x + ", " + z);
                assertTrue(Math.abs(r(c) - r(PLAINS_GRASS)) <= 0.10 * r(PLAINS_GRASS) + 1, "red at " + x + ", " + z);
                assertTrue(Math.abs(g(c) - g(PLAINS_GRASS)) <= 0.10 * g(PLAINS_GRASS) + 1, "green at " + x + ", " + z);
                assertTrue(Math.abs(b(c) - b(PLAINS_GRASS)) <= 0.10 * b(PLAINS_GRASS) + 1, "blue at " + x + ", " + z);
                assertEquals(0, c & 0xFF000000, "no bits above the rgb");
            }
        }
    }

    @Test
    void neighbouringBlocksDifferByAShadeAtMost() {
        // continuous across block borders: a step of one block moves the field by little (no visible block grid),
        // and the colour by at most one or two levels per channel
        for (int x = -500; x < 500; x++) {
            for (int z = -500; z < 500; z += 13) {
                double f = GrassTint.field(x, z, 3);
                assertTrue(Math.abs(GrassTint.field(x + 1, z, 3) - f) <= 0.12, "x step at " + x + ", " + z);
                assertTrue(Math.abs(GrassTint.field(x, z + 1, 3) - f) <= 0.12, "z step at " + x + ", " + z);
                int c = GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS);
                int n = GrassTint.apply(PLAINS_GRASS, x + 1, z, GrassTint.GRASS);
                assertTrue(Math.abs(r(c) - r(n)) <= 2 && Math.abs(g(c) - g(n)) <= 2 && Math.abs(b(c) - b(n)) <= 2,
                        "colour step at " + x + ", " + z);
            }
        }
    }

    @Test
    void aFieldOfGrassIsNotOneFlatGreen() {
        // over a 256 x 256 map the field takes both signs and spreads (a real large-scale variation) ...
        double sum = 0, sum2 = 0, lo = 1, hi = -1;
        int n = 0;
        for (int x = 0; x < 256; x += 2) {
            for (int z = 0; z < 256; z += 2) {
                double f = GrassTint.field(x, z, 5);
                sum += f;
                sum2 += f * f;
                lo = Math.min(lo, f);
                hi = Math.max(hi, f);
                n++;
            }
        }
        double mean = sum / n, std = Math.sqrt(sum2 / n - mean * mean);
        assertTrue(std >= 0.2, "field std " + std);
        assertTrue(lo < -0.3 && hi > 0.3, "range " + lo + " .. " + hi);
        // ... and the colour of two far-apart patches differs by a visible amount somewhere on the map
        double minL = 1e9, maxL = -1e9;
        for (int x = 0; x < 256; x += 4) {
            for (int z = 0; z < 256; z += 4) {
                double l = luma(GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS));
                minL = Math.min(minL, l);
                maxL = Math.max(maxL, l);
            }
        }
        assertTrue(maxL - minL >= 0.04 * luma(PLAINS_GRASS), "luma spread " + (maxL - minL));
    }

    @Test
    void grassAndLeavesVaryIndependently() {
        // the foliage field is its own (a tree is not always as dry as the grass under it)
        int same = 0, total = 0;
        for (int x = 0; x < 512; x += 16) {
            for (int z = 0; z < 512; z += 16) {
                total++;
                if (Math.abs(GrassTint.field(x, z, GrassTint.GRASS) - GrassTint.field(x, z, GrassTint.FOLIAGE)) < 1e-9) same++;
            }
        }
        assertTrue(same < total / 4, same + " of " + total + " equal");
    }
}
