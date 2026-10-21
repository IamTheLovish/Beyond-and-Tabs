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
import net.minecraft.client.model.geom.PartPose;
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
 * their _hand, halo_outer), folded by BuildingParts.MESH_WING_* / MESH_HAND_*, its baked marble and carved-depth
 * occlusion in its vertex colours (MeshCube), drawn through PlainBuffers so Oculus + Embeddium draw it at all. Three
 * LODs: LOD0 only in first person within MESH_LOD0_DISTANCE, LOD1 at the RTS camera's detail zoom, LOD2 beyond. A
 * missing or broken mesh file keeps the cube statue (MeshBody logs why).
 *
 * Cost: one statue per capitol, 864 cubes, drawn only when built and explored, never in strategic zoom, frustum-culled
 * on a cached box, within 260 blocks; its small detail ("lod" parts) hidden past the units' detail zoom
 * (GeneratedBodies.DETAIL_ZOOM) - the winged silhouette is what the RTS camera reads at full zoom-out. Everything a
 * frame needs is worked out on the client tick every 10 ticks. An error switches statues off for the session with one
 * WARN line.
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
    /** First person within this many blocks draws the finest sculpt (the face's drill-fine detail); else LOD1/2. */
    private static final double MESH_LOD0_DISTANCE = 32.0, MESH_LOD1_DISTANCE = 110.0;
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

    /** LOD0 close in first person, LOD1 at the RTS camera's detail zoom (or a middle distance), LOD2 beyond. */
    private static int meshLod(double distSqr) {
        if (OrthoviewClientEvents.isEnabled())
            return OrthoviewClientEvents.getZoom() <= GeneratedBodies.DETAIL_ZOOM ? 1 : 2;
        if (distSqr < MESH_LOD0_DISTANCE * MESH_LOD0_DISTANCE)
            return 0;
        return distSqr < MESH_LOD1_DISTANCE * MESH_LOD1_DISTANCE ? 1 : 2;
    }

    /**
     * A part's fold: its rest rotation (the rig's - the hands' forward yaw that arcs the spread wings round the figure)
     * plus DSL degrees per degree (x, y, z) times the fold in radians, as dsl_to_game.mc_rot turns them - what
     * tools/sculpt_angel.posed adds (rest + fold) for the sheets.
     */
    private static void applyFold(@Nullable ModelPart part, float x, float y, float z, float f) {
        if (part == null)
            return;
        PartPose rest = part.getInitialPose();
        part.xRot = rest.xRot - x * f;
        part.yRot = rest.yRot + y * f;
        part.zRot = rest.zRot - z * f;
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
            s.box = new AABB(s.x - r, s.y - 1, s.z - r, s.x + r, s.y + statue.height() + 1, s.z + r);
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
            float fold = BuildingParts.statueWingFold(s.level, since, animate ? t : 0f) * Mth.DEG_TO_RAD;
            float k = s.statue.scale();
            MeshRig mesh = meshRig(meshLod(dx * dx + dy * dy + dz * dz));
            if (mesh != null) {
                applyFold(mesh.wingL, BuildingParts.MESH_WING_X, BuildingParts.MESH_WING_Y, BuildingParts.MESH_WING_Z, fold);
                applyFold(mesh.wingR, BuildingParts.MESH_WING_X, -BuildingParts.MESH_WING_Y, -BuildingParts.MESH_WING_Z, fold);
                applyFold(mesh.handL, BuildingParts.MESH_HAND_X, BuildingParts.MESH_HAND_Y, BuildingParts.MESH_HAND_Z, fold);
                applyFold(mesh.handR, BuildingParts.MESH_HAND_X, -BuildingParts.MESH_HAND_Y, -BuildingParts.MESH_HAND_Z, fold);
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
