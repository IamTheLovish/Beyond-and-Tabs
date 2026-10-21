package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.unit.modelling.mesh.MeshBody;
import com.solegendary.reignofnether.unit.modelling.mesh.PlainBuffers;
import com.solegendary.reignofnether.unit.modelling.models.gen.WhiteAngelStatueModel;
import com.solegendary.reignofnether.unit.modelling.renderers.GeneratedBodies;
import com.solegendary.reignofnether.unit.modelling.renderers.MipRenderTypes;
import com.solegendary.reignofnether.unit.modelling.renderers.MipmappedTextures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;

/**
 * Statues drawn on buildings ({@link BuildingParts#STATUES}): a sculpted figure made with the unit-model tooling
 * (tools/gen_angel_statue.py -> tools/dsl_to_game.py -> the generated WhiteAngelStatueModel and its painted, mipmapped
 * marble atlas) stood on its anchor cell like a part (design/moving_buildings.md: drawn, not built - lovish, Oct 20, on
 * the White Angel capitol: "the angel looks bad"; the 13 x 13 block angel became this). Client side only: nothing is
 * simulated, sent or saved.
 *
 * Motion: the wings unfold as the capitol levels up (BuildingParts.statueWingFold: closed at L1, spread at L6, eased
 * over 6 s after each level-up) and breathe slowly - each wing's root turns back and droops with the fold and its
 * HAND bone folds the primaries along the forearm (BuildingParts.WING_DROOP / WING_HAND, the same numbers
 * tools/gen_angel_statue.fold_pose gives the offline preview); the halo's outer ring ("halo_outer") shows from L6.
 * The "Building animation" setting Off holds the wings still at the level's openness - the statue itself is always
 * drawn, since the building is not whole without it.
 *
 * The mesh angel (lovish, Oct 21: "more fluidity", "a Roman face", "I thought we added a way to add smooth mesh"): when
 * meshes/white_angel_statue.bmesh is loaded (tools/sculpt_angel.py -> tools/mesh_import.py, written by CI before the
 * build) the statue is that smooth marble sculpt instead - the same parts by name (body, wing_left / wing_right and
 * their _hand, halo_outer), folded by BuildingParts.meshFold (keyed, slerped), its baked marble and carved-depth
 * occlusion in its vertex colours (MeshCube), drawn through PlainBuffers so Oculus + Embeddium draw it at all. Three
 * LODs chosen by the statue's pixels per block on screen (MESH_LOD*_MAX_PX), each written as paired quads (two
 * triangles a quad: half the vertices of r1-r9's a b c c) with its colours averaged from LOD0's bake; its carved
 * marble pedestal is a part of the mesh (round 10). A missing or broken mesh file keeps the cube statue (MeshBody logs
 * why).
 *
 * Cost: one statue per capitol, drawn only when built and explored, never in strategic zoom, frustum-culled on a
 * cached box, within 260 blocks, once a frame (AFTER_TRANSLUCENT_BLOCKS) with one batch flush for every statue; a
 * steady frame allocates nothing here (the fold's angles go into one array, PlainBuffers caches its wrappers) - the
 * per-vertex transform in MeshCube.compile is the cost, measured per LOD by StatueDrawBench ([StatueBench] in CI).
 * The cube fallback hides its small detail ("lod" parts) past the units' detail zoom. Everything a frame needs is
 * worked out on the client tick every 10 ticks. An error switches statues off for the session with one WARN line.
 */
