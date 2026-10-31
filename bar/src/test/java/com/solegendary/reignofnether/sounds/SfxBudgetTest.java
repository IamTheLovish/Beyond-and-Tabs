package com.solegendary.reignofnether.sounds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod Sfx throttles (SfxBudget): per-sound gaps, a budget per category per tick, the busy rate - and the tick they
 * count in, which must run with real time while the level's game time (the time of day on the client) stands still.
 */
class SfxBudgetTest {

    private static final int UNITS = 0;

    private static SfxBudget units() {
        return new SfxBudget(64, new int[] { 3 });
    }

    /** Asks for `n` different sounds (no gap) at `now`, near; returns how many were let through. */
    private static int ask(SfxBudget b, long now, int firstSound, int n) {
        b.roll(now);
        int played = 0;
        for (int i = 0; i < n; i++)
            if (b.admit((firstSound + i) % 64, UNITS, 0, now, false))
                played++;
        return played;
    }

    @Test
    void aFrozenTickAdmitsTheBudgetAndThenNothing() {
        // why a clock that stands still silences a category: its budget is never renewed
        SfxBudget b = units();
        int played = 0;
        for (int i = 0; i < 60; i++)
            played += ask(b, 500, i * 3, 3);
        assertEquals(3, played);
    }

    @Test
    void anAdvancingTickRenewsTheBudgetEveryTick() {
        SfxBudget b = units();
        for (long t = 1; t <= 20; t++)
            assertEquals(3, ask(b, t, (int) t * 5, 5), "tick " + t);
    }

    @Test
    void aSoundWaitsOutItsGap() {
        SfxBudget b = units();
        b.roll(10);
        assertTrue(b.admit(7, UNITS, 2, 10, false));
        b.roll(11);
        assertFalse(b.admit(7, UNITS, 2, 11, false), "a tick later: inside its 2-tick gap");
        b.roll(12);
        assertTrue(b.admit(7, UNITS, 2, 12, false), "two ticks later: again");
        assertTrue(b.admitGapOnly(9, 2, 12));
        assertFalse(b.admitGapOnly(9, 2, 13));
    }

    @Test
    void theBusyRateDecaysByATenthEachTick() {
        SfxBudget b = units();
        ask(b, 1, 0, 10);                     // 10 asked in tick 1
        b.roll(2);
        assertEquals(1.0f, b.busy(UNITS), 1e-5f, "a tenth of what was asked");
        b.roll(5);                            // three ticks on, nothing asked
        assertEquals(0.729f, b.busy(UNITS), 1e-5f, "x0.9 per tick elapsed");
    }

    @Test
    void aBusyCategorySkipsTheFarHalfAndPlaysQuieter() {
        SfxBudget b = units();
        for (long t = 1; t <= 30; t++)
            ask(b, t, (int) t, 40);          // 40 asked a tick: busy settles near 40
        b.roll(31);
        assertTrue(b.busy(UNITS) > SfxBudget.CALM_RATE * 2);
        assertFalse(b.admit(50, UNITS, 0, 31, true), "far: skipped while busy");
        assertTrue(b.admit(51, UNITS, 0, 31, false), "near: still plays");
        assertTrue(b.crowdScale(UNITS) < 0.25f, "and quieter");
        assertEquals(1f, units().crowdScale(UNITS));
    }

    @Test
    void aFrozenDayStillRenewsTheBudgetEveryRealTick() {
        // the client's game time is the time of day; with the day standing still (cycle Off, before the match...) it
        // is the same value for minutes. Counted in it, 60 real ticks let 3 unit sounds through; counted in real time,
        // 3 a tick
        SfxBudget b = units();
        int played = 0;
        for (int t = 0; t < 60; t++)
            played += ask(b, SfxBudget.clock(500, 1_000_000L + t * 50L), t * 3, 3);
        assertEquals(180, played);
    }

    @Test
    void theClockIsRealTicks() {
        assertEquals(SoundTicks.of(1_000_050L) - SoundTicks.of(1_000_000L), 1);
        assertEquals(SfxBudget.clock(500, 1_000_000L), SfxBudget.clock(123_456, 1_000_000L), "the game time is ignored");
        assertEquals(20, SfxBudget.clock(0, 2_000_000L) - SfxBudget.clock(0, 1_999_000L), "20 a second");
    }
}
