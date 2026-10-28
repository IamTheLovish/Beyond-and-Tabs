package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.hud.SplashArt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * lovish (Oct 22): the cathedral behind every waiting screen on the way into a game, not only the launch overlay.
 * Vanilla's connect ("Connecting to the server..."), receiving-level ("Loading terrain..."), generic dirt message
 * ("Saving world", "Joining world"...), progress ("Preparing spawn area", downloading) and level-loading (the chunk
 * square) screens all paint Screen.renderDirtBackground behind their text; for those five we draw the cathedral in its
 * title layout instead (SplashArt.renderWaiting: the scene without the lettering, a soft pad behind the centre so the
 * status text and the progress stay readable). Everything else they draw is untouched; if the art cannot load (or for any
 * other screen) the dirt stays. Reduce motion freezes the scene as everywhere else.
 */
@Mixin(Screen.class)
public abstract class WaitingScreenBackdropMixin {

    @Inject(method = "renderDirtBackground", at = @At("HEAD"), cancellable = true)
    private void reignofnether$cathedralBehindWaitingScreens(GuiGraphics graphics, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ConnectScreen || self instanceof ReceivingLevelScreen
                || self instanceof GenericDirtMessageScreen || self instanceof ProgressScreen
                || self instanceof LevelLoadingScreen))
            return;
        Screen screen = (Screen) self;
        if (SplashArt.renderWaiting(graphics, screen.width, screen.height))
            ci.cancel();
    }
}
