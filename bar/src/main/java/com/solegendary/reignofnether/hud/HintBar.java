package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.matchstart.MatchLoadingScreen;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.List;

/**
 * The ONE hint bar of the RTS view (inbox/spec-3, section 2.2): a sticky banner (the start-position phase's
 * countdown, set by MapClient every tick) and, under it, a queue of one-shot tips shown one at a time - the camera
 * controls on the first RTS view, "/rts-help" after the loading veil, the spectator note, "the clock placed your
 * commander at ...". Rover run 1, finding 8: the start banner (RenderGuiEvent, y 34) and the camera-controls hint
 * (HudClientEvents' temporary message, y 40) were two bars drawn by two systems over each other, the second clipped
 * into a marquee by its column; finding 9: the banner drew over the pause menu, because RenderGuiEvent fires under
 * every screen. So this bar draws only in the RTS screen's own render pass (ScreenEvent.Render.Post for TopdownGui):
 * never over the pause menu, the chat, a lobby or the loading veil. Its clock stops while it is hidden, so a tip
 * queued under the veil is seen after the cut instead of expiring unread.
 * <p>
 * It stacks from {@link HudClientEvents#alertBottom} (under the economy bar and the HUD's own red warning line) and
 * pushes that mark down, so the alert toasts (NotificationClientEvents, LOW priority) line up under it. Text wraps to
 * the window instead of being clipped. HudClientEvents' temporary message stays what it is: the red warning line for
 * refused orders - a hint is not a warning.
 */
public final class HintBar {
    private HintBar() { }

    private static final Minecraft MC = Minecraft.getInstance();

    /** Ticks over which a tip fades before the next shows. */
    static final int FADE_TICKS = 20;
    /** Never more than this many tips waiting: a flood would otherwise play for minutes after the fact. */
    static final int MAX_QUEUE = 6;
    static final int DEFAULT_EDGE = 0xB08A3E;

    private record Hint(String text, int ticks, int edge) { }

    private static final ArrayDeque<Hint> queue = new ArrayDeque<>();
    @Nullable private static Hint showing = null;
    private static int showTicks = 0;
    @Nullable private static String banner = null;
    private static int bannerEdge = 0xD8D8D8;

    /** The sticky line (the start phase's countdown); null clears it. Set every tick by its owner. */
    public static void setBanner(@Nullable String text, int edgeRgb) {
        banner = text == null || text.isBlank() ? null : text;
        bannerEdge = edgeRgb & 0xFFFFFF;
    }

    public static void queue(String text, int ticks) {
        queue(text, ticks, DEFAULT_EDGE);
    }

    /** A one-shot tip shown for {@code ticks} once its turn comes, with a coloured rule under it. */
    public static void queue(String text, int ticks, int edgeRgb) {
        if (text == null || text.isBlank())
            return;
        // the same text twice in a row says nothing new
        Hint last = queue.peekLast();
        if (last != null && last.text().equals(text))
            return;
        if (showing != null && showing.text().equals(text))
            return;
        while (queue.size() >= MAX_QUEUE)
            queue.pollFirst();
        queue.addLast(new Hint(text, Math.max(20, ticks), edgeRgb & 0xFFFFFF));
    }

    public static void clear() {
        queue.clear();
        showing = null;
        showTicks = 0;
        banner = null;
    }

    /** Only on the RTS screen itself, with the HUD on, and never while the loading veil is up. */
    static boolean visible() {
        return MC.player != null && MC.level != null && MC.screen instanceof TopdownGui
            && OrthoviewClientEvents.isEnabled() && !HudPanels.hidden() && !MatchLoadingScreen.isUp();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || !visible())
            return;   // the clock stops while hidden
        if (showing == null) {
            showing = queue.pollFirst();
            showTicks = showing == null ? 0 : showing.ticks();
        } else if (--showTicks <= 0) {
            showing = null;
        }
    }

    /** Registered after HudClientEvents (the same NORMAL priority), so alertBottom is this frame's. */
    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post evt) {
        if (!(evt.getScreen() instanceof TopdownGui) || !visible())
            return;
        if (banner == null && (showing == null || !HudPanels.showExtras()))
            return;
        GuiGraphics gg = evt.getGuiGraphics();
        Font font = MC.font;
        int sw = gg.guiWidth();
        int y = HudClientEvents.alertBottom;
        if (banner != null)
            y = draw(gg, font, sw, banner, y, bannerEdge, 1f) + 2;
        // the banner is essential (clean view keeps it); tips are extras
        if (showing != null && HudPanels.showExtras()) {
            float a = showTicks < FADE_TICKS ? Math.max(0.05f, showTicks / (float) FADE_TICKS) : 1f;
            y = draw(gg, font, sw, showing.text(), y, showing.edge(), a) + 2;
        }
        HudClientEvents.alertBottom = y;
    }

    /** One dark box centred on the screen, the text wrapped to the window, a coloured rule under it;
     * returns its bottom. */
    private static int draw(GuiGraphics gg, Font font, int sw, String text, int y, int edge, float alpha) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), sw - 28);
        int tw = 0;
        for (FormattedCharSequence line : lines)
            tw = Math.max(tw, font.width(line));
        int w = tw + 12, h = lines.size() * 10 + 5;
        int x = sw / 2 - w / 2;
        gg.fill(x, y, x + w, y + h, argb(alpha * 0.69f, 0x101216));
        gg.fill(x, y + h, x + w, y + h + 1, argb(alpha, edge));
        int ly = y + 3;
        for (FormattedCharSequence line : lines) {
            gg.drawString(font, line, sw / 2 - font.width(line) / 2, ly, argb(alpha, 0xFFFFFF), true);
            ly += 10;
        }
        return y + h + 1;
    }

    private static int argb(float alpha, int rgb) {
        // the font renderer treats an alpha under 4 as opaque: never go that low
        return (Mth.clamp(Math.round(alpha * 255f), 8, 255) << 24) | (rgb & 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        clear();
    }
}
