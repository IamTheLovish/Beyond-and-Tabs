package com.solegendary.reignofnether.resources;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The match's economy pace (design/economy_pace.md): one multiplier on every resource income (capitol, extractors,
 * generators, converters, reclaim, capture-site income) AND on construction and production speed - so a Relaxed
 * match is the same game played slower, not a poorer one. Movement, combat, ranges and cooldowns are untouched.
 *
 * The host picks it in the skirmish lobby or the multiplayer lobby (Relaxed / Standard / Fast). It is match-wide
 * state: set when the match starts, saved with the world (MatchStateSaveData), synced to clients on the 4x-a-second
 * economy packet so the HUD, the production prediction, the match menu and the results screen agree.
 *
 * Standard (1.0) is the pace the game was tuned and playtested at. Relaxed 0.75 / Fast 1.35: see the design doc.
 */
public final class EconomyPace {
    private EconomyPace() { }

    public static final int RELAXED = 0, STANDARD = 1, FAST = 2;
    private static final float[] MULTIPLIERS = { 0.75f, 1.0f, 1.35f };
    private static final String[] NAMES = { "Relaxed", "Standard", "Fast" };

    /** Server: the running match's pace code. */
    private static volatile int matchCode = STANDARD;
    /** Client: the pace the server last synced (EconomyClientboundPacket). */
    private static volatile int clientCode = STANDARD;

    /**
     * Per-owner overrides for the GameTests only: tests run in parallel in one world, so a test that changed the
     * match-wide pace would change every other test's income. A test pins its own owner instead.
     */
    private static final Map<String, Integer> testOverrides = new ConcurrentHashMap<>();

    public static int clamp(int code) {
        return code < RELAXED || code > FAST ? STANDARD : code;
    }

    public static float multiplier(int code) {
        return MULTIPLIERS[clamp(code)];
    }

    public static String name(int code) {
        return NAMES[clamp(code)];
    }

    /** Short lobby tooltip text for a pace code. */
    public static String describe(int code) {
        return switch (clamp(code)) {
            case RELAXED -> "Relaxed: all income and build/production speed x0.75 - more time per decision. Movement and combat unchanged.";
            case FAST -> "Fast: all income and build/production speed x1.35 - for players who know the game. Movement and combat unchanged.";
            default -> "Standard: the pace the game is tuned for (x1.0).";
        };
    }

    // ---- server ----

    public static int getMatchCode() {
        return matchCode;
    }

    public static void setMatchCode(int code) {
        matchCode = clamp(code);
    }

    /** The multiplier for one owner's economy (the match pace, unless a GameTest pinned that owner). */
    public static float of(String ownerName) {
        if (!testOverrides.isEmpty() && ownerName != null) {
            Integer o = testOverrides.get(ownerName);
            if (o != null)
                return multiplier(o);
        }
        return MULTIPLIERS[matchCode];
    }

    /** The pace code for one owner (what the economy packet sends). */
    public static int codeOf(String ownerName) {
        if (!testOverrides.isEmpty() && ownerName != null) {
            Integer o = testOverrides.get(ownerName);
            if (o != null)
                return o;
        }
        return matchCode;
    }

    /** GameTests: pin one owner's pace (null clears it). */
    public static void setTestOverride(String ownerName, Integer code) {
        if (code == null)
            testOverrides.remove(ownerName);
        else
            testOverrides.put(ownerName, clamp(code));
    }

    /** A new world: back to Standard until its save (or a lobby start) says otherwise. */
    public static void reset() {
        matchCode = STANDARD;
    }

    // ---- client ----

    public static int getClientCode() {
        return clientCode;
    }

    public static float clientMultiplier() {
        return MULTIPLIERS[clientCode];
    }

    public static void setClientCode(int code) {
        clientCode = clamp(code);
    }
}
