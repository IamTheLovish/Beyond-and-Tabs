package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.guiscreen.PauseFlow;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Puts {@link MatchMenuScreen} where the vanilla Game Menu would open while the RTS view is up. Escape on the
 * TopdownGui closes it and TopdownGuiClientEvents opens a PauseScreen a few ticks later (it has to: the key is
 * handled by the screen that is closing); this swaps that PauseScreen for ours as it opens, so every path that
 * opens the vanilla menu lands on the match menu. A first-person view during a live match you play or watch gets the
 * match menu too; outside a match (sandbox, the lobby, menus) Escape is untouched.
 *
 * Escape on the RTS screen is BAR's cancel key (guiscreen/PauseFlow.onRtsEsc): it drops the building cursor, then an
 * armed order, then the selection, and otherwise does nothing - it never opens the menu (lovish, Oct 21). The menu is
 * the HUD's Menu button (HelperButtons.menuButton) or Shift+Esc.
 */
public class MatchMenuClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    // HIGHEST so our own GUI-scale handler (TopdownGuiClientEvents.onScreenOpen) sees the replacement screen
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpen(ScreenEvent.Opening evt) {
        if (!(evt.getScreen() instanceof PauseScreen))
            return;
        if (MC.level == null || MC.player == null)
            return;
        // the RTS view, or any moment of a live match you are in or watching: Escape in a first-person view used to
        // open the vanilla Game Menu (Advancements, Statistics...) mid-match (lovish's Oct 12 clips)
        if (!OrthoviewClientEvents.isEnabled() && !MatchMenuScreen.inMatch()
                && !com.solegendary.reignofnether.player.PlayerClientEvents.isMatchSpectator())
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
        PauseFlow.EscAction action = PauseFlow.onRtsEsc(
                BuildingClientEvents.getBuildingToPlace() != null,
                CursorClientEvents.getLeftClickAction() != null,
                !UnitClientEvents.getSelectedUnits().isEmpty() || !BuildingClientEvents.getSelectedBuildings().isEmpty(),
                Keybindings.shiftMod.isDown());
        switch (action) {
            case OPEN_MENU:
                return;   // TopdownGuiClientEvents.onKeyPress arms the menu as before
            case DROP_BUILDING:
                BuildingClientEvents.setBuildingToPlace(null);
                break;
            case CANCEL_ORDER:
                CursorClientEvents.setLeftClickAction(null);
                break;
            case DESELECT:
                UnitClientEvents.clearSelectedUnits();
                BuildingClientEvents.clearSelectedBuildings();
                break;
            default:
                break;   // nothing to cancel: Esc does nothing (the menu is the HUD's Menu button or Shift+Esc)
        }
        evt.setCanceled(true);
    }
}
