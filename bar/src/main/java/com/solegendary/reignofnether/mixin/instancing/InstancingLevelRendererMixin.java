package com.solegendary.reignofnether.mixin.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.instancing.Instancer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The instanced unit bodies draw only units the level's entity loop draws (unit/modelling/instancing/Instancer): their
 * batch is drawn right after that loop's batches (AFTER_ENTITIES). A unit drawn anywhere else - a HUD portrait, a menu -
 * keeps the old path.
 */
@Mixin(LevelRenderer.class)
public abstract class InstancingLevelRendererMixin {

    private static final String RENDER_ENTITY =
        "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Inject(method = RENDER_ENTITY, at = @At("HEAD"))
    private void bat$instancedLoopIn(Entity entity, double camX, double camY, double camZ, float partialTick,
                                     PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        Instancer.enterEntity();
    }

    @Inject(method = RENDER_ENTITY, at = @At("RETURN"))
    private void bat$instancedLoopOut(Entity entity, double camX, double camY, double camZ, float partialTick,
                                      PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        Instancer.exitEntity();
    }
}
