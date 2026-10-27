package com.solegendary.reignofnether.perf;

import java.util.Arrays;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * A wall-clock measurement a JUnit budget can gate on without flaking on a shared CI runner: a warm-up, then several
 * rounds of frames, each round reduced to its median frame (a GC pause or a recompile inside a round is dropped), and
 * the BEST round is the result. A neighbour job that hogs the core for a while slows one or two rounds; a real
 * regression slows every round, so the best one still trips the budget. Rounds are a short pause apart, so one burst
 * of noise is unlikely to cover them all.
 *
 * Oct 27: AnimationCostBench.livingArmyTick failed on #146's build at 1.41 us/unit against its 1.0 budget - one median
 * of 30 frames, ~20 ms in all, taken in a noisy moment. Tests: BenchRoundsTest.
 */
final class BenchRounds {
    private BenchRounds() { }

    /** One frame of work: returns the nanoseconds it took. The warm-up calls it with round -1. */
    @FunctionalInterface
    interface Frame {
        long run(int round, int frame);
    }

    record Result(double bestMs, double[] roundMedianMs) {
        /** Every round's median, for the bench line ("0.351/0.349/0.702 ms"), so noise is visible next to the result. */
        String roundsText() {
            StringJoiner j = new StringJoiner("/", "", " ms");
            for (double m : roundMedianMs)
                j.add(String.format(Locale.ROOT, "%.3f", m));
            return j.toString();
        }
    }

    static Result measure(int rounds, int warmupFrames, int frames, long pauseMs, Frame frame) {
        for (int f = 0; f < warmupFrames; f++)
            frame.run(-1, f);
        double[] medians = new double[rounds];
        long[] ns = new long[frames];
        for (int r = 0; r < rounds; r++) {
            if (r > 0 && pauseMs > 0)
                pause(pauseMs);
            for (int f = 0; f < frames; f++)
                ns[f] = frame.run(r, f);
            long[] sorted = ns.clone();
            Arrays.sort(sorted);
            medians[r] = sorted[frames / 2] / 1.0e6;
        }
        double best = Double.MAX_VALUE;
        for (double m : medians)
            best = Math.min(best, m);
        return new Result(best, medians);
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
