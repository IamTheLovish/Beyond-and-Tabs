package com.solegendary.reignofnether.barfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Untextured coloured-quad render types for the battle effects. Client only. */
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
     * A depth-only prepass (ConstructionFx's ghost, round 13 critic: a pixel behind two ghost faces was blended twice,
     * ~0.45 over grass instead of 0.28): the volume's nearest surface goes into the depth buffer, so the TRANSLUCENT
     * colour pass after it (LEQUAL) blends each pixel exactly once.
     */
    public static final RenderType DEPTH_ONLY = create("reignofnether_barfx_depth_only", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(DEPTH_WRITE)
                    .setTransparencyState(NO_TRANSPARENCY).createCompositeState(false));

    /** Alpha blended and pulled forward with polygon offset: lines on a surface that must not sink into it (the build
     *  front, round 13 critic). */
    public static final RenderType TRANSLUCENT_OFFSET = create("reignofnether_barfx_translucent_offset",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));

    /** Flashes, fire, sparks, beams, shockwaves: added on top of what is behind (reads as light). */
    public static final RenderType ADDITIVE = create("reignofnether_barfx_additive", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).createCompositeState(false));
}
