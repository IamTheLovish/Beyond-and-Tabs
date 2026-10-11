package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.KillFeedClientboundPacket.Entry;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;

/**
 * BAR kill feed: up to {@link #MAX_LINES} fading lines, newest on top, right-aligned in the alert column under the
 * economy bar and the toasts (HudClientEvents.alertColumn / NotificationClientEvents). A line reads
 * "Stag Lancer x Zombie" with each name in its owner's colour, " x3" when the server merged duplicates or the same
 * kill arrives again while the line is up, and in bold when the victim was a building. Lines are promoted from a
 * small queue at most one per {@link #PROMOTE_TICKS} ticks, so a big fight scrolls instead of flashing; the text is
 * laid out once per line (a FormattedCharSequence and its width), nothing allocates per frame.
 */
public class KillFeedClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    public static final int MAX_LINES = 5;
    public static final int PROMOTE_TICKS = 5;
    static final long LINE_MS = 7000, FADE_MS = 1500;
    static final int MAX_QUEUED = 10;
    static final int CROSS_COLOUR = 0x9AA0A8, NEUTRAL_COLOUR = 0xC8C8C8;

    private static final class Line {
        Entry entry;
        int count;
        FormattedCharSequence text;
        int width;
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

    private static void layout(Line l) {
        Entry e = l.entry;
        Style base = e.building() ? Style.EMPTY.withBold(true) : Style.EMPTY;
        MutableComponent c = Component.literal(name(e.killerKey()))
            .withStyle(base.withColor(TextColor.fromRgb(ownerColour(e.killerOwner()))));
        c.append(Component.literal(" ✕ ").withStyle(base.withColor(TextColor.fromRgb(CROSS_COLOUR))));
        c.append(Component.literal(name(e.victimKey()))
            .withStyle(base.withColor(TextColor.fromRgb(ownerColour(e.victimOwner())))));
        if (l.count > 1)
            c.append(Component.literal(" x" + l.count).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
        l.text = c.getVisualOrderText();
        l.width = MC.font.width(l.text);
    }

    public static void clear() {
        for (Line l : lines)
            l.entry = null;
        queued.clear();
    }

    // drawn after HudClientEvents (which sets alertBottom) and NotificationClientEvents (LOW) in the same frame
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (!OrthoviewClientEvents.isEnabled() || !(evt.getScreen() instanceof TopdownGui) || !HudClientEvents.enabled)
            return;
        long now = System.currentTimeMillis();
        int[] col = HudClientEvents.alertColumn(MC.getWindow().getGuiScaledWidth());
        int right = col[1] - 2;
        int y = HudClientEvents.alertBottom + 2 + NotificationClientEvents.visibleHeight();
        GuiGraphics gg = evt.getGuiGraphics();
        for (Line l : lines) {
            if (l.entry == null)
                continue;
            long age = now - l.startMs;
            if (age >= LINE_MS) {
                l.entry = null;
                continue;
            }
            if (y + 10 > HudClientEvents.bottomPanelTop - 2)
                break;   // out of room above the command panel (GUI scale 4 on 1080p): the oldest lines wait
            long left = LINE_MS - age;
            float a = left < FADE_MS ? (float) left / FADE_MS : 1f;
            int alpha = Math.max(8, (int) (0xFF * a));   // the font treats alpha < 4 as opaque
            int w = l.width + 8;
            gg.fill(right - w, y - 2, right, y + 10, ((int) (0x90 * a) << 24) | 0x101216);
            gg.drawString(MC.font, l.text, right - 4 - l.width, y, (alpha << 24) | 0xFFFFFF);
            y += 12;
        }
    }
}
