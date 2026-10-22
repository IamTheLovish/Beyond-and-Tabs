package com.solegendary.reignofnether.hud;

import java.util.HashMap;
import java.util.Map;

/**
 * Collapse rule for the mod's own system chat lines (T3 "has fielded an experimental", hero "Boon received"), plain
 * Java so a GameTest can drive it with a fake clock. Playtest clip: the same line stacked 2-3 times mid-left over the
 * map and sat there. {@link #seen} says how many times this exact text has arrived inside {@link #WINDOW_MS}; the
 * caller shows the first and drops the repeats.
 */
public final class ChatDedupe {

    public static final long WINDOW_MS = 10_000;

    private static final class Entry {
        long firstMs;
        int count;
    }

    private final Map<String, Entry> entries = new HashMap<>();

    /** Registers one arrival of {@code text} at {@code nowMs}; returns 1 for a fresh line, 2+ for a repeat in the window. */
    public int seen(String text, long nowMs) {
        Entry e = entries.get(text);
        if (e == null || nowMs - e.firstMs >= WINDOW_MS || nowMs < e.firstMs) {
            if (entries.size() > 64)
                entries.values().removeIf(x -> nowMs - x.firstMs >= WINDOW_MS || nowMs < x.firstMs);
            e = new Entry();
            e.firstMs = nowMs;
            entries.put(text, e);
        }
        return ++e.count;
    }

    /** Is this translation key one of the mod's own announcement lines (the only chat this collapses)? */
    public static boolean isAnnouncement(String key) {
        return key != null && (key.startsWith("server.reignofnether.") || key.startsWith("hud.hero.reignofnether.boon"));
    }

    public void clear() {
        entries.clear();
    }
}
