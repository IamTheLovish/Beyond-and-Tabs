package com.solegendary.reignofnether.building;

import java.util.List;

/**
 * What stands and what is still missing on a rising site, cell by cell (claude/faction-construction-oct29.md). Pure -
 * no Minecraft types - so ConstructionGridTest covers it; ConstructionFx fills it from the placement's blocks every
 * recount and draws from it every frame.
 *
 * <p>Round 9 critic (eyes 41084414751): the gold ghost caged finished walls and the tools hung over the lawn, because
 * everything keyed off ONE build front - the lowest layer under half placed - while the walls above it stood (the
 * builders do not lay strictly layer by layer: a few blocks lag, and one lagging layer held the front at the foot).
 * The grid answers per block instead:
 * <ul>
 *   <li>the MISSING blocks - the ghost is drawn over those and nothing else;</li>
 *   <li>the RIM - the top edge of a standing block with a missing block on it, on each side open to air or the
 *   outside: the build front of that column, wherever it is;</li>
 *   <li>the FACES the tools may strike - those same blocks' open sides: standing stone, never air.</li>
 * </ul>
 * Built once per site; {@link #clear} / {@link #mark} / {@link #finish} reuse its arrays, so a recount allocates
 * nothing.
 */
public final class ConstructionGrid {

    private static final byte EMPTY = 0, STANDING = 1, MISSING = 2;
    /** At most this many rim edges and faces are kept (a large site's are a few hundred). */
    public static final int MAX_EDGES = 1024;

    private final int x0, y0, z0, w, h, d;
    private final byte[] cells;
    private final int[] missing;
    private int missingN;
    // the rim: {x0, z0, x1, z1, y} per edge, and the face it tops: {x, y, z, side}
    private final float[] rim = new float[MAX_EDGES * 5];
    private final int[] face = new int[MAX_EDGES * 4];
    private int rimN;
    private float rimSum;
    private final float[] front = new float[MAX_EDGES * 3];
    private final int[] frontSide = new int[MAX_EDGES];
    private int frontN;

    private ConstructionGrid(int x0, int y0, int z0, int w, int h, int d) {
        this.x0 = x0; this.y0 = y0; this.z0 = z0; this.w = w; this.h = h; this.d = d;
        cells = new byte[w * h * d];
        missing = new int[w * h * d];
    }

    /** @param blocks the site's (non-air) block positions as {x, y, z} */
    public static ConstructionGrid of(List<int[]> blocks) {
        if (blocks.isEmpty())
            return new ConstructionGrid(0, 0, 0, 0, 0, 0);
        int ax = Integer.MAX_VALUE, ay = Integer.MAX_VALUE, az = Integer.MAX_VALUE;
        int bx = Integer.MIN_VALUE, by = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (int[] b : blocks) {
            ax = Math.min(ax, b[0]); ay = Math.min(ay, b[1]); az = Math.min(az, b[2]);
            bx = Math.max(bx, b[0]); by = Math.max(by, b[1]); bz = Math.max(bz, b[2]);
        }
        return new ConstructionGrid(ax, ay, az, bx - ax + 1, by - ay + 1, bz - az + 1);
    }

    /** Before a recount: every cell back to empty. */
    public void clear() {
        java.util.Arrays.fill(cells, EMPTY);
    }

    /** One of the site's blocks: standing or still missing. Positions outside the site are ignored. */
    public void mark(int x, int y, int z, boolean placed) {
        int i = index(x, y, z);
        if (i >= 0)
            cells[i] = placed ? STANDING : MISSING;
    }

    /** After a recount's marks: the missing list, the rim and the faces. */
    public void finish() {
        missingN = 0;
        for (int i = 0; i < cells.length; i++)
            if (cells[i] == MISSING)
                missing[missingN++] = i;
        rimN = 0;
        rimSum = 0;
        for (int y = y0; y < y0 + h; y++)
            for (int z = z0; z < z0 + d; z++)
                for (int x = x0; x < x0 + w; x++) {
                    if (cell(x, y, z) != STANDING || cell(x, y + 1, z) != MISSING)
                        continue;
                    for (int side = 0; side < 4 && rimN < MAX_EDGES; side++) {
                        int nx = x + (side == 1 ? 1 : side == 3 ? -1 : 0), nz = z + (side == 2 ? 1 : side == 0 ? -1 : 0);
                        if (cell(nx, y, nz) != EMPTY)
                            continue;
                        int r = rimN * 5;
                        float top = y + 1;
                        switch (side) {
                            case 0 -> set(r, x, z, x + 1, z, top);
                            case 1 -> set(r, x + 1, z, x + 1, z + 1, top);
                            case 2 -> set(r, x + 1, z + 1, x, z + 1, top);
                            default -> set(r, x, z + 1, x, z, top);
                        }
                        int f = rimN * 4;
                        face[f] = x; face[f + 1] = y; face[f + 2] = z; face[f + 3] = side;
                        rimSum += top;
                        rimN++;
                    }
                }
        fronts();
    }

