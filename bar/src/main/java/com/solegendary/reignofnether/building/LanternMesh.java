package com.solegendary.reignofnether.building;

/**
 * The lanterns hung under a building's living crowns (lovish's Verdant reference, Nov 4: lanterns on long chains from
 * the branches, a purple-pink glow): each a chain and a lantern as vanilla tessellated them once, swung each frame by
 * {@link CrownMotion#lanternSwing} about its hang point - the underside of a leaf cell - which itself rides the crown's
 * lean. Pure, like {@link CrownMesh}; the frame allocates nothing.
 */
public final class LanternMesh {

    public final int vertexCount;
    /** Per vertex, as captured (relative to the anchor), like CrownMesh's. */
    public final float[] pos, uv;
    public final int[] color, light, normal;
    final int[] lantern;
    public final int[] batchStart, batchCount;
    /** Per lantern: the hang point relative to the anchor (x, y, z), its crown, its lean share and its own phase. */
    final float[] hang;
    final int[] crown;
    final float[] weight, phase;
    // per frame, per lantern (reused)
    private final float[] push, sag, cosA, sinA, swellX, swellY, swellZ;

    public LanternMesh(float[] hang, int[] crown, float[] weight, float[] phase, float[] pos, float[] uv, int[] color,
                       int[] light, int[] normal, int[] vertLantern, int[] batchStart, int[] batchCount) {
        this.vertexCount = color.length;
        this.hang = hang;
        this.crown = crown;
        this.weight = weight;
        this.phase = phase;
        this.pos = pos;
        this.uv = uv;
        this.color = color;
        this.light = light;
        this.normal = normal;
        this.lantern = vertLantern;
        this.batchStart = batchStart;
        this.batchCount = batchCount;
        int n = phase.length;
        this.push = new float[n];
        this.sag = new float[n];
        this.cosA = new float[n];
        this.sinA = new float[n];
        this.swellX = new float[n];
        this.swellY = new float[n];
        this.swellZ = new float[n];
    }

    /**
     * The vertices of this frame, camera-relative, into {@code out} (3 floats a vertex): each lantern rotated about
     * the horizontal axis across the wind by its swing (downwind positive), at its hang point pushed with its crown,
     * and moved with that point as the crown swells about its pivot (as CrownMesh.pose moves the leaf it hangs from).
     *
     * @param sway  the crowns' sway (CrownMotion.Crown.sway, one per crown)
     * @param pivot the crowns' pivots relative to the anchor (CrownMesh.pivot: x, base y, z per crown)
     * @param swellSince ticks since the building completed or levelled up; the rest as CrownMesh.pose
     */
    public void pose(float offX, float offY, float offZ, double seconds, double windMul, double heading, float[] gust,
                     float[] sway, float[] pivot, float swellSince, float[] out) {
        float dirX = (float) Math.cos(heading), dirZ = (float) Math.sin(heading);
        float grow = CrownMotion.swellScale(swellSince) - 1f;
        for (int l = 0; l < phase.length; l++) {
            int c = crown[l], h = l * 3, p = c * 3;
            push[l] = CrownMotion.push(sway[c], windMul, gust[c], weight[l], 0);
            sag[l] = CrownMotion.sag(sway[c], windMul, gust[c], weight[l]);
            // the hang point, leaned, scaled about the crown's pivot by the swell: the whole lantern moves with it
            swellX[l] = (hang[h] + push[l] * dirX - pivot[p]) * grow;
            swellY[l] = (hang[h + 1] + sag[l] - pivot[p + 1]) * grow;
            swellZ[l] = (hang[h + 2] + push[l] * dirZ - pivot[p + 2]) * grow;
            double a = Math.toRadians(CrownMotion.lanternSwing(gust[c], windMul, seconds, phase[l]));
            cosA[l] = (float) Math.cos(a);
            sinA[l] = (float) Math.sin(a);
        }
        for (int v = 0; v < vertexCount; v++) {
            int l = lantern[v], h = l * 3;
            float cos = cosA[l], sin = sinA[l];
            int i = v * 3;
            float rx = pos[i] - hang[h], ry = pos[i + 1] - hang[h + 1], rz = pos[i + 2] - hang[h + 2];
            // Rodrigues about the axis (-dirZ, 0, dirX): a point straight below moves downwind for a positive swing
            float along = rx * dirX + rz * dirZ;           // the part across the axis, in the swing plane
            float acrossX = rx - along * dirX, acrossZ = rz - along * dirZ;
            float nAlong = along * cos - ry * sin;
            float nY = along * sin + ry * cos;
            out[i] = hang[h] + push[l] * dirX + acrossX + nAlong * dirX + swellX[l] + offX;
            out[i + 1] = hang[h + 1] + sag[l] + nY + swellY[l] + offY;
            out[i + 2] = hang[h + 2] + push[l] * dirZ + acrossZ + nAlong * dirZ + swellZ[l] + offZ;
        }
    }
}
