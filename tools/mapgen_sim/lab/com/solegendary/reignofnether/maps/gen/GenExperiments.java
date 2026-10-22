package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MetalOptions;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Phase 1 experiments (lab only): for one cell (players, shape, contrast) and a range of seeds, build every balancing
 * strategy's map, score it under the central weights and under 40 draws from the weights' ranges, run the economic sim
 * on the main strategies' maps, and write one CSV row per (seed, strategy) plus one per seat. Seeds are independent,
 * so they run on a thread pool; rows are written in seed order.
 * <p>
 * Usage: GenExperiments out n shape contrast seeds threads [simRuns]
 */
public final class GenExperiments {
    static final String[] STRATS = { "NONE", "NUDGE", "REROLL", "SWAP", "SWAP_NUDGE", "REROLL_NUDGE" };
    static final int ROBUST = 40;
    static final Fairness.Weights[] DRAWS = new Fairness.Weights[ROBUST];

    static {
        GenRng r = new GenRng(0xFA1E5L);
        for (int i = 0; i < ROBUST; i++)
            DRAWS[i] = Fairness.Weights.CENTRAL.sample(r);
    }

    public static void main(String[] a) throws Exception {
        File out = new File(a[0]);
        out.mkdirs();
        int n = Integer.parseInt(a[1]);
        GenOptions.Shape shape = GenOptions.Shape.valueOf(a[2]);
        GenOptions.Contrast contrast = GenOptions.Contrast.valueOf(a[3]);
        int seeds = Integer.parseInt(a[4]);
        int threads = Integer.parseInt(a[5]);
        int simRuns = a.length > 6 ? Integer.parseInt(a[6]) : 12;
        String tag = "n" + n + "_" + shape + "_" + contrast;
        int[] teams = new int[n];
        for (int i = 0; i < n; i++)
            teams[i] = i < n / 2 ? 0 : 1;
        GenOptions o = new GenOptions(shape, GenOptions.Modifiers.ON, contrast, GenOptions.Balance.NONE, 0.05, false);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<SeedResult>> fs = new ArrayList<>();
        for (int k = 0; k < seeds; k++) {
            long seed = 0x5EED0000L + k * 104729L + n * 7L + shape.ordinal() * 1_000_003L + contrast.ordinal() * 99_991L;
            fs.add(pool.submit(() -> runSeed(teams, seed, o, simRuns)));
        }
        List<GenMap> sheet = new ArrayList<>();
        try (PrintWriter rows = new PrintWriter(new FileWriter(new File(out, tag + ".csv")));
             PrintWriter seatRows = new PrintWriter(new FileWriter(new File(out, tag + "_seats.csv")))) {
            rows.println("n,shape,contrast,seed,strategy,valid,spread,teamSpread,robMedian,robP90,robWithin5,simSpread,simTeam,"
                + "nudges,attempt,swap,ms,patchesMin,patchesMax,patchesTotal,sites,camps,jungles,mods,holeMean,holeP90,holeMax,"
                + "metalShareMin,metalShareMax");
            seatRows.println("n,shape,contrast,seed,strategy,seat,team,archetype,total,metal,objectives,defence,tempo,space,"
                + "patches,frontage,toMiddle,simArmy,simIncome,simStrength,front");
            for (Future<SeedResult> f : fs) {
                SeedResult r = f.get();
                for (String line : r.rows)
                    rows.println(line);
                for (String line : r.seatRows)
                    seatRows.println(line);
                if (sheet.size() < 8 && r.show != null)
                    sheet.add(r.show);
            }
        }
        pool.shutdown();
        GenRender.sheet(sheet, 4, 420, tag + " (SWAP_NUDGE)", new File(out, tag + ".png"));
        System.out.println("done " + tag);
    }

    static final class SeedResult {
        final List<String> rows = new ArrayList<>(), seatRows = new ArrayList<>();
        GenMap show;
    }

