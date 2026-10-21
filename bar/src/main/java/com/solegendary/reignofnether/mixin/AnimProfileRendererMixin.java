package com.solegendary.reignofnether.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.perf.AnimProfile;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clocks for perf/AnimProfile: the whole of LivingEntityRenderer.render for a unit, and inside it the model's posing
 * (prepareMobModel through setupAnim). Four nanoTime reads per unit per frame, nothing else.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class AnimProfileRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    private static final String RENDER =
        "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void bat$renderBegin(T entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                                 int light, CallbackInfo ci) {
        AnimProfile.beginRender(entity);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/model/EntityModel;prepareMobModel(Lnet/minecraft/world/entity/Entity;FFF)V"))
    private void bat$animBegin(T entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                               int light, CallbackInfo ci) {
        AnimProfile.beginAnim(entity);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
        shift = At.Shift.AFTER))
    private void bat$animEnd(T entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                             int light, CallbackInfo ci) {
        AnimProfile.endAnim(entity);
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void bat$renderEnd(T entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                               int light, CallbackInfo ci) {
        AnimProfile.endRender(entity);
    }
}
