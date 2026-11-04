package com.solegendary.reignofnether.environment.edge;

/**
 * The pure rules of the edge overlays (lovish, Oct 30: "build edge overlays"): where a path / dirt / sand / gravel top
 * meets grass, a ragged grass fringe spills over onto it, so the border stops being a hard square tile line from the
 * RTS camera. No Minecraft types here - {@link EdgeOverlayBakedModel} reads the neighbours and draws the quads; this
 * class decides which pieces a block draws, how each is turned and which variant a position gets
 * (EdgeOverlayRulesTest).
 * <p>
 * Pieces, each a 128x sprite rooted along the sprite's top edge (t = 0, north in vanilla's top-face UVs):
 * <ul>
 *   <li>EDGE - a ragged strip along one grass side, reaching up to {@link #REACH} into the block;</li>
 *   <li>OUTER - a corner fill where two grass sides meet (the strips' L gets a rounded inside);</li>
 *   <li>INNER - a small tuft in a corner whose only grass neighbour is the diagonal one.</li>
 * </ul>
 * Rotations: sides N 0, E 1, S 2, W 3; corners NW 0, NE 1, SE 2, SW 3 (corner r sits between side r and side r - 1).
 * <p>
 * A block's whole overlay is one int key: its drawn mask (bits 0-7) and each drawn piece's variant (edges 2 bits each
 * from bit 8, corners 1 bit each from bit 16). Two blocks that draw the same thing share a key, so the baked quad lists
 * are cached per key and the mesher only looks them up.
 */
public final class EdgeOverlayRules {

    private EdgeOverlayRules() {}

    // neighbour bits: the 4 sides, then the 4 diagonals
    public static final int N = 1, E = 2, S = 4, W = 8, NE = 16, SE = 32, SW = 64, NW = 128;

    public static final int PIECE_EDGE = 0, PIECE_OUTER = 1, PIECE_INNER = 2, PIECES = 3;
    public static final int EDGE_VARIANTS = 3, OUTER_VARIANTS = 2, INNER_VARIANTS = 2;

    /** Slots 0-3: the side strips, 4-7: the corners. One piece per slot at most, each slot its own lift. */
    public static final int SLOTS = 8;

    /** How far a piece's quad reaches into the block (5/16): the sprites keep every texel within it. */
    public static final float REACH = 5f / 16f;

    /** Every key is below this: 8 mask bits, 4 x 2 edge-variant bits, 4 corner-variant bits. */
    public static final int KEYS = 1 << 20;

    private static final int[] SIDE_BIT = {N, E, S, W};
    private static final int[] CORNER_BIT = {NW, NE, SE, SW};

    // the 6 orders of the 3 strip variants: a long edge is cut into groups of 3 blocks, each group one order, so a
    // variant never shows more than twice in a row (last of one group, first of the next) and has no period
    private static final int[][] PERMS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

    public static int variants(int piece) {
        return piece == PIECE_EDGE ? EDGE_VARIANTS : piece == PIECE_OUTER ? OUTER_VARIANTS : INNER_VARIANTS;
    }

    // a piece code: piece << 4 | rot << 2 | variant
    public static int piece(int code) { return code >> 4; }
    public static int rot(int code) { return (code >> 2) & 3; }
    public static int variant(int code) { return code & 3; }
    public static int slot(int code) { return piece(code) == PIECE_EDGE ? rot(code) : 4 + rot(code); }

    /** The lift of a slot's quads over the top face (~0.0015 block, a step per slot): above the face, never level with
     * another piece it overlaps, so nothing z-fights; far too small to see as a gap from the RTS camera. */
    public static float lift(int slot) {
        return 0.0015f + 0.0003f * slot;
    }

    /** The mask with only what draws: a diagonal counts only when neither side beside it is grass (a strip already
     * reaches that corner). */
    public static int drawnMask(int mask) {
        int out = mask & 0x0F;
        for (int r = 0; r < 4; r++) {
            if ((mask & CORNER_BIT[r]) == 0) continue;
            if ((mask & (SIDE_BIT[r] | SIDE_BIT[(r + 3) & 3])) == 0) out |= CORNER_BIT[r];
        }
        return out;
    }

    /** The overlay key of the block at (x, y, z) whose grass neighbours are mask; 0 = nothing to draw. */
    public static int key(int mask, int x, int y, int z) {
        int m = drawnMask(mask);
        if (m == 0) return 0;
        int k = m;
        // strip variants: N and S strips run along x, E and W along z; each grass boundary line its own sequence
        if ((m & N) != 0) k |= stripVariant(x, 2 * z, y) << 8;
        if ((m & E) != 0) k |= stripVariant(z, 2 * (x + 1) + 1, y) << 10;
        if ((m & S) != 0) k |= stripVariant(x, 2 * (z + 1), y) << 12;
        if ((m & W) != 0) k |= stripVariant(z, 2 * x + 1, y) << 14;
        for (int r = 0; r < 4; r++) {
            boolean outer = (m & SIDE_BIT[r]) != 0 && (m & SIDE_BIT[(r + 3) & 3]) != 0;
            if (outer || (m & CORNER_BIT[r]) != 0)
                k |= (mix(x, y, z, 11 + r) & 1) << (16 + r);
        }
        return k;
    }

