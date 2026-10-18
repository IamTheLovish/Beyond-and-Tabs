package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.matchstart.MatchLoadingScreen;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No vanilla recipe or tutorial toasts while a match is being prepared or played (inbox/spec-3, section 2.2). A
 * fresh skirmish world unlocks every recipe on login, so "New Recipes Unlocked!" slid in over the first seconds of
 * every match (rover run 1, finding 8), and the vanilla tutorial would offer "punch a tree" to a commander.
 * OrthoviewClientEvents already stops the tutorial when the RTS view opens; the recipe toast arrives before that,
 * under the loading veil, so it is dropped at the door instead. System and advancement toasts are untouched.
 */
@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void reignofnether$noVanillaToastsInAMatch(Toast toast, CallbackInfo ci) {
        if ((toast instanceof RecipeToast || toast instanceof TutorialToast) && MatchLoadingScreen.scrubbing())
            ci.cancel();
    }
}
