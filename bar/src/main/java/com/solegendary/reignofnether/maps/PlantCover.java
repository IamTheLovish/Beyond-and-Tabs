package com.solegendary.reignofnether.maps;

/**
 * Where the pre-made maps grow grass, ferns and flowers - the arithmetic of MapBuilder's plant cover, pulled out of
 * MapBuilder so a plain JUnit test can run it (MapBuilder's block tables need Minecraft's registries; this class
 * answers in indexes into MapBuilder.PLANTS and touches no block). Everything is a function of the folded slot-frame
 * position (u, v) and the slope, so the cover is n-fold symmetric like the rest of the map.
 */
final class PlantCover {
    private PlantCover() { }

    /** Indexes into MapBuilder.PLANTS. */
    static final int GRASS = 0, TALL = 1, FERN = 2, LARGE_FERN = 3, FLOWER0 = 4;
    static final int KINDS = 10;

    /**
     * One map's mix. {@code grass} sets how thick a meadow grows (r35: its weight against CLUMP_GRASS), {@code tall} and
     * {@code fern} the shares of those plants that are two-block grass and ferns (middles only), {@code flower} the
     * share of a flower drift's ground, {@code flowers} the PLANTS indexes the drifts draw from. {@code lo} and
     * {@code span} set where a meadow starts (noise) and how fast it thickens: a lower start is a wider meadow.
     * {@code scatter} is the share of open, flat ground that gets a single tuft of short grass (0: none, the oases).
     */
    record Mix(double grass, double tall, double fern, double flower, int[] flowers, double lo, double span,
               double scatter) { }

    /**
     * Oct 28 (lovish, "more grass"): the meadows start at noise 0.18 instead of 0.30 (a wider meadow), Verdant's grass
     * share rises from 0.58 to 0.68 and the other two's from 0.42 / 0.40 to 0.44, and open ground carries a 10% scatter
     * of tufts. About 1.75-1.8x the plant blocks of Oct 16's cover on all three green maps (the share of grass-topped
     * columns that carry one: Verdant 0.37 to 0.64, Highlands 0.26 to 0.45, Archipelago 0.25 to 0.45); two-block
     * plants stay in the middles, about as many as before.
     */
    static Mix mixFor(MapPreset preset) {
        int[] meadow = { 4, 5, 6, 7, 8 };
        return switch (preset) {
            case VERDANT_VALLEY -> new Mix(0.68, 0.065, 0.04, 0.05, meadow, 0.18, 0.40, 0.10);
            case HIGHLANDS -> new Mix(0.44, 0.05, 0.22, 0.02, new int[] { 8, 7, 9 }, 0.18, 0.40, 0.10);
            case ARCHIPELAGO -> new Mix(0.44, 0.05, 0.04, 0.03, new int[] { 5, 4, 6 }, 0.18, 0.40, 0.10);
            case DESERT_MESA -> new Mix(0.45, 0.08, 0.08, 0.01, new int[] { 5 }, 0.30, 0.45, 0);   // the oases, as they were
            default -> null;   // nether, end: their own groves, no meadow
        };
    }

    /** Noise above which a meadow is "middle": two-block grass and large ferns grow only here. */
    static final double CORE = 0.68;
    /** Noise above which a fern may grow (the inner meadow). */
    static final double FERN_FROM = 0.55;

