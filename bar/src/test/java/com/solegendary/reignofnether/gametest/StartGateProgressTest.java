package com.solegendary.reignofnether.gametest;

import org.junit.jupiter.api.Test;

import static com.solegendary.reignofnether.gametest.StartGateProgress.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;

/** The start gate's wait rule (StartGateProgress), with made-up readings - no Minecraft needed. */
class StartGateProgressTest {

    static final long TICK = 50_000_000L;   // 20 ticks per second

    @Test
    void anArenaReadyAtOnceStartsAtOnce() {
        StartGateProgress p = new StartGateProgress();
        assertEquals(READY, p.update(9, 9, 0));
        assertEquals(0, p.ticksHeld());
    }

    @Test
    void holdsWhileChunksStillBecomeReadyThenStarts() {
        StartGateProgress p = new StartGateProgress();
        long t = 0;
        for (int ready = 0; ready < 9; ready++) {
            // one more chunk every 150 ticks: under the 200-tick stall, so it never gives up
            for (int i = 0; i < 150; i++, t += TICK)
                assertEquals(HOLD, p.update(ready, 9, t), "ready " + ready + " tick " + i);
        }
        assertEquals(READY, p.update(9, 9, t));
        assertEquals(9 * 150, p.ticksHeld());
    }

    @Test
    void anUnthrottledServerDoesNotGiveUpBeforeTheWallTimeHasPassed() {
        // the GameTest server ticks ~1000-6700 times a second: 200 ticks in 0.1 s is not a stall
        StartGateProgress p = new StartGateProgress();
        long t = 0;
        for (int i = 0; i < 5000; i++, t += 100_000L)   // 0.1 ms a tick: 5000 ticks in 0.5 s
            assertEquals(HOLD, p.update(3, 9, t), "tick " + i);
    }

    @Test
    void givesUpOnlyAfterBothTheTicksAndTheirWallTimeWithoutProgress() {
        StartGateProgress p = new StartGateProgress();
        long t = 0;
        StartGateProgress.Verdict v = HOLD;
        int i = 0;
        for (; v == HOLD && i < 10_000; i++, t += TICK)
            v = p.update(4, 9, t);
        assertEquals(STALLED, v);
        // tick 200 is the first with 200 ticks AND 200 ticks' wall time (10 s) since the last progress
        assertEquals(StartGateProgress.STALL_TICKS + 1, p.ticksHeld());
        assertTrue(p.millisSince(t) >= StartGateProgress.STALL_TICKS * 50L - 50, "waited " + p.millisSince(t) + " ms");
    }

    @Test
    void aSlowButProgressingWaitEndsAtTheWallClockCap() {
        StartGateProgress p = new StartGateProgress();
        long t = 0, step = 1_000_000_000L;   // one tick a second, a new ready chunk each tick: never stalled
        StartGateProgress.Verdict v = HOLD;
        int ready = 0;
        while (v == HOLD && t <= StartGateProgress.CAP_NANOS + step) {
            v = p.update(ready++, 10_000, t);
            t += step;
        }
        assertEquals(CAPPED, v);
    }
}
