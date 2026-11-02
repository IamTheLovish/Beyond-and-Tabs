package com.solegendary.reignofnether.maps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verdant Valley's river banks (Oct 29, claude/environment-palette-oct29.md item 2). The sand bank used to be a fixed
 * ring 5-7 blocks out from the river's centre line; rasterised to blocks, a perfect ring is a staircase, which read
 * from the RTS camera as a sawtooth ribbon. The bank's outer edge now wanders with the terrain noise, while the water
 * itself (and so the crossings and the pathing across it) stays exactly where it was.
 */
class RiverBankEdgeTest {

    private static final int[] TEAMS = { 0, 0, 1, 1 };

    /** Outer edge of the bank at angle th: the first radius past the water that is neither sand nor water. */
    private static double outerEdge(MapLayout l, double th, MapLayout.Sample s) {
        double rv = l.riverR();
        for (double r = rv + 5; r < rv + 14; r += 0.25) {
            l.sample(r * Math.cos(th), r * Math.sin(th), s);
            if (s.surface != MapLayout.Surface.SAND && s.surface != MapLayout.Surface.WATER)
                return r - rv;
        }
        return 14;
    }

    @Test
    void theBankEdgeWandersInsteadOfFollowingARing() {
        for (long seed : new long[] { 0L, 42L }) {
            MapLayout l = MapLayout.build(MapPreset.VERDANT_VALLEY, TEAMS, seed);
            MapLayout.Sample s = new MapLayout.Sample();
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (int i = 0; i < 720; i++) {
                double e = outerEdge(l, i * Math.PI * 2 / 720, s);
                min = Math.min(min, e);
                max = Math.max(max, e);
            }
            System.out.printf("seed %d: bank outer edge %.2f .. %.2f blocks from the river centre%n", seed, min, max);
            assertTrue(max - min >= 2.0, "seed " + seed + ": the bank edge is still a ring (" + min + " .. " + max + ")");
            assertTrue(min >= 5.0, "seed " + seed + ": grass reaches into the water's band (" + min + ")");
            assertTrue(max <= 10.5, "seed " + seed + ": the bank eats the lawn (" + max + ")");
        }
    }

    @Test
    void theWaterIsWhereItWas() {
        MapLayout l = MapLayout.build(MapPreset.VERDANT_VALLEY, TEAMS, 0L);
        MapLayout.Sample s = new MapLayout.Sample();
        double rv = l.riverR();
        for (int i = 0; i < 720; i++) {
            double th = i * Math.PI * 2 / 720;
            for (double d = -4.75; d < 5; d += 0.5) {
                l.sample((rv + d) * Math.cos(th), (rv + d) * Math.sin(th), s);
                assertEquals(MapLayout.Surface.WATER, s.surface, "no water at " + d + " from the river centre");
            }
        }
    }
}