    static SeedResult runSeed(int[] teams, long seed, GenOptions o, int simRuns) {
        SeedResult res = new SeedResult();
        MetalOptions metal = MetalOptions.DEFAULT;
        Fairness.Weights w = Fairness.Weights.CENTRAL;
        double tol = 0.05;
        // NONE, then NUDGE on the same build
        long t0 = System.nanoTime();
        MapGenerator.Built b0 = MapGenerator.build(new GenPlan(teams, seed, seed, o, false, 0), metal, w);
        long tNone = System.nanoTime() - t0;
        record(res, "NONE", b0, tNone, simRuns, seed);
        double spread0 = b0.m.report.spread();
        boolean valid0 = b0.m.valid;
        long t1 = System.nanoTime();
        MapGenerator.nudge(b0, tol, w);
        record(res, "NUDGE", b0, tNone + System.nanoTime() - t1, simRuns, seed);
        // REROLL (12) and the best of its first 4, nudged
        long t2 = System.nanoTime();
        GenPlan bestPlan = null, best4 = null;
        double bestS = Double.MAX_VALUE, best4S = Double.MAX_VALUE;
        boolean bestValid = false, best4Valid = false;
        MapGenerator.Built chosen = null;
        int attempt = -1;
        long tReroll4 = 0;
        for (int k = 0; k < MapGenerator.REROLLS; k++) {
            MapGenerator.Built b;
            double sp;
            boolean ok;
            GenPlan pl = new GenPlan(teams, seed, MapGenerator.drawSeed(seed, k), o, false, 0);
            if (k == 0) {
                b = null;
                sp = spread0;
                ok = valid0;
            } else {
                b = MapGenerator.build(pl, metal, w);
                b.m.attempt = k;
                sp = b.m.report.spread();
                ok = b.m.valid;
            }
            if ((ok && !bestValid) || (ok == bestValid && sp < bestS)) {
                bestS = sp;
                bestValid = ok;
                bestPlan = pl;
                attempt = k;
            }
            if (k < 4 && ((ok && !best4Valid) || (ok == best4Valid && sp < best4S))) {
                best4S = sp;
                best4Valid = ok;
                best4 = pl;
            }
            if (k == 3)
                tReroll4 = System.nanoTime() - t2 + tNone;
            if (ok && sp <= tol)
                break;
        }
        long tReroll = System.nanoTime() - t2 + tNone;
        chosen = MapGenerator.build(bestPlan, metal, w);
        chosen.m.attempt = attempt;
        record(res, "REROLL", chosen, tReroll, simRuns, seed);
        MapGenerator.Built r4 = MapGenerator.build(best4, metal, w);
        long t3 = System.nanoTime();
        MapGenerator.nudge(r4, tol, w);
        record(res, "REROLL_NUDGE", r4, tReroll4 + System.nanoTime() - t3, simRuns, seed);
        // SWAP, then nudged
        long t4 = System.nanoTime();
        MapGenerator.Built sw = swap(teams, seed, o, metal, w);
        long tSwap = System.nanoTime() - t4;
        record(res, "SWAP", sw, tSwap, simRuns, seed);
        long t5 = System.nanoTime();
        MapGenerator.nudge(sw, tol, w);
        record(res, "SWAP_NUDGE", sw, tSwap + System.nanoTime() - t5, simRuns, seed);
        res.show = sw.m;
        return res;
    }

    /** As MapGenerator's private swap: the best of the rotations and side swaps. */
    static MapGenerator.Built swap(int[] teams, long seed, GenOptions o, MetalOptions metal, Fairness.Weights w) {
        int n = teams.length;
        GenPlan first = new GenPlan(teams, seed, seed, o, false, 0);
        boolean twoSided = first.contrast != GenOptions.Contrast.MIRROR && first.contrast != GenOptions.Contrast.MIXED;
        List<Integer> rots = new ArrayList<>();
        int step = Math.max(1, n / 4);
        for (int r = 0; r < n && rots.size() < 4; r += step)
            rots.add(r);
        MapGenerator.Built best = null;
        int option = 0;
        for (int f = 0; f < (twoSided ? 2 : 1); f++)
            for (int r : rots) {
                GenPlan pl = f == 0 && r == 0 ? first : new GenPlan(teams, seed, seed, o, f == 1, r);
                MapGenerator.Built b = MapGenerator.build(pl, metal, w);
                b.m.swapOption = option++;
                if (best == null || MapGenerator.better(b, best))
                    best = b;
            }
        return best;
    }

    static boolean simFor(String strat) {
        return strat.equals("NONE") || strat.equals("NUDGE") || strat.equals("SWAP_NUDGE") || strat.equals("REROLL");
    }

