package com.solegendary.reignofnether.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The animated lobby backdrop (design/lobby-backdrop.md): "the moonlit cathedral and the crimson river" behind the
 * skirmish setup and multiplayer lobby screens. The layers are our own, painted from code by tools/gen_lobby_art.py;
 * this class only places and animates them.
 *
 * Layout: one artboard in A units (A = the cover-scaled screen height, x from the centre, y from the top, bottom
 * anchored), the same rects and river geometry as the generator. The framing layers and the foreground pop-outs are
 * pinned to the screen edges instead, so 16:9 and 21:9 both keep their framing.
 *
 * Depth (lovish: "give the castle an ability to be 3D ... so it pops out and comes to life"): every layer has a parallax
 * depth z (A of shift per unit of camera offset). The camera drifts on a slow lissajous (26 s x 19 s) and leans toward
 * the mouse; the cathedral is eight depth layers (chevet, nave, aisle, far tower, facade, gable, near tower, portal),
 * the ground is drawn as horizontal strips sheared by their own depth (so it reads as a plane receding to the horizon,
 * and the river, cascade and flowers ride it exactly), mist sheets lie between the cathedral's layers, light shafts
 * fall from the rose through them, and out-of-focus briars and a dead branch in the extreme foreground move fastest.
 *
 * Motion (lovish: "the castle is breathing with wind and water moving"): a 7 s breath swells the cathedral 0.6 % and
 * its rim light, glass, shafts and portal haze together, sends a wave of glass light outward and thickens the mist; a
 * gust crosses left to right every 9 s and everything wind-driven reads it at its own x; the cascade falls, glints flow
 * down the river, the moon's reflection shimmers, rings spread from the rocks, embers rise from the door; stars twinkle,
 * bats cross now and then. `splash_reduce_motion` freezes all of it (no drift, no parallax).
 *
 * Cost: about 150 textured quads a frame through the shared Tesselator, no allocation (static matrix, indexed loops,
 * time-derived state). The PNGs are read once from our own mod file and uploaded as plain GL textures (like SplashArt);
 * if any of them cannot be read the screens keep the vanilla background.
 */
public class LobbyBackdrop {

    // ---- shared with tools/gen_lobby_art.py
    private static final float ART_W = 2560f / 1100f;
    private static final float MOON_X = -0.27f, MOON_Y = 0.205f, MOON_R = 0.14f;
    private static final float FRAME_W = 0.66f;
    private static final float HORIZON = 0.64f;
    private static final float[][] RIVER = {   // x, y, half width
        {0.372f, 0.800f, 0.048f}, {0.330f, 0.812f, 0.064f}, {0.200f, 0.826f, 0.080f}, {0.020f, 0.846f, 0.098f},
        {-0.160f, 0.868f, 0.118f}, {-0.285f, 0.895f, 0.130f}, {-0.300f, 0.925f, 0.160f}, {-0.160f, 0.955f, 0.200f},
        {0.040f, 0.985f, 0.250f}, {0.170f, 1.030f, 0.320f}};
    /** the cascade: top x, top y, top half width, bottom x, bottom y, bottom half width */
    private static final float[] CASCADE = {0.300f, 0.688f, 0.016f, 0.372f, 0.802f, 0.042f};
    /** the cathedral breathes about the facade's foot */
    private static final float PIVOT_X = 0.30f, PIVOT_Y = 0.69f;
    // <gen:cath> written by tools/gen_lobby_art.py - do not edit by hand
    private static final String[] CATH = {"cath_back", "cath_nave", "cath_aisle", "cath_tfar", "cath_facade", "cath_gable", "cath_tnear", "cath_portal"};
    private static final float[][] CATH_RECT = {{-0.1546f, 0.2105f, 0.0729f, 0.6779f}, {-0.1041f, 0.1095f, 0.3636f, 0.6968f}, {-0.2020f, 0.1853f, 0.2214f, 0.6968f}, {0.3320f, 0.1663f, 0.4520f, 0.6937f}, {0.2182f, 0.2421f, 0.3762f, 0.6968f}, {0.2214f, 0.1663f, 0.3857f, 0.3905f}, {0.1013f, -0.0011f, 0.2466f, 0.7000f}, {0.2119f, 0.1947f, 0.4741f, 0.7411f}};
    private static final float[] CATH_Z = {0.0130f, 0.0170f, 0.0220f, 0.0250f, 0.0290f, 0.0320f, 0.0350f, 0.0400f};
    /** the rocks breaking the river's surface (x, y, r) - gen_lobby_art.river_rocks() */
    private static final float[][] RIVER_ROCKS = {{-0.1500f, 0.8750f, 0.0100f}, {-0.3530f, 0.9050f, 0.0130f}, {-0.1850f, 0.9300f, 0.0160f}, {-0.2200f, 0.9550f, 0.0180f}, {0.2150f, 0.9850f, 0.0240f}, {-0.0943f, 1.0000f, 0.0260f}};
    /** the rose window and the portal (artboard), where the light comes from */
    private static final float ROSE_X = 0.2981f, ROSE_Y = 0.3211f, DOOR_X = 0.3000f, DOOR_Y = 0.6735f;
    // </gen:cath>
    private static final float TAU = 6.2831855f;
    private static final float BREATH_S = 7f;    // the cathedral's slow inhale / exhale
    private static final float GUST_S = 9f;      // a gust crosses the scene left to right this often
    private static final float RIPPLE_K = 8f, RIPPLE_SPEED = 0.35f, STREAK_HW = 0.035f;
    private static final float CASCADE_TILES = 1.6f, CASCADE_SPEED = 0.9f;
    private static final float DRIFT_X_S = 26f, DRIFT_Y_S = 19f, DRIFT_X = 0.85f, DRIFT_Y = 0.55f;
    private static final float OVERSCAN = 1.05f;
    private static final float[][] STARS = {
        {-0.92f, 0.06f}, {-0.71f, 0.13f}, {-0.55f, 0.04f}, {-0.48f, 0.19f}, {-0.08f, 0.07f}, {0.03f, 0.16f},
        {0.62f, 0.12f}, {0.58f, 0.03f}, {0.77f, 0.09f}, {0.86f, 0.22f}, {1.02f, 0.05f}, {-1.05f, 0.20f},
        {0.69f, 0.27f}, {-0.83f, 0.27f}};

