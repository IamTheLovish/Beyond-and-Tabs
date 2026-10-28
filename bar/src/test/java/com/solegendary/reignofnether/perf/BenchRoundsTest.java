package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BenchRounds gates a wall-clock budget on the best of several rounds (each a median of frames), so a burst of
 * runner noise does not fail a bench while a real regression - every round slower - still does. Oct 27:
 * AnimationCostBench.livingArmyTick failed on #146's build at 1.41 us/unit against its 1.0 budget, one median of 30
 * frames taken in ~20 ms on a shared CI runner.
 */
class BenchRoundsTest {

    @Test
    void one_noisy_round_does_not_decide_the_result() {
        // rounds 0, 1, 3.. take 1000 ns a frame; round 2 (a neighbour job hogging the core) takes 3000
        BenchRounds.Result r = BenchRounds.measure(5, 10, 30, 0, (round, frame) -> round == 2 ? 3000 : 1000);
        assertEquals(1000.0e-6, r.bestMs(), 1e-12);
        assertEquals(3000.0e-6, r.roundMedianMs()[2], 1e-12);
    }

    @Test
    void a_real_regression_slows_every_round_and_is_not_hidden() {
        BenchRounds.Result r = BenchRounds.measure(5, 10, 30, 0, (round, frame) -> 1500 + round * 10);
        assertEquals(1500.0e-6, r.bestMs(), 1e-12);
    }

    @Test
    void spikes_inside_a_round_are_dropped_by_its_median() {
        // a GC pause or a JIT recompile every 7th frame
        BenchRounds.Result r = BenchRounds.measure(1, 0, 30, 0, (round, frame) -> frame % 7 == 0 ? 50_000 : 1000);
        assertEquals(1000.0e-6, r.bestMs(), 1e-12);
    }

    @Test
    void warmup_frames_are_run_but_not_counted() {
        int[] calls = { 0 };
        BenchRounds.Result r = BenchRounds.measure(2, 25, 30, 0, (round, frame) -> {
            calls[0]++;
            return round < 0 ? 1_000_000 : 1000;   // the warm-up (round -1) is slow, as a cold JIT is
        });
        assertEquals(25 + 2 * 30, calls[0]);
        assertEquals(1000.0e-6, r.bestMs(), 1e-12);
    }

    @Test
    void the_harness_itself_allocates_nothing_per_frame() {
        // the zero-allocation gates sit on top of it: any byte the harness spent would read as the bench code's
        BenchRounds.Result r = BenchRounds.measure(7, 300, 30, 0, (round, frame) -> frame);
        assertEquals(0, r.quietestRoundBytes(), r.bytesText());
        for (long b : r.roundBytes())
            assertEquals(0, b, r.bytesText());
    }

    @Test
    void a_one_off_allocation_shows_in_its_round_but_not_in_the_quietest() {
        // Oct 27, #293's first build: OSR of the frame's inner loop compiling mid-measurement allocated one ~4 KB array
        // ("4000 bytes allocated"); under -Xint, C1 only or -XX:-UseOnStackReplacement every round reads 0
        Object[] keep = new Object[1];
        BenchRounds.Result r = BenchRounds.measure(5, 0, 30, 0, (round, frame) -> {
            if (round == 3 && frame == 10)
                keep[0] = new long[500];
            return 1;
        });
        assertTrue(r.roundBytes()[3] >= 500 * 8, r.bytesText());
        assertEquals(0, r.quietestRoundBytes(), r.bytesText());
    }

    @Test
    void a_one_off_allocation_passes_the_gate() {
        Object[] keep = new Object[1];
        BenchRounds.Result r = BenchRounds.measure(7, 0, 30, 0, (round, frame) -> {
            if (round == 2 && frame == 0)
                keep[0] = new long[500];
            return 1;
        });
        assertTrue(r.allocationFree(), r.bytesText());
    }

    @Test
    void an_allocation_every_45_frames_fails_the_gate() {
        // a cache rebuilt every N ticks, 30 < N <= 210: it misses some 30-frame rounds, so "the quietest round is
        // clean" would pass it, where the old single 210-frame sum caught it (round-3 review of #293)
        Object[] keep = new Object[1];
        BenchRounds.Result r = BenchRounds.measure(7, 0, 30, 0, (round, frame) -> {
            if ((round * 30 + frame) % 45 == 44)
                keep[0] = new long[64];
            return 1;
        });
        assertFalse(r.allocationFree(), r.bytesText());
    }

    @Test
    void an_allocation_every_frame_shows_in_every_round() {
        Object[] keep = new Object[1];
        BenchRounds.Result r = BenchRounds.measure(5, 0, 30, 0, (round, frame) -> {
            keep[0] = new long[8];
            return 1;
        });
        assertTrue(r.quietestRoundBytes() >= 30 * 8 * 8, r.bytesText());
    }

    @Test
    void the_line_lists_every_round() {
        BenchRounds.Result r = BenchRounds.measure(3, 0, 5, 0, (round, frame) -> 1000L * (round + 1));
        String s = r.roundsText();
        assertTrue(s.contains("0.001") && s.contains("0.002") && s.contains("0.003"), s);
    }
}
