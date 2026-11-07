package com.solegendary.reignofnether.unit.goals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The flyer stand-off arithmetic (review r1 of #538): inside the 3D reach, and never NaN. */
class FlyerStandoffTest {

    /** The parked point's 3D distance to the block's top, from the flat stand-off and the height. */
    private static double reach3d(double flat, double up) {
        return Math.sqrt(flat * flat + up * up);
    }

    @Test
    void a_sky_corsair_parks_inside_its_3d_reach() {
        double flat = FlyerStandoff.flat(13, 10);   // range 13, cruising 10 up
        assertTrue(flat > 0, "flat " + flat);
        assertTrue(reach3d(flat, 10) < 13, "3D " + reach3d(flat, 10));
        assertTrue(flat < 10 - 3, "the old flat range - 3 (10) is what parked it out of reach; got " + flat);
    }

    @Test
    void a_low_flyer_parks_about_where_it_did() {
        double flat = FlyerStandoff.flat(12, 3);   // the Seraph Archer: range 12, 3 up
        assertTrue(reach3d(flat, 3) < 12, "3D " + reach3d(flat, 3));
        assertTrue(flat > 8.5 && flat < 10, "flat " + flat);
    }

    @Test
    void too_high_for_its_reach_means_the_block_itself_never_nan() {
        assertEquals(FlyerStandoff.BLOCK, FlyerStandoff.flat(8, 7));     // reach 6.5, 7 up
        assertEquals(FlyerStandoff.BLOCK, FlyerStandoff.flat(8, 6.5));   // exactly the reach
        assertEquals(FlyerStandoff.BLOCK, FlyerStandoff.flat(1, 0));     // no reach at all
        assertEquals(FlyerStandoff.BLOCK, FlyerStandoff.flat(13, Double.NaN));
        assertEquals(FlyerStandoff.BLOCK, FlyerStandoff.flat(Double.NaN, 3));
        for (double range = 0; range <= 40; range += 0.5)
            for (double up = -5; up <= 40; up += 0.5) {
                double flat = FlyerStandoff.flat(range, up);
                assertFalse(Double.isNaN(flat), "NaN at range " + range + " up " + up);
                assertTrue(flat == FlyerStandoff.BLOCK || flat >= FlyerStandoff.MIN_FLAT, "range " + range + " up " + up + ": " + flat);
                if (flat != FlyerStandoff.BLOCK)
                    assertTrue(reach3d(flat, Math.max(0, up)) < range, "range " + range + " up " + up + " parks out of reach");
            }
    }

    @Test
    void below_the_block_counts_as_level_with_it() {
        assertEquals(FlyerStandoff.flat(12, 0), FlyerStandoff.flat(12, -4));
    }
}
