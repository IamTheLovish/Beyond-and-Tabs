package com.solegendary.reignofnether.hud.playerdisplay;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * BAR's spectator player view (design/playtest_oct12.md, "BAR's spectator"): a spectator watches everything by
 * default and can follow one side - its economy on the top bar (HudClientEvents shows it when nothing is selected,
 * with the "<name>'s resources" caption a selected enemy unit already gave) and the camera on its capitol - cycling
 * with the F8 key (Shift+F8 goes back; past the last side is the full view again). BAR does the same from its player
 * list and the spectator view toggle.
 * <p>
 * Client only, nothing sent. Not done: that side's <i>line of sight</i>. Fog is decided per player on the server and a
 * spectator gets every chunk, so showing one side's vision needs the server to send that side's sight to the
 * spectators following it (FogOfWarServerEvents) - see design/match-lifecycle-progress.md, Proposals. F8 is otherwise
 * an operator's fog refresh, which only acts with fog on - never for a spectator.
 */
public class SpectatorView {

    private static final Minecraft MC = Minecraft.getInstance();
    @Nullable private static String viewed = null;

    /** The side being followed, or null for the full view (also once it left play or we are no longer watching). */
    @Nullable
    public static String viewed() {
        if (viewed != null && (!PlayerClientEvents.isMatchSpectator() || !PlayerClientEvents.isRTSPlayer(viewed)))
            viewed = null;
        return viewed;
    }

    /** Next ({@code step} 1) or previous (-1) side in the roster's order; one step past either end is the full view. */
    public static void cycle(int step) {
        List<String> sides = new ArrayList<>();   // only on a key press
        for (RTSPlayer p : new ArrayList<>(PlayerClientEvents.rtsPlayers))
            sides.add(p.name);
        String key = Keybindings.getFnum(8).getCurrentLabel();
        if (sides.isEmpty()) {
            viewed = null;
            return;
        }
        // positions -1 (full view), 0 .. n-1 (a side), wrapping
        int at = viewed == null ? -1 : sides.indexOf(viewed);
        int n = sides.size() + 1;
        int next = Math.floorMod(at + 1 + step, n) - 1;
        viewed = next < 0 ? null : sides.get(next);
        if (viewed == null) {
            HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.spectate_full_view", key));
            return;
        }
        centreOn(viewed);
        HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.spectate_watching", viewed, key));
    }

    /** The camera on the side's capitol, or on any of its units when it has none. */
    static void centreOn(String name) {
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (b.isCapitol && name.equals(b.ownerName)) {
                OrthoviewClientEvents.centreCameraOnPos(b.centrePos);
                return;
            }
        for (LivingEntity le : UnitClientEvents.getAllUnits())
            if (le instanceof Unit u && name.equals(u.getOwnerName())) {
                OrthoviewClientEvents.centreCameraOnPos(le.blockPosition());
                return;
            }
    }

    @SubscribeEvent
    public static void onInput(InputEvent.Key evt) {
        if (evt.getAction() != GLFW.GLFW_PRESS || evt.getKey() != Keybindings.getFnum(8).getKey())
            return;
        if (MC.player == null || (MC.screen != null && !(MC.screen instanceof TopdownGui))
                || TextInputClientEvents.isAnyInputFocused() || !PlayerClientEvents.isMatchSpectator())
            return;
        cycle(Keybindings.shiftMod.isDown() ? -1 : 1);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        viewed = null;
    }
}
