package com.solegendary.reignofnether.gametest;

/**
 * The start gate's wait rule, kept free of Minecraft so it is unit-tested on its own ({@link TestStartGate} feeds it
 * one reading per tick). A test's first tick is held while some chunk of its structure is not ready for entities,
 * and released when all are, or when the wait stops making progress.
 * <p>
 * Progress-based, never a flat tick count: the GameTest server ticks unthrottled (~1000-6700 ticks/s), while entity
 * sections load through the IO worker in wall time, so a tick bound alone can run out before one IO round-trip ends
 * (research inbox Nov 6, finding 7). A give-up needs BOTH {@link #STALL_TICKS} ticks AND as much wall time as those
 * ticks take at 20 per second without one more chunk becoming ready - the rule {@code whenEntitiesReady} uses - or
 * the wall-clock cap {@link #CAP_NANOS} in all. Giving up only lets the test start as it would have without the
 * gate (its own timeout and messages decide), with a log line saying what was still not ready.
 */
public final class StartGateProgress {

    /** Ticks without a new ready chunk before the wait counts as stalled (also needs the wall time, see above). */
    public static final int STALL_TICKS = 200;
    /** The wall time those ticks take at 20 per second: a stall needs both. */
    public static final long STALL_NANOS = STALL_TICKS * 50_000_000L;
    /** Wall time of the whole wait, at most: under the CI watchdog and the suite's own per-test budget. */
    public static final long CAP_NANOS = 120_000_000_000L;

    public enum Verdict { HOLD, READY, STALLED, CAPPED }

    private int ticks = 0, best = -1, bestAt = 0;
    private long startNanos = Long.MIN_VALUE, bestAtNanos;

    /**
     * One reading: {@code ready} of {@code total} chunks ready this tick, at wall time {@code nanos}. HOLD = keep the
     * test's first tick back; anything else = let it start (READY: all ready; STALLED / CAPPED: given up).
     */
    public Verdict update(int ready, int total, long nanos) {
        if (startNanos == Long.MIN_VALUE) {
            startNanos = nanos;
            bestAtNanos = nanos;
        }
        if (ready >= total)
            return Verdict.READY;
        if (ready > best) {
            best = ready;
            bestAt = ticks;
            bestAtNanos = nanos;
        }
        ticks++;
        if (nanos - startNanos >= CAP_NANOS)
            return Verdict.CAPPED;
        if (ticks - bestAt >= STALL_TICKS && nanos - bestAtNanos >= STALL_NANOS)
            return Verdict.STALLED;
        return Verdict.HOLD;
    }

    /** Ticks held so far (0 when the first reading was already ready). */
    public int ticksHeld() {
        return ticks;
    }

    /** Wall milliseconds since the first reading, given the current time. */
    public long millisSince(long nanos) {
        return startNanos == Long.MIN_VALUE ? 0 : (nanos - startNanos) / 1_000_000L;
    }
}
