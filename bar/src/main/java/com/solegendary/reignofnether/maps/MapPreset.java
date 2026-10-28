package com.solegendary.reignofnether.maps;

/**
 * The battlefield the lobby host picks (design/maps.md): the old procedural terrain ("Vanilla terrain (legacy)" since
 * inbox spec-2 PR 1 made Verdant Valley the default) or one of the pre-made
 * maps. A pre-made map is a deterministic layout in code (MapLayout) stamped into the skirmish world at Start
 * (MapBuilder) - no structure files. Ids travel in lobby packets as bytes, so values are only ever appended.
 */
public enum MapPreset {
    RANDOM("Vanilla terrain (legacy)", "The world's own rolling terrain, a procedural ring of metal and generic sites",
        Theme.OVERWORLD, 0, 0),
    ARCHIPELAGO("Archipelago", "Islands in a shallow sea, sand fords, Sunken Bells on the islets, a lighthouse",
        Theme.OVERWORLD, 160, 92),
    HIGHLANDS("Highlands", "Plateau bases with ramps, ridges broken by passes, a forge in the basin",
        Theme.OVERWORLD, 150, 86),
    NETHER_RIFT("Nether Rift", "A lava rift crossed by basalt bridges, capturable lava vents, debris mine",
        Theme.NETHER, 150, 86),
    END_SPIRES("End Spires", "End stone islands over the void joined by bridges, obsidian spires",
        Theme.END, 150, 86),
    VERDANT_VALLEY("Verdant Valley", "Rolling meadows, a ring river with fords, forest reclaim lanes and camps",
        Theme.OVERWORLD, 150, 86),
    DESERT_MESA("Desert Mesa", "Open dunes, mesas with high-ground metal, oases that heal",
        Theme.OVERWORLD, 155, 86),
    // realm maps (design/maps.md 4.7, 4.8): the Nether and the End as places to fight in, not just block palettes
    SOULFIRE_DELTAS("Soulfire Deltas", "Slow soul-sand valleys between the bases, lava lakes with geothermal vents, "
        + "fungus groves, nether-brick garrisons that muster wither skeletons", Theme.NETHER, 155, 90),
    CHORUS_ISLES("Chorus Isles", "Railed end-stone islands joined by land necks and bridges, air rules the void, "
        + "obsidian garrisons that muster endermen", Theme.END, 155, 96),
    // generated maps (design/maps.md "Generated maps", lovish Oct 21 22:28): a new natural battlefield from every seed,
    // the sides given different characters and evened out by a measured fairness score (maps/gen/MapGenerator)
    GENERATED("Generated", "A new natural battlefield every seed: tiers, ramps, rivers, different sides evened out, "
        + "map modifiers", Theme.OVERWORLD, 150, 90);

    /** Which blocks and sky tint the map uses (all of them are built in the Overworld, see design/maps.md). */
    public enum Theme { OVERWORLD, NETHER, END }

    public final String displayName;
    public final String blurb;
    public final Theme theme;
    /** Start ring radius for a 1v1; more players widen it so neighbours keep their distance (MapLayout). */
    public final int baseRing;
    /** Arena wall distance beyond the start ring (backfield metal lives there). */
    public final int margin;

    MapPreset(String displayName, String blurb, Theme theme, int baseRing, int margin) {
        this.displayName = displayName;
        this.blurb = blurb;
        this.theme = theme;
        this.baseRing = baseRing;
        this.margin = margin;
    }

    /** A map the game stamps itself (every preset but the world's own terrain), hand-made or generated. */
    public boolean premade() {
        return this != RANDOM;
    }

    /** Drawn by the map generator from the match's seed (MapLayout.build delegates to maps/gen/MapGenerator). */
    public boolean generated() {
        return this == GENERATED;
    }

    /**
     * A hand-made layout: folded into slot frames, so fair by construction (design/maps.md section 1) - what the
     * symmetric fairness tests sweep. A generated map is measured instead (MapGenGameTests).
     */
    public boolean handmade() {
        return premade() && !generated();
    }

    public static MapPreset byId(int id) {
        MapPreset[] all = values();
        return id >= 0 && id < all.length ? all[id] : RANDOM;
    }

    public static int count() {
        return values().length;
    }

    /** Fog tint for the client (0xRRGGBB), or -1 to leave vanilla's. */
    public int fogColour() {
        // the realm maps carry their own haze: crimson ash over the deltas, a paler violet over the isles (the void
        // below must still read darker than the end stone from the RTS camera)
        if (this == SOULFIRE_DELTAS)
            return 0x34101A;
        if (this == CHORUS_ISLES)
            return 0x241634;
        return switch (theme) {
            case NETHER -> 0x3A0C08;
            case END -> 0x1A1024;
            default -> -1;
        };
    }

    /** Lang key of the name (the blurb is {@code key + ".blurb"}); displayName is the fallback (MapPreview.nameOf). */
    public String langKey() {
        return "maps.reignofnether.preset." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Whether every edge between walkable ground and the void carries a guard rail (MapLayout.railAt). Chorus Isles
     * only: its islands are the map, so a unit pushed off an edge would be lost for good (design/maps.md 4.8). End
     * Spires predates the rails and keeps its 5-wide bridges walkable as they are.
     */
    public boolean railedVoid() {
        return this == CHORUS_ISLES;
    }
}
