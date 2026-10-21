package com.solegendary.reignofnether.match;

/**
 * The battlefield light the skirmish lobby picks (inbox spec-2 PR 1, section 2.1). Rover playtest on lovish's PC, Oct
 * 17: a skirmish world started at sunrise and Complementary's low golden sun washed the whole map orange; at day time
 * 5000 the same ground read green. PR #2 starts every fresh skirmish world at that late morning with the daylight
 * cycle off (ArenaStructureClearing.onServerStarted); this makes the light the host's choice. Morning is the default.
 * Night stays possible on purpose: lanterns and lit buildings are part of the look (BUILDING STANDARD rule 7).
 * Ids travel as a byte in SkirmishServerboundPacket and the lobby snapshot, and are saved by name in MatchSetup: only
 * ever append. Whether the sun then moves is the lobby's other choice, DayCycle (lovish, Oct 18: "Day/night cycle:
 * on/off" with a start time, days "drawn out so it doesnt feel super fast") - so the old fifth value "Cycle" (a
 * morning start at vanilla speed) became Morning + DayCycle.NORMAL.
 */
public enum TimeOfDay {
    MORNING("Morning", "Late morning, the sun high enough to light the ground rather than tint it (the default)", 5000L),
    NOON("Noon", "The sun overhead: shortest shadows, flattest light", 6000L),
    DUSK("Dusk", "A low evening sun: long shadows, warm light", 11800L),
    NIGHT("Night", "Moonlight: lanterns and glowing buildings carry the picture", 18000L);

    public final String displayName;
    public final String blurb;
    /** The overworld's day time at Start. */
    public final long dayTime;

    TimeOfDay(String displayName, String blurb, long dayTime) {
        this.displayName = displayName;
        this.blurb = blurb;
        this.dayTime = dayTime;
    }

    public static TimeOfDay byId(int id) {
        TimeOfDay[] all = values();
        return id >= 0 && id < all.length ? all[id] : MORNING;
    }

    /** The saved name back to a value; anything unknown (an older save) is the default. */
    public static TimeOfDay byName(String name) {
        for (TimeOfDay t : values())
            if (t.name().equals(name))
                return t;
        return MORNING;
    }

    public static int count() {
        return values().length;
    }
}
