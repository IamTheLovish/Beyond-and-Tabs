package com.solegendary.reignofnether.environment.ground;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The giant ground sheets (lovish, Nov 4: "vary the block sizes" = PATTERN SIZES - ground textures spanning 2x2, 4x4
 * or 8x8 blocks, so no block grid shows). The pure rules behind GiantGroundBakedModel: which cell of an N x N sheet a
 * block's top shows, which sprites are the top a sheet replaces, how big a sheet the pack ships (the art decides: 4, 16
 * or 64 sprites), and how an up quad's UVs are laid from its position - so a lowered top (dirt_path, 15/16) and a turned
 * model variant land on the same world-aligned sheet. Round 62's lawn (grass_block_top_g1..16, 4x4) is the first row
 * of the table and must keep its cells exactly. Written before GroundSheetRules' round 63 version.
 */
class GroundSheetRulesTest {

    // ---- cells ---------------------------------------------------------------------------------------------------

    @Test
    void lawnCellsAreRound62sUnchanged() {
        // the r62 sheet order (tools/gen_blocks.py GIANT: sprite i = 1 + column + row * 4)
        assertEquals(1, GroundSheetRules.cell(0, 0, 4));
        assertEquals(2, GroundSheetRules.cell(1, 0, 4), "x is the sheet column");
        assertEquals(4, GroundSheetRules.cell(3, 0, 4));
        assertEquals(5, GroundSheetRules.cell(0, 1, 4), "z is the sheet row");
        assertEquals(16, GroundSheetRules.cell(3, 3, 4));
    }

    @Test
    void cellsOfTheOtherSizes() {
        assertEquals(1, GroundSheetRules.cell(0, 0, 2));
        assertEquals(2, GroundSheetRules.cell(1, 0, 2));
        assertEquals(3, GroundSheetRules.cell(0, 1, 2));
        assertEquals(4, GroundSheetRules.cell(1, 1, 2));
        assertEquals(1, GroundSheetRules.cell(2, 2, 2));
        assertEquals(8, GroundSheetRules.cell(7, 0, 8));
        assertEquals(9, GroundSheetRules.cell(0, 1, 8));
        assertEquals(64, GroundSheetRules.cell(7, 7, 8));
        assertEquals(1, GroundSheetRules.cell(8, 16, 8));
    }

    @Test
    void negativeCoordinatesContinueTheSheetWithoutAMirror() {
        // -1 is the column left of 0, i.e. the sheet's last column: no flip at the world origin
        assertEquals(4, GroundSheetRules.cell(-1, 0, 4));
        assertEquals(13, GroundSheetRules.cell(0, -1, 4));
        assertEquals(16, GroundSheetRules.cell(-1, -1, 4));
        assertEquals(1, GroundSheetRules.cell(-4, -4, 4));
        assertEquals(2, GroundSheetRules.cell(-1, 0, 2));
        assertEquals(4, GroundSheetRules.cell(-1, -1, 2));
        assertEquals(8, GroundSheetRules.cell(-1, 0, 8));
        assertEquals(57, GroundSheetRules.cell(0, -1, 8));
        assertEquals(64, GroundSheetRules.cell(-1, -1, 8));
        assertEquals(GroundSheetRules.cell(1, 1, 4), GroundSheetRules.cell(1_000_001, -3, 4));
        assertEquals(1, GroundSheetRules.cell(Integer.MIN_VALUE, Integer.MIN_VALUE, 8));
    }

    @Test
    void cellsRepeatEveryNBlocksAndUseEachCellOncePerSheet() {
        for (int n : new int[]{2, 4, 8}) {
            Set<Integer> seen = new HashSet<>();
            for (int x = -13; x < -13 + n; x++)
                for (int z = 5; z < 5 + n; z++) {
                    int c = GroundSheetRules.cell(x, z, n);
                    assertTrue(c >= 1 && c <= n * n, "n " + n + " cell " + c);
                    seen.add(c);
                    assertEquals(c, GroundSheetRules.cell(x + n, z, n), "n " + n);
                    assertEquals(c, GroundSheetRules.cell(x, z - 3 * n, n), "n " + n);
                }
            assertEquals(n * n, seen.size(), "n " + n);
        }
    }

