package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.perf.FrameBudget;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * War banners: every finished production building (and every capitol) flies its faction's colours from a pole on
 * its highest point, the cloth rippling in segments. The Sunforged Kingdom flies white and gold, the Gravebound
 * soul-blue on black, the Ironhide Horde rust red trimmed in bone. Client-side boxes only; skipped entirely in strategic zoom.
 */
public class BannerRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static final int SEGMENTS = 5;
    private static final float SEG_LEN = 0.32f;
    private static final float CLOTH_HEIGHT = 0.9f;
    private static final float POLE_HEIGHT = 2.4f;
    private static final double MAX_RENDER_DIST_SQR = 160 * 160;
    /** Added to the draw distance (squared) when judging a building's centre: its mount can be this far from it, and then some. */
    private static final double MOUNT_SLACK_SQR = 2 * 160 * 70 + 70 * 70;

    /** placement -> its banner mount (top block), recomputed only if the building changes shape (upgrades). */
    private record Mount(int blockCount, BlockPos pos) { }
    private static final Map<BuildingPlacement, Mount> mounts = new WeakHashMap<>();

    /**
     * Mounts worked out per frame. computeMount walks every block of a placement (tens of thousands for an upgraded
     * lab); a base coming into view, a fog reveal or a join handed it dozens in one frame - the buildingDecor spike of
     * 13.7 ms in lovish's Oct 23 log. One a frame: the banners of a new base appear over a few frames, nobody sees it.
     */
    private static final FrameBudget MOUNT_BUDGET = new FrameBudget(1);

    /** The banner mount, or null when it is not worked out yet and this frame's budget is spent (try again next frame). */
    private static BlockPos mountFor(BuildingPlacement placement) {
        int count = placement.getBlocks().size();
        Mount cached = mounts.get(placement);
        if (cached != null && cached.blockCount() == count)
            return cached.pos();
        if (!MOUNT_BUDGET.tryTake())
            return cached == null ? null : cached.pos();   // an upgraded building keeps its old pole until its turn
        BlockPos pos = computeMount(placement);
        mounts.put(placement, new Mount(count, pos));
        return pos;
    }

    private static BlockPos computeMount(BuildingPlacement p) {
        {
            BlockPos best = null;
            for (BuildingBlock bb : p.getBlocks()) {
                if (bb.getBlockState().isAir())
                    continue;
                BlockPos bp = bb.getBlockPos();
                // highest block; ties broken towards a corner so the pole doesn't skewer the roof ridge centre
                if (best == null || bp.getY() > best.getY()
                        || (bp.getY() == best.getY() && bp.getX() + bp.getZ() < best.getX() + best.getZ()))
                    best = bp;
            }
            return best;
        }
    }

    private static boolean fliesBanner(BuildingPlacement placement) {
        if (!placement.isBuilt || !placement.isExploredClientside)
            return false;
        if (placement.getBuilding() instanceof MetalExtractor)
            return false;
        return placement.getBuilding().isCapitol || placement instanceof ProductionPlacement;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.BUILDING_DECOR);
        try {
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                return;
            if (MC.level == null || MC.cameraEntity == null || StrategicViewClientEvents.isStrategicView())
                return;

            PoseStack matrix = evt.getPoseStack();
            // the RENDER camera, not the camera entity (see WindmillRenderClientEvents: the sails floated off their towers
            // by a camera-dependent amount while they used the entity)
            net.minecraft.world.phys.Vec3 cam = evt.getCamera().getPosition();
            BlockRenderDispatcher renderer = MC.getBlockRenderer();
            float time = MC.level.getGameTime() + evt.getPartialTick();
            BlockState pole = Blocks.DARK_OAK_PLANKS.defaultBlockState();
            boolean renderedAny = false;
            Frustum frustum = evt.getFrustum();
            MOUNT_BUDGET.newFrame();

            for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
                if (!fliesBanner(placement))
                    continue;
                // far buildings: no mount to work out (the pole is within ~60 blocks of the centre)
                if (cam.distanceToSqr(placement.centrePos.getX(), placement.centrePos.getY(), placement.centrePos.getZ())
                        > MAX_RENDER_DIST_SQR + MOUNT_SLACK_SQR)
                    continue;
                BlockPos mount = mountFor(placement);
                if (mount == null || cam.distanceToSqr(mount.getX(), mount.getY(), mount.getZ()) > MAX_RENDER_DIST_SQR)
                    continue;
                // off screen: no light lookup, no pose, none of the pole and cloth boxes
                if (frustum != null && !frustum.isVisible(new AABB(mount.getX() - 2, mount.getY(), mount.getZ() - 2,
                        mount.getX() + 3, mount.getY() + 4, mount.getZ() + 3)))
                    continue;

                // colours per faction in FactionTraits (Sunforged white/gold, Gravebound soul-blue/black, Horde red/bone,
                // Verdant green/silver); factions without an entry fly no banner rather than the Kingdom's
                FactionTraits traits = FactionTraits.of(placement.getBuilding().getFaction());
                BlockState cloth = traits.bannerCloth;
                BlockState trim = traits.bannerTrim;
                if (cloth == null || trim == null)
                    continue;
                int light = LevelRenderer.getLightColor(MC.level, mount.above(2));
                float phase = (mount.hashCode() & 255) / 255f * Mth.TWO_PI;

                matrix.pushPose();
                matrix.translate(mount.getX() + 0.5 - cam.x, mount.getY() + 1.0 - cam.y, mount.getZ() + 0.5 - cam.z);

                // the pole
                matrix.pushPose();
                matrix.translate(-0.06, 0, -0.06);
                matrix.scale(0.12f, POLE_HEIGHT, 0.12f);
                renderer.renderSingleBlock(pole, matrix, MC.renderBuffers().bufferSource(),
                    light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                matrix.popPose();

                // the cloth: segments trailing off the pole, each bending a little further in the wind
                float yaw = Mth.sin(time * 0.013f + phase) * 0.35f;   // slow drift of the wind direction
                matrix.translate(0, POLE_HEIGHT - CLOTH_HEIGHT - 0.05, 0);
                matrix.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
                float x = 0.06f;
                for (int i = 0; i < SEGMENTS; i++) {
                    float wave = Mth.sin(time * 0.22f - i * 0.9f + phase) * 0.09f * (i + 1);
                    float droop = i * 0.03f;
                    matrix.pushPose();
                    matrix.translate(x, -droop, wave - 0.02);
                    matrix.scale(SEG_LEN + 0.01f, CLOTH_HEIGHT, 0.04f);
                    renderer.renderSingleBlock(i == SEGMENTS - 1 ? trim : cloth, matrix,
                        MC.renderBuffers().bufferSource(), light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                    matrix.popPose();
                    x += SEG_LEN;
                }
                matrix.popPose();
                renderedAny = true;
            }
            if (renderedAny)
                MC.renderBuffers().bufferSource().endBatch();
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }
}
