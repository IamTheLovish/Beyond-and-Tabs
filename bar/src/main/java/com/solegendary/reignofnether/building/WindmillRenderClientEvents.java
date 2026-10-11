package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The windmill's sails: every Wind Generator declares its rotor hub (WindGenerator.SAIL_HUBS, checked against the
 * structures by tools/check_sail_hubs.py) and this renderer spins four cloth blades round it once the mill is built,
 * each mill with its own phase so a wind farm doesn't march in lockstep. Pure client-side rendering - no block
 * updates, no server cost; the hub is worked out once per placement.
 */
public class WindmillRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static final float SPIN_RADS_PER_TICK = 0.045f;  // a lazy ~8s per turn
    private static final float BLADE_WIDTH = 0.8f;    // wider sails: at 0.52 they read as thin sticks from the RTS camera
    // playtest (Oct 10): seen from the RTS camera a vertical rotor is nearly edge-on and the sails looked like
    // broken sticks that didn't turn. Tilt the rotor back so its face points up towards the camera.
    private static final float ROTOR_TILT_DEG = 40f;
    private static final float BLADE_THICKNESS = 0.14f;
    private static final double MAX_RENDER_DIST_SQR = 220 * 220;

    /** A mill's hub in world space, worked out once per placement (origin + rotated SailHub offset). */
    private record Hub(BlockPos pos, Direction outward, float length, BlockState cloth, BlockState arm) { }
    private static final Map<BuildingPlacement, Hub> HUBS = new WeakHashMap<>();

    /**
     * The world hub of a mill: the declared SailHub rotated with the placement exactly as
     * BuildingUtils.getAbsoluteBlockData rotates the structure (BlockPos.rotate, then origin + (0, 1, 0)).
     */
    private static Hub hubOf(BuildingPlacement placement, WindGenerator mill) {
        Hub hub = HUBS.get(placement);
        if (hub != null)
            return hub;
        WindGenerator.SailHub sh = mill.getSailHub();
        if (sh == null || placement.originPos == null)
            return null;
        Rotation rot = placement.rotation == null ? Rotation.NONE : placement.rotation;
        BlockPos rel = sh.pos().rotate(rot);
        BlockPos pos = placement.originPos.offset(rel.getX(), rel.getY() + 1, rel.getZ());
        // each faction flies its own sails: Sunforged white canvas on spruce, Gravebound grey rags on dark oak,
        // the Ironhide Horde rust-red hides on dark timber, Verdant leaf-green on birch, Tidewrought teal sailcloth
        // on spruce spars, Stormcallers jade vanes on red-lacquer spars
        BlockState cloth, arm;
        switch (mill.variant) {
            case "_nether" -> { cloth = Blocks.RED_WOOL.defaultBlockState(); arm = Blocks.DARK_OAK_PLANKS.defaultBlockState(); }
            case "_dark" -> { cloth = Blocks.LIGHT_GRAY_WOOL.defaultBlockState(); arm = Blocks.DARK_OAK_PLANKS.defaultBlockState(); }
            case "_verdant" -> { cloth = Blocks.GREEN_WOOL.defaultBlockState(); arm = Blocks.BIRCH_PLANKS.defaultBlockState(); }
            case "_storm" -> { cloth = Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState(); arm = Blocks.RED_TERRACOTTA.defaultBlockState(); }
            case "_tide" -> { cloth = Blocks.CYAN_WOOL.defaultBlockState(); arm = Blocks.SPRUCE_PLANKS.defaultBlockState(); }
            default -> { cloth = Blocks.WHITE_WOOL.defaultBlockState(); arm = Blocks.SPRUCE_PLANKS.defaultBlockState(); }
        }
        hub = new Hub(pos, rot.rotate(sh.outward()), sh.length(), cloth, arm);
        HUBS.put(placement, hub);
        return hub;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (MC.level == null || MC.cameraEntity == null)
            return;
        // at strategic zoom the sails are subpixel; skip them entirely (zoom-out performance)
        if (com.solegendary.reignofnether.orthoview.StrategicViewClientEvents.isStrategicView())
            return;

        PoseStack matrix = evt.getPoseStack();
        // the RENDER camera: in the RTS view the camera entity (the player) is nowhere near it, and translating by the
        // entity's position is what made the sails float off their towers by a camera-dependent amount
        Vec3 cam = evt.getCamera().getPosition();
        BlockRenderDispatcher renderer = MC.getBlockRenderer();
        float time = MC.level.getGameTime() + evt.getPartialTick();
        boolean renderedAny = false;

        for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
            if (!(placement.getBuilding() instanceof WindGenerator mill)
                    || !placement.isBuilt || !placement.isExploredClientside)
                continue;
            Hub h = hubOf(placement, mill);
            if (h == null)
                continue;
            BlockPos hub = h.pos();
            if (cam.distanceToSqr(hub.getX() + 0.5, hub.getY() + 0.5, hub.getZ() + 0.5) > MAX_RENDER_DIST_SQR)
                continue;
            // only on a standing mill: a site being rebuilt (or the hub knocked out) has no rotor to turn
            if (MC.level.getBlockState(hub).isAir())
                continue;

            Direction.Axis axis = h.outward().getAxis();
            BlockState bladeCloth = h.cloth();
            BlockState bladeArm = h.arm();
            float bladeLength = h.length();
            // rotor plane sits just outside the hub's outward face
            double outward = h.outward().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.95 : -0.95;

            float phase = (hub.hashCode() & 255) / 255f * Mth.TWO_PI;
            float angle = time * SPIN_RADS_PER_TICK + phase;
            int light = LevelRenderer.getLightColor(MC.level, hub.above());

            matrix.pushPose();
            matrix.translate(
                hub.getX() + 0.5 + (axis == Direction.Axis.X ? outward : 0) - cam.x,
                hub.getY() + 0.5 - cam.y,
                hub.getZ() + 0.5 + (axis == Direction.Axis.Z ? outward : 0) - cam.z);
            // lean the rotor back (top towards the tower) so the sails' face points up at the sky and the camera
            if (axis == Direction.Axis.Z)
                matrix.mulPose(com.mojang.math.Axis.XP.rotationDegrees(outward > 0 ? -ROTOR_TILT_DEG : ROTOR_TILT_DEG));   // +X turns +Y toward +Z
            else
                matrix.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(outward > 0 ? ROTOR_TILT_DEG : -ROTOR_TILT_DEG));   // +Z turns +Y toward -X

            for (int i = 0; i < 4; i++) {
                matrix.pushPose();
                if (axis == Direction.Axis.Z)
                    matrix.mulPose(com.mojang.math.Axis.ZP.rotation(angle + i * Mth.HALF_PI));
                else
                    matrix.mulPose(com.mojang.math.Axis.XP.rotation(angle + i * Mth.HALF_PI));

                // the cloth sail: a long box reaching out from the hub along local +Y
                matrix.pushPose();
                if (axis == Direction.Axis.Z) {
                    matrix.translate(-BLADE_WIDTH / 2, 0.35, -BLADE_THICKNESS / 2);
                    matrix.scale(BLADE_WIDTH, bladeLength, BLADE_THICKNESS);
                } else {
                    matrix.translate(-BLADE_THICKNESS / 2, 0.35, -BLADE_WIDTH / 2);
                    matrix.scale(BLADE_THICKNESS, bladeLength, BLADE_WIDTH);
                }
                renderer.renderSingleBlock(bladeCloth, matrix, MC.renderBuffers().bufferSource(),
                    light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                matrix.popPose();

                // the wooden spar running up the middle of the sail
                matrix.pushPose();
                if (axis == Direction.Axis.Z) {
                    matrix.translate(-0.09, 0.0, -BLADE_THICKNESS / 2 - 0.02);
                    matrix.scale(0.18f, bladeLength + 0.4f, BLADE_THICKNESS + 0.04f);
                } else {
                    matrix.translate(-BLADE_THICKNESS / 2 - 0.02, 0.0, -0.09);
                    matrix.scale(BLADE_THICKNESS + 0.04f, bladeLength + 0.4f, 0.18f);
                }
                renderer.renderSingleBlock(bladeArm, matrix, MC.renderBuffers().bufferSource(),
                    light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                matrix.popPose();

                matrix.popPose();
            }
            matrix.popPose();
            renderedAny = true;
        }
        if (renderedAny)
            MC.renderBuffers().bufferSource().endBatch();
    }
}
