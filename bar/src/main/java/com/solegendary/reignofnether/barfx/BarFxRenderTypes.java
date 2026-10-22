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
 * data-driven effects (VfxClient). Every state is a vanilla one (position_color / position_color_tex shaders), so
 * Oculus / Iris map them to the pack's own programs (gbuffers_basic / gbuffers_textured) - never our own shader.
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

    /** Vanilla's position_color_tex core shader (Oculus swaps in the pack's textured program for it). */
    private static final ShaderStateShard VFX_SHADER =
            new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionColorTexShader);

    /** Sigils, rings, beams, sparks, glints, embers, light pools: added on top (reads as light). */
    public static final RenderType VFX_ADDITIVE = create("reignofnether_vfx_additive", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(VFX_SHADER)
                    .setTextureState(new TextureStateShard(VFX_ATLAS, true, true)).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).createCompositeState(false));

    /** Smoke, soot, heat shimmer: alpha blended (darkens / veils instead of glowing). */
    public static final RenderType VFX_TRANSLUCENT = create("reignofnether_vfx_translucent", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(VFX_SHADER)
                    .setTextureState(new TextureStateShard(VFX_ATLAS, true, true)).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));
}