    public static int keyMask(int key) { return key & 0xFF; }

    /** The pieces a key draws (one per slot). */
    public static int[] pieces(int key) {
        int m = keyMask(key);
        if (m == 0) return EMPTY;
        int[] buf = new int[SLOTS];
        int c = 0;
        for (int r = 0; r < 4; r++)
            if ((m & SIDE_BIT[r]) != 0) buf[c++] = code(PIECE_EDGE, r, (key >> (8 + 2 * r)) & 3);
        for (int r = 0; r < 4; r++) {
            int v = (key >> (16 + r)) & 1;
            if ((m & SIDE_BIT[r]) != 0 && (m & SIDE_BIT[(r + 3) & 3]) != 0) buf[c++] = code(PIECE_OUTER, r, v);
            else if ((m & CORNER_BIT[r]) != 0) buf[c++] = code(PIECE_INNER, r, v);
        }
        int[] out = new int[c];
        System.arraycopy(buf, 0, out, 0, c);
        return out;
    }

    private static final int[] EMPTY = new int[0];

    private static int code(int piece, int rot, int variant) {
        return piece << 4 | rot << 2 | variant;
    }

    /** The variant of the strip at `along` on one straight grass line (groups of 3 in a hashed order). */
    static int stripVariant(int along, int line, int y) {
        int group = Math.floorDiv(along, 3);
        int perm = Math.floorMod(mix(group, y, line, 7), PERMS.length);
        return PERMS[perm][Math.floorMod(along, 3)];
    }

    /** A well-mixed non-negative hash of a block position and a salt (murmur3's finaliser). */
    static int mix(int x, int y, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ y * 0x165667B19E3779F9L ^ salt * 0xD6E8FEB86659FD93L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (int) (h & 0x7FFFFFFF);
    }

    private static final float[][] RISE = {
            {1, 0, 0, 1},   // EDGE: its grass side (t = 0)
            {1, 1, 0, 1},   // OUTER: both grass sides (s = 0 and t = 0)
            {1, 0, 0, 0},   // INNER: the corner point the diagonal grass touches
    };

    /**
     * How far each vertex of {@link #quad} rises from the block's own top to the grass's (0..1, in quad's vertex
     * order; the same for every rotation, being in sprite space). On a full block both are 1 and nothing changes. On a
     * lowered top (dirt_path, farmland: 15/16) the grass block beside it shows a 1/16 side step, which drew a straight
     * dark line along the true block boundary outside the ragged fringe (eyes 41049090535, Oct 30): the fringe climbs to
     * the grass's top where it touches grass and slopes down inward, so from the RTS camera (55-75 deg down) the step
     * is behind it. The grass top it meets is at the same height there, so the two join with no lip.
     */
    public static float[] rise(int piece) {
        return RISE[piece].clone();
    }

    /** {@link #rise} without the copy, for the baker. */
    static float rise(int piece, int vertex) {
        return RISE[piece][vertex];
    }

    /**
     * The quad of a piece turned by rot: 4 vertices of (x, z, s, t), in vanilla's up-face order (counter-clockwise
     * from above). x, z are block coordinates 0-1; s, t the sprite coordinates 0-1 (t = 0 the sprite's top edge,
     * where the fringe is rooted). Rotation turns the geometry and keeps the sprite's own UVs, so the fringe always
     * faces out of its grass side (and a shader's tangent frame turns with it).
     */
    public static float[] quad(int piece, int rot) {
        float s1 = piece == PIECE_EDGE ? 1f : REACH, t1 = REACH;
        float[][] st = {{0, 0}, {0, t1}, {s1, t1}, {s1, 0}};
        float[] out = new float[16];
        for (int i = 0; i < 4; i++) {
            float s = st[i][0], t = st[i][1], x, z;
            switch (rot & 3) {
                case 0 -> { x = s; z = t; }
                case 1 -> { x = 1 - t; z = s; }
                case 2 -> { x = 1 - s; z = 1 - t; }
                default -> { x = t; z = 1 - s; }
            }
            out[i * 4] = x;
            out[i * 4 + 1] = z;
            out[i * 4 + 2] = s;
            out[i * 4 + 3] = t;
        }
        return out;
    }
}
