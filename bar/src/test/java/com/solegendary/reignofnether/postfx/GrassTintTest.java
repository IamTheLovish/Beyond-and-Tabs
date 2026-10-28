package com.solegendary.reignofnether.postfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grass tint noise (lovish Oct 22 20:23, "yes this sounds good"): a low-frequency world-position noise on the grass
 * and foliage colour so a big grass field is not one flat green from the RTS camera. Deterministic, continuous across
 * block borders, and it really varies across a field. Written before the implementation (rule of Oct 22 19:58).
 * Oct 28 (lovish: "the grass needs a lot more work, looks like 1 bland color from rts view"): the Oct 22 version
 * (+-4.5% value, +-3.5% hue) was too subtle to read from the RTS camera, so the variation tests now ask for a meadow's
 * range - a luma spread of 15%+, patches that differ at the 16-block scale, a straw-to-lush hue drift - and the
 * Oct 22 version fails them.
 * Round 3/4 (Oct 28, two fresh critic rounds on renders beside the reference scan Grass004): round 2's patches were one
 * blurred 20-40 block scale and its dark patches went teal. Added: detail at the 4-block scale against its 16-block
 * surroundings (what the default RTS zoom shows), no teal (blue never rises where red does not), darks that keep
 * units readable (luma never below 70% of the plains colour). Round 2 fails the first two.
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
    void colourStaysInAMeadowsRange() {
        // the variation round the grass's own base colour (round 5 moved that base on purpose, OLIVE_GRASS: the range
        // is about the patches, not the base)
        double br = r(PLAINS_GRASS) * GrassTint.OLIVE_GRASS[0], bg = g(PLAINS_GRASS) * GrassTint.OLIVE_GRASS[1],
            bb = b(PLAINS_GRASS) * GrassTint.OLIVE_GRASS[2];
        double lumaBase = 0.2126 * br + 0.7152 * bg + 0.0722 * bb;
        for (int x = -1000; x < 1000; x += 7) {
            for (int z = -1000; z < 1000; z += 11) {
                int c = GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS);
                assertTrue(Math.abs(luma(c) - lumaBase) <= 0.26 * lumaBase, "luma at " + x + ", " + z);
                assertTrue(Math.abs(r(c) - br) <= 0.42 * br + 1, "red at " + x + ", " + z);
                assertTrue(Math.abs(g(c) - bg) <= 0.42 * bg + 1, "green at " + x + ", " + z);
                assertTrue(Math.abs(b(c) - bb) <= 0.42 * bb + 1, "blue at " + x + ", " + z);
                assertEquals(0, c & 0xFF000000, "no bits above the rgb");
            }
        }
    }

    @Test
    void neighbouringBlocksDifferByAShadeAtMost() {
        // continuous across block borders: a step of one block moves the field by 0.6 at most (its 4.5-block octave
        // through the contrast curve; VALUE turns that into ~3%); the colour moves by at most
        // 26 levels (~12%) at the steepest, ragged edge of a straw or dark clump (the critic asked for 1-2 block
        // transitions, not 15-block gradients) - Embeddium (in the pack) blends it across the block's corners
        for (int x = -500; x < 500; x++) {
            for (int z = -500; z < 500; z += 13) {
                double f = GrassTint.field(x, z, 3);
                assertTrue(Math.abs(GrassTint.field(x + 1, z, 3) - f) <= 0.60, "x step at " + x + ", " + z);
                assertTrue(Math.abs(GrassTint.field(x, z + 1, 3) - f) <= 0.60, "z step at " + x + ", " + z);
                int c = GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS);
                int n = GrassTint.apply(PLAINS_GRASS, x + 1, z, GrassTint.GRASS);
                assertTrue(Math.abs(r(c) - r(n)) <= 26 && Math.abs(g(c) - g(n)) <= 26 && Math.abs(b(c) - b(n)) <= 26,
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
        assertTrue(std >= 0.3, "field std " + std);
        assertTrue(lo < -0.5 && hi > 0.5, "range " + lo + " .. " + hi);
        // ... and the colour of two far-apart patches differs by a visible amount somewhere on the map
        double minL = 1e9, maxL = -1e9;
        for (int x = 0; x < 256; x += 4) {
            for (int z = 0; z < 256; z += 4) {
                double l = luma(GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS));
                minL = Math.min(minL, l);
                maxL = Math.max(maxL, l);
            }
        }
        assertTrue(maxL - minL >= 0.15 * luma(PLAINS_GRASS), "luma spread " + (maxL - minL));
    }

    @Test
    void patchesDifferAtTheScaleTheRtsCameraReads() {
        // the mean brightness of 16 x 16-block patches (about what one patch is on screen when zoomed out) varies -
        // not only single blocks; Oct 22: 0.018 of the mean, a flat field 0.000; round 4 0.026 (its 48 / 14-block blobs
        // were halved on the critic's "camouflage, blur" - the default zoom's detail is tested below)
        double sum = 0, sum2 = 0;
        int n = 0;
        for (int px = 0; px < 256; px += 16) {
            for (int pz = 0; pz < 256; pz += 16) {
                double s = 0;
                for (int x = px; x < px + 16; x++) {
                    for (int z = pz; z < pz + 16; z++) {
                        s += luma(GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS));
                    }
                }
                s /= 256;
                sum += s;
                sum2 += s * s;
                n++;
            }
        }
        double mean = sum / n, std = Math.sqrt(sum2 / n - mean * mean);
        assertTrue(std >= 0.022 * mean, "patch std " + std / mean + " of the mean");
    }

    @Test
    void hueDriftsBetweenStrawAndLush() {
        // dry patches lean to straw-olive, lush ones to blue-green: red / green spreads across a map
        double lo = 9, hi = 0;
        for (int x = 0; x < 256; x += 4) {
            for (int z = 0; z < 256; z += 4) {
                int c = GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS);
                double rg = r(c) / (double) g(c);
                lo = Math.min(lo, rg);
                hi = Math.max(hi, rg);
            }
        }
        assertTrue(hi - lo >= 0.12, "red / green " + lo + " .. " + hi);
    }

    @Test
    void detailAtTheDefaultZoomsScale() {
        // the default RTS zoom shows ~30 blocks: 4-block patches must differ from their 16-block surroundings
        // (round 2: 0.022 of the mean - one slow blurred gradient across the view; round 4: 0.035)
        int n = 256;
        double[][] lum = new double[n][n];
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                lum[x][z] = luma(GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS));
            }
        }
        double s1 = 0, s2 = 0;
        int count = 0;
        for (int px = 0; px < n; px += 16) {
            for (int pz = 0; pz < n; pz += 16) {
                double big = mean(lum, px, pz, 16);
                for (int qx = px; qx < px + 16; qx += 4) {
                    for (int qz = pz; qz < pz + 16; qz += 4) {
                        double d = (mean(lum, qx, qz, 4) - big) / big;
                        s1 += d;
                        s2 += d * d;
                        count++;
                    }
                }
            }
        }
        double std = Math.sqrt(s2 / count - (s1 / count) * (s1 / count));
        assertTrue(std >= 0.03, "4-block vs 16-block std " + std);
    }

    private static double mean(double[][] a, int x0, int z0, int size) {
        double s = 0;
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                s += a[x][z];
            }
        }
        return s / (size * size);
    }

    @Test
    void lushNeverTurnsTeal() {
        // where a block is not yellower than the plains colour, its blue / green stays within 6% of the plains ratio
        // (round 2's lush patches raised blue: +18%, read as teal next to the reference's olive)
        double rg0 = r(PLAINS_GRASS) / (double) g(PLAINS_GRASS), bg0 = b(PLAINS_GRASS) / (double) g(PLAINS_GRASS);
        for (int x = -400; x < 400; x += 3) {
            for (int z = -400; z < 400; z += 7) {
                int c = GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS);
                if (r(c) / (double) g(c) <= rg0)
                    assertTrue(b(c) / (double) g(c) <= 1.06 * bg0, "teal at " + x + ", " + z);
            }
        }
    }

    @Test
    void darksKeepUnitsReadable() {
        // the darkest grass stays above 70% of the plains colour's luma, so dark team colours still separate
        double base = luma(PLAINS_GRASS);
        for (int x = -400; x < 400; x += 2) {
            for (int z = -400; z < 400; z += 5) {
                assertTrue(luma(GrassTint.apply(PLAINS_GRASS, x, z, GrassTint.GRASS)) >= 0.70 * base, "dark at " + x + ", " + z);
            }
        }
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

    private static double hue(int c) {
        float[] hsb = java.awt.Color.RGBtoHSB(r(c), g(c), b(c), null);
        return hsb[0] * 360.0;
    }

    private static double sat(int c) {
        float[] hsb = java.awt.Color.RGBtoHSB(r(c), g(c), b(c), null);
        return hsb[1];
    }

    private static double median(java.util.List<Double> xs) {
        java.util.Collections.sort(xs);
        return xs.get(xs.size() / 2);
    }

    @Test
    void grassLeansYellowGreenLeavesDoNot() {
        // round 5 (Oct 28, noon eyes stills): the lawn read at hue 80 under lovish's Complementary preset and 96 in the
        // plain lane against the reference scan's 72 - too blue-green. Grass on a forest / meadow biome colour (hue
        // 102) now comes out at 92 or under; the leaves keep their own hue (95+) and the grass its saturation
        int forest = 0x79C05A;
        java.util.List<Double> gh = new java.util.ArrayList<>(), fh = new java.util.ArrayList<>(), gs = new java.util.ArrayList<>();
        for (int x = -400; x < 400; x += 5) {
            for (int z = -400; z < 400; z += 7) {
                int c = GrassTint.apply(forest, x, z, GrassTint.GRASS);
                gh.add(hue(c));
                gs.add(sat(c));
                fh.add(hue(GrassTint.apply(forest, x, z, GrassTint.FOLIAGE)));
            }
        }
        double grassHue = median(gh), leafHue = median(fh), grassSat = median(gs);
        assertTrue(grassHue <= 92, "grass hue " + grassHue);
        assertTrue(leafHue >= 95, "leaf hue " + leafHue);
        assertTrue(grassSat <= sat(forest) + 0.03, "grass saturation " + grassSat + " against " + sat(forest));
    }
}
