package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MetalOptions;

/**
 * Lab: the mirror fold. "seam K": K mirror maps per (players, shape); share of fold columns (|dot| < 0.75) with a
 * height step of 2+ to a neighbour against a parallel control line (|dot| in [40, 41.5)), and columns that differ from
 * their point mirror. "hash K": a fingerprint of K non-mirror maps (heights, surfaces, patches), to show the change
 * leaves them alone.
 */
public final class GenSeam {
    public static void main(String[] a) {
        int k = Integer.parseInt(a[1]);
        if (a[0].equals("hash")) {
            long h = 1469598103934665603L;
            for (int n : new int[] { 2, 8 })
                for (int i = 0; i < k; i++) {
                    int[] teams = teams(n);
                    GenOptions o = new GenOptions(i % 2 == 0 ? GenOptions.Shape.ROUND : GenOptions.Shape.SQUARE,
                        GenOptions.Modifiers.ON, GenOptions.Contrast.AUTO, GenOptions.Balance.NONE, 0.05, true);
                    GenMap m = MapGenerator.generate(teams, 0x5EA40000L + i * 7919L + n, o, MetalOptions.DEFAULT);
                    for (byte b : m.height) h = (h ^ b) * 1099511628211L;
                    for (byte b : m.surface) h = (h ^ b) * 1099511628211L;
                    for (MapLayout.Mex x : m.mexes) h = (h ^ (x.dx() * 31L + x.dz())) * 1099511628211L;
                }
            System.out.printf("hash %016x%n", h);
            return;
        }
        for (int n : new int[] { 2, 8 })
            for (GenOptions.Shape sh : new GenOptions.Shape[] { GenOptions.Shape.ROUND, GenOptions.Shape.SQUARE }) {
                long fold = 0, foldJ = 0, ctl = 0, ctlJ = 0, diff = 0, cols = 0;
                for (int i = 0; i < k; i++) {
                    int[] teams = teams(n);
                    GenOptions o = new GenOptions(sh, GenOptions.Modifiers.ON, GenOptions.Contrast.MIRROR,
                        GenOptions.Balance.NONE, 0.05, true);
                    GenMap m = MapGenerator.generate(teams, 0x5EA50000L + i * 7919L + n, o, MetalOptions.DEFAULT);
                    double ax = 0, az = 0;
                    for (int s = 0; s < n; s++)
                        if (teams[s] == 0) {
                            ax += m.slots.get(s).dx();
                            az += m.slots.get(s).dz();
                        }
                    double al = Math.sqrt(ax * ax + az * az);
                    ax /= al;
                    az /= al;
                    int hf = m.half;
                    for (int x = -hf + 1; x < hf; x++)
                        for (int z = -hf + 1; z < hf; z++) {
                            if (m.distanceToEdge(x, z) < 4)
                                continue;
                            cols++;
                            int i0 = m.idx(x, z);
                            if (m.height[i0] != m.height[m.idx(-x, -z)])
                                diff++;
                            int step = 0;
                            for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                                step = Math.max(step, Math.abs(m.height[i0] - m.height[m.idx(x + d[0], z + d[1])]));
                            double dot = Math.abs(x * ax + z * az);
                            if (dot < 0.75) {
                                fold++;
                                if (step >= 2) foldJ++;
                            } else if (dot >= 40 && dot < 41.5) {
                                ctl++;
                                if (step >= 2) ctlJ++;
                            }
                        }
                }
                System.out.printf("n=%d %s: fold %d/%d (%.1f%%) control %d/%d (%.1f%%) unmirrored %d/%d%n", n, sh, foldJ,
                    fold, 100.0 * foldJ / fold, ctlJ, ctl, 100.0 * ctlJ / ctl, diff, cols);
            }
    }

    static int[] teams(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++)
            t[i] = i < n / 2 ? 0 : 1;
        return t;
    }
}
