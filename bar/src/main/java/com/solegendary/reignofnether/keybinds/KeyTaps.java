package com.solegendary.reignofnether.keybinds;

import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * How long each key has been held, so one key can be a TAP order and a HOLD pan: A and S are both WASD panning and
 * BAR's Attack / Stop. A press shorter than {@link #TAP_MS} that the selected card has an order on fires the order
 * (HudClientEvents.onKeyRelease) and does not pan (OrthoviewClientEvents waits TAP_MS before an order key pans);
 * anything longer pans and fires nothing.
 *
 * InputEvent.Key arrives after the screen's own key events, so at ScreenEvent.KeyReleased time the press time is
 * still here. One long[] for every GLFW key, allocated once.
 */
public final class KeyTaps {

    public static final long TAP_MS = 180;
    private static final long[] pressedAt = new long[GLFW.GLFW_KEY_LAST + 1];

    private KeyTaps() { }

    @SubscribeEvent
    public static void onKey(InputEvent.Key evt) {
        int key = evt.getKey();
        if (key < 0 || key >= pressedAt.length)
            return;
        if (evt.getAction() == GLFW.GLFW_PRESS)
            pressedAt[key] = System.currentTimeMillis();
        else if (evt.getAction() == GLFW.GLFW_RELEASE)
            pressedAt[key] = 0;
    }

    /** Milliseconds the key has been down (0 when it is up or was pressed before we saw it). */
    public static long heldMs(int key) {
        if (key < 0 || key >= pressedAt.length || pressedAt[key] == 0)
            return 0;
        return System.currentTimeMillis() - pressedAt[key];
    }

    /** True while the key is down but not yet past a tap. */
    public static boolean inTapWindow(int key) {
        long held = heldMs(key);
        return held > 0 && held < TAP_MS;
    }
}
