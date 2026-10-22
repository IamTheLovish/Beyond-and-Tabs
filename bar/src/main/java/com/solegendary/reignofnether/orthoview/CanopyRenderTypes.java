package com.solegendary.reignofnether.orthoview;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Render types for seeing units under canopies (CanopySilhouettes, LeafFade). A RenderType subclass only to reach
 * vanilla's protected state shards, like MipRenderTypes. Client only.
 */
@OnlyIn(Dist.CLIENT)
public final class CanopyRenderTypes extends RenderType {
    private CanopyRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a,
                              Runnable r) {
        super(n, f, m, b, c, s, a, r);
    }

    /**
     * A unit's body in one flat colour, ONLY where something already in the depth buffer (a leaf, a wall) is in front of
     * it: depth test GREATER, no depth write. The texture is the unit's own, sampled only for its cut-outs (the shader
     * discards transparent texels), so the silhouette has the body's exact shape. Drawn into CanopySilhouettes' own
     * target, whose depth attachment is the main depth texture. The polygon offset pulls the redraw a hair toward the
     * camera, so a visible unit's own depth (equal to the redraw's, or a sub-pixel off under a pack's TAA jitter) never
     * passes as "behind something".
     */
    private static final Function<ResourceLocation, RenderType> SILHOUETTE = Util.memoize(texture ->
            create("reignofnether_unit_silhouette", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS,
                    256, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(CanopySilhouettes::silhouetteShader))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setDepthTestState(new DepthTestStateShard(">", GL11.GL_GREATER))
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLayeringState(POLYGON_OFFSET_LAYERING)
                            .createCompositeState(false)));

    /**
     * Every capsule silhouette of a frame, in one draw (CanopySilhouettes): camera-facing quads whose rounded shape,
     * fill and rim the fragment shader makes from a distance field. Same depth rules as {@link #SILHOUETTE}.
     */
    public static final RenderType CAPSULES = create("reignofnether_unit_capsules", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 16384, false, false,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(CanopySilhouettes::capsuleShader))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(new DepthTestStateShard(">", GL11.GL_GREATER))
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));

    /**
     * Leaves inside a fade hole (LeafFade), drawn per frame with per-vertex alpha. Vanilla's entity_translucent_cull in
     * name and shader, so a shader pack draws them with its own translucent-entity program (Oculus picks programs by the
     * shader), but on the block atlas WITH mipmaps (the 256x leaves would shimmer from the RTS camera without them) and
     * quad sorting on upload, so overlapping faded faces blend back to front.
     */
    public static final RenderType LEAF_FADE = create("entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1 << 18, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    public static RenderType silhouette(ResourceLocation texture) {
        return SILHOUETTE.apply(texture);
    }

    // ---- which texture a unit's render type samples --------------------------------------------------------------

    private static final Map<RenderType, ResourceLocation> TEXTURES = new IdentityHashMap<>();
    private static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "none");
    private static boolean reflectionBroken = false;
    private static Field stateField = null;     // CompositeRenderType.state, found by its type (no names: obfuscation)

    /**
     * The single texture a vanilla-style render type samples (entity_cutout, entity_translucent, our mip types...), or
     * null when it has none or several. Read once per render type by reflection - by field and method TYPE, never by
     * name, so it works the same under the dev names and the obfuscated ones - and cached. Render thread only.
     */
    public static ResourceLocation cutoutTexture(RenderType type) {
        ResourceLocation cached = TEXTURES.get(type);
        if (cached != null)
            return cached == NONE ? null : cached;
        ResourceLocation found = reflectionBroken ? null : readTexture(type);
        TEXTURES.put(type, found == null ? NONE : found);
        return found;
    }

    private static ResourceLocation readTexture(RenderType type) {
        try {
            if (stateField == null || !stateField.getDeclaringClass().isInstance(type)) {
                Field f = findField(type.getClass(), CompositeState.class);
                if (f == null)
                    return null;   // not a composite type (a mod's own RenderType subclass): no silhouette for it
                stateField = f;
            }
            Object state = stateField.get(type);
            if (state == null)
                return null;
            for (Field f : CompositeState.class.getDeclaredFields()) {
                if (!RenderStateShard.class.isAssignableFrom(f.getType()))
                    continue;
                f.setAccessible(true);
                Object shard = f.get(state);
                if (shard == null)
                    continue;
                Method m = findOptionalGetter(shard.getClass());
                if (m == null)
                    continue;
                Object o = m.invoke(shard);
                if (o instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof ResourceLocation rl)
                    return rl;
                return null;
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionBroken = true;
            ReignOfNether.LOGGER.warn("Unit silhouettes: cannot read render type textures ({}); silhouettes are off",
                    e.toString());
            return null;
        }
    }

    private static Field findField(Class<?> c, Class<?> type) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields())
                if (f.getType() == type) {
                    f.setAccessible(true);
                    return f;
                }
        return null;
    }

    // EmptyTextureStateShard.cutoutTexture(): the only no-argument method returning an Optional in the shard classes
    private static Method findOptionalGetter(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            for (Method m : k.getDeclaredMethods())
                if (m.getParameterCount() == 0 && m.getReturnType() == Optional.class) {
                    m.setAccessible(true);
                    return m;
                }
        return null;
    }

    /** Whether silhouettes can work at all this session (the texture reflection has not failed). */
    public static boolean texturesReadable() {
        return !reflectionBroken;
    }
}
