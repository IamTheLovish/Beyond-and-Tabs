package com.solegendary.reignofnether.maps;

/**
 * The battlefield the lobby host picks (design/maps.md): the old procedural "Random terrain" or one of the pre-made
 * maps. A pre-made map is a deterministic layout in code (MapLayout) stamped into the skirmish world at Start
 * (MapBuilder) - no structure files. Ids travel in lobby packets as bytes, so values are only ever appended.
 */
public enum MapPreset {
    RANDOM("Random terrain", "The world's own rolling terrain, a procedural ring of metal and generic sites",
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
        Theme.OVERWORLD, 155, 86);

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

    public boolean premade() {
        return this != RANDOM;
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
        return switch (theme) {
            case NETHER -> 0x3A0C08;
            case END -> 0x1A1024;
            default -> -1;
        };
    }
}
