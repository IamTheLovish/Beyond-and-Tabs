package com.solegendary.reignofnether.resources;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * BAR's metal-spot markers: every FREE metal patch carries a small spinning, bobbing raw-iron gem floating above
 * it, drawn fullbright so it reads at the Fallen's midnight as well as at noon, and scaled up in strategic zoom
 * so expansions can be planned from the map. Taken patches (a building on them) lose their gem. Client-only.
 *
 * And BAR's mex circle round every patch (lovish, Oct 16: "Metal extractors are hard to see"): a ring on the ground
 * - pale gold round a free patch (a second ring inside it for a rich one), the owner's colour round a taken one where
 * the client can see it (fog of war: a remembered extractor in the dark gets a grey ring, never its owner's colour),
 * wider in strategic zoom. Lines, so they cost a few hundred vertices for a whole map.
 */
public class MetalPatchMarkerClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    private static final double MAX_DIST_SQR = 260 * 260;
    private static final int FULLBRIGHT = LightTexture.pack(15, 15);
    private static final int RING_SEGMENTS = 32;
    /** The ring round a patch (blocks): the plus of ore and the extractor's footprint sit inside it. */
    private static final float RING = 2.7f, RING_STRATEGIC = 3.6f, RING_RICH_GAP = 0.45f;

    /** The building standing on each patch (patch centre as a long), refreshed every ON_TICKS or when a list changes. */
    private static final java.util.Map<Long, BuildingPlacement> ON = new java.util.HashMap<>();
    private static final int ON_TICKS = 10;
    private static long onAt = Long.MIN_VALUE;
    private static int onBuildings = -1, onPatches = -1;

    /**
     * The building standing on a patch, or null (the client's own list: fog-remembered buildings included). Cached:
     * with metal spread over the whole map (MapLayout.spread) a per-frame patch x building scan, twice a frame (rings
     * and gems), became the marker's main cost.
     */
    static BuildingPlacement buildingOn(BlockPos patch) {
        long t = MC.level == null ? 0 : MC.level.getGameTime();
        java.util.List<BuildingPlacement> buildings = BuildingClientEvents.getBuildings();
        java.util.List<BlockPos> patches = MetalPatchesClient.getPatches();
        if (t - onAt >= ON_TICKS || t < onAt || buildings.size() != onBuildings || patches.size() != onPatches) {
            ON.clear();
            for (BlockPos p : patches)
                for (BuildingPlacement b : buildings)
                    if (Math.abs(b.centrePos.getX() - p.getX()) <= 4 && Math.abs(b.centrePos.getZ() - p.getZ()) <= 4) {
                        ON.put(p.asLong(), b);
                        break;
                    }
            onAt = t;
            onBuildings = buildings.size();
            onPatches = patches.size();
        }
        return ON.get(patch.asLong());
    }

    static boolean isTaken(BlockPos patch) {
        return buildingOn(patch) != null;
    }

    /** The rings, drawn after the entities (where the area-command circle draws its lines too). */
    @SubscribeEvent
    public static void onRenderRings(RenderLevelStageEvent evt) {
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.WORLD_MARKERS);
        try {
            if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
                return;
            if (MC.level == null || MC.cameraEntity == null || !OrthoviewClientEvents.isEnabled())
                return;
            if (MetalPatchesClient.getPatches().isEmpty())
                return;
            Vec3 cam = evt.getCamera().getPosition();
            boolean strategic = StrategicViewClientEvents.isStrategicView();
            float rad = strategic ? RING_STRATEGIC : RING;
            PoseStack pose = evt.getPoseStack();
            VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            boolean drewAny = false;
            for (BlockPos patch : MetalPatchesClient.getPatches()) {
                if (cam.distanceToSqr(patch.getX() + 0.5, patch.getY(), patch.getZ() + 0.5) > MAX_DIST_SQR)
                    continue;
                float r, g, b, a;
                BuildingPlacement on = buildingOn(patch);
                if (on == null) {   // free: pale gold, the colour of the gem's light
                    r = 1.0f; g = 0.86f; b = 0.45f; a = 0.85f;
                } else if (FogOfWarClientEvents.isInBrightChunk(patch)) {
                    int hex = PlayerColors.getPlayerDisplayColorHex(on.ownerName);
                    r = ((hex >> 16) & 255) / 255f; g = ((hex >> 8) & 255) / 255f; b = (hex & 255) / 255f; a = 0.95f;
                } else {             // remembered in the fog: taken, by whom is not ours to know
                    r = 0.62f; g = 0.62f; b = 0.62f; a = 0.7f;
                }
                float cx = patch.getX() + 0.5f, cy = patch.getY() + 1.06f, cz = patch.getZ() + 0.5f;
                ring(vc, m, n, cx, cy, cz, rad, r, g, b, a);
                if (MetalPatchesClient.valueOf(patch) > 1.01f)   // rich (1.5x) and centre (2.0x): a second ring
                    ring(vc, m, n, cx, cy, cz, rad - RING_RICH_GAP, r, g, b, a);
                drewAny = true;
            }
            pose.popPose();
            if (drewAny)
                MC.renderBuffers().bufferSource().endBatch(RenderType.lines());
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Matrix3f n, float cx, float cy, float cz, float rad,
                             float r, float g, float b, float a) {
        float px = cx + rad, pz = cz;
        for (int i = 1; i <= RING_SEGMENTS; i++) {
            double ang = Math.PI * 2 * i / RING_SEGMENTS;
            float x = cx + (float) (Math.cos(ang) * rad), z = cz + (float) (Math.sin(ang) * rad);
            // the lines shader widens a segment along its normal: the normal is the segment's direction
            float dx = x - px, dz = z - pz, len = Mth.sqrt(dx * dx + dz * dz);
            vc.vertex(m, px, cy, pz).color(r, g, b, a).normal(n, dx / len, 0f, dz / len).endVertex();
            vc.vertex(m, x, cy, z).color(r, g, b, a).normal(n, dx / len, 0f, dz / len).endVertex();
            px = x;
            pz = z;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.WORLD_MARKERS);
        try {
            if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                return;
            if (MC.level == null || MC.cameraEntity == null || !OrthoviewClientEvents.isEnabled())
                return;
            if (MetalPatchesClient.getPatches().isEmpty())
                return;

            PoseStack matrix = evt.getPoseStack();
            // the RENDER camera, not the camera entity (WindmillRenderClientEvents: in the RTS view the player is not where
            // the camera is, and translating by the entity's position put the gem somewhere off its patch)
            Vec3 cam = evt.getCamera().getPosition();
            BlockRenderDispatcher renderer = MC.getBlockRenderer();
            BlockState gem = Blocks.RAW_IRON_BLOCK.defaultBlockState();
            float time = MC.level.getGameTime() + evt.getPartialTick();
            boolean strategic = StrategicViewClientEvents.isStrategicView();
            float size = strategic ? 2.2f : 0.7f;   // 0.5 read as a speck at the default zoom (Oct 16)
            boolean renderedAny = false;

            for (BlockPos patch : MetalPatchesClient.getPatches()) {
                if (cam.distanceToSqr(patch.getX(), patch.getY(), patch.getZ()) > MAX_DIST_SQR)
                    continue;
                if (isTaken(patch))
                    continue;
                float phase = (patch.hashCode() & 255) / 255f * Mth.TWO_PI;
                float bob = strategic ? 0 : Mth.sin(time * 0.08f + phase) * 0.12f;

                matrix.pushPose();
                matrix.translate(patch.getX() + 0.5 - cam.x,
                    patch.getY() + 1.9 + bob - cam.y,
                    patch.getZ() + 0.5 - cam.z);
                matrix.mulPose(com.mojang.math.Axis.YP.rotation(time * 0.03f + phase));
                matrix.mulPose(com.mojang.math.Axis.XP.rotation((Mth.HALF_PI / 2)));
                matrix.mulPose(com.mojang.math.Axis.ZP.rotation((Mth.HALF_PI / 2)));
                matrix.translate(-size / 2, -size / 2, -size / 2);
                matrix.scale(size, size, size);
                renderer.renderSingleBlock(gem, matrix, MC.renderBuffers().bufferSource(),
                    FULLBRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
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
