package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.MetalOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Lab: render sheets of what the lobby draws (MapLayout.build(GENERATED) with packed seeds: the lobby balancer + fallback,
 * live modifiers), not cherry-picked: seeds 1..k in order. Usage: GenShowcase outDir
 */
public final class GenShowcase {
    static int[] teams(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++)
            t[i] = i < n / 2 ? 0 : 1;
        return t;
    }

    public static void main(String[] a) throws Exception {
        File out = new File(a[0]);
        out.mkdirs();
        for (int n : new int[] { 2, 8 }) {
            List<GenMap> maps = new ArrayList<>();
            for (int k = 1; k <= 8; k++) {
                int shape = k <= 4 ? 1 : 2;
                long seed = GenSeed.pack(0xBA70000L + k, shape, 0);
                maps.add(MapLayout.build(MapPreset.GENERATED, teams(n), seed).gen);
            }
            String title = (n == 2 ? "1v1" : "4v4") + " generated maps as the lobby draws them (seeds 1-8, round then square; balanced " + GenOptions.DEFAULT.balance() + ", live modifiers)";
            GenRender.sheetWithLegend(maps, 4, 640, title, new File(out, "generated-maps-" + (n == 2 ? "1v1" : "4v4") + ".png"));
        }
        // before / after balancing: the same draw raw and as shipped, 4v4
        List<GenMap> ba = new ArrayList<>();
        for (int k = 1; k <= 4; k++) {
            long seed = 0xBA70100L + k;
            GenOptions raw = GenOptions.DEFAULT.withShape(k % 2 == 0 ? GenOptions.Shape.SQUARE : GenOptions.Shape.ROUND)
                .withBalance(GenOptions.Balance.NONE);
            ba.add(MapGenerator.generate(teams(8), seed, raw, MetalOptions.DEFAULT));
            ba.add(MapGenerator.generate(teams(8), seed, raw.withBalance(GenOptions.DEFAULT.balance()), MetalOptions.DEFAULT));
        }
        GenRender.sheetWithLegend(ba, 4, 640, "4v4: the same seed raw (left) and balanced (right): swap seats/sides, then nudge metal", new File(out, "generated-maps-balancing.png"));
        System.out.println("done");
    }
}
