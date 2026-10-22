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
import com.mojang.blaze3d.vertex.VertexSorting;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.joml.Matrix4f;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The title / loading screen art (design/splash.md). By default the animated moonlit cathedral (LobbyBackdrop in its
 * title layout, with the blood-red "BEYOND AND TABS" lettering); lovish (Oct 22): "I don't want this old one anymore
 * ... use the cathedral". The older "cathedral rose window" below stays only as the fallback when the cathedral's art
 * cannot be read: a stained-glass rose window
 * in a gothic arch, "BEYOND AND TABS" in 3D gold and "Developed by Lovish" beneath, candlelight around it. The layers are
 * drawn from code by tools/gen_splash_art.py; this class only puts them on screen.
 *
 * Used by TitleScreenMixin (the title screen, GUI units) and by the loading-overlay mixins (the resource-loading screen
 * at launch, where the resource manager is mid-reload). That second caller is why the PNGs are read straight out of our
 * own mod file and uploaded as plain GL textures instead of going through the TextureManager: they are available before
 * the first reload finishes, and no resource pack can swap them half-way through. Each texture is uploaded once; the
 * per-frame path allocates nothing.
 */
public class SplashArt {

    // layout as fractions of the screen height, centred horizontally (the same numbers as tools/gen_splash_art.py)
    private static final float WINDOW_CY = 0.47f, WINDOW_D = 0.74f;
    private static final float TITLE_CY = 0.108f, TITLE_H = 0.19f;
    private static final float BYLINE_CY = 0.226f, BYLINE_H = 0.075f;

    private static final String DIR = "assets/" + ReignOfNether.MOD_ID + "/textures/gui/splash/";
    private static final String[] FILES = {"background", "frame", "window", "rays", "glow", "title", "byline"};
    private static final int BACKGROUND = 0, FRAME = 1, WINDOW = 2, RAYS = 3, GLOW = 4, TITLE = 5, BYLINE = 6;
    private static final int[] TEX = new int[FILES.length];
    private static final float[] ASPECT = new float[FILES.length];
    private static int state = 0;   // 0 not loaded yet, 1 ready, -1 failed (callers fall back to the vanilla look)

    // smoothed mouse parallax, -1..1
    private static float parX = 0, parY = 0;
    private static long lastFrameMs = 0;

    // loading overlay state (one reload at a time)
    private static final Matrix4f PIXEL_PROJ = new Matrix4f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static ReloadInstance seenReload = null;
    private static long reloadDoneMs = -1;
    private static float shownProgress = 0;

    /**
     * True if either look can be drawn: the cathedral (the default) or the rose window (its fallback). Uploads on
     * first use (render thread only).
     */
    public static boolean ensureLoaded() {
        return LobbyBackdrop.ensureLoaded() || ensureRoseLoaded();
    }

