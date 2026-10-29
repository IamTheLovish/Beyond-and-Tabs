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
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static Uniform litU, deepU, visOriginU, visInvSizeU, fogMaxU, maxCoverU, valleyBaseU;
    /** r11: where the mist lies (ValleyField) - recomputed when the camera moves or every VALLEY_PERIOD ticks. */
    static final int VALLEY_PERIOD = 40;
    private static ValleyField.Result valley = null;
    private static int valleyBase = 64, valleyX = Integer.MIN_VALUE, valleyZ = Integer.MIN_VALUE;
    private static long valleyTick = Long.MIN_VALUE;
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
        valleyBaseU = s.getUniform("ValleyBase");
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
        // the view rotation from the camera itself, as GameRenderer.renderLevel builds it: X by the pitch, then Y by the
        // yaw + 180. Not the event's pose stack: in the RTS view it carries no rotation (the first in-game r6 still drew
        // the mist as a band across the bottom of the screen - "height" was the screen's own up)
        invView.identity().rotateX(camera.getXRot() * Mth.DEG_TO_RAD).rotateY((camera.getYRot() + 180f) * Mth.DEG_TO_RAD)
            .invert();
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
        // the mist scatters the sky's own light: its top takes a share of the sky colour (rose-gold at dawn, blue by
        // day), its core stays the cool, dim body - so dawn mist glows instead of reading as grey paint
        float[] sky = com.solegendary.reignofnether.postfx.WorldLookPass.skyFog();
        float lr = Math.min(0.97f, c[0] * 0.65f + sky[0] * 0.4f), lg = Math.min(0.97f, c[1] * 0.65f + sky[1] * 0.4f),
            lb = Math.min(0.97f, c[2] * 0.65f + sky[2] * 0.4f);
        // r11 (the approved paintover): lit tops a warm off-white, the shaded body a cool lavender-grey - r8-r10's tops
        // took 40% of the sky's blue by day and read as a grey-blue film. The hour's colour still leads at dawn and dusk
        float warm = 0.75f;   // r11b 0.55 -> 0.75: r48's tops read pale blue over the water, not lit white
        lr = Math.min(0.97f, lr * (1 - warm) + 0.95f * warm);
        lg = Math.min(0.97f, lg * (1 - warm) + 0.92f * warm);
        lb = Math.min(0.97f, lb * (1 - warm) + 0.84f * warm);
        float cl = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
        if (litU != null) litU.set(lr, lg, lb);
        if (deepU != null) deepU.set(0.70f * cl / 0.9f, 0.73f * cl / 0.9f, 0.80f * cl / 0.9f);
        set(fogMaxU, (float) MistMath.FOG_UNEXPLORED);
        set(maxCoverU, (float) MistMath.MAX_COVER);
        updateVis(cam);
        set(valleyBaseU, (float) valleyBase);
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
        // the first call always draws (review, Oct 29: tick - Long.MIN_VALUE overflowed negative, so a camera starting
        // near 0, 0 never uploaded and the shader read an undefined texture)
        if (visTick != Long.MIN_VALUE && !moved && tick - visTick < VIS_PERIOD && tick >= visTick)
            return;
        visTick = tick;
        visX = nx;
        visZ = nz;
        NativeImage img = vis.getPixels();
        if (img == null)
            return;
        if (valley == null || nx != valleyX || nz != valleyZ || tick - valleyTick >= VALLEY_PERIOD || tick < valleyTick)
            updateValley(nx, nz, tick);
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
                int g = (int) Math.round(255 * valley.cover[i][j]);
                int b = ValleyField.encodeHeight(valley.smooth[i][j], valleyBase);
                img.setPixelRGBA(i, j, 0xFF000000 | (b << 16) | (g << 8) | r);   // ABGR: r fog, g cover, b ground
            }
        vis.upload();
        // r11b (eyes r48: the mist drawn as hard 2 x 2-block squares): NativeImage's upload sets the texture's filter
        // back to nearest every time, so the linear filter set once at creation held only until the first refresh -
        // the cover, the ground and the fog-of-war channel all read texel by texel. Set it again after each upload
        vis.setFilter(true, false);
    }

    /**
     * The columns of the texture's grid: the ground without leaves (the water's surface on water) and whether it is
     * water, then ValleyField. A column in an unloaded chunk takes the camera's ground and counts as dry.
     */
    private static void updateValley(int nx, int nz, long tick) {
        valleyX = nx;
        valleyZ = nz;
        valleyTick = tick;
        valleyBase = (int) Math.floor(OrthoviewClientEvents.isEnabled() ? OrthoviewClientEvents.getGroundY()
            : (MC.player != null ? MC.player.getY() : 64));
        int[][] h = new int[VIS_N][VIS_N];
        boolean[][] w = new boolean[VIS_N][VIS_N];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int min = MC.level.getMinBuildHeight();
        for (int i = 0; i < VIS_N; i++)
            for (int j = 0; j < VIS_N; j++) {
                int x = nx + i * VIS_STEP + VIS_STEP / 2, z = nz + j * VIS_STEP + VIS_STEP / 2;
                int top = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (top <= min) {
                    h[i][j] = valleyBase;
                    continue;
                }
                h[i][j] = top;
                w[i][j] = MC.level.getFluidState(p.set(x, top, z)).is(FluidTags.WATER);
            }
        valley = ValleyField.compute(h, w, VIS_STEP);
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
        visTick = Long.MIN_VALUE;
        valley = null;
        valleyTick = Long.MIN_VALUE;
    }
}
