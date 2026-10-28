package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MetalOptions;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Lab: SWAP_NUDGE against SWAP_ROBUST (the nudge judged under eight weight draws as well) on the same swapped builds,
 * scored under the experiments' 40 test draws (a different set). Usage: GenRobustCmp out.csv n seeds
 */
public final class GenRobustCmp {
    public static void main(String[] a) throws Exception {
        int n = Integer.parseInt(a[1]), seeds = Integer.parseInt(a[2]);
        int[] teams = new int[n];
        for (int i = 0; i < n; i++)
            teams[i] = i < n / 2 ? 0 : 1;
        Fairness.Weights w = Fairness.Weights.CENTRAL;
        try (PrintWriter out = new PrintWriter(new FileWriter(a[0]))) {
            out.println("n,contrast,shape,seed,strategy,spread,teamSpread,robMedian,robWithin5,nudges,ms");
            for (GenOptions.Contrast c : new GenOptions.Contrast[] { GenOptions.Contrast.PROSPECTOR_WARDEN,
                    GenOptions.Contrast.OPEN_BASTION, GenOptions.Contrast.RIVER, GenOptions.Contrast.MIXED })
                for (int k = 0; k < seeds; k++) {
                    long seed = 0x40B00000L + k * 7919L + c.ordinal();
                    GenOptions.Shape sh = k % 2 == 0 ? GenOptions.Shape.ROUND : GenOptions.Shape.SQUARE;
                    GenOptions o = new GenOptions(sh, GenOptions.Modifiers.ON, c, GenOptions.Balance.NONE, 0.05, false);
                    for (boolean robust : new boolean[] { false, true }) {
                        long t0 = System.nanoTime();
                        MapGenerator.Built b = GenExperiments.swap(teams, seed, o, MetalOptions.DEFAULT, w);
                        MapGenerator.nudge(b, 0.05, w, robust);
                        double ms = (System.nanoTime() - t0) / 1e6;
                        Fairness.Geometry geo = new Fairness.Geometry(b.m, b.g, b.dist);
                        double[] sp = new double[GenExperiments.ROBUST];
                        int within = 0;
                        for (int i = 0; i < sp.length; i++) {
                            sp[i] = new Fairness.Context(geo, GenExperiments.DRAWS[i]).report(geo.rows(b.m.mexes)).spread();
                            if (sp[i] <= 0.05)
                                within++;
                        }
                        java.util.Arrays.sort(sp);
                        out.printf(Locale.ROOT, "%d,%s,%s,%d,%s,%.4f,%.4f,%.4f,%.3f,%d,%.0f%n", n, c, sh, seed,
                            robust ? "SWAP_ROBUST" : "SWAP_NUDGE", b.m.report.spread(), b.m.report.teamSpread(),
                            sp[sp.length / 2], within / (double) sp.length, b.m.nudges, ms);
                        out.flush();
                    }
                }
        }
        System.out.println("done");
    }
}
