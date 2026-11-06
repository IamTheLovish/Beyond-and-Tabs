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
