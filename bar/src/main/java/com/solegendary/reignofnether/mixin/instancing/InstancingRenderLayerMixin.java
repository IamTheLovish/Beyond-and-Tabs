package com.solegendary.reignofnether.mixin.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.instancing.Instancer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's coloured cutout copy of a model (RenderLayer.renderColoredCutoutModel: the villager profession and biome
 * layers, a clothing layer, TeamColourLayer's plain branch) announced to the instanced unit bodies with the render type
 * it draws in (unit/modelling/instancing/Instancer).
 */
@Mixin(RenderLayer.class)
public abstract class InstancingRenderLayerMixin {

    private static final String COLOURED =
        "renderColoredCutoutModel(Lnet/minecraft/client/model/EntityModel;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFF)V";

    @Inject(method = COLOURED, at = @At("HEAD"))
    private static void bat$instancedCopy(EntityModel<?> model, ResourceLocation texture, PoseStack pose,
                                          MultiBufferSource buffers, int light, LivingEntity entity, float r, float g,
                                          float b, CallbackInfo ci) {
        Instancer.tag(RenderType.entityCutoutNoCull(texture));
    }

    @Inject(method = COLOURED, at = @At("RETURN"))
    private static void bat$instancedCopyEnd(EntityModel<?> model, ResourceLocation texture, PoseStack pose,
                                             MultiBufferSource buffers, int light, LivingEntity entity, float r, float g,
                                             float b, CallbackInfo ci) {
        Instancer.untag();
    }
}
