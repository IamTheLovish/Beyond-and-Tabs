package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.hud.SplashArt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.loading.ForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The loading screen at launch: Forge replaces the Mojang overlay with ForgeLoadingOverlay, which blits the early
 * window's progress screen into the game window while the first resource reload runs (the long part of startup). We
 * draw the rose window over it with the same fade (design/splash.md).
 *
 * remap = false and the target is selected by descriptor only ("any method taking (GuiGraphics, int, int, float)"):
 * the method is render() in dev but its SRG name in production, and ForgeLoadingOverlay is not in the obfuscation
 * mappings, so a name would only match on one side. require = 0: if a Forge update changes the class, startup keeps the
 * plain Forge screen rather than crashing.
 */
@Mixin(value = ForgeLoadingOverlay.class, remap = false)
public abstract class ForgeLoadingOverlayMixin {

    @Inject(method = "*(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), require = 0)
    private void reignofnether$drawSplash(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SplashArt.renderLoading(graphics, ((LoadingOverlayAccessor) (Object) this).reignofnether$getReload(), -1f);
    }
}
