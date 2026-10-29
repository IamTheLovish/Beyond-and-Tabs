package com.solegendary.reignofnether.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.modelling.motion.Corpses;
import com.solegendary.reignofnether.unit.modelling.motion.DeathFall;
import com.solegendary.reignofnether.unit.modelling.motion.MotionPass;
import com.solegendary.reignofnether.unit.modelling.renderers.MipRenderTypes;
import com.solegendary.reignofnether.unit.modelling.renderers.MipmappedTextures;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context ctx) { super(ctx); }

    // always shake when on wraith snow
    @Inject(
            method = "isShaking(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void reignofnether$forceShake(
            T entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity.hasEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get()) &&
            entity.level().getBlockState(entity.getOnPos().above()).getBlock() == BlockRegistrar.WRAITH_SNOW_LAYER.get()) {
            cir.setReturnValue(true);
        }
    }

    private static boolean isTransparent(LivingEntity entity) {
        return entity.hasEffect(MobEffectRegistrar.PHASING.get());
    }
    private static final float ALPHA = 0.5f;

    @Unique private T ron$currentEntity = null;

    @Inject(
            method = "getRenderType",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ron$render(T entity, boolean bodyVisible, boolean translucent,
                                  boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        ron$currentEntity = entity;
        // a fallen body fading out in its faction's finish (unit/modelling/motion/Corpses, DeathFinish) too
        if (isTransparent(entity) || Corpses.alpha() < 1f) {
            cir.setReturnValue(RenderType.entityTranslucent(this.getTextureLocation(entity)));
            return;
        }
        // a generated body (GeneratedBodies) is drawn with its mipmaps - the model's own type would sample level 0
        // only and the 8x paint would shimmer from the RTS camera (claude/unit-mipmaps.md)
        if (bodyVisible && !translucent) {
            ResourceLocation texture = this.getTextureLocation(entity);
            if (MipmappedTextures.has(texture))
                cir.setReturnValue(MipRenderTypes.body(texture));
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V")
    )
    private void ron$renderWithAlpha(EntityModel<T> model, PoseStack poseStack, VertexConsumer buffer,
                                    int packedLight, int packedOverlay,
                                    float red, float green, float blue, float alpha) {
        float finalAlpha = (ron$currentEntity != null && isTransparent(ron$currentEntity) ? alpha * ALPHA : alpha)
            * Corpses.alpha();
        model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, red, green, blue, finalAlpha);
    }

    // a body that falls by itself (DeathFall) is red only while hurt, as in life - not for its whole death. Here, at
    // vanilla's one overlay rule, so the body and every layer that asks for the overlay (team cloth, profession,
    // armour) agree, on the dying unit and on the body Corpses holds
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void bat$deathPoseNoRed(LivingEntity entity, float white, CallbackInfoReturnable<Integer> cir) {
        if (entity.deathTime > 0 && MotionPass.ownsDeath(entity) != null)
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(white),
                OverlayTexture.v(DeathFall.red(entity.hurtTime, entity.deathTime, true))));
    }

    // the death-pose contract (unit/modelling/motion/DeathFall): a body that falls by itself is not flipped as well
    @Inject(method = "getFlipDegrees", at = @At("HEAD"), cancellable = true)
    private void bat$deathPoseNoFlip(T entity, CallbackInfoReturnable<Float> cir) {
        if (MotionPass.ownsDeath(entity, this.model) != null)
            cir.setReturnValue(0f);
    }


    @Shadow protected M model;

    // the living army (design/living_army.md): its offsets go on after the model's own pose and come off when the
    // unit's render returns, so the body pass and every layer draw the same pose and nothing accumulates
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER)
    )
    private void bat$livingArmy(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        MotionPass.apply(entity, this.model, poseStack, partialTicks);
    }

    // and at HEAD too: a render that threw after the pass (a caller that swallows it, like the portrait) skipped its
    // RETURN, and its parts must not carry that pose into the next unit drawn with the same model
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void bat$livingArmyClear(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        MotionPass.restore();
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void bat$livingArmyRestore(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                       MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        MotionPass.restore();
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER)
    )
    private void ron$applyScale(T entity, float entityYaw, float partialTicks,
                               PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                               CallbackInfo ci) {
        if (entity instanceof Unit unit) {
            float scale = unit.getScaleAttribute();
            poseStack.scale(scale, scale, scale);
        }
    }
}