    /**
     * The squares no plant grows in, {dx, dz, half-size} from the arena centre: patch pads and extractor squares,
     * capture sites, vents and pillars - their surroundings are stamped after the plants and stay clean.
     */
    static java.util.List<int[]> keepOuts(MapLayout l) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (MapLayout.Mex m : l.mexes)
            out.add(new int[] { m.dx(), m.dz(), 4 });
        for (MapLayout.Site st : l.sites)
            out.add(new int[] { st.dx(), st.dz(), 6 });
        for (MapLayout.Spot sp : l.vents)
            out.add(new int[] { sp.dx(), sp.dz(), 3 });
        for (MapLayout.Spot sp : l.pillars)
            out.add(new int[] { sp.dx(), sp.dz(), 3 });
        return out;
    }

    /** Whether (dx, dz) from the arena centre lies in one of the keep-out squares. */
    static boolean keptClear(java.util.List<int[]> keepOuts, int dx, int dz) {
        for (int[] k : keepOuts)
            if (Math.abs(dx - k[0]) <= k[2] && Math.abs(dz - k[1]) <= k[2])
                return true;
        return false;
    }

    /** A smooth value noise in [0, 1] over slot frame (lattice from decorHash, smoothstep-blended). */
    static double plantNoise(MapLayout l, double u, double v, double scale, double salt) {
        double x = u / scale, y = v / scale;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = l.decorHash(x0 + salt, y0), b = l.decorHash(x0 + 1 + salt, y0);
        double c = l.decorHash(x0 + salt, y0 + 1), d = l.decorHash(x0 + 1 + salt, y0 + 1);
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    /**
     * The noise octaves' lattice turns, {degrees, shift u, shift v} - the meadow sampler's r35 TURNS
     * (tools/gen_meadow_sampler.py, bug hunt r34). A square value-noise lattice puts ~2x more gradient energy along its
     * axes than along its diagonals (the sampler's clump field: 0.333 axis / 0.166 diagonal), which the 45-degree RTS
     * camera shows as diagonal streaks of grass. Turned 22.5 and 67.5 degrees every lattice axis sits 22.5 degrees from
     * both the world axes and the diagonals (0.228 / 0.219). The shifts are in blocks, added after the turn and before
     * the scale exactly as the sampler does, and differ per octave so no two octaves share a lattice corner.
     */
    static final double[] TURN_M11 = { 22.5, 0.0, 0.0 }, TURN_M4 = { 67.5, 7.3, 3.1 },
        TURN_C5 = { 22.5, 1.7, 9.2 }, TURN_C2 = { 67.5, 4.4, 0.6 };

    /** {@link #plantNoise} on a lattice turned and shifted by {@code turn} ({degrees, shift u, shift v}). */
    static double plantNoise(MapLayout l, double u, double v, double scale, double salt, double[] turn) {
        double a = Math.toRadians(turn[0]), ca = Math.cos(a), sa = Math.sin(a);
        return plantNoise(l, u * ca - v * sa + turn[1], u * sa + v * ca + turn[2], scale, salt);
    }

    /** How much of a meadow the position is in, [0, 1]: the smooth noise, two octaves, on turned lattices. */
    static double meadowNoise(MapLayout l, double u, double v) {
        return 0.65 * plantNoise(l, u, v, 11.0, 0, TURN_M11) + 0.35 * plantNoise(l, u, v, 4.0, 200, TURN_M4);
    }

    /**
     * Where the tufts clump, [0, 1]: a 5-block octave and a 2-block one (the sampler's clump field, r31-r35), on turned
     * lattices. Salts 1000 / 1500 keep its lattice values apart from the meadow's (0 / 200) and the flowers' (500).
     */
    static double clumpNoise(MapLayout l, double u, double v) {
        return 0.75 * plantNoise(l, u, v, 5.0, 1000, TURN_C5) + 0.25 * plantNoise(l, u, v, 2.0, 1500, TURN_C2);
    }

    /**
     * The meadow sampler's r35 clump rule (critic r34, tufts 5/10: "sparse, bare lawn between clumps... double tuft
     * density"; r31 before it: "wallpaper scatter", every tuft its own dark shadow star). A block is in a clump where
     * the clump noise is over CLUMP_AT - CLUMP_LUSH x the meadow's lushness (bigger clumps where it is lush); a clump
     * fills to CLUMP_FILL x min(1, CLUMP_BASE + lushness), and the lawn between clumps carries a BETWEEN scatter of
     * single tufts, so it is never bare. On the sampler's patch: tuft cover 39% -> 72% of the open lawn, about a quarter
     * left in clearings; on Verdant's meadows (Oct 28's #316 cover) 0.64 -> 0.74, PlantCoverTest.
     */
    static final double CLUMP_AT = 0.50, CLUMP_LUSH = 0.26, CLUMP_FILL = 0.97, CLUMP_BASE = 0.6, BETWEEN = 0.20;
    /**
     * The grass share at which a meadow's middle carries the full r35 clumps (Verdant's, the map the sampler stands
     * for); a map with a thinner grass share (Highlands, Archipelago 0.44) gets them in proportion, as it did before.
     */
    static final double CLUMP_GRASS = 0.68;

    /**
     * What grows at (u, v) in slot frame on ground with this slope: an index into MapBuilder.PLANTS, or -1 for bare
     * grass. Inside a meadow the tufts grow in the r35 clumps (see CLUMP_AT): their weight rises with the meadow
     * (lushness x (2 - lushness), the Oct 16 onset, scaled by the map's grass share) so a meadow still fades into the
     * open; middles carry two-block grass and large ferns (inside a clump only, as in the sampler), ferns the inner
     * meadow, flowers their own drifts. Outside it, flat ground gets the low scatter of short grass - thicker round a
     * meadow's edge, half as thick on a gentle slope, none on a steeper one (which the builder does not plant anyway).
     */
    static int kindAt(Mix mix, MapLayout l, double u, double v, int slope) {
        double meadow = meadowNoise(l, u, v);
        double dense = Math.max(0, Math.min(1, (meadow - mix.lo()) / mix.span()));
        double weight = Math.min(1, mix.grass() / CLUMP_GRASS) * dense * (2 - dense);
        boolean inClump = weight > 0 && clumpNoise(l, u, v) > CLUMP_AT - CLUMP_LUSH * dense;
        double tuft = inClump ? CLUMP_FILL * Math.min(1, CLUMP_BASE + dense) : BETWEEN;
        double inMeadow = Math.min(1 - mix.flower(), weight * tuft);
        double onSlope = slope <= 0 ? 1 : slope == 1 ? 0.5 : 0;
        double edge = Math.max(0, Math.min(1, meadow / 0.35));
        double scatter = mix.scatter() * onSlope * (1 + 0.5 * edge);
        double pick = l.decorHash(u + 311, v);
        if (pick < inMeadow) {
            double h = l.decorHash(u + 733, v);
            if (meadow > CORE && inClump && h < mix.tall())
                return h < mix.tall() * 0.25 && mix.fern() > 0.1 ? LARGE_FERN : TALL;
            return meadow > FERN_FROM && h >= mix.tall() && h < mix.tall() + mix.fern() ? FERN : GRASS;
        }
        // the open ground's scatter gives way to the meadow's own as the meadow thickens (r35's BETWEEN is the whole
        // scatter between a lush meadow's clumps, not one more on top of it); weight 0 = the open ground, as before
        if (pick < Math.min(1 - mix.flower(), inMeadow + (1 - weight) * scatter))
            return GRASS;
        if (mix.flower() > 0 && plantNoise(l, u, v, 13.0, 500) > 0.7 && pick > 1 - mix.flower())
            return mix.flowers()[(int) (l.decorHash(u + 977, v) * mix.flowers().length) % mix.flowers().length];
        return -1;
    }
}
