package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Function;

/**
 * Vanilla's entity_cutout_no_cull and eyes render types, except that the texture is sampled with its mipmaps
 * (TextureStateShard mipmap = true -> GL_NEAREST_MIPMAP_LINEAR: crisp within a level, blended between levels). Vanilla
 * re-applies a texture's filter every time a render type binds it, so mipmaps uploaded by {@link MipmappedTextures} only
 * show through these. Same names and shader shards as vanilla's, so shader packs (Iris / Oculus pick their programs by
 * the shader) treat our units exactly as before. Client only.
 */
@OnlyIn(Dist.CLIENT)
public final class MipRenderTypes extends RenderType {
    private MipRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a,
                           Runnable r) {
        super(n, f, m, b, c, s, a, r);
    }

    private static final Function<ResourceLocation, RenderType> BODY = Util.memoize(location ->
            create("entity_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
                    CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                            .setTextureState(new TextureStateShard(location, false, true))
                            .setTransparencyState(NO_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY).createCompositeState(true)));

    private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(location ->
            create("eyes", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                    CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(location, false, true))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    /** A generated body or its team mask: RenderType.entityCutoutNoCull with mipmaps. */
    public static RenderType body(ResourceLocation texture) {
        return BODY.apply(texture);
    }

    /** A generated body's light (_glow): RenderType.eyes with mipmaps - full-bright and additive. */
    public static RenderType glow(ResourceLocation texture) {
        return GLOW.apply(texture);
    }
}
