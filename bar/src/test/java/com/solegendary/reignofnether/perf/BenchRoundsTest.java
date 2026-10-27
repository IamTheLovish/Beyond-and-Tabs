package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void the_line_lists_every_round() {
        BenchRounds.Result r = BenchRounds.measure(3, 0, 5, 0, (round, frame) -> 1000L * (round + 1));
        String s = r.roundsText();
        assertTrue(s.contains("0.001") && s.contains("0.002") && s.contains("0.003"), s);
    }
}
