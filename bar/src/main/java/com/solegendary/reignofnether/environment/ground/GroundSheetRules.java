package com.solegendary.reignofnether.environment.ground;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * The pure rules of the giant ground sheets (lovish, Nov 4: "vary the block sizes" = PATTERN SIZES - ground textures
 * spanning 2x2, 4x4 or 8x8 blocks so no block grid shows; round 62 began it with the lawn's 4x4 sheet). No Minecraft
 * types here, so GroundSheetRulesTest checks them in seconds; GroundSheet / GiantGroundBakedModel apply them to the
 * blocks' baked quads.
 * <ul>
 *   <li><b>Which blocks:</b> {@link #TABLE} - the block, the sprite its top draws (with the pack's _v2.._v6 variants),
 *   which client option switches it, and whether it also gets the floating clover and flower layers (the lawn
 *   only).</li>
 *   <li><b>How big:</b> the art decides. A sheet is the sprites {@code <top>_g1 .. _g<N*N>} (row-major from the top
 *   left: i = 1 + column + row * N, as tools/gen_blocks.py and Athena's giant loader number them) and the pack ships 4,
 *   16 or 64 of them for a 2x2, 4x4 or 8x8 sheet; anything else (none, a missing cell, 9) leaves the block as it is,
 *   so this is safe to ship before the art exists.</li>
 *   <li><b>Where:</b> the block at (x, z) shows column floorMod(x, N), row floorMod(z, N); its top quad's UVs are laid
 *   from the quad's POSITION (u along +x, v along +z, vanilla's unturned up face), so a lowered top (dirt_path at
 *   15/16) and a model the blockstate turns (vanilla turns dirt and the path at random) still meet their neighbours
 *   edge to edge.</li>
 *   <li><b>Which sheet (round 64, lovish: "8x8 sheet + hashed pick", so the patches never line up into a grid):</b> a
 *   top may have up to {@link #MAX_VARIANTS} same-sized sheets. Variant 1 is the set above ({@code <top>_g<i>},
 *   unchanged); variant k = 2..4 is {@code <top>_k<k>_g<i>}, numbered the same way. The {@code _k} cannot collide with
 *   {@link #isTop}'s {@code <top>_v<digits>} (the pack's per-block variants of the top itself, which a sheet replaces):
 *   a name with {@code _k} is never a top, and {@code _v} names never carry a {@code _g}. A variant counts only when it
 *   has exactly N*N sprites, 1..N*N (the size variant 1 gives); a partial set is ignored (and logged once), so a set
 *   being drawn cannot put holes in the ground. Each N x N square of the world (floorDiv(x, N), floorDiv(z, N)) picks
 *   one of the K complete variants by a well-mixed hash of the square ({@link #variantIndex}): uniform, constant
 *   inside the square, independent of its neighbours. NEVER turned or mirrored - the art bakes its light from the east
 *   (the engine's sun at t5000) and a turn would spin the sun per square; the variants differ in content and each
 *   one's edge rows are drawn to join any other's (the texture side's job).</li>
 * </ul>
 */
final class GroundSheetRules {

    /**
     * One ground block: its registry path (namespace minecraft), the atlas path of the sprite its top draws, whether
     * the lawn option ({@code lawn_sheet}) switches it rather than {@code ground_sheets}, and whether it gets the
     * floating layers (the clover and, round 64, the flowers: the lawn only).
     */
    record Entry(String block, String top, boolean lawn, boolean clover) {
    }

    /** The ground the RTS camera sees most (concept C: bright lawn, pale warm trail, dark rich soil). */
    static final List<Entry> TABLE = List.of(
            new Entry("grass_block", "block/grass_block_top", true, true),   // snowy=false only (the snow top is white)
            new Entry("dirt", "block/dirt", false, false),
            new Entry("coarse_dirt", "block/coarse_dirt", false, false),
            new Entry("dirt_path", "block/dirt_path_top", false, false),     // top at 15/16
            new Entry("gravel", "block/gravel", false, false),
            new Entry("mud", "block/mud", false, false),
            new Entry("podzol", "block/podzol_top", false, false));          // snowy=false only

    /** The sheet sizes a pack may ship (blocks per side), largest first. */
    static final int[] SIZES = {8, 4, 2};
    /** The most cells of any sheet (8 x 8). */
    static final int MAX_CELLS = 64;
    /** The most sheet variants of one top (variant 1 plus _k2.._k4). */
    static final int MAX_VARIANTS = 4;
    /** The most slots (a variant's cell) of any top: what the model data carries. */
    static final int MAX_SLOTS = MAX_VARIANTS * MAX_CELLS;

    private static final long SALT_VARIANT = 0x5EE7L;

    private GroundSheetRules() {
    }

    /** The sheet cell (1..N*N) the top at world column x, row z shows; floorMod, so negative coordinates continue the
     * sheet without a mirror at the origin. */
    static int cell(int x, int z, int n) {
        return 1 + Math.floorMod(x, n) + Math.floorMod(z, n) * n;
    }

    /** The atlas path of a cell's sprite: {@code <top>_g<cell>} (namespace minecraft). */
    static String sheetSprite(String top, int cell) {
        return top + "_g" + cell;
    }

    /** The atlas path of variant k's cell: {@code <top>_g<cell>} for variant 1 (round 62's names), else
     * {@code <top>_k<k>_g<cell>}. */
    static String variantSprite(String top, int variant, int cell) {
        return variant == 1 ? sheetSprite(top, cell) : top + "_k" + variant + "_g" + cell;
    }

    /** A sprite the sheet replaces: the block's top itself or a pack variant of it ({@code <top>_v<digits>}) - an
     * exact rule, since "block/dirt" is a prefix of "block/dirt_path_top", and never a sheet cell ({@code _g<i>}). */
    static boolean isTop(String top, String path) {
        if (!path.startsWith(top))
            return false;
        int n = top.length();
        if (path.length() == n)
            return true;
        if (path.length() < n + 3 || path.charAt(n) != '_' || path.charAt(n + 1) != 'v')
            return false;
        for (int i = n + 2; i < path.length(); i++)
            if (!Character.isDigit(path.charAt(i)))
                return false;
        return true;
    }

    /**
     * The sheet's blocks per side: the count of sprites {@code <top>_g1, _g2, ...} present without a gap must be
     * exactly 4, 16 or 64 (2x2, 4x4, 8x8); 0 (off) for anything else - a 4x4 sheet missing a cell is not a 2x2 one.
     */
    static int sheetSize(String top, Predicate<String> present) {
        int count = 0;
        while (count <= MAX_CELLS && present.test(sheetSprite(top, count + 1)))
            count++;
        for (int n : SIZES)
            if (count == n * n)
                return n;
        return 0;
    }

    /** The variants of a top's N x N sheet: the complete ones (variant numbers, ascending; 1 first) and the partial
     * ones (some sprites, not exactly 1..N*N). */
    record Variants(int[] complete, int[] partial) {
        int count() {
            return complete.length;
        }
    }

    /**
     * Which sheet variants the pack ships for a top whose variant 1 is an n x n sheet (n from {@link #sheetSize}; 0 =
     * no sheet, no variants). Variant 1 is complete by that; variant k = 2..MAX_VARIANTS is complete when its sprites
     * are exactly {@code <top>_k<k>_g1 .. _g<n*n>} - none missing, none past n*n (a sheet of another size) - and
     * partial when it has some sprite but is not complete.
     */
    static Variants variants(String top, int n, Predicate<String> present) {
        if (n <= 0)
            return new Variants(new int[0], new int[0]);
        int[] complete = new int[MAX_VARIANTS];
        int[] partial = new int[MAX_VARIANTS];
        int nc = 0, np = 0;
        complete[nc++] = 1;
        for (int k = 2; k <= MAX_VARIANTS; k++) {
            int count = 0;
            boolean whole = true;
            for (int c = 1; c <= MAX_CELLS + 1; c++) {
                boolean has = present.test(variantSprite(top, k, c));
                if (has)
                    count++;
                else if (c <= n * n)
                    whole = false;
            }
            if (whole && count == n * n)
                complete[nc++] = k;
            else if (count > 0)
                partial[np++] = k;
        }
        return new Variants(Arrays.copyOf(complete, nc), Arrays.copyOf(partial, np));
    }

    /** Which of k variants (0..k-1, an index into Variants.complete) the n x n square holding block (x, z) shows: a
     * hash of the square's coordinates, so every block of the square agrees and the squares are independent. */
    static int variantIndex(int x, int z, int n, int k) {
        if (k <= 1)
            return 0;
        long h = GroundMath.hash(Math.floorDiv(x, n), Math.floorDiv(z, n), SALT_VARIANT);
        return (int) Long.remainderUnsigned(h, k);
    }

    /** The slot (1..k*n*n) of block (x, z) in a top with k variants of an n x n sheet: its variant's block of n*n
     * slots, then its cell - the same cell in every variant (no turn, no mirror). */
    static int slot(int x, int z, int n, int k) {
        return 1 + variantIndex(x, z, n, k) * n * n + (cell(x, z, n) - 1);
    }

    /** The cell (1..n*n) of a slot. */
    static int cellOfSlot(int slot, int n) {
        return 1 + (slot - 1) % (n * n);
    }

    /** The variant index (0..k-1) of a slot. */
    static int variantIndexOfSlot(int slot, int n) {
        return (slot - 1) / (n * n);
    }

    private static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    /**
     * A copy of an up quad's packed vertices with each vertex's uv (ints 4 and 5: the BLOCK format puts position,
     * colour, then uv) laid from its position: u from x across [u0, u1], v from z across [v0, v1], the block clamped to
     * its cell so nothing samples a neighbouring sprite; then pulled towards the face's centre by shrink, as vanilla's
     * FaceBakery does with the sprite's uvShrinkRatio. Everything else is copied as it is; the stride is the array's
     * quarter, so a wider vertex format still works. null when the array is not 4 vertices of at least 6 ints (the
     * caller then keeps the original quad).
     */
    static int[] projectUp(int[] vertices, float shrink, float u0, float u1, float v0, float v1) {
        if (vertices.length % 4 != 0 || vertices.length / 4 < 6)
            return null;
        int stride = vertices.length / 4;
        float minX = 1f, maxX = 0f, minZ = 1f, maxZ = 0f;
        for (int i = 0; i < 4; i++) {
            float x = clamp01(Float.intBitsToFloat(vertices[i * stride]));
            float z = clamp01(Float.intBitsToFloat(vertices[i * stride + 2]));
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        float cx = (minX + maxX) / 2f, cz = (minZ + maxZ) / 2f;
        int[] out = vertices.clone();
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            float x = clamp01(Float.intBitsToFloat(vertices[o]));
            float z = clamp01(Float.intBitsToFloat(vertices[o + 2]));
            x += (cx - x) * shrink;
            z += (cz - z) * shrink;
            out[o + 4] = Float.floatToRawIntBits(u0 + x * (u1 - u0));
            out[o + 5] = Float.floatToRawIntBits(v0 + z * (v1 - v0));
        }
        return out;
    }
}
