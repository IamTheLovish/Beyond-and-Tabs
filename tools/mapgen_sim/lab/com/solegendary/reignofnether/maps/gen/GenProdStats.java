package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Lab: what the lobby gets (MapLayout.build(GENERATED) with packed seeds: the lobby balancer + fallback draws, live modifiers,
 * live weights): per map the time, the fallback attempt, the spreads, the robustness under weight draws, patch and site
 * counts, holes. Single-threaded so the times are one core's. Usage: GenProdStats out.csv seedsPerConfig
 */
public final class GenProdStats {
    public static void main(String[] a) throws Exception {
        int per = Integer.parseInt(a[1]);
        GenRng dr = new GenRng(0xFA1E5L);
        Fairness.Weights[] draws = new Fairness.Weights[40];
        for (int i = 0; i < draws.length; i++)
            draws[i] = Fairness.Weights.CENTRAL.sample(dr).live();
        try (PrintWriter w = new PrintWriter(new FileWriter(a[0]))) {
            w.println("n,shape,seed,contrast,ms,attempt,spread,teamSpread,robWithin5,robMedian,robTeamWithin5,robTeamMedian,patches,sites,jungles,mods,holeP90,holeMax,valid,nudges");
            for (int n : new int[] { 2, 4, 8 })
                for (int shape = 1; shape <= 2; shape++)
                    for (int k = 0; k < per; k++) {
                        long seed = GenSeed.pack(0x9A0D0000L + k * 104729L + n, shape, 0);
                        int[] teams = new int[n];
                        for (int i = 0; i < n; i++)
                            teams[i] = i < n / 2 ? 0 : 1;
                        long t0 = System.nanoTime();
                        MapLayout l = MapLayout.build(MapPreset.GENERATED, teams, seed);
                        double ms = (System.nanoTime() - t0) / 1e6;
                        GenMap m = l.gen;
                        WalkGrid g = new WalkGrid(m, true);
                        Fairness.Geometry geo = new Fairness.Geometry(m, g, Fairness.fields(m, g));
                        int within = 0, teamWithin = 0;
                        double[] sp = new double[draws.length], tsp = new double[draws.length];
                        for (int i = 0; i < draws.length; i++) {
                            Fairness.Report r = new Fairness.Context(geo, draws[i]).report(geo.rows(m.mexes));
                            sp[i] = r.spread();
                            tsp[i] = r.teamSpread();
                            if (sp[i] <= 0.05)
                                within++;
                            if (tsp[i] <= 0.05)
                                teamWithin++;
                        }
                        java.util.Arrays.sort(sp);
                        java.util.Arrays.sort(tsp);
                        double[] holes = GenExperiments.holes(m, g);
                        StringBuilder mods = new StringBuilder();
                        for (Modifier mo : m.modifiers)
                            mods.append(mods.length() > 0 ? "+" : "").append(mo.name());
                        w.printf(Locale.ROOT, "%d,%s,%d,%s,%.0f,%d,%.4f,%.4f,%.3f,%.4f,%.3f,%.4f,%d,%d,%d,%s,%.1f,%.1f,%s,%d%n",
                            n, m.shape, seed, m.contrast, ms, m.attempt, m.report.spread(), m.report.teamSpread(),
                            within / 40.0, sp[20], teamWithin / 40.0, tsp[20], m.mexes.size(), m.sites.size(),
                            m.jungle.size(), mods, holes[1], holes[2], m.valid, m.nudges);
                        w.flush();
                    }
        }
        System.out.println("done");
    }
}
