package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.RectZone;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.PlayerClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Stormcallers Mana, client side: the Overcharge toggle. Two ways to flip it, both only for a player whose own
 * faction has {@link FactionTraits#mana}: the Overcharge keybind, and the chip EconomyBarRenderer draws under the
 * energy bar (it stores its zone in {@link #overchargeChip}). Both just send {@link ManaServerboundPacket}; the
 * server decides, and the state comes back with the economy sync. Debounced so a held key or a double click can't
 * flood the server.
 */
public class ManaClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final long DEBOUNCE_MS = 250;
    private static long lastToggleMs = 0;

    /** Set every frame by EconomyBarRenderer while the chip is drawn, null otherwise. */
    public static RectZone overchargeChip = null;

    /** Is the local player on a Mana faction (the only players who see the chip and may use the keybind)? */
    public static boolean localIsMana() {
        return FactionTraits.of(PlayerClientEvents.getFaction()).mana;
    }

    private static void requestToggle() {
        long now = System.currentTimeMillis();
        if (now - lastToggleMs < DEBOUNCE_MS || !localIsMana())
            return;
        lastToggleMs = now;
        ManaServerboundPacket.toggleOvercharge();
    }

    @SubscribeEvent
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Post evt) {
        if (!(MC.screen instanceof TopdownGui) || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        RectZone chip = overchargeChip;
        if (chip != null && chip.isMouseOver((int) evt.getMouseX(), (int) evt.getMouseY()))
            requestToggle();
    }

    @SubscribeEvent
    public static void onKeyRelease(ScreenEvent.KeyReleased.Post evt) {
        if (TextInputClientEvents.isAnyInputFocused())
            return;
        if (!(MC.screen instanceof TopdownGui))
            return;
        if (evt.getKeyCode() == Keybindings.overcharge.getKey())
            requestToggle();
    }
}
