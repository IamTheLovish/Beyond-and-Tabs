package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.player.PlayerClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The economy bar's two BAR sliders, client side. EconomyBarRenderer draws a marker on the player's OWN metal and energy
 * bars and tells this class where the bars are ({@link #metalBar} / {@link #energyBar}, set every frame, null when not
 * drawn: someone else's bar, the compact bar, no HUD). Press on a bar (or drag along it) with the left button to set
 * its level, right-click to put it back to the default; the new level goes up in {@link EconomySliderServerboundPacket}
 * (rate-limited here and again on the server; the last position of a drag always goes) and comes back with the next
 * economy sync, which is what the marker draws.
 * <ul>
 *   <li>energy bar = converters run only on energy above this fill (default 50%, Stormcallers 75%);</li>
 *   <li>metal bar = metal above this fill is shared with allies (default 100%: only the overflow).</li>
 * </ul>
 */
public class EconomySliderClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final long SEND_EVERY_MS = 80;

    /** One bar's geometry in GUI pixels: x0 / x1 = where 0% and 100% sit, y0 / y1 = the bar's top and bottom. */
    public record BarGeom(int x0, int x1, int y0, int y1) {
        public boolean hit(int mx, int my) {
            return mx >= x0 - 4 && mx <= x1 + 4 && my >= y0 - 4 && my <= y1 + 4;
        }

        public float levelAt(int mx) {
            float f = (mx - x0) / (float) Math.max(1, x1 - x0);
            return Math.round(Math.max(0f, Math.min(1f, f)) * 100f) / 100f;
        }

        public int xOf(float level) {
            return x0 + Math.round((x1 - x0) * Math.max(0f, Math.min(1f, level)));
        }
    }

    public static BarGeom metalBar = null;
    public static BarGeom energyBar = null;

    private static int dragging = 0;   // 0 none, 1 metal, 2 energy
    private static long lastSendMs = 0;
    private static float pending = -1;

    private static void send(boolean energy, float level, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastSendMs < SEND_EVERY_MS) {
            pending = level;
            return;
        }
        lastSendMs = now;
        pending = -1;
        EconomySliderServerboundPacket.send(energy, level);
    }

    @SubscribeEvent
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Post evt) {
        if (!(MC.screen instanceof TopdownGui))
            return;
        int mx = (int) evt.getMouseX(), my = (int) evt.getMouseY();
        boolean onEnergy = energyBar != null && energyBar.hit(mx, my);
        boolean onMetal = !onEnergy && metalBar != null && metalBar.hit(mx, my);
        if (!onEnergy && !onMetal)
            return;
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {   // back to the default
            float def = onEnergy ? FactionTraits.of(PlayerClientEvents.getFaction()).converterThreshold : 1f;
            send(onEnergy, def, true);
            return;
        }
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        dragging = onEnergy ? 2 : 1;
        send(onEnergy, (onEnergy ? energyBar : metalBar).levelAt(mx), true);
    }

    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre evt) {
        if (dragging == 0 || !(MC.screen instanceof TopdownGui))
            return;
        BarGeom g = dragging == 2 ? energyBar : metalBar;
        if (g == null) {
            dragging = 0;
            return;
        }
        send(dragging == 2, g.levelAt((int) evt.getMouseX()), false);
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Post evt) {
        if (dragging == 0)
            return;
        if (pending >= 0)
            send(dragging == 2, pending, true);   // the last position of the drag always goes
        dragging = 0;
    }
}
