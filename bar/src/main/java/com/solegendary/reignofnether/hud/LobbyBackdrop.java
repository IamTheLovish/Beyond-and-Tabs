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
 * skirmish setup and multiplayer lobby screens. lovish (Oct 21) wanted the lobby art changed and animated, from two
 * mood references; the layers are our own, painted from code by tools/gen_lobby_art.py. This class only places and
 * animates them.
 *
 * Layout: one artboard in A units (A = the cover-scaled screen height, x from the centre, y from the top, bottom
 * anchored), the same rects and river geometry as the generator. The two framing layers are pinned to the screen edges
 * instead, so 16:9 and 21:9 both keep their framing.
 *
 * Motion (lovish: "the castle is breathing with wind and water moving"): a 7 s breath swells the facade 0.6 % and its
 * rim light, glass and portal haze together and thickens the mist; a gust crosses left to right every 9 s and everything
 * wind-driven reads it at its own x (clouds and mist hurry, the trees and vines lean, the flowers ripple in a wave,
 * petals and leaves lift); the cascade falls, glints flow down the river toward the viewer, the moon's reflection
 * shimmers, rings spread from the rocks in the stream; stars twinkle, bats cross now and then, the whole scene pushes in
 * very slowly and parallaxes with the mouse by depth. `splash_reduce_motion` freezes all of it.
 *
 * Cost: about 70 textured quads a frame through the shared Tesselator, no allocation (static matrix, indexed loops,
 * time-derived state). The PNGs are read once from our own mod file and uploaded as plain GL textures (like SplashArt);
 * if any of them cannot be read the screens keep the vanilla background.
 */
public class LobbyBackdrop {

    // ---- shared with tools/gen_lobby_art.py
    private static final float ART_W = 2560f / 1100f;
    private static final float MOON_X = 0f, MOON_Y = 0.27f, MOON_R = 0.20f;
    private static final float FRAME_W = 0.66f;
    private static final float HORIZON = 0.70f;
    private static final float[][] RIVER = {   // x, y, half width
        {0.000f, 0.836f, 0.050f}, {0.020f, 0.850f, 0.070f}, {0.010f, 0.866f, 0.095f}, {-0.070f, 0.886f, 0.130f},
        {-0.140f, 0.910f, 0.175f}, {-0.100f, 0.938f, 0.225f}, {0.060f, 0.966f, 0.285f}, {0.200f, 0.995f, 0.350f},
        {0.240f, 1.030f, 0.420f}};
    /** the rocks breaking the river's surface (x, y, r) - gen_lobby_art.river_rocks() */
    private static final float[][] RIVER_ROCKS = {
        {0.0128f, 0.875f, 0.010f}, {-0.1999f, 0.905f, 0.013f}, {0.0045f, 0.930f, 0.016f}, {-0.0813f, 0.955f, 0.018f},
        {0.3810f, 0.985f, 0.024f}, {-0.0283f, 1.000f, 0.026f}};
    private static final float CASCADE_X = 0f, CASCADE_TOP = 0.736f, CASCADE_TOP_HW = 0.020f,
        CASCADE_BOTTOM = 0.840f, CASCADE_BOTTOM_HW = 0.046f;
    private static final float BREATH_S = 7f;    // the cathedral's slow inhale / exhale
    private static final float GUST_S = 9f;      // a gust crosses the scene left to right this often
    private static final float RIPPLE_K = 8f, RIPPLE_SPEED = 0.35f, STREAK_HW = 0.035f;
    private static final float CASCADE_TILES = 1.6f, CASCADE_SPEED = 0.9f;
    private static final float[][] STARS = {
        {-0.92f, 0.06f}, {-0.71f, 0.13f}, {-0.55f, 0.04f}, {-0.38f, 0.17f}, {-0.30f, 0.07f}, {0.31f, 0.05f},
        {0.42f, 0.15f}, {0.58f, 0.08f}, {0.77f, 0.03f}, {0.86f, 0.16f}, {1.02f, 0.09f}, {-1.05f, 0.20f},
        {0.66f, 0.21f}, {-0.83f, 0.25f}};

