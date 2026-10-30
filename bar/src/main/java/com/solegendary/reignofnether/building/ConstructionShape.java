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
    /** The most outline boxes a building draws (12 streaks each; ~20 sites rise at once in a 4v4). */
    public static final int MAX_OUTLINE_BOXES = 8;
    /**
     * How far the ring stands off the layer's own footprint (x its half-diagonal). 1.18 -> 1.10 after the first stills
     * (eyes 41003840710): a fresh critic read the wider ring as a halo round the site, not a band at the build height.
     */
    public static final float RING_STANDOFF = 1.10f;

    private final int minY;
    private final int[] perLayer;      // blocks in each layer, bottom up
    private final float[] halfDiag;    // each layer's footprint half-diagonal (blocks, from its centre)
    private final int total;
    private final float cx, cz;
    /** The outline's boxes, bottom up: one per run of layers sharing a footprint, {x0, x1, z0, z1, y0, y1} each. */
    private final float[] boxes;
    /** Each layer's own footprint box {x0, x1, z0, z1} (block edges); an empty layer takes the one below. */
    private final float[] layerBoxes;

    private ConstructionShape(int minY, int[] perLayer, float[] halfDiag, int total, float cx, float cz, float[] boxes,
                              float[] layerBoxes) {
        this.minY = minY;
        this.perLayer = perLayer;
        this.halfDiag = halfDiag;
        this.total = total;
        this.cx = cx;
        this.cz = cz;
        this.boxes = boxes;
        this.layerBoxes = layerBoxes;
    }

    /** @param blocks absolute block positions as {x, y, z} */
    public static ConstructionShape of(List<int[]> blocks) {
        if (blocks.isEmpty())
            return new ConstructionShape(0, new int[0], new float[0], 0, 0, 0, new float[0], new float[0]);
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
        // the outline: a layer within a block of the current box on every side joins it and the box grows to cover it
        // (code review Oct 30: identical-only merging gave a pyramid or a round tower one box per layer - ~3k vertices
        // a site and a staircase of lines); empty layers (a gap in a reworked design) end a run
        float[] runs = new float[6 * layers];
        int nRuns = 0;
        for (int l = 0; l < layers; l++) {
            if (count[l] == 0)
                continue;
            int o = (nRuns - 1) * 6;
            boolean joins = nRuns > 0 && runs[o + 5] == minY + l
                    && Math.abs(runs[o] - lx0[l]) <= 1 && Math.abs(runs[o + 1] - (lx1[l] + 1)) <= 1
                    && Math.abs(runs[o + 2] - lz0[l]) <= 1 && Math.abs(runs[o + 3] - (lz1[l] + 1)) <= 1;
            if (joins) {
                runs[o] = Math.min(runs[o], lx0[l]); runs[o + 1] = Math.max(runs[o + 1], lx1[l] + 1);
                runs[o + 2] = Math.min(runs[o + 2], lz0[l]); runs[o + 3] = Math.max(runs[o + 3], lz1[l] + 1);
                runs[o + 5] = minY + l + 1;
                continue;
            }
            o = nRuns++ * 6;
            runs[o] = lx0[l]; runs[o + 1] = lx1[l] + 1; runs[o + 2] = lz0[l]; runs[o + 3] = lz1[l] + 1;
            runs[o + 4] = minY + l; runs[o + 5] = minY + l + 1;
        }
        // the cap: merge the touching neighbour pair with the least combined height until few enough remain
        while (nRuns > MAX_OUTLINE_BOXES) {
            int best = -1;
            float bestH = Float.MAX_VALUE;
            for (int i = 0; i + 1 < nRuns; i++) {
                int a = i * 6, b = a + 6;
                if (runs[a + 5] != runs[b + 4])
                    continue;   // a gap between them: never bridge it
                float h = runs[b + 5] - runs[a + 4];
                if (h < bestH) { bestH = h; best = i; }
            }
            if (best < 0)
                break;
            int a = best * 6, b = a + 6;
            runs[a] = Math.min(runs[a], runs[b]); runs[a + 1] = Math.max(runs[a + 1], runs[b + 1]);
            runs[a + 2] = Math.min(runs[a + 2], runs[b + 2]); runs[a + 3] = Math.max(runs[a + 3], runs[b + 3]);
            runs[a + 5] = runs[b + 5];
            System.arraycopy(runs, b + 6, runs, b, (nRuns - best - 2) * 6);
            nRuns--;
        }
        float[] lb = new float[4 * layers];
        for (int l = 0; l < layers; l++) {
            if (count[l] == 0) {
                if (l > 0)
                    System.arraycopy(lb, 4 * (l - 1), lb, 4 * l, 4);
                continue;
            }
            lb[4 * l] = lx0[l]; lb[4 * l + 1] = lx1[l] + 1; lb[4 * l + 2] = lz0[l]; lb[4 * l + 3] = lz1[l] + 1;
        }
        return new ConstructionShape(minY, count, reach, blocks.size(), cx, cz, java.util.Arrays.copyOf(runs, 6 * nRuns), lb);
    }

    /**
     * The footprint {x0, x1, z0, z1} of the layer the build front is in (clamped to the first / last layer) - where
     * the cut line is drawn. Allocation-free.
     */
    public void layerBox(float frontY, float[] out) {
        int layers = perLayer.length;
        if (layers == 0) {
            java.util.Arrays.fill(out, 0, 4, 0f);
            return;
        }
        int l = Math.max(0, Math.min(layers - 1, (int) Math.floor(frontY - minY)));
        System.arraycopy(layerBoxes, 4 * l, out, 0, 4);
    }

    /**
     * A point a fraction {@code u} (wrapping) of the way round a footprint {x0, x1, z0, z1}, walked from (x0, z0)
     * along +x, +z, -x, -z, written to {@code out} as {x, z, nx, nz} with the outward normal of that side. The White
     * Angel's tools walk the cut line with it (round 4 critic: on the ring's circle their blows landed on the lawn).
     * Allocation-free.
     */
    public static void perimeterPoint(float[] box, float u, float[] out) {
        float x0 = box[0], x1 = box[1], z0 = box[2], z1 = box[3];
        float w = Math.max(0, x1 - x0), d = Math.max(0, z1 - z0), per = 2 * (w + d);
        float f = u - (float) Math.floor(u);
        float s = f * per;
        if (s < w || per <= 0) {
            out[0] = x0 + s; out[1] = z0; out[2] = 0; out[3] = -1;
        } else if ((s -= w) < d) {
            out[0] = x1; out[1] = z0 + s; out[2] = 1; out[3] = 0;
        } else if ((s -= d) < w) {
            out[0] = x1 - s; out[1] = z1; out[2] = 0; out[3] = 1;
        } else {
            s -= w;
            out[0] = x0; out[1] = z1 - s; out[2] = -1; out[3] = 0;
        }
    }

    /** Seconds of a tool's beat: moving to the next spot, winding up, the blow; the rest of the cycle is the recoil. */
    public static final float BEAT_MOVE = 0.2f, BEAT_WIND = 0.15f, BEAT_STRIKE = 0.1f, BEAT_CYCLE = 0.9f;

    /** The length of {@link #ringPoint}'s rounded rectangle round {@code box} at {@code pad}. */
    public static float ringLength(float[] box, float pad) {
        float w = Math.max(0, box[1] - box[0]), d = Math.max(0, box[3] - box[2]);
        return 2 * (w + d) + (float) (2 * Math.PI) * Math.max(0, pad);
    }

    /**
     * A point a fraction {@code u} (wrapping) round the footprint {x0, x1, z0, z1} grown by {@code pad} on every side
     * with rounded corners (radius pad), written to {@code out} as {x, z, nx, nz} with the outward normal. Walked as
     * {@link #perimeterPoint}: from the -z side's start along +x, +z, -x, -z, each side followed by its corner arc. The
     * White Angel's cloud ring hugs the layer on it (round 5 critic: a circle through a long building's corners stood
     * far off its long walls and read as a lens-flare halo). Allocation-free.
     */
    public static void ringPoint(float[] box, float pad, float u, float[] out) {
        float x0 = box[0], x1 = box[1], z0 = box[2], z1 = box[3];
        float w = Math.max(0, x1 - x0), d = Math.max(0, z1 - z0), p = Math.max(0, pad);
        float arc = (float) (Math.PI / 2) * p, per = 2 * (w + d) + 4 * arc;
        if (per <= 0) {
            out[0] = x0; out[1] = z0; out[2] = 0; out[3] = -1;
            return;
        }
        float s = (u - (float) Math.floor(u)) * per;
        // four sides, each with its corner after it: side start, direction, normal; the corner's centre and start angle
        for (int side = 0; side < 4; side++) {
            float len = (side & 1) == 0 ? w : d;
            float nx = side == 1 ? 1 : side == 3 ? -1 : 0, nz = side == 0 ? -1 : side == 2 ? 1 : 0;
            float sx = side == 0 || side == 3 ? x0 : x1, sz = side < 2 ? z0 : z1;   // where the side starts (on the box)
            float tx = side == 0 ? 1 : side == 2 ? -1 : 0, tz = side == 1 ? 1 : side == 3 ? -1 : 0;
            if (s < len || side == 3 && arc <= 0 && s <= len + 1e-4f) {
                out[0] = sx + tx * s + nx * p; out[1] = sz + tz * s + nz * p; out[2] = nx; out[3] = nz;
                return;
            }
            s -= len;
            if (s < arc || side == 3) {
                // round the corner at the side's end: from this side's normal toward the next side's
                double a0 = Math.atan2(nz, nx), a = a0 + (arc > 0 ? Math.min(s, arc) / p : 0);
                float cx = sx + tx * len, cz = sz + tz * len;
                float ox = (float) Math.cos(a), oz = (float) Math.sin(a);
                out[0] = cx + ox * p; out[1] = cz + oz * p; out[2] = ox; out[3] = oz;
                return;
            }
            s -= arc;
        }
    }

    /**
     * Where a tool is in its beat at {@code t} seconds (the round 5 critic: the tools hovered - now each moves to a
     * spot on the front, winds up, strikes, recoils while its sparks fly, and moves on): written to {@code out} as
     * {cycle, move done 0..1, wound back 0..1, seconds since the last blow (-1 before the first)}. Allocation-free.
     */
    public static void strikeBeat(float t, float[] out) {
        float c = (float) Math.floor(t / BEAT_CYCLE), k = t - c * BEAT_CYCLE;
        float hit = BEAT_MOVE + BEAT_WIND + BEAT_STRIKE;
        out[0] = c;
        out[1] = Math.min(1f, k / BEAT_MOVE);
        if (k < BEAT_MOVE)
            out[2] = 0;
        else if (k < BEAT_MOVE + BEAT_WIND) {
            float e = (k - BEAT_MOVE) / BEAT_WIND;
            out[2] = 1 - (1 - e) * (1 - e);                       // winding up, easing out
        } else if (k < hit) {
            float e = (k - BEAT_MOVE - BEAT_WIND) / BEAT_STRIKE;
            out[2] = 1 - e * e;                                    // the blow, accelerating
        } else
            out[2] = 0;
        out[3] = k >= hit ? k - hit : c > 0 ? k + BEAT_CYCLE - hit : -1;
    }

    /** Layers from the bottom of the building to its top (some may be empty). */
    public int layers() {
        return perLayer.length;
    }

    /** The layer a world height falls in, clamped to the building. */
    public int layerOf(float y) {
        return Math.max(0, Math.min(perLayer.length - 1, (int) Math.floor(y - minY)));
    }

    /** The minimum y of the building: {@link #layerOf} is relative to it. */
    public int minY() {
        return minY;
    }

    /**
     * The build front from what actually stands in each layer ({@code placed[l]} blocks of layer l): the lowest layer
     * less than half placed, plus how far through it the builders are. A straggling lower layer (a jammed pool's
     * water) no longer holds the front down while the walls rise (round 3, eyes 41023134378).
     */
    public float frontFromLayers(int[] placed) {
        for (int l = 0; l < perLayer.length; l++) {
            int n = perLayer[l];
            if (n == 0)
                continue;
            float f = Math.min(1f, (float) placed[l] / n);
            if (f < 0.5f)
                return minY + l + f;
        }
        return minY + perLayer.length;
    }

    /** How many boxes {@link #outline} can write at most (size its buffer 6x this, once). */
    public int maxOutlineBoxes() {
        return boxes.length / 6;
    }

    /**
     * The unbuilt part's outline above the build front: each box still (partly) to come, its bottom cut at the front,
     * written to {@code out} as {x0, x1, z0, z1, y0, y1} (block edges). Allocation-free - ConstructionFx calls it each
     * frame. Returns the number of boxes written.
     */
    public int outline(float frontY, float[] out) {
        int n = 0;
        for (int i = 0; i < boxes.length; i += 6) {
            if (boxes[i + 5] <= frontY + 1e-4f)
                continue;
            int o = n++ * 6;
            System.arraycopy(boxes, i, out, o, 5);
            out[o + 4] = Math.max(boxes[i + 4], frontY);
            out[o + 5] = boxes[i + 5];
        }
        return n;
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
        return ringRadiusAt(frontY(placed));
    }

    /** {@link #ringRadius} for a front height. */
    public float ringRadiusAt(float front) {
        if (perLayer.length == 0)
            return MIN_RING;
        return Math.max(MIN_RING, halfDiag[layerOf(front)] * RING_STANDOFF);
    }
}
