package com.solegendary.reignofnether.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.renderers.UnitLod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unit render LOD (unit/modelling/renderers/UnitLod): an eyes/glow layer on a UNIT draws nothing into a shader pack's
 * shadow map. Every such layer re-draws the unit's model (the Thunder / Celestial dragons and the Phoenix: the whole
 * body a second time) on the body's own surface, full-bright and additive - the body pass already casts that shadow.
 * Covers the vanilla-style anonymous EyesLayers in the unit renderers; GeneratedBodies.glow overrides render and asks
 * UnitLod itself. A cancellable HEAD inject, so other mods' injects into EyesLayer still run; non-units untouched.
 */
@Mixin(EyesLayer.class)
public abstract class UnitLodEyesLayerMixin<T extends Entity, M extends EntityModel<T>> {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void bat$skipInShadowPass(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                                      float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                                      float headPitch, CallbackInfo ci) {
        if (UnitLod.skipOverlay(entity))
            ci.cancel();
    }
}
