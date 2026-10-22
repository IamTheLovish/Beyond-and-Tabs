package com.solegendary.reignofnether.unit;

import java.util.HashMap;
import java.util.Map;

/**
 * "Say it once" rule for server announcements, plain Java so a GameTest can check it without a world.
 *
 * Playtest clip (lovish, 1v2, 25 min): "X has fielded an experimental: Sun Colossus!" showed 2-3 times stacked, and
 * the Iron Leviathan Engine line twice. Every T3 unit announced itself from its own tick (the KEY_ANNOUNCED flag is
 * per unit), so a second Colossus, or an engine and its twin, repeated the line for the same player within seconds.
 * The gate lets one announcement per key (owner + unit type) through per window.
 */
public final class AnnounceGate {

    private final Map<String, Long> last = new HashMap<>();

    /** True (and the key is stamped) when {@code key} has not been let through in the last {@code windowMs}. */
    public boolean allow(String key, long nowMs, long windowMs) {
        Long prev = last.get(key);
        if (prev != null && nowMs - prev >= 0 && nowMs - prev < windowMs)
            return false;
        if (last.size() > 256)   // keys are owner|unit type: tiny in practice, but never unbounded
            last.values().removeIf(t -> nowMs - t >= windowMs || nowMs - t < 0);
        last.put(key, nowMs);
        return true;
    }

    public void clear() {
        last.clear();
    }
}
