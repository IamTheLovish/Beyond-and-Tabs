package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.CanopySilhouettes;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unit silhouettes (orthoview/CanopySilhouettes) are blended over the picture right after GameRenderer.renderLevel
 * returns. Earlier is too early with a shader pack: Oculus runs the pack's composite and final passes at the very end
 * of LevelRenderer.renderLevel (after Forge's AFTER_LEVEL stage), and they repaint the whole main target. Here the
 * picture is finished, and the hand, entity outlines and the whole HUD are still to come on top.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererSilhouetteMixin {

    @Inject(
            method = "render(FJZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER)
    )
    private void reignofnether$compositeSilhouettes(float partialTicks, long nanoTime, boolean renderLevel,
                                                    CallbackInfo ci) {
        CanopySilhouettes.composite();
    }
}
