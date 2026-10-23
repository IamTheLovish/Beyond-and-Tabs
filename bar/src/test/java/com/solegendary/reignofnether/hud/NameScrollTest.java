package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A long building name scrolls instead of hiding under the production queue: start, scroll, hold the end, loop. */
class NameScrollTest {

    @Test
    void aNameThatFitsNeverMoves() {
        for (long t = 0; t < 10_000; t += 250)
            assertEquals(0, NameScroll.offset(80, 80, t));
    }

    @Test
    void holdsAtTheStartThenScrollsToShowTheEnd() {
        // 40 px too wide: 1.5 s hold, 2 s scroll at 20 px/s, 1.5 s hold on the end
        assertEquals(0, NameScroll.offset(140, 100, 0));
        assertEquals(0, NameScroll.offset(140, 100, 1499));
        assertEquals(20, NameScroll.offset(140, 100, 2500));
        assertEquals(40, NameScroll.offset(140, 100, 3500));
        assertEquals(40, NameScroll.offset(140, 100, 4999));
        assertEquals(0, NameScroll.offset(140, 100, 5000));   // loops back to the start
    }

    @Test
    void neverShiftsPastTheEndOrBeforeTheStart() {
        for (long t = -7_000; t < 20_000; t += 37) {
            int o = NameScroll.offset(300, 120, t);
            assertTrue(o >= 0 && o <= 180, "offset " + o + " at " + t);
        }
    }
}
