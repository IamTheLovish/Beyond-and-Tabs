package com.solegendary.reignofnether.config;

/**
 * Graphics presets (N1, design/HANDOFF-lane-c.md C5): one Display setting that picks the costly display features
 * together, so the game stays playable at 300 units on a weak PC. A preset only writes the existing switches
 * (world look, valley mist, path edges, building ambience, building animation, branch wind); each stays editable
 * on its own, and a mix that matches no preset reads as Custom. High is today's defaults, so nothing changes for a
 * player who never touches it. No Minecraft types in the mapping: GraphicsPresetsTest checks it headless.
 */
public final class GraphicsPresets {

    private GraphicsPresets() { }

    public enum Preset { LOW, MEDIUM, HIGH, ULTRA }

    public static final String[] NAMES = { "Low", "Medium", "High", "Ultra" };

    /** worldLook and buildingAnimation use DisplaySettings' LOOK_* / ANIM_* values; branchWind 0 Off, 1 Near, 2 All. */
    public record Values(int worldLook, boolean valleyMist, boolean pathEdges, boolean buildingAmbience,
                         int buildingAnimation, int branchWind) { }

    public static Values values(Preset p) {
        return switch (p) {
            case LOW -> new Values(DisplaySettings.LOOK_OFF, false, false, false, DisplaySettings.ANIM_OFF, 0);
            case MEDIUM -> new Values(DisplaySettings.LOOK_SUBTLE, false, true, true, DisplaySettings.ANIM_LOW, 0);
            case HIGH -> new Values(DisplaySettings.LOOK_SUBTLE, true, true, true, DisplaySettings.ANIM_FULL, 1);
            case ULTRA -> new Values(DisplaySettings.LOOK_FULL, true, true, true, DisplaySettings.ANIM_FULL, 2);
        };
    }

    /** The preset these settings are, or null (Custom). */
    public static Preset match(Values current) {
        for (Preset p : Preset.values())
            if (values(p).equals(current))
                return p;
        return null;
    }

    /** The next preset for the cycle button; Custom starts at Low. */
    public static Preset next(Preset p) {
        return p == null ? Preset.LOW : Preset.values()[(p.ordinal() + 1) % Preset.values().length];
    }

    /** The settings as the game reads them now. */
    public static Values current() {
        return new Values(DisplaySettings.worldLook(), DisplaySettings.valleyMist(), DisplaySettings.pathEdges(),
            DisplaySettings.buildingAmbience(), DisplaySettings.buildingAnimation(), DisplaySettings.branchWind());
    }

    /** Writes the preset's values to the client config. Returns whether path edges changed (the terrain must rebuild). */
    public static boolean apply(Preset p) {
        Values v = values(p);
        boolean edgesChanged = DisplaySettings.pathEdges() != v.pathEdges();
        ReignOfNetherClientConfigs.WORLD_LOOK.set(v.worldLook());
        ReignOfNetherClientConfigs.VALLEY_MIST.set(v.valleyMist());
        ReignOfNetherClientConfigs.PATH_EDGES.set(v.pathEdges());
        ReignOfNetherClientConfigs.BUILDING_AMBIENCE.set(v.buildingAmbience());
        ReignOfNetherClientConfigs.BUILDING_ANIMATION.set(v.buildingAnimation());
        ReignOfNetherClientConfigs.BRANCH_WIND.set(v.branchWind());
        return edgesChanged;
    }
}