    // ---- textures
    private static final String DIR = "assets/" + ReignOfNether.MOD_ID + "/textures/gui/lobby/";
    private static final String[] BASE_FILES = {"sky", "moon", "glow", "sparkle", "clouds_far", "spires_far",
        "clouds_near", "shafts", "mist", "ground", "cascade", "ripples", "streak", "field_far", "field_near", "bats",
        "frame_left", "frame_right", "petal", "vignette", "grain", "mist_pool", "frame_left_tree", "frame_right_tree",
        "frame_left_vines", "frame_right_vines", "leaf", "ring", "fg_briar", "fg_thorns", "fg_branch"};
    private static final int SKY = 0, MOON = 1, GLOW = 2, SPARKLE = 3, CLOUDS_FAR = 4, SPIRES_FAR = 5, CLOUDS_NEAR = 6,
        SHAFTS = 7, MIST = 8, GROUND = 9, CASCADE_T = 10, RIPPLES = 11, STREAK = 12, FIELD_FAR = 13, FIELD_NEAR = 14,
        BATS = 15, FRAME_LEFT = 16, FRAME_RIGHT = 17, PETAL = 18, VIGNETTE = 19, GRAIN = 20, MIST_POOL = 21,
        TREE_LEFT = 22, TREE_RIGHT = 23, VINES_LEFT = 24, VINES_RIGHT = 25, LEAF = 26, RING = 27, FG_BRIAR = 28,
        FG_THORNS = 29, FG_BRANCH = 30;
    /** wrap mode per base texture: 0 clamp, 1 repeat, 2 clamp across / repeat along (the moon's reflection) */
    private static final int[] WRAP = {0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 2, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0};
    /** the cathedral's layers follow the base textures: per layer its stone, its moon rim and its glass glow */
    private static final int CATH0 = BASE_FILES.length;
    private static final int[] TEX = new int[BASE_FILES.length + CATH.length * 3];
    private static int state = 0;   // 0 not loaded yet, 1 ready, -1 failed (vanilla background)

    // ---- per-frame state (no allocation)
    private static final Matrix4f M = new Matrix4f();
    private static float parX = 0, parY = 0, camX = 0, camY = 0;
    private static long lastFrameMs = 0;
    private static float A, top, cx, w, h, gustFront;
    private static final int SLICES = 16, ROWS = 12, PETALS = 16, EMBERS = 12;

    /** Uploads the layers on first use (render thread only). False if the art is unavailable. */
    public static boolean ensureLoaded() {
        if (state != 0)
            return state > 0;
        if (!RenderSystem.isOnRenderThread())
            return false;
        int loaded = 0;
        try {
            for (int i = 0; i < TEX.length; i++) {
                String name;
                boolean optional = false;
                int wrap = 0;
                if (i < CATH0) {
                    name = BASE_FILES[i];
                    wrap = WRAP[i];
                } else {
                    int k = (i - CATH0) % 3;
                    name = CATH[(i - CATH0) / 3] + (k == 0 ? "" : k == 1 ? "_rim" : "_glow");
                    optional = k == 2;      // a layer without glass has no glow
                }
                NativeImage img;
                try (InputStream in = open(DIR + name + ".png")) {
                    img = NativeImage.read(in);
                } catch (FileNotFoundException e) {
                    if (!optional)
                        throw e;
                    TEX[i] = -1;
                    loaded = i + 1;
                    continue;
                }
                int iw = img.getWidth(), ih = img.getHeight();
                TEX[i] = TextureUtil.generateTextureId();
                loaded = i + 1;
                TextureUtil.prepareImage(TEX[i], iw, ih);
                // linear filtering, the CPU copy freed straight after the upload; clamped unless the layer scrolls
                img.upload(0, 0, 0, 0, 0, iw, ih, true, wrap == 0, false, true);
                if (wrap == 2) {
                    GlStateManager._bindTexture(TEX[i]);
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                }
            }
            state = 1;
        } catch (Throwable e) {
            ReignOfNether.LOGGER.warn("[LobbyBackdrop] lobby art unavailable, keeping the vanilla background", e);
            for (int i = 0; i < loaded; i++)
                if (TEX[i] > 0)
                    TextureUtil.releaseTextureId(TEX[i]);
            state = -1;
        }
        return state > 0;
    }

