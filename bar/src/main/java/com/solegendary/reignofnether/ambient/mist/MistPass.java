package com.solegendary.reignofnether.ambient.mist;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.environment.wind.WindField;
import com.solegendary.reignofnether.fogofwar.FogExploration;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.postfx.ShaderPacks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The valley mist as a volume (r6; shaders/core/bat_mist.fsh): one fullscreen pass at AFTER_LEVEL, just before the
 * world look pass grades the frame (MistClientEvents subscribes it at LOW, the look runs at LOWEST). lovish, Oct 29, on
 * the sprite mist: "the fog needs alot of work ... it needs to be deep and rich and look like its wisping around not
 * just white blotches", and "the fod should be thicker in fog of war compared to not fog of war".
 * <p>
 * Each frame it hands the shader the camera (inverse projection and view rotation, position), the mist floor (the
 * ground the RTS camera works from, chased slowly so panning over a hill never lifts the whole layer), the hour's
 * density times the zoom fade (MistMath), the wind's accumulated drift and heading (WindField: the strands stream the
 * way the trees lean), the light (MistMath.colour against the sky) and a 128 x 128 fog-of-war texture round the camera
 * (2 blocks a texel, refreshed every few ticks): how much thicker the mist lies there (MistMath.fogBoost).
 * <p>
 * With a shader pack the pass steps aside like the world look, and the sprite wisps (MistParticle) stand in; while
 * this pass draws, the sprites stay away (MistClientEvents.mayShow). It switches itself off for the session with one
 * warn line if anything throws.
 */
public final class MistPass {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bat_mist");
    static final int VIS_N = 128, VIS_STEP = 2, VIS_PERIOD = 5;
    /** Wind drift of the noise, blocks a second: a slow walk, the strands visibly stream. */
    static final double DRIFT = 0.9;
    /** Below the ground estimate, blocks: the floor sits a little under the ground so open ground gets a veil. */
    static final float FLOOR_BELOW = 0.5f;
    private static final float FLOOR_CHASE = 0.4f, FLOOR_SNAP = 24f;

    private static ShaderInstance shader = null;
    private static boolean failed = false;
    private static Uniform invProjU, invViewU, camPosU, orthoU, floorU, heightU, sigmaU, densityU, offsetU, windU, timeU;
    private static Uniform litU, deepU, visOriginU, visInvSizeU, fogMaxU, maxCoverU;
    private static TextureTarget target = null;
    private static DynamicTexture vis = null;
    private static int visX = 0, visZ = 0;
    private static long visTick = Long.MIN_VALUE;
    private static double offX = 0, offZ = 0;
    private static long lastNanos = 0;
    private static float floor = Float.NaN;
    private static boolean drawing = false;
    private static final Matrix4f invProj = new Matrix4f(), invView = new Matrix4f();

    private MistPass() {
    }

