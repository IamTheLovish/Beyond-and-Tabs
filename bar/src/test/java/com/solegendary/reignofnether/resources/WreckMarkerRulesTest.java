package com.solegendary.reignofnether.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Readable wrecks (lovish, Oct 24: winning a fight should pay, and "players don't notice" the wrecks): in the RTS view
 * every known wreck carries a compact metal marker, not only the hovered one with a worker selected. The frame cost
 * is bounded by one pure rule - which wrecks get a marker (in range, in the frustum and the fog's light, richest
 * first, at most MAX_MARKERS) - written from that requirement before WreckClientEvents drew anything.
 */
class WreckMarkerRulesTest {

    private static final double R2 = WreckMarkerRules.RANGE * WreckMarkerRules.RANGE;

    @Test
    void tiersSeparateScrapFromBigHeapsAndCommanderWrecks() {
        assertEquals(WreckMarkerRules.Tier.SMALL, WreckMarkerRules.tier(15));
        assertEquals(WreckMarkerRules.Tier.SMALL, WreckMarkerRules.tier(WreckMarkerRules.BIG_METAL - 1));
        assertEquals(WreckMarkerRules.Tier.BIG, WreckMarkerRules.tier(WreckMarkerRules.BIG_METAL));
        assertEquals(WreckMarkerRules.Tier.BIG, WreckMarkerRules.tier(WreckMarkerRules.HUGE_METAL - 1));
        assertEquals(WreckMarkerRules.Tier.HUGE, WreckMarkerRules.tier(WreckMarkerRules.HUGE_METAL));
        assertEquals(WreckMarkerRules.Tier.HUGE, WreckMarkerRules.tier((int) WreckServerEvents.COMMANDER_WRECK_METAL),
            "a commander's wreck must read as the biggest kind of heap");
    }

    @Test
    void biggerHeapsDrawBiggerMarkers() {
        float small = WreckMarkerRules.sizeMult(WreckMarkerRules.Tier.SMALL);
        float big = WreckMarkerRules.sizeMult(WreckMarkerRules.Tier.BIG);
        float huge = WreckMarkerRules.sizeMult(WreckMarkerRules.Tier.HUGE);
        assertEquals(1f, small, 1e-6f);
        assertTrue(big > small && huge > big, "marker size must grow with the tier: " + small + " " + big + " " + huge);
    }

    @Test
    void fadeIsFullNearAndNeverBelowTheFloorInsideTheRange() {
        assertEquals(1f, WreckMarkerRules.fade(0), 1e-6f);
        assertEquals(1f, WreckMarkerRules.fade(WreckMarkerRules.FADE_START), 1e-6f);
        float last = 1f;
        for (double d = 0; d <= WreckMarkerRules.RANGE; d += 5) {
            float f = WreckMarkerRules.fade(d);
            assertTrue(f <= last + 1e-6f, "fade rose with distance at " + d);
            assertTrue(f >= WreckMarkerRules.MIN_FADE - 1e-6f, "faded below the readable floor at " + d + ": " + f);
            last = f;
        }
        assertEquals(WreckMarkerRules.MIN_FADE, WreckMarkerRules.fade(WreckMarkerRules.RANGE), 1e-5f);
    }

    @Test
    void compactLabelIsJustTheNumberForScrapAndHasTheReclaimMarkForBigHeaps() {
        assertEquals("25", WreckMarkerRules.label(25));
        assertEquals("149", WreckMarkerRules.label(149));
        assertTrue(WreckMarkerRules.label(150).endsWith("150") && WreckMarkerRules.label(150).length() > 3,
            "a big heap carries the reclaim mark: " + WreckMarkerRules.label(150));
        assertTrue(WreckMarkerRules.label(600).endsWith("600"));
        assertFalse(WreckMarkerRules.label(600).toLowerCase().contains("metal"), "the compact marker has no unit word");
    }

    @Test
    void chooseDropsWhatIsTooFarOffScreenFoggedOrEmpty() {
        double[] d2 = { 100, R2 + 1, 100, 100, R2 };
        int[] metal = { 50, 50, 50, 0, 50 };
        boolean[] view = { true, true, false, true, true };
        int[] out = new int[5];
        int n = WreckMarkerRules.choose(5, d2, metal, view, 10, out);
        assertEquals(2, n, "only #0 (all good) and #4 (exactly at the range edge) qualify");
        assertEquals(0, out[0]);
        assertEquals(4, out[1]);
    }

    @Test
    void chooseKeepsTheRichestWhenOverTheCap() {
        int total = 20;
        double[] d2 = new double[total];
        int[] metal = new int[total];
        boolean[] view = new boolean[total];
        for (int i = 0; i < total; i++) {
            d2[i] = 100 + i;
            metal[i] = 30 + i;   // #19 is the richest
            view[i] = true;
        }
        metal[7] = (int) WreckServerEvents.COMMANDER_WRECK_METAL;   // the commander wreck among scrap
        int[] out = new int[total];
        int n = WreckMarkerRules.choose(total, d2, metal, view, 5, out);
        assertEquals(5, n, "never more than the cap");
        assertEquals(7, out[0], "the commander's wreck comes first");
        assertEquals(19, out[1]);
        assertEquals(18, out[2]);
        assertEquals(17, out[3]);
        assertEquals(16, out[4]);
    }

    @Test
    void chooseBreaksTiesByDistance() {
        double[] d2 = { 900, 100, 400 };
        int[] metal = { 40, 40, 40 };
        boolean[] view = { true, true, true };
        int[] out = new int[3];
        int n = WreckMarkerRules.choose(3, d2, metal, view, 2, out);
        assertEquals(2, n);
        assertEquals(1, out[0], "the nearest of equals first");
        assertEquals(2, out[1]);
    }

    @Test
    void chooseUnderTheCapKeepsEverythingEligibleAndHandlesNothing() {
        assertEquals(0, WreckMarkerRules.choose(0, new double[0], new int[0], new boolean[0], 10, new int[0]));
        double[] d2 = { 10, 20, 30 };
        int[] metal = { 5, 5, 5 };
        boolean[] view = { true, true, true };
        int[] out = new int[3];
        assertEquals(3, WreckMarkerRules.choose(3, d2, metal, view, WreckMarkerRules.MAX_MARKERS, out));
    }

    @Test
    void theCapCoversTheMostWrecksTheServerKeeps() {
        // MAX_WRECKS (150) bound the whole list; a marker for each would be a frame cost at 8v8, so the cap is
        // well under it - but still enough for a battlefield's worth of heaps
        assertTrue(WreckMarkerRules.MAX_MARKERS < WreckServerEvents.MAX_WRECKS);
        assertTrue(WreckMarkerRules.MAX_MARKERS >= 32);
    }
}
