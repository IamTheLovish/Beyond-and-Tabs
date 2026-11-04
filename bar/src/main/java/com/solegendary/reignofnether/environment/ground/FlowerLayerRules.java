package com.solegendary.reignofnether.environment.ground;

import java.util.function.Predicate;

/**
 * The pure rules of the floating flower layer (lovish, Nov 4, concept C: "flower specks", the meadow photo's daisies;
 * the round 62 critic: "flower DRIFTS: 6-12 dots ... pale yellow / white, inside a 1-2 block patch, ~1 drift per 12
 * blocks"; round 63's critic found "no white or yellow specks" - under the biome tint a baked speck turns green). So
 * the flowers are their own UNTINTED layer: about 6% of lawn blocks get ONE extra cutout up-quad at 16.7/16 (just over
 * the clover's 16.6/16) carrying one of eight mostly transparent sprites {@code block/grass_flower_layer_1..8}. Never
 * turned: the art may light its specks from the east like the sheets (round 64 brief: the engine's sun at t5000), and a
 * quarter turn would spin that light per block.
 * <p>
 * <b>Drifts, not salt:</b> flowers grow in clumps, and a rare detail scattered evenly is the easiest repeat to spot
 * (research, Nov 4). Which blocks flower follows a low-frequency field ({@link #field}): 0 over most of the lawn, 40-58%
 * inside round drift spots 1.5-2.5 blocks across, 2 or 3 drift seeds per 6 x 6 blocks (one per ~14 blocks of lawn),
 * each anywhere in its square, so the drifts never line up. A block flowers when an independent hash of its position
 * falls under the field there. Both hashes are deterministic (a block meshes the same on every rebuild), independent of
 * the clover pick and of the sheet cells. No Minecraft types here: FlowerLayerRulesTest checks them; FloatingLayer bakes
 * the quads.
 */
final class FlowerLayerRules {

    /** Flower sprites the art provides. */
    static final int LAYERS = 8;
    /** The layer's height: 0.7/16 above the top, 0.1/16 over the clover so the specks sit on the leaves. */
    static final float HEIGHT = 16.7f / 16f;

    /** Drift seeds live in squares of this many blocks; each holds 2 or 3 (one drift per 14.4 blocks on average). */
    static final int DRIFT_SQUARE = 6;
    /** A drift's outer radius in blocks (it fades to 0 there) and the share of it at full strength (the plateau). */
    static final double RADIUS_MIN = 0.75, RADIUS_MAX = 1.25, PLATEAU = 0.55;
    /** The flowering probability on a drift's plateau. */
    static final double PEAK_MIN = 0.40, PEAK_MAX = 0.58;

    private static final String SPRITE = "block/grass_flower_layer_";
    private static final long SALT_DRIFT = 0xD21F7L, SALT_PICK = 0xF10E55L;

    private FlowerLayerRules() {
    }

    /** The atlas path of flower sprite k (namespace minecraft). */
    static String sprite(int layer) {
        return SPRITE + layer;
    }

    /** Whether the pack has all the flower sprites; one missing switches the layer off (safe to merge before the art
     * exists). */
    static boolean ready(Predicate<String> present) {
        for (int k = 1; k <= LAYERS; k++)
            if (!present.test(sprite(k)))
                return false;
        return true;
    }

    /**
     * The probability that the block at column (x, z) flowers: the strongest drift over the block's centre - its peak
     * on the plateau (the inner PLATEAU of its radius), a smoothstep down to 0 at the radius, 0 beyond. Drifts are
     * seeded per DRIFT_SQUARE square and reach at most RADIUS_MAX (< one square) from their seed, so the 3 x 3 squares
     * round the block hold every drift that can touch it.
     */
    static double field(int x, int z) {
        double px = x + 0.5, pz = z + 0.5;
        int sx = Math.floorDiv(x, DRIFT_SQUARE), sz = Math.floorDiv(z, DRIFT_SQUARE);
        double best = 0;
        for (int i = sx - 1; i <= sx + 1; i++)
            for (int j = sz - 1; j <= sz + 1; j++) {
                long c = GroundMath.hash(i, j, SALT_DRIFT);
                int seeds = 2 + (int) (c & 1);
                for (int k = 1; k <= seeds; k++) {
                    long h = GroundMath.mix(c + k * GroundMath.KX);
                    double dx = (i + GroundMath.unit16(h, 0)) * DRIFT_SQUARE - px;
                    double dz = (j + GroundMath.unit16(h, 16)) * DRIFT_SQUARE - pz;
                    double r = RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * GroundMath.unit16(h, 32);
                    double d2 = dx * dx + dz * dz;
                    if (d2 >= r * r)
                        continue;
                    double d = Math.sqrt(d2), inner = r * PLATEAU;
                    double t = d <= inner ? 1 : (r - d) / (r - inner);
                    double p = (PEAK_MIN + (PEAK_MAX - PEAK_MIN) * GroundMath.unit16(h, 48)) * t * t * (3 - 2 * t);
                    if (p > best)
                        best = p;
                }
            }
        return best;
    }

    /** The flowers of the block at (x, y, z): 0 = none (about 94 blocks in 100), else the sprite 1..LAYERS. */
    static int pick(int x, int y, int z) {
        long h = GroundMath.hash(x, y, z, SALT_PICK);
        double u = (h >>> 11) * 0x1.0p-53;   // the top 53 bits; the sprite takes the low 3, independent of them
        if (u >= PEAK_MAX || u >= field(x, z))
            return 0;
        return 1 + (int) (h & (LAYERS - 1));
    }

    /** The flower quad over the whole block at {@link #HEIGHT}, unturned (u along +x, v along +z), white: the layer
     * is untinted, so white and yellow stay white and yellow (see GroundMath.upQuad for the format). */
    static int[] quad(float u0, float u1, float v0, float v1) {
        return GroundMath.upQuad(HEIGHT, 0, u0, u1, v0, v1);
    }
}
