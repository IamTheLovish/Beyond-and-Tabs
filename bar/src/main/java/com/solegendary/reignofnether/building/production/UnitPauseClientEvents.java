package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.RectZone;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * "Pause unit production", client side: the chip EconomyBarRenderer draws under the metal readout (it stores its zone
 * in {@link #chip}) and the {@link Keybindings#pauseUnits} key both just send
 * {@link UnitProductionPauseServerboundPacket}; the state comes back with the economy sync. Debounced like Overcharge.
 */
public class UnitPauseClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final long DEBOUNCE_MS = 250;
    private static long lastToggleMs = 0;

    /** Set every frame by EconomyBarRenderer while the chip is drawn, null otherwise. */
    public static RectZone chip = null;

    private static void requestToggle() {
        long now = System.currentTimeMillis();
        if (now - lastToggleMs < DEBOUNCE_MS)
            return;
        lastToggleMs = now;
        UnitProductionPauseServerboundPacket.toggle();
    }

    @SubscribeEvent
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Post evt) {
        if (!(MC.screen instanceof TopdownGui) || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        RectZone c = chip;
        if (c != null && c.isMouseOver((int) evt.getMouseX(), (int) evt.getMouseY()))
            requestToggle();
    }

    @SubscribeEvent
    public static void onKeyRelease(ScreenEvent.KeyReleased.Post evt) {
        if (TextInputClientEvents.isAnyInputFocused() || !(MC.screen instanceof TopdownGui))
            return;
        if (evt.getKeyCode() == Keybindings.pauseUnits.getKey())
            requestToggle();
    }
}
