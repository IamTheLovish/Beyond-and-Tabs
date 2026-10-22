package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MetalOptions;

import java.io.FileWriter;
import java.io.PrintWriter;

/** Lab: per-minute sim curves by archetype for balanced 1v1 maps (does the Prospector catch the Warden, and when?). Usage: GenCurves out.csv seeds */
public final class GenCurves {
    public static void main(String[] a) throws Exception {
        int seeds = Integer.parseInt(a[1]);
        try (PrintWriter w = new PrintWriter(new FileWriter(a[0]))) {
            w.println("contrast,seed,seat,archetype,minute,income,army,objIncome,score");
            for (GenOptions.Contrast c : new GenOptions.Contrast[] { GenOptions.Contrast.PROSPECTOR_WARDEN,
                    GenOptions.Contrast.OPEN_BASTION, GenOptions.Contrast.RIVER })
                for (int k = 0; k < seeds; k++) {
                    long seed = 0xC0FFEE00L + k * 7919L;
                    GenOptions o = new GenOptions(GenOptions.Shape.ANY, GenOptions.Modifiers.ON, c, GenOptions.Balance.SWAP_NUDGE, 0.05, false);
                    MapGenerator.Built b = GenExperiments.swap(new int[] { 0, 1 }, seed, o, MetalOptions.DEFAULT, Fairness.Weights.CENTRAL);
                    MapGenerator.nudge(b, 0.05, Fairness.Weights.CENTRAL);
                    EconSim.Curves cv = EconSim.curves(b.m, b.g, b.dist, 12, seed);
                    for (int s = 0; s < 2; s++)
                        for (int t = 0; t < cv.income()[s].length; t++)
                            w.printf(java.util.Locale.ROOT, "%s,%d,%d,%s,%d,%.2f,%.1f,%.2f,%.3f%n", c, seed, s, b.m.seats[s], t,
                                cv.income()[s][t], cv.army()[s][t], cv.objectives()[s][t], b.m.report.total(s));
                }
        }
        System.out.println("done");
    }
}
