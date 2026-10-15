package com.solegendary.reignofnether.postfx;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Forge-bus hooks for the world look pass (WorldLookPass); its shader loads on the mod bus (ClientModEvents). */
@OnlyIn(Dist.CLIENT)
public final class WorldLookClientEvents {

    private WorldLookClientEvents() {
    }

    // AFTER_LEVEL: the last world stage - after it come the hand, entity outlines and the HUD, none of which is graded.
    // LOWEST so anything another mod adds to the world at this stage is graded with it.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL)
            WorldLookPass.render(evt);
    }

    // HIGHEST: vanilla's own colour, before the RTS view swaps it for the map-edge tone (OrthoviewClientEvents)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFogColour(ViewportEvent.ComputeFogColor evt) {
        WorldLookPass.captureSkyFog(evt.getRed(), evt.getGreen(), evt.getBlue());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        WorldLookPass.release();
    }
}
