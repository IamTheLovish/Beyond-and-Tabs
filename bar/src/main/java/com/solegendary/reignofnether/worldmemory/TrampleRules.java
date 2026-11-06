package com.solegendary.reignofnether.worldmemory;

/**
 * Trampled ground, the world memory's slice 2 (design/battlefield_effects.md): the pure rules, no Minecraft classes, so
 * JUnit checks every number (TrampleRulesTest).
 * <p>
 * Units walking over soft ground wear it. Each unit's path since the last sample is stamped into the field's
 * {@link GroundMemory#TRAMPLE} channel as a capsule (GroundMemory.addTrampleCapsule), so a cell the unit crossed is
 * worn once per pass, whatever the unit's speed or the sample spacing. The stamp saturates at 255: a road that is used
 * all match stops changing, so it stops costing sync bytes and mesh rebuilds. Decay is DecayParams' trample half-life
 * (8 minutes, to nothing).
 * <p>
 * The look goes in two stages. The grass is first flattened: lighter and straw-yellow, because bent blades catch the
 * light. Then it wears down to soil: warmer and close in value to the lawn, inside the dirt-path band, so a worn track
 * reads as a path and never as a hole or a shadow. {@link #lookArgb} is the vertex colour the decal mesh draws.
 */
public final class TrampleRules {
    private TrampleRules() { }

    /** What a column's top block does under feet. {@link #wear} scales the stamp. */
    public enum Surface {
        NONE(0f), GRASS(1f), SOIL(0.7f), SAND(0.8f), SNOW(1.4f);

        public final float wear;

        Surface(float wear) {
            this.wear = wear;
        }
    }

    /** Server ticks between two samples of every unit's position. */
    public static final int SAMPLE_TICKS = 10;
    /** A unit that moved less than this between samples is standing (idle sway, a melee shuffle): no stamp. */
    public static final double MIN_STEP = 0.3;
    /** ...and one that moved more than this was teleported, spawned or respawned: no stamp across the gap. */
    public static final double MAX_STEP = 8.0;
    /** A militia's body width: the unit every other wear is measured against. */
    public static final double REFERENCE_WIDTH = 0.6;
    /** One militia pass, at the centre of its track (12 militia flatten the grass, 20+ wear it to soil). */
    public static final int BASE_PEAK = 12;
    public static final float MIN_SCALE = 0.75f, MAX_SCALE = 4f;
    /** The part of the capsule's radius worn at the full peak (then a smooth fall to the edge). */
    public static final double CORE = 0.4;

    /** Where the look turns from flattened grass to worn soil, in channel units. */
    public static final int FLAT_FROM = 6, WORN_FROM = 200;

    /** The surface of a top block by its registry id ("minecraft:grass_block"). Anything not listed is NONE. */
    public static Surface surfaceOf(String blockId) {
        if (blockId == null)
            return Surface.NONE;
        return switch (blockId) {
            case "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium", "minecraft:moss_block" -> Surface.GRASS;
            case "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:mud", "minecraft:farmland",
                 "minecraft:clay", "minecraft:soul_soil", "minecraft:muddy_mangrove_roots" -> Surface.SOIL;
            case "minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:soul_sand" -> Surface.SAND;
            case "minecraft:snow", "minecraft:snow_block", "minecraft:powder_snow" -> Surface.SNOW;
            default -> Surface.NONE;
        };
    }

