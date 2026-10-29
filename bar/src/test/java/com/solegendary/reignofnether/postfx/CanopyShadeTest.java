package com.solegendary.reignofnether.postfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Canopy shade (critic r40, Oct 29: "plain lane: trees float"; target lawn 15-20% darker in a ~2-block ring under
 * crowns). Written before the mixin hook.
 */
class CanopyShadeTest {

    private static final int GRASS = 0x91BD59;

    /** A round crown of radius 3 over (0, 0), its leaves 5 blocks over the ground at y = 64. */
    private static final CanopyShade.Canopy CROWN = (x, y, z) -> x * x + z * z <= 9;

    private static double luma(int c) {
        return 0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF);
    }

    @Test
    void openLawnIsUntouched() {
        assertEquals(GRASS, CanopyShade.apply(GRASS, (x, y, z) -> false, 10, 64, 10));
    }

    @Test
    void underTheCrownIsFifteenToTwentyPercentDarker() {
        double k = luma(CanopyShade.apply(GRASS, CROWN, 0, 64, 0)) / luma(GRASS);
        assertTrue(k >= 0.79 && k <= 0.85, "under the crown " + k);
    }

    @Test
    void theEdgeFadesOverTwoBlocks() {
        double prev = 0;
        for (int x = 0; x <= 6; x++) {
            double k = luma(CanopyShade.apply(GRASS, CROWN, x, 64, 0)) / luma(GRASS);
            assertTrue(k >= prev - 1e-9, "darker going out at x " + x);
            prev = k;
        }
        double rim = luma(CanopyShade.apply(GRASS, CROWN, 4, 64, 0)) / luma(GRASS);
        assertTrue(rim > 0.82 && rim < 0.99, "a block outside the crown is a little dark " + rim);
        assertEquals(GRASS, CanopyShade.apply(GRASS, CROWN, 6, 64, 0), "three blocks out: open lawn");
    }

    @Test
    void thePoolIsRoundNotADiamond() {
        // eyes r42: the axis-only taps drew a diamond - a block on the diagonal got less shade than one as far out on
        // an axis. Two blocks out on the diagonal (2.8) and three on the axis (3) must be within a few percent
        double diag = luma(CanopyShade.apply(GRASS, CROWN, 2, 64, 2)), axis = luma(CanopyShade.apply(GRASS, CROWN, 3, 64, 0));
        assertTrue(Math.abs(diag - axis) / luma(GRASS) < 0.04, "diagonal " + diag + " axis " + axis);
    }

    @Test
    void theHueIsKept() {
        int c = CanopyShade.apply(GRASS, CROWN, 0, 64, 0);
        double ratio = ((c >> 8) & 0xFF) / (double) ((c >> 16) & 0xFF);
        assertEquals(0xBD / (double) 0x91, ratio, 0.02);
    }

    @Test
    void onlyLeavesWithinReachAboveCount() {
        assertTrue(CanopyShade.leavesOver(70, true, 64));        // the top block, y 69, is a leaf 5 over
        assertFalse(CanopyShade.leavesOver(70, false, 64));      // a log or a roof is not a canopy
        assertFalse(CanopyShade.leavesOver(65, true, 64));       // the ground's own block
        assertFalse(CanopyShade.leavesOver(64 + CanopyShade.REACH + 2, true, 64));   // a canopy far overhead
    }
}
