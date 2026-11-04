package com.solegendary.reignofnether.environment.ground;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floating clover layer (lovish, Nov 4, concept C: "a low thick clover carpet everywhere"): about three grass
 * blocks in four get one extra cutout up-quad 0.6/16 above the lawn top, one of eight mostly transparent clover sheets
 * turned by a multiple of 90 degrees, chosen by a hash of the block position so the carpet never shows a pattern. The
 * pure rules behind FloatingLayer / GiantGroundBakedModel: the pick and its distribution, the sprite names, the switch-off
 * when the art is missing, and the quad's vertices. Written before CloverLayerRules.
 */
class CloverLayerRulesTest {

    private static final int SPAN = 512;

    @Test
    void picksAreNoneOrOneOfThirtyTwoAndDeterministic() {
        for (int x = -40; x < 40; x++)
            for (int z = -40; z < 40; z++) {
                int p = CloverLayerRules.pick(x, 64, z);
                assertTrue(p >= 0 && p <= CloverLayerRules.PICKS, "pick " + p);
                assertEquals(p, CloverLayerRules.pick(x, 64, z), "the same block always gets the same clover");
            }
        assertEquals(32, CloverLayerRules.PICKS);
    }

    @Test
    void threeBlocksInFourGetCloverUniformOverEightLayersAndFourTurns() {
        int none = 0;
        int[] layers = new int[CloverLayerRules.LAYERS + 1];
        int[] turns = new int[4];
        for (int x = -SPAN / 2; x < SPAN / 2; x++)
            for (int z = -SPAN / 2; z < SPAN / 2; z++) {
                int p = CloverLayerRules.pick(x, 70, z);
                if (p == 0) { none++; continue; }
                layers[CloverLayerRules.layer(p)]++;
                turns[CloverLayerRules.rotation(p)]++;
            }
        double n = (double) SPAN * SPAN;
        double clover = n - none;
        assertEquals(0.25, none / n, 0.01, "a quarter of the blocks without clover");
        assertEquals(0, layers[0]);
        for (int k = 1; k <= CloverLayerRules.LAYERS; k++)
            assertEquals(1.0 / 8, layers[k] / clover, 0.01, "layer " + k);
        for (int r = 0; r < 4; r++)
            assertEquals(0.25, turns[r] / clover, 0.01, "rotation " + r);
    }

    @Test
    void theCloverShowsNoLatticeAlongTheSheetCells() {
        // the pick must not follow x mod 4 / z mod 4 (the lawn sheet's cells) or the carpet would repeat with the sheet
        int[][] with = new int[4][4];
        int[][] all = new int[4][4];
        for (int x = 0; x < SPAN; x++)
            for (int z = 0; z < SPAN; z++) {
                all[x & 3][z & 3]++;
                if (CloverLayerRules.pick(x, 63, z) != 0) with[x & 3][z & 3]++;
            }
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                assertEquals(0.75, with[i][j] / (double) all[i][j], 0.02, "cell " + i + "," + j);
    }

    @Test
    void neighboursAreIndependent() {
        // P(same pick) for independent neighbours = 0.25^2 + 32 * (0.75 / 32)^2 = 0.0801
        int same = 0, pairs = 0;
        for (int x = 0; x < SPAN; x++)
            for (int z = 0; z < SPAN; z++) {
                int p = CloverLayerRules.pick(x, 64, z);
                if (p == CloverLayerRules.pick(x + 1, 64, z)) same++;
                if (p == CloverLayerRules.pick(x, 64, z + 1)) same++;
                pairs += 2;
            }
        assertEquals(0.0801, same / (double) pairs, 0.006);
    }

    @Test
    void theHeightMattersToo() {
        int differ = 0;
        for (int x = 0; x < 100; x++)
            if (CloverLayerRules.pick(x, 64, 7) != CloverLayerRules.pick(x, 65, 7)) differ++;
        assertTrue(differ > 80, "a terrace above a terrace is not the same carpet: " + differ);
    }

    @Test
    void everyLayerAndTurnIsOnePick() {
        Set<Integer> seen = new HashSet<>();
        for (int p = 1; p <= CloverLayerRules.PICKS; p++) {
            int k = CloverLayerRules.layer(p);
            int r = CloverLayerRules.rotation(p);
            assertTrue(k >= 1 && k <= 8, "layer " + k);
            assertTrue(r >= 0 && r <= 3, "rotation " + r);
            assertTrue(seen.add(k * 10 + r), "pick " + p + " repeats " + k + "/" + r);
        }
        assertEquals(32, seen.size());
    }

    @Test
    void spriteNamesAndTheMissingArtSwitch() {
        assertEquals("block/grass_clover_layer_1", CloverLayerRules.sprite(1));
        assertEquals("block/grass_clover_layer_8", CloverLayerRules.sprite(8));
        assertTrue(CloverLayerRules.ready(name -> name.matches("block/grass_clover_layer_[1-8]")));
        assertFalse(CloverLayerRules.ready(name -> false), "no art: off");
        assertFalse(CloverLayerRules.ready(name -> name.matches("block/grass_clover_layer_[1-7]")), "7 of 8: off");
        assertFalse(CloverLayerRules.ready(name -> name.matches("block/grass_clover_layer_[2-8]")), "no 1: off");
    }

    private static float f(int[] q, int i, int k) {
        return Float.intBitsToFloat(q[i * 8 + k]);
    }

    @Test
    void theQuadFloatsAboveTheTopOverTheWholeBlock() {
        int[] q = CloverLayerRules.quad(0, 0.25f, 0.5f, 0.75f, 1f);
        assertEquals(32, q.length);
        float[][] xz = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};   // vanilla's up-face corner order (front face from above)
        for (int i = 0; i < 4; i++) {
            assertEquals(xz[i][0], f(q, i, 0), 0f);
            assertEquals(16.6f / 16f, f(q, i, 1), 0f, "0.6/16 above the top");
            assertEquals(xz[i][1], f(q, i, 2), 0f);
            assertEquals(-1, q[i * 8 + 3], "white: the grass tint colours it");
            assertEquals(0, q[i * 8 + 6], "light: filled by the lighter");
            assertEquals(0x7F << 8, q[i * 8 + 7], "normal (0, 1, 0)");
        }
        // unturned: u along +x, v along +z, the whole sprite
        assertEquals(0.25f, f(q, 0, 4), 0f);
        assertEquals(0.75f, f(q, 0, 5), 0f);
        assertEquals(0.5f, f(q, 2, 4), 0f);
        assertEquals(1f, f(q, 2, 5), 0f);
    }

    @Test
    void theTurnsAreQuarterTurnsOfTheWholeSprite() {
        int[] q0 = CloverLayerRules.quad(0, 0f, 1f, 0f, 1f);
        Set<String> layouts = new HashSet<>();
        for (int r = 0; r < 4; r++) {
            int[] q = CloverLayerRules.quad(r, 0f, 1f, 0f, 1f);
            // corner i shows the sprite corner that corner (i + r) shows unturned
            for (int i = 0; i < 4; i++) {
                assertEquals(f(q0, (i + r) % 4, 4), f(q, i, 4), 0f, "r " + r + " corner " + i);
                assertEquals(f(q0, (i + r) % 4, 5), f(q, i, 5), 0f, "r " + r + " corner " + i);
            }
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 4; i++) s.append(f(q, i, 4)).append(',').append(f(q, i, 5)).append(';');
            layouts.add(s.toString());
        }
        assertEquals(4, layouts.size(), "four different turns");
    }
}
