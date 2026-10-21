package com.solegendary.reignofnether.keybinds;

import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * How long each key has been held, so one key can be a TAP order and a HOLD pan: S is both WASD panning and BAR's
 * Stop (and any order a player rebinds onto W/A/S/D is the same). A press shorter than {@link #TAP_MS} that the
 * selected card has an order on fires the order (HudClientEvents.onKeyRelease) and does not pan (OrthoviewClientEvents
 * waits out the tap before an order key pans); a press the camera already panned for fires nothing.
 *
 * The timing is real wall-clock time (System.nanoTime at the key event, never ticks), and the verdict allows for lag:
 * GLFW delivers key events once per frame, so a press is only seen when its frame ends. The rule and the reasoning
 * live in {@link KeyTapTracker} (pure, unit-tested); this class only feeds it key events and frame starts.
 *
 * InputEvent.Key arrives after the screen's own key events, so at ScreenEvent.KeyReleased time the press is
 * still here.
 */
public final class KeyTaps {

    public static final long TAP_MS = KeyTapTracker.TAP_MS;
    private static final KeyTapTracker TRACKER = new KeyTapTracker(GLFW.GLFW_KEY_LAST + 1);

    private KeyTaps() { }

    @SubscribeEvent
    public static void onKey(InputEvent.Key evt) {
        if (evt.getAction() == GLFW.GLFW_PRESS)
            TRACKER.press(evt.getKey(), System.nanoTime());
        else if (evt.getAction() == GLFW.GLFW_RELEASE)
            TRACKER.release(evt.getKey());
    }

    /** Every frame start: the frame times a held key lived through are what the tap verdict forgives. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase == TickEvent.Phase.START)
            TRACKER.frame(System.nanoTime());
    }

    /** Milliseconds the key has been down (0 when it is up or was pressed before we saw it). */
    public static long heldMs(int key) {
        return TRACKER.heldMs(key, System.nanoTime());
    }

    /** True while the key is down but not yet past a tap. */
    public static boolean inTapWindow(int key) {
        return TRACKER.inTapWindow(key, System.nanoTime());
    }

    /** The camera panned for this press: it is a hold from here on, however short it measures. */
    public static void markPan(int key) {
        TRACKER.markPan(key);
    }

    /** At key release: true when this press was a pan (fire no order), false for a tap. */
    public static boolean isHold(int key) {
        return TRACKER.isHold(key, System.nanoTime());
    }
}
