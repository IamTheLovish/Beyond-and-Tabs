package com.solegendary.reignofnether.maps.gen;

/**
 * Per-match map modifiers (lovish Oct 21 22:28: "sometimes changes rng with different buffs and bonuses"). Each one is
 * either built from a mechanic the game already has ({@link #live}) or needs a new one (named in {@link #needs}); the
 * lobby rolls only live ones, the experiments roll all of them to price the new ideas before anyone builds them.
 */
public enum Modifier {
    RICH_VEIN("Rich vein", "A seam of rich (1.5x) metal runs through one side's ground", true, 3, null),
    LEY_LINE("Ley line", "Ley Shrines (+30 energy/s) stand along a line across the map", true, 2, null),
    GEOTHERMAL("Geothermal rift", "Lava vents for geothermal plants and a capturable Lava Vent on contested ground",
        true, 2, null),
    GOLD_RUSH("Gold rush", "The contested ground's metal is rich and the centre holds twice the gold", true, 2, null),
    MERCENARY_ROAD("Mercenary road", "Mercenary Camps hire wolves along the roads between the sides", true, 2, null),
    GARRISON_RUINS("Garrison ruins", "Forsaken Garrisons muster recruits on the front seams", true, 2, null),
    WATCH_RIDGE("Watch ridge", "Watchtowers crown the high ground (64-block sight)", true, 1, null),
    // new mechanics: data only until built (the generator places their spots so the map has room for them)
    HERO_RELIC("Hero relic", "A relic site: its holder's heroes fight stronger", false, 2, "a new capture kind RELIC"),
    HYDRA_LAIR("Hydra lair", "A hydra sleeps in the middle; the hero who slays it is buffed for minutes", false, 1,
        "a neutral boss unit, a lair, a timed hero buff (Oct 20 jungle plan)"),
    FOG_VALLEYS("Fog valleys", "Low valleys fill with fog: sight drops to 8 blocks inside", false, 2,
        "valley fog that limits sight (world-memory slice 5 + a fog-of-war sight cap)");

    public final String displayName;
    public final String blurb;
    public final boolean live;
    public final double weight;
    /** What has to be built before a non-live modifier can play (null when live). */
    public final String needs;

    Modifier(String displayName, String blurb, boolean live, double weight, String needs) {
        this.displayName = displayName;
        this.blurb = blurb;
        this.live = live;
        this.weight = weight;
        this.needs = needs;
    }
}