    // ---- sprites -------------------------------------------------------------------------------------------------

    @Test
    void sheetSpriteNames() {
        assertEquals("block/grass_block_top_g1", GroundSheetRules.sheetSprite("block/grass_block_top", 1));
        assertEquals("block/grass_block_top_g16", GroundSheetRules.sheetSprite("block/grass_block_top", 16));
        assertEquals("block/dirt_path_top_g64", GroundSheetRules.sheetSprite("block/dirt_path_top", 64));
    }

    @Test
    void theTopsAreTheBaseSpriteAndItsPackVariantsOnly() {
        String grass = "block/grass_block_top";
        assertTrue(GroundSheetRules.isTop(grass, "block/grass_block_top"));
        assertTrue(GroundSheetRules.isTop(grass, "block/grass_block_top_v2"));
        assertTrue(GroundSheetRules.isTop(grass, "block/grass_block_top_v6"));
        // not the sides, the overlay, the snow - nor a sheet cell (never swapped twice)
        assertFalse(GroundSheetRules.isTop(grass, "block/grass_block_side"));
        assertFalse(GroundSheetRules.isTop(grass, "block/grass_block_side_overlay"));
        assertFalse(GroundSheetRules.isTop(grass, "block/grass_block_snow"));
        assertFalse(GroundSheetRules.isTop(grass, "block/grass_block_top_g1"));
        assertFalse(GroundSheetRules.isTop(grass, "block/grass_block_top_g16"));
        assertFalse(GroundSheetRules.isTop(grass, "block/grass_block_top_v"));
        assertFalse(GroundSheetRules.isTop(grass, "block/grass_block_top_vx"));
        // "block/dirt" is a prefix of the path's and the coarse dirt's names: an exact rule, not startsWith
        assertTrue(GroundSheetRules.isTop("block/dirt", "block/dirt"));
        assertTrue(GroundSheetRules.isTop("block/dirt", "block/dirt_v3"));
        assertFalse(GroundSheetRules.isTop("block/dirt", "block/dirt_path_top"));
        assertFalse(GroundSheetRules.isTop("block/dirt", "block/dirt_path_side"));
        assertFalse(GroundSheetRules.isTop("block/dirt", "block/dirt_g4"));
        assertFalse(GroundSheetRules.isTop("block/coarse_dirt", "block/dirt"));
    }

    @Test
    void theTableCoversTheGroundBlocks() {
        assertEquals("block/grass_block_top", top("grass_block"));
        assertEquals("block/dirt", top("dirt"));
        assertEquals("block/coarse_dirt", top("coarse_dirt"));
        assertEquals("block/dirt_path_top", top("dirt_path"));
        assertEquals("block/gravel", top("gravel"));
        assertEquals("block/mud", top("mud"));
        assertEquals("block/podzol_top", top("podzol"));
        for (GroundSheetRules.Entry e : GroundSheetRules.TABLE) {
            assertEquals(e.block().equals("grass_block"), e.lawn(), e.block() + ": only the lawn reads lawn_sheet");
            assertEquals(e.block().equals("grass_block"), e.clover(), e.block() + ": only the lawn has clover");
            // no entry's top is another entry's sprite
            for (GroundSheetRules.Entry o : GroundSheetRules.TABLE)
                if (o != e)
                    assertFalse(GroundSheetRules.isTop(e.top(), o.top()), e.top() + " vs " + o.top());
        }
    }

    private static String top(String block) {
        for (GroundSheetRules.Entry e : GroundSheetRules.TABLE)
            if (e.block().equals(block))
                return e.top();
        return null;
    }

