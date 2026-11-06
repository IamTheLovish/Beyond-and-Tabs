package com.solegendary.reignofnether.mixin.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.instancing.Instancer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An eyes / glow layer's pass (vanilla's EyesLayer, and every generated body's _glow layer, which ends in it) announced
 * to the instanced unit bodies with its render type (unit/modelling/instancing/Instancer).
 */
@Mixin(EyesLayer.class)
public abstract class InstancingEyesLayerMixin {

    private static final String RENDER =
        "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void bat$instancedGlow(PoseStack pose, MultiBufferSource buffers, int light, Entity entity, float limbSwing,
                                   float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                                   float headPitch, CallbackInfo ci) {
        Instancer.tag(((EyesLayer<?, ?>) (Object) this).renderType());
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void bat$instancedGlowEnd(PoseStack pose, MultiBufferSource buffers, int light, Entity entity, float limbSwing,
                                      float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                                      float headPitch, CallbackInfo ci) {
        Instancer.untag();
    }
}
