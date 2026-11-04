package com.solegendary.reignofnether.environment.lawn;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The giant lawn (lovish, Nov 4: the lawn's top is one 4x4-block sheet, so its patches run across blocks and the
 * field has no per-block repeat). The pure rules behind GiantLawnBakedModel: which cell of the sheet a block's top
 * shows, which sprites are the lawn top it replaces, and how a top quad's UVs move from its old sprite onto the cell's.
 * The sheet's cells are written by tools/gen_blocks.py (GIANT): sprite i = 1 + x + z * 4, x the sheet column (u), z the
 * row (v); vanilla's up face maps u along +x and v along +z, so cell(x, z) must follow world x and z the same way.
 * Written before GiantLawnCells.
 */
class GiantLawnCellsTest {

    @Test
    void cellsFollowGenBlocksSheetOrder() {
        assertEquals(1, GiantLawnCells.cell(0, 0));
        assertEquals(2, GiantLawnCells.cell(1, 0), "x is the sheet column");
        assertEquals(4, GiantLawnCells.cell(3, 0));
        assertEquals(5, GiantLawnCells.cell(0, 1), "z is the sheet row");
        assertEquals(16, GiantLawnCells.cell(3, 3));
    }

    @Test
    void cellsRepeatEveryFourBlocks() {
        for (int x = -9; x < 9; x++)
            for (int z = -9; z < 9; z++) {
                assertEquals(GiantLawnCells.cell(x, z), GiantLawnCells.cell(x + 4, z), "x " + x + " z " + z);
                assertEquals(GiantLawnCells.cell(x, z), GiantLawnCells.cell(x, z - 8), "x " + x + " z " + z);
            }
    }

    @Test
    void negativeCoordinatesContinueTheSheetWithoutAMirror() {
        // -1 is the column left of 0, i.e. the sheet's last column: no flip at the world origin
        assertEquals(4, GiantLawnCells.cell(-1, 0));
        assertEquals(13, GiantLawnCells.cell(0, -1));
        assertEquals(16, GiantLawnCells.cell(-1, -1));
        assertEquals(1, GiantLawnCells.cell(-4, -4));
        assertEquals(GiantLawnCells.cell(1_000_001, -3), GiantLawnCells.cell(1, 1));
        assertEquals(GiantLawnCells.cell(Integer.MIN_VALUE, Integer.MIN_VALUE), 1);
    }

    @Test
    void everyCellOneToSixteenIsUsedOncePerSheet() {
        Set<Integer> seen = new HashSet<>();
        for (int x = 8; x < 12; x++)
            for (int z = -4; z < 0; z++) {
                int c = GiantLawnCells.cell(x, z);
                assertTrue(c >= 1 && c <= GiantLawnCells.CELLS, "cell " + c);
                seen.add(c);
            }
        assertEquals(16, seen.size());
        assertEquals(16, GiantLawnCells.CELLS);
    }

    @Test
    void sheetSpriteNames() {
        assertEquals("block/grass_block_top_g1", GiantLawnCells.sheetSprite(1));
        assertEquals("block/grass_block_top_g16", GiantLawnCells.sheetSprite(16));
    }

    @Test
    void theLawnTopsAreTheGrassTopAndItsVariants() {
        assertTrue(GiantLawnCells.isLawnTop("block/grass_block_top"));
        assertTrue(GiantLawnCells.isLawnTop("block/grass_block_top_v2"));
        assertTrue(GiantLawnCells.isLawnTop("block/grass_block_top_v6"));
        // not the sides, the overlay, the snow, other blocks - nor a sheet cell (never swapped twice)
        assertFalse(GiantLawnCells.isLawnTop("block/grass_block_side"));
        assertFalse(GiantLawnCells.isLawnTop("block/grass_block_side_overlay"));
        assertFalse(GiantLawnCells.isLawnTop("block/grass_block_snow"));
        assertFalse(GiantLawnCells.isLawnTop("block/dirt"));
        assertFalse(GiantLawnCells.isLawnTop("block/grass_block_top_g1"));
        assertFalse(GiantLawnCells.isLawnTop("block/grass_block_top_g16"));
        assertFalse(GiantLawnCells.isLawnTop("item/grass_block_top"));
    }