    private static InputStream open(String path) throws IOException {
        try {
            IModFileInfo info = ModList.get() == null ? null : ModList.get().getModFileById(ReignOfNether.MOD_ID);
            if (info != null) {
                Path p = info.getFile().findResource(path.split("/"));
                if (Files.exists(p))
                    return Files.newInputStream(p);
            }
        } catch (Throwable ignored) {
            // fall through to the class loader
        }
        InputStream in = LobbyBackdrop.class.getClassLoader().getResourceAsStream(path);
        if (in == null)
            throw new FileNotFoundException(path);
        return in;
    }

    private static boolean reduceMotion() {
        try {
            return ReignOfNetherClientConfigs.SPLASH_REDUCE_MOTION.get();
        } catch (Throwable e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- the scene

    /**
     * Draws the backdrop over the whole screen in place of Screen.renderBackground (which it falls back to when the
     * art is unavailable).
     */
    public static void render(Screen screen, GuiGraphics gg, int mouseX, int mouseY) {
        if (!ensureLoaded()) {
            screen.renderBackground(gg);
            return;
        }
        gg.flush();
        w = screen.width;
        h = screen.height;
        if (w <= 0 || h <= 0)
            return;
        long now = Util.getMillis();
        boolean motion = !reduceMotion();
        // a fixed, pleasant moment when frozen; otherwise seconds, wrapped hourly so floats stay precise
        float t = motion ? (now % 3_600_000L) / 1000f : 7f;
        float dt = Math.min(0.1f, Math.max(0f, (now - lastFrameMs) / 1000f));
        lastFrameMs = now;
        float tx = motion ? Mth.clamp(mouseX / w * 2f - 1f, -1f, 1f) : 0f;
        float ty = motion ? Mth.clamp(mouseY / h * 2f - 1f, -1f, 1f) : 0f;
        float k = 1f - (float) Math.exp(-dt * 2.5f);
        parX += (tx - parX) * k;
        parY += (ty - parY) * k;
        if (motion) {
            // the camera: a slow lissajous drift (the scene turns a little by itself) plus a lean toward the mouse
            camX = Mth.clamp(DRIFT_X * Mth.sin(t * TAU / DRIFT_X_S) + 0.5f * parX, -1f, 1f);
            camY = Mth.clamp(DRIFT_Y * Mth.sin(t * TAU / DRIFT_Y_S + 1.3f) + 0.5f * parY, -1f, 1f);
        } else {
            parX = parY = camX = camY = 0f;
        }
        draw(t, motion, gg.pose().last().pose());
    }

    /** The whole scene at time t for the current camera (shared by render). */
    private static void draw(float t, boolean motion, Matrix4f pose) {
        A = Math.max(h, w / ART_W);
        top = h - A;
        cx = w / 2f;
        // overscan (so parallax never shows an edge) and the slow breathing push-in, about a point under the rose
        float zoom = motion ? OVERSCAN * (1f + 0.012f * (0.5f - 0.5f * Mth.cos(t * TAU / 48f))) : 1f;
        float fx = cx, fy = h * 0.5f;
        M.set(pose).translate(fx, fy, 0f).scale(zoom, zoom, 1f).translate(-fx, -fy, 0f);

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // ---- sky, stars, moon
        art(SKY, -ART_W / 2, 0f, ART_W / 2, 1f, 0.003f, 1f);
        additive();
        for (int i = 0; i < STARS.length; i++) {
            float s = Mth.sin(t * (0.7f + 0.13f * i) + i * 1.7f);
            float tw = 0.35f + 0.65f * (s > 0 ? s * s * s : 0f);
            float sz = A * (0.012f + 0.006f * (i % 3));
            float x = sx(STARS[i][0]) + ox(0.004f), y = sy(STARS[i][1]) + oy(0.004f);
            quad(SPARKLE, x - sz, y - sz, x + sz, y + sz, 0f, 0f, 1f, 1f, 1f, 0.95f, 0.88f, tw);
        }
        float mx = sx(MOON_X) + ox(0.005f), my = sy(MOON_Y) + oy(0.005f);
        float halo = MOON_R * 2.9f * A;
        quad(GLOW, mx - halo, my - halo, mx + halo, my + halo, 0f, 0f, 1f, 1f, 1f, 0.82f, 0.62f,
            0.28f + 0.06f * Mth.sin(t * 0.35f));
        RenderSystem.defaultBlendFunc();
        quad(MOON, mx - MOON_R * A, my - MOON_R * A, mx + MOON_R * A, my + MOON_R * A, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);

        // ---- wind: a gust front crosses left to right every GUST_S seconds; clouds and mist speed up with it
        gustFront = -1.7f + 3.4f * frac(t / GUST_S);
        float gustInt = t * 0.5f - GUST_S / (4f * Mth.PI) * Mth.sin(t * TAU / GUST_S);   // integral of the global gust
        float windT = motion ? t + 0.6f * gustInt : t;
        // ---- the breath: a slow inhale / exhale of the glass, the rim light, the shafts, the portal's haze and the mist
        float breath = motion ? 0.5f - 0.5f * Mth.cos(t * TAU / BREATH_S) : 0.5f;

        // ---- clouds and the distant spires
        wrapped(CLOUDS_FAR, 0.28f, 0.80f, windT * 0.006f, 0.007f, 1f);
        art(SPIRES_FAR, -ART_W / 2, 0.12f, ART_W / 2, 0.80f, 0.011f, 1f);
        wrapped(CLOUDS_NEAR, 0.46f, 0.84f, windT * 0.014f, 0.012f, 1f);

        // ---- the cathedral, back to front: each depth layer, its moon rim and its glass; mist sheets between
        float swell = 1f + 0.006f * breath;
        for (int i = 0; i < CATH.length; i++) {
            float z = CATH_Z[i];
            cathLayer(CATH0 + 3 * i, CATH_RECT[i], z, swell, 1f, false);
            cathLayer(CATH0 + 3 * i + 1, CATH_RECT[i], z, swell, 0.22f + 0.38f * breath, true);
            float pulse = (0.30f + 0.30f * breath) * (0.9f + 0.1f * Mth.sin(t * (0.6f + 0.13f * i) + i));
            cathLayer(CATH0 + 3 * i + 2, CATH_RECT[i], z, swell, pulse, true);
            if (i == 1)
                wrapped(MIST, 0.50f, 0.72f, windT * 0.006f + 0.3f, 0.019f, 0.30f + 0.15f * breath);
            if (i == 3)
                wrapped(MIST, 0.56f, 0.76f, windT * 0.008f + 0.7f, 0.027f, 0.28f + 0.15f * breath);
        }
        additive();
        // light shafts from the rose, falling through the mist toward the viewer
        float rx = sx(ROSE_X) + ox(0.036f), ry = sy(ROSE_Y) + oy(0.036f);
        float sway = motion ? 0.012f * Mth.sin(t * 0.21f) : 0f;
        shaft(rx, ry, 0.03f * A, sx(ROSE_X - 0.22f + sway) + ox(0.045f), sx(ROSE_X + 0.20f + sway) + ox(0.045f),
            sy(0.86f) + oy(0.045f), 0.20f + 0.22f * breath);
        // the wave of glass light breathing outward from the rose and the door on each exhale
        float ph = frac(t / BREATH_S - 0.5f);
        float wr = (0.04f + 0.34f * ph) * A, wa = motion ? 0.22f * (1f - ph) * (1f - ph) : 0f;
        quad(GLOW, rx - wr, ry - wr, rx + wr, ry + wr, 0f, 0f, 1f, 1f, 1f, 0.55f, 0.32f, wa);
        float dx0 = sx(DOOR_X) + ox(0.040f), dy0 = sy(DOOR_Y) + oy(0.040f);
        float hr = 0.10f * A * (0.9f + 0.2f * breath);
        quad(GLOW, dx0 - hr, dy0 - hr * 0.7f, dx0 + hr, dy0 + hr * 0.7f, 0f, 0f, 1f, 1f, 1f, 0.55f, 0.30f, 0.10f + 0.20f * breath);
        quad(GLOW, dx0 - wr * 0.6f, dy0 - wr * 0.4f, dx0 + wr * 0.6f, dy0 + wr * 0.4f, 0f, 0f, 1f, 1f, 1f, 0.5f, 0.28f, wa * 0.8f);
        RenderSystem.defaultBlendFunc();
        wrapped(MIST, 0.60f, 0.80f, windT * 0.010f + breath * 0.012f, 0.031f, 0.55f + 0.30f * breath);

        // ---- ground (a plane: strips sheared by depth), cascade, river
        ground(GROUND, 0.56f, 1f);
        float cv = -t * CASCADE_SPEED % 1f;
        cascade(cv, 0.95f);
        additive();
        river(RIPPLES, t, 0.55f + 0.1f * Mth.sin(t * 0.9f));
        float shimmer = 0.7f + 0.3f * Mth.sin(t * 5.3f) * Mth.sin(t * 3.1f + 1f);
        river(STREAK, t, 0.75f * shimmer);
        // foam churning at the foot of the fall
        float fx0 = sx(CASCADE[3]) + ox(zg(CASCADE[4])), fy0 = sy(CASCADE[4] + 0.004f);
        float fr = 0.06f * A * (1f + 0.08f * Mth.sin(t * 4.1f));
        quad(GLOW, fx0 - fr, fy0 - fr * 0.35f, fx0 + fr, fy0 + fr * 0.35f, 0f, 0f, 1f, 1f, 1f, 0.78f, 0.74f,
            0.22f + 0.06f * Mth.sin(t * 6.7f));
        // now and then a ring spreads from a rock in the stream
        if (motion) {
            for (int i = 0; i < RIVER_ROCKS.length; i++) {
                float period = 3.7f + 0.6f * i;
                float p = frac(t / period + i * 0.37f);
                float rr = RIVER_ROCKS[i][2] * A * (1.2f + 2.4f * p);
                float ry0 = RIVER_ROCKS[i][1] + RIVER_ROCKS[i][2] * 0.3f;
                float rx0 = sx(RIVER_ROCKS[i][0]) + ox(zg(ry0)), ry1 = sy(ry0);
                quad(RING, rx0 - rr, ry1 - rr * 0.32f, rx0 + rr, ry1 + rr * 0.32f, 0f, 0f, 1f, 1f, 1f, 0.72f, 0.68f,
                    0.45f * (1f - p) * (1f - p));
            }
        }
        RenderSystem.defaultBlendFunc();
        // mist pooling in the low ground: the hollow on the left, and along the river
        float md = motion ? 0.02f * Mth.sin(t * 0.13f) : 0f;
        float mp = 0.55f + 0.35f * breath;
        quad(MIST_POOL, sx(-1.0f + md) + ox(0.030f), sy(0.655f), sx(-0.12f + md) + ox(0.030f), sy(0.785f), 0f, 0f, 1f, 1f,
            1f, 1f, 1f, mp);
        quad(MIST_POOL, sx(-0.55f - md) + ox(0.045f), sy(0.78f), sx(0.62f - md) + ox(0.045f), sy(0.88f), 0f, 0f, 1f, 1f,
            1f, 1f, 1f, mp * 0.35f);

        // ---- the flower fields: a gentle sway, and a wave rolling through behind each gust
        swayed(FIELD_FAR, 0.62f, 0.93f, 0.0016f, 0.0030f, t, motion);
        if (motion)
            bats(t);
        swayed(FIELD_NEAR, 0.78f, 1f, 0.0040f, 0.0085f, t, motion);

        // ---- the framing: trees (behind) and vines (in front) sway with the wind; the ruined stone stays still
        float fw = FRAME_W * A, ftop = h - A;
        float lxL = -0.03f * A, lxR = w + 0.03f * A - fw;
        float edgeL = (-w / 2f + fw * 0.5f) / A, edgeR = (w / 2f - fw * 0.5f) / A;   // the frames' centres in A
        float leanL = motion ? A * (0.0035f * Mth.sin(t * 0.7f) + 0.014f * gust(edgeL)) : 0f;
        float leanR = motion ? A * (0.0035f * Mth.sin(t * 0.7f + 1.9f) + 0.014f * gust(edgeR)) : 0f;
        float oT = ox(0.055f), oR = ox(0.065f), oV = ox(0.075f);
        leaning(TREE_LEFT, lxL + oT, ftop, lxL + fw + oT, h, leanL, true);
        leaning(TREE_RIGHT, lxR + oT, ftop, lxR + fw + oT, h, leanR, true);
        quad(FRAME_LEFT, lxL + oR, ftop, lxL + fw + oR, h, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
        quad(FRAME_RIGHT, lxR + oR, ftop, lxR + fw + oR, h, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
        // vines hang from the top: their lower ends trail behind the gust (a little later than the trees)
        float vL = motion ? A * (0.004f * Mth.sin(t * 1.1f) + 0.016f * gust(edgeL - 0.25f)) : 0f;
        float vR = motion ? A * (0.004f * Mth.sin(t * 1.1f + 2.3f) + 0.016f * gust(edgeR - 0.25f)) : 0f;
        leaning(VINES_LEFT, lxL + oV, ftop, lxL + fw + oV, h, vL, false);
        leaning(VINES_RIGHT, lxR + oV, ftop, lxR + fw + oV, h, vR, false);
        if (motion) {
            embers(t);
            petals(t);
        }

        // ---- the extreme foreground, out of focus, moving fastest: briars rising from the bottom corners, a dead
        // branch hanging in at the top
        float bl = motion ? A * (0.006f * Mth.sin(t * 0.8f) + 0.02f * gust(-w / 2f / A + 0.2f)) : 0f;
        float br = motion ? A * (0.005f * Mth.sin(t * 0.9f + 1f) + 0.016f * gust(w / 2f / A - 0.15f)) : 0f;
        float tb = motion ? A * (0.004f * Mth.sin(t * 0.6f + 2f) + 0.012f * gust(-w / 2f / A + 0.3f)) : 0f;
        float o1 = ox(0.13f), o2 = ox(0.12f), o3 = ox(0.11f), oyf = oy(0.10f);
        leaning(FG_BRIAR, -0.12f * A + o1, h - 0.44f * A + oyf, 0.54f * A + o1, h + 0.02f * A + oyf, bl, true);
        leaning(FG_THORNS, w - 0.30f * A + o2, h - 0.30f * A + oyf, w + 0.10f * A + o2, h + 0.02f * A + oyf, br, true);
        leaning(FG_BRANCH, -0.08f * A + o3, -0.06f * A + oyf, 0.34f * A + o3, 0.36f * A + oyf, tb, false);

        // ---- vignette and paper grain (screen space, not zoomed)
        M.set(pose);
        quad(VIGNETTE, 0f, 0f, w, h, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
        quad(GRAIN, 0f, 0f, w, h, 0f, 0f, w / 128f, h / 128f, 1f, 1f, 1f, 1f);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /** A soft dark scrim behind a lobby panel so its text stays readable over the art (feathered edges). */
    public static void scrim(GuiGraphics gg, int x0, int y0, int x1, int y1) {
        if (state <= 0)
            return;
        for (int i = 5; i >= 0; i--) {
            int d = i * 3;
            gg.fill(x0 - d, y0 - d, x1 + d, y1 + d, 0x1E000000);
        }
        gg.fill(x0, y0, x1, y1, 0x40000000);
    }

    // ---------------------------------------------------------------- pieces

    private static float sx(float x) {
        return cx + x * A;
    }

    private static float sy(float y) {
        return top + y * A;
    }

    /** The parallax shift (screen px) of something at depth z for the current camera. */
    private static float ox(float z) {
        return -camX * z * A;
    }

    private static float oy(float z) {
        return -camY * z * 0.4f * A;
    }

    /**
     * The depth of the ground at artboard y: it matches the cathedral's facade at its foot and grows toward the
     * viewer like a plane seen from eye height (eased, and capped so the overscan always covers it).
     */
    private static float zg(float y) {
        float d = Math.max(0.3f, (y - 0.615f) / 0.072f);
        return Math.min(0.062f, 0.029f * (float) Math.pow(d, 0.6));
    }

    /** An artboard layer with a parallax depth. */
    private static void art(int tex, float x0, float y0, float x1, float y1, float depth, float alpha) {
        float px = ox(depth), py = oy(depth);
        quad(tex, sx(x0) + px, sy(y0) + py, sx(x1) + px, sy(y1) + py, 0f, 0f, 1f, 1f, 1f, 1f, 1f, alpha);
    }

    /** A horizontally wrapping layer across the whole screen, scrolled by `drift` A (u repeats every ART_W). */
    private static void wrapped(int tex, float y0, float y1, float drift, float depth, float alpha) {
        float px = ox(depth), py = oy(depth);
        float xa = -w / 2f / A - 0.12f, xb = w / 2f / A + 0.12f;   // the screen edges in A, and beyond
        float off = (drift / ART_W) % 1f;
        float u0 = (xa + ART_W / 2) / ART_W - off, u1 = (xb + ART_W / 2) / ART_W - off;
        quad(tex, sx(xa) + px, sy(y0) + py, sx(xb) + px, sy(y1) + py, u0, 0f, u1, 1f, 1f, 1f, 1f, alpha);
    }

    /** The wind at artboard x right now: the travelling gust front plus its trailing eddy, 0..~1.2. */
    private static float gust(float x) {
        float d = (x - gustFront) / 0.35f, e = (x - gustFront + 0.5f) / 0.6f;
        return (float) (Math.exp(-d * d) + 0.4 * Math.exp(-e * e));
    }

    /** One cathedral depth layer (or its rim / glow), swollen by `swell` about the facade's foot. */
    private static void cathLayer(int tex, float[] r, float z, float swell, float alpha, boolean add) {
        if (TEX[tex] < 0 || alpha <= 0.002f)
            return;
        if (add)
            additive();
        float px = ox(z), py = oy(z);
        float pvx = sx(PIVOT_X) + px, pvy = sy(PIVOT_Y) + py;
        float x0 = pvx + (sx(r[0]) + px - pvx) * swell, x1 = pvx + (sx(r[2]) + px - pvx) * swell;
        float y0 = pvy + (sy(r[1]) + py - pvy) * swell, y1 = pvy + (sy(r[3]) + py - pvy) * swell;
        quad(tex, x0, y0, x1, y1, 0f, 0f, 1f, 1f, 1f, 1f, 1f, alpha);
        if (add)
            RenderSystem.defaultBlendFunc();
    }

    /** The ground as ROWS horizontal strips, each edge shifted by the ground's depth at its height. */
    private static void ground(int tex, float y0, float y1) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float xa = sx(-ART_W / 2), xb = sx(ART_W / 2);
        for (int j = 0; j < ROWS; j++) {
            float va = j / (float) ROWS, vb = (j + 1) / (float) ROWS;
            float ya = y0 + (y1 - y0) * va, yb = y0 + (y1 - y0) * vb;
            float sa = ox(zg(ya)), sb = ox(zg(yb)), ta = oy(zg(ya)), tb = oy(zg(yb));
            buf.vertex(M, xa + sa, sy(ya) + ta, 0f).uv(0f, va).color(1f, 1f, 1f, 1f).endVertex();
            buf.vertex(M, xa + sb, sy(yb) + tb, 0f).uv(0f, vb).color(1f, 1f, 1f, 1f).endVertex();
            buf.vertex(M, xb + sb, sy(yb) + tb, 0f).uv(1f, vb).color(1f, 1f, 1f, 1f).endVertex();
            buf.vertex(M, xb + sa, sy(ya) + ta, 0f).uv(1f, va).color(1f, 1f, 1f, 1f).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
    }

    /**
     * A flower field as SLICES x ROWS cells riding the ground plane: each vertex takes the ground's depth at its
     * height, and the cells' upper edges lean - a gentle sway that travels across (phase by x) plus a wave rolling
     * through behind each gust. Shared edges keep it seamless.
     */
    private static void swayed(int tex, float y0, float y1, float sway, float gustAmp, float t, boolean motion) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int rows = 4;
        for (int i = 0; i < SLICES; i++) {
            float ua = i / (float) SLICES, ub = (i + 1) / (float) SLICES;
            float xa = -ART_W / 2 + ART_W * ua, xb = -ART_W / 2 + ART_W * ub;
            float la = motion ? A * (sway * Mth.sin(t * 0.9f + xa * 3f) + gustAmp * gust(xa - 0.15f)) : 0f;
            float lb = motion ? A * (sway * Mth.sin(t * 0.9f + xb * 3f) + gustAmp * gust(xb - 0.15f)) : 0f;
            for (int j = 0; j < rows; j++) {
                float va = j / (float) rows, vb = (j + 1) / (float) rows;
                float ya = y0 + (y1 - y0) * va, yb = y0 + (y1 - y0) * vb;
                // the lean fades toward each cell row's foot so whole plants bend from the ground
                float ka = 1f - va, kb = 1f - vb;
                float sa = ox(zg(ya)), sb = ox(zg(yb)), ta = oy(zg(ya)), tb = oy(zg(yb));
                buf.vertex(M, sx(xa) + sa + la * ka, sy(ya) + ta, 0f).uv(ua, va).color(1f, 1f, 1f, 1f).endVertex();
                buf.vertex(M, sx(xa) + sb + la * kb, sy(yb) + tb, 0f).uv(ua, vb).color(1f, 1f, 1f, 1f).endVertex();
                buf.vertex(M, sx(xb) + sb + lb * kb, sy(yb) + tb, 0f).uv(ub, vb).color(1f, 1f, 1f, 1f).endVertex();
                buf.vertex(M, sx(xb) + sa + lb * ka, sy(ya) + ta, 0f).uv(ub, va).color(1f, 1f, 1f, 1f).endVertex();
            }
        }
        BufferUploader.drawWithShader(buf.end());
    }

    /** A framing layer that leans: anchored at the bottom (a tree, a briar) or at the top (hanging vines, a branch). */
    private static void leaning(int tex, float x0, float y0, float x1, float y1, float lean, boolean anchoredBottom) {
        float lt = anchoredBottom ? lean : 0f, lb = anchoredBottom ? 0f : lean;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(M, x0 + lt, y0, 0f).uv(0f, 0f).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(M, x0 + lb, y1, 0f).uv(0f, 1f).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(M, x1 + lb, y1, 0f).uv(1f, 1f).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(M, x1 + lt, y0, 0f).uv(1f, 0f).color(1f, 1f, 1f, 1f).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    /** The red fall from the doorway down the steps and the scarp, riding the ground's depth at each end. */
    private static void cascade(float v0, float alpha) {
        float ya = CASCADE[1], yb = CASCADE[4];
        float xa = sx(CASCADE[0]) + ox(zg(ya)), xb = sx(CASCADE[3]) + ox(zg(yb));
        float hwa = CASCADE[2] * A, hwb = CASCADE[5] * A;
        float pa = sy(ya) + oy(zg(ya)), pb = sy(yb) + oy(zg(yb));
        float v1 = v0 + CASCADE_TILES;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[CASCADE_T]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(M, xa - hwa, pa, 0f).uv(0f, v0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, xb - hwb, pb, 0f).uv(0f, v1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, xb + hwb, pb, 0f).uv(1f, v1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, xa + hwa, pa, 0f).uv(1f, v0).color(1f, 1f, 1f, alpha).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    /** The light shafts: a fan from a narrow top at the rose to a wide foot (additive). */
    private static void shaft(float x, float y, float hw, float xl, float xr, float yb, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[SHAFTS]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(M, x - hw, y, 0f).uv(0f, 0f).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, xl, yb, 0f).uv(0f, 1f).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, xr, yb, 0f).uv(1f, 1f).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, x + hw, y, 0f).uv(1f, 0f).color(1f, 1f, 1f, alpha).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    /**
     * An overlay on the river as a strip of quads along its centreline (4 per segment), riding the ground's depth.
     * RIPPLES maps u bank to bank and v by perspective distance, scrolling toward the viewer; STREAK (the moon's
     * reflection) is fixed under the moon: its u follows artboard x, so it stays a straight column while the water
     * winds under it.
     */
    private static void river(int tex, float t, float alpha) {
        boolean streak = tex == STREAK;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float scroll = streak ? t * 0.12f : t * RIPPLE_SPEED;
        float vk = streak ? RIPPLE_K * 0.7f : RIPPLE_K;
        scroll %= 1f;
        for (int i = 0; i + 1 < RIVER.length; i++) {
            for (int j = 0; j < 4; j++) {
                float ta = j / 4f, tb = (j + 1) / 4f;
                float[] p = RIVER[i], q = RIVER[i + 1];
                float xa = p[0] + (q[0] - p[0]) * ta, ya = p[1] + (q[1] - p[1]) * ta, wa = p[2] + (q[2] - p[2]) * ta;
                float xb = p[0] + (q[0] - p[0]) * tb, yb = p[1] + (q[1] - p[1]) * tb, wb = p[2] + (q[2] - p[2]) * tb;
                float va = 0.08f / Math.max(ya - HORIZON, 0.02f) * vk + scroll;
                float vb = 0.08f / Math.max(yb - HORIZON, 0.02f) * vk + scroll;
                float aa = alpha, ab = alpha;
                float ula = 0f, ura = 1f, ulb = 0f, urb = 1f;
                if (streak) {
                    aa *= smooth(0.84f, 0.88f, ya) * (1f - smooth(0.95f, 0.99f, ya));
                    ab *= smooth(0.84f, 0.88f, yb) * (1f - smooth(0.95f, 0.99f, yb));
                    ula = 0.5f + (xa - wa - MOON_X) / (2f * STREAK_HW);
                    ura = 0.5f + (xa + wa - MOON_X) / (2f * STREAK_HW);
                    ulb = 0.5f + (xb - wb - MOON_X) / (2f * STREAK_HW);
                    urb = 0.5f + (xb + wb - MOON_X) / (2f * STREAK_HW);
                }
                float sa = ox(zg(ya)), sb = ox(zg(yb)), qa = sy(ya) + oy(zg(ya)), qb = sy(yb) + oy(zg(yb));
                buf.vertex(M, sx(xa - wa) + sa, qa, 0f).uv(ula, va).color(1f, 1f, 1f, aa).endVertex();
                buf.vertex(M, sx(xb - wb) + sb, qb, 0f).uv(ulb, vb).color(1f, 1f, 1f, ab).endVertex();
                buf.vertex(M, sx(xb + wb) + sb, qb, 0f).uv(urb, vb).color(1f, 1f, 1f, ab).endVertex();
                buf.vertex(M, sx(xa + wa) + sa, qa, 0f).uv(ura, va).color(1f, 1f, 1f, aa).endVertex();
            }
        }
        BufferUploader.drawWithShader(buf.end());
    }

    private static float smooth(float e0, float e1, float x) {
        float k = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return k * k * (3f - 2f * k);
    }

    private static int hash(int x) {
        x *= 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return x & 0x7FFFFFFF;
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }

    /** Now and then (every 26 s) a little flock of 2-4 bats crosses the sky, flapping (frames up, level, down, level). */
    private static void bats(float t) {
        int cycle = Mth.floor(t / 26f);
        float local = t - cycle * 26f - 3f;
        if (local < 0f || local > 14f)
            return;
        int hsh = hash(cycle + 7);
        int n = 2 + hsh % 3;
        boolean rightward = (hsh & 8) != 0;
        float half = w / 2f / A + 0.15f;
        float baseY = 0.10f + (hsh % 97) / 97f * 0.18f;
        for (int i = 0; i < n; i++) {
            float delay = i * 0.45f + (hash(hsh + i) % 100) / 100f * 0.6f;
            float p = (local - delay) / 11f;
            if (p <= 0f || p >= 1f)
                continue;
            float x = (rightward ? -half + 2 * half * p : half - 2 * half * p) + (i % 2) * 0.04f;
            float y = baseY + i * 0.028f + 0.018f * Mth.sin(p * 9f + i * 2f) - p * 0.05f;
            float s = (0.030f + 0.008f * (i % 3)) * A;
            int f = ((int) (t * 9f + i * 3)) & 3;
            int frame = f == 3 ? 1 : f;
            float px = sx(x) + ox(0.014f), py = sy(y) + oy(0.014f);
            quad(BATS, px - s, py - s / 2f, px + s, py + s / 2f, frame / 3f, 0f, (frame + 1) / 3f, 1f, 1f, 1f, 1f, 0.95f);
        }
    }

    /** Embers rising from the glowing door and the foot of the fall, drifting with the wind, each at its own depth. */
    private static void embers(float t) {
        additive();
        for (int i = 0; i < EMBERS; i++) {
            float life = 6f + 3f * frac(i * 0.618f);
            float p = frac(t / life + i * 0.37f);
            boolean door = i % 3 != 0;
            float x0 = door ? DOOR_X : CASCADE[3], y0 = door ? DOOR_Y + 0.01f : CASCADE[4];
            float x = x0 + (frac(i * 0.43f) - 0.5f) * 0.06f + p * 0.10f * (0.5f + gust(x0)) + 0.012f * Mth.sin(t * 1.3f + i);
            float y = y0 - p * (0.18f + 0.1f * frac(i * 0.71f));
            float z = 0.045f + 0.04f * frac(i * 0.29f);
            float s = (0.004f + 0.004f * frac(i * 0.53f)) * A * (1f + z * 10f);
            float a = Math.min(1f, p * 6f) * (1f - p) * (0.6f + 0.4f * Mth.sin(t * 7f + i * 2f));
            float px = sx(x) + ox(z), py = sy(y) + oy(z);
            quad(SPARKLE, px - s, py - s, px + s, py + s, 0f, 0f, 1f, 1f, 1f, 0.55f, 0.22f, a);
        }
        RenderSystem.defaultBlendFunc();
    }

    /** Petals and copper leaves blowing across, each on its own looping path and depth; a gust lifts and hurries them. */
    private static void petals(float t) {
        float span = w / A + 0.5f;
        for (int i = 0; i < PETALS; i++) {
            float speed = 0.05f + 0.035f * frac(i * 0.618f);
            float phase = frac(t * speed / span + i * 0.37f);
            float x = -span / 2f + phase * span;
            float g = gust(x);
            x += 0.10f * g;
            float y = 0.38f + 0.58f * frac(i * 0.731f + 0.2f) + 0.03f * Mth.sin(t * 0.9f + i) + phase * 0.06f - 0.06f * g;
            float z = 0.05f + 0.06f * frac(i * 0.29f);
            float s = (0.006f + 0.004f * frac(i * 0.47f)) * A * (0.6f + z * 10f);
            float ang = t * (1f + frac(i * 0.41f)) * 1.6f + i + 2f * g;
            float fade = Math.min(1f, Math.min(phase, 1f - phase) * 8f);
            rotated(i % 3 == 2 ? LEAF : PETAL, x * A + cx + ox(z), sy(y) + oy(z), s, ang, 0.9f * fade);
        }
    }

    // ---------------------------------------------------------------- quads

    private static void additive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    private static void quad(int tex, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                             float r, float g, float b, float a) {
        if (a <= 0.002f || TEX[tex] < 0)
            return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(M, x0, y0, 0f).uv(u0, v0).color(r, g, b, a).endVertex();
        buf.vertex(M, x0, y1, 0f).uv(u0, v1).color(r, g, b, a).endVertex();
        buf.vertex(M, x1, y1, 0f).uv(u1, v1).color(r, g, b, a).endVertex();
        buf.vertex(M, x1, y0, 0f).uv(u1, v0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    private static void rotated(int tex, float x, float y, float half, float angle, float a) {
        if (a <= 0.002f)
            return;
        float c = Mth.cos(angle) * half, s = Mth.sin(angle) * half;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(M, x - c + s, y - s - c, 0f).uv(0f, 0f).color(1f, 1f, 1f, a).endVertex();
        buf.vertex(M, x - c - s, y - s + c, 0f).uv(0f, 1f).color(1f, 1f, 1f, a).endVertex();
        buf.vertex(M, x + c - s, y + s + c, 0f).uv(1f, 1f).color(1f, 1f, 1f, a).endVertex();
        buf.vertex(M, x + c + s, y + s - c, 0f).uv(1f, 0f).color(1f, 1f, 1f, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }
}
