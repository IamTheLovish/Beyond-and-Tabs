package com.solegendary.reignofnether.building;

import java.util.List;

/**
 * The shape a rising building's construction effects follow (claude/faction-construction-oct29.md). Pure - no
 * Minecraft types - so ConstructionShapeTest covers it; ConstructionFx reads it every frame.
 *
 * <p>Builders lay blocks strictly bottom-up (BuildingPlacement offers only the lowest unfinished layer), so with each
 * layer's block count the build front's height follows from the count placed. Built once per placement from that
 * placement's own blocks, so every building - including the reworked ones (lovish, Oct 29) - gets it with nothing
 * hand-placed per building.
 */
public final class ConstructionShape {

    /** The smallest ring (blocks): a spire's ring must still read as a ring. */
    public static final float MIN_RING = 1.6f;
    /** How far the ring stands off the layer's own footprint (x its half-diagonal). */
    public static final float RING_STANDOFF = 1.18f;

    private final int minY;
    private final int[] perLayer;      // blocks in each layer, bottom up
    private final float[] halfDiag;    // each layer's footprint half-diagonal (blocks, from its centre)
    private final int total;
    private final float cx, cz;

    private ConstructionShape(int minY, int[] perLayer, float[] halfDiag, int total, float cx, float cz) {
        this.minY = minY;
        this.perLayer = perLayer;
        this.halfDiag = halfDiag;
        this.total = total;
        this.cx = cx;
        this.cz = cz;
    }

    /** @param blocks absolute block positions as {x, y, z} */
    public static ConstructionShape of(List<int[]> blocks) {
        if (blocks.isEmpty())
            return new ConstructionShape(0, new int[0], new float[0], 0, 0, 0);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] b : blocks) {
            minX = Math.min(minX, b[0]); maxX = Math.max(maxX, b[0]);
            minY = Math.min(minY, b[1]); maxY = Math.max(maxY, b[1]);
            minZ = Math.min(minZ, b[2]); maxZ = Math.max(maxZ, b[2]);
        }
        int layers = maxY - minY + 1;
        int[] count = new int[layers];
        int[] lx0 = new int[layers], lx1 = new int[layers], lz0 = new int[layers], lz1 = new int[layers];
        java.util.Arrays.fill(lx0, Integer.MAX_VALUE); java.util.Arrays.fill(lz0, Integer.MAX_VALUE);
        java.util.Arrays.fill(lx1, Integer.MIN_VALUE); java.util.Arrays.fill(lz1, Integer.MIN_VALUE);
        for (int[] b : blocks) {
            int l = b[1] - minY;
            count[l]++;
            lx0[l] = Math.min(lx0[l], b[0]); lx1[l] = Math.max(lx1[l], b[0]);
            lz0[l] = Math.min(lz0[l], b[2]); lz1[l] = Math.max(lz1[l], b[2]);
        }
        // the ring centres on the whole footprint (it must not wander as the layers above step in); each layer's reach
        // is measured from that centre to its farthest block corner
        float cx = (minX + maxX + 1) / 2f, cz = (minZ + maxZ + 1) / 2f;
        float[] reach = new float[layers];
        for (int l = 0; l < layers; l++) {
            if (count[l] == 0) {
                reach[l] = l > 0 ? reach[l - 1] : 0;
                continue;
            }
            float dx = Math.max(Math.abs(lx0[l] - cx), Math.abs(lx1[l] + 1 - cx));
            float dz = Math.max(Math.abs(lz0[l] - cz), Math.abs(lz1[l] + 1 - cz));
            reach[l] = (float) Math.sqrt(dx * dx + dz * dz) * 0.92f;   // the corners are a block's diagonal past the walls
        }
        return new ConstructionShape(minY, count, reach, blocks.size(), cx, cz);
    }

    public int total() {
        return total;
    }

    public float centreX() {
        return cx;
    }

    public float centreZ() {
        return cz;
    }

    /** The build front's height (world y) with {@code placed} blocks laid: the lowest unfinished layer's bottom, plus how far through it the builders are. */
    public float frontY(int placed) {
        if (perLayer.length == 0)
            return minY;
        int left = Math.max(0, placed);
        for (int l = 0; l < perLayer.length; l++) {
            int n = perLayer[l];
            if (left < n)
                return minY + l + (n == 0 ? 0 : (float) left / n);
            left -= n;
        }
        return minY + perLayer.length;
    }

    /** The ring's radius round the layer being built: just wider than that layer's footprint, never under MIN_RING. */
    public float ringRadius(int placed) {
        if (perLayer.length == 0)
            return MIN_RING;
        int l = Math.min(perLayer.length - 1, (int) Math.floor(frontY(placed) - minY));
        return Math.max(MIN_RING, halfDiag[l] * RING_STANDOFF);
    }
}
