package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.onboarding.FirstLaunch;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No advancement or recipe pop-ups: they are vanilla survival progress that means nothing in an RTS match and
 * covered the HUD's top right (Lane C tester, Oct 20: "disable achievements for the mod"). Other toasts (tutorial,
 * system, our own) are untouched; the tutorial is stopped where it starts (OrthoviewClientEvents, the lobby).
 */
@Mixin(ToastComponent.class)
public class ToastComponentMixin {

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void reignofnether$dropProgressToasts(Toast toast, CallbackInfo ci) {
        if (FirstLaunch.hideToast(toast instanceof AdvancementToast, toast instanceof RecipeToast))
            ci.cancel();
    }
}
