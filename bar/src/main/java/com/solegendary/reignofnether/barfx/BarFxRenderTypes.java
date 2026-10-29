package com.solegendary.reignofnether.barfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Render types for the battle effects: untextured coloured quads (BarFx) and, since the vfx foundation (Oct 22), the
 * same three blends textured with OUR atlas (tools/gen_vfx_textures.py -> textures/vfx/vfx_atlas.png) for the
 * data-driven effects (VfxClient). Every state is a vanilla one (position_color; for the atlas the eyes and the
 * entity-translucent states), so Oculus / Iris map them to the pack's own programs - never our own shader.
 * One atlas = one texture bind = one buffer per blend for the whole effects pass. Client only.
 */
@OnlyIn(Dist.CLIENT)
public final class BarFxRenderTypes extends RenderType {
    private BarFxRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a, Runnable r) {
        super(n, f, m, b, c, s, a, r);
    }

    /** Debris chunks: opaque, depth-writing. */
    public static final RenderType SOLID = create("reignofnether_barfx_solid", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));

    /** Smoke and scorch marks: alpha blended, no depth write. */
    public static final RenderType TRANSLUCENT = create("reignofnether_barfx_translucent", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));

    /** Flashes, fire, sparks, beams, shockwaves: added on top of what is behind (reads as light). */
    public static final RenderType ADDITIVE = create("reignofnether_barfx_additive", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).createCompositeState(false));

    /** The vfx atlas (VfxAtlas has its tiles). Mipmapped (MipmappedTextures): rings spin, and a 512 px glyph band
     *  read at 36 px per block would shimmer without mips. */
    public static final ResourceLocation VFX_ATLAS =
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/vfx/vfx_atlas.png");

    static {
        com.solegendary.reignofnether.unit.modelling.renderers.MipmappedTextures.register(VFX_ATLAS);
    }

    /**
     * Sigils, rings, beams, sparks, glints, embers, light pools: added on top (reads as light). Vanilla's "eyes" state
     * (NEW_ENTITY, rendertype_eyes, ONE + ONE) - what spider and enderman eyes use - because shader packs map it to
     * their emissive program (gbuffers_spidereyes): the first eyes run under Complementary (39863847091) drew our
     * position_color_tex quads as flat colour squares in its textured program. ONE + ONE ignores alpha, so the atlas is
     * premultiplied (RGB = mask) and VfxClient premultiplies the vertex colour by the layer's alpha.
     */
    public static final RenderType VFX_ADDITIVE = create("reignofnether_vfx_additive", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(VFX_ATLAS, true, true)).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY).createCompositeState(false));

    /**
     * The light pass while a shader pack is on: vanilla's entity-translucent-emissive state (NEW_ENTITY, alpha
     * blended, full bright). Eyes run 39873260211 (Oculus + Complementary) logged 617 quads drawn through the eyes
     * state and showed none of them, while the entity programs draw our ground scars; the pack's bloom makes the
     * emissive layers glow. Not premultiplied in the vertex colour (the alpha does the blending).
     */
    public static final RenderType VFX_EMISSIVE = create("reignofnether_vfx_emissive", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1 << 18, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(VFX_ATLAS, true, true)).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));

    /**
     * Smoke, soot, heat veil: alpha blended and lit by the world (a smoke puff at night is dark) - vanilla's
     * entity-translucent state, the one the ground scars use under shader packs (worldmemory/ScarRenderTypes).
     */
    public static final RenderType VFX_TRANSLUCENT = create("reignofnether_vfx_translucent", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(VFX_ATLAS, true, true)).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));
}
