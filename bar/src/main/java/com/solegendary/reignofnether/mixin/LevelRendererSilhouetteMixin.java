package com.solegendary.reignofnether.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.orthoview.CanopySilhouettes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unit silhouettes (orthoview/CanopySilhouettes): a unit under cover has its own draw copied into the silhouette batches
 * as it happens - the buffer source handed to EntityRenderDispatcher.render is teed for that one entity, the way vanilla
 * tees the glowing outline. Only an inject and a ModifyArg: Oculus' batched entity rendering hooks the call to
 * renderEntity in renderLevel, not this call inside it, and its shadow pass reaches renderEntity too - the capture
 * refuses the shadow pass itself.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSilhouetteMixin {

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void reignofnether$silhouetteBegin(Entity entity, double camX, double camY, double camZ, float partialTick,
                                               PoseStack poseStack, MultiBufferSource buffers, CallbackInfo ci) {
        CanopySilhouettes.beginEntity(entity);
    }

    @ModifyArg(method = "renderEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            index = 7)
    private MultiBufferSource reignofnether$silhouetteTee(MultiBufferSource buffers) {
        return CanopySilhouettes.wrap(buffers);
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void reignofnether$silhouetteEnd(Entity entity, double camX, double camY, double camZ, float partialTick,
                                             PoseStack poseStack, MultiBufferSource buffers, CallbackInfo ci) {
        CanopySilhouettes.endEntity();
    }
}
