package com.solegendary.reignofnether.match;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * The battlefield light the skirmish lobby picks (inbox spec-2 PR 1, section 2.1). Rover playtest on lovish's PC, Oct
 * 17: a skirmish world started at sunrise and Complementary's low golden sun washed the whole map orange; at day time
 * 5000 the same ground read green. PR #2 starts every fresh skirmish world at that late morning with the daylight
 * cycle off (ArenaStructureClearing.onServerStarted); this makes the light the host's choice. Morning is the default.
 * Night stays possible on purpose: lanterns and lit buildings are part of the look (BUILDING STANDARD rule 7).
 * Ids travel as a byte in SkirmishServerboundPacket and are saved by name in MatchSetup: only ever append.
 */
public enum TimeOfDay {
    MORNING("Morning", "Late morning, the sun high enough to light the ground rather than tint it (the default)", 5000L, false),
    NOON("Noon", "The sun overhead: shortest shadows, flattest light", 6000L, false),
    DUSK("Dusk", "A low evening sun: long shadows, warm light", 11800L, false),
    NIGHT("Night", "Moonlight: lanterns and glowing buildings carry the picture", 18000L, false),
    CYCLE("Cycle", "Starts at late morning and lets the day run: dusk about 6 minutes in, night soon after", 5000L, true);

    public final String displayName;
    public final String blurb;
    /** The overworld's day time at Start. */
    public final long dayTime;
    /** Whether the daylight cycle runs during the match. */
    public final boolean cycle;

    TimeOfDay(String displayName, String blurb, long dayTime, boolean cycle) {
        this.displayName = displayName;
        this.blurb = blurb;
        this.dayTime = dayTime;
        this.cycle = cycle;
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

    /** Sets the overworld's clock and daylight rule for this light (server thread). */
    public void apply(ServerLevel anyLevel) {
        MinecraftServer server = anyLevel.getServer();
        ServerLevel overworld = server.overworld();
        overworld.setDayTime(dayTime);
        overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(cycle, server);
    }
}