    // ---- the sheet's size: the art decides, a partial sheet is off ---------------------------------------------

    private static Predicate<String> upTo(String top, int count) {
        return name -> {
            for (int i = 1; i <= count; i++)
                if (name.equals(GroundSheetRules.sheetSprite(top, i)))
                    return true;
            return false;
        };
    }

    @Test
    void theSheetSizeIsTheSpriteCount() {
        String t = "block/mud";
        assertEquals(2, GroundSheetRules.sheetSize(t, upTo(t, 4)));
        assertEquals(4, GroundSheetRules.sheetSize(t, upTo(t, 16)));
        assertEquals(8, GroundSheetRules.sheetSize(t, upTo(t, 64)));
        // the r62 lawn: g1..16 -> the 4x4 sheet, as before
        assertEquals(4, GroundSheetRules.sheetSize("block/grass_block_top", upTo("block/grass_block_top", 16)));
    }

    @Test
    void missingOrPartialSpritesSwitchTheSheetOff() {
        String t = "block/gravel";
        assertEquals(0, GroundSheetRules.sheetSize(t, name -> false), "no sprites: off");
        assertEquals(0, GroundSheetRules.sheetSize(t, upTo(t, 1)));
        // a 4x4 sheet missing its last cell is not a 2x2 sheet (cells 3 and 4 would be row 0's) - off
        assertEquals(0, GroundSheetRules.sheetSize(t, upTo(t, 15)));
        assertEquals(0, GroundSheetRules.sheetSize(t, upTo(t, 9)), "3x3 is not a size");
        assertEquals(0, GroundSheetRules.sheetSize(t, upTo(t, 63)));
        // a hole in the middle: off
        Predicate<String> holed = upTo(t, 16).and(n -> !n.equals(GroundSheetRules.sheetSprite(t, 7)));
        assertEquals(0, GroundSheetRules.sheetSize(t, holed));
        // another block's sheet does not count
        assertEquals(0, GroundSheetRules.sheetSize(t, upTo("block/dirt", 16)));
        // more than 64: not a size we lay
        assertEquals(0, GroundSheetRules.sheetSize(t, upTo(t, 65)));
    }

    // ---- round 64: sheet variants by a hash of the square ("8x8 sheet + hashed pick") ----------------------------

    /** Every cell of the given variants of an n x n sheet of top. */
    private static Predicate<String> sets(String top, int n, int... variants) {
        Set<String> names = new HashSet<>();
        for (int k : variants)
            for (int c = 1; c <= n * n; c++)
                names.add(GroundSheetRules.variantSprite(top, k, c));
        return names::contains;
    }

    @Test
    void variantSpriteNamesKeepVariantOnesAndNeverReadAsTheTop() {
        String g = "block/grass_block_top";
        assertEquals("block/grass_block_top_g1", GroundSheetRules.variantSprite(g, 1, 1), "variant 1 = round 62's names");
        assertEquals("block/grass_block_top_g64", GroundSheetRules.variantSprite(g, 1, 64));
        assertEquals("block/grass_block_top_k2_g1", GroundSheetRules.variantSprite(g, 2, 1));
        assertEquals("block/grass_block_top_k4_g64", GroundSheetRules.variantSprite(g, 4, 64));
        assertEquals("block/dirt_path_top_k3_g16", GroundSheetRules.variantSprite("block/dirt_path_top", 3, 16));
        assertEquals(4, GroundSheetRules.MAX_VARIANTS);
        // a variant cell is never the top a sheet replaces (no double swap), and never another table entry's top
        for (GroundSheetRules.Entry e : GroundSheetRules.TABLE)
            for (int k = 1; k <= GroundSheetRules.MAX_VARIANTS; k++)
                for (int c : new int[]{1, 4, 16, 64})
                    for (GroundSheetRules.Entry o : GroundSheetRules.TABLE)
                        assertFalse(GroundSheetRules.isTop(o.top(), GroundSheetRules.variantSprite(e.top(), k, c)),
                                o.top() + " vs " + GroundSheetRules.variantSprite(e.top(), k, c));
        // the variants do not change the size variant 1 gives
        assertEquals(8, GroundSheetRules.sheetSize(g, sets(g, 8, 1, 2, 3, 4)));
    }