    /** How much wider than a militia a body is, clamped to [{@link #MIN_SCALE}, {@link #MAX_SCALE}]. */
    public static float scaleFor(double bodyWidth) {
        if (!(bodyWidth > 0))
            return MIN_SCALE;
        return (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, bodyWidth / REFERENCE_WIDTH));
    }

    /** One pass's peak wear at the centre of a unit's track on a GRASS surface (scaled by the surface's wear). */
    public static int peakFor(double bodyWidth) {
        return Math.max(1, Math.round(BASE_PEAK * scaleFor(bodyWidth)));
    }

    /** The track's half-width: a little wider than the body (feet spread, the grass round them bends too). */
    public static double radiusFor(double bodyWidth) {
        double w = bodyWidth > 0 ? bodyWidth : REFERENCE_WIDTH;
        return Math.max(0.6, Math.min(3.0, 0.45 + w * 0.5));
    }

    /** Whether a move between two samples is a walk that wears the ground. */
    public static boolean walked(double dx, double dz) {
        double d2 = dx * dx + dz * dz;
        return d2 >= MIN_STEP * MIN_STEP && d2 <= MAX_STEP * MAX_STEP;
    }

    /** Distance from (px, pz) to the segment (ax, az)-(bx, bz). */
    public static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double t = len2 <= 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len2));
        double dx = px - (ax + t * vx), dz = pz - (az + t * vz);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * The wear a cell centre at (px, pz) takes from one pass along the segment: 1 in the core, falling to 0 at the
     * radius, across the walk. The ends are FLAT: only points whose projection falls in (a, b] are worn, so one walk's
     * consecutive samples tile its path - a round cap at every sample point would wear a bead of double depth there,
     * a dotted track. A zero-length segment wears nothing.
     */
    public static float capsuleWeight(double px, double pz, double ax, double az, double bx, double bz, double radius) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        if (len2 <= 1e-12)
            return 0;
        double t = ((px - ax) * vx + (pz - az) * vz) / len2;
        if (t <= 0 || t > 1)
            return 0;
        double dx = px - (ax + t * vx), dz = pz - (az + t * vz);
        return GroundMemory.discWeight(Math.sqrt(dx * dx + dz * dz), radius, CORE);
    }

    // ------------------------------------------------------------------ the look

    /*
     * The colour targets come from the environment palette's bands (the lawn at noon: luma 0.33-0.40, hue 72-82; dirt and
     * paths: luma 0.30-0.40, hue 28-40, warmer than the lawn and close in value). The decal texture multiplies the vertex
     * colour (TEXTURE_RGB_MEAN, checked by tools/gen_scars.py when it writes trample.png) and its alpha thins it
     * (TEXTURE_ALPHA_MEAN); TrampleRulesTest composites the result over three lawn shades.
     */
    public static final float TEXTURE_RGB_MEAN = 0.86f, TEXTURE_ALPHA_MEAN = 0.90f;
    // flattened grass: pale straw; worn soil: warm earth. Before the ground tint (GROUND_MIX of the block's map colour)
    static final int FLAT_R = 205, FLAT_G = 198, FLAT_B = 128;
    static final int WORN_R = 160, WORN_G = 96, WORN_B = 58;
    static final float FLAT_ALPHA = 0.20f, WORN_ALPHA = 0.78f;
    // packed soil darkens, sand roughens darker, snow turns to slush
    static final int SOIL_R = 92, SOIL_G = 70, SOIL_B = 50;
    static final float SOIL_ALPHA = 0.42f;
    static final int SAND_R = 150, SAND_G = 126, SAND_B = 92;
    static final float SAND_ALPHA = 0.40f;
    static final int SNOW_R = 150, SNOW_G = 140, SNOW_B = 128;
    static final float SNOW_ALPHA = 0.60f;
    public static final float GROUND_MIX = 0.10f;

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static int mix(int a, int b, float t) {
        return Math.max(0, Math.min(255, Math.round(a + (b - a) * t)));
    }

    /**
     * The decal's vertex colour for a trample value (0-255) on a surface, as ARGB (alpha 0-255), with {@code groundRgb}
     * (the top block's map colour) mixed in by {@link #GROUND_MIX}. Alpha 0 below {@link #FLAT_FROM}.
     */
    public static int lookArgb(int value, Surface surface, int groundRgb) {
        if (surface == null || surface == Surface.NONE || value < FLAT_FROM)
            return 0;
        float v = Math.min(255, value);
        // how far along the track is: 0 at the first trace, 1 at full wear
        float k = smooth(FLAT_FROM, 255, v);
        int r, g, b;
        float a;
        switch (surface) {
            case GRASS -> {
                float t = smooth(110, WORN_FROM, v);   // flattened -> worn
                r = mix(FLAT_R, WORN_R, t);
                g = mix(FLAT_G, WORN_G, t);
                b = mix(FLAT_B, WORN_B, t);
                // flattened grass reaches its full lift by ~110 (a squad's pass), then the soil takes over
                a = FLAT_ALPHA * smooth(FLAT_FROM, 110, v) * (1 - t) + WORN_ALPHA * t * k;
            }
            case SOIL -> {
                r = SOIL_R; g = SOIL_G; b = SOIL_B;
                a = SOIL_ALPHA * k;
            }
            case SAND -> {
                r = SAND_R; g = SAND_G; b = SAND_B;
                a = SAND_ALPHA * k;
            }
            default -> {
                r = SNOW_R; g = SNOW_G; b = SNOW_B;
                a = SNOW_ALPHA * k;
            }
        }
        r = mix(r, (groundRgb >> 16) & 255, GROUND_MIX);
        g = mix(g, (groundRgb >> 8) & 255, GROUND_MIX);
        b = mix(b, groundRgb & 255, GROUND_MIX);
        int ai = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (ai << 24) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ the client's rebuild throttle

    /** A region whose only change is trample is rebuilt at most this often (a marching army changes it every pass). */
    public static final long TRAMPLE_REBUILD_MS = 1500;

    /**
     * Whether a dirty region may be rebuilt now: a scorch or crater change ({@code hard}) at once; a trample-only change
     * once {@link #TRAMPLE_REBUILD_MS} have passed since the region's last build.
     */
    public static boolean rebuildDue(boolean hard, long nowMs, long lastBuiltMs) {
        return hard || nowMs - lastBuiltMs >= TRAMPLE_REBUILD_MS;
    }
}
