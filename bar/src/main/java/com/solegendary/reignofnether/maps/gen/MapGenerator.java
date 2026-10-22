package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MetalOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * The automatic map generator (lovish Oct 21 22:28: "a way to automatically generate maps that are intricate and
 * sometimes changes rng with different buffs and bonuses. The random map can give one side more neutral objectives with
 * the other side giving more mexes"). Deterministic: the same seats, seed and options always draw the same map, so the
 * lobby's preview is the map that is built.
 * <p>
 * One build: plan (GenPlan: arena, seats, characters, roads, river, modifiers) -> ground (GenTerrain) -> economy and
 * objectives (GenEconomy) -> checks on the final walkable ground (every seat reaches every seat and the middle, every
 * patch and site is reachable; what is not is dropped) -> the seat fairness score (Fairness). The balancer then evens
 * the seats out (GenOptions.Balance; design/maps.md "Generated maps" has the comparison):
 * <ul>
 * <li>REROLL: draw again from a derived seed until the spread is within tolerance (up to 12 tries, else the best);</li>
 * <li>SWAP: the same landscape with the seats turned round the ring and the sides' characters swapped: the best of up to
 *     eight;</li>
 * <li>NUDGE: small metal edits (a standard patch made rich on the poorest seat, a crowding patch removed from the
 *     richest...) while they narrow the spread;</li>
 * <li>SWAP_NUDGE / REROLL_NUDGE: the two in turn.</li>
 * </ul>
 */
public final class MapGenerator {
    private MapGenerator() { }

    static final int REROLLS = 12, NUDGE_STEPS = 48;
    /** Fresh draws tried when a balanced draw is invalid or outside the tolerance (see generate), numbered from 100. */
    static final int FALLBACK_DRAWS = 4, FALLBACK_BASE = 100;

    /** The map for these seats (teams per slot, teammates adjacent) with the default weights. */
    public static GenMap generate(int[] slotTeams, long seed, GenOptions o, MetalOptions metal) {
        return generate(slotTeams, seed, o, metal, Fairness.Weights.CENTRAL);
    }

    public static GenMap generate(int[] slotTeams, long seed, GenOptions o, MetalOptions metal, Fairness.Weights w) {
        int[] teams = slotTeams.length >= 2 ? slotTeams.clone() : new int[] { 0, 1 };
        if (o == null)
            o = GenOptions.DEFAULT;
        if (metal == null)
            metal = MetalOptions.DEFAULT;
        double tol = o.tolerance();
        Built best = balanced(teams, seed, seed, o, metal, w, tol);
        // the promise to the lobby: a valid map within the tolerance. A draw the balancer cannot even out (or that cut a
        // seat off) gives way to a fresh draw of the same seed, balanced the same way, up to FALLBACK_DRAWS times; the
        // best one stands if none makes it (a NONE balance is an experiment's raw draw and is never replaced)
        for (int k = 1; o.balance() != GenOptions.Balance.NONE && k <= FALLBACK_DRAWS
                && (!best.m.valid || best.m.report.spread() > tol); k++) {
            Built b = balanced(teams, seed, drawSeed(seed, FALLBACK_BASE + k), o, metal, w, tol);
            b.m.attempt = FALLBACK_BASE + k;
            if (better(b, best))
                best = b;
        }
        return best.m;
    }

    /** One draw ({@code draw}) of the seed's map, balanced by the options' strategy. */
    private static Built balanced(int[] teams, long seed, long draw, GenOptions o, MetalOptions metal, Fairness.Weights w,
                                  double tol) {
        Built best;
        switch (o.balance()) {
            case NONE -> best = build(new GenPlan(teams, seed, draw, o, false, 0), metal, w);
            case REROLL -> best = reroll(teams, seed, draw, o, metal, w, REROLLS, tol);
            case NUDGE -> {
                best = build(new GenPlan(teams, seed, draw, o, false, 0), metal, w);
                nudge(best, tol, w);
            }
            case SWAP -> best = swap(teams, seed, draw, o, metal, w);
            case SWAP_NUDGE -> {
                best = swap(teams, seed, draw, o, metal, w);
                nudge(best, tol, w);
            }
            case REROLL_NUDGE -> {
                best = reroll(teams, seed, draw, o, metal, w, 4, tol);
                nudge(best, tol, w);
            }
            default -> throw new IllegalStateException();
        }
        return best;
    }

    /** One finished build and what its balancing needs. */
    static final class Built {
        GenPlan p;
        GenMap m;
        GenTerrain t;
        GenEconomy e;
        WalkGrid g;
        int[][] dist;
    }

    static long drawSeed(long seed, int attempt) {
        return attempt == 0 ? seed : GenRng.mix(seed + attempt * 0x9E3779B97F4A7C15L);
    }

    private static Built reroll(int[] teams, long seed, long draw, GenOptions o, MetalOptions metal, Fairness.Weights w,
                                int tries, double tol) {
        Built best = null;
        for (int k = 0; k < tries; k++) {
            Built b = build(new GenPlan(teams, seed, drawSeed(draw, k), o, false, 0), metal, w);
            b.m.attempt = k;
            if (best == null || better(b, best))
                best = b;
            if (b.m.valid && b.m.report.spread() <= tol)
                return b;
        }
        return best;
    }

    /** Valid beats invalid; then the smaller spread. */
    static boolean better(Built a, Built b) {
        if (a.m.valid != b.m.valid)
            return a.m.valid;
        return a.m.report.spread() < b.m.report.spread();
    }

    /**
     * The same landscape (the draw's noise) with the seats turned to up to four places round the ring and, for a
     * two-sided contrast, the sides' characters swapped: the most even of up to eight builds.
     */
    private static Built swap(int[] teams, long seed, long draw, GenOptions o, MetalOptions metal, Fairness.Weights w) {
        int n = teams.length;
        GenPlan first = new GenPlan(teams, seed, draw, o, false, 0);
        boolean twoSided = first.contrast != GenOptions.Contrast.MIRROR && first.contrast != GenOptions.Contrast.MIXED;
        List<Integer> rots = new ArrayList<>();
        int step = Math.max(1, n / 4);
        for (int r = 0; r < n && rots.size() < 4; r += step)
            rots.add(r);
        Built best = null;
        int option = 0;
        for (int f = 0; f < (twoSided ? 2 : 1); f++)
            for (int r : rots) {
                GenPlan pl = f == 0 && r == 0 ? first : new GenPlan(teams, seed, draw, o, f == 1, r);
                Built b = build(pl, metal, w);
                b.m.swapOption = option++;
                if (best == null || better(b, best))
                    best = b;
            }
        return best;
    }

    static Built build(GenPlan p, MetalOptions metal, Fairness.Weights w) {
        Built b = new Built();
        b.p = p;
        GenMap m = new GenMap(p.seed, p.teams, p.shape, p.ring, p.half, p.rot);
        b.m = m;
        for (int i = 0; i < p.n; i++)
            m.slots.add(new MapLayout.Slot(i, p.teams[i], (int) p.slotX(i), (int) p.slotZ(i), p.rot + 2 * Math.PI * i / p.n));
        m.seats = p.seat.clone();
        m.contrast = p.contrast;
        m.modifiers.addAll(p.modifiers);
        for (GenPlan.Road r : p.roads) {
            int[] xs = new int[r.xs.length], zs = new int[r.zs.length];
            for (int k = 0; k < xs.length; k++) {
                xs[k] = (int) Math.round(r.xs[k]);
                zs[k] = (int) Math.round(r.zs[k]);
            }
            m.lanes.add(new GenMap.Lane(r.seat, r.main, xs, zs));
        }
        GenTerrain t = new GenTerrain(p, m);
        t.build();
        b.t = t;
        GenEconomy e = new GenEconomy(p, m, t, metal);
        e.build();
        b.e = e;
        // the checks run on the final ground, trees included
        WalkGrid fin = new WalkGrid(m, true);
        b.g = fin;
        check(b);
        b.dist = Fairness.fields(m, fin);
        e.writeWith(fin, b.dist);
        m.report = Fairness.score(m, fin, b.dist, w);
        return b;
    }

    /**
     * Every seat must reach every other seat and the plaza by ground; a patch, site, vent or camp the ground does not
     * reach is dropped (with its mirror twin), never left as a trap.
     */
    static void check(Built b) {
        GenMap m = b.m;
        MapLayout.Slot s0 = m.slots.get(0);
        boolean[] seen = b.g.flood(s0.dx(), s0.dz());
        for (MapLayout.Slot s : m.slots)
            if (!seen[m.idx(s.dx(), s.dz())]) {
                m.valid = false;
                m.invalidWhy = "seat " + s.index() + " is cut off";
            }
        boolean mid = false;
        for (int x = -6; x <= 6 && !mid; x++)
            for (int z = -6; z <= 6; z++)
                if (seen[m.idx(x, z)]) {
                    mid = true;
                    break;
                }
        if (!mid) {
            m.valid = false;
            m.invalidWhy = "the middle is cut off";
        }
        for (GenEconomy.Patch q : b.e.patches)
            if (q.alive && !reachedNear(m, seen, q.x, q.z, 1)) {
                q.alive = false;
                if (q.twin >= 0)
                    b.e.patches.get(q.twin).alive = false;
            }
        m.sites.removeIf(st -> !reachedNear(m, seen, st.dx(), st.dz(), 4));
        m.vents.removeIf(v -> !reachedNear(m, seen, v.dx(), v.dz(), 3));
        m.camps.removeIf(c -> !reachedNear(m, seen, c.dx(), c.dz(), 4));
    }

    static boolean reachedNear(GenMap m, boolean[] seen, int x, int z, int r) {
        for (int a = -r; a <= r; a++)
            for (int c = -r; c <= r; c++)
                if (m.inGrid(x + a, z + c) && seen[m.idx(x + a, z + c)])
                    return true;
        return false;
    }

    // ------------------------------------------------------------------------------------------------- nudge

    /**
     * Small metal edits while they narrow the seats' spread, down to 80% of the tolerance (a margin, since the score
     * is a model): the poorest seat gets a standard patch made rich, a lean one made standard or a new patch on free
     * ground; the richest loses a rich patch's richness, a far patch drops to lean, or a crowding fill patch goes. Each
     * step takes the edit that leaves the smallest spread; it stops when none helps.
     */
    static void nudge(Built b, double tol, Fairness.Weights w) {
        GenMap m = b.m;
        if (b.p.mirror || !m.valid)
            return;
        GenEconomy e = b.e;
        Fairness.Context ctx = new Fairness.Context(new Fairness.Geometry(m, b.g, b.dist), w);
        List<GenEconomy.Patch> live = new ArrayList<>();
        List<double[]> rows = new ArrayList<>();
        for (GenEconomy.Patch q : e.patches)
            if (q.alive) {
                double[] r = ctx.patch(q.x, q.z, q.value, owner(ctx, q.x, q.z));
                if ((int) r[3] < 0)
                    continue;
                live.add(q);
                rows.add(r);
            }
        Fairness.Report rep = ctx.report(rows);
        for (int step = 0; step < NUDGE_STEPS && rep.spread() > tol * 0.8; step++) {
            int poor = 0, rich = 0;
            for (int s = 1; s < m.n; s++) {
                if (rep.total(s) < rep.total(poor))
                    poor = s;
                if (rep.total(s) > rep.total(rich))
                    rich = s;
            }
            List<Runnable[]> edits = new ArrayList<>();   // {apply, undo}
            List<double[]> addRows = new ArrayList<>();
            for (int i = 0; i < live.size(); i++) {
                GenEconomy.Patch q = live.get(i);
                double[] r = rows.get(i);
                int o = (int) r[3];
                boolean fill = q.kind == GenEconomy.PKind.FILL || q.kind == GenEconomy.PKind.NATURAL
                    || q.kind == GenEconomy.PKind.NUDGE;
                if (o == poor && fill && q.value == GenEconomy.STANDARD && exposureOf(ctx, r, o) < 0.5)
                    edits.add(valueEdit(r, GenEconomy.RICH));
                if (o == poor && q.value == GenEconomy.LEAN)
                    edits.add(valueEdit(r, GenEconomy.STANDARD));
                if (o == rich && (fill || q.kind == GenEconomy.PKind.VEIN) && q.value == GenEconomy.RICH)
                    edits.add(valueEdit(r, GenEconomy.STANDARD));
                if (o == rich && fill && q.value == GenEconomy.STANDARD && exposureOf(ctx, r, o) < 0.3)
                    edits.add(valueEdit(r, GenEconomy.LEAN));
                if (o == rich && (q.kind == GenEconomy.PKind.FILL || q.kind == GenEconomy.PKind.NUDGE)) {
                    final int at = i;
                    edits.add(new Runnable[] { () -> r[2] = 0, () -> r[2] = live.get(at).value });
                }
            }
            // a new patch on the poorest seat's free ground: the candidates it owns most surely (nearest to it first)
            List<double[]> pool = new ArrayList<>();
            for (GenEconomy.Cand c : e.cands) {
                if (c.used || c.near < 18 || c.owner != poor && c.d[poor] > 1.15 * c.d[c.owner])
                    continue;
                pool.add(new double[] { c.d[poor], c.x, c.z });
            }
            pool.sort((x, y) -> Double.compare(x[0], y[0]));
            for (double[] c : pool) {
                if (addRows.size() >= 10)
                    break;
                int cx = (int) c[1], cz = (int) c[2];
                if (!padClear(m, cx, cz) || clearOf(m, cx, cz) < 18 || inJungle(m, cx, cz) || nearPatch(rows, cx, cz) < 18)
                    continue;
                double[] r = ctx.patch(cx, cz, GenEconomy.STANDARD, owner(ctx, cx, cz));
                if ((int) r[3] != poor)
                    continue;
                addRows.add(r);
            }
            // judge by the spread plus half the seats' coefficient of variation: with two seats tied at the top, cutting
            // one of them leaves the spread where it was, but it is still progress
            double bestJ = objective(rep);
            int bestEdit = -1;
            Fairness.Report bestRep = null;
            for (int k = 0; k < edits.size(); k++) {
                edits.get(k)[0].run();
                Fairness.Report r2 = ctx.report(rows);
                edits.get(k)[1].run();
                double j = objective(r2);
                if (j < bestJ - 1e-5) {
                    bestJ = j;
                    bestEdit = k;
                    bestRep = r2;
                }
            }
            int bestAdd = -1;
            for (int k = 0; k < addRows.size(); k++) {
                rows.add(addRows.get(k));
                Fairness.Report r2 = ctx.report(rows);
                rows.remove(rows.size() - 1);
                double j = objective(r2);
                if (j < bestJ - 1e-5) {
                    bestJ = j;
                    bestAdd = k;
                    bestRep = r2;
                }
            }
            if (bestRep == null)
                break;
            if (bestAdd >= 0) {
                double[] r = addRows.get(bestAdd);
                GenEconomy.Patch q = new GenEconomy.Patch((int) r[0], (int) r[1], (float) r[2], GenEconomy.PKind.NUDGE);
                e.patches.add(q);
                live.add(q);
                rows.add(r);
                for (GenEconomy.Cand c : e.cands) {
                    if (c.x == q.x && c.z == q.z)
                        c.used = true;
                    double dd = Geo.len(c.x - q.x, c.z - q.z);
                    if (dd < c.near)
                        c.near = dd;
                }
            } else {
                edits.get(bestEdit)[0].run();
            }
            // carry the row values back to the patches (a removed patch has value 0)
            for (int i = live.size() - 1; i >= 0; i--) {
                double v = rows.get(i)[2];
                GenEconomy.Patch q = live.get(i);
                if (v <= 0) {
                    q.alive = false;
                    live.remove(i);
                    rows.remove(i);
                } else {
                    q.value = (float) v;
                }
            }
            rep = bestRep;
            m.nudges++;
        }
        e.writeWith(b.g, b.dist);
        m.report = Fairness.score(m, b.g, b.dist, w);
    }

    /** What the nudge minimises: the spread, plus half the coefficient of variation of the seats' scores. */
    static double objective(Fairness.Report r) {
        double var = 0;
        for (Fairness.SeatScore sc : r.seats())
            var += (sc.total() - r.mean()) * (sc.total() - r.mean());
        double cv = r.mean() > 0 ? Math.sqrt(var / r.seats().length) / r.mean() : 0;
        return r.spread() + 0.5 * cv;
    }

    private static double nearPatch(List<double[]> rows, int x, int z) {
        double best = Double.MAX_VALUE;
        for (double[] r : rows)
            if (r[2] > 0)
                best = Math.min(best, Geo.len(r[0] - x, r[1] - z));
        return best;
    }

    private static Runnable[] valueEdit(double[] row, float to) {
        double from = row[2];
        return new Runnable[] { () -> row[2] = to, () -> row[2] = from };
    }

    private static double exposureOf(Fairness.Context ctx, double[] row, int s) {
        double[] d = new double[ctx.m.n];
        System.arraycopy(row, 4, d, 0, d.length);
        return Fairness.exposure(ctx.m, d, s);
    }

    private static int owner(Fairness.Context ctx, int x, int z) {
        int best = -1;
        double bd = Double.POSITIVE_INFINITY;
        for (int s = 0; s < ctx.m.n; s++) {
            double d = ctx.g.blocks(ctx.dist[s], x, z);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    /** No tree on the pad (the forest grew after the fill) and the pad's columns are reached by ground. */
    private static boolean padClear(GenMap m, int x, int z) {
        for (int a = -MapLayout.PAD - 1; a <= MapLayout.PAD + 1; a++)
            for (int b = -MapLayout.PAD - 1; b <= MapLayout.PAD + 1; b++)
                if (m.treeAt(x + a, z + b))
                    return false;
        return true;
    }

    private static double clearOf(GenMap m, int x, int z) {
        double best = Double.MAX_VALUE;
        for (MapLayout.Site s : m.sites)
            best = Math.min(best, Geo.len(s.dx() - x, s.dz() - z));
        for (MapLayout.Spot v : m.vents)
            best = Math.min(best, Geo.len(v.dx() - x, v.dz() - z) + 6);
        for (GenMap.Camp c : m.camps)
            best = Math.min(best, Geo.len(c.dx() - x, c.dz() - z));
        return best;
    }

    private static boolean inJungle(GenMap m, int x, int z) {
        for (GenMap.Jungle j : m.jungle)
            for (int[] d : j.discs())
                if (Geo.len(x - d[0], z - d[1]) <= d[2] + 4)
                    return true;
        return false;
    }
}
