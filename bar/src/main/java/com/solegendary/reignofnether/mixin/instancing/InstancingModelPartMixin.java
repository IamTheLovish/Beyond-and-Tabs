package com.solegendary.reignofnether.mixin.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.unit.modelling.instancing.Instancer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The instanced unit bodies' entry (unit/modelling/instancing/Instancer): every model part render in the game passes
 * here, so this is the one place the path is decided - the same for every unit model, generated, hand-made or vanilla.
 * Outside a unit's render it costs one static read. Priority 500: before Embeddium's ModelPart mixin (1500, which hooks
 * compile, one level down, and overwrites translateAndRotate - which the rig uses for its bones, so the matrices are the
 * ones Embeddium's walk would make).
 */
@Mixin(value = ModelPart.class, priority = 500)
public abstract class InstancingModelPartMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void bat$instance(PoseStack pose, VertexConsumer consumer, int light, int overlay, float r, float g, float b,
                              float a, CallbackInfo ci) {
        if (Instancer.onPartRender((ModelPart) (Object) this, pose, consumer, light, overlay, r, g, b, a))
            ci.cancel();
    }
}
