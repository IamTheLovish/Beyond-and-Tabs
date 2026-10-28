package com.solegendary.reignofnether.hero.wager;

import net.minecraft.server.level.ServerLevel;

/**
 * Weather from a wager, ended on our own clock (Mode A B1): the mod's load function turns doWeatherCycle off, so
 * vanilla's rain timer never runs out in a match. Stub: the red-proof commit.
 */
public final class WagerWeather {
    private WagerWeather() { }

    public static void start(ServerLevel level, boolean rain, int ticks) {
        level.setWeatherParameters(rain ? 0 : ticks, rain ? ticks : 0, rain, false);
    }

    /** The game time the wager weather in this level ends, -1 when none is running. */
    public static long until(ServerLevel level) {
        return -1;
    }
}
