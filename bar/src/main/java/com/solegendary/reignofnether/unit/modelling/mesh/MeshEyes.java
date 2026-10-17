package com.solegendary.reignofnether.unit.modelling.mesh;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.modelling.renderers.RadiantPaladinRenderer;
import com.solegendary.reignofnether.unit.reactions.ReactionClientEvents;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.File;
import java.io.IOException;

/**
 * The eyes tour's picture proof of mesh bodies step 1 (inbox/spec-1-mesh-bodies-2026-10-17.md). With -Dbat.eyes=true a
 * Paladin of the tour is drawn twice in the same frame into an offscreen picture - once with its cube body, once with
 * its mesh body, through RadiantPaladinRenderer.forceBody (the very swap -Dreignofnether.meshBodies=paladin makes) -
 * with the same pose, animation, team colour, glow and light, and the two pictures are compared pixel by pixel.
 * Two separate tours never replay a battle (tools/eyes_diff.py compares by regions for that reason), so this
 * same-frame A/B is what "identical" can mean for a whole body in the real client.
 *
 * Three samples (the first Paladin out, one walking, one striking), each saved as
 * bat_eyes_07z_mesh_ab_N_cube.png and _mesh.png (plus _diff.png with every differing pixel in red, if any) and
 * logged as "[Eyes] mesh A/B ...", which the eyes workflow lists. Expected: 0 differing pixels without Embeddium (the
 * plain lane). With Embeddium (the shader lane) the cube body goes through Embeddium's own cuboid writer and the mesh
 * through vanilla's arithmetic, so a float's last bit may move an edge pixel - the log says how many.
 *
 * Off outside eyes runs: nothing here runs in a player's game.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class MeshEyes {
    private MeshEyes() { }

    private static final boolean ENABLED = Boolean.getBoolean("bat.eyes");
    private static final int SAMPLES = 3;
    private static final int FIRST_AFTER = 20 * 90;   // client ticks in a world before the first sample: the army is out
    private static final int SPACING = 20 * 25;       // ticks between samples
    private static final int PREFER_FOR = 20 * 60;    // a sample waits this long for its pose, then takes any Paladin
    private static final int W = 640, H = 720;        // the picture, px
    private static final float PX_PER_BLOCK = 210f;   // the Paladin stands ~2.6 blocks: ~550 px
    private static final int MIN_BODY_PX = 2000;      // fewer body pixels than this proves nothing

    private static int ticks = 0, sample = 0, nextAt = FIRST_AFTER, waitingSince = -1;
    private static int pendingId = -1;
    private static int differing = 0;
    private static boolean done = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (!ENABLED || done || evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        ticks++;
        if (ticks < nextAt || pendingId >= 0)
            return;
        if (waitingSince < 0)
            waitingSince = ticks;
        boolean anyPose = ticks - waitingSince > PREFER_FOR;
        for (Entity e : mc.level.entitiesForRendering()) {
            // not a braced one: each model blends the brace in over its own frames, and the mesh model has drawn none
            if (e instanceof IronGolemUnit unit && unit.isAlive() && !unit.isInvisible()
                    && !ReactionClientEvents.isBraced(unit.getId())
                    && mc.getEntityRenderDispatcher().getRenderer(unit) instanceof RadiantPaladinRenderer
                    && (anyPose || wanted(unit))) {
                pendingId = unit.getId();   // drawn at the end of the next frame (onRenderTick)
                return;
            }
        }
    }

    private static boolean wanted(IronGolemUnit unit) {
        return switch (sample) {
            case 1 -> unit.walkAnimation.speed() > 0.25f;
            case 2 -> unit.getAttackAnimationTick() > 0;
            default -> true;
        };
    }

    private static String poseOf(IronGolemUnit unit) {
        if (unit.getAttackAnimationTick() > 0)
            return "striking";
        return unit.walkAnimation.speed() > 0.25f ? "walking" : "standing";
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (!ENABLED || done || pendingId < 0 || evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        int id = pendingId;
        pendingId = -1;
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        if (!(e instanceof IronGolemUnit unit)
                || !(mc.getEntityRenderDispatcher().getRenderer(unit) instanceof RadiantPaladinRenderer renderer))
            return;   // gone between the tick and the frame: the next tick picks again
        try {
            if (!renderer.meshReady()) {
                ReignOfNether.LOGGER.warn("[Eyes] mesh A/B skipped: the Paladin has no mesh body "
                        + "(meshes/radiant_paladin_unit.bmesh - see the [Mesh] lines)");
                done = true;
                return;
            }
            compare(mc, unit, renderer, evt.renderTickTime);
        } catch (Throwable t) {
            ReignOfNether.LOGGER.error("[Eyes] mesh A/B #{} failed", sample + 1, t);
        } finally {
            renderer.forceBody(null);
            mc.getMainRenderTarget().bindWrite(true);
        }
        sample++;
        waitingSince = -1;
        nextAt = ticks + SPACING;
        if (sample >= SAMPLES) {
            done = true;
            ReignOfNether.LOGGER.info("[Eyes] mesh A/B summary: {} samples, {} differing pixels in all - {}", SAMPLES,
                    differing, differing == 0 ? "the mesh Paladin draws exactly as the cube Paladin" : "NOT identical");
        }
    }

    private static void compare(Minecraft mc, IronGolemUnit unit, RadiantPaladinRenderer renderer, float partialTick)
            throws IOException {
        TextureTarget target = new TextureTarget(W, H, true, Minecraft.ON_OSX);
        NativeImage cube = null, mesh = null, marks = null;
        try {
            target.setClearColor(0.23f, 0.24f, 0.27f, 1f);
            String pose = poseOf(unit);
            renderer.forceBody(false);
            cube = draw(mc, target, unit, partialTick);
            renderer.forceBody(true);
            mesh = draw(mc, target, unit, partialTick);
            int background = cube.getPixelRGBA(0, 0);
            int cubePx = 0, meshPx = 0, diff = 0;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int a = cube.getPixelRGBA(x, y), b = mesh.getPixelRGBA(x, y);
                    if (a != background)
                        cubePx++;
                    if (b != background)
                        meshPx++;
                    if (a != b) {
                        if (marks == null) {
                            marks = new NativeImage(W, H, false);
                            for (int yy = 0; yy < H; yy++)
                                for (int xx = 0; xx < W; xx++)   // the mesh picture at a quarter brightness
                                    marks.setPixelRGBA(xx, yy, (mesh.getPixelRGBA(xx, yy) >>> 2 & 0x003F3F3F) | 0xFF000000);
                        }
                        marks.setPixelRGBA(x, y, 0xFF0000FF);   // ABGR: opaque red
                        diff++;
                    }
                }
            }
            differing += diff;
            String name = "bat_eyes_07z_mesh_ab_" + (sample + 1);
            File dir = new File(mc.gameDirectory, Screenshot.SCREENSHOT_DIR);
            dir.mkdirs();
            cube.writeToFile(new File(dir, name + "_cube.png"));
            mesh.writeToFile(new File(dir, name + "_mesh.png"));
            if (marks != null)
                marks.writeToFile(new File(dir, name + "_diff.png"));
            ReignOfNether.LOGGER.info("[Eyes] mesh A/B #{} (Paladin {}, {}): cube body {} px, mesh body {} px, {} of {} px "
                    + "differ - {}", sample + 1, unit.getId(), pose, cubePx, meshPx, diff, W * H,
                    diff == 0 ? "IDENTICAL" : "DIFFERENT (" + name + "_diff.png)");
            if (cubePx < MIN_BODY_PX)
                ReignOfNether.LOGGER.warn("[Eyes] mesh A/B #{}: only {} body pixels - the Paladin barely shows, not a proof",
                        sample + 1, cubePx);
        } finally {
            if (cube != null)
                cube.close();
            if (mesh != null)
                mesh.close();
            if (marks != null)
                marks.close();
            target.destroyBuffers();
        }
    }

    /** The unit as the inventory screen draws an entity (orthographic, entity light), into the target; read back. */
    private static NativeImage draw(Minecraft mc, TextureTarget target, IronGolemUnit unit, float partialTick) {
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack view = RenderSystem.getModelViewStack();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        float bodyO = unit.yBodyRotO, body = unit.yBodyRot, headO = unit.yHeadRotO, head = unit.yHeadRot;
        float pitchO = unit.xRotO, pitch = unit.getXRot();
        view.pushPose();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, W, H, 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
            view.setIdentity();
            view.translate(0f, 0f, -11000f);
            RenderSystem.applyModelViewMatrix();
            Lighting.setupForEntityInInventory();
            // the same three-quarter view whichever way the unit faces in the world (restored below)
            unit.yBodyRotO = unit.yBodyRot = 155f;
            unit.yHeadRotO = unit.yHeadRot = 155f;
            unit.xRotO = 0f;
            unit.setXRot(0f);
            PoseStack pose = new PoseStack();
            pose.translate(W / 2f, H - 40f, 50f);
            pose.mulPoseMatrix(new Matrix4f().scaling(PX_PER_BLOCK, PX_PER_BLOCK, -PX_PER_BLOCK));
            pose.mulPose(new Quaternionf().rotateZ((float) Math.PI).rotateX(12f * Mth.DEG_TO_RAD));
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(unit, 0.0, 0.0, 0.0, 0f, partialTick, pose, buffers,
                    LightTexture.FULL_BRIGHT));
            buffers.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            unit.yBodyRotO = bodyO;
            unit.yBodyRot = body;
            unit.yHeadRotO = headO;
            unit.yHeadRot = head;
            unit.xRotO = pitchO;
            unit.setXRot(pitch);
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            Lighting.setupFor3DItems();
        }
        return Screenshot.takeScreenshot(target);
    }
}
