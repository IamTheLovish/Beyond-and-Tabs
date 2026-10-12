package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.hud.SplashArt;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vanilla resource-loading overlay (a resource-pack change, F3+T, or launch with Forge's early window turned off):
 * the rose window and "Developed by Lovish" are drawn over the Mojang screen with the overlay's own fade, so it reads
 * as our loading screen (design/splash.md). The first load with the early window on goes through
 * ForgeLoadingOverlay instead (ForgeLoadingOverlayMixin).
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private boolean fadeIn;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Inject(method = "render", at = @At("TAIL"))
    private void reignofnether$drawSplash(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // the same fade the vanilla render just used for the logo
        long now = Util.getMillis();
        float out = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000f : -1f;
        float in = this.fadeInStart > -1L ? (now - this.fadeInStart) / 500f : -1f;
        float alpha;
        if (out >= 1f)
            alpha = 1f - Mth.clamp(out - 1f, 0f, 1f);
        else if (this.fadeIn)
            alpha = Mth.clamp(in, 0f, 1f);
        else
            alpha = 1f;
        SplashArt.renderLoading(graphics, this.reload, alpha);
    }
}
