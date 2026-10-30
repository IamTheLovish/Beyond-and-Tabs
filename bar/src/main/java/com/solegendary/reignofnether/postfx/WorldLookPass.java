package com.solegendary.reignofnether.postfx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The world look: the realism floor every player gets without a shader pack (realistic-look notes, Oct 15: packs need
 * Oculus and get distance wrong in the ortho RTS view - we own that projection, so we can get it right). One
 * fullscreen pass, run by WorldLookClientEvents at RenderLevelStageEvent AFTER_LEVEL: the world is finished there
 * (Fabulous transparency composited, particles and weather drawn) and the depth buffer still holds it; the first-person
 * hand, entity outlines and the whole HUD - strategic icons included - are drawn after it and never touched.
 * <ol>
 * <li>Edge smoothing: FXAA, only on pixels where the depth buffer shows a geometry edge - unit boxes, building and
 * terrain outlines stop crawling when the camera pans; textures inside a face are never touched (Oct 15,
 * tools/unit_rts_view.py: 90%+ of a unit's shimmer at RTS zoom is its edges, which the unit mipmaps cannot fix).</li>
 * <li>Distance haze (aerial perspective): view position from depth through the inverse projection, so the RTS ortho
 * camera and the perspective first-person view both work with no near/far assumptions; sky pixels are never hazed.</li>
 * <li>A parametric grade: filmic S on luma, cool shadows / warm highlights, saturation at or below neutral.</li>
 * <li>A faint vignette. No depth of field, deliberately (blur plus a high camera = the toy-miniature look).</li>
 * </ol>
 * Our world-space overlays (selection plates and rings, health bars, range rings, barfx) draw in earlier stages, so they
 * take the haze and grade of the ground under them, like any object there: they stay pixel-sharp (only the edge
 * smoothing moves pixels, and only on geometry edges - a health or build bar, drawn without depth, is softened just
 * where it crosses one), the haze is capped well below opaque, and the grade only reshapes contrast and hue, never
 * saturation upward.
 * <p>
 * The pass draws into its own target, sampling the main colour and depth (it never reads a texture attached to the
 * framebuffer it writes), and blits the result back. It stays off with a shader pack, off when the config says so, and
 * switches itself off for the session with one warn line if anything throws.
 */
public final class WorldLookPass {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bat_postfx");

    // per look, [subtle, full, enchanted]. Haze caps: well below opaque, so far overlays stay legible.
    // Enchanted (lovish Oct 28: "the game needs a shader to dim it down a bit and make it look more saturated and
    // whimsy ... fantasy esque"; he picked "B Enchanted" from three strengths previewed on eyes stills): dimmer, richer
    // dull colours (vibrance, not saturation: team colours and glows keep theirs), golden highlights and warm
    // shadows, a soft glow of the bright areas. The default look since Oct 28.
    // Its shadow tint is warm, not violet-blue (lovish's option B, Oct 28 night): the shadow bell peaks at luma 0.25,
    // where Exposure and the S-curve park the noon lawn, so a blue tint cast the whole terrain blue-green (r34 eyes
    // still, shaders off: hue 82 -> 93, V 0.39 -> 0.32; real grass is hue ~72). Warm shadows and Exposure 0.95 keep
    // it olive and a little dimmer than the look off (tools/test_world_look.py).
    private static final float[] HAZE_MAX = { 0.30f, 0.45f, 0.30f };
    private static final float[] CONTRAST = { 0.10f, 0.18f, 0.18f };     // midtone slope, black and white fixed
    private static final float[] SPLIT_TONE = { 0.5f, 0.9f, 1.2f };
    private static final float[] SATURATION = { 0.96f, 0.92f, 1.0f };    // Subtle / Full stay at or below neutral
    private static final float[] VIGNETTE = { 0.05f, 0.09f, 0.16f };      // the HUD already frames the RTS view
    private static final float[] EXPOSURE = { 1f, 1f, 0.95f };
    private static final float[] VIBRANCE = { 0f, 0f, 0.45f };
    private static final float[] GLOW = { 0f, 0f, 0.30f };
    private static final float[] GLOW_THRESHOLD = { 0.5f, 0.5f, 0.50f };
    private static final float[] GLOW_RADIUS = { 0.02f, 0.02f, 0.016f };  // of the screen height (~14 px at 900)
    private static final float[][] SHADOW_TINT = { { 0.96f, 1f, 1.06f }, { 0.96f, 1f, 1.06f }, { 1.06f, 1f, 0.90f } };
    private static final float[][] HIGHLIGHT_TINT = { { 1.04f, 1f, 0.94f }, { 1.04f, 1f, 0.94f }, { 1.09f, 1f, 0.85f } };
    private static final float HAZE_MAX_LIMIT = 0.85f;            // at 200% haze strength

    // First person: clear for the nearest blocks (the fight in front of you), one e-fold at 180 blocks - a gentle lead
    // into vanilla's own fog, which still owns the render-distance edge.
    private static final float FP_HAZE_START = 12f, FP_HAZE_SCALE = 180f;
    // RTS view: an ortho image has no eye, so distance is measured from the ground at the screen's bottom edge. The
    // ground above it on screen spans zoom * cot(pitch) blocks of depth: ~7 at zoom 10, ~15 at the default 30, ~54 at
    // zoom 200, hundreds at the shallowest tilt. One e-fold per 56 blocks makes that a gentle top-of-screen falloff
    // (haze at the top edge in Subtle: ~4% at zoom 10, ~7% at the default, ~19% zoomed fully out) that grows with the
    // distance on screen - and in the strategic zoom the icons, drawn by the HUD, are not touched at all.
    private static final float RTS_HAZE_SCALE = 56f;
    // the camera's ground estimate is a whole-block average refreshed each tick: chase it over ~0.25 s so panning over
    // hills never pulses the haze; a bigger jump (map change, teleport) snaps
    private static final float GROUND_CHASE_RATE = 4f, GROUND_SNAP = 24f;

    // a scripted override (the eyes tour photographs the same frame with the look off: an exact A/B); null = the setting
    public static Integer lookOverride = null;
    private static ShaderInstance shader = null;    // null until loaded, or after a failed load
    private static Uniform invProjU, hazeColorU, hazeStartU, hazeInvScaleU, hazeMaxU, orthoU;
    private static Uniform contrastU, splitToneU, saturationU, vignetteU, aspectU, edgeAaU;
    private static Uniform exposureU, vibranceU, glowU, glowThresholdU, glowRadiusU, shadowTintU, highlightTintU;
    private static TextureTarget target = null;     // the pass's output, blitted back into the main target
    private static boolean failed = false;          // a render-time error: off until the next resource reload
    private static final int STATE_PACK = 4;        // logState: past DisplaySettings' LOOK_OFF ... LOOK_ENCHANTED
    private static int loggedState = -1;

    private static final Matrix4f invProj = new Matrix4f();
    // vanilla's sky-tracking fog colour this frame (WorldLookClientEvents); plains noon until the first frame
    private static float skyR = 0.75f, skyG = 0.85f, skyB = 1.0f;
    private static float groundY = Float.NaN;
    private static long lastNanos = 0;

    private WorldLookPass() {
    }

    /** Mod bus, from ClientModEvents: loads with vanilla's core shaders, so F3+T reloads it too. */
    public static void registerShader(RegisterShadersEvent evt) {
        // the previous instance is closed by this reload whether or not the new one loads; a reload (F3+T) is also the
        // retry after a render-time failure (a one-off, e.g. a failed allocation on resize) - a repeat costs one line
        shader = null;
        failed = false;
        try {
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION),
                WorldLookPass::onShaderLoaded);
        } catch (Exception e) {   // missing or broken file, GLSL compile error: never take the resource reload down
            ReignOfNether.LOGGER.warn("World look: shader {} did not load, the pass is off: {}", SHADER, e.toString());
        }
    }

    private static void onShaderLoaded(ShaderInstance s) {
        shader = s;
        invProjU = s.getUniform("InvProjMat");
        hazeColorU = s.getUniform("HazeColor");
        hazeStartU = s.getUniform("HazeStart");
        hazeInvScaleU = s.getUniform("HazeInvScale");
        hazeMaxU = s.getUniform("HazeMax");
        orthoU = s.getUniform("Ortho");
        contrastU = s.getUniform("Contrast");
        splitToneU = s.getUniform("SplitTone");
        saturationU = s.getUniform("Saturation");
        vignetteU = s.getUniform("Vignette");
        aspectU = s.getUniform("Aspect");
        edgeAaU = s.getUniform("EdgeAA");
        exposureU = s.getUniform("Exposure");
        vibranceU = s.getUniform("Vibrance");
        glowU = s.getUniform("Glow");
        glowThresholdU = s.getUniform("GlowThreshold");
        glowRadiusU = s.getUniform("GlowRadius");
        shadowTintU = s.getUniform("ShadowTint");
        highlightTintU = s.getUniform("HighlightTint");
    }

    private static ShaderInstance currentShader() {
        return shader;
    }

    private static final float[] NEUTRAL = { 1f, 1f, 1f };
    private static float[] highlight = NEUTRAL;

    /**
     * The highlight tint this pass applied last frame ({1, 1, 1} when it stepped aside or grades none). The valley mist
     * runs before this pass and pre-divides its light by it, so the mist's white leaves the grade white (eyes r54, Oct 29:
     * a golden highlight tint turned the mist cream). A grade that tints highlights sets {@code highlight} where it
     * sets its uniforms.
     */
    public static float[] highlightTint() {
        return highlight;
    }

    /** Vanilla's sky-tracking fog colour this frame (before the RTS view's map-edge override): the valley mist's light. */
    public static float[] skyFog() {
        return new float[] { skyR, skyG, skyB };
    }

    /** Vanilla's fog colour before any override (sky, biome, time of day, weather), captured every frame. */
    static void captureSkyFog(float r, float g, float b) {
        skyR = r;
        skyG = g;
        skyB = b;
    }

    static void render(RenderLevelStageEvent evt) {
        if (failed || shader == null || MC.level == null)
            return;
        int look = lookOverride != null ? lookOverride : DisplaySettings.worldLook();
        boolean pack = look != DisplaySettings.LOOK_OFF && ShaderPacks.inUse();
        logState(pack ? STATE_PACK : look);
        if (look == DisplaySettings.LOOK_OFF || pack) {
            highlight = NEUTRAL;
            return;
        }
        RenderTarget main = MC.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0)   // minimised window
            return;
        int filter = main.filterMode;
        try {
            // the edge smoothing reads the colour between texels: LINEAR for this pass only (texel centres read the
            // same either way, so the rest of the pass is unchanged)
            main.setFilterMode(GL11.GL_LINEAR);
            setUniforms(evt, look == DisplaySettings.LOOK_ENCHANTED ? 2 : look == DisplaySettings.LOOK_FULL ? 1 : 0, main);
            draw(main);
        } catch (Exception | LinkageError e) {
            failed = true;
            StackTraceElement[] st = e.getStackTrace();
            ReignOfNether.LOGGER.warn("World look pass switched off for this session: {} at {}", e.toString(),
                st.length > 0 ? st[0] : "?");
        } finally {
            if (main.filterMode != filter)
                main.setFilterMode(filter);
            // the state the rest of the frame (hand, outlines, HUD) expects from the end of the level render
            main.bindWrite(true);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    // one info line per change (config edit, shader pack toggled), so a CI log says which look the stills were taken in
    private static void logState(int state) {
        if (state == loggedState)
            return;
        loggedState = state;
        ReignOfNether.LOGGER.info("World look: {}", state == STATE_PACK ? "a shader pack is on, the pass steps aside"
            : DisplaySettings.WORLD_LOOK_NAMES[state].toLowerCase(java.util.Locale.ROOT));
    }

    private static void setUniforms(RenderLevelStageEvent evt, int m, RenderTarget main) {
        Matrix4f proj = evt.getProjectionMatrix();
        // no perspective divide (w row 0,0,0,1): the RTS camera's ortho projection (OrthoViewMixin)
        boolean ortho = proj.m03() == 0f && proj.m13() == 0f && proj.m23() == 0f;
        invProj.set(proj).invert();

        float start = ortho ? rtsBottomEdgeDepth(evt.getCamera()) : FP_HAZE_START;
        float scale = ortho ? RTS_HAZE_SCALE : FP_HAZE_SCALE;

        // Haze toward the fog colour the world fades into. The RTS view swaps the fog for a dark ground tone
        // (OrthoviewClientEvents.onFogColor): that is the void past the map edge, not air - hazing toward it would only
        // darken the top of the screen - so there the haze takes vanilla's own sky colour. Anyone else's override (a
        // realm map's haze, underwater fog) is real atmosphere and is used as is.
        float[] fog = RenderSystem.getShaderFogColor();
        float r = fog[0], g = fog[1], b = fog[2];
        if (ortho && OrthoviewClientEvents.isEdgeFogColour(r, g, b)) {
            r = skyR;
            g = skyG;
            b = skyB;
        }

        if (invProjU != null) invProjU.set(invProj);
        if (hazeColorU != null) hazeColorU.set(r, g, b);
        set(hazeStartU, start);
        set(hazeInvScaleU, 1f / scale);
        set(hazeMaxU, Math.min(HAZE_MAX[m] * DisplaySettings.hazeStrength(), HAZE_MAX_LIMIT));
        set(orthoU, ortho ? 1f : 0f);
        set(contrastU, CONTRAST[m]);
        set(splitToneU, SPLIT_TONE[m]);
        set(saturationU, SATURATION[m]);
        set(vignetteU, VIGNETTE[m]);
        set(aspectU, (float) main.width / main.height);
        set(edgeAaU, 1f);   // every look: the edges are the same problem at every strength
        set(exposureU, EXPOSURE[m]);
        set(vibranceU, VIBRANCE[m]);
        set(glowU, GLOW[m]);
        set(glowThresholdU, GLOW_THRESHOLD[m]);
        set(glowRadiusU, GLOW_RADIUS[m]);
        if (shadowTintU != null) shadowTintU.set(SHADOW_TINT[m][0], SHADOW_TINT[m][1], SHADOW_TINT[m][2]);
        if (highlightTintU != null) highlightTintU.set(HIGHLIGHT_TINT[m][0], HIGHLIGHT_TINT[m][1], HIGHLIGHT_TINT[m][2]);
        highlight = HIGHLIGHT_TINT[m];   // the valley mist pre-divides its light by it (highlightTint)
    }

    private static void set(Uniform u, float v) {
        if (u != null)
            u.set(v);
    }

    /**
     * View depth (blocks along the view direction) of the ground at the bottom edge of the RTS view: the point the haze
     * starts from. A point drawn at view-space height y with depth t sits at world height camY + y*cos(p) - t*sin(p)
     * (p = pitch below the horizon); solving for the ground gives t. The bottom edge's y comes from the inverse
     * projection, so zoom and camera shake are already in it.
     */
    private static float rtsBottomEdgeDepth(Camera camera) {
        float pitch = camera.getXRot() * Mth.DEG_TO_RAD;
        float sin = Math.max(Mth.sin(pitch), 0.05f);
        float yBottom = invProj.m31() - invProj.m11();   // inverse projection applied to NDC (0, -1, 0, 1)
        double camY = camera.getPosition().y;
        return (float) ((camY - smoothedGroundY() + yBottom * Mth.cos(pitch)) / sin);
    }

    private static float smoothedGroundY() {
        float target = OrthoviewClientEvents.getGroundY();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f : Math.min((now - lastNanos) / 1e9f, 1f);
        lastNanos = now;
        if (Float.isNaN(groundY) || Math.abs(target - groundY) > GROUND_SNAP)
            groundY = target;
        else
            groundY += (target - groundY) * (1f - (float) Math.exp(-GROUND_CHASE_RATE * dt));
        return groundY;
    }

    private static void draw(RenderTarget main) {
        int w = main.width, h = main.height;
        if (target == null)
            target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        else if (target.width != w || target.height != h)
            target.resize(w, h, Minecraft.ON_OSX);

        shader.setSampler("DiffuseSampler", main);   // a RenderTarget sampler binds its colour texture
        shader.setSampler("DepthSampler", main.getDepthTextureId());

        target.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(WorldLookPass::currentShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, 1.0, 0.0).endVertex();
        bb.vertex(-1.0, 1.0, 0.0).endVertex();
        BufferUploader.drawWithShader(bb.end());

        // back into the main target: a GPU copy, no second draw
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /** Leaving a world: free the full-screen target (rebuilt on the next world's first frame). */
    static void release() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        groundY = Float.NaN;
        lastNanos = 0;
    }
}
