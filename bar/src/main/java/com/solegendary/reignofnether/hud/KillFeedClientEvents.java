package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.KillFeedClientboundPacket.Entry;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;

/**
 * BAR kill feed: up to {@link #MAX_LINES} fading lines, newest on top, small (HudLayout.FEED_SCALE) and right-aligned
 * at the side under the stats strip (PlayerPanelClientEvents.feedTop), out of the middle of the screen where the
 * economy bar's status line is. A line reads
 * "Stag Lancer x Zombie", " x3" when the server merged duplicates or the same kill arrives again while the line is
 * up, and in bold when the victim was a building. Each name is light text (its owner's hue lightened to a 4.5:1
 * contrast on the line's backing, TextContrast) after a small swatch in the owner's true colour: lovish's names were
 * drawn in his dark blue on a dark-green world and could not be read. Lines are promoted from a
 * small queue at most one per {@link #PROMOTE_TICKS} ticks, so a big fight scrolls instead of flashing; the text is
 * laid out once per line (a FormattedCharSequence and its width), nothing allocates per frame.
 */
public class KillFeedClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    public static final int MAX_LINES = 5;
    public static final int PROMOTE_TICKS = 5;
    static final long LINE_MS = 4500, FADE_MS = 900;   // was 7 s / 1.5 s: the feed hides itself sooner (Oct 22)
    static final int MAX_QUEUED = 10;
    static final int CROSS_COLOUR = 0x9AA0A8, NEUTRAL_COLOUR = 0xC8C8C8;
    // the line backing: dark and mostly opaque so the world behind can't wash the text out
    static final int BACKING_RGB = 0x101216, BACKING_ALPHA = 0xC8;
    static final int SWATCH_W = 4, SWATCH_GAP = 3;
    private static final int READ_ON = TextContrast.worstCaseBacking(BACKING_RGB, BACKING_ALPHA);
    private static final String CROSS = " \u2715 ";

    private static final class Line {
        Entry entry;
        int count;
        FormattedCharSequence killer, victim, cross;
        String countStr;
        int killerW, victimW, crossW, countW, width;
        int killerText, victimText, killerSwatch, victimSwatch;
        long startMs;
    }

    private static final Line[] lines = new Line[MAX_LINES];
    private static final ArrayDeque<Entry> queued = new ArrayDeque<>();
    private static int promoteCooldown = 0;

    static {
        for (int i = 0; i < MAX_LINES; i++)
            lines[i] = new Line();
    }

    /** From the packet handler (main thread). */
    public static void receive(java.util.List<Entry> entries) {
        long now = System.currentTimeMillis();
        for (Entry e : entries) {
            // the same kill again while its line is still up: bump the count instead of adding a line
            boolean merged = false;
            for (Line l : lines) {
                if (l.entry != null && now - l.startMs < LINE_MS && l.entry.sameKill(e)) {
                    l.count += e.count();
                    l.startMs = now;
                    layout(l);
                    merged = true;
                    break;
                }
            }
            if (merged)
                continue;
            if (queued.size() >= MAX_QUEUED)
                queued.pollFirst();   // drop the oldest: a stale kill line is noise
            queued.addLast(e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (promoteCooldown > 0)
            promoteCooldown--;
        if (promoteCooldown > 0 || queued.isEmpty())
            return;
        if (MC.level == null) {
            queued.clear();
            return;
        }
        Entry e = queued.pollFirst();
        // newest on top; the bottom line (oldest) falls off
        Line bottom = lines[MAX_LINES - 1];
        for (int s = MAX_LINES - 1; s > 0; s--)
            lines[s] = lines[s - 1];
        lines[0] = bottom;
        bottom.entry = e;
        bottom.count = e.count();
        bottom.startMs = System.currentTimeMillis();
        layout(bottom);
        promoteCooldown = PROMOTE_TICKS;
    }

    static String name(String key) {
        return key.isEmpty() ? "" : I18n.exists(key) ? I18n.get(key) : key;
    }

    static int ownerColour(String owner) {
        return owner == null || owner.isEmpty() ? NEUTRAL_COLOUR : PlayerColors.getPlayerDisplayColorHex(owner) & 0xFFFFFF;
    }

    /** The text cut to {@code budget} px with "..." (a very long unit or building name must not push the chip out of its column). */
    private static String fit(String text, int budget) {
        if (MC.font.width(text) <= budget)
            return text;
        return MC.font.plainSubstrByWidth(text, Math.max(8, budget - MC.font.width("..."))) + "...";
    }

    private static void layout(Line l) {
        Entry e = l.entry;
        Style base = e.building() ? Style.EMPTY.withBold(true) : Style.EMPTY;
        l.countStr = l.count > 1 ? " x" + l.count : "";
        l.countW = l.countStr.isEmpty() ? 0 : MC.font.width(l.countStr);
        l.crossW = MC.font.width(CROSS);
        // each name gets half of what is left of the chip's width (HudLayout.FEED_CHIP_MAX_W, before the 0.75 scale)
        int budget = (HudLayout.FEED_CHIP_MAX_W - 8 - (SWATCH_W + SWATCH_GAP) * 2 - l.crossW - l.countW) / 2;
        l.killer = Component.literal(fit(name(e.killerKey()), budget)).withStyle(base).getVisualOrderText();
        l.victim = Component.literal(fit(name(e.victimKey()), budget)).withStyle(base).getVisualOrderText();
        l.cross = Component.literal(CROSS).withStyle(base).getVisualOrderText();
        l.killerW = MC.font.width(l.killer);
        l.victimW = MC.font.width(l.victim);
        l.crossW = MC.font.width(l.cross);
        l.killerSwatch = ownerColour(e.killerOwner());
        l.victimSwatch = ownerColour(e.victimOwner());
        l.killerText = TextContrast.readable(l.killerSwatch, READ_ON);
        l.victimText = TextContrast.readable(l.victimSwatch, READ_ON);
        l.width = (SWATCH_W + SWATCH_GAP) * 2 + l.killerW + l.crossW + l.victimW + l.countW;
    }

    public static void clear() {
        for (Line l : lines)
            l.entry = null;
        queued.clear();
    }

    // drawn after the player panel (its strip / table sets the feed's top) in the same frame
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (!OrthoviewClientEvents.isEnabled() || !(evt.getScreen() instanceof TopdownGui) || !HudPanels.showExtras())
            return;
        // collapsed (the default, HudPanels): a one-line ticker - only the newest line, the rest wait their turn
        boolean ticker = HudPanels.killFeedCollapsed;
        int drawn = 0;
        long now = System.currentTimeMillis();
        int sw = MC.getWindow().getGuiScaledWidth(), sh = MC.getWindow().getGuiScaledHeight();
        // the side feed (lovish, Oct 22: the kill lines sat in the middle of the screen under the economy bar and the
        // toasts): small (FEED_SCALE), right-aligned under the stats strip, never over the minimap or the command panel
        int right = sw - HudLayout.STATS_RIGHT_MARGIN;
        int y = com.solegendary.reignofnether.hud.playerdisplay.PlayerPanelClientEvents.feedTop(sw);
        int limit = Math.min(HudClientEvents.bottomPanelTop, HudLayout.minimapTop(sh,
            com.solegendary.reignofnether.minimap.MinimapClientEvents.getMapGuiRadius(),
            com.solegendary.reignofnether.config.ReignOfNetherClientConfigs.SQUARE_MINIMAP.get()));
        GuiGraphics gg = evt.getGuiGraphics();
        for (Line l : lines) {
            if (l.entry == null)
                continue;
            long age = now - l.startMs;
            if (age >= LINE_MS) {
                l.entry = null;
                continue;
            }
            if (y + HudLayout.FEED_LINE_H > limit - 2)
                break;   // out of room above the map / command panel: the oldest lines wait
            if (ticker && drawn >= 1)
                continue;   // aged (and expired) above like the rest, just not drawn
            drawn++;
            long left = LINE_MS - age;
            float a = left < FADE_MS ? (float) left / FADE_MS : 1f;
            int alpha = Math.max(8, (int) (0xFF * a));   // the font treats alpha < 4 as opaque
            int w = l.width + 8;
            float sc = HudLayout.FEED_SCALE;
            int chipLeft = right - Math.round(w * sc);
            if (drawn == 1)   // the chevron left of the newest line: ticker <-> five lines
                HudPanels.chevron(gg, HudPanels.KILLFEED, chipLeft - HudPanels.CHEVRON - 2, y, ticker);
            gg.pose().pushPose();
            gg.pose().translate(chipLeft, y, 0);
            gg.pose().scale(sc, sc, 1f);
            gg.fill(0, 0, w, 12, ((int) (BACKING_ALPHA * a) << 24) | BACKING_RGB);
            int x = 4;
            int swA = (int) (0xFF * a) << 24;
            gg.fill(x, 2, x + SWATCH_W, 9, swA | l.killerSwatch);
            x += SWATCH_W + SWATCH_GAP;
            gg.drawString(MC.font, l.killer, x, 2, (alpha << 24) | l.killerText);
            x += l.killerW;
            gg.drawString(MC.font, l.cross, x, 2, (alpha << 24) | CROSS_COLOUR);
            x += l.crossW;
            gg.fill(x, 2, x + SWATCH_W, 9, swA | l.victimSwatch);
            x += SWATCH_W + SWATCH_GAP;
            gg.drawString(MC.font, l.victim, x, 2, (alpha << 24) | l.victimText);
            x += l.victimW;
            if (l.countW > 0)
                gg.drawString(MC.font, l.countStr, x, 2, (alpha << 24) | 0xFFFFFF);
            gg.pose().popPose();
            y += HudLayout.FEED_LINE_H;
        }
    }
}
