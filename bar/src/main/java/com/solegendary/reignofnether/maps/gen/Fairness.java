package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The seat fairness score of a generated map (lovish Oct 21 21:47: "a seat must land within ~5% of the others on the
 * combined score"). Everything is priced in one unit, **metal per second averaged over a 15-minute game**, so a patch,
 * a captured mine, a held shrine, a defensible ramp and a long walk to the middle can be added up.
 * <p>
 * The parts (numbers from the hidden-mechanics codex, commit 8816e8a):
 * <ul>
 * <li><b>metal</b>: each patch a seat can claim pays T1 2.0/s x value from the moment a builder gets there (the seat's
 *     patches are built nearest first, one every {@code buildGap} seconds plus the walk), then T2 7.0/s x value once the
 *     seat refits it (safe patches only, one every {@code t2Gap} seconds from {@code t2Time}, 45 s without income during
 *     the refit). A patch is shared between seats by a soft nearest rule on walk distance (softness {@code tau}) and loses
 *     up to {@code riskMax} of its value the closer it lies to an enemy (raids).</li>
 * <li><b>objectives</b>: a held site pays its income (Ancient Mine 3 metal/s, Ley Shrine 30 and Lava Vent 45 energy/s at
 *     {@code energyPerMetal} energy a metal) or its estimated worth (camps, garrisons, sight, the forge's 25% research
 *     discount for the whole team), from capture on (army walk + 20 s), shared by the same soft rule.</li>
 * <li><b>defence</b>: the share of directions toward the enemy from which ground units walk straight into the base
 *     (a plateau with one ramp is cheap to hold, an open meadow is not).</li>
 * <li><b>tempo</b>: walk to the middle against the seats' mean (map control comes to who is closer).</li>
 * <li><b>space</b>: a penalty when the seat has too little buildable ground near its start.</li>
 * </ul>
 * The weights are honest guesses with ranges ({@link Weights#sample}); the report measures fairness under the central
 * weights and the experiments measure how much of it survives the whole range.
 */
public final class Fairness {
    private Fairness() { }

    /**
     * Every weight of the score with its central value; {@link #sample} draws one from its range (see the report's table).
     */
    public record Weights(double walk, double buildGap, double t2Time, double t2Gap, double tau, double riskMax,
                          double energyPerMetal, double camp, double garrison, double bell, double tower, double forge,
                          double oasis, double relic, double creep, double hydra, double defence, double tempo,
                          double space, double geoStart) {

        public static final Weights CENTRAL = new Weights(2.5, 22, 540, 35, 30, 0.6, 35, 0.6, 0.8, 0.3, 0.45, 0.75, 0.4,
            0.8, 0.5, 2.0, 2.5, 0.01, 2.0, 420);

        /** One draw from the uncertainty ranges (uniform, or log-uniform for the ones that span a factor). */
        public Weights sample(GenRng r) {
            return new Weights(r.range(2.0, 3.5), r.range(15, 35), r.range(420, 720), r.range(25, 60), r.range(15, 50),
                r.range(0.3, 0.9), logU(r, 20, 60), logU(r, 0.3, 1.3), logU(r, 0.4, 1.7), logU(r, 0.1, 0.6),
                logU(r, 0.15, 0.9), logU(r, 0.3, 1.5), logU(r, 0.1, 0.8), logU(r, 0.3, 2.0), logU(r, 0.2, 1.0),
                logU(r, 0.8, 4.0), r.range(1.0, 5.0), r.range(0.003, 0.02), r.range(1.0, 3.0), r.range(360, 540));
        }

        private static double logU(GenRng r, double lo, double hi) {
            return lo * StrictMath.exp(r.nextDouble() * StrictMath.log(hi / lo));
        }
    }

    public static final double HORIZON = 900;
    static final double T1 = 2.0, T2 = 7.0, REFIT = 45;

    /** One seat's score and its parts (metal per second, 15-minute average). */
    public record SeatScore(double metal, double objectives, double defence, double tempo, double space, double total,
                            int patches, double frontage, double toMiddle) { }

    public record Report(SeatScore[] seats, double mean, double spread, double teamSpread) {
        public double total(int seat) {
            return seats[seat].total();
        }
    }

    /** Walk distances of every seat to every coarse cell (WalkGrid.distances), computed once per scoring. */
    static int[][] fields(GenMap m, WalkGrid g) {
        int[][] d = new int[m.n][];
        for (int s = 0; s < m.n; s++)
            d[s] = g.distances(m.slots.get(s).dx(), m.slots.get(s).dz());
        return d;
    }

    public static Report score(GenMap m, WalkGrid g, int[][] dist, Weights w) {
        Context c = new Context(new Geometry(m, g, dist), w);
        return c.report(c.geo.rows(m.mexes));
    }

    /**
     * Everything the score reads from the ground, independent of the weights: each seat's frontage, walk to the middle
     * and buildable room, every objective's walk distance from every seat. Worked out once per map; the weights then
     * cost almost nothing (the experiments price 40 weight draws per map, the nudge balancer hundreds of edits).
     */
    static final class Geometry {
        final GenMap m;
        final WalkGrid g;
        final int[][] dist;
        final double[] front, mid;
        final int[] build;
        /** Objectives: {kind code, team-wide 0/1, d_0 .. d_n-1}; kind codes: 0..8 site kinds, 10 creep, 11 hydra, 12 relic, 20 vent. */
        final List<double[]> objectives = new ArrayList<>();

        Geometry(GenMap m, WalkGrid g, int[][] dist) {
            this.m = m;
            this.g = g;
            this.dist = dist;
            int n = m.n;
            front = new double[n];
            mid = new double[n];
            build = new int[n];
            for (int s = 0; s < n; s++) {
                front[s] = frontage(m, g, dist[s], s);
                mid[s] = g.blocks(dist[s], 0, 0);
                if (Double.isInfinite(mid[s]))
                    mid[s] = 2.0 * m.ring;
                build[s] = buildable(m, g, dist, s);
            }
            for (MapLayout.Site st : m.sites)
                objectives.add(obj(st.dx(), st.dz(), st.kind().ordinal(), st.kind() == Kind.FORGE));
            for (GenMap.Camp c : m.camps)
                objectives.add(obj(c.dx(), c.dz(), 10 + c.kind().ordinal(), c.kind() == GenMap.CampKind.HYDRA));
            for (MapLayout.Spot v : m.vents)
                objectives.add(obj(v.dx(), v.dz(), 20, false));
        }

        private double[] obj(int x, int z, int code, boolean team) {
            double[] r = new double[2 + m.n];
            r[0] = code;
            r[1] = team ? 1 : 0;
            for (int s = 0; s < m.n; s++)
                r[2 + s] = g.blocks(dist[s], x, z);
            return r;
        }

        /** A patch as the score reads it: {x, z, value, owner, d_0 .. d_n-1}. */
        double[] patch(int x, int z, float value, int owner) {
            double[] r = new double[4 + m.n];
            r[0] = x;
            r[1] = z;
            r[2] = value;
            r[3] = owner;
            for (int s = 0; s < m.n; s++)
                r[4 + s] = g.blocks(dist[s], x, z);
            return r;
        }

        List<double[]> rows(List<MapLayout.Mex> mexes) {
            List<double[]> rows = new ArrayList<>();
            for (MapLayout.Mex p : mexes)
                rows.add(patch(p.dx(), p.dz(), p.value(), p.slot()));
            return rows;
        }
    }

    /** The score's weights applied to one map's geometry: objectives, defence, tempo and space priced once. */
    static final class Context {
        final Geometry geo;
        final GenMap m;
        final WalkGrid g;
        final int[][] dist;
        final Weights w;
        final double[] obj, def, tempo, space;

        Context(Geometry geo, Weights w) {
            this.geo = geo;
            this.m = geo.m;
            this.g = geo.g;
            this.dist = geo.dist;
            this.w = w;
            int n = m.n;
            obj = new double[n];
            def = new double[n];
            tempo = new double[n];
            space = new double[n];
            double[] d = new double[n];
            for (double[] o : geo.objectives) {
                int code = (int) o[0];
                double v;
                double start = 0;
                if (code < 10)
                    v = siteValue(Kind.values()[code], w);
                else if (code == 10)
                    v = w.creep();
                else if (code == 11)
                    v = w.hydra();
                else if (code == 12)
                    v = w.relic();
                else {
                    v = 120 / w.energyPerMetal();
                    start = w.geoStart();
                }
                System.arraycopy(o, 2, d, 0, n);
                objective(m, w, d, v, o[1] > 0, start, obj);
            }
            double meanMid = 0;
            for (int s = 0; s < n; s++)
                meanMid += geo.mid[s] / n;
            for (int s = 0; s < n; s++) {
                def[s] = -w.defence() * geo.front[s];
                tempo[s] = -w.tempo() * (geo.mid[s] - meanMid);
                space[s] = -w.space() * Math.max(0, 1 - geo.build[s] / 1800.0);
            }
        }

        double[] patch(int x, int z, float value, int owner) {
            return geo.patch(x, z, value, owner);
        }

        Report report(List<double[]> patches) {
            int n = m.n;
            List<List<double[]>> claims = new ArrayList<>();
            for (int s = 0; s < n; s++)
                claims.add(new ArrayList<>());
            int[] count = new int[n];
            double[] d = new double[n];
            for (double[] p : patches) {
                if (p[2] <= 0)
                    continue;   // a patch the nudge is pricing the removal of
                System.arraycopy(p, 4, d, 0, n);
                double[] c = shares(m, d, w.tau(), false);
                for (int s = 0; s < n; s++) {
                    if (c[s] < 0.03)
                        continue;
                    claims.get(s).add(new double[] { d[s], c[s], p[2], exposure(m, d, s) });
                }
                if ((int) p[3] >= 0 && (int) p[3] < n)
                    count[(int) p[3]]++;
            }
            SeatScore[] out = new SeatScore[n];
            for (int s = 0; s < n; s++) {
                double metal = metalIncome(claims.get(s), w);
                double total = metal + obj[s] + def[s] + tempo[s] + space[s];
                out[s] = new SeatScore(metal, obj[s], def[s], tempo[s], space[s], total, count[s], geo.front[s], geo.mid[s]);
            }
            return summarise(m, out);
        }
    }

    static Report summarise(GenMap m, SeatScore[] out) {
        int n = out.length;
        double mean = 0;
        for (SeatScore sc : out)
            mean += sc.total() / n;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (SeatScore sc : out) {
            lo = Math.min(lo, sc.total());
            hi = Math.max(hi, sc.total());
        }
        // team spread: mean seat score per team
        int teams = 0;
        for (int t : m.teams)
            teams = Math.max(teams, t + 1);
        double[] tSum = new double[teams];
        int[] tN = new int[teams];
        for (int s = 0; s < n; s++) {
            tSum[m.teams[s]] += out[s].total();
            tN[m.teams[s]]++;
        }
        double tLo = Double.MAX_VALUE, tHi = -Double.MAX_VALUE;
        for (int t = 0; t < teams; t++)
            if (tN[t] > 0) {
                tLo = Math.min(tLo, tSum[t] / tN[t]);
                tHi = Math.max(tHi, tSum[t] / tN[t]);
            }
        double spread = mean > 0 ? (hi - lo) / mean : 1;
        double tSpread = mean > 0 && tLo < Double.MAX_VALUE ? (tHi - tLo) / mean : 1;
        return new Report(out, mean, spread, tSpread);
    }

    /**
     * Soft shares of a contested thing by walk distance: exp(-d / tau) normalised over the seats (or, for a team-wide
     * reward, every seat gets its whole team's share). Unreached seats get nothing.
     */
    static double[] shares(GenMap m, double[] d, double tau, boolean teamWide) {
        int n = d.length;
        double min = Double.POSITIVE_INFINITY;
        for (double v : d)
            min = Math.min(min, v);
        double[] c = new double[n];
        if (Double.isInfinite(min))
            return c;
        double sum = 0;
        for (int s = 0; s < n; s++) {
            c[s] = Double.isInfinite(d[s]) ? 0 : StrictMath.exp(-(d[s] - min) / tau);
            sum += c[s];
        }
        for (int s = 0; s < n; s++)
            c[s] /= sum;
        if (teamWide) {
            double[] t = new double[n];
            for (int s = 0; s < n; s++)
                for (int o = 0; o < n; o++)
                    if (m.teams[o] == m.teams[s])
                        t[s] += c[o];
            return t;
        }
        return c;
    }

    /** How far toward the enemy a thing lies for seat s: d_s / (d_s + nearest enemy's d), 0.5 on the front line. */
    static double exposure(GenMap m, double[] d, int s) {
        double de = Double.POSITIVE_INFINITY;
        for (int o = 0; o < d.length; o++)
            if (m.teams[o] != m.teams[s])
                de = Math.min(de, d[o]);
        if (Double.isInfinite(de))
            return 0;
        if (Double.isInfinite(d[s]))
            return 1;
        return d[s] / Math.max(1e-6, d[s] + de);
    }

    static double loss(double x, Weights w) {
        return w.riskMax() * smooth(0.35, 0.8, x);
    }

    /**
     * The 15-minute average income of a seat's claims: rows of {walk distance, share, value, exposure}. Built nearest
     * first at one per buildGap (a share counts as that much of a build), T2 for the safe ones from t2Time.
     */
    static double metalIncome(List<double[]> rows, Weights w) {
        rows.sort((a, b) -> Double.compare(a[0], b[0]));
        int k = rows.size();
        double[] t1 = new double[k];
        double built = 0;
        for (int i = 0; i < k; i++) {
            double[] r = rows.get(i);
            t1[i] = 45 + w.buildGap() * built + r[0] / w.walk();
            built += r[1];
        }
        // T2 refits: the safe patches (exposure < 0.42), earliest built first
        Integer[] order = new Integer[k];
        for (int i = 0; i < k; i++)
            order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(t1[a], t1[b]));
        double[] t2 = new double[k];
        Arrays.fill(t2, Double.POSITIVE_INFINITY);
        double refits = 0;
        for (int oi = 0; oi < k; oi++) {
            int i = order[oi];
            double[] r = rows.get(i);
            if (r[3] >= 0.42)
                continue;
            t2[i] = Math.max(t1[i] + 60, w.t2Time() + w.t2Gap() * refits);
            refits += r[1];
        }
        double sum = 0;
        for (int i = 0; i < k; i++) {
            double[] r = rows.get(i);
            double a = Math.min(HORIZON, t1[i]), b = Math.min(HORIZON, t2[i]);
            double inc = T1 * (b - a);
            if (b < HORIZON)
                inc += T2 * Math.max(0, HORIZON - b - REFIT);
            sum += inc * r[2] * r[1] * (1 - loss(r[3], w));
        }
        return sum / HORIZON;
    }

    static double siteValue(Kind k, Weights w) {
        return switch (k) {
            case MINE -> 3.0;
            case SHRINE -> 30 / w.energyPerMetal();
            case VENT -> 45 / w.energyPerMetal();
            case CAMP -> w.camp();
            case GARRISON -> w.garrison();
            case BELL -> w.bell();
            case WATCHTOWER -> w.tower();
            case FORGE -> w.forge();
            case OASIS -> w.oasis();
        };
    }

    /** Adds one objective's worth to every seat by its share, from capture (or from {@code start}) to the horizon. */
    private static void objective(GenMap m, Weights w, double[] d, double value, boolean teamWide, double start,
                                  double[] out) {
        int n = m.n;
        double[] c = shares(m, d, w.tau(), teamWide);
        for (int s = 0; s < n; s++) {
            if (c[s] <= 0 || Double.isInfinite(d[s]))
                continue;
            double t = Math.max(start, 100 + d[s] / w.walk() + 20);
            double frac = Math.max(0, HORIZON - t) / HORIZON;
            out[s] += value * frac * c[s] * (1 - 0.5 * loss(exposure(m, d, s), w));
        }
    }

    /**
     * Share of the directions toward the enemy from which ground units walk nearly straight into the base: a point 85
     * blocks out is "open" when its walk distance is at most 1.35 x 85 (a ramp's detour or a cliff closes it). Weighted
     * toward the nearest enemy start (half weight on the far side).
     */
    static double frontage(GenMap m, WalkGrid g, int[] dist, int s) {
        MapLayout.Slot sl = m.slots.get(s);
        double ex = 0, ez = 0, best = Double.MAX_VALUE;
        for (int o = 0; o < m.n; o++)
            if (m.teams[o] != m.teams[s]) {
                MapLayout.Slot e = m.slots.get(o);
                double dd = Geo.len(e.dx() - sl.dx(), e.dz() - sl.dz());
                if (dd < best) {
                    best = dd;
                    ex = e.dx() - sl.dx();
                    ez = e.dz() - sl.dz();
                }
            }
        double toEnemy = StrictMath.atan2(ez, ex);
        double open = 0, all = 0;
        for (int k = 0; k < 72; k++) {
            double a = k * Math.PI * 2 / 72;
            double wgt = 0.5 + 0.5 * StrictMath.cos(a - toEnemy);
            double x = sl.dx() + 85 * StrictMath.cos(a), z = sl.dz() + 85 * StrictMath.sin(a);
            all += wgt;
            if (!m.inside(x, z))
                continue;   // the wall: nobody comes from there
            double d = g.blocks(dist, x, z);
            if (d <= 1.35 * 85)
                open += wgt;
        }
        return all > 0 ? open / all : 0;
    }

    /** Coarse cells (4 blocks each) of flat walkable ground within 80 blocks' walk that this seat is nearest to. */
    static int buildable(GenMap m, WalkGrid g, int[][] dist, int s) {
        int cnt = 0;
        for (int c = 0; c < g.cw * g.cw; c++) {
            int ds = dist[s][c];
            if (ds == WalkGrid.UNREACHED || ds > 800 || g.cWet[c])
                continue;
            boolean nearest = true;
            for (int o = 0; o < m.n && nearest; o++)
                if (o != s && dist[o][c] < ds)
                    nearest = false;
            if (nearest)
                cnt++;
        }
        return cnt;
    }

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
