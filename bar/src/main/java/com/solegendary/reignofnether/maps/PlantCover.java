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
     * One map's mix. {@code grass}, {@code tall}, {@code fern} and {@code flower} are shares, {@code flowers} the
     * PLANTS indexes the flower drifts draw from.
     */
    record Mix(double grass, double tall, double fern, double flower, int[] flowers) { }

    static Mix mixFor(MapPreset preset) {
        int[] meadow = { 4, 5, 6, 7, 8 };
        return switch (preset) {
            case VERDANT_VALLEY -> new Mix(0.58, 0.08, 0.04, 0.05, meadow);
            case HIGHLANDS -> new Mix(0.42, 0.05, 0.16, 0.02, new int[] { 8, 7, 9 });
            case ARCHIPELAGO -> new Mix(0.40, 0.05, 0.04, 0.03, new int[] { 5, 4, 6 });
            case DESERT_MESA -> new Mix(0.45, 0.08, 0.08, 0.01, new int[] { 5 });   // the oases
            default -> null;   // nether, end: their own groves, no meadow
        };
    }

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

    /** How much of a meadow the position is in, [0, 1]: the smooth noise, two octaves. */
    static double meadowNoise(MapLayout l, double u, double v) {
        return 0.65 * plantNoise(l, u, v, 11.0, 0) + 0.35 * plantNoise(l, u, v, 4.0, 200);
    }

    /**
     * What grows at (u, v) in slot frame on ground with this slope: an index into MapBuilder.PLANTS, or -1 for bare
     * grass.
     */
    static int kindAt(Mix mix, MapLayout l, double u, double v, int slope) {
        double meadow = meadowNoise(l, u, v);
        double dense = Math.max(0, Math.min(1, (meadow - 0.3) / 0.45));
        double pick = l.decorHash(u + 311, v);
        if (pick < mix.grass() * dense * (2 - dense)) {
            double h = l.decorHash(u + 733, v);
            if (meadow > 0.68 && h < mix.tall())
                return h < mix.tall() * 0.25 && mix.fern() > 0.1 ? LARGE_FERN : TALL;
            return h < mix.tall() + mix.fern() ? FERN : GRASS;
        }
        if (mix.flower() > 0 && plantNoise(l, u, v, 13.0, 500) > 0.7 && pick > 1 - mix.flower())
            return mix.flowers()[(int) (l.decorHash(u + 977, v) * mix.flowers().length) % mix.flowers().length];
        return -1;
    }
}
