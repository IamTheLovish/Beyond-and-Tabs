package com.solegendary.reignofnether.environment.lawn;

/**
 * The pure rules of the giant lawn (lovish, Nov 4: the lawn's top is one sheet across blocks, so its soft patches run
 * across blocks and a field shows no per-block repeat; 4x4 in round 62, 8x8 since round 64, "8x8 sheet"). No Minecraft types here, so GiantLawnCellsTest checks them in
 * seconds; GiantLawnBakedModel / GiantLawnQuads apply them to the grass block's baked quads.
 * <p>
 * The sheet is written by tools/gen_blocks.py (write_lawn_sheet, LAWN_SHEET_BLOCKS = {@link #SIZE}; test_lawn_sheet_java
 * ties the two): sprite {@code grass_block_top_g<i>}, i = 1 + column + row * SIZE,
 * the column along the sheet's u and the row along its v. Vanilla's grass_block up face has uv [0, 0, 16, 16] and no
 * rotation (nor do the pack's grass_block..grass_block_v6 blockstate variants), so u runs along +x and v along +z: the
 * block at (x, z) shows column floorMod(x, SIZE), row floorMod(z, SIZE), and the cells meet edge to edge.
 */
final class GiantLawnCells {

    /** Blocks per side of the sheet: gen_blocks.LAWN_SHEET_BLOCKS (tools/test_lawn_sheet_java checks they agree). */
    static final int SIZE = 8;
    /** Cells in the sheet, numbered 1..CELLS like the sprites. */
    static final int CELLS = SIZE * SIZE;

    private static final String TOP = "block/grass_block_top";
    private static final String SHEET = TOP + "_g";

    private GiantLawnCells() {
    }

    /** The sheet cell (1..CELLS) a grass top at world column x, row z shows; floorMod, so negative coordinates continue
     * the sheet without a mirror at the origin. */
    static int cell(int x, int z) {
        return 1 + Math.floorMod(x, SIZE) + Math.floorMod(z, SIZE) * SIZE;
    }

    /** The atlas path of a cell's sprite (namespace minecraft). */
    static String sheetSprite(int cell) {
        return SHEET + cell;
    }

    /** A sprite path the sheet replaces: the grass top and its pack variants (_v2.._v6), never a sheet cell itself. */
    static boolean isLawnTop(String spritePath) {
        return spritePath.startsWith(TOP) && !spritePath.startsWith(SHEET);
    }

    /** t, a coordinate inside [from0, from1], carried to the same place inside [to0, to1]. A zero-width source (a
     * degenerate sprite) maps to to0. */
    static float remap(float t, float from0, float from1, float to0, float to1) {
        float span = from1 - from0;
        if (span == 0f)
            return to0;
        return to0 + (t - from0) / span * (to1 - to0);
    }

    /**
     * A copy of a quad's packed vertices with each vertex's uv (ints 4 and 5: the BLOCK format puts position, colour,
     * then uv) moved from the old sprite's [u0, u1] x [v0, v1] onto the new one's; everything else copied as it is. The
     * stride is the array's quarter, so a wider vertex format still works. null when the array is not 4 vertices of at
     * least 6 ints (the caller then keeps the original quad).
     */
    static int[] remapUVs(int[] vertices, float fromU0, float fromU1, float fromV0, float fromV1,
                          float toU0, float toU1, float toV0, float toV1) {
        if (vertices.length % 4 != 0 || vertices.length / 4 < 6)
            return null;
        int stride = vertices.length / 4;
        int[] out = vertices.clone();
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            float u = Float.intBitsToFloat(vertices[o + 4]);
            float v = Float.intBitsToFloat(vertices[o + 5]);
            out[o + 4] = Float.floatToRawIntBits(remap(u, fromU0, fromU1, toU0, toU1));
            out[o + 5] = Float.floatToRawIntBits(remap(v, fromV0, fromV1, toV0, toV1));
        }
        return out;
    }
}
