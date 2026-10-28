package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Lab: how the random metal spread lands round the starts (lovish Oct 27), on the lobby's maps. Per map: the fallback
 * attempt (100+ means the first draw was refused, by the reach rule or the tolerance), validity and why, and per seat
 * the walk to its nearest patch and its metal within MapGenerator.REACH_WALK - the worst seat's nearest walk, the best,
 * and the poorest seat's reach as a share of the richest's. Usage: GenReachStats out.csv seedsPerConfig
 */
public final class GenReachStats {
    public static void main(String[] a) throws Exception {
        int per = Integer.parseInt(a[1]);
        try (PrintWriter w = new PrintWriter(new FileWriter(a[0]))) {
            w.println("n,shape,seed,attempt,valid,why,nearMin,nearMax,reachRatio,patches,spread");
            for (int n : new int[] { 2, 4, 8 })
                for (int shape = 1; shape <= 2; shape++)
                    for (int k = 0; k < per; k++) {
                        long seed = GenSeed.pack(0x9A0D0000L + k * 104729L + n, shape, 0);
                        int[] teams = new int[n];
                        for (int i = 0; i < n; i++)
                            teams[i] = i < n / 2 ? 0 : 1;
                        MapLayout l = MapLayout.build(MapPreset.GENERATED, teams, seed);
                        GenMap m = l.gen;
                        WalkGrid g = new WalkGrid(m, true);
                        int[][] dist = Fairness.fields(m, g);
                        double[] near = new double[n], reach = new double[n];
                        java.util.Arrays.fill(near, Double.POSITIVE_INFINITY);
                        for (MapLayout.Mex q : m.mexes)
                            for (int s = 0; s < n; s++) {
                                double d = g.blocks(dist[s], q.dx(), q.dz());
                                near[s] = Math.min(near[s], d);
                                if (d <= MapGenerator.REACH_WALK)
                                    reach[s] += q.value();
                            }
                        double nMin = Double.MAX_VALUE, nMax = 0, rMin = Double.MAX_VALUE, rMax = 0;
                        for (int s = 0; s < n; s++) {
                            nMin = Math.min(nMin, near[s]);
                            nMax = Math.max(nMax, near[s]);
                            rMin = Math.min(rMin, reach[s]);
                            rMax = Math.max(rMax, reach[s]);
                        }
                        w.printf(Locale.ROOT, "%d,%s,%d,%d,%s,\"%s\",%.0f,%.0f,%.3f,%d,%.4f%n", n, m.shape, seed, m.attempt,
                            m.valid, m.invalidWhy, nMin, nMax, rMax > 0 ? rMin / rMax : 0, m.mexes.size(), m.report.spread());
                        w.flush();
                    }
        }
        System.out.println("done");
    }
}
