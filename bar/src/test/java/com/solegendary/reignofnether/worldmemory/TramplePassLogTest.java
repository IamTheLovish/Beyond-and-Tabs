package com.solegendary.reignofnether.worldmemory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trample cost GameTest's measure: the median of the passes' cost per walk. It must not hide a real cost (every
 * pass dearer moves it) and must not be decided by one stalled pass (a GC pause moves only that pass).
 */
class TramplePassLogTest {

    /** 36 passes of 44 walks at {@code usPerWalk} each (CPU and wall alike). */
    private static TramplePassLog steady(double usPerWalk) {
        TramplePassLog log = new TramplePassLog(64);
        for (int i = 0; i < 36; i++) {
            long nanos = Math.round(usPerWalk * 1e3 * 44);
            log.add(44, nanos, nanos);
        }
        return log;
    }

    @Test
    void one_stalled_pass_moves_the_old_number_but_not_the_median() {
        TramplePassLog log = steady(1.5);
        log.add(44, 20_000_000L, 200_000L);   // a 20 ms GC pause inside one pass; the thread's own CPU barely moved
        assertEquals(1.5, log.percentileUsPerWalk(0.5), 1e-9);
        assertTrue(log.wholeWindowWallUsPerWalk() > 3.0, "the whole-window wall number fails on that pause alone: "
            + log.wholeWindowWallUsPerWalk());
    }

    @Test
    void a_real_cost_on_every_pass_moves_the_median() {
        assertTrue(steady(1.5).percentileUsPerWalk(0.5) <= 3.0);
        assertTrue(steady(1.5 + 5.0).percentileUsPerWalk(0.5) > 3.0, "5 us more a walk on every pass must fail the 3.0 budget");
        assertTrue(steady(3.2).percentileUsPerWalk(0.5) > 3.0, "a cost just over the budget fails");
    }

    @Test
    void a_cost_on_more_than_half_the_passes_moves_the_median() {
        TramplePassLog log = new TramplePassLog(64);
        for (int i = 0; i < 36; i++) {
            long nanos = Math.round((i < 19 ? 6.0 : 1.0) * 1e3 * 44);
            log.add(44, nanos, nanos);
        }
        assertTrue(log.percentileUsPerWalk(0.5) > 3.0);
    }

    @Test
    void passes_without_walks_are_left_out_and_p90_is_reported() {
        TramplePassLog log = new TramplePassLog(64);
        log.add(0, 50_000, 50_000);   // the turn-around: nobody walked
        for (int i = 1; i <= 10; i++)
            log.add(10, i * 10_000L, i * 10_000L);   // 1..10 us a walk
        assertEquals(5.0, log.percentileUsPerWalk(0.5), 1e-9);
        assertEquals(9.0, log.percentileUsPerWalk(0.9), 1e-9);
        assertEquals(10.0, log.percentileUsPerWalk(1.0), 1e-9);
    }

    @Test
    void wall_time_when_any_pass_lacks_cpu_time() {
        TramplePassLog log = new TramplePassLog(8);
        log.add(10, 40_000, 10_000);
        assertTrue(log.cpuTime());
        assertEquals(1.0, log.percentileUsPerWalk(0.5), 1e-9);
        log.add(10, 40_000, -1);
        assertFalse(log.cpuTime());
        assertEquals(4.0, log.percentileUsPerWalk(0.5), 1e-9);
    }

    @Test
    void no_walks_is_not_a_number_and_a_full_log_ignores_more() {
        TramplePassLog log = new TramplePassLog(2);
        assertTrue(Double.isNaN(log.percentileUsPerWalk(0.5)));
        assertTrue(Double.isNaN(log.wholeWindowWallUsPerWalk()));
        log.add(1, 1_000, 1_000);
        log.add(1, 1_000, 1_000);
        log.add(1, 99_000, 99_000);
        assertEquals(2, log.passes());
        assertEquals(1.0, log.percentileUsPerWalk(1.0), 1e-9);
    }
}
