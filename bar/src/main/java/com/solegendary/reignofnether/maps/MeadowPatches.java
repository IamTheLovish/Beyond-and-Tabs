package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.startpos.StartAreaClearing;

import java.util.ArrayList;
import java.util.List;

/**
 * The pre-made maps' plant cover as soft meadow patches (lovish, Nov 4 2026, concept C "clover + grass patches": "a
 * low thick clover carpet everywhere ... with soft patches of taller meadow grass and daisies; open clover keeps
 * soldiers readable", and "we need things to have more blending"). The clover is the grass block's own texture; this
 * class only says what grows on top of it, as indexes into MapBuilder.PLANTS, and touches no block - so plain JUnit
 * runs it (MeadowPatchesTest) and tools/meadow_patches.py mirrors it bit for bit for the offline sampler and map.
 *
 * <p>A smooth patch field (three octaves of value noise on lattices turned 22.5 and 67.5 degrees, so the RTS camera
 * shows no diagonal streaks - the r35 bug hunt) is above {@link #T} on ~30% of open ground, in patches ~3-8 blocks
 * across. Inside a patch: ~90% of blocks carry a plant, half of them tall grass, the rest the big short-grass tufts
 * (and ferns where the map has them). Its edge is soft: across ~2 blocks the cover thins from 90% to 25% and the
 * tall grass gives way to short tufts, then ~1.5 blocks of fringe carry scattered single tufts, then open clover.
 * Flowers cluster in drifts inside the patches and thickest at their edges (white daisies most, yellow dandelions
 * next, a few others), with the odd one out on the clover.
 *
 * <p>Everything is a function of the folded slot-frame position (u, v) and the map's seed (through
 * {@link MapLayout#decorHash}), so every wedge grows the same cover and each seed its own. Readability: tall grass
 * keeps {@link #TALL_CLEAR} blocks off start areas, the start-to-centre lanes StartAreaClearing cuts, capture sites,
 * metal patches, vents and pillars ({@link #clearance}); the plants' own keep-out squares stay bare
 * ({@link #keptClear}).
 */
public final class MeadowPatches {
    private MeadowPatches() { }

    /** Indexes into MapBuilder.PLANTS (its order). */
    public static final int NONE = -1, GRASS = 0, TALL = 1, FERN = 2, LARGE_FERN = 3, POPPY = 4, DANDELION = 5,
        DAISY = 6, BLUET = 7, CORNFLOWER = 8, ALLIUM = 9;

    /** cos / sin of 22.5 and 67.5 degrees, as literals so the Python mirror multiplies by the same doubles. */
    static final double C22 = 0.9238795325112867, S22 = 0.3826834323650898;
    static final double C67 = 0.3826834323650898, S67 = 0.9238795325112867;

    /** The patch: field above T (its edge's middle) - 31% of open ground on Verdant Valley, seeds 0 and 42. */
    static final double T = 0.60;
    /** Half the soft edge in field units: the field climbs ~0.054 a block there, so the edge is ~2.2 blocks wide. */
    static final double E = 0.06;
    /** The fringe of single tufts outside the edge, in field units (~1.5 blocks). */
    static final double H = 0.08;
    /** Share of blocks with a plant: the patch's core, the edge's outer end (rising to the core's), the fringe's inner end. */
    static final double CORE_DENSITY = 0.9, EDGE_DENSITY = 0.25, FRINGE_DENSITY = 0.22;
    /** Tall grass grows from this far into the edge (strength) and reaches its full share TALL_SPAN further in. */
    static final double TALL_FROM = 0.3, TALL_SPAN = 0.6;
    /** Tall grass stays TALL_CLEAR blocks off the readability zones and reaches its full share TALL_RAMP further out. */
    static final double TALL_CLEAR = 2.0, TALL_RAMP = 2.0;
    /** A flower's chance, times the mix's: in a patch's core, on its edge (thickest there), the fringe, open clover. */
    static final double FLOWER_CORE = 1.0, FLOWER_EDGE = 1.4, FLOWER_FRINGE = 0.45, FLOWER_OPEN = 0.04;
    /** Flower drifts: where the drift noise is above CLUSTER_FROM, full chance; elsewhere CLUSTER_LOW of it. */
    static final double CLUSTER_FROM = 0.55, CLUSTER_LOW = 0.3;

