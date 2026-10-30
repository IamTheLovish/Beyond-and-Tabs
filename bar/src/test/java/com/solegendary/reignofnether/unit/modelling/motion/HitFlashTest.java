package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hit reads as a hit (combat feel gain 3): a white flash sized by the blow, over in 3 ticks, then 4 ticks of red
 * instead of vanilla's 10. hurtTime is vanilla's: 10 as the damage event arrives, 9 by the first frame after a tick.
 */
class HitFlashTest {

    @Test
    void aChipFlickersABigBlowPops() {
        float chip = HitFlash.white(9, 0f, 0.02f), big = HitFlash.white(9, 0f, 0.3f);
        assertEquals(1f, big, 1e-6f, "a quarter of its health or more: the full flash");
        assertTrue(chip > 0f && chip < 0.35f, "5 damage on a 250-health Mammoth: a flicker, " + chip);
        assertTrue(HitFlash.white(9, 0f, 0.1f) > chip && HitFlash.white(9, 0f, 0.1f) < big, "the flash grows with the blow");
    }

    @Test
    void aBlowOfUnknownSizeStillFlashes() {
        // the health the server syncs can come a frame after the damage event, and healing can outrun the blow
        assertEquals(HitFlash.MIN_STRENGTH, HitFlash.white(9, 0f, 0f), 1e-6f);
        assertEquals(HitFlash.MIN_STRENGTH, HitFlash.white(9, 0f, -0.1f), 1e-6f);
        assertEquals(HitFlash.MIN_STRENGTH, HitFlash.white(9, 0f, Float.NaN), 1e-6f);
    }

    @Test
    void theFrameBeforeTheFirstTickIsTheHitItself() {
        for (float p = 0f; p < 1f; p += 0.25f)
            assertEquals(1f, HitFlash.white(10, p, 0.5f), 1e-6f, "partial " + p);
        assertFalse(HitFlash.red(10, 0.5f));
    }

    @Test
    void theWhiteFadesAndIsOverByThreeTicks() {
        float last = 2f;
        for (int hurt = 10; hurt >= 1; hurt--)
            for (float p = 0f; p < 1f; p += 0.1f) {
                float w = HitFlash.white(hurt, p, 0.5f);
                assertTrue(w <= last, "never brightens again, hurtTime " + hurt + " partial " + p);
                if (HitFlash.sinceHit(hurt, p) >= 3f)
                    assertEquals(0f, w, "over by 3 ticks, hurtTime " + hurt + " partial " + p);
                last = w;
            }
        assertTrue(HitFlash.white(8, 0.5f, 0.5f) > 0f, "still showing a tick and a half in");
    }

    @Test
    void theRedIsShortAndComesAfterTheWhite() {
        int redTicks = 0;
        for (int hurt = 10; hurt >= 0; hurt--) {
            boolean red = HitFlash.red(hurt, 0f);
            if (red)
                redTicks++;
            for (float p = 0f; p < 1f; p += 0.1f)
                assertFalse(HitFlash.red(hurt, p) && HitFlash.white(hurt, p, 1f) > 0f,
                    "never white and red at once, hurtTime " + hurt + " partial " + p);
        }
        assertEquals(4, redTicks, "4 ticks of red, not vanilla's 10");
        assertTrue(HitFlash.red(6, 0f), "red as the white ends");
        assertFalse(HitFlash.red(2, 0f), "gone 7 ticks after the hit");
    }

    @Test
    void noHitNoOverlay() {
        assertEquals(0f, HitFlash.white(0, 0.5f, 1f));
        assertFalse(HitFlash.red(0, 0.5f));
    }
}
