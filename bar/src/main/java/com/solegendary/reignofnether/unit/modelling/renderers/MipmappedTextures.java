package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.solegendary.reignofnether.util.TextureMips;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mipmaps for the generated unit bodies' textures (claude/unit-mipmaps.md). Vanilla uploads an entity texture as one
 * level and samples it nearest, so our 8x atlases shimmer from the RTS camera; the textures registered here get up to
 * {@link TextureMips#MAX_LEVELS} more levels (mixin/SimpleTextureMixin calls {@link #upload} in place of vanilla's
 * level-0-only upload) and are drawn through {@link MipRenderTypes}, whose texture state asks for mipmapped sampling.
 *
 * The texture object stays a vanilla SimpleTexture on purpose: Iris / Oculus attach the _n / _s PBR maps only to that
 * exact class. Only registered locations change - everything else loads exactly as before.
 */
@OnlyIn(Dist.CLIENT)
public final class MipmappedTextures {
    private MipmappedTextures() { }

    private static final Logger LOGGER = LogManager.getLogger("MipmappedTextures");
    private static final Set<ResourceLocation> REGISTERED = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> UPLOADED = ConcurrentHashMap.newKeySet();

    /** Ask for mipmaps on this texture; call before it is first drawn (renderers do it in their static fields). */
    public static void register(ResourceLocation location) {
        REGISTERED.add(location);
    }

    public static boolean has(ResourceLocation location) {
        return REGISTERED.contains(location);
    }

    /**
     * The eyes tour's check (it draws every generated body): how many registered textures got mips, and a WARN when
     * none did although mipmaps are on - the sign that SimpleTextureMixin no longer applies (it is require = 0).
     */
    public static void reportForEyes() {
        int wanted = wantedLevels();
        LOGGER.info("[Eyes] unit textures with mips: {} of {} registered (up to {} levels)", UPLOADED.size(),
                REGISTERED.size(), wanted);
        if (wanted > 0 && !REGISTERED.isEmpty() && UPLOADED.isEmpty())
            LOGGER.warn("[Eyes] no generated unit texture was uploaded with mips - SimpleTextureMixin did not apply?");
        else if (wanted > 0 && UPLOADED.size() < REGISTERED.size())
            // the tour draws every generated body with an owner, so each body, mask and glow should have loaded; one
            // missing was drawn before it was registered, or never drawn - either way it shimmers until a reload
            LOGGER.warn("[Eyes] unit textures without mips: {}", REGISTERED.stream().filter(l -> !UPLOADED.contains(l))
                    .map(ResourceLocation::toString).sorted().toList());
    }

    /** The player's Mipmap Levels video setting caps ours: 0 there means no mipmaps here either. */
    static int wantedLevels() {
        Minecraft mc = Minecraft.getInstance();
        int setting = mc.options == null ? TextureMips.MAX_LEVELS : mc.options.mipmapLevels().get();
        return Math.max(0, Math.min(TextureMips.MAX_LEVELS, setting));
    }

    /**
     * Uploads a registered texture with its mip chain into the texture object {@code id}, closing {@code image} as
     * vanilla's upload does. Returns false (and touches nothing) for any other texture, or when no level fits, so
     * vanilla's own upload runs. Called on the render thread.
     */
    public static boolean upload(ResourceLocation location, int id, NativeImage image, boolean blur, boolean clamp) {
        if (location == null || !REGISTERED.contains(location))
            return false;
        int w = image.getWidth(), h = image.getHeight();
        int levels = TextureMips.levels(w, h, wantedLevels());
        if (levels == 0)
            return false;
        // build the whole chain before touching GL, so a failure leaves vanilla's path intact
        int[][] chain = new int[levels + 1][];
        try {
            // getPixelRGBA (ABGR, what setPixelRGBA takes back) - NOT getPixelsRGBA, which converts to ARGB and would
            // swap red and blue in every mip level
            chain[0] = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    chain[0][y * w + x] = image.getPixelRGBA(x, y);
            for (int l = 1; l <= levels; l++)
                chain[l] = TextureMips.halve(chain[l - 1], w >> (l - 1), h >> (l - 1));
        } catch (RuntimeException e) {
            LOGGER.warn("Unit textures: could not build mips for {}, drawn without them", location, e);
            return false;
        }

        TextureUtil.prepareImage(id, levels, w, h);
        image.upload(0, 0, 0, 0, 0, w, h, blur, clamp, true, true);
        for (int l = 1; l <= levels; l++) {
            int lw = w >> l, lh = h >> l;
            NativeImage mip = new NativeImage(lw, lh, false);
            try {
                for (int y = 0; y < lh; y++)
                    for (int x = 0; x < lw; x++)
                        mip.setPixelRGBA(x, y, chain[l][y * lw + x]);
                mip.upload(l, 0, 0, 0, 0, lw, lh, blur, clamp, true, false);
            } finally {
                mip.close();
            }
        }
        UPLOADED.add(location);
        LOGGER.info("Unit textures: {} ({}x{}) uploaded with {} mip levels", location, w, h, levels);
        return true;
    }
}
