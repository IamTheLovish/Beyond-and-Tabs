package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;

/**
 * Where ground units can walk on a generated map, and how far each start is from everything by foot.
 * <p>
 * The fine grid is the rule MapLayout.reachFrom uses on the pre-made maps (one block per column, a step of at most one,
 * no deep water, lava, void or tree stem) and decides reachability exactly. Walk distances for the fairness score run on
 * a 2x2-block coarse grid (a quarter of the cells; a generated 4v4 needs 16 distance fields per balancing option): a
 * coarse cell is walkable only when its four columns are and none of its own steps exceeds one, and two coarse cells
 * connect when some pair of columns across their border does. Lanes, ramps and fords are 11+ blocks wide, so nothing
 * that matters is lost; the exact check stays on the fine grid.
 * Distances are Dijkstra with integer chamfer costs (10 straight, 14 diagonal; twice that wading, which halves a
 * ground unit's speed, design/terrain_intelligence.md) on a bucket queue, in tenths of a block.
 */
final class WalkGrid {
    static final int UNREACHED = Integer.MAX_VALUE;
    final GenMap m;
    final int half, width;
    final boolean[] walk;
    /** The coarse grid: (2*half+1)/2 rounded up per side. */
    final int cw;
    final boolean[] cWalk;
    final boolean[] cWet;
    /** Coarse edges: bit d set when the cell connects to its neighbour in direction d (E, W, S, N). */
    final byte[] cEdge;

