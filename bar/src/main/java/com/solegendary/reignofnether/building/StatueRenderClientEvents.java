package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.unit.modelling.models.gen.WhiteAngelStatueModel;
import com.solegendary.reignofnether.unit.modelling.renderers.GeneratedBodies;
import com.solegendary.reignofnether.unit.modelling.renderers.MipRenderTypes;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Statues drawn on buildings ({@link BuildingParts#STATUES}): a sculpted figure made with the unit-model tooling
 * (tools/gen_angel_statue.py -> tools/dsl_to_game.py -> the generated WhiteAngelStatueModel and its painted, mipmapped
 * marble atlas) stood on its anchor cell like a part (design/moving_buildings.md: drawn, not built - lovish, Oct 20, on
 * the White Angel capitol: "the angel looks bad"; the 13 x 13 block angel became this). Client side only: nothing is
 * simulated, sent or saved.
 *
 * Motion: the wings unfold as the capitol levels up (BuildingParts.statueWingFold: half open at L1, spread at L6, eased
 * over 6 s after each level-up) and breathe slowly; the "Building animation" setting Off holds them still at the
 * level's openness - the statue itself is always drawn, since the building is not whole without it.
 *
 * Cost: one statue per capitol, 662 cubes, drawn only when built and explored, never in strategic zoom, frustum-culled
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
    private static ModelPart wingLeft, wingRight;
    private static float wingLeftRest, wingRightRest;
    private static RenderType renderType;
    private static final Quaternionf ROT = new Quaternionf();

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
        model.restPose();
        wingLeftRest = wingLeft == null ? 0f : wingLeft.yRot;
        wingRightRest = wingRight == null ? 0f : wingRight.yRot;
        ResourceLocation tex = GeneratedBodies.texture(TEXTURE_NAME);
        renderType = MipRenderTypes.body(tex);
        return true;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.BUILDING_DECOR);
        try {
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                return;
            if (disabled || MC.level == null || CANDIDATES.isEmpty() || StrategicViewClientEvents.isStrategicView())
                return;
            try {
                draw(evt);
            } catch (RuntimeException | LinkageError e) {
                disable(e);
            }
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
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
                ReignOfNether.LOGGER.info("[Statues] first statue drawn: {} at {} {} {}", s.statue.structure(), s.x, s.y, s.z);
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
            model.restPose();
            model.setDetail(detail);
            // folding turns each wing back about its shoulder blade: in the DSL a negative y turns the left wing's
            // outer edge back (+z), a positive one the right's; ModelPart keeps the DSL's y sign (dsl_to_game.mc_rot)
            if (wingLeft != null)
                wingLeft.yRot = wingLeftRest - fold;
            if (wingRight != null)
                wingRight.yRot = wingRightRest + fold;

            float k = s.statue.scale();
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
        if (buffers != null)
            buffers.endBatch(renderType);
    }
}