    @Test
    void completeVariantSetsAreDetected() {
        String t = "block/grass_block_top";
        assertArrayEquals(new int[]{1}, GroundSheetRules.variants(t, 8, sets(t, 8, 1)).complete(), "variant 1 alone");
        assertEquals(1, GroundSheetRules.variants(t, 8, sets(t, 8, 1)).count());
        assertArrayEquals(new int[]{1, 2, 3, 4}, GroundSheetRules.variants(t, 8, sets(t, 8, 1, 2, 3, 4)).complete());
        assertEquals(4, GroundSheetRules.variants(t, 8, sets(t, 8, 1, 2, 3, 4)).count());
        assertArrayEquals(new int[]{1, 2, 3, 4}, GroundSheetRules.variants(t, 2, sets(t, 2, 1, 2, 3, 4)).complete());
        // a missing variant 2 does not hide variant 3
        GroundSheetRules.Variants v = GroundSheetRules.variants(t, 4, sets(t, 4, 1, 3));
        assertArrayEquals(new int[]{1, 3}, v.complete());
        assertArrayEquals(new int[0], v.partial(), "an absent set is not a partial one");
        // no sheet: no variants
        assertEquals(0, GroundSheetRules.variants(t, 0, sets(t, 8, 1, 2, 3, 4)).count());
    }

    @Test
    void incompleteVariantSetsAreIgnoredAndReported() {
        String t = "block/mud";
        // variant 2 missing one cell
        Predicate<String> holed = sets(t, 8, 1, 2, 3)
                .and(name -> !name.equals(GroundSheetRules.variantSprite(t, 2, 37)));
        GroundSheetRules.Variants v = GroundSheetRules.variants(t, 8, holed);
        assertArrayEquals(new int[]{1, 3}, v.complete());
        assertArrayEquals(new int[]{2}, v.partial());
        // variant 2 drawn at another size: a 4x4 set beside an 8x8 variant 1 is partial
        v = GroundSheetRules.variants(t, 8, sets(t, 8, 1).or(sets(t, 4, 2)));
        assertArrayEquals(new int[]{1}, v.complete());
        assertArrayEquals(new int[]{2}, v.partial());
        // and an 8x8 variant beside a 4x4 variant 1 (cells past 16): partial, not complete
        v = GroundSheetRules.variants(t, 4, sets(t, 4, 1).or(sets(t, 8, 4)));
        assertArrayEquals(new int[]{1}, v.complete());
        assertArrayEquals(new int[]{4}, v.partial());
        // a single stray sprite
        v = GroundSheetRules.variants(t, 2, sets(t, 2, 1).or(n -> n.equals(GroundSheetRules.variantSprite(t, 3, 1))));
        assertArrayEquals(new int[]{1}, v.complete());
        assertArrayEquals(new int[]{3}, v.partial());
    }

    @Test
    void theVariantIsConstantInsideASquareAndInRange() {
        for (int n : new int[]{2, 4, 8})
            for (int k = 1; k <= 4; k++)
                for (int sx = -5; sx < 5; sx++)
                    for (int sz = -5; sz < 5; sz++) {
                        int first = GroundSheetRules.variantIndex(sx * n, sz * n, n, k);
                        assertTrue(first >= 0 && first < k, "index " + first + " of " + k);
                        for (int x = sx * n; x < sx * n + n; x++)
                            for (int z = sz * n; z < sz * n + n; z++)
                                assertEquals(first, GroundSheetRules.variantIndex(x, z, n, k),
                                        "n " + n + " k " + k + " block " + x + "," + z + ": one variant per square");
                    }
        assertEquals(0, GroundSheetRules.variantIndex(123, -77, 8, 1), "one variant: always it");
    }

