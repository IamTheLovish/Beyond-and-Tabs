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
 * Motion: two cloud banks drift at different speeds, the rose window and the lancets pulse out of step, the moon's
 * halo breathes, stars twinkle, mist drifts, the cascade falls from the doorway, ripples and the moon's reflection run
 * down the river, the flower fields sway, petals blow across, now and then a few bats cross the sky, the whole scene
 * pushes in very slowly, and the layers parallax with the mouse by depth. `splash_reduce_motion` freezes all of it.
 *
 * Cost: about 40 textured quads a frame through the shared Tesselator, no allocation (static matrix, indexed loops,
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
        {0.000f, 0.792f, 0.030f}, {0.016f, 0.806f, 0.044f}, {-0.012f, 0.822f, 0.058f}, {-0.075f, 0.842f, 0.080f},
        {-0.090f, 0.866f, 0.108f}, {-0.010f, 0.893f, 0.145f}, {0.120f, 0.926f, 0.195f}, {0.110f, 0.962f, 0.255f},
        {-0.060f, 1.010f, 0.330f}};
    private static final float CASCADE_X = 0f, CASCADE_TOP = 0.736f, CASCADE_TOP_HW = 0.020f,
        CASCADE_BOTTOM = 0.797f, CASCADE_BOTTOM_HW = 0.030f;
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
        "field_near", "bats", "frame_left", "frame_right", "petal", "vignette", "grain"};
    private static final int SKY = 0, MOON = 1, GLOW = 2, SPARKLE = 3, CLOUDS_FAR = 4, SPIRES_FAR = 5, CLOUDS_NEAR = 6,
        CATHEDRAL = 7, GLASS_ROSE = 8, GLASS_LANCETS = 9, MIST = 10, GROUND = 11, CASCADE = 12, RIPPLES = 13,
        STREAK = 14, FIELD_FAR = 15, FIELD_NEAR = 16, BATS = 17, FRAME_LEFT = 18, FRAME_RIGHT = 19, PETAL = 20,
        VIGNETTE = 21, GRAIN = 22;
    /** wrap mode per texture: 0 clamp, 1 repeat, 2 clamp across / repeat along (the moon's reflection) */
    private static final int[] WRAP = {0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 1, 2, 0, 0, 0, 0, 0, 0, 0, 1};
    private static final int[] TEX = new int[FILES.length];
    private static int state = 0;   // 0 not loaded yet, 1 ready, -1 failed (vanilla background)

    // ---- per-frame state (no allocation)
    private static final Matrix4f M = new Matrix4f();
    private static float parX = 0, parY = 0;
    private static long lastFrameMs = 0;
    private static float A, top, cx, w, h;

    private static final int PETALS = 12;

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

        // ---- clouds and the distant spires
        wrapped(CLOUDS_FAR, 0.28f, 0.80f, t * 0.006f, 0.008f, 1f);
        art(SPIRES_FAR, -ART_W / 2, 0.12f, ART_W / 2, 0.80f, 0.010f, 1f);
        wrapped(CLOUDS_NEAR, 0.46f, 0.84f, t * 0.014f, 0.012f, 1f);

        // ---- the cathedral and its glass
        art(CATHEDRAL, -0.40f, 0f, 0.40f, 0.80f, 0.014f, 1f);
        additive();
        art(GLASS_ROSE, -0.40f, 0f, 0.40f, 0.80f, 0.014f, 0.40f + 0.12f * Mth.sin(t * 0.80f));
        art(GLASS_LANCETS, -0.40f, 0f, 0.40f, 0.80f, 0.014f, 0.36f + 0.12f * Mth.sin(t * 0.63f + 2.1f));
        RenderSystem.defaultBlendFunc();
        wrapped(MIST, 0.60f, 0.86f, t * 0.010f, 0.018f, 0.9f);

        // ---- ground, cascade, river
        art(GROUND, -ART_W / 2, 0.56f, ART_W / 2, 1f, 0.020f, 1f);
        float gpx = -parX * 0.020f * A;
        float c0 = CASCADE_X * A + cx + gpx, cv = -t * CASCADE_SPEED % 1f;
        trapezoid(CASCADE, c0, sy(CASCADE_TOP), CASCADE_TOP_HW * A, sy(CASCADE_BOTTOM), CASCADE_BOTTOM_HW * A,
            cv, cv + CASCADE_TILES, 0.95f);
        additive();
        river(RIPPLES, gpx, t, 0.55f + 0.1f * Mth.sin(t * 0.9f));
        river(STREAK, gpx, t, 0.75f * (0.85f + 0.15f * Mth.sin(t * 1.7f)));
        RenderSystem.defaultBlendFunc();

        // ---- the flower fields, swaying
        float sway1 = Mth.sin(t * 0.9f) + 0.4f * Mth.sin(t * 2.3f + 1f);
        float sway2 = Mth.sin(t * 0.75f + 1.3f) + 0.4f * Mth.sin(t * 1.9f + 0.4f);
        swayed(FIELD_FAR, 0.62f, 0.93f, 0.026f, sway1 * 0.0022f * A);
        if (motion)
            bats(t);
        swayed(FIELD_NEAR, 0.78f, 1f, 0.034f, sway2 * 0.0055f * A);

        // ---- the framing ruins, pinned to the screen edges
        float fw = FRAME_W * A, fpx = -parX * 0.02f * A, fy0 = h - A;
        quad(FRAME_LEFT, -0.03f * A + fpx, fy0, -0.03f * A + fpx + fw, h, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
        quad(FRAME_RIGHT, w + 0.03f * A + fpx - fw, fy0, w + 0.03f * A + fpx, h, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
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

    /** A flower field: the bottom edge stays put, the top edge leans with the wind. */
    private static void swayed(int tex, float y0, float y1, float depth, float lean) {
        float px = -parX * depth * A;
        float x0 = sx(-ART_W / 2) + px, x1 = sx(ART_W / 2) + px, ya = sy(y0), yb = sy(y1);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(M, x0 + lean, ya, 0f).uv(0f, 0f).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(M, x0, yb, 0f).uv(0f, 1f).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(M, x1, yb, 0f).uv(1f, 1f).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(M, x1 + lean, ya, 0f).uv(1f, 0f).color(1f, 1f, 1f, 1f).endVertex();
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

    /** A dozen petals blowing across, each on its own looping path (all derived from time, nothing stored). */
    private static void petals(float t) {
        float span = w / A + 0.5f;
        for (int i = 0; i < PETALS; i++) {
            float speed = 0.05f + 0.035f * frac(i * 0.618f);
            float phase = frac(t * speed / span + i * 0.37f);
            float x = -span / 2f + phase * span;
            float y = 0.38f + 0.58f * frac(i * 0.731f + 0.2f) + 0.03f * Mth.sin(t * 0.9f + i) + phase * 0.06f;
            float s = (0.007f + 0.005f * frac(i * 0.29f)) * A;
            float ang = t * (1f + frac(i * 0.41f)) * 1.6f + i;
            float fade = Math.min(1f, Math.min(phase, 1f - phase) * 8f);
            float px = x * A - parX * 0.04f * A + cx;
            rotated(PETAL, px, sy(y), s, ang, 0.9f * fade);
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