    // ---- textures
    private static final String DIR = "assets/" + ReignOfNether.MOD_ID + "/textures/gui/lobby/";
    private static final String[] FILES = {"sky", "moon", "glow", "sparkle", "clouds_far", "spires_far", "clouds_near",
        "cathedral", "glass_rose", "glass_lancets", "mist", "ground", "cascade", "ripples", "streak", "field_far",
        "field_near", "bats", "frame_left", "frame_right", "petal", "vignette", "grain", "cathedral_rim",
        "frame_left_tree", "frame_right_tree", "frame_left_vines", "frame_right_vines", "leaf", "ring"};
    private static final int SKY = 0, MOON = 1, GLOW = 2, SPARKLE = 3, CLOUDS_FAR = 4, SPIRES_FAR = 5, CLOUDS_NEAR = 6,
        CATHEDRAL = 7, GLASS_ROSE = 8, GLASS_LANCETS = 9, MIST = 10, GROUND = 11, CASCADE = 12, RIPPLES = 13,
        STREAK = 14, FIELD_FAR = 15, FIELD_NEAR = 16, BATS = 17, FRAME_LEFT = 18, FRAME_RIGHT = 19, PETAL = 20,
        VIGNETTE = 21, GRAIN = 22, CATHEDRAL_RIM = 23, TREE_LEFT = 24, TREE_RIGHT = 25, VINES_LEFT = 26,
        VINES_RIGHT = 27, LEAF = 28, RING = 29;
    /** wrap mode per texture: 0 clamp, 1 repeat, 2 clamp across / repeat along (the moon's reflection) */
    private static final int[] WRAP = {0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 1, 2, 0, 0, 0, 0, 0, 0, 0, 1, 0,
        0, 0, 0, 0, 0, 0};
    private static final int[] TEX = new int[FILES.length];
    private static int state = 0;   // 0 not loaded yet, 1 ready, -1 failed (vanilla background)

    // ---- per-frame state (no allocation)
    private static final Matrix4f M = new Matrix4f();
    private static float parX = 0, parY = 0;
    private static long lastFrameMs = 0;
    private static float A, top, cx, w, h, gustFront;
    private static final int SLICES = 16;

    private static final int PETALS = 16;

