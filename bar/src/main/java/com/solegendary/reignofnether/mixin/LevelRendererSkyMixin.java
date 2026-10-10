package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The RTS camera looks down at the ground, so the sky dome only ever showed up as blue wedges at the screen
 * corners and a blue sheet past the render distance when zoomed out (clip 15-56). In the RTS view the sky is not
 * drawn at all; the frame's clear colour (OrthoviewClientEvents.onFogColor) is a dark ground tone instead.
 * The first-person view keeps its sky.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererSkyMixin {

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void reignofnether$noSkyInRtsView(CallbackInfo ci) {
        if (OrthoviewClientEvents.isEnabled())
            ci.cancel();
    }
}
