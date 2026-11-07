package com.solegendary.reignofnether.barfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Coloured-quad render types for the battle effects (untextured, plus the breath's flame atlas). Client only. */
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

    /**
     * Lighten (GL_MAX): each channel the brighter of what is there and the quad's colour. ConstructionFx's ghost (round
     * 15 critic: blended over, the ghost of the courses still to come tinted the laid white wall under it pale blue -
     * (157, 166, 184), "the building reads as 100% ghost"). A dark slate-blue lightened onto the scene leaves anything
     * brighter than it - the laid white stone - exactly as it is, and lifts only the darker ground (grass, shadow) toward
     * blue. Idempotent: a pixel behind two ghost faces is the same as behind one.
     */
    private static final TransparencyStateShard LIGHTEN_TRANSPARENCY = new TransparencyStateShard(
            "reignofnether_barfx_lighten", () -> {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.blendEquation(org.lwjgl.opengl.GL14.GL_MAX);
                com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                        com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            }, () -> {
                com.mojang.blaze3d.systems.RenderSystem.blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            });

    /** The construction ghost: lightened (see LIGHTEN_TRANSPARENCY), depth-tested, no depth write. */
    public static final RenderType LIGHTEN = create("reignofnether_barfx_lighten", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTEN_TRANSPARENCY).createCompositeState(false));

    /**
     * A depth-only prepass (ConstructionFx's ghost): the volume's nearest surface goes into the depth buffer, so the
     * normal-blended tint after it (LEQUAL, the same quads) lands exactly once a pixel. Round 13 critic: without it a
     * pixel behind two ghost faces was blended twice; round 16 critic: the lightened ghost alone left the upper courses
     * invisible over the laid white wall, so a blended tint is back on top of it.
     */
    public static final RenderType DEPTH_ONLY = create("reignofnether_barfx_depth_only", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(DEPTH_WRITE)
                    .setTransparencyState(NO_TRANSPARENCY).createCompositeState(false));

    /** Alpha blended with NO depth test: a line that must read through whatever stands in front of it (the occluded
     *  stretches of ConstructionFx's build front, at a lower alpha - round 16 critic). */
    public static final RenderType TRANSLUCENT_SEE_THROUGH = create("reignofnether_barfx_translucent_see_through",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));

    /** Flashes, fire, sparks, beams, shockwaves: added on top of what is behind (reads as light). */
    public static final RenderType ADDITIVE = create("reignofnether_barfx_additive", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).createCompositeState(false));

    /** The breath's flame atlas (tools/breath_sprites.py): 4 x 2 frames, tongues 0-5 along +u, puffs 6-7. */
    public static final ResourceLocation FLAME_ATLAS = new ResourceLocation(com.solegendary.reignofnether.ReignOfNether.MOD_ID,
            "textures/fx/breath_flame.png");

    /** Breath flames with a texture, alpha blended (a body of colour). */
    public static final RenderType FLAME_BLEND = create("reignofnether_barfx_flame_blend", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER).setTextureState(new TextureStateShard(FLAME_ATLAS, false, false))
                    .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));

    /** Breath flames with a texture, added as light (the hot core). */
    public static final RenderType FLAME_ADD = create("reignofnether_barfx_flame_add", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER).setTextureState(new TextureStateShard(FLAME_ATLAS, false, false))
                    .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).createCompositeState(false));
}
