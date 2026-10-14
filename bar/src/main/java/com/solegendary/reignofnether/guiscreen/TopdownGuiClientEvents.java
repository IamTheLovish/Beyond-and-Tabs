package com.solegendary.reignofnether.guiscreen;

import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handler for TopdownGui, the GUI screen that allows for cursor movement on screen
 * Doing stuff like initialising it and controlling where it comes up in the game client
 *
 * @author SoLegendary
 */

public class TopdownGuiClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static int noScreenTicks = 0; // ticks that no screen has been opened
    private static boolean shouldPause = false;
    // the last screen to close was a pause menu: if that leaves no screen, the player wants the RTS view back now
    private static boolean pauseJustClosed = false;
    private static final int MAX_GUI_SCALE_MOD = 3;

    // if no other screen is open and we've got orthoview enabled, open a screen based on shouldPause
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;

        if (OrthoviewClientEvents.isEnabled() && Minecraft.getInstance().screen == null) {
            noScreenTicks += 1;
            // Esc out of the pause menu used to leave the HUD blank for ~0.2 s (clip study Oct 13 b11): three ticks
            // of grace here plus the server round trip that opens the RTS screen. A pause menu that closed to no
            // screen can only mean "back to the game", so that case asks for the screen on the very next tick.
            if (noScreenTicks >= 3 || (pauseJustClosed && !shouldPause && MC.player != null)) {
                if (shouldPause) {
                    shouldPause = false;
                    MC.setScreen(new PauseScreen(true));
                }
                else if (MC.player != null)
                    TopdownGuiServerboundPacket.openTopdownGui(MC.player.getId());
                noScreenTicks = 0;
                pauseJustClosed = false;
            }
        } else {
            pauseJustClosed = false;
        }
    }

    // allow closing by pressing escape
    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing evt) {
        if (evt.getScreen().isPauseScreen()) {
            shouldPause = false;
            pauseJustClosed = true;
        }

        int i = MC.getWindow().calculateScale(MC.options.guiScale().get(), MC.isEnforceUnicode());
        MC.getWindow().setGuiScale(i);
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening evt) {
        if (evt.getScreen() instanceof TopdownGui) {
            int i = MC.getWindow().calculateScale(Math.min(MAX_GUI_SCALE_MOD, MC.options.guiScale().get()), MC.isEnforceUnicode());
            if (MC.options.guiScale().get() == 0)
                i = MAX_GUI_SCALE_MOD;
            MC.getWindow().setGuiScale(i);
        } else {
            int i = MC.getWindow().calculateScale(MC.options.guiScale().get(), MC.isEnforceUnicode());
            MC.getWindow().setGuiScale(i);
        }
    }

    @SubscribeEvent
    public static void beforeGuiRender(ScreenEvent.Render.Pre evt) {
        // cancel drawing the GUI
        if (evt.getScreen() instanceof TopdownGui)
            evt.setCanceled(true);
    }

    // prevent opening inventory with E or advancements with L
    @SubscribeEvent
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre evt) {
        if (OrthoviewClientEvents.isEnabled()) {
            if (evt.getKeyCode() == Keybindings.pause.getKey())
                shouldPause = true;
            else if (evt.getKeyCode() == MC.options.keyInventory.getKey().getValue())
                evt.setCanceled(true);
            else if (evt.getKeyCode() == MC.options.keyAdvancements.getKey().getValue())
                evt.setCanceled(true);
        }
    }
}