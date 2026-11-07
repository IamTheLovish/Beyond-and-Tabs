package com.solegendary.reignofnether.worldmemory;

import java.util.Arrays;

/**
 * The trample sampler's cost pass by pass, for the cost GameTest. The test once divided the wall time of a whole
 * 36-pass window by its walks (~6 ms of work in all), so one GC pause or safepoint inside the window decided it: the same
 * code read 2.54 to 17.51 us a walk on 8 runners (stress 42359261272). Per pass, the median of the passes' cost per walk
 * is the steady cost; p90 and the whole-window number are reported beside it. Thread CPU time where the JVM gives it
 * (a pass the scheduler set aside is not charged), else wall time. Fixed arrays, so recording a pass allocates nothing.
 * Pure (no Minecraft): JUnit checks the statistics.
 */
public final class TramplePassLog {
    private final int[] walks;
    private final long[] wallNanos, cpuNanos;
    private int n;
    private boolean cpuEverywhere = true;

    public TramplePassLog(int capacity) {
        walks = new int[capacity];
        wallNanos = new long[capacity];
        cpuNanos = new long[capacity];
    }

    /** One pass: its walks, its wall time and its thread CPU time ({@code cpu < 0}: not available). Full: ignored. */
    public void add(int passWalks, long wall, long cpu) {
        if (n == walks.length)
            return;
        walks[n] = passWalks;
        wallNanos[n] = wall;
        cpuNanos[n] = cpu;
        if (cpu < 0)
            cpuEverywhere = false;
        n++;
    }

    public int passes() {
        return n;
    }

    /** True when every pass has a thread CPU time, so {@link #percentileUsPerWalk} measures CPU, not wall time. */
    public boolean cpuTime() {
        return n > 0 && cpuEverywhere;
    }

    /**
     * The q-quantile (0..1, nearest rank) of the passes' cost per walk in microseconds, over the passes that walked at
     * all; NaN when none did.
     */
    public double percentileUsPerWalk(double q) {
        double[] perWalk = new double[n];
        int m = 0;
        boolean cpu = cpuTime();
        for (int i = 0; i < n; i++)
            if (walks[i] > 0)
                perWalk[m++] = (cpu ? cpuNanos[i] : wallNanos[i]) / 1e3 / walks[i];
        if (m == 0)
            return Double.NaN;
        Arrays.sort(perWalk, 0, m);
        int rank = (int) Math.ceil(q * m);
        return perWalk[Math.min(m, Math.max(1, rank)) - 1];
    }

    /** The old measure: all the window's wall time over all its walks, in microseconds (NaN without walks). */
    public double wholeWindowWallUsPerWalk() {
        long nanos = 0, w = 0;
        for (int i = 0; i < n; i++) {
            nanos += wallNanos[i];
            w += walks[i];
        }
        return w == 0 ? Double.NaN : nanos / 1e3 / w;
    }
}
