package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Puts {@link MatchMenuScreen} where the vanilla Game Menu would open while the RTS view is up. Escape on the
 * TopdownGui closes it and TopdownGuiClientEvents opens a PauseScreen a few ticks later (it has to: the key is
 * handled by the screen that is closing); this swaps that PauseScreen for ours as it opens, so every path that
 * opens the vanilla menu lands on the match menu. Outside the RTS view (first-person, the lobby, menus) Escape is
 * untouched.
 *
 * Escape with something to cancel cancels that instead of opening the menu: a building being placed is dropped
 * (BAR's Escape clears the build cursor first).
 */
public class MatchMenuClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    // HIGHEST so our own GUI-scale handler (TopdownGuiClientEvents.onScreenOpen) sees the replacement screen
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpen(ScreenEvent.Opening evt) {
        if (!(evt.getScreen() instanceof PauseScreen))
            return;
        if (!OrthoviewClientEvents.isEnabled() || MC.level == null || MC.player == null)
            return;
        evt.setNewScreen(new MatchMenuScreen(true));
    }

    // Before TopdownGuiClientEvents.onKeyPress (which arms the pause): drop the build cursor and eat the key
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre evt) {
        if (!(evt.getScreen() instanceof TopdownGui) || !OrthoviewClientEvents.isEnabled())
            return;
        if (evt.getKeyCode() != Keybindings.pause.getKey())
            return;
        if (BuildingClientEvents.getBuildingToPlace() != null) {
            BuildingClientEvents.setBuildingToPlace(null);
            evt.setCanceled(true);
        }
    }
}
