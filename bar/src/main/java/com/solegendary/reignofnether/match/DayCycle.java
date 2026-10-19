package com.solegendary.reignofnether.match;

/**
 * Whether the sun moves during a match, and how fast - the lobby's "Day" choice next to its start time (TimeOfDay).
 * lovish, Oct 18 2026: "Day/night cycle: on/off" per match, "make the days and nights drawn out so it doesnt feel
 * super fast", and he likes the cycle, so it is on by default at the slow speed. Vanilla's 20-minute day is "Normal";
 * "Slow" is a third of that - a 60-minute day, so a morning start (5000) keeps its daylight for about 17 minutes and is
 * night by minute 20, where Normal is dark before minute 7. The one number to tune after a playtest is {@link #ticksPerStep}.
 * DayClock moves the clock; ids travel as a byte (start packet, lobby snapshot): only ever append.
 */
public enum DayCycle {
    OFF("Off", "The sun stays where the match started", 0),
    SLOW("Slow", "A slow day: one day and night take 60 minutes - from a morning start, dusk about 17 minutes in, night by 20 (the default)", 3),
    NORMAL("Normal", "Minecraft's own day: one day and night take 20 minutes - from a morning start, dusk about 6 minutes in", 1);

    /** The lobby's default: the cycle on, drawn out. */
    public static final DayCycle DEFAULT = SLOW;

    public final String displayName;
    public final String blurb;
    /** Game ticks per day-time tick: 1 is vanilla speed, 3 a third of it; 0 = the sun stands still. */
    public final int ticksPerStep;

    DayCycle(String displayName, String blurb, int ticksPerStep) {
        this.displayName = displayName;
        this.blurb = blurb;
        this.ticksPerStep = ticksPerStep;
    }

    public static DayCycle byId(int id) {
        DayCycle[] all = values();
        return id >= 0 && id < all.length ? all[id] : DEFAULT;
    }

    /** The cycle whose speed is {@code ticksPerStep} (the saved game rule); an unknown speed is still a moving sun. */
    public static DayCycle bySpeed(int ticksPerStep) {
        for (DayCycle c : values())
            if (c.ticksPerStep == ticksPerStep)
                return c;
        return ticksPerStep <= 0 ? OFF : SLOW;
    }

    public static int count() {
        return values().length;
    }
}
