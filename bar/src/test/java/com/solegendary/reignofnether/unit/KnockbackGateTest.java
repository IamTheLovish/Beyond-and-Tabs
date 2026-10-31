package com.solegendary.reignofnether.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KnockbackGate's rule (gain 5) from its arithmetic alone; KnockbackGameTests checks it in the game. */
class KnockbackGateTest {

    static final int V = 7, A = 11, B = 12, N = KnockbackGate.NO_HITTER;

    @Test
    void oneLightShovePerFiveTicks() {
        KnockbackGate g = new KnockbackGate();
        assertTrue(g.allow(V, 100, 0.4, A));
        assertFalse(g.allow(V, 100, 0.4, B));
        assertFalse(g.allow(V, 104, 0.4, A));
        assertTrue(g.allow(V, 105, 0.4, B));
    }

    @Test
    void eachVictimHasItsOwnWindow() {
        KnockbackGate g = new KnockbackGate();
        assertTrue(g.allow(V, 100, 0.4, A));
        assertTrue(g.allow(V + 1, 100, 0.4, A));
    }

    @Test
    void theStrongestWins() {
        KnockbackGate g = new KnockbackGate();
        assertTrue(g.allow(V, 100, 0.2, A));
        assertTrue(g.allow(V, 102, 0.4, B));
        assertFalse(g.allow(V, 103, 0.3, A));
        assertFalse(g.allow(V, 103, 0.4, A));   // equal is not stronger
    }

    @Test
    void heavyShovesAlwaysLand() {
        KnockbackGate g = new KnockbackGate();
        assertTrue(g.allow(V, 100, 0.4, A));
        assertTrue(g.allow(V, 100, 1.5, B));
        assertTrue(g.allow(V, 101, 0.5, B));
        assertFalse(g.allow(V, 102, 0.45, A));   // a light one after a heavy one: the heavy one was stronger
    }

    @Test
    void oneBlowKeepsBothItsShoves() {
        KnockbackGate g = new KnockbackGate();
        assertTrue(g.allow(V, 100, 0.4, A));    // the hurt shove
        assertTrue(g.allow(V, 100, 0.25, A));   // the same blow's attack knockback part
        assertFalse(g.allow(V, 101, 0.25, A));  // a tick later it is another blow
    }

    @Test
    void shovesWithNoHitterAreNeverOneBlow() {
        KnockbackGate g = new KnockbackGate();
        assertTrue(g.allow(V, 100, 0.4, N));
        assertFalse(g.allow(V, 100, 0.4, N));
    }

    @Test
    void aTickBeforeTheWindowClosesIt() {
        KnockbackGate g = new KnockbackGate();
        assertTrue(g.allow(V, 1000, 0.4, A));
        assertTrue(g.allow(V, 3, 0.4, B));   // a new world's tick count started again
    }

    @Test
    void sweepDropsClosedWindowsOnly() {
        KnockbackGate g = new KnockbackGate();
        g.allow(V, 100, 0.4, A);
        g.allow(V + 1, 103, 0.4, A);
        g.sweep(105);
        assertEquals(1, g.size());
        assertFalse(g.allow(V + 1, 105, 0.4, B));
        g.sweep(108);
        assertEquals(0, g.size());
    }
}
