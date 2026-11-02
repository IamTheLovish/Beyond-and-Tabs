package com.solegendary.reignofnether.environment.wind;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.resources.Wind;
import com.solegendary.reignofnether.time.ClientTicks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws a sway limb block under the wind (design/branch_wind.md): the block's own model, tessellated with the level's
 * lighting at the block's real position (the same AO and face light the chunk mesh gives it), translated by
 * {@link WindField#offset}. Only blocks the chunk mesh leaves out are drawn ({@link SwayBlockEntity#meshHidden} -
 * {@link BranchWindClient} decides which, by the setting and the camera distance); the rest cost nothing here.
 * <p>
 * Distance: full amplitude within {@link #FULL_RANGE} blocks of the camera, fading to zero at {@link #SWAY_RANGE} -
 * where BranchWindClient hands the block back to the mesh, so the hand-over happens with the two copies coinciding.
 * The quads go to the shared solid / cutout buffers: one draw per render type for every limb on screen.
 */
public class BranchWindRenderer implements BlockEntityRenderer<SwayBlockEntity> {

    /** Full amplitude within this distance of the camera (blocks). */
    public static final double FULL_RANGE = 48.0;
    /** Zero amplitude, and back in the chunk mesh, beyond this (blocks). */
    public static final double SWAY_RANGE = 60.0;

    private static final Minecraft MC = Minecraft.getInstance();
    private final RandomSource random = RandomSource.create();
    private final double[] off = new double[3];

    public BranchWindRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public boolean shouldRender(SwayBlockEntity be, Vec3 cameraPos) {
        return be.meshHidden;
    }

    @Override
    public int getViewDistance() {
        return 512;   // "All" draws every loaded limb; shouldRender is the real gate
    }

    @Override
    public void render(SwayBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null)
            return;
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        double fade = 1.0;
        if (BranchWindClient.mode() != BranchWindClient.ALL) {
            Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
            double dist = Math.sqrt(cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            fade = (SWAY_RANGE - dist) / (SWAY_RANGE - FULL_RANGE);
        }
        double seconds = ClientTicks.at(partialTicks) / 20.0;
        WindField.offset(pos.getX(), pos.getY(), pos.getZ(), be.tier, seconds, Wind.multiplier(), fade, off);

        poseStack.pushPose();
        poseStack.translate(off[0], off[1], off[2]);
        BlockRenderDispatcher dispatcher = MC.getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(state);
        long seed = state.getSeed(pos);
        random.setSeed(seed);
        for (RenderType rt : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            // ModelData.EMPTY: the real quads (the mesher's call carries the block entity's MESH_HIDDEN flag instead)
            dispatcher.getModelRenderer().tesselateBlock(level, model, state, pos, poseStack,
                buffers.getBuffer(RenderTypeHelper.getMovingBlockRenderType(rt)), true, random, seed,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, rt);
        }
        poseStack.popPose();
    }
}