public class StatueRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static final double MAX_DIST_SQR = 260 * 260;
    private static final int REFRESH_TICKS = 10;
    private static final String TEXTURE_NAME = "white_angel_statue";

    private static final class State {
        BuildingParts.Statue statue;
        float x, y, z;               // the anchor's floor centre in the world
        float yaw;                   // the placement's rotation
        int level = -1;
        long levelSince = Long.MIN_VALUE;   // the game tick the level changed (a level-up eases the wings open)
        int light;
        AABB box;
        boolean visible;
        float frozenT = Float.NaN;
    }

    private static final Map<BuildingPlacement, State> STATES = new WeakHashMap<>();
    private static final ArrayList<State> CANDIDATES = new ArrayList<>();
    private static long refreshedAt = Long.MIN_VALUE;
    private static boolean disabled = false, firstDrawLogged = false;
    private static WhiteAngelStatueModel<Entity> model;
    private static ModelPart wingLeft, wingRight, handLeft, handRight, haloOuter;
    private static float wingLeftRest, wingRightRest, wingLeftRestZ, wingRightRestZ, handLeftRestZ, handRightRestZ;
    private static RenderType renderType;
    private static final Quaternionf ROT = new Quaternionf();

    // ---- the mesh angel ----
    private static final ResourceLocation MESH_BODY = MeshBody.location("white_angel_statue");
    private static final ResourceLocation MESH_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/entities/white_angel_mesh.png");
    private static final int MESH_LODS = 3;
    /**
     * LODs by the statue's pixels per block on screen (r10). Measured (the PR's notes): LOD0's feather shafts and curls
     * still show at every RTS zoom, so no view got a coarser LOD than r9 drew it - r9's choice (RTS: LOD1 to zoom 36,
     * LOD2 beyond; first person: LOD0 within 32 blocks, LOD1 within 110) is kept, put in pixels so it holds on any
     * screen height and field of view (zoom 36 on a 1080p screen is 30 px a block; 32 blocks at 70 degrees, 24) - and
     * the closest RTS zooms (90+ px a block: the statue fills the screen, no other fits) now draw LOD0, where r9's LOD1
     * was a visible loss. Every LOD writes ~40% fewer vertices than r9's (paired quads).
     */
    static final float RTS_LOD0_PX = 90f, RTS_LOD1_PX = 30f, FP_LOD0_PX = 24f, FP_LOD1_PX = 7f;
    private static final MeshRig[] MESH_RIGS = new MeshRig[MESH_LODS];
    private static int meshGeneration = -1;
    private static RenderType meshRenderType;

    /** One LOD's part tree and the parts that move. */
    private static final class MeshRig {
        final ModelPart root;
        @Nullable final ModelPart wingL, wingR, handL, handR, haloOuter;

        MeshRig(ModelPart root) {
            this.root = root;
            ModelPart body = child(root, "body");
            wingL = child(body, "wing_left");
            wingR = child(body, "wing_right");
            handL = child(wingL, "wing_left_hand");
            handR = child(wingR, "wing_right_hand");
            haloOuter = child(body, "halo_outer");
        }
    }

    @Nullable
    private static ModelPart child(@Nullable ModelPart parent, String name) {
        if (parent == null)
            return null;
        try {
            return parent.getChild(name);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /** The mesh angel at a LOD, or null while its .bmesh is missing (the cube statue is drawn). */
    @Nullable
    private static MeshRig meshRig(int lod) {
        int gen = MeshBody.generation();
        if (gen != meshGeneration) {
            Arrays.fill(MESH_RIGS, null);
            meshGeneration = gen;
        }
        MeshBody body = MeshBody.get(MESH_BODY);
        if (body == null)
            return null;
        MeshRig rig = MESH_RIGS[lod];
        if (rig == null) {
            rig = new MeshRig(body.buildTree(lod));
            MESH_RIGS[lod] = rig;
        }
        if (meshRenderType == null) {
            MipmappedTextures.register(MESH_TEXTURE);
            meshRenderType = MipRenderTypes.body(MESH_TEXTURE);
        }
        return rig;
    }

    /** The LOD for a statue this far away: by its pixels per block on screen (RTS_* / FP_*_PX). */
    private static int meshLod(double distSqr) {
        float px = pixelsPerBlock(distSqr);
        if (OrthoviewClientEvents.isEnabled())
            return px >= RTS_LOD0_PX ? 0 : px >= RTS_LOD1_PX ? 1 : 2;
        return px >= FP_LOD0_PX ? 0 : px >= FP_LOD1_PX ? 1 : 2;
    }

    /**
     * Pixels per block where the statue stands: the RTS view is orthographic (zoom = blocks of view height, as
     * PartRenderClientEvents reads it), first person goes by distance and the field of view.
     */
    static float pixelsPerBlock(double distSqr) {
        float screenH = MC.getWindow().getHeight();
        if (OrthoviewClientEvents.isEnabled())
            return screenH / Math.max(1f, OrthoviewClientEvents.getZoom());
        double halfFov = Math.toRadians(MC.options.fov().get()) / 2;
        return (float) (screenH / (2.0 * Math.max(1.0, Math.sqrt(distSqr)) * Math.tan(halfFov)));
    }

    private static final float[] FOLD_ANGLES = new float[3];

    /**
     * The mesh angel's keyed fold (BuildingParts.meshFold: spread -> raised -> closed, slerped) on a left part and its
     * mirror on the right (x, -y, -z) - what tools/sculpt_angel.posed gives the sheets.
     */
    private static void applyFold(@Nullable ModelPart left, @Nullable ModelPart right, boolean hand, float foldDeg) {
        BuildingParts.meshFold(hand, foldDeg, FOLD_ANGLES);
        if (left != null) {
            left.xRot = FOLD_ANGLES[0];
            left.yRot = FOLD_ANGLES[1];
            left.zRot = FOLD_ANGLES[2];
        }
        if (right != null) {
            right.xRot = FOLD_ANGLES[0];
            right.yRot = -FOLD_ANGLES[1];
            right.zRot = -FOLD_ANGLES[2];
        }
    }

    private static void disable(Throwable e) {
        disabled = true;
        CANDIDATES.clear();
        StackTraceElement[] st = e.getStackTrace();
        ReignOfNether.LOGGER.warn("[Statues] switched off for this session after an error: {}{}", e,
            st.length > 0 ? " at " + st[0] : "");
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.level == null) {
            CANDIDATES.clear();
            STATES.clear();
            refreshedAt = Long.MIN_VALUE;
            return;
        }
        if (disabled)
            return;
        long now = MC.level.getGameTime();
        if (now - refreshedAt < REFRESH_TICKS && now >= refreshedAt)
            return;
        refreshedAt = now;
        try {
            refresh(BuildingClientEvents.getBuildings(), now);
        } catch (RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    private static void refresh(List<BuildingPlacement> buildings, long now) {
        CANDIDATES.clear();
        for (int i = 0; i < buildings.size(); i++) {
            BuildingPlacement p = buildings.get(i);
            if (!p.isBuilt || !p.isExploredClientside || p.originPos == null)
                continue;
            BuildingParts.Statue statue = BuildingParts.statueFor(p.currentStructureName);
            if (statue == null)
                continue;
            State s = STATES.computeIfAbsent(p, k -> new State());
            Rotation rot = p.rotation == null ? Rotation.NONE : p.rotation;
            BlockPos a = statue.anchor();
            s.statue = statue;
            s.yaw = BuildingParts.yawOf(rot);
            // the anchor cell as BuildingUtils.getAbsoluteBlockData places it (rotated, then origin + (0, 1, 0)), its
            // floor centre: where the soles stand
            s.x = p.originPos.getX() + BuildingParts.cellX(rot, a.getX(), a.getZ()) + 0.5f;
            s.y = p.originPos.getY() + 1 + a.getY();
            s.z = p.originPos.getZ() + BuildingParts.cellZ(rot, a.getX(), a.getZ()) + 0.5f;
            int level = BuildingLevels.levelOfStructure(p.currentStructureName);
            if (s.level != level) {   // first seen: settled at its level; a later change: a level-up to ease into
                s.levelSince = s.level < 0 ? Long.MIN_VALUE : now;
                s.level = level;
            }
            float r = statue.halfSpan() + 1f;
            s.box = new AABB(s.x - r, s.y - 1, s.z - r, s.x + r,
                s.y + statue.height() * BuildingParts.STATUE_BOX_HEADROOM + 1, s.z + r);
            s.visible = FogOfWarClientEvents.isInBrightChunk(p.centrePos);
            s.light = LevelRenderer.getLightColor(MC.level, BlockPos.containing(s.x, s.y, s.z));
            CANDIDATES.add(s);
        }
    }

    private static boolean ensureModel() {
        if (model != null)
            return true;
        model = new WhiteAngelStatueModel<>(MC.getEntityModels().bakeLayer(WhiteAngelStatueModel.LAYER_LOCATION));
        wingLeft = model.bone("wing_left");
        wingRight = model.bone("wing_right");
        handLeft = model.bone("wing_left_hand");
        handRight = model.bone("wing_right_hand");
        haloOuter = model.bone("halo_outer");
        model.restPose();
        wingLeftRest = wingLeft == null ? 0f : wingLeft.yRot;
        wingRightRest = wingRight == null ? 0f : wingRight.yRot;
        wingLeftRestZ = wingLeft == null ? 0f : wingLeft.zRot;
        wingRightRestZ = wingRight == null ? 0f : wingRight.zRot;
        handLeftRestZ = handLeft == null ? 0f : handLeft.zRot;
        handRightRestZ = handRight == null ? 0f : handRight.zRot;
        ResourceLocation tex = GeneratedBodies.texture(TEXTURE_NAME);
        renderType = MipRenderTypes.body(tex);
        return true;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (disabled || MC.level == null || CANDIDATES.isEmpty() || StrategicViewClientEvents.isStrategicView())
            return;
        try {
            draw(evt);
        } catch (RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    private static void draw(RenderLevelStageEvent evt) {
        Vec3 cam = evt.getCamera().getPosition();   // the RENDER camera: in the RTS view the player entity is elsewhere
        Frustum frustum = evt.getFrustum();
        boolean animate = com.solegendary.reignofnether.config.DisplaySettings.buildingAnimation()
            != com.solegendary.reignofnether.config.DisplaySettings.ANIM_OFF;
        boolean detail = OrthoviewClientEvents.isEnabled() ? OrthoviewClientEvents.getZoom() <= GeneratedBodies.DETAIL_ZOOM
            : true;
        long tick = MC.level.getGameTime();
        float now = (tick % 72000L) + evt.getPartialTick();
        MultiBufferSource.BufferSource buffers = null;
        boolean meshDrawn = false, cubeDrawn = false;
        PoseStack pose = evt.getPoseStack();
        for (int i = 0, n = CANDIDATES.size(); i < n; i++) {
            State s = CANDIDATES.get(i);
            double dx = s.x - cam.x, dy = s.y - cam.y, dz = s.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DIST_SQR || (frustum != null && !frustum.isVisible(s.box)))
                continue;
            if (buffers == null) {
                ensureModel();
                buffers = MC.renderBuffers().bufferSource();
            }
            if (!firstDrawLogged) {   // proof in a log (an eyes tour's) that a statue was really drawn
                firstDrawLogged = true;
                ReignOfNether.LOGGER.info("[Statues] first statue drawn: {} at {} {} {} ({})", s.statue.structure(), s.x, s.y,
                    s.z, MeshBody.get(MESH_BODY) != null ? "the mesh angel" : "the cube angel");
            }
            float t;
            if (animate && s.visible) {
                t = now;
                s.frozenT = t;
            } else {
                t = Float.isNaN(s.frozenT) ? 0f : s.frozenT;
            }
            float since = s.levelSince == Long.MIN_VALUE ? Float.MAX_VALUE
                : animate ? (tick - s.levelSince) + evt.getPartialTick() : Float.MAX_VALUE;
            float foldDeg = BuildingParts.statueWingFold(s.level, since, animate ? t : 0f);
            float fold = foldDeg * Mth.DEG_TO_RAD;
            float k = s.statue.scale();
            MeshRig mesh = meshRig(meshLod(dx * dx + dy * dy + dz * dz));
            if (mesh != null) {
                applyFold(mesh.wingL, mesh.wingR, false, foldDeg);
                applyFold(mesh.handL, mesh.handR, true, foldDeg);
                if (mesh.haloOuter != null)
                    mesh.haloOuter.visible = s.level >= 6;
                pose.pushPose();
                pose.translate(dx, dy, dz);
                pose.mulPose(ROT.identity().rotateY(s.yaw + Mth.PI));
                pose.scale(-k, -k, k);
                pose.translate(0.0, -1.501, 0.0);
                mesh.root.render(pose, PlainBuffers.of(buffers).getBuffer(meshRenderType), s.light,
                    OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
                pose.popPose();
                meshDrawn = true;
                continue;
            }
            cubeDrawn = true;
            model.restPose();
            model.setDetail(detail);
            // folding turns each wing back about its shoulder blade: in the DSL a negative y turns the left wing's
            // outer edge back (+z), a positive one the right's; ModelPart keeps the DSL's y sign and flips z
            // (dsl_to_game.mc_rot: x and z negated) - so gen_angel_statue.fold_pose's DSL degrees [0, -f, +droop f]
            // for the left wing are yRot - f, zRot - droop f here, and the hand's DSL [0, 0, hand f] is zRot - hand f
            if (wingLeft != null) {
                wingLeft.yRot = wingLeftRest - fold;
                wingLeft.zRot = wingLeftRestZ - BuildingParts.WING_DROOP * fold;
            }
            if (wingRight != null) {
                wingRight.yRot = wingRightRest + fold;
                wingRight.zRot = wingRightRestZ + BuildingParts.WING_DROOP * fold;
            }
            if (handLeft != null)
                handLeft.zRot = handLeftRestZ - BuildingParts.WING_HAND * fold;
            if (handRight != null)
                handRight.zRot = handRightRestZ + BuildingParts.WING_HAND * fold;
            if (haloOuter != null)
                haloOuter.visible = s.level >= 6;

            pose.pushPose();
            pose.translate(dx, dy, dz);
            // the placement's turn, then the entity convention: a model faces -Z, turned 180 to face the structure's
            // front (+z), flipped to Minecraft's y-down model space with its origin 24 px above the soles
            pose.mulPose(ROT.identity().rotateY(s.yaw + Mth.PI));
            pose.scale(-k, -k, k);
            pose.translate(0.0, -1.501, 0.0);
            model.renderToBuffer(pose, buffers.getBuffer(renderType), s.light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            pose.popPose();
        }
        if (buffers != null && cubeDrawn)
            buffers.endBatch(renderType);
        if (buffers != null && meshDrawn)
            buffers.endBatch(meshRenderType);
    }
}
