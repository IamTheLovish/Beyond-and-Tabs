package com.solegendary.reignofnether.environment.light;

import com.solegendary.reignofnether.postfx.ShaderPacks;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaf faces without a shader pack (LeafLight.FACE_LIFT): every leaf block's quads lose vanilla's solid-cube
 * directional shade and carry their own, softer one in the vertex colour - which both vanilla's block renderer and
 * Embeddium multiply in (Embeddium: ModelQuadUtil.mixARGBColors(colour provider, quad colour) x brightness), so the
 * foliage tint and the fog of war's tint still apply on top. Called from FogTintingBakedModel, which wraps every baked
 * model: one entry for every leaf of every tree, never per tree. With a shader pack in use the quads stay as they
 * are - the pack lights leaves itself and would read the colour as albedo.
 */
public final class LeafFaces {
    private LeafFaces() { }

    /** The quads as drawn: a leaf block's softened when the lift is on and no shader pack is in use; else as given. */
    public static List<BakedQuad> apply(BlockState state, List<BakedQuad> quads) {
        if (state == null || quads.isEmpty() || !LeafLight.porous(state.getBlock()))
            return quads;
        float lift = LeafLight.faceLift();
        if (lift <= 0F || ShaderPacks.inUse())
            return quads;
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            if (!q.isShade()) {
                out.add(q);
                continue;
            }
            float f = q.getDirection() == null ? 1F : LeafLight.leafFaceShade(q.getDirection().get3DDataValue(), lift);
            out.add(new BakedQuad(tinted(q.getVertices(), f), q.getTintIndex(), q.getDirection(), q.getSprite(), false));
        }
        return out;
    }

    /** STRIDE ints per vertex in the block vertex format (position 3, colour, uv 2, light, normal); colour at COLOUR. */
    static final int STRIDE = 8, COLOUR = 3;

    /** A copy of the vertex data with every vertex colour's RGB times f (ABGR, alpha kept). */
    static int[] tinted(int[] vertices, float f) {
        int[] v = vertices.clone();
        int stride = v.length / 4;
        for (int i = 0; i < 4; i++) {
            int at = i * stride + COLOUR;
            int c = v[at];
            int a = c >>> 24, b = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, r = c & 0xFF;
            v[at] = (a << 24) | (Math.round(b * f) << 16) | (Math.round(g * f) << 8) | Math.round(r * f);
        }
        return v;
    }
}