    /** Mod bus, beside the world look's shader (ClientModEvents.registerShaders). */
    public static void registerShader(RegisterShadersEvent evt) {
        shader = null;
        failed = false;
        try {
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION),
                MistPass::onShaderLoaded);
        } catch (Exception e) {
            ReignOfNether.LOGGER.warn("Valley mist: shader {} did not load, the sprites stand in: {}", SHADER, e.toString());
        }
    }

    private static void onShaderLoaded(ShaderInstance s) {
        shader = s;
        invProjU = s.getUniform("InvProjMat");
        invViewU = s.getUniform("InvViewMat");
        camPosU = s.getUniform("CamPos");
        orthoU = s.getUniform("Ortho");
        floorU = s.getUniform("Floor");
        heightU = s.getUniform("Height");
        sigmaU = s.getUniform("Sigma");
        densityU = s.getUniform("Density");
        offsetU = s.getUniform("Offset");
        windU = s.getUniform("WindDir");
        timeU = s.getUniform("Time");
        litU = s.getUniform("LitColor");
        deepU = s.getUniform("DeepColor");
        visOriginU = s.getUniform("VisOrigin");
        visInvSizeU = s.getUniform("VisInvSize");
        fogMaxU = s.getUniform("FogBoostMax");
        maxCoverU = s.getUniform("MaxCover");
    }

    /** The volume can draw this frame: loaded, no shader pack, the mist allowed (setting, eyes rig), the overworld. */
    public static boolean available() {
        return shader != null && !failed && MC.level != null && MistClientEvents.enabled()
            && MC.level.dimension() == Level.OVERWORLD && !ShaderPacks.inUse();
    }

    /** The volume drew the last frame (the sprites stay away meanwhile). */
    public static boolean drawing() {
        return drawing;
    }

    static void render(RenderLevelStageEvent evt) {
        drawing = false;
        if (!available() || MC.level.isRaining())
            return;
        boolean ortho = OrthoviewClientEvents.isEnabled();
        boolean strategic = StrategicViewClientEvents.isStrategicView();
        double density = MistMath.density(MC.level.getDayTime())
            * MistMath.zoomFade(ortho ? OrthoviewClientEvents.getZoom() : 20f, strategic);
        if (density <= 0.001)
            return;
        RenderTarget main = MC.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0)
            return;
        try {
            setUniforms(evt, ortho, (float) density);
            draw(main);
            drawing = true;
        } catch (Exception | LinkageError e) {
            failed = true;
            StackTraceElement[] st = e.getStackTrace();
            ReignOfNether.LOGGER.warn("Valley mist volume switched off for this session: {} at {}", e.toString(),
                st.length > 0 ? st[0] : "?");
        } finally {
            main.bindWrite(true);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void setUniforms(RenderLevelStageEvent evt, boolean ortho, float density) {
        Camera camera = evt.getCamera();
        Vec3 cam = camera.getPosition();
        invProj.set(evt.getProjectionMatrix()).invert();
        invView.set(evt.getPoseStack().last().pose()).invert();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min((now - lastNanos) / 1e9f, 0.25f);
        lastNanos = now;
        double seconds = MC.level.getGameTime() / 20.0 + MC.getFrameTime() / 20.0;

        // the floor: the ground the camera works from (RTS) or under the eye (first person), chased slowly
        float ground = ortho ? OrthoviewClientEvents.getGroundY() : (float) Math.floor(MC.player != null ? MC.player.getY() : cam.y);
        if (Float.isNaN(floor) || Math.abs(ground - floor) > FLOOR_SNAP)
            floor = ground;
        else
            floor += (ground - floor) * (1f - (float) Math.exp(-FLOOR_CHASE * dt));

        // the wind: the strands drift and stretch the way the trees lean
        double heading = WindField.heading(seconds);
        double wx = Math.cos(heading), wz = Math.sin(heading);
        offX = (offX - wx * DRIFT * dt) % 4096.0;
        offZ = (offZ - wz * DRIFT * dt) % 4096.0;

        // the light: the hour's mist colour (cool by day, warm at dusk, dim at night); the core cooler and darker
        float[] c = MistMath.colour(MC.level.getDayTime());
        if (invProjU != null) invProjU.set(invProj);
        if (invViewU != null) invViewU.set(invView);
        if (camPosU != null) camPosU.set((float) cam.x, (float) cam.y, (float) cam.z);
        set(orthoU, ortho ? 1f : 0f);
        set(floorU, floor - FLOOR_BELOW);
        set(heightU, (float) MistMath.HEIGHT);
        set(sigmaU, (float) MistMath.SIGMA);
        set(densityU, density);
        if (offsetU != null) offsetU.set((float) offX, 0f, (float) offZ);
        if (windU != null) windU.set((float) wx, (float) wz);
        set(timeU, (float) (seconds % 2048.0));
        if (litU != null) litU.set(c[0], c[1], c[2]);
        if (deepU != null) deepU.set(c[0] * 0.56f, c[1] * 0.63f, c[2] * 0.76f);
        set(fogMaxU, (float) MistMath.FOG_UNEXPLORED);
        set(maxCoverU, (float) MistMath.MAX_COVER);
        updateVis(cam);
        if (visOriginU != null) visOriginU.set((float) visX, (float) visZ);
        set(visInvSizeU, 1f / (VIS_N * VIS_STEP));
    }

    private static void set(Uniform u, float v) {
        if (u != null)
            u.set(v);
    }

    /**
     * The fog-of-war texture round the camera's ground point: r = 0 in sight, else (boost - 1) / (FOG_UNEXPLORED - 1)
     * - 0.6 over explored ground, 1 over ground never explored. All zero with the fog of war off. Every VIS_PERIOD
     * ticks, or when the camera has moved a quarter of the texture.
     */
    private static void updateVis(Vec3 cam) {
        if (vis == null) {
            vis = new DynamicTexture(VIS_N, VIS_N, false);
            vis.setFilter(true, false);   // linear: the edge of the fog is a soft rise, not texel steps
        }
        double[] f = OrthoviewClientEvents.isEnabled()
            ? MistMath.focus(cam.x, cam.y, cam.z, OrthoviewClientEvents.getCamRotX(), OrthoviewClientEvents.getCamRotY(),
                OrthoviewClientEvents.getGroundY())
            : new double[] { cam.x, cam.y, cam.z };
        int half = VIS_N * VIS_STEP / 2;
        int nx = (int) Math.floor(f[0] / VIS_STEP) * VIS_STEP - half, nz = (int) Math.floor(f[2] / VIS_STEP) * VIS_STEP - half;
        long tick = MC.level.getGameTime();
        boolean moved = Math.abs(nx - visX) > half / 2 || Math.abs(nz - visZ) > half / 2;
        if (!moved && tick - visTick < VIS_PERIOD && tick >= visTick)
            return;
        visTick = tick;
        visX = nx;
        visZ = nz;
        NativeImage img = vis.getPixels();
        if (img == null)
            return;
        boolean fogOn = FogOfWarClientEvents.isEnabled();
        double span = MistMath.FOG_UNEXPLORED - 1;
        for (int i = 0; i < VIS_N; i++)
            for (int j = 0; j < VIS_N; j++) {
                int x = visX + i * VIS_STEP + VIS_STEP / 2, z = visZ + j * VIS_STEP + VIS_STEP / 2;
                int r = 0;
                if (fogOn) {
                    boolean visible = FogOfWarClientEvents.isBlockVisible(x, z);
                    double b = MistMath.fogBoost(true, visible, visible || FogExploration.isExplored(x >> 4, z >> 4));
                    r = (int) Math.round(255 * (b - 1) / span);
                }
                img.setPixelRGBA(i, j, 0xFF000000 | r);   // ABGR: r in the low byte
            }
        vis.upload();
    }

    private static void draw(RenderTarget main) {
        int w = main.width, h = main.height;
        if (target == null)
            target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        else if (target.width != w || target.height != h)
            target.resize(w, h, Minecraft.ON_OSX);

        shader.setSampler("DiffuseSampler", main);
        shader.setSampler("DepthSampler", main.getDepthTextureId());
        shader.setSampler("VisSampler", vis);

        target.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(MistPass::currentShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, 1.0, 0.0).endVertex();
        bb.vertex(-1.0, 1.0, 0.0).endVertex();
        BufferUploader.drawWithShader(bb.end());

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static ShaderInstance currentShader() {
        return shader;
    }

    /** On logout: the targets go, the floor and drift start again. */
    static void release() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        floor = Float.NaN;
        lastNanos = 0;
        drawing = false;
    }
}
