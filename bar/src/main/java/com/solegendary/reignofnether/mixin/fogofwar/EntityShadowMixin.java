package com.solegendary.reignofnether.mixin.fogofwar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.perf.FrameParts;
import com.solegendary.reignofnether.perf.ShadowCells;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// brightness shading for blocks excluding liquids and flat flace blocks (like tall grass)

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityShadowMixin {

    /**
     * Units stand on the ground in the RTS view: vanilla fades an entity's round shadow by its distance from the
     * CAMERA ((1 - d^2 / 256) x strength, gone past 16 blocks), and the orthographic RTS camera sits far above the
     * field - so no unit ever had a shadow under it there; every body floated over the grass (eyes stills, Oct 16).
     * In the RTS view the distance is taken as 0: the shadow is drawn at its renderer's full strength. Shaders with
     * their own shadows (Iris / Oculus) skip vanilla's anyway. Fog-of-war hiding below still applies.
     */
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;distanceToSqr(DDD)D")
    )
    private double reignofnether$shadowDistance(EntityRenderDispatcher self, double x, double y, double z) {
        if (OrthoviewClientEvents.isEnabled())
            return 0;
        return self.distanceToSqr(x, y, z);
    }

    // when the shadow being drawn started (0: none open); renderShadow runs on the render thread only
    private static long reignofnether$shadowStart = 0;

    @Inject(
            method = "renderShadow",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onRenderShadow(PoseStack pMatrixStack, MultiBufferSource pBuffer, Entity pEntity, float pWeight, float pPartialTicks, LevelReader pLevel, float pSize, CallbackInfo ci) {
        if (OrthoviewClientEvents.isEnabled() &&
            pEntity == Minecraft.getInstance().player) {
            ci.cancel();
            return;
        }
        long start = System.nanoTime();

        if (FogOfWarClientEvents.isEnabled() && !FogOfWarClientEvents.isInBrightChunk(pEntity)) {
            FrameParts.add(FrameParts.ENTITY_SHADOWS, System.nanoTime() - start);
            ci.cancel();
            return;
        }
        reignofnether$shadowStart = start;
    }

    @Shadow
    private static void renderBlockShadow(PoseStack.Pose pPose, VertexConsumer pVertexConsumer, ChunkAccess pChunk, LevelReader pLevel, BlockPos pPos, double pX, double pY, double pZ, float pSize, float pWeight) {
        throw new AssertionError();
    }

    /**
     * renderShadow's call per cell of the entity's block box: a cell that cannot take a shadow (the same tests as
     * renderBlockShadow and Embeddium's replacement: the block below invisible, the light at the cell 3 or less, the
     * block below not a full block or its shape empty) is remembered for the frame (perf/ShadowCells) and skipped for
     * every later entity over it. Any other cell is drawn by renderBlockShadow as before - the same quads.
     */
    @Redirect(
            method = "renderShadow",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderBlockShadow(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;DDDFF)V")
    )
    private static void reignofnether$skipBareCells(PoseStack.Pose pPose, VertexConsumer pVertexConsumer, ChunkAccess pChunk, LevelReader pLevel, BlockPos pPos, double pX, double pY, double pZ, float pSize, float pWeight) {
        long key = pPos.asLong();
        int known = ShadowCells.CLIENT.get(key);
        if (known == ShadowCells.BARE)
            return;
        if (known == ShadowCells.UNKNOWN) {
            BlockPos below = pPos.below();
            BlockState state = pChunk.getBlockState(below);
            boolean bare = state.getRenderShape() == RenderShape.INVISIBLE
                    || pLevel.getMaxLocalRawBrightness(pPos) <= 3
                    || !state.isCollisionShapeFullBlock(pChunk, below)
                    || state.getShape(pChunk, below).isEmpty();
            ShadowCells.CLIENT.put(key, bare);
            if (bare)
                return;
        }
        renderBlockShadow(pPose, pVertexConsumer, pChunk, pLevel, pPos, pX, pY, pZ, pSize, pWeight);
    }

    /** The shadow's block box walked and its quads emitted: its time goes to the [FrameParts] entityShadows section. */
    @Inject(method = "renderShadow", at = @At("RETURN"))
    private static void onRenderShadowEnd(PoseStack pMatrixStack, MultiBufferSource pBuffer, Entity pEntity, float pWeight, float pPartialTicks, LevelReader pLevel, float pSize, CallbackInfo ci) {
        if (reignofnether$shadowStart != 0) {
            FrameParts.add(FrameParts.ENTITY_SHADOWS, System.nanoTime() - reignofnether$shadowStart);
            reignofnether$shadowStart = 0;
        }
    }
}