    static void record(SeedResult res, String strat, MapGenerator.Built b, long ns, int simRuns, long seed) {
        GenMap m = b.m;
        Fairness.Geometry geo = new Fairness.Geometry(m, b.g, b.dist);
        List<double[]> rows = geo.rows(m.mexes);
        double[] sp = new double[ROBUST];
        int within = 0;
        for (int i = 0; i < ROBUST; i++) {
            sp[i] = new Fairness.Context(geo, DRAWS[i]).report(rows).spread();
            if (sp[i] <= 0.05)
                within++;
        }
        java.util.Arrays.sort(sp);
        Fairness.Report rep = m.report;
        EconSim.Result sim = simFor(strat) && m.valid ? EconSim.run(m, b.g, b.dist, simRuns, seed) : null;
        double simTeam = Double.NaN;
        if (sim != null) {
            double a = 0, c = 0;
            int na = 0, nc = 0;
            for (int s = 0; s < m.n; s++)
                if (m.teams[s] == m.teams[0]) {
                    a += sim.strength()[s];
                    na++;
                } else {
                    c += sim.strength()[s];
                    nc++;
                }
            double mean = (a + c) / m.n;
            simTeam = Math.abs(a / na - c / Math.max(1, nc)) / mean;
        }
        int[] per = new int[m.n];
        for (MapLayout.Mex x : m.mexes)
            per[x.slot()]++;
        int pmin = Integer.MAX_VALUE, pmax = 0;
        for (int v : per) {
            pmin = Math.min(pmin, v);
            pmax = Math.max(pmax, v);
        }
        double[] holes = holes(m, b.g);
        double msMin = Double.MAX_VALUE, msMax = 0, mtot = 0;
        for (int s = 0; s < m.n; s++)
            mtot += rep.seats()[s].metal();
        for (int s = 0; s < m.n; s++) {
            double share = rep.seats()[s].metal() / rep.seats()[s].total();
            msMin = Math.min(msMin, share);
            msMax = Math.max(msMax, share);
        }
        StringBuilder mods = new StringBuilder();
        for (Modifier mo : m.modifiers)
            mods.append(mods.length() > 0 ? "+" : "").append(mo.name());
        res.rows.add(String.format(Locale.ROOT,
            "%d,%s,%s,%d,%s,%s,%.4f,%.4f,%.4f,%.4f,%.3f,%s,%s,%d,%d,%d,%.1f,%d,%d,%d,%d,%d,%d,%s,%.1f,%.1f,%.1f,%.3f,%.3f",
            m.n, m.shape, m.contrast, seed, strat, m.valid, rep.spread(), rep.teamSpread(), sp[ROBUST / 2],
            sp[(int) (ROBUST * 0.9)], within / (double) ROBUST, sim == null ? "" : String.format(Locale.ROOT, "%.4f", sim.spread()),
            sim == null ? "" : String.format(Locale.ROOT, "%.4f", simTeam), m.nudges, m.attempt, m.swapOption, ns / 1e6,
            pmin, pmax, m.mexes.size(), m.sites.size(), m.camps.size(), m.jungle.size(), mods, holes[0], holes[1], holes[2],
            msMin, msMax));
        for (int s = 0; s < m.n; s++) {
            Fairness.SeatScore sc = rep.seats()[s];
            res.seatRows.add(String.format(Locale.ROOT,
                "%d,%s,%s,%d,%s,%d,%d,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%.3f,%.1f,%s,%s,%s,%d",
                m.n, m.shape, m.contrast, seed, strat, s, m.teams[s], m.seats[s].name(), sc.total(), sc.metal(),
                sc.objectives(), sc.defence(), sc.tempo(), sc.space(), sc.patches(), sc.frontage(), sc.toMiddle(),
                sim == null ? "" : String.format(Locale.ROOT, "%.1f", sim.army()[s]),
                sim == null ? "" : String.format(Locale.ROOT, "%.2f", sim.income()[s]),
                sim == null ? "" : String.format(Locale.ROOT, "%.1f", sim.strength()[s]), front(m, s) ? 1 : 0));
        }
    }

    /** A seat with an enemy neighbour on the ring (1v1: both). */
    static boolean front(GenMap m, int s) {
        int a = (s + 1) % m.n, b = (s + m.n - 1) % m.n;
        return m.teams[a] != m.teams[s] || m.teams[b] != m.teams[s];
    }

    /** Distance from reachable open ground (off base pads and the jungle) to the nearest patch: mean, p90, max. */
    static double[] holes(GenMap m, WalkGrid g) {
        MapLayout.Slot s0 = m.slots.get(0);
        boolean[] seen = g.flood(s0.dx(), s0.dz());
        List<Double> ds = new ArrayList<>();
        for (int x = -m.half; x <= m.half; x += 4)
            for (int z = -m.half; z <= m.half; z += 4) {
                if (!seen[m.idx(x, z)])
                    continue;
                boolean skip = false;
                for (MapLayout.Slot sl : m.slots)
                    if (Geo.len(sl.dx() - x, sl.dz() - z) < 52)
                        skip = true;
                for (GenMap.Jungle j : m.jungle)
                    if (j.contains(x, z))
                        skip = true;
                if (skip)
                    continue;
                double best = Double.MAX_VALUE;
                for (MapLayout.Mex mx : m.mexes)
                    best = Math.min(best, Geo.len(mx.dx() - x, mx.dz() - z));
                ds.add(best);
            }
        if (ds.isEmpty())
            return new double[] { 0, 0, 0 };
        ds.sort(Double::compare);
        double mean = 0;
        for (double d : ds)
            mean += d / ds.size();
        return new double[] { mean, ds.get((int) (ds.size() * 0.9)), ds.get(ds.size() - 1) };
    }
}
