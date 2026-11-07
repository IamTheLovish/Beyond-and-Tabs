package com.solegendary.reignofnether.environment.ground;

import java.util.function.Predicate;

/**
 * The pure rules of the floating clover layer (lovish, Nov 4, concept C: "a low thick clover carpet everywhere - round
 * leaves, lit tops, dark between"). About three lawn blocks in four get ONE extra cutout up-quad 0.6/16 above the grass
 * top, carrying one of eight mostly transparent 128x128 clover sheets ({@code block/grass_clover_layer_1..8}) turned by
 * 0, 90, 180 or 270 degrees - the lawn sheet underneath shows through the gaps, so the carpet gets real depth (its own
 * cutout edge over the soil and grass) and never repeats. The pick is a hash of the block position: deterministic (the
 * same block meshes the same every rebuild), independent between neighbours and of the 4x4 lawn cells. Studied from
 * the Optimum Realism pack (numbers only; nothing copied): ~75% of grass blocks, 0.5-0.8/16 lift, AO and shade off.
 * No Minecraft types here: CloverLayerRulesTest checks them; FloatingLayer bakes the quads.
 */
final class CloverLayerRules {

    /** Clover sheets the art provides. */
    static final int LAYERS = 8;
    /** Picks besides "none": every layer at every quarter turn. */
    static final int PICKS = LAYERS * 4;
    /** The layer's height in the block: 0.6/16 above the top, clear of z-fighting from the RTS camera's distance. */
    static final float HEIGHT = 16.6f / 16f;

    private static final String SPRITE = "block/grass_clover_layer_";

    private CloverLayerRules() {
    }

    /** The clover of the block at (x, y, z): 0 = none (one block in four), else 1..PICKS (see layer / rotation). */
    static int pick(int x, int y, int z) {
        long h = GroundMath.hash(x, y, z, 0x7C10E5L);
        if ((h & 3) == 0)
            return 0;
        return 1 + (int) ((h >>> 2) & (PICKS - 1));
    }

    /** The clover sheet (1..LAYERS) of a pick 1..PICKS. */
    static int layer(int pick) {
        return 1 + (pick - 1) / 4;
    }

    /** The quarter turns (0..3) of a pick 1..PICKS. */
    static int rotation(int pick) {
        return (pick - 1) % 4;
    }

    /** The atlas path of clover sheet k (namespace minecraft). */
    static String sprite(int layer) {
        return SPRITE + layer;
    }

    /** Whether the pack has all the clover sheets; one missing switches the layer off (it is safe to merge before the
     * art exists). */
    static boolean ready(Predicate<String> present) {
        for (int k = 1; k <= LAYERS; k++)
            if (!present.test(sprite(k)))
                return false;
        return true;
    }

    /**
     * The clover quad over the whole block at {@link #HEIGHT} in the BLOCK vertex format (position, colour, uv, light,
     * normal: 8 ints a vertex), in vanilla's up-face corner order (x0 z0, x0 z1, x1 z1, x1 z0: front face from above),
     * the sprite [u0, u1] x [v0, v1] turned by rotation quarter turns (corner i shows the sprite corner that corner
     * i + rotation shows unturned). White (the grass tint colours it), light 0 (the lighter fills it), normal up.
     */
    static int[] quad(int rotation, float u0, float u1, float v0, float v1) {
        return GroundMath.upQuad(HEIGHT, rotation, u0, u1, v0, v1);
    }
}