    @Test
    void theVariantPickIsUniformOverK() {
        int span = 256;   // squares per side: 65536 squares
        for (int k = 2; k <= 4; k++) {
            int[] counts = new int[k];
            for (int sx = -span / 2; sx < span / 2; sx++)
                for (int sz = -span / 2; sz < span / 2; sz++)
                    counts[GroundSheetRules.variantIndex(sx * 8, sz * 8, 8, k)]++;
            for (int i = 0; i < k; i++)
                assertEquals(1.0 / k, counts[i] / (double) (span * span), 0.01, "k " + k + " variant " + i);
        }
    }

    @Test
    void neighbouringSquaresPickIndependently() {
        // for independent squares: P(same as a neighbour) = 1/k, and the pair (here, east) is uniform over k*k
        int span = 256, k = 4;
        int sameEast = 0, sameNorth = 0, sameDiag = 0, n = 0;
        int[][] pairs = new int[k][k];
        for (int sx = -span / 2; sx < span / 2; sx++)
            for (int sz = -span / 2; sz < span / 2; sz++) {
                int v = GroundSheetRules.variantIndex(sx * 8, sz * 8, 8, k);
                int east = GroundSheetRules.variantIndex(sx * 8 + 8, sz * 8, 8, k);
                if (v == east) sameEast++;
                if (v == GroundSheetRules.variantIndex(sx * 8, sz * 8 - 8, 8, k)) sameNorth++;
                if (v == GroundSheetRules.variantIndex(sx * 8 + 8, sz * 8 + 8, 8, k)) sameDiag++;
                pairs[v][east]++;
                n++;
            }
        assertEquals(0.25, sameEast / (double) n, 0.01, "east neighbour");
        assertEquals(0.25, sameNorth / (double) n, 0.01, "north neighbour");
        assertEquals(0.25, sameDiag / (double) n, 0.01, "diagonal neighbour");
        for (int a = 0; a < k; a++)
            for (int b = 0; b < k; b++)
                assertEquals(1.0 / 16, pairs[a][b] / (double) n, 0.006, "pair " + a + "," + b);
        // the squares really differ: along a row of 64 squares the variant changes more than half the time
        int changes = 0;
        for (int sx = 0; sx < 64; sx++)
            if (GroundSheetRules.variantIndex(sx * 8, 40, 8, k) != GroundSheetRules.variantIndex(sx * 8 + 8, 40, 8, k))
                changes++;
        assertTrue(changes > 32, "changes along a row: " + changes);
    }

    @Test
    void theVariantPickHasNoLatticeOrAxisPattern() {
        // not a function of sx + sz, sx - sz or of one axis alone (a diagonal stripe or a column of one variant)
        int k = 4;
        for (int line = 0; line < 8; line++) {
            int[] diag = new int[k], anti = new int[k], column = new int[k];
            for (int t = 0; t < 400; t++) {
                diag[GroundSheetRules.variantIndex(t * 4, (t + line) * 4, 4, k)]++;
                anti[GroundSheetRules.variantIndex(t * 4, (line - t) * 4, 4, k)]++;
                column[GroundSheetRules.variantIndex(line * 4, t * 4, 4, k)]++;
            }
            for (int i = 0; i < k; i++) {
                assertTrue(diag[i] > 60 && diag[i] < 140, "diagonal " + line + " variant " + i + ": " + diag[i]);
                assertTrue(anti[i] > 60 && anti[i] < 140, "anti-diagonal " + line + " variant " + i + ": " + anti[i]);
                assertTrue(column[i] > 60 && column[i] < 140, "column " + line + " variant " + i + ": " + column[i]);
            }
        }
    }

