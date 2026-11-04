package com.solegendary.reignofnether.orthoview;

/**
 * Which block-face directions an orthographic camera can see, as Embeddium's per-section face mask.
 * <p>
 * Embeddium (0.3.31, "Use Block Face Culling", on by default) skips a whole direction of faces in a section when the
 * camera's POSITION is on the far side of that section's plane: a section more than 3 blocks south of the camera
 * draws no south-facing faces, one west of it no west-facing faces. That is right for a perspective camera, which
 * cannot see the south face of a block that is south of it. The RTS view is orthographic, though, and the camera
 * position (the spectator player, standing back from what it looks at; the ortho near plane reaches 500 blocks behind
 * it) says nothing about which faces face the viewer: every face whose normal points against the view direction is
 * visible, wherever it is. So in the RTS view the south and west faces of everything south / west of the camera
 * were dropped, and the eye looked through the block into the void - the hard, flat, 1-block-tall dark seams along
 * the block-sampler pad's south edge in the eyes stills (lp11 bat_eyes_15_block_sampler_1_rts / _rts_t5000 /
 * _rts_close, the pad standing one block above the lawn south of it), the missing west faces of the sampler cubes in
 * lp10, and the terrain zigzags; they jumped between stills because the camera position moves by whole sections.
 * <p>
 * The orthographic rule: a face is visible exactly when its normal points against the look direction. Taking the
 * mask from the direction keeps Embeddium's saving (half the face sets are skipped) and loses no visible face.
 * The bit order mirrors Embeddium's ModelQuadFacing (POS_X, POS_Y, POS_Z, NEG_X, NEG_Y, NEG_Z, UNASSIGNED).
 */
public final class OrthoFaceCulling {

    static final int POS_X = 0, POS_Y = 1, POS_Z = 2, NEG_X = 3, NEG_Y = 4, NEG_Z = 5, UNASSIGNED = 6;
    public static final int ALL = (1 << 7) - 1;

    // faces this close to edge-on are drawn anyway: they cover no pixels, and a direction a hair past edge-on (the
    // camera turning) must never open a seam
    static final double EDGE_ON = 0.05;

    private OrthoFaceCulling() {
    }

    /**
     * The face mask for an orthographic camera looking along (lx, ly, lz) (any length > 0): a direction is drawn
     * when its normal n has n . look < EDGE_ON (faces the camera, or edge-on). Quads with no direction (UNASSIGNED)
     * are always drawn, as in Embeddium.
     */
    public static int visibleFaces(double lx, double ly, double lz) {
        double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (!(len > 1e-9))
            return ALL;   // no direction (or NaN): cull nothing rather than guess
        lx /= len;
        ly /= len;
        lz /= len;
        int planes = 1 << UNASSIGNED;
        if (lx < EDGE_ON) planes |= 1 << POS_X;
        if (ly < EDGE_ON) planes |= 1 << POS_Y;
        if (lz < EDGE_ON) planes |= 1 << POS_Z;
        if (-lx < EDGE_ON) planes |= 1 << NEG_X;
        if (-ly < EDGE_ON) planes |= 1 << NEG_Y;
        if (-lz < EDGE_ON) planes |= 1 << NEG_Z;
        return planes;
    }
}
