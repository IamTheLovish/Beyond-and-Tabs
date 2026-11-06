package com.solegendary.reignofnether.environment.light;

/**
 * How dark a leaf block makes the faces round it in smooth lighting (ambient occlusion) - the one entry every tree
 * crown's light goes through, with or without our shader pack (lovish, Nov 6: "Crowns too dark: fix lighting").
 * <p>
 * Vanilla 1.20.1 ({@code BlockBehaviour.getShadeBrightness}) gives every block with a full collision box an AO shade of
 * {@link #SOLID_SHADE} and the rest 1.0; leaves have a full box, so a leaf neighbour occludes like stone. Smooth lighting
 * (vanilla's {@code AmbientOcclusionFace} and Embeddium's {@code LightDataAccess} alike) averages four such shades per
 * face corner, so a leaf face between other leaves - every face seen through a crown's holes and at its steps - comes
 * out at ~0.2 of the light: the crowns read near black against the open lawn (top faces, no AO). A leaf block is mostly
 * air between its leaves; it shades its neighbours by {@link #LEAF_SHADE} instead. The shader pack takes the same
 * vertex AO for its sky fill (lit.fsh, separateAo), so one value serves both looks.
 * <p>
 * Applied by {@code mixin.LeafShadeMixin} to every {@code LeavesBlock} that is see-through ({@link #porous}); the
 * mod's decayable wart blocks are leaves to the game but solid wart to the eye and keep the vanilla shade. Only the
 * renderer reads this value: no gameplay, light level or collision changes. The eyes sweep (EyesDirector,
 * -Dbat.eyes.leafAo) sets {@link #setEyesShade} to photograph several values in one run.
 */
public final class LeafLight {
    private LeafLight() { }

    /** Vanilla's AO shade of a block with a full collision box (and, before this, of a leaf block). */
    public static final float SOLID_SHADE = 0.2F;

    /** A leaf block's AO shade as a neighbour: see the class comment and crown-lighting notes for how it was chosen. */
    public static final float LEAF_SHADE = SOLID_SHADE;

    private static volatile float eyesShade = Float.NaN;

    /** The shade a see-through leaf block gives its neighbours now (the eyes sweep's value while it runs). */
    public static float leafShade() {
        float s = eyesShade;
        return Float.isNaN(s) ? LEAF_SHADE : s;
    }

    /** The eyes sweep only: a value to photograph, or NaN for the shipped one. The caller re-meshes the world. */
    public static void setEyesShade(float shade) {
        eyesShade = shade;
    }

    /**
     * The AO shade a block gives the faces round it: vanilla's rule ({@code fullCollision ? 0.2 : 1.0}) for every
     * block but a see-through leaf block.
     */
    public static float shadeOf(boolean porousLeaf, boolean fullCollision) {
        if (porousLeaf)
            return leafShade();
        return fullCollision ? SOLID_SHADE : 1.0F;
    }

    /**
     * Smooth lighting's light at one face corner: the mean of the shades of the block in front of the face, the two
     * edge neighbours beside it in the face's plane and the corner block between them (vanilla's
     * {@code AmbientOcclusionFace}: (f0 + f1 + f2 + f3) / 4, the corner taken only when an edge neighbour lets it be
     * seen). {@code cornerSeen} false = both edges opaque, the corner counts as the edge's shade.
     */
    public static float cornerAo(float front, float edgeA, float edgeB, float corner, boolean cornerSeen) {
        float c = cornerSeen ? corner : edgeA;
        return (front + edgeA + edgeB + c) * 0.25F;
    }
}