    /** Uploads the layers on first use (render thread only). False if the art is unavailable. */
    public static boolean ensureLoaded() {
        if (state != 0)
            return state > 0;
        if (!RenderSystem.isOnRenderThread())
            return false;
        int loaded = 0;
        try {
            for (int i = 0; i < FILES.length; i++) {
                NativeImage img;
                try (InputStream in = open(DIR + FILES[i] + ".png")) {
                    img = NativeImage.read(in);
                }
                int iw = img.getWidth(), ih = img.getHeight();
                TEX[i] = TextureUtil.generateTextureId();
                loaded = i + 1;
                TextureUtil.prepareImage(TEX[i], iw, ih);
                // linear filtering, the CPU copy freed straight after the upload; clamped unless the layer scrolls
                img.upload(0, 0, 0, 0, 0, iw, ih, true, WRAP[i] == 0, false, true);
                if (WRAP[i] == 2) {
                    GlStateManager._bindTexture(TEX[i]);
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                }
            }
            state = 1;
        } catch (Throwable e) {
            ReignOfNether.LOGGER.warn("[LobbyBackdrop] lobby art unavailable, keeping the vanilla background", e);
            for (int i = 0; i < loaded; i++)
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
        float k = 1f - (float) Math.exp(-dt * 3f);
        parX += (tx - parX) * k;
        parY += (ty - parY) * k;
        if (!motion) {
            parX = 0;
            parY = 0;
        }

        A = Math.max(h, w / ART_W);
        top = h - A;
        cx = w / 2f;
        // the slow breathing push-in, about a point just under the rose window
        float zoom = motion ? 1f + 0.012f * (0.5f - 0.5f * Mth.cos(t * 6.2831855f / 48f)) : 1f;
        float fx = cx, fy = sy(0.45f);
        M.set(gg.pose().last().pose()).translate(fx, fy, 0f).scale(zoom, zoom, 1f).translate(-fx, -fy, 0f);

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // ---- sky, stars, moon
        art(SKY, -ART_W / 2, 0f, ART_W / 2, 1f, 0.004f, 1f);
        additive();
        for (int i = 0; i < STARS.length; i++) {
            float s = Mth.sin(t * (0.7f + 0.13f * i) + i * 1.7f);
            float tw = 0.35f + 0.65f * (s > 0 ? s * s * s : 0f);
            float sz = A * (0.012f + 0.006f * (i % 3));
            float x = sx(STARS[i][0]) - parX * 0.004f * A, y = sy(STARS[i][1]) - parY * 0.003f * A;
            quad(SPARKLE, x - sz, y - sz, x + sz, y + sz, 0f, 0f, 1f, 1f, 1f, 0.95f, 0.88f, tw);
        }
        float mx = sx(MOON_X) - parX * 0.006f * A, my = sy(MOON_Y) - parY * 0.004f * A;
        float halo = MOON_R * 2.9f * A;
        quad(GLOW, mx - halo, my - halo, mx + halo, my + halo, 0f, 0f, 1f, 1f, 1f, 0.82f, 0.62f,
            0.28f + 0.06f * Mth.sin(t * 0.35f));
        RenderSystem.defaultBlendFunc();
        quad(MOON, mx - MOON_R * A, my - MOON_R * A, mx + MOON_R * A, my + MOON_R * A, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);

        // ---- wind: a gust front crosses left to right every GUST_S seconds; clouds and mist speed up with it
        float gp = t / GUST_S;
        gustFront = -1.7f + 3.4f * frac(gp);
        float gustInt = t * 0.5f - GUST_S / (4f * Mth.PI) * Mth.sin(t * 6.2831855f / GUST_S);   // integral of the global gust
        float windT = motion ? t + 0.6f * gustInt : t;
        // ---- the breath: a slow inhale / exhale of the glass, the rim light, the portal's haze and the mist
        float breath = motion ? 0.5f - 0.5f * Mth.cos(t * 6.2831855f / BREATH_S) : 0.5f;

        // ---- clouds and the distant spires
        wrapped(CLOUDS_FAR, 0.28f, 0.80f, windT * 0.006f, 0.008f, 1f);
        art(SPIRES_FAR, -ART_W / 2, 0.12f, ART_W / 2, 0.80f, 0.010f, 1f);
        wrapped(CLOUDS_NEAR, 0.46f, 0.84f, windT * 0.014f, 0.012f, 1f);

        // ---- the cathedral (a 0.6 % swell on the breath, about its foot - the architecture itself never moves) and its glass
        float swell = 1f + 0.006f * breath;
        cathedralLayer(CATHEDRAL, swell, 1f);
        additive();
        cathedralLayer(CATHEDRAL_RIM, swell, 0.22f + 0.38f * breath);
        cathedralLayer(GLASS_ROSE, swell, (0.34f + 0.26f * breath) * (0.92f + 0.08f * Mth.sin(t * 0.80f)));
        cathedralLayer(GLASS_LANCETS, swell, (0.30f + 0.26f * breath) * (0.92f + 0.08f * Mth.sin(t * 0.63f + 2.1f)));
        // a warm haze breathing out of the portal
        float gpx0 = -parX * 0.016f * A;
        float hx = sx(0f) + gpx0, hy = sy(0.725f), hr = 0.10f * A * (0.9f + 0.2f * breath);
        quad(GLOW, hx - hr, hy - hr * 0.7f, hx + hr, hy + hr * 0.7f, 0f, 0f, 1f, 1f, 1f, 0.55f, 0.30f, 0.10f + 0.20f * breath);
        RenderSystem.defaultBlendFunc();
        wrapped(MIST, 0.60f, 0.86f, windT * 0.010f + breath * 0.012f, 0.018f, 0.65f + 0.35f * breath);

        // ---- ground, cascade, river
        art(GROUND, -ART_W / 2, 0.56f, ART_W / 2, 1f, 0.020f, 1f);
        float gpx = -parX * 0.020f * A;
        float c0 = CASCADE_X * A + cx + gpx, cv = -t * CASCADE_SPEED % 1f;
        trapezoid(CASCADE, c0, sy(CASCADE_TOP), CASCADE_TOP_HW * A, sy(CASCADE_BOTTOM), CASCADE_BOTTOM_HW * A,
            cv, cv + CASCADE_TILES, 0.95f);
        additive();
        river(RIPPLES, gpx, t, 0.55f + 0.1f * Mth.sin(t * 0.9f));
        float shimmer = 0.7f + 0.3f * Mth.sin(t * 5.3f) * Mth.sin(t * 3.1f + 1f);
        river(STREAK, gpx, t, 0.75f * shimmer);
        // foam churning at the foot of the fall
        float fx0 = c0, fy0 = sy(CASCADE_BOTTOM + 0.004f), fr = 0.06f * A * (1f + 0.08f * Mth.sin(t * 4.1f));
        quad(GLOW, fx0 - fr, fy0 - fr * 0.35f, fx0 + fr, fy0 + fr * 0.35f, 0f, 0f, 1f, 1f, 1f, 0.78f, 0.74f,
            0.22f + 0.06f * Mth.sin(t * 6.7f));
        // now and then a ring spreads from a rock in the stream
        if (motion) {
            for (int i = 0; i < RIVER_ROCKS.length; i++) {
                float period = 3.7f + 0.6f * i;
                float p = frac(t / period + i * 0.37f);
                float rr = RIVER_ROCKS[i][2] * A * (1.2f + 2.4f * p);
                float rx = sx(RIVER_ROCKS[i][0]) + gpx, ry = sy(RIVER_ROCKS[i][1] + RIVER_ROCKS[i][2] * 0.3f);
                quad(RING, rx - rr, ry - rr * 0.32f, rx + rr, ry + rr * 0.32f, 0f, 0f, 1f, 1f, 1f, 0.72f, 0.68f,
                    0.45f * (1f - p) * (1f - p));
            }
        }
        RenderSystem.defaultBlendFunc();

        // ---- the flower fields: a gentle sway, and a wave rolling through behind each gust
        swayed(FIELD_FAR, 0.62f, 0.93f, 0.026f, 0.0016f, 0.0030f, t, motion);
        if (motion)
            bats(t);
        swayed(FIELD_NEAR, 0.78f, 1f, 0.034f, 0.0040f, 0.0085f, t, motion);

        // ---- the framing: trees (behind) and vines (in front) sway with the wind; the ruined stone stays still
        float fw = FRAME_W * A, fpx = -parX * 0.02f * A, ftop = h - A;
        float lxL = -0.03f * A + fpx, lxR = w + 0.03f * A + fpx - fw;
        float edgeL = (-w / 2f + fw * 0.5f) / A, edgeR = (w / 2f - fw * 0.5f) / A;   // the frames' centres in A
        float leanL = motion ? A * (0.0035f * Mth.sin(t * 0.7f) + 0.014f * gust(edgeL)) : 0f;
        float leanR = motion ? A * (0.0035f * Mth.sin(t * 0.7f + 1.9f) + 0.014f * gust(edgeR)) : 0f;
        leaning(TREE_LEFT, lxL, ftop, lxL + fw, h, leanL, true);
        leaning(TREE_RIGHT, lxR, ftop, lxR + fw, h, leanR, true);
        quad(FRAME_LEFT, lxL, ftop, lxL + fw, h, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
        quad(FRAME_RIGHT, lxR, ftop, lxR + fw, h, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
        // vines hang from the top: their lower ends trail behind the gust (a little later than the trees)
        float vL = motion ? A * (0.004f * Mth.sin(t * 1.1f) + 0.016f * gust(edgeL - 0.25f)) : 0f;
        float vR = motion ? A * (0.004f * Mth.sin(t * 1.1f + 2.3f) + 0.016f * gust(edgeR - 0.25f)) : 0f;
        leaning(VINES_LEFT, lxL, ftop, lxL + fw, h, vL, false);
        leaning(VINES_RIGHT, lxR, ftop, lxR + fw, h, vR, false);
        if (motion)
            petals(t);

        // ---- vignette and paper grain
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

    /** An artboard layer with a parallax depth (horizontal; a quarter of it vertical for the far layers only). */
    private static void art(int tex, float x0, float y0, float x1, float y1, float depth, float alpha) {
        float px = -parX * depth * A;
        float py = depth < 0.016f ? -parY * depth * 0.3f * A : 0f;
        quad(tex, sx(x0) + px, sy(y0) + py, sx(x1) + px, sy(y1) + py, 0f, 0f, 1f, 1f, 1f, 1f, 1f, alpha);
    }

    /** A horizontally wrapping layer across the whole screen, scrolled by `drift` A (u repeats every ART_W). */
    private static void wrapped(int tex, float y0, float y1, float drift, float depth, float alpha) {
        float px = -parX * depth * A;
        float xa = -w / 2f / A - 0.05f, xb = w / 2f / A + 0.05f;   // screen edges in A, a little beyond
        float off = (drift / ART_W) % 1f;
        float u0 = (xa + ART_W / 2) / ART_W - off, u1 = (xb + ART_W / 2) / ART_W - off;
        quad(tex, sx(xa) + px, sy(y0), sx(xb) + px, sy(y1), u0, 0f, u1, 1f, 1f, 1f, 1f, alpha);
    }

    /** The wind at artboard x right now: the travelling gust front plus its trailing eddy, 0..~1.2. */
    private static float gust(float x) {
        float d = (x - gustFront) / 0.35f, e = (x - gustFront + 0.5f) / 0.6f;
        return (float) (Math.exp(-d * d) + 0.4 * Math.exp(-e * e));
    }

    /** A cathedral-frame layer (the facade, its rim light, its glass), swollen by `swell` about the facade's foot. */
    private static void cathedralLayer(int tex, float swell, float alpha) {
        float px = -parX * 0.014f * A, py = -parY * 0.014f * 0.3f * A;
        float pvx = sx(0f) + px, pvy = sy(0.745f) + py;
        float x0 = pvx + (sx(-0.40f) + px - pvx) * swell, x1 = pvx + (sx(0.40f) + px - pvx) * swell;
        float y0 = pvy + (sy(0f) + py - pvy) * swell, y1 = pvy + (sy(0.80f) + py - pvy) * swell;
        quad(tex, x0, y0, x1, y1, 0f, 0f, 1f, 1f, 1f, 1f, 1f, alpha);
    }

    /**
     * A flower field in SLICES vertical strips: the bottom edge stays put, the top edge leans - a gentle sway that
     * travels across (phase by x) plus a wave rolling through behind each gust. Shared strip edges keep it seamless.
     */
    private static void swayed(int tex, float y0, float y1, float depth, float sway, float gustAmp, float t, boolean motion) {
        float px = -parX * depth * A;
        float ya = sy(y0), yb = sy(y1);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float prevLean = 0f;
        for (int i = 0; i <= SLICES; i++) {
            float u = i / (float) SLICES;
            float x = -ART_W / 2 + ART_W * u;
            float lean = motion ? A * (sway * Mth.sin(t * 0.9f + x * 3f) + gustAmp * gust(x - 0.15f)) : 0f;
            if (i > 0) {
                float u0 = (i - 1) / (float) SLICES;
                float xa = sx(-ART_W / 2 + ART_W * u0) + px, xb = sx(x) + px;
                buf.vertex(M, xa + prevLean, ya, 0f).uv(u0, 0f).color(1f, 1f, 1f, 1f).endVertex();
                buf.vertex(M, xa, yb, 0f).uv(u0, 1f).color(1f, 1f, 1f, 1f).endVertex();
                buf.vertex(M, xb, yb, 0f).uv(u, 1f).color(1f, 1f, 1f, 1f).endVertex();
                buf.vertex(M, xb + lean, ya, 0f).uv(u, 0f).color(1f, 1f, 1f, 1f).endVertex();
            }
            prevLean = lean;
        }
        BufferUploader.drawWithShader(buf.end());
    }

    /** A framing layer that leans: anchored at the bottom (a tree) or at the top (hanging vines). */
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

    private static void trapezoid(int tex, float x, float ya, float hwa, float yb, float hwb, float v0, float v1, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(M, x - hwa, ya, 0f).uv(0f, v0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, x - hwb, yb, 0f).uv(0f, v1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, x + hwb, yb, 0f).uv(1f, v1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(M, x + hwa, ya, 0f).uv(1f, v0).color(1f, 1f, 1f, alpha).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    /**
     * An overlay on the river as a strip of quads along its centreline (4 per segment). RIPPLES maps u bank to bank and
     * v by perspective distance, scrolling toward the viewer; STREAK (the moon's reflection) is fixed under the moon:
     * its u follows screen x, so it stays a straight column while the water winds under it.
     */
    private static void river(int tex, float px, float t, float alpha) {
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
                    aa *= smooth(0.80f, 0.90f, ya);
                    ab *= smooth(0.80f, 0.90f, yb);
                    ula = 0.5f + (xa - wa) / (2f * STREAK_HW);
                    ura = 0.5f + (xa + wa) / (2f * STREAK_HW);
                    ulb = 0.5f + (xb - wb) / (2f * STREAK_HW);
                    urb = 0.5f + (xb + wb) / (2f * STREAK_HW);
                }
                buf.vertex(M, sx(xa - wa) + px, sy(ya), 0f).uv(ula, va).color(1f, 1f, 1f, aa).endVertex();
                buf.vertex(M, sx(xb - wb) + px, sy(yb), 0f).uv(ulb, vb).color(1f, 1f, 1f, ab).endVertex();
                buf.vertex(M, sx(xb + wb) + px, sy(yb), 0f).uv(urb, vb).color(1f, 1f, 1f, ab).endVertex();
                buf.vertex(M, sx(xa + wa) + px, sy(ya), 0f).uv(ura, va).color(1f, 1f, 1f, aa).endVertex();
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
            float px = sx(x) - parX * 0.012f * A, py = sy(y);
            quad(BATS, px - s, py - s / 2f, px + s, py + s / 2f, frame / 3f, 0f, (frame + 1) / 3f, 1f, 1f, 1f, 1f, 0.95f);
        }
    }

    /** Petals and copper leaves blowing across, each on its own looping path; a passing gust lifts and hurries them. */
    private static void petals(float t) {
        float span = w / A + 0.5f;
        for (int i = 0; i < PETALS; i++) {
            float speed = 0.05f + 0.035f * frac(i * 0.618f);
            float phase = frac(t * speed / span + i * 0.37f);
            float x = -span / 2f + phase * span;
            float g = gust(x);
            x += 0.10f * g;
            float y = 0.38f + 0.58f * frac(i * 0.731f + 0.2f) + 0.03f * Mth.sin(t * 0.9f + i) + phase * 0.06f - 0.06f * g;
            float s = (0.007f + 0.005f * frac(i * 0.29f)) * A;
            float ang = t * (1f + frac(i * 0.41f)) * 1.6f + i + 2f * g;
            float fade = Math.min(1f, Math.min(phase, 1f - phase) * 8f);
            float px = x * A - parX * 0.04f * A + cx;
            rotated(i % 3 == 2 ? LEAF : PETAL, px, sy(y), s, ang, 0.9f * fade);
        }
    }

    // ---------------------------------------------------------------- quads

    private static void additive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    private static void quad(int tex, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                             float r, float g, float b, float a) {
        if (a <= 0.002f)
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