    @Test
    void slotsKeepTheUnturnedCellInEveryVariant() {
        // no rotation, no mirror: whatever variant a square picks, the block shows the same cell (column floorMod(x, N),
        // row floorMod(z, N)) of it - the variant only changes which sprite set, never the layout
        for (int n : new int[]{2, 4, 8})
            for (int k = 1; k <= 4; k++) {
                Set<Integer> variantsSeen = new HashSet<>();
                for (int x = -40; x < 40; x++)
                    for (int z = -40; z < 40; z++) {
                        int slot = GroundSheetRules.slot(x, z, n, k);
                        assertTrue(slot >= 1 && slot <= k * n * n, "slot " + slot);
                        assertEquals(GroundSheetRules.cell(x, z, n), GroundSheetRules.cellOfSlot(slot, n),
                                "n " + n + " k " + k + " block " + x + "," + z + ": the unturned cell");
                        assertEquals(GroundSheetRules.variantIndex(x, z, n, k),
                                GroundSheetRules.variantIndexOfSlot(slot, n));
                        variantsSeen.add(GroundSheetRules.variantIndexOfSlot(slot, n));
                    }
                assertEquals(k, variantsSeen.size(), "n " + n + ": all " + k + " variants appear");
            }
        assertEquals(GroundSheetRules.MAX_VARIANTS * GroundSheetRules.MAX_CELLS, GroundSheetRules.MAX_SLOTS);
        // k = 1 is round 63 exactly: slot = cell
        for (int x = -9; x < 9; x++)
            for (int z = -9; z < 9; z++)
                assertEquals(GroundSheetRules.cell(x, z, 8), GroundSheetRules.slot(x, z, 8, 1));
    }

    // ---- UVs from the position -----------------------------------------------------------------------------------

