package com.solegendary.reignofnether.building;

import java.util.List;

/**
 * One building's living crowns as the renderer draws them (CrownRenderClientEvents): the leaf cells' own vertices, as
 * vanilla tessellated them once (light, ambient occlusion, face shade and tint already in the colour), and per frame
 * {@link #pose} moves them with {@link CrownMotion}: the lean and flutter of their crown, a cell growing in, the
 * crown's swell. Built once per capture; the frame allocates nothing.
 * <p>
 * Pure (no Minecraft types): the render types are the renderer's, by batch index; JUnit times {@link #pose} on a
 * crown the size of Heartwood Hall's (CrownCostBench).
 */
public final class CrownMesh {

    public final int vertexCount;
    /** Per vertex, as captured: position relative to the anchor (x, y, z), colour (ABGR as the vertex format packs
     *  it: the consumer gets r, g, b, a), texture u, v, packed light, normal (x, y, z bytes packed). */
    public final float[] pos;
    public final int[] color, light, normal;
    public final float[] uv;
    /** Per vertex: its cell, its crown's lean share ({@link CrownMotion#heightWeight}) and its flutter phase. */
    final int[] cell;
    final int[] crown;
    final float[] weight, phaseSin, phaseCos;
    /** Per batch (one render type each): the first vertex and the count; batch i is the renderer's render type i. */
    public final int[] batchStart, batchCount;

    /** Per cell: its middle, relative to the anchor; the client tick it appeared (NaN: already standing). */
    final float[] cellMid;
    public final float[] bornAt;
    /** Per crown: the rules' crown, and its pivot relative to the anchor (x, base y, z). */
    public final CrownMotion.Crown[] crowns;
    final float[] pivot;

    // per frame, per crown (reused)
    private final float[] lean, sag;

    /**
     * @param anchor  the world position everything is stored relative to (x, y, z: the placement's origin)
     * @param cellX   the cells (world), with their crown in {@code cellCrown} (CrownMotion.crowns' crownOf)
     * @param bornAt  per cell, the client tick it appeared (NaN: already standing) - the caller's array, kept across
     *                captures (a cell laid a tick ago keeps growing when the next cell makes a new capture)
     * @param pos     the captured vertices: pos (relative to the anchor), uv, color, light, normal, and each one's cell
     */
    public CrownMesh(int[] anchor, int[] cellX, int[] cellY, int[] cellZ, int[] cellCrown, List<CrownMotion.Crown> crownList,
                     float[] bornAt, float[] pos, float[] uv, int[] color, int[] light, int[] normal, int[] vertCell,
                     int[] batchStart, int[] batchCount) {
        this.vertexCount = color.length;
        this.pos = pos;
        this.uv = uv;
        this.color = color;
        this.light = light;
        this.normal = normal;
        this.cell = vertCell;
        this.batchStart = batchStart;
        this.batchCount = batchCount;
        this.crowns = crownList.toArray(new CrownMotion.Crown[0]);
        int cells = cellX.length;
        this.cellMid = new float[cells * 3];
        this.bornAt = bornAt;
        for (int k = 0; k < cells; k++) {
            cellMid[k * 3] = cellX[k] - anchor[0] + 0.5f;
            cellMid[k * 3 + 1] = cellY[k] - anchor[1] + 0.5f;
            cellMid[k * 3 + 2] = cellZ[k] - anchor[2] + 0.5f;
        }
        this.pivot = new float[crowns.length * 3];
        for (int c = 0; c < crowns.length; c++) {
            pivot[c * 3] = crowns[c].cx() - anchor[0];
            pivot[c * 3 + 1] = crowns[c].baseY() - anchor[1];
            pivot[c * 3 + 2] = crowns[c].cz() - anchor[2];
        }
        this.crown = new int[vertexCount];
        this.weight = new float[vertexCount];
        this.phaseSin = new float[vertexCount];
        this.phaseCos = new float[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            int c = cellCrown[vertCell[v]];
            crown[v] = c;
            double wx = anchor[0] + (double) pos[v * 3], wy = anchor[1] + (double) pos[v * 3 + 1],
                wz = anchor[2] + (double) pos[v * 3 + 2];
            weight[v] = CrownMotion.heightWeight((float) wy, crowns[c].baseY(), crowns[c].height());
            double ph = CrownMotion.flutterPhase(wx, wy, wz);
            phaseSin[v] = (float) Math.sin(ph);
            phaseCos[v] = (float) Math.cos(ph);
        }
        this.lean = new float[crowns.length];
        this.sag = new float[crowns.length];
    }

    /**
     * The vertices of this frame, camera-relative, into {@code out} (3 floats a vertex).
     *
     * @param off      the anchor minus the camera (x, y, z)
     * @param seconds  the clock (ClientTicks) in seconds: the flutter
     * @param windMul  the match wind multiplier
     * @param heading  WindField.heading at this time
     * @param gust     WindField.gust at each crown's pivot (one per crown)
     * @param now      the clock in ticks: cells growing in
     * @param swellSince ticks since the building completed or levelled up (large: settled)
     */
    public void pose(float offX, float offY, float offZ, double seconds, double windMul, double heading, float[] gust,
                     float now, float swellSince, float[] out) {
        float dirX = (float) Math.cos(heading), dirZ = (float) Math.sin(heading);
        double clock = CrownMotion.flutterClock(seconds);
        float cosT = (float) Math.cos(clock), sinT = (float) Math.sin(clock);
        float flut = CrownMotion.push(1f, windMul, 0, 1f, 1);            // windMul x FLUTTER, clamped like the lean
        float sw = CrownMotion.swellScale(swellSince);
        for (int c = 0; c < crowns.length; c++) {
            lean[c] = CrownMotion.push(crowns[c].sway(), windMul, gust[c], 1f, 0);
            sag[c] = CrownMotion.sag(crowns[c].sway(), windMul, gust[c], 1f);
        }
        for (int v = 0; v < vertexCount; v++) {
            int i = v * 3, c = crown[v];
            float w = weight[v];
            float push = w * (lean[c] + flut * (phaseSin[v] * cosT + phaseCos[v] * sinT));
            float x = pos[i] + dirX * push, y = pos[i + 1] + w * sag[c], z = pos[i + 2] + dirZ * push;
            float born = bornAt[cell[v]];
            if (born == born) {                     // not NaN: this cell is growing in (or has just grown)
                float g = CrownMotion.growScale(now - born);
                if (g != 1f) {
                    int m = cell[v] * 3;
                    x = cellMid[m] + (x - cellMid[m]) * g;
                    y = cellMid[m + 1] + (y - cellMid[m + 1]) * g;
                    z = cellMid[m + 2] + (z - cellMid[m + 2]) * g;
                }
            }
            if (sw != 1f) {
                int p = c * 3;
                x = pivot[p] + (x - pivot[p]) * sw;
                y = pivot[p + 1] + (y - pivot[p + 1]) * sw;
                z = pivot[p + 2] + (z - pivot[p + 2]) * sw;
            }
            out[i] = x + offX;
            out[i + 1] = y + offY;
            out[i + 2] = z + offZ;
        }
    }

    /** Settles every cell that has finished growing (so pose skips them); true while any is still growing. */
    public boolean settle(float now) {
        boolean growing = false;
        for (int k = 0; k < bornAt.length; k++) {
            float b = bornAt[k];
            if (b != b)
                continue;
            if (now - b >= CrownMotion.GROW_TICKS)
                bornAt[k] = Float.NaN;
            else
                growing = true;
        }
        return growing;
    }
}
