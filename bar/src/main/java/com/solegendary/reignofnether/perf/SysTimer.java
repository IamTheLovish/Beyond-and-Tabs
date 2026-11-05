package com.solegendary.reignofnether.perf;

import java.util.function.LongSupplier;

/**
 * A stopwatch for named, nested sections that reports each section's SELF time: the time inside it minus the time
 * inside the sections it opened. That is what makes the numbers add up. AttackerUnit.tick contains the entity scan and
 * the building scan; "attacker" is only what is left once those two are taken out, so summing every section never
 * counts a millisecond twice and "ours" can be set against the whole tick.
 *
 * No Minecraft types and no clock of its own (the caller passes one in), so the accounting is unit-tested
 * (SysTimerTest). {@link Sys} (server tick) and {@link FrameParts} (client frame and client tick) each own one.
 * Single thread only: the thread check lives in the facades.
 *
 * Use: {@code int t = timer.begin(ID); try { ... } finally { timer.end(t); }}. {@code begin} hands back a token
 * (0 = not recorded: the stack was full) and {@code end} gives it back. A token whose section was already unwound by
 * an outer {@code end} (an inner end skipped by an exception) is ignored instead of corrupting the stack.
 */
final class SysTimer {

    static final int MAX_DEPTH = 32;

    private final LongSupplier clock;
    private final int[] frameId = new int[MAX_DEPTH];
    private final long[] frameStart = new long[MAX_DEPTH];
    private final long[] frameChild = new long[MAX_DEPTH];
    private int depth = 0;

    /** Self nanoseconds per section over the window. */
    final long[] selfNs;
    /** Completed begin/end pairs per section over the window. */
    final long[] calls;
    /** The longest single call's self time per section over the window (nanoseconds). */
    final long[] worstCallNs;
    /** The most self time one tick (or frame) spent in the section over the window (nanoseconds). */
    final long[] worstTickNs;
    private final long[] tickNs;
    /** Completed calls per section since the timer was made ({@link #reset} leaves these; see {@link #lifetimeNs}). */
    final long[] lifetimeCalls;
    /** Closed ticks (frames) in the window. */
    int ticks = 0;
    /** Begins refused because the stack was full (a runaway recursion), and ticks that closed with a section still open. */
    int overflow = 0, unbalanced = 0;
    /**
     * Self nanoseconds per section and closed ticks (frames) since the timer was made: like {@link #selfNs} and
     * {@link #ticks} but never reset, so a measurement that does not line up with the report windows (an eyes scene's
     * slice) can difference two readings.
     */
    final long[] lifetimeNs;
    long lifetimeTicks = 0;

    SysTimer(int sections, LongSupplier clock) {
        this.clock = clock;
        this.selfNs = new long[sections];
        this.calls = new long[sections];
        this.worstCallNs = new long[sections];
        this.worstTickNs = new long[sections];
        this.tickNs = new long[sections];
        this.lifetimeNs = new long[sections];
        this.lifetimeCalls = new long[sections];
    }

    int sections() {
        return selfNs.length;
    }

    /** Opens a section; returns the token for {@link #end}, or 0 when it is not recorded. */
    int begin(int id) {
        if (depth >= MAX_DEPTH) {
            overflow++;
            return 0;
        }
        frameId[depth] = id;
        frameStart[depth] = clock.getAsLong();
        frameChild[depth] = 0;
        return ++depth;
    }

    /** Closes the section {@code token} opened (and any section opened inside it and left open). */
    void end(int token) {
        if (token <= 0 || token > depth)
            return;
        long now = clock.getAsLong();
        int i = token - 1;
        depth = i;
        long total = Math.max(0, now - frameStart[i]);
        long self = Math.max(0, total - frameChild[i]);
        int id = frameId[i];
        selfNs[id] += self;
        tickNs[id] += self;
        lifetimeNs[id] += self;
        calls[id]++;
        lifetimeCalls[id]++;
        if (self > worstCallNs[id])
            worstCallNs[id] = self;
        if (depth > 0)
            frameChild[depth - 1] += total;
    }

    /** Adds time measured by someone else's clock to a section, as one call (nothing is taken from an open parent). */
    void add(int id, long nanos) {
        if (nanos < 0)
            return;
        selfNs[id] += nanos;
        tickNs[id] += nanos;
        lifetimeNs[id] += nanos;
        calls[id]++;
        lifetimeCalls[id]++;
        if (nanos > worstCallNs[id])
            worstCallNs[id] = nanos;
    }

    /** True while a section is open. */
    boolean open() {
        return depth > 0;
    }

    /** One tick (frame) is over: fold what each section spent in it into the worst-tick record. */
    void tickEnd() {
        if (depth != 0) {
            unbalanced++;
            depth = 0;
        }
        for (int id = 0; id < tickNs.length; id++) {
            if (tickNs[id] > worstTickNs[id])
                worstTickNs[id] = tickNs[id];
            tickNs[id] = 0;
        }
        ticks++;
        lifetimeTicks++;
    }

    /** Starts a new window (the totals; sections open now stay open). */
    void reset() {
        java.util.Arrays.fill(selfNs, 0);
        java.util.Arrays.fill(calls, 0);
        java.util.Arrays.fill(worstCallNs, 0);
        java.util.Arrays.fill(worstTickNs, 0);
        java.util.Arrays.fill(tickNs, 0);
        ticks = 0;
        overflow = 0;
        unbalanced = 0;
    }

    /** Total self nanoseconds over every section. */
    long totalSelfNs() {
        long sum = 0;
        for (long v : selfNs)
            sum += v;
        return sum;
    }
}