    /**
     * One map's mix: {@code tall} the share of a patch core's plants that are tall grass, {@code fern} the share
     * that are ferns (at 0.2 and up a quarter of the tall ones are large ferns), {@code flower} the flower chance,
     * {@code flowers} the table a flower is drawn from (repeats = weight).
     */
    public record Mix(double tall, double fern, double flower, int[] flowers) { }

    private static int[] table(int... kindAndCount) {
        int n = 0;
        for (int i = 1; i < kindAndCount.length; i += 2)
            n += kindAndCount[i];
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < kindAndCount.length; i += 2)
            for (int c = 0; c < kindAndCount[i + 1]; c++)
                out[k++] = kindAndCount[i];
        return out;
    }

    /**
     * Daisies most, dandelions next, a few others (concept C); Highlands keeps its ferns and its blue and purple
     * flowers among them, the desert oases lean yellow. Nether and End maps grow their own groves: null.
     */
    public static Mix mixFor(MapPreset preset) {
        return switch (preset) {
            case VERDANT_VALLEY -> new Mix(0.55, 0.04, 0.10,
                table(DAISY, 10, DANDELION, 6, BLUET, 2, POPPY, 1, CORNFLOWER, 1));
            case HIGHLANDS -> new Mix(0.35, 0.30, 0.07,
                table(DAISY, 8, DANDELION, 5, CORNFLOWER, 3, BLUET, 2, ALLIUM, 2));
            case ARCHIPELAGO -> new Mix(0.45, 0.05, 0.08, table(DAISY, 9, DANDELION, 7, POPPY, 2, BLUET, 2));
            case DESERT_MESA -> new Mix(0.45, 0.10, 0.06, table(DANDELION, 6, DAISY, 4));   // the oases
            default -> null;
        };
    }

    // ------------------------------------------------------------------------------------------ the fields

    /** Smooth value noise in [0, 1] at lattice coordinates (x, y), the lattice from decorHash shifted by salt. */
    static double noise(MapLayout l, double x, double y, double salt) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = l.decorHash(x0 + salt, y0), b = l.decorHash(x0 + 1 + salt, y0);
        double c = l.decorHash(x0 + salt, y0 + 1), d = l.decorHash(x0 + 1 + salt, y0 + 1);
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    /**
     * The patch field in [0, 1]: a 7-block octave (the patches), a 2.5-block one (their ragged edges) and a little of
     * a 20-block one (some stretches patchier than others), the first two on turned lattices. Weighted 0.8 / 0.12 /
     * 0.08: a heavier ragged octave (0.25 at 5 / 2 blocks, the first try) broke the patches into rings and strings
     * 3 blocks wide; these are 5 wide at the median (MeadowPatchesTest).
     */
    static double field(MapLayout l, double u, double v) {
        double a = noise(l, (u * C22 - v * S22) / 7.0, (u * S22 + v * C22) / 7.0, 1100);
        double b = noise(l, (u * C67 - v * S67) / 2.5, (u * S67 + v * C67) / 2.5, 1300);
        double c = noise(l, u / 20.0, v / 20.0, 1500);
        return 0.8 * a + 0.12 * b + 0.08 * c;
    }

    /** The flower drifts' noise (3-block cells). */
    static double cluster(MapLayout l, double u, double v) {
        return noise(l, (u * C67 - v * S67) / 3.0, (u * S67 + v * C67) / 3.0, 1700);
    }

    /** How far into a patch: 1 in its core, 0..1 across its soft edge, 0 outside. */
    static double strength(double p) {
        if (p >= T + E)
            return 1;
        if (p > T - E)
            return (p - (T - E)) / (2 * E);
        return 0;
    }

    /** The fringe outside a patch's edge: 1 at the edge, falling to 0 at its outer end; 0 elsewhere. */
    static double fringe(double p) {
        if (p > T - E || p <= T - E - H)
            return 0;
        return (p - (T - E - H)) / H;
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    // ------------------------------------------------------------------------------------------ the plant

    /**
     * The plant from the patch field {@code p}, the flower drift {@code cl}, four independent draws in [0, 1) and the
     * clearance (blocks to the nearest readability zone; +infinity for none): an index into MapBuilder.PLANTS or NONE.
     */
    static int kindFor(Mix mix, double p, double cl, double pick, double h, double fp, double fk, double clear) {
        double s = strength(p);
        double fr = fringe(p);
        double density;
        if (s >= 1)
            density = CORE_DENSITY;
        else if (s > 0)
            density = EDGE_DENSITY + (CORE_DENSITY - EDGE_DENSITY) * s;
        else
            density = FRINGE_DENSITY * fr;
        int kind = NONE;
        if (pick < density) {
            double tall = mix.tall() * clamp01((s - TALL_FROM) / TALL_SPAN) * clamp01((clear - TALL_CLEAR) / TALL_RAMP);
            double fern = s > 0 ? mix.fern() : 0.0;
            if (h < tall)
                kind = mix.fern() >= 0.2 && h < tall * 0.25 ? LARGE_FERN : TALL;
            else if (h < tall + fern)
                kind = FERN;
            else
                kind = GRASS;
        }
        double w = s >= 1 ? FLOWER_CORE : s > 0 ? FLOWER_EDGE : fr > 0 ? FLOWER_FRINGE : FLOWER_OPEN;
        if (fp < mix.flower() * w * (cl > CLUSTER_FROM ? 1.0 : CLUSTER_LOW))
            return mix.flowers()[(int) (fk * mix.flowers().length) % mix.flowers().length];
        return kind;
    }

    /** What grows at slot-frame (u, v) with this clearance: an index into MapBuilder.PLANTS, or NONE. */
    public static int kindAt(Mix mix, MapLayout l, double u, double v, double clear) {
        return kindFor(mix, field(l, u, v), cluster(l, u, v), l.decorHash(u + 311, v), l.decorHash(u + 733, v),
            l.decorHash(u + 977, v), l.decorHash(u + 1201, v), clear);
    }

    // ------------------------------------------------------------------------------------------ readability

    /**
     * The squares no plant grows in, {dx, dz, half-size} from the arena centre: patch pads and extractor squares,
     * capture sites, vents and pillars - stamped after the plants, their surroundings stay clean.
     */
    public static List<int[]> keepOuts(MapLayout l) {
        List<int[]> out = new ArrayList<>();
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
    public static boolean keptClear(List<int[]> keepOuts, int dx, int dz) {
        for (int[] k : keepOuts)
            if (Math.abs(dx - k[0]) <= k[2] && Math.abs(dz - k[1]) <= k[2])
                return true;
        return false;
    }

    /**
     * Blocks from (dx, dz) - slot frame (u, v) - to the nearest ground units must read clearly on: a start area
     * (StartAreaClearing's disc), its lane to the centre, a keep-out square (Chebyshev). Zero or less is inside one.
     */
    public static double clearance(MapLayout l, double u, double v, int dx, int dz, List<int[]> keepOuts) {
        double start = Math.hypot(u - l.ring, v) - StartAreaClearing.BASE_RADIUS;
        double lane = (u < 0 ? Math.hypot(u, v) : u > l.ring ? Math.hypot(u - l.ring, v) : Math.abs(v))
            - StartAreaClearing.LANE_WIDTH / 2.0;
        double c = Math.min(start, lane);
        for (int[] k : keepOuts)
            c = Math.min(c, Math.max(Math.abs(dx - k[0]), Math.abs(dz - k[1])) - k[2]);
        return c;
    }
}
