package com.solegendary.reignofnether.mixin.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.instancing.Instancer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The instanced unit bodies (unit/modelling/instancing/Instancer): a unit's render opens and closes here, and its body
 * pass is announced by the render's own getBuffer (the body's render type, passed on unchanged). The body pass ends
 * where the layers begin. Its own class, so LivingEntityRendererMixin (the living army, the hit flash) stays as it is.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class InstancingLivingRendererMixin<T extends LivingEntity> {

    private static final String RENDER =
        "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void bat$instancedBegin(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                                    int light, CallbackInfo ci) {
        Instancer.beginUnit(entity, buffers);
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
        index = 0)
    private RenderType bat$instancedBody(RenderType type) {
        return Instancer.tag(type);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private void bat$instancedLayers(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                                     int light, CallbackInfo ci) {
        Instancer.untag();
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private void bat$instancedLayer(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                                    int light, CallbackInfo ci) {
        Instancer.untag();
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void bat$instancedEnd(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                                  int light, CallbackInfo ci) {
        Instancer.endUnit();
    }
}
