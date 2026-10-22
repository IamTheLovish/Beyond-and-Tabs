package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MetalOptions;

import java.util.*;

/**
 * Headless experiments on the map generator (no Minecraft). See tools/mapgen_sim/run.sh.
 * balance N SEEDS | robust N SEEDS DRAWS | time N REPS | gametest
 */
public final class MapGenSim {
    static int[] teams(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++) t[i] = i < (n + 1) / 2 ? 0 : 1;
        return t;
    }

    static GenOptions opts(GenOptions.Balance b) {
        return GenOptions.DEFAULT.withBalance(b);
    }

    public static void main(String[] a) {
        String cmd = a[0];
        switch (cmd) {
            case "balance" -> balance(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            case "robust" -> robust(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            case "time" -> time(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            case "gametest" -> gametest();
            default -> throw new IllegalArgumentException(cmd);
        }
    }

    static long seedOf(int i) { return 0x5EED000000L + i * 7919L; }

    /** spread under central weights per balancer, by contrast */
    static void balance(int n, int seeds) {
        System.out.println("n=" + n + " seeds=" + seeds);
        for (GenOptions.Balance b : GenOptions.Balance.values()) {
            int within = 0, valid = 0; double sum = 0, worst = 0;
            Map<String, int[]> byC = new TreeMap<>();
            for (int i = 0; i < seeds; i++) {
                GenMap m = MapGenerator.generate(teams(n), seedOf(i), opts(b), MetalOptions.DEFAULT);
                double sp = m.report.spread();
                if (m.valid) valid++;
                if (sp <= 0.05 + 1e-9) within++;
                sum += sp; worst = Math.max(worst, sp);
                int[] c = byC.computeIfAbsent(m.contrast.name(), k -> new int[2]);
                c[0]++; if (sp <= 0.05 + 1e-9) c[1]++;
            }
            StringBuilder sb = new StringBuilder();
            byC.forEach((k, v) -> sb.append(k).append(' ').append(v[1]).append('/').append(v[0]).append("  "));
            System.out.printf("%-12s within5=%5.1f%% valid=%d/%d meanSpread=%.2f%% worst=%.2f%%  | %s%n", b,
                100.0 * within / seeds, valid, seeds, 100 * sum / seeds, 100 * worst, sb);
        }
    }

    /** Robustness: for each map, the share of weight draws in which the spread stays within 5%; and the mean spread. */
    static void robust(int n, int seeds, int draws) {
        for (GenOptions.Balance b : new GenOptions.Balance[] { GenOptions.Balance.NONE, GenOptions.Balance.SWAP_NUDGE }) {
            Map<String, double[]> byC = new TreeMap<>();
            for (int i = 0; i < seeds; i++) {
                GenMap m = MapGenerator.generate(teams(n), seedOf(i), opts(b), MetalOptions.DEFAULT);
                WalkGrid g = new WalkGrid(m, true);
                int[][] d = Fairness.fields(m, g);
                Fairness.Geometry geo = new Fairness.Geometry(m, g, d);
                GenRng r = GenRng.stream(seedOf(i) ^ 0xABCDEF, "weights");
                int ok = 0; double sp = 0;
                for (int k = 0; k < draws; k++) {
                    Fairness.Weights w = Fairness.Weights.CENTRAL.sample(r);
                    Fairness.Context c = new Fairness.Context(geo, w);
                    Fairness.Report rep = c.report(geo.rows(m.mexes));
                    if (rep.spread() <= 0.05 + 1e-9) ok++;
                    sp += rep.spread();
                }
                double[] v = byC.computeIfAbsent(m.contrast.name(), k -> new double[3]);
                v[0]++; v[1] += (double) ok / draws; v[2] += sp / draws;
            }
            System.out.println("n=" + n + " balancer=" + b + " seeds=" + seeds + " weightDraws=" + draws);
            byC.forEach((k, v) -> System.out.printf("  %-18s maps=%3.0f robustness(share of draws within 5%%)=%.2f meanSpreadUnderDraws=%.1f%%%n",
                k, v[0], v[1] / v[0], 100 * v[2] / v[0]));
            double tot = 0, cnt = 0; for (double[] v : byC.values()) { tot += v[1]; cnt += v[0]; }
            System.out.printf("  ALL robustness=%.2f%n", tot / cnt);
        }
    }

    static void time(int n, int reps) {
        for (GenOptions.Balance b : new GenOptions.Balance[] { GenOptions.Balance.NONE, GenOptions.Balance.SWAP_NUDGE }) {
            GenOptions o = opts(b);
            for (int i = 0; i < 5; i++) MapGenerator.generate(teams(n), seedOf(i + 1000), o, MetalOptions.DEFAULT);
            long[] t = new long[reps];
            for (int i = 0; i < reps; i++) {
                long t0 = System.nanoTime();
                MapGenerator.generate(teams(n), seedOf(i), o, MetalOptions.DEFAULT);
                t[i] = System.nanoTime() - t0;
            }
            Arrays.sort(t);
            System.out.printf("n=%d balancer=%s reps=%d median=%.0f ms p90=%.0f ms max=%.0f ms%n", n, b, reps, t[reps / 2] / 1e6, t[reps * 9 / 10] / 1e6, t[reps - 1] / 1e6);
        }
    }

    static void gametest() {
        long[] seeds = { GenSeed.pack(0x5EED0001L, 1, 0), GenSeed.pack(0x5EED0002L, 2, 0), GenSeed.pack(0x5EED0003L, 1, 0), GenSeed.pack(0x5EED0004L, 2, 0) };
        double worst = 0; int maps = 0, bad = 0;
        for (int n : new int[] { 2, 4, 8 })
            for (long s : seeds) {
                GenMap m = MapGenerator.generate(teams(n), GenSeed.generatorSeed(s), GenSeed.options(s), MetalOptions.DEFAULT);
                maps++;
                double sp = m.report.spread(), ts = m.report.teamSpread();
                worst = Math.max(worst, sp);
                if (!m.valid || sp > 0.05 + 1e-9 || ts > 0.05 + 1e-9) { bad++; System.out.println("PROBLEM n=" + n + " seed " + Long.toHexString(s) + " valid=" + m.valid + " spread=" + sp + " team=" + ts); }
                System.out.printf("n=%d seed=%x %s shape=%s spread=%.2f%% team=%.2f%% valid=%b%n", n, s, m.contrast, m.shape, 100 * sp, 100 * ts, m.valid);
            }
        System.out.printf("maps=%d problems=%d worstSpread=%.2f%%%n", maps, bad, 100 * worst);
    }
}