    WalkGrid(GenMap m, boolean trees) {
        this.m = m;
        this.half = m.half;
        this.width = m.width;
        walk = new boolean[width * width];
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = m.idx(x, z);
                walk[i] = walkable(m, x, z, i, trees);
            }
        cw = (width + 1) / 2;
        cWalk = new boolean[cw * cw];
        cWet = new boolean[cw * cw];
        cEdge = new byte[cw * cw];
        for (int a = 0; a < cw; a++)
            for (int b = 0; b < cw; b++) {
                boolean ok = true, wet = false;
                for (int p = 0; p < 2 && ok; p++)
                    for (int q = 0; q < 2 && ok; q++) {
                        int fx = 2 * a + p, fz = 2 * b + q;
                        if (fx >= width || fz >= width || !walk[fx * width + fz]) {
                            ok = false;
                            break;
                        }
                        if (m.surface[fx * width + fz] == WATER)
                            wet = true;
                        if (p == 1 && Math.abs(m.height[fx * width + fz] - m.height[(fx - 1) * width + fz]) > 1)
                            ok = false;
                        if (q == 1 && Math.abs(m.height[fx * width + fz] - m.height[fx * width + fz - 1]) > 1)
                            ok = false;
                    }
                cWalk[a * cw + b] = ok;
                cWet[a * cw + b] = wet;
            }
        for (int a = 0; a < cw; a++)
            for (int b = 0; b < cw; b++) {
                int c = a * cw + b;
                if (!cWalk[c])
                    continue;
                byte e = 0;
                if (a + 1 < cw && cWalk[c + cw] && crossesX(2 * a + 1, 2 * b))
                    e |= 1;
                if (a > 0 && cWalk[c - cw] && crossesX(2 * a - 1, 2 * b))
                    e |= 2;
                if (b + 1 < cw && cWalk[c + 1] && crossesZ(2 * a, 2 * b + 1))
                    e |= 4;
                if (b > 0 && cWalk[c - 1] && crossesZ(2 * a, 2 * b - 1))
                    e |= 8;
                cEdge[c] = e;
            }
    }

    static final byte WATER = (byte) MapLayout.Surface.WATER.ordinal();

    static boolean walkable(GenMap m, int x, int z, int i, boolean trees) {
        if (!m.inside(x, z))
            return false;
        MapLayout.Surface s = GenMap.SURFACES[m.surface[i]];
        if (s == MapLayout.Surface.VOID || s == MapLayout.Surface.LAVA)
            return false;
        if (s == MapLayout.Surface.WATER && m.height[i] < -1)
            return false;
        return !(trees && m.tree.get(i));
    }

    /** Fine columns fx (left of the border) and fx + 1, rows fz and fz + 1: some pair steps across. */
    private boolean crossesX(int fx, int fz) {
        for (int q = 0; q < 2; q++) {
            int z = fz + q;
            if (z >= width || fx + 1 >= width)
                continue;
            int i = fx * width + z, j = (fx + 1) * width + z;
            if (walk[i] && walk[j] && Math.abs(m.height[i] - m.height[j]) <= 1)
                return true;
        }
        return false;
    }

    private boolean crossesZ(int fx, int fz) {
        for (int p = 0; p < 2; p++) {
            int x = fx + p;
            if (x >= width || fz + 1 >= width)
                continue;
            int i = x * width + fz, j = x * width + fz + 1;
            if (walk[i] && walk[j] && Math.abs(m.height[i] - m.height[j]) <= 1)
                return true;
        }
        return false;
    }

    boolean walkable(int dx, int dz) {
        return m.inGrid(dx, dz) && walk[m.idx(dx, dz)];
    }

    /** Exact reachability (fine grid, 4-connected, steps of one) from a column; the start is walkable by definition. */
    boolean[] flood(int sx, int sz) {
        boolean[] seen = new boolean[width * width];
        if (!m.inGrid(sx, sz))
            return seen;
        int[] queue = new int[width * width];
        int head = 0, tail = 0, s = m.idx(sx, sz);
        seen[s] = true;
        queue[tail++] = s;
        while (head < tail) {
            int c = queue[head++];
            int cx = c / width, cz = c % width;
            for (int d = 0; d < 4; d++) {
                int nx = cx + (d == 0 ? 1 : d == 1 ? -1 : 0), nz = cz + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= width || nz >= width)
                    continue;
                int ni = nx * width + nz;
                if (!seen[ni] && walk[ni] && Math.abs(m.height[ni] - m.height[c]) <= 1) {
                    seen[ni] = true;
                    queue[tail++] = ni;
                }
            }
        }
        return seen;
    }

    /** Coarse cell of a centre-relative column. */
    int cell(int dx, int dz) {
        int a = (dx + half) >> 1, b = (dz + half) >> 1;
        a = Math.max(0, Math.min(cw - 1, a));
        b = Math.max(0, Math.min(cw - 1, b));
        return a * cw + b;
    }

    /**
     * Walk distance from (sx, sz) to every coarse cell, in tenths of a block (UNREACHED where no ground path exists).
     * The start's own cell is walkable by definition (a capitol stands there).
     */
    int[] distances(int sx, int sz) {
        int n = cw * cw;
        int[] dist = new int[n];
        java.util.Arrays.fill(dist, UNREACHED);
        int start = cell(sx, sz);
        // a start that rounds onto an unwalkable coarse cell (a pad's border) begins at the nearest walkable cell
        if (!cWalk[start]) {
            int best = -1;
            for (int r = 1; r <= 4 && best < 0; r++)
                for (int a = -r; a <= r && best < 0; a++)
                    for (int b = -r; b <= r; b++) {
                        int ca = start / cw + a, cb = start % cw + b;
                        if (ca >= 0 && cb >= 0 && ca < cw && cb < cw && cWalk[ca * cw + cb]) {
                            best = ca * cw + cb;
                            break;
                        }
                    }
            if (best < 0)
                return dist;
            start = best;
        }
        // bucket queue (Dial): edge costs are at most 56 (a wet diagonal on a 2-block cell = 2 * 28), so 57 buckets
        final int buckets = 64;
        int[] head = new int[buckets];
        java.util.Arrays.fill(head, -1);
        int cap = n * 2;
        int[] poolCell = new int[cap], poolNext = new int[cap];
        int pool = 0;
        dist[start] = 0;
        poolCell[pool] = start;
        poolNext[pool] = -1;
        head[0] = pool++;
        int pending = 1;
        for (int d = 0; pending > 0; d++) {
            int bk = d & (buckets - 1);
            while (head[bk] >= 0) {
                int node = head[bk];
                head[bk] = poolNext[node];
                pending--;
                int c = poolCell[node];
                if (dist[c] != d)
                    continue;   // a stale entry: the cell was reached cheaper
                int a = c / cw, b = c % cw;
                byte e = cEdge[c];
                for (int k = 0; k < 8; k++) {
                    int na, nb;
                    boolean diag = k >= 4;
                    if (!diag) {
                        if ((e & (1 << k)) == 0)
                            continue;
                        na = a + (k == 0 ? 1 : k == 1 ? -1 : 0);
                        nb = b + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    } else {
                        // a diagonal needs both of its corner moves (no cutting a cliff's corner)
                        int ex = (k == 4 || k == 5) ? 1 : 2, ez = (k == 4 || k == 6) ? 4 : 8;
                        if ((e & ex) == 0 || (e & ez) == 0)
                            continue;
                        na = a + (ex == 1 ? 1 : -1);
                        nb = b + (ez == 4 ? 1 : -1);
                        int via1 = na * cw + b, via2 = a * cw + nb;
                        if ((cEdge[via1] & ez) == 0 || (cEdge[via2] & ex) == 0)
                            continue;
                    }
                    int ni = na * cw + nb;
                    int cost = (diag ? 28 : 20) * (cWet[ni] || cWet[c] ? 2 : 1);   // 2 blocks a coarse cell
                    int nd = d + cost;
                    if (nd < dist[ni]) {
                        dist[ni] = nd;
                        if (pool == cap) {
                            cap *= 2;
                            poolCell = java.util.Arrays.copyOf(poolCell, cap);
                            poolNext = java.util.Arrays.copyOf(poolNext, cap);
                        }
                        int nbk = nd & (buckets - 1);
                        poolCell[pool] = ni;
                        poolNext[pool] = head[nbk];
                        head[nbk] = pool++;
                        pending++;
                    }
                }
            }
        }
        return dist;
    }

    /** A distance field's value at a column, in blocks (Double.POSITIVE_INFINITY where unreached). */
    double blocks(int[] field, double dx, double dz) {
        int d = field[cell((int) Math.round(dx), (int) Math.round(dz))];
        return d == UNREACHED ? Double.POSITIVE_INFINITY : d / 10.0;
    }
}
