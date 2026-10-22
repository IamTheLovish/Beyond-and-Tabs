package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MetalOptions;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Lab: 4v4 team-level sim check with many runs (the 12-run sim in exp1 cannot resolve team gaps under ~6%): for each
 * contrast, balanced maps (SWAP_NUDGE), sim strength per team averaged over {@code runs}; one row per map with the
 * signed gap side A - side B (A = the first archetype alphabetically) and the score's team gap.
 * Usage: GenTeamBias out.csv seeds runs
 */
public final class GenTeamBias {
    public static void main(String[] a) throws Exception {
        int seeds = Integer.parseInt(a[1]), runs = Integer.parseInt(a[2]);
        int[] teams = { 0, 0, 0, 0, 1, 1, 1, 1 };
        try (PrintWriter w = new PrintWriter(new FileWriter(a[0]))) {
            w.println("contrast,shape,seed,A,B,scoreGap,simGap,simSeatSpread");
            for (GenOptions.Contrast c : new GenOptions.Contrast[] { GenOptions.Contrast.MIRROR,
                    GenOptions.Contrast.PROSPECTOR_WARDEN, GenOptions.Contrast.OPEN_BASTION, GenOptions.Contrast.RIVER })
                for (int k = 0; k < seeds; k++) {
                    long seed = 0x7EA40000L + k * 7919L;
                    GenOptions.Shape sh = k % 2 == 0 ? GenOptions.Shape.ROUND : GenOptions.Shape.SQUARE;
                    GenOptions o = new GenOptions(sh, GenOptions.Modifiers.ON, c, GenOptions.Balance.SWAP_NUDGE, 0.05, true);
                    MapGenerator.Built b = GenExperiments.swap(teams, seed, o, MetalOptions.DEFAULT, Fairness.Weights.CENTRAL.live());
                    MapGenerator.nudge(b, 0.05, Fairness.Weights.CENTRAL.live());
                    EconSim.Result r = EconSim.run(b.m, b.g, b.dist, runs, seed);
                    String archA = b.m.seats[0].name(), archB = b.m.seats[4].name();
                    boolean swapAB = archA.compareTo(archB) > 0;
                    double sa = 0, sb = 0, ta = 0, tb = 0;
                    for (int s = 0; s < 8; s++) {
                        boolean sideA = (b.m.teams[s] == 0) != swapAB;
                        if (sideA) {
                            sa += r.strength()[s];
                            ta += b.m.report.total(s);
                        } else {
                            sb += r.strength()[s];
                            tb += b.m.report.total(s);
                        }
                    }
                    w.printf(Locale.ROOT, "%s,%s,%d,%s,%s,%.4f,%.4f,%.4f%n", c, sh, seed, swapAB ? archB : archA,
                        swapAB ? archA : archB, (ta - tb) / ((ta + tb) / 2), (sa - sb) / ((sa + sb) / 2), r.spread());
                    w.flush();
                }
        }
        System.out.println("done");
    }
}