    private static boolean ensureRoseLoaded() {
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
                int w = img.getWidth(), h = img.getHeight();
                ASPECT[i] = w / (float) h;
                TEX[i] = TextureUtil.generateTextureId();
                loaded = i + 1;
                TextureUtil.prepareImage(TEX[i], w, h);
                // linear filtering (the art is scaled to any screen), clamped edges (no bleed from the far side),
                // and the CPU copy is freed straight after the upload
                img.upload(0, 0, 0, 0, 0, w, h, true, true, false, true);
            }
            state = 1;
        } catch (Throwable e) {
            ReignOfNether.LOGGER.warn("[SplashArt] title art unavailable, keeping the vanilla title screen", e);
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
        InputStream in = SplashArt.class.getClassLoader().getResourceAsStream(path);
        if (in == null)
            throw new FileNotFoundException(path);
        return in;
    }

    /** The reduce-motion setting; read defensively because the loading screen runs before configs may be loaded. */
    private static boolean reduceMotion() {
        try {
            return ReignOfNetherClientConfigs.SPLASH_REDUCE_MOTION.get();
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Draws the whole scene into a w x h area (GUI units on the title screen, framebuffer pixels on the loading screen)
     * through the given pose matrix. mouseX/Y drive the parallax (pass the centre for none).
     */
    public static void renderScene(Matrix4f pose, float w, float h, float mouseX, float mouseY, float alpha) {
        if (alpha <= 0.003f)
            return;
        if (LobbyBackdrop.renderScene(pose, w, h, mouseX, mouseY, alpha, true))
            return;
        if (!ensureRoseLoaded())
            return;
        long now = Util.getMillis();
        boolean motion = !reduceMotion();
        float t = motion ? (now % 3_600_000L) / 1000f : 0f;
        float dt = Math.min(0.1f, Math.max(0f, (now - lastFrameMs) / 1000f));
        lastFrameMs = now;
        float tx = motion ? Mth.clamp(mouseX / w * 2f - 1f, -1f, 1f) : 0f;
        float ty = motion ? Mth.clamp(mouseY / h * 2f - 1f, -1f, 1f) : 0f;
        float k = 1f - (float) Math.exp(-dt * 4f);   // ~0.25 s glide so the layers drift rather than jump
        parX += (tx - parX) * k;
        parY += (ty - parY) * k;
        float cx = w / 2f;

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // deepest layers move most against the mouse, the lettering slightly with it: a gentle depth
        float bh = Math.max(h, w / ASPECT[BACKGROUND]) * 1.04f, bw = bh * ASPECT[BACKGROUND];
        float bx = cx - parX * 0.010f * h, by = h / 2f - parY * 0.008f * h;
        quad(pose, BACKGROUND, bx - bw / 2, by - bh / 2, bx + bw / 2, by + bh / 2, 1f, 1f, 1f, alpha);

        float wx = cx - parX * 0.018f * h, wy = WINDOW_CY * h - parY * 0.012f * h, d = WINDOW_D * h;
        additive();
        float bloom = 0.30f + 0.05f * Mth.sin(t * 0.9f);
        centred(pose, GLOW, wx, wy, d * 1.55f, d * 1.55f, 1f, 0.85f, 0.75f, alpha * bloom);
        RenderSystem.defaultBlendFunc();

        float fh = h, fw = h * ASPECT[FRAME];
        centred(pose, FRAME, cx - parX * 0.013f * h, h / 2f - parY * 0.009f * h, fw, fh, 1f, 1f, 1f, alpha);
        centred(pose, WINDOW, wx, wy, d, d, 1f, 1f, 1f, alpha * 0.94f);

        // light turning slowly through the glass: two beam sets at different speeds shimmer where they cross
        additive();
        rotated(pose, RAYS, wx, wy, d * 0.63f, t * 0.026f, 1f, 0.92f, 0.80f, alpha * (0.20f + 0.05f * Mth.sin(t * 0.6f)));
        rotated(pose, RAYS, wx, wy, d * 0.55f, -t * 0.017f + 0.13f, 0.9f, 0.8f, 1f, alpha * 0.09f);
        // candles low on either side, flickering out of step
        float f1 = 0.30f + 0.05f * Mth.sin(t * 7.3f) + 0.03f * Mth.sin(t * 13.1f + 1f) + 0.03f * Mth.sin(t * 2.9f);
        float f2 = 0.30f + 0.05f * Mth.sin(t * 6.1f + 2f) + 0.03f * Mth.sin(t * 11.7f) + 0.03f * Mth.sin(t * 3.7f + 4f);
        centred(pose, GLOW, 0.07f * w, 0.93f * h, 0.42f * h, 0.42f * h, 1f, 1f, 1f, alpha * f1);
        centred(pose, GLOW, 0.93f * w, 0.93f * h, 0.42f * h, 0.42f * h, 1f, 1f, 1f, alpha * f2);
        RenderSystem.defaultBlendFunc();

        float lx = cx + parX * 0.004f * h, ly = parY * 0.003f * h;
        float th = TITLE_H * h, tw = th * ASPECT[TITLE];
        if (tw > w * 0.96f) { th *= w * 0.96f / tw; tw = w * 0.96f; }   // narrow windows: shrink, never clip
        centred(pose, TITLE, lx, TITLE_CY * h + ly, tw, th, 1f, 1f, 1f, alpha);
        float yh = BYLINE_H * h, yw = yh * ASPECT[BYLINE];
        if (yw > w * 0.96f) { yh *= w * 0.96f / yw; yw = w * 0.96f; }
        centred(pose, BYLINE, lx, BYLINE_CY * h + ly, yw, yh, 1f, 1f, 1f, alpha);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    // ---------------------------------------------------------------- loading screen

    /**
     * Drawn on top of the resource-loading overlay (LoadingOverlayMixin / ForgeLoadingOverlayMixin). vanillaAlpha is the
     * vanilla overlay's own fade; pass a negative value for Forge's launch overlay, whose fade we track ourselves (it
     * starts fading one second after the reload completes and is gone a second later - ForgeLoadingOverlay).
     */
    public static void renderLoading(GuiGraphics graphics, ReloadInstance reload, float vanillaAlpha) {
        if (reload == null || !ensureLoaded())
            return;
        if (reload != seenReload) {
            seenReload = reload;
            reloadDoneMs = -1;
            shownProgress = 0;
        }
        long now = Util.getMillis();
        float alpha;
        if (vanillaAlpha >= 0) {
            alpha = vanillaAlpha;
        } else {
            if (reloadDoneMs < 0 && reload.isDone())
                reloadDoneMs = now;
            float f = reloadDoneMs < 0 ? -1f : (now - reloadDoneMs) / 1000f;
            alpha = 1f - Mth.clamp(f - 1f, 0f, 1f);
        }
        if (alpha <= 0.003f)
            return;
        shownProgress = Mth.clamp(shownProgress * 0.9f + reload.getActualProgress() * 0.1f, 0f, 1f);

        Matrix4f pose;
        float w, h;
        Minecraft mc = Minecraft.getInstance();
        if (vanillaAlpha >= 0) {
            // the vanilla overlay draws in GUI units through GuiGraphics: stay in its space
            graphics.flush();
            pose = graphics.pose().last().pose();
            w = mc.getWindow().getGuiScaledWidth();
            h = mc.getWindow().getGuiScaledHeight();
        } else {
            // Forge's overlay leaves a framebuffer-pixel projection (y up) and an identity model-view: use pixels, y down
            w = mc.getWindow().getWidth();
            h = mc.getWindow().getHeight();
            PIXEL_PROJ.setOrtho(0f, w, h, 0f, -1000f, 1000f);
            RenderSystem.setProjectionMatrix(PIXEL_PROJ, VertexSorting.ORTHOGRAPHIC_Z);
            pose = IDENTITY;
        }
        renderScene(pose, w, h, w / 2f, h / 2f, alpha);

        // a slim gold progress rule low in the meadow, on a soft dark pad so it reads over the flowers
        float cx = w / 2f, bw = h * 0.55f, by = h * 0.905f, bt = Math.max(2f, h * 0.006f);
        LobbyBackdrop.softScrim(pose, cx - bw / 2, by - bt * 4f, cx + bw / 2, by + bt * 4f, alpha * 0.8f);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        fill(pose, cx - bw / 2 - bt, by - bt * 1.5f, cx + bw / 2 + bt, by + bt * 1.5f, 0.75f, 0.58f, 0.27f, alpha);
        fill(pose, cx - bw / 2, by - bt * 0.75f, cx + bw / 2, by + bt * 0.75f, 0.07f, 0.05f, 0.04f, alpha);
        fill(pose, cx - bw / 2, by - bt * 0.75f, cx - bw / 2 + bw * shownProgress, by + bt * 0.75f, 0.96f, 0.80f, 0.42f, alpha);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    // ---------------------------------------------------------------- title screen buttons

    /**
     * Repaints the title screen's text buttons as dark stone with a gold border and a gothic, blood-dripping label
     * (GothicText; lovish Oct 21: "gothic and bold, standing out", "blood dripping down"). Drawn after the buttons have
     * rendered, so clicks, narration and tooltips stay vanilla. Square icon buttons (language, accessibility, our maps
     * button) are left alone.
     */
    public static void restyleButtons(GuiGraphics g, Font font, List<? extends GuiEventListener> children, float alpha) {
        if (state <= 0 && !LobbyBackdrop.available())
            return;
        GothicText.restyleButtons(g, font, children, null, null, true, alpha);
    }

    /**
     * A soft dark pad behind the title screen's column of text buttons (lovish's cathedral is busy; the stone plates
     * must stay readable), feathered well past the column. Only with the cathedral look.
     */
    public static void buttonScrim(GuiGraphics g, List<? extends GuiEventListener> children, float alpha) {
        if (!LobbyBackdrop.available())
            return;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (GuiEventListener c : children) {
            if (c instanceof net.minecraft.client.gui.components.AbstractWidget wd && wd.visible && wd.getWidth() >= 60) {
                x0 = Math.min(x0, wd.getX());
                y0 = Math.min(y0, wd.getY());
                x1 = Math.max(x1, wd.getX() + wd.getWidth());
                y1 = Math.max(y1, wd.getY() + wd.getHeight());
            }
        }
        if (x0 >= x1 || y0 >= y1)
            return;
        g.flush();
        LobbyBackdrop.softScrim(g.pose().last().pose(), x0, y0, x1, y1, alpha * 0.62f);
    }

    // ---------------------------------------------------------------- quads

    private static void additive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    private static void centred(Matrix4f m, int tex, float cx, float cy, float w, float h, float r, float g, float b, float a) {
        quad(m, tex, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, r, g, b, a);
    }

    private static void quad(Matrix4f m, int tex, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(m, x0, y0, 0f).uv(0f, 0f).color(r, g, b, a).endVertex();
        buf.vertex(m, x0, y1, 0f).uv(0f, 1f).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y1, 0f).uv(1f, 1f).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y0, 0f).uv(1f, 0f).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    private static void rotated(Matrix4f m, int tex, float cx, float cy, float half, float angle, float r, float g, float b, float a) {
        float c = Mth.cos(angle) * half, s = Mth.sin(angle) * half;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX[tex]);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // corners (-1,-1) (-1,1) (1,1) (1,-1) rotated by angle
        buf.vertex(m, cx - c + s, cy - s - c, 0f).uv(0f, 0f).color(r, g, b, a).endVertex();
        buf.vertex(m, cx - c - s, cy - s + c, 0f).uv(0f, 1f).color(r, g, b, a).endVertex();
        buf.vertex(m, cx + c - s, cy + s + c, 0f).uv(1f, 1f).color(r, g, b, a).endVertex();
        buf.vertex(m, cx + c + s, cy + s - c, 0f).uv(1f, 0f).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    private static void fill(Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(m, x0, y0, 0f).color(r, g, b, a).endVertex();
        buf.vertex(m, x0, y1, 0f).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y1, 0f).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y0, 0f).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }
}
