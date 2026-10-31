package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;

import java.util.HashMap;
import java.util.Map;

/**
 * Which GOALS the units' AI time goes to (lovish Oct 31: "the game keeps lagging"). The [Sys] line and vanilla's profiler
 * stop at the goal selector: in lovish's Oct 29 22:44 server profile 213 skull hurlers spent 13.4 ms a tick, the biggest
 * share under {@code goalTick} "unspecified" - the running goals' own ticks, which no section names. This times every
 * call GoalSelector makes on a goal (mixin/goals/WrappedGoalMixin: tick, canUse, canContinueToUse, start) and sums it
 * per goal CLASS over every unit, so the next game's log names the expensive goals - one row per system, never one per
 * unit - and the fix can be made where it pays for every unit that runs that goal.
 *
 * <p>A {@code [Goals]} line beside each [Sys] line (every 10 s), only while [Sys] measures: the server is slow (tick p95
 * 25 ms and up), a lag capture or /rts-perf runs, or a test forces it ({@link Sys#force}). Cost while it does not: one
 * volatile read per goal call. While it does: two nanoTime calls and a map lookup per goal call, under 0.1 us
 * (~5,000 goal calls a tick at 700 units: under 0.5 ms a tick, only in a slow window). Server thread only, like [Sys].
 */
public final class GoalTimes {
    private GoalTimes() { }

    public static final int TICK = GoalTimer.TICK, CAN_USE = GoalTimer.CAN_USE, CAN_CONTINUE = GoalTimer.CAN_CONTINUE,
        START = GoalTimer.START;
    /** {@link #start}'s answer when the call is not timed (nanoTime can be any long, so not 0). */
    public static final long OFF = Long.MIN_VALUE;

    private static final GoalTimer TIMER = new GoalTimer();
    // server thread only (the ids outlive the windows; a class's id never changes)
    private static final Map<Class<?>, Integer> IDS = new HashMap<>();
    /** The last [Goals] line written (tests). */
    public static volatile String lastLine = null;

    /** At the start of a goal call: a time stamp for {@link #stop}, or {@link #OFF} unless [Sys] measures on this thread. */
    public static long start() {
        return Sys.measuringHere() ? System.nanoTime() : OFF;
    }

    /** At the end of a goal call that {@link #start} stamped: adds its time to the goal's class. */
    public static void stop(Object goal, int kind, long started) {
        if (started == OFF || goal == null)
            return;
        long nanos = System.nanoTime() - started;
        TIMER.add(idOf(goal.getClass()), kind, nanos);
    }

    private static int idOf(Class<?> c) {
        Integer id = IDS.get(c);
        if (id == null) {
            id = TIMER.id(name(c));
            IDS.put(c, id);
        }
        return id;
    }

    /** The printed name of a goal class: without the package, nested classes as Outer$Inner. */
    static String name(Class<?> c) {
        String n = c.getName();
        return n.substring(n.lastIndexOf('.') + 1);
    }

    /** Goal calls of every kind timed for a goal class since the last report (tests). */
    public static long calls(Class<?> goal) {
        Integer id = IDS.get(goal);
        return id == null ? 0 : TIMER.calls(id);
    }

    /** A measured server tick closed (Sys.onServerTickEnd, while measuring). */
    static void tickEnd() {
        TIMER.tickEnd();
    }

    /** Every 10 s, after the [Sys] line: writes the [Goals] line if the window measured enough ticks; a new window. */
    static void report() {
        if (TIMER.ticks >= Sys.MIN_TICKS) {
            String line = TIMER.line("[Goals]");
            lastLine = line;
            ReignOfNether.LOGGER.info(line);
        }
        TIMER.reset();
    }

    /** The [Goals] line for what was measured since the last report, whatever its length, and a new window (tests). */
    public static String snapshot() {
        String line = TIMER.line("[Goals]");
        TIMER.reset();
        if (line != null)
            lastLine = line;
        return line;
    }

    /** Measuring starts: a new window. */
    static void newWindow() {
        TIMER.reset();
    }

    static void onServerStopping() {
        TIMER.reset();
    }
}
