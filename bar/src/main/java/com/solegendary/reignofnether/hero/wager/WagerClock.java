package com.solegendary.reignofnether.hero.wager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The fairness clock for legendary (T4) phenomena (design "Multiplayer fairness"): one per hero every 60 s and one per
 * intent every 120 s across the whole match, so two Lovishes on opposite teams cannot chain monsoons. The server uses
 * {@link #MATCH}; a test makes its own.
 */
public final class WagerClock {

    public static final WagerClock MATCH = new WagerClock();
    public static final int HERO_T4_TICKS = 60 * 20;
    public static final int INTENT_T4_TICKS = 120 * 20;

    private final Map<UUID, Long> byHero = new HashMap<>();
    private final Map<String, Long> byIntent = new HashMap<>();

    public synchronized boolean mayGrantLegendary(UUID hero, String intent, long now) {
        Long h = byHero.get(hero), i = byIntent.get(intent);
        return (h == null || now - h >= HERO_T4_TICKS) && (i == null || now - i >= INTENT_T4_TICKS);
    }

    public synchronized void recordLegendary(UUID hero, String intent, long now) {
        byHero.put(hero, now);
        byIntent.put(intent, now);
    }

    /** The tier the clock allows: a T4 it refuses becomes a T3. */
    public int fairTier(UUID hero, String intent, int tier, long now) {
        return tier >= 4 && !mayGrantLegendary(hero, intent, now) ? 3 : tier;
    }

    /** A new match starts the clocks over (NewMatch / server stop). */
    public synchronized void reset() {
        byHero.clear();
        byIntent.clear();
    }
}
