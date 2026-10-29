package com.solegendary.reignofnether.maps;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps spec M6: where our own tree templates (tools/gen_trees.py, structures/trees/*.nbt) stand on a pre-made map,
 * planned without the world (TreeScatterTest), the way MapLayout plans the terrain. MapBuilder offers every forest
 * column with a slope under 2 and places what comes back.
 *
 * <p><b>Natural, roughly fair</b> (lovish, Oct 29: "every players side doesnt need the same amount of trees that could
 * look weird"; asked how strict, "Natural, roughly fair"). The terrain and the economy stay mirror-fair (MapLayout), but
 * the trees on them are not folded: every choice - where, which species, which form, which turn, mirrored or not -
 * comes from a hash of the column's own map position and the seed, so each side's woods are its own, with groves and
 * clearings in different places. What stays fair is the amount: each side (the wedge round its start) grows trees
 * until its weight - the wood a player can fell for energy plus one per tree for the blocking - reaches its budget, the
 * same for every side but for a +-JITTER nudge from the side's own hash, so a side may come out a little richer or
 * poorer (the tests hold it within 15%).
 *
 * <p>Before, MapBuilder grew vanilla's configured trees at folded hash spots with level.getRandom() and a birch for
 * every third in chunk order - mirrored spots, but trees that differed slot to slot and build to build.
 */
public final class TreeScatter {
    private TreeScatter() { }

    /** A template family: file prefix, forms (_1.._n), wood per tree (felling credit weight), crown radius in blocks. */
    public enum Kind {
        OAK("oak", 4, 4, 7), BIRCH("birch", 4, 3, 5), SPRUCE("spruce", 4, 3, 5), PINE("pine", 4, 3, 5),
        ACACIA("acacia", 4, 2, 6), BUSH("bush", 4, 1, 4), BOULDER("boulder", 4, 0, 4);

        public final String file;
        public final int variants, wood, crown;

        Kind(String file, int variants, int wood, int crown) {
            this.file = file;
            this.variants = variants;
            this.wood = wood;
            this.crown = crown;
        }
    }

    public record Share(Kind kind, double share) { }

    /**
     * One planted template: trunk column (dx, dz) from the map centre, the form 1..variants, quarter turns clockwise
     * from above (0-3), mirrored east-west before turning, and the side (slot index) it counts for.
     */
    public record Tree(int dx, int dz, Kind kind, int variant, int rotation, boolean mirror, int slot) {
        /** The structure name under data/reignofnether/structures/. */
        public String template() {
            return "trees/" + kind.file + "_" + variant;
        }
    }

    /** Trees per offered column, before spacing: MapBuilder.TREE_DENSITY's sparse reclaim lanes you can see through. */
    public static final double DENSITY = 0.045;
    /** Trunk to trunk, blocks: a plain hash threshold clumps trees into one blob of leaves. */
    public static final int SPACING = 5;
    /** A side's budget is the mean times 1 +- this, from the side's own hash: roughly fair, never identical. */
    public static final double JITTER = 0.08;
    /** Trunk distance kept from a metal patch / a capture site / a vent or pillar, plus the crown radius. */
    public static final int MEX_CLEAR = 5, SITE_CLEAR = 6, SPOT_CLEAR = 3;
    /** No tree this close to a start (the forest flag already keeps them off; a second guard). */
    public static final int START_CLEAR = 24;

    /** The species mix per map, or null for no trees (the realm maps grow their own fungi and chorus). */
    public static List<Share> mixFor(MapPreset p) {
        return switch (p) {
            case VERDANT_VALLEY -> List.of(new Share(Kind.OAK, 0.6), new Share(Kind.BIRCH, 0.25), new Share(Kind.BUSH, 0.15));
            case HIGHLANDS -> List.of(new Share(Kind.SPRUCE, 0.5), new Share(Kind.PINE, 0.35), new Share(Kind.BOULDER, 0.15));
            case DESERT_MESA -> List.of(new Share(Kind.ACACIA, 0.7), new Share(Kind.BUSH, 0.3));
            case ARCHIPELAGO -> List.of(new Share(Kind.OAK, 0.7), new Share(Kind.BIRCH, 0.3));
            default -> null;
        };
    }

    /**
     * The trees for this map from the offered columns ({dx, dz} from the centre: forest, slope under 2). Deterministic
     * in the layout's seed; the order of the offered list does not matter.
     */
    public static List<Tree> plan(MapLayout l, List<int[]> offered) {
        List<Share> mix = mixFor(l.preset);
        List<Tree> out = new ArrayList<>();
        if (mix == null || offered.isEmpty())
            return out;
        long seed = MapLayout.mixSeed(l.seed) ^ 0x7EE5CA77E2L;
        double meanWeight = 0;
        int crown = 0;
        for (Share s : mix) {
            meanWeight += s.share() * (s.kind().wood + 1);
            crown = Math.max(crown, s.kind().crown);
        }

        // each offered column: its side, the tree it would grow, and its turn in the queue
        List<List<Cand>> bySide = new ArrayList<>();
        for (int i = 0; i < l.n; i++)
            bySide.add(new ArrayList<>());
        int[] offeredPerSide = new int[l.n];   // before the keep-outs: the same count on every side (folded terrain)
        for (int[] c : offered) {
            int dx = c[0], dz = c[1];
            int side = sideOf(l, dx, dz);   // once per column (review, Oct 29: counting per side redid it n times)
            offeredPerSide[side]++;
            // the widest crown of the mix keeps every species off the economy alike: a per-species keep-out would
            // push the big oaks out of the narrow lanes and tilt the mix toward bushes
            if (!clear(l, dx, dz, crown))
                continue;
            // a smooth noise makes groves and clearings; inside a grove a column comes up sooner
            double grove = noise(seed, dx, dz, 15.0);
            double turn = h(seed, dx, dz, 3) / (0.2 + grove * grove);
            bySide.get(side).add(new Cand(dx, dz, turn));
        }

        // the sides take turns, one tree each a round, so no side gets first pick of the columns on a border
        Map<Long, List<int[]>> grid = new HashMap<>();   // accepted trunks in SPACING cells
        double[] budget = new double[l.n], weight = new double[l.n];
        int[] next = new int[l.n], planted = new int[l.n];
        for (int side = 0; side < l.n; side++) {
            // the side's offered columns before the keep-outs: the same count on every side (the terrain is folded)
            budget[side] = DENSITY * offeredPerSide[side] * meanWeight
                * (1 + JITTER * (2 * h(seed, side, 0, 97) - 1));
            bySide.get(side).sort(Comparator.comparingDouble((Cand c) -> c.turn).thenComparingInt(c -> c.dx)
                .thenComparingInt(c -> c.dz));
        }
        for (boolean any = true; any; ) {
            any = false;
            for (int side = 0; side < l.n; side++) {
                List<Cand> cs = bySide.get(side);
                while (weight[side] < budget[side] && next[side] < cs.size()) {
                    Cand c = cs.get(next[side]++);
                    if (crowded(grid, c.dx, c.dz))
                        continue;
                    grid.computeIfAbsent(cell(c.dx, c.dz), k -> new ArrayList<>()).add(new int[] { c.dx, c.dz });
                    // the species: the side's k-th draw of a golden-ratio sequence from its own start, so every side
                    // grows the mix's shares (and so about the same wood) in its own order
                    Kind kind = pick(mix, frac(h(seed, side, 1, 11) + planted[side]++ * GOLDEN));
                    weight[side] += kind.wood + 1;
                    out.add(new Tree(c.dx, c.dz, kind, 1 + (int) (h(seed, c.dx, c.dz, 23) * kind.variants),
                        (int) (h(seed, c.dx, c.dz, 31) * 4), h(seed, c.dx, c.dz, 41) < 0.5, side));
                    any = true;
                    break;
                }
            }
        }
        out.sort(Comparator.comparingInt(Tree::dx).thenComparingInt(Tree::dz));
        return out;
    }

    private record Cand(int dx, int dz, double turn) { }

    /** The side a column counts for: the start nearest in angle (the wedge round it). */
    static int sideOf(MapLayout l, double dx, double dz) {
        double a = Math.atan2(dz, dx), best = Double.MAX_VALUE;
        int side = 0;
        for (int i = 0; i < l.slots.size(); i++) {
            MapLayout.Slot s = l.slots.get(i);
            double d = Math.abs(Math.IEEEremainder(a - Math.atan2(s.dz(), s.dx()), 2 * Math.PI));
            if (d < best) {
                best = d;
                side = i;
            }
        }
        return side;
    }

    private static boolean clear(MapLayout l, int dx, int dz, int r) {
        for (MapLayout.Mex m : l.mexes)
            if (Math.hypot(dx - m.dx(), dz - m.dz()) < MEX_CLEAR + r)
                return false;
        for (MapLayout.Site s : l.sites)
            if (Math.hypot(dx - s.dx(), dz - s.dz()) < SITE_CLEAR + r)
                return false;
        for (MapLayout.Spot s : l.vents)
            if (Math.hypot(dx - s.dx(), dz - s.dz()) < SPOT_CLEAR + r)
                return false;
        for (MapLayout.Spot s : l.pillars)
            if (Math.hypot(dx - s.dx(), dz - s.dz()) < SPOT_CLEAR + r)
                return false;
        for (MapLayout.Slot s : l.slots)
            if (Math.hypot(dx - s.dx(), dz - s.dz()) < START_CLEAR)
                return false;
        return true;
    }

    private static long cell(int dx, int dz) {
        return ((long) Math.floorDiv(dx, SPACING) << 32) ^ (Math.floorDiv(dz, SPACING) & 0xffffffffL);
    }

    private static boolean crowded(Map<Long, List<int[]>> grid, int dx, int dz) {
        int cx = Math.floorDiv(dx, SPACING), cz = Math.floorDiv(dz, SPACING);
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                List<int[]> in = grid.get(((long) (cx + i) << 32) ^ ((cz + j) & 0xffffffffL));
                if (in == null)
                    continue;
                for (int[] t : in)
                    if (Math.hypot(t[0] - dx, t[1] - dz) < SPACING)
                        return true;
            }
        return false;
    }

    private static final double GOLDEN = 0.6180339887498949;

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static Kind pick(List<Share> mix, double r) {
        double acc = 0;
        for (Share s : mix) {
            acc += s.share();
            if (r < acc)
                return s.kind();
        }
        return mix.get(mix.size() - 1).kind();
    }

    /** A deterministic [0, 1) from a map position (not folded: each side its own) and a salt. */
    static double h(long seed, long x, long z, long salt) {
        long v = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
        v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
        v ^= v >>> 31;
        return (v >>> 11) * 0x1.0p-53;
    }

    /** Smooth value noise in [0, 1] over map position (lattice from h, smoothstep-blended). */
    static double noise(long seed, double x, double z, double scale) {
        double fx0 = x / scale, fz0 = z / scale;
        long x0 = (long) Math.floor(fx0), z0 = (long) Math.floor(fz0);
        double fx = fx0 - x0, fz = fz0 - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = h(seed, x0, z0, 5), b = h(seed, x0 + 1, z0, 5), c = h(seed, x0, z0 + 1, 5), d = h(seed, x0 + 1, z0 + 1, 5);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    // ---- turning a template ----

    /**
     * Where template block p = {x, y, z} lands, as {dx, dy, dz} from the trunk column, for a template sizeX by sizeZ
     * whose trunk is its centre (sizeX / 2, sizeZ / 2): mirrored east-west first if asked (Minecraft's
     * Mirror.FRONT_BACK), then turned the given quarter turns clockwise from above (Rotation.CLOCKWISE_90 takes east to
     * south), the order StructureTemplate applies them. Block states get the same mirror and rotate.
     */
    public static int[] offset(int[] p, int sizeX, int sizeZ, int rotation, boolean mirror) {
        int x = p[0] - sizeX / 2, z = p[2] - sizeZ / 2;
        if (mirror)
            x = -x;
        for (int i = 0; i < Math.floorMod(rotation, 4); i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new int[] { x, p[1], z };
    }
}
