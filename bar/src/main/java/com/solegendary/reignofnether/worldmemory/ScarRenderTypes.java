package com.solegendary.reignofnether.worldmemory;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.modelling.renderers.MipmappedTextures;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The ground decal mesh's render types: vanilla's entity-translucent state (shader, lightmap, overlay) with our own
 * textures, so Oculus / Iris pick their entity program for it exactly as they do for a unit (claude/world-that-
 * remembers.md 3.3: "the mesh must use a vanilla state, never our own ShaderInstance" - BarFx's POSITION_COLOR quads
 * are the precedent), plus:
 * <ul>
 *   <li>trilinear sampling with mipmaps (MipmappedTextures uploads the chain): a 128-texel-per-block ash texture read
 *       at 36 px per block from the RTS camera would shimmer otherwise;</li>
 *   <li>polygon offset (vanilla's crumbling-overlay layering): the quads sit on the real top faces and must never
 *       z-fight them, in the orthographic view's depth range or a first-person one;</li>
 *   <li>no depth write: a scar is paint, nothing behind it should be occluded by it.</li>
 * </ul>
 * Each texture is its own file sampled with world-space coordinates (REPEAT), so the 4x4-block tiles join without
 * seams and without atlas bleed in the mips. Client only.
 */
@OnlyIn(Dist.CLIENT)
public final class ScarRenderTypes extends RenderType {
    private ScarRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a, Runnable r) {
        super(n, f, m, b, c, s, a, r);
    }

    public static final ResourceLocation SCORCH_TEXTURE = new ResourceLocation(ReignOfNether.MOD_ID, "textures/decals/scorch.png");
    public static final ResourceLocation CRATER_TEXTURE = new ResourceLocation(ReignOfNether.MOD_ID, "textures/decals/crater.png");

    static {
        MipmappedTextures.register(SCORCH_TEXTURE);
        MipmappedTextures.register(CRATER_TEXTURE);
    }

    public static final RenderType SCORCH = decal("reignofnether_scar_scorch", SCORCH_TEXTURE);
    public static final RenderType CRATER = decal("reignofnether_scar_crater", CRATER_TEXTURE);

    private static RenderType decal(String name, ResourceLocation texture) {
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, true, true))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                .setLayeringState(POLYGON_OFFSET_LAYERING).setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
    }
}
