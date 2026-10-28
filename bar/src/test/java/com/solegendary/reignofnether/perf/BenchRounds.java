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
 * The bytes each round's frames allocate (the thread's allocation counter around every frame, nothing else) are
 * counted the same way: a zero-allocation gate takes the QUIETEST round. A real allocation per frame shows in every
 * round; a one-off - the JIT swapping a frame's compiled code mid-measurement, which materialises ~4 KB once (#293's
 * first build; the same code allocates nothing under -Xint) - shows in one round only.
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

    record Result(double bestMs, double[] roundMedianMs, long[] roundBytes) {
        /** Every round's median, for the bench line ("0.351/0.349/0.702 ms"), so noise is visible next to the result. */
        String roundsText() {
            StringJoiner j = new StringJoiner("/", "", " ms");
            for (double m : roundMedianMs)
                j.add(String.format(Locale.ROOT, "%.3f", m));
            return j.toString();
        }

        long quietestRoundBytes() {
            long q = Long.MAX_VALUE;
            for (long b : roundBytes)
                q = Math.min(q, b);
            return roundBytes.length == 0 ? 0 : q;
        }

        /** The zero-allocation gate. */
        boolean allocationFree() {
            return quietestRoundBytes() == 0;
        }

        /** Every round's allocated bytes ("0/0/4000/0 B"). */
        String bytesText() {
            StringJoiner j = new StringJoiner("/", "", " B");
            for (long b : roundBytes)
                j.add(Long.toString(b));
            return j.toString();
        }
    }

    private static final com.sun.management.ThreadMXBean MX =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();

    static Result measure(int rounds, int warmupFrames, int frames, long pauseMs, Frame frame) {
        long tid = Thread.currentThread().getId();
        for (int f = 0; f < warmupFrames; f++)
            frame.run(-1, f);
        double[] medians = new double[rounds];
        long[] bytes = new long[rounds];
        long[] ns = new long[frames];
        long[] sorted = new long[frames];
        for (int r = 0; r < rounds; r++) {
            if (r > 0 && pauseMs > 0)
                pause(pauseMs);
            long allocated = 0;
            for (int f = 0; f < frames; f++) {
                long a0 = MX.getThreadAllocatedBytes(tid);
                ns[f] = frame.run(r, f);
                allocated += MX.getThreadAllocatedBytes(tid) - a0;
            }
            bytes[r] = allocated;
            System.arraycopy(ns, 0, sorted, 0, frames);
            Arrays.sort(sorted);
            medians[r] = sorted[frames / 2] / 1.0e6;
        }
        double best = Double.MAX_VALUE;
        for (double m : medians)
            best = Math.min(best, m);
        return new Result(best, medians, bytes);
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
