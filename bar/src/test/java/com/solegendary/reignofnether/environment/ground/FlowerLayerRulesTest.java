package com.solegendary.reignofnether.environment.ground;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floating flower layer (lovish, Nov 4, concept C: "flower specks"; round 63's critic: "no white or yellow
 * specks" - under the biome tint they cannot be white, so they are their own untinted layer): about 6% of lawn blocks
 * carry one cutout up-quad at 16.7/16 with one of eight flower sprites, and the flowered blocks come in DRIFTS (the
 * round 62 critic: "6-12 dots ... inside a 1-2 block patch, ~1 drift per 12 blocks"), not as even salt. The pure rules
 * behind FloatingLayer / GiantGroundBakedModel: the drift field, the pick and its share, its clustering, its
 * independence from the clover, the sprite names, the switch-off when the art is missing and the quad. Written before
 * FlowerLayerRules.
 */
class FlowerLayerRulesTest {

    private static final int SPAN = 512;

    /** The flowered blocks of [x0, x0 + span) x [z0, z0 + span) plus a 2-block margin (index = coordinate - x0 + 2). */
    private static boolean[][] flowers(int x0, int z0, int span, int y) {
        boolean[][] f = new boolean[span + 4][span + 4];
        for (int x = 0; x < span + 4; x++)
            for (int z = 0; z < span + 4; z++)
                f[x][z] = FlowerLayerRules.pick(x0 + x - 2, y, z0 + z - 2) != 0;
        return f;
    }

    @Test
    void aboutSixInAHundredLawnBlocksFlower() {
        boolean[][] f = flowers(-SPAN / 2, -SPAN / 2, SPAN, 64);
        int n = 0;
        for (int x = 2; x < SPAN + 2; x++)
            for (int z = 2; z < SPAN + 2; z++)
                if (f[x][z]) n++;
        assertEquals(0.06, n / (double) (SPAN * SPAN), 0.01, "flowered share over 512 x 512");
    }

    @Test
    void theFlowersComeInDrifts() {
        // most flowered blocks have a flowered neighbour within 2 blocks, and clearly more often than evenly
        // scattered flowers of the same share would (1 - (1 - s)^8 for the 8 blocks round it)
        boolean[][] f = flowers(1000, -3000, SPAN, 70);
        int n = 0, near1 = 0, near2 = 0;
        for (int x = 2; x < SPAN + 2; x++)
            for (int z = 2; z < SPAN + 2; z++) {
                if (!f[x][z])
                    continue;
                n++;
                boolean a1 = false, a2 = false;
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        if ((dx == 0 && dz == 0) || !f[x + dx][z + dz])
                            continue;
                        a2 = true;
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1)
                            a1 = true;
                    }
                if (a1) near1++;
                if (a2) near2++;
            }
        double share = n / (double) (SPAN * SPAN);
        double even1 = 1 - Math.pow(1 - share, 8);
        assertTrue(near2 / (double) n > 0.75, "flowered blocks with a flowered block within 2: " + near2 / (double) n);
        assertTrue(near1 / (double) n > 1.4 * even1,
                "flowered blocks with a flowered neighbour: " + near1 / (double) n + " vs " + even1 + " if even");
    }

    @Test
    void theFieldIsZeroOverMostOfTheLawnAndAboutHalfInsideADrift() {
        int zero = 0, strong = 0;
        double max = 0;
        for (int x = 0; x < SPAN; x++)
            for (int z = 0; z < SPAN; z++) {
                double p = FlowerLayerRules.field(x, z);
                assertTrue(p >= 0 && p <= 0.6, "field " + p + " at " + x + "," + z);
                if (p < 0.01) zero++;
                if (p >= 0.4) strong++;
                max = Math.max(max, p);
            }
        double area = SPAN * (double) SPAN;
        assertTrue(zero / area > 0.7, "field ~0 over most of the lawn: " + zero / area);
        assertTrue(max >= 0.5, "drift peaks reach ~half: " + max);
        assertTrue(strong / area > 0.03 && strong / area < 0.12, "share inside a drift's plateau: " + strong / area);
    }

    @Test
    void aboutOneDriftPerTwelveToSixteenBlocksOfLawn() {
        // count the drift spots: 4-connected patches of field >= 0.2 (two drifts that touch count as one, so the
        // count is a little under the seeds' one per 14.4 blocks)
        int span = 384;
        boolean[][] in = new boolean[span][span];
        for (int x = 0; x < span; x++)
            for (int z = 0; z < span; z++)
                in[x][z] = FlowerLayerRules.field(x - 5000, z + 777) >= 0.2;
        int patches = 0, size = 0;
        int[] stack = new int[span * span];
        for (int x = 0; x < span; x++)
            for (int z = 0; z < span; z++) {
                if (!in[x][z])
                    continue;
                patches++;
                int sp = 0;
                stack[sp++] = x * span + z;
                in[x][z] = false;
                while (sp > 0) {
                    int c = stack[--sp], cx = c / span, cz = c % span;
                    size++;
                    int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] o : d) {
                        int nx = cx + o[0], nz = cz + o[1];
                        if (nx >= 0 && nz >= 0 && nx < span && nz < span && in[nx][nz]) {
                            in[nx][nz] = false;
                            stack[sp++] = nx * span + nz;
                        }
                    }
                }
            }
        double perDrift = span * (double) span / patches;
        assertTrue(perDrift >= 12 && perDrift <= 20, "blocks of lawn per drift: " + perDrift);
        double across = Math.sqrt(size / (double) patches);
        assertTrue(across >= 1 && across <= 2.5, "a drift spot is 1-2 blocks across: " + across);
    }

    @Test
    void noFlowersOutsideTheDrifts() {
        for (int x = -200; x < 200; x++)
            for (int z = -200; z < 200; z++)
                if (FlowerLayerRules.field(x, z) == 0)
                    assertEquals(0, FlowerLayerRules.pick(x, 64, z), "a flower outside every drift at " + x + "," + z);
    }

    @Test
    void picksAreDeterministicAndUniformOverTheEightSprites() {
        int[] layers = new int[FlowerLayerRules.LAYERS + 1];
        int n = 0;
        for (int x = -SPAN / 2; x < SPAN / 2; x++)
            for (int z = -SPAN / 2; z < SPAN / 2; z++) {
                int p = FlowerLayerRules.pick(x, 66, z);
                assertTrue(p >= 0 && p <= FlowerLayerRules.LAYERS, "pick " + p);
                if (p == 0)
                    continue;
                assertEquals(p, FlowerLayerRules.pick(x, 66, z), "the same block always gets the same flowers");
                layers[p]++;
                n++;
            }
        assertEquals(8, FlowerLayerRules.LAYERS);
        assertEquals(0, layers[0]);
        for (int k = 1; k <= FlowerLayerRules.LAYERS; k++)
            assertEquals(1.0 / 8, layers[k] / (double) n, 0.012, "sprite " + k);
    }

    @Test
    void theFlowersAreIndependentOfTheClover() {
        // a second, independent hash: flowers are as common under the clover as on the bare lawn
        int withClover = 0, clover = 0, without = 0, bare = 0;
        for (int x = 0; x < SPAN; x++)
            for (int z = 0; z < SPAN; z++) {
                boolean f = FlowerLayerRules.pick(x, 64, z) != 0;
                if (CloverLayerRules.pick(x, 64, z) != 0) {
                    clover++;
                    if (f) withClover++;
                } else {
                    bare++;
                    if (f) without++;
                }
            }
        assertEquals(without / (double) bare, withClover / (double) clover, 0.006);
    }

    @Test
    void theFlowersShowNoLatticeAlongTheSheetCells() {
        // not tied to x mod 8 / z mod 8 (the lawn sheet's cells): every cell flowers about as often
        int[][] with = new int[8][8];
        int[][] all = new int[8][8];
        for (int x = 0; x < 1024; x++)
            for (int z = 0; z < 1024; z++) {
                all[x & 7][z & 7]++;
                if (FlowerLayerRules.pick(x, 64, z) != 0) with[x & 7][z & 7]++;
            }
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                assertEquals(0.06, with[i][j] / (double) all[i][j], 0.02, "cell " + i + "," + j);
    }

    @Test
    void spriteNamesAndTheMissingArtSwitch() {
        assertEquals("block/grass_flower_layer_1", FlowerLayerRules.sprite(1));
        assertEquals("block/grass_flower_layer_8", FlowerLayerRules.sprite(8));
        assertTrue(FlowerLayerRules.ready(name -> name.matches("block/grass_flower_layer_[1-8]")));
        assertFalse(FlowerLayerRules.ready(name -> false), "no art: off");
        assertFalse(FlowerLayerRules.ready(name -> name.matches("block/grass_flower_layer_[1-7]")), "7 of 8: off");
        assertFalse(FlowerLayerRules.ready(name -> name.matches("block/grass_flower_layer_[2-8]")), "no 1: off");
        assertFalse(FlowerLayerRules.ready(name -> name.matches("block/grass_clover_layer_[1-8]")), "clover is not it");
    }

    private static float f(int[] q, int i, int k) {
        return Float.intBitsToFloat(q[i * 8 + k]);
    }

    @Test
    void theQuadFloatsOverTheCloverUnturnedAndWhite() {
        int[] q = FlowerLayerRules.quad(0.25f, 0.5f, 0.75f, 1f);
        assertEquals(32, q.length);
        assertTrue(FlowerLayerRules.HEIGHT > CloverLayerRules.HEIGHT, "above the clover");
        float[][] xz = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};   // vanilla's up-face corner order (front face from above)
        for (int i = 0; i < 4; i++) {
            assertEquals(xz[i][0], f(q, i, 0), 0f);
            assertEquals(16.7f / 16f, f(q, i, 1), 0f, "0.7/16 above the top");
            assertEquals(xz[i][1], f(q, i, 2), 0f);
            assertEquals(-1, q[i * 8 + 3], "white: nothing tints the flowers");
            assertEquals(0, q[i * 8 + 6], "light: filled by the lighter");
            assertEquals(0x7F << 8, q[i * 8 + 7], "normal (0, 1, 0)");
            // never turned: u along +x, v along +z (the art's light comes from the east)
            assertEquals(xz[i][0] == 0 ? 0.25f : 0.5f, f(q, i, 4), 0f, "u of corner " + i);
            assertEquals(xz[i][1] == 0 ? 0.75f : 1f, f(q, i, 5), 0f, "v of corner " + i);
        }
    }
}
