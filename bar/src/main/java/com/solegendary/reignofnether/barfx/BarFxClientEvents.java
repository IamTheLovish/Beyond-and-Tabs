package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Client hooks for the battle effects: draw every frame, shake the first-person camera, reset on logout. */
@OnlyIn(Dist.CLIENT)
public class BarFxClientEvents {
    private static boolean failedOnce = false;
    private static boolean scarsFailedOnce = false;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.WORLD_FX);
        try {
            if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                return;
            // the ground memory's decal mesh first (worldmemory/GroundMemoryClient): BarFx's glow and smoke sit on top of
            // the scar they leave
            try {
                com.solegendary.reignofnether.worldmemory.GroundMemoryClient.render(evt.getPoseStack(), evt.getCamera(),
                    evt.getProjectionMatrix(), evt.getFrustum());
            } catch (Exception e) {
                if (!scarsFailedOnce) {
                    scarsFailedOnce = true;
                    com.solegendary.reignofnether.ReignOfNether.LOGGER.error("World memory render failed", e);
                }
                com.solegendary.reignofnether.worldmemory.GroundMemoryClient.clear();
            }
            try {
                BarFxClient.onRenderFrame(evt.getPoseStack(), evt.getCamera(), evt.getPartialTick());
            } catch (Exception e) {
                // never take the game down over a visual effect
                if (!failedOnce) {
                    failedOnce = true;
                    com.solegendary.reignofnether.ReignOfNether.LOGGER.error("BarFx render failed", e);
                }
                BarFxClient.clear();
            }
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    /** First-person / free camera: a slight decaying jolt (the RTS camera shakes via its projection instead). */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles evt) {
        if (OrthoviewClientEvents.isEnabled())
            return;
        FeelFxClient.applyCamera(evt);   // trauma shake of the feel kit (first/third person only)
        float sx = BarFxClient.shakeX(), sy = BarFxClient.shakeY();
        if (sx == 0 && sy == 0)
            return;
        evt.setYaw(evt.getYaw() + sx * 2.5f);
        evt.setPitch(evt.getPitch() + sy * 2.5f);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        BarFxClient.clear();
        com.solegendary.reignofnether.sounds.SfxClient.reset();
    }
}