    @Test
    void remapCarriesTheEndsAndTheMiddle() {
        // old sprite u in [0.25, 0.5], new in [0.75, 0.8125]
        assertEquals(0.75f, GiantLawnCells.remap(0.25f, 0.25f, 0.5f, 0.75f, 0.8125f), 1e-7f);
        assertEquals(0.8125f, GiantLawnCells.remap(0.5f, 0.25f, 0.5f, 0.75f, 0.8125f), 1e-7f);
        assertEquals(0.78125f, GiantLawnCells.remap(0.375f, 0.25f, 0.5f, 0.75f, 0.8125f), 1e-7f);
        // a sprite whose u runs the other way round still maps its ends onto the new ends
        assertEquals(0.8125f, GiantLawnCells.remap(0.25f, 0.5f, 0.25f, 0.75f, 0.8125f), 1e-7f);
    }

    @Test
    void remapOfADegenerateSpriteGivesTheNewStart() {
        assertEquals(0.1f, GiantLawnCells.remap(0.3f, 0.3f, 0.3f, 0.1f, 0.2f), 1e-7f);
    }

    /** One BLOCK-format vertex: x, y, z, colour, u, v, light, normal. */
    private static int[] vertex(float x, float z, float u, float v) {
        return new int[]{Float.floatToRawIntBits(x), Float.floatToRawIntBits(1f), Float.floatToRawIntBits(z), -1,
                Float.floatToRawIntBits(u), Float.floatToRawIntBits(v), 0x00F000F0, 0x7F << 8};
    }

    private static int[] quad(float u0, float u1, float v0, float v1) {
        int[] q = new int[32];
        System.arraycopy(vertex(0, 0, u0, v0), 0, q, 0, 8);
        System.arraycopy(vertex(0, 1, u0, v1), 0, q, 8, 8);
        System.arraycopy(vertex(1, 1, u1, v1), 0, q, 16, 8);
        System.arraycopy(vertex(1, 0, u1, v0), 0, q, 24, 8);
        return q;
    }

    @Test
    void remapUVsMovesOnlyTheUVsAndCopies() {
        int[] old = quad(0.25f, 0.5f, 0.125f, 0.25f);
        int[] before = old.clone();
        int[] out = GiantLawnCells.remapUVs(old, 0.25f, 0.5f, 0.125f, 0.25f, 0.5f, 0.625f, 0f, 0.0625f);
        assertNotSame(old, out);
        assertArrayEquals(before, old, "the original quad's vertices are not touched");
        int[] want = quad(0.5f, 0.625f, 0f, 0.0625f);
        assertArrayEquals(want, out, "same positions, colour, light and normal; UVs on the new sprite");
    }

    @Test
    void remapUVsKeepsAPartialFaceInsideTheNewSprite() {
        // a quad covering the sprite's left half (u 0..8 of 16) stays the left half on the new sprite
        int[] old = quad(0.25f, 0.375f, 0f, 0.25f);
        int[] out = GiantLawnCells.remapUVs(old, 0.25f, 0.5f, 0f, 0.25f, 0.5f, 0.75f, 0.5f, 0.75f);
        assertEquals(0.5f, Float.intBitsToFloat(out[4]), 1e-7f);
        assertEquals(0.625f, Float.intBitsToFloat(out[16 + 4]), 1e-7f);
        assertEquals(0.75f, Float.intBitsToFloat(out[8 + 5]), 1e-7f);
    }

    @Test
    void remapUVsFollowsTheStrideOfAWiderFormat() {
        // a 10-int vertex (an extended format): the uv still sits at ints 4 and 5 of each vertex
        int[] old = new int[40];
        for (int i = 0; i < 4; i++) {
            old[i * 10 + 4] = Float.floatToRawIntBits(0f);
            old[i * 10 + 5] = Float.floatToRawIntBits(1f);
            old[i * 10 + 9] = 12345;
        }
        int[] out = GiantLawnCells.remapUVs(old, 0f, 1f, 0f, 1f, 0.5f, 0.75f, 0.25f, 0.5f);
        for (int i = 0; i < 4; i++) {
            assertEquals(0.5f, Float.intBitsToFloat(out[i * 10 + 4]), 1e-7f);
            assertEquals(0.5f, Float.intBitsToFloat(out[i * 10 + 5]), 1e-7f);
            assertEquals(12345, out[i * 10 + 9]);
        }
    }

    @Test
    void remapUVsRefusesAVertexArrayItCannotRead() {
        assertNull(GiantLawnCells.remapUVs(new int[30], 0, 1, 0, 1, 0, 1, 0, 1), "not 4 vertices");
        assertNull(GiantLawnCells.remapUVs(new int[20], 0, 1, 0, 1, 0, 1, 0, 1), "a vertex too short for a uv");
    }
}