    /** One BLOCK-format vertex: x, y, z, colour, u, v, light, normal. */
    private static int[] vertex(float x, float y, float z, float u, float v) {
        return new int[]{Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z), -1,
                Float.floatToRawIntBits(u), Float.floatToRawIntBits(v), 0x00F000F0, 0x7F << 8};
    }

    /** An up quad in vanilla's corner order (x0 z0, x0 z1, x1 z1, x1 z0) at height y with the given corner UVs. */
    private static int[] quad(float y, float[][] uv) {
        float[][] xz = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        int[] q = new int[32];
        for (int i = 0; i < 4; i++)
            System.arraycopy(vertex(xz[i][0], y, xz[i][1], uv[i][0], uv[i][1]), 0, q, i * 8, 8);
        return q;
    }

    private static float u(int[] q, int i) {
        return Float.intBitsToFloat(q[i * 8 + 4]);
    }

    private static float v(int[] q, int i) {
        return Float.intBitsToFloat(q[i * 8 + 5]);
    }

    @Test
    void anUnturnedTopGetsTheSameUVsAsRound62sRemap() {
        // r62 moved the uv inside the old sprite to the same place inside the new one; for an unturned full top
        // (u along +x, v along +z) laying it by position is the same thing
        int[] old = quad(1f, new float[][]{{0.25f, 0.125f}, {0.25f, 0.25f}, {0.5f, 0.25f}, {0.5f, 0.125f}});
        int[] before = old.clone();
        int[] out = GroundSheetRules.projectUp(old, 0f, 0.5f, 0.625f, 0f, 0.0625f);
        assertNotSame(old, out);
        assertArrayEquals(before, old, "the original quad's vertices are not touched");
        int[] want = quad(1f, new float[][]{{0.5f, 0f}, {0.5f, 0.0625f}, {0.625f, 0.0625f}, {0.625f, 0f}});
        assertArrayEquals(want, out, "same positions, colour, light and normal; UVs on the new sprite");
    }

    @Test
    void aLoweredTurnedTopIsLaidByPositionLikeAFullOne() {
        // dirt_path: top at 15/16, and vanilla's blockstate may turn the model (y 90), which turns its UVs with it -
        // the sheet must still run along world x and z, or every turned block would show a seam
        float y = 15f / 16f;
        int[] turned = quad(y, new float[][]{{0.5f, 0.125f}, {0.25f, 0.125f}, {0.25f, 0.25f}, {0.5f, 0.25f}});
        int[] out = GroundSheetRules.projectUp(turned, 0f, 0.5f, 0.75f, 0.5f, 0.75f);
        float[][] xz = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (int i = 0; i < 4; i++) {
            assertEquals(0.5f + xz[i][0] * 0.25f, u(out, i), 1e-7f, "u of corner " + i);
            assertEquals(0.5f + xz[i][1] * 0.25f, v(out, i), 1e-7f, "v of corner " + i);
            assertEquals(y, Float.intBitsToFloat(out[i * 8 + 1]), 0f, "the height stays");
        }
    }

    @Test
    void aPartialTopKeepsItsPartOfTheCell() {
        // a quad over the block's west half (x 0..0.5) shows the west half of its cell
        int[] q = quad(1f, new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}});
        for (int i : new int[]{2, 3})
            q[i * 8] = Float.floatToRawIntBits(0.5f);
        int[] out = GroundSheetRules.projectUp(q, 0f, 0f, 1f, 0f, 1f);
        assertEquals(0f, u(out, 0), 1e-7f);
        assertEquals(0.5f, u(out, 2), 1e-7f);
        assertEquals(1f, v(out, 2), 1e-7f);
    }

    @Test
    void positionsOutsideTheBlockAreClampedToTheCell() {
        // never sample the neighbouring sprite in the atlas
        int[] q = quad(1f, new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}});
        q[0] = Float.floatToRawIntBits(-0.25f);
        q[2 * 8 + 2] = Float.floatToRawIntBits(1.5f);
        int[] out = GroundSheetRules.projectUp(q, 0f, 0.25f, 0.5f, 0.25f, 0.5f);
        assertEquals(0.25f, u(out, 0), 1e-7f);
        assertEquals(0.5f, v(out, 2), 1e-7f);
    }

    @Test
    void theShrinkPullsTheUVsTowardsTheFaceCentreLikeFaceBakery() {
        // vanilla's FaceBakery lerps a face's uvs towards their centre by the sprite's uvShrinkRatio (no atlas bleed)
        int[] q = quad(1f, new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}});
        int[] out = GroundSheetRules.projectUp(q, 0.01f, 0f, 1f, 0f, 1f);
        assertEquals(0.005f, u(out, 0), 1e-6f);
        assertEquals(0.995f, u(out, 2), 1e-6f);
        assertEquals(0.995f, v(out, 1), 1e-6f);
    }

    @Test
    void projectFollowsTheStrideOfAWiderFormat() {
        // a 10-int vertex (an extended format): position at 0..2, uv at 4 and 5
        int[] old = new int[40];
        float[][] xz = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (int i = 0; i < 4; i++) {
            old[i * 10] = Float.floatToRawIntBits(xz[i][0]);
            old[i * 10 + 2] = Float.floatToRawIntBits(xz[i][1]);
            old[i * 10 + 9] = 12345;
        }
        int[] out = GroundSheetRules.projectUp(old, 0f, 0.5f, 0.75f, 0.25f, 0.5f);
        for (int i = 0; i < 4; i++) {
            assertEquals(0.5f + 0.25f * xz[i][0], Float.intBitsToFloat(out[i * 10 + 4]), 1e-7f);
            assertEquals(0.25f + 0.25f * xz[i][1], Float.intBitsToFloat(out[i * 10 + 5]), 1e-7f);
            assertEquals(12345, out[i * 10 + 9]);
        }
    }

    @Test
    void projectRefusesAVertexArrayItCannotRead() {
        assertNull(GroundSheetRules.projectUp(new int[30], 0, 0, 1, 0, 1), "not 4 vertices");
        assertNull(GroundSheetRules.projectUp(new int[20], 0, 0, 1, 0, 1), "a vertex too short for a uv");
    }
}