    /** The vertical fronts: each standing block's open faces, against a missing neighbour along the face. */
    private void fronts() {
        frontN = 0;
        for (int y = y0; y < y0 + h; y++)
            for (int z = z0; z < z0 + d; z++)
                for (int x = x0; x < x0 + w; x++) {
                    if (cell(x, y, z) != STANDING)
                        continue;
                    for (int side = 0; side < 4; side++) {
                        int nx = side == 1 ? 1 : side == 3 ? -1 : 0, nz = side == 2 ? 1 : side == 0 ? -1 : 0;
                        if (cell(x + nx, y, z + nz) != EMPTY)
                            continue;
                        for (int t = -1; t <= 1 && frontN < MAX_EDGES; t += 2) {
                            int tx = nz != 0 ? t : 0, tz = nx != 0 ? t : 0;     // along the face
                            int lx = x + tx, lz = z + tz;
                            if (cell(lx, y, lz) != MISSING || cell(lx + nx, y, lz + nz) != EMPTY)
                                continue;
                            // the corner on the face's plane, on the side toward the missing block
                            float cx = nx > 0 ? x + 1 : nx < 0 ? x : (tx > 0 ? x + 1 : x);
                            float cz = nz > 0 ? z + 1 : nz < 0 ? z : (tz > 0 ? z + 1 : z);
                            front[frontN * 3] = cx; front[frontN * 3 + 1] = cz; front[frontN * 3 + 2] = y;
                            frontSide[frontN++] = side;
                        }
                    }
                }
    }

    private void set(int r, float ax, float az, float bx, float bz, float y) {
        rim[r] = ax; rim[r + 1] = az; rim[r + 2] = bx; rim[r + 3] = bz; rim[r + 4] = y;
    }

    private int index(int x, int y, int z) {
        int i = x - x0, j = y - y0, k = z - z0;
        if (i < 0 || j < 0 || k < 0 || i >= w || j >= h || k >= d)
            return -1;
        return (j * d + k) * w + i;
    }

    private byte cell(int x, int y, int z) {
        int i = index(x, y, z);
        return i < 0 ? EMPTY : cells[i];
    }

    public boolean isMissing(int x, int y, int z) { return cell(x, y, z) == MISSING; }
    public boolean isStanding(int x, int y, int z) { return cell(x, y, z) == STANDING; }
    /** A block of the site, standing or missing (not air, not outside). */
    public boolean isStructure(int x, int y, int z) { return cell(x, y, z) != EMPTY; }

    public int missingCount() { return missingN; }
    /** The i-th missing block's x / y / z. */
    public int missingX(int i) { return missing[i] % w + x0; }
    public int missingY(int i) { return missing[i] / (w * d) + y0; }
    public int missingZ(int i) { return (missing[i] / w) % d + z0; }

    public int rimCount() { return rimN; }
    /** The i-th rim edge, from (x0, z0) to (x1, z1) at height y (block edges; the face's side is on its right). */
    public float rimX0(int i) { return rim[i * 5]; }
    public float rimZ0(int i) { return rim[i * 5 + 1]; }
    public float rimX1(int i) { return rim[i * 5 + 2]; }
    public float rimZ1(int i) { return rim[i * 5 + 3]; }
    public float rimY(int i) { return rim[i * 5 + 4]; }
    /** The rim's mean height (NaN with no rim): where the halo floats. */
    public float meanRimY() { return rimN == 0 ? Float.NaN : rimSum / rimN; }

    /** The faces under the rim edges, one per edge: a standing block's open side (0: -z, 1: +x, 2: +z, 3: -x). */
    public int faceCount() { return rimN; }
    public int faceX(int i) { return face[i * 4]; }
    public int faceY(int i) { return face[i * 4 + 1]; }
    public int faceZ(int i) { return face[i * 4 + 2]; }
    public int faceSide(int i) { return face[i * 4 + 3]; }

    /**
     * The vertical build fronts (round 11 critic: run the rim up them too): where a standing block's open face meets
     * the coplanar open face of a missing block beside it, the vertical edge between them, a block high from its foot
     * {@link #frontY}, at (x, z) on the face's plane, with the face's side.
     */
    public int frontCount() { return frontN; }
    public float frontX(int i) { return front[i * 3]; }
    public float frontZ(int i) { return front[i * 3 + 1]; }
    public int frontY(int i) { return (int) front[i * 3 + 2]; }
    public int frontSide(int i) { return frontSide[i]; }

    /** The top of the highest standing block in column (x, z) (NaN for none, or outside). */
    public float columnTop(int x, int z) {
        if (index(x, y0, z) < 0)
            return Float.NaN;
        for (int y = y0 + h - 1; y >= y0; y--)
            if (cell(x, y, z) == STANDING)
                return y + 1;
        return Float.NaN;
    }

    /** The site's bounds (block coordinates, inclusive minimum, exclusive maximum). */
    public int minX() { return x0; }
    public int minZ() { return z0; }
    public int maxX() { return x0 + w; }
    public int maxZ() { return z0 + d; }
}
