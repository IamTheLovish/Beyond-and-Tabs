package com.solegendary.reignofnether.perf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The accounting behind the {@code [Goals]} line ({@link GoalTimes}): nanoseconds and calls per goal CLASS and per kind
 * of call, summed over every unit that runs that goal - one row per system, not per unit. No Minecraft types and no
 * clock of its own, so it is unit-tested (GoalTimerTest). Single thread only: the facade checks the thread.
 *
 * <p>Times are inclusive: a goal's tick that calls into a [Sys] section (an attacker's entity scan) counts that time
 * here as well. The [Goals] line says which goal spends the time; the [Sys] line says on what.
 */
final class GoalTimer {

    /** The kinds of call GoalSelector makes on a goal, as they print. */
    static final int TICK = 0, CAN_USE = 1, CAN_CONTINUE = 2, START = 3, KINDS = 4;
    static final String[] KIND_NAMES = { "tick", "canUse", "canContinue", "start" };
    /** A goal class under this many milliseconds per tick is folded into the "smaller" count. */
    static final double MIN_MS = 0.01;
    static final int TOP = 12;

    private final Map<String, Integer> ids = new HashMap<>();
    private String[] names = new String[32];
    private long[][] ns = new long[KINDS][32];
    private long[][] calls = new long[KINDS][32];
    private int count = 0;
    /** Closed ticks in the window. */
    int ticks = 0;

    /** The id of a goal class by its printed name; a new name gets the next id. */
    int id(String name) {
        Integer id = ids.get(name);
        if (id != null)
            return id;
        if (count == names.length) {
            int n = names.length * 2;
            names = Arrays.copyOf(names, n);
            for (int k = 0; k < KINDS; k++) {
                ns[k] = Arrays.copyOf(ns[k], n);
                calls[k] = Arrays.copyOf(calls[k], n);
            }
        }
        names[count] = name;
        ids.put(name, count);
        return count++;
    }

    void add(int id, int kind, long nanos) {
        if (id < 0 || id >= count || kind < 0 || kind >= KINDS || nanos < 0)
            return;
        ns[kind][id] += nanos;
        calls[kind][id]++;
    }

    void tickEnd() {
        ticks++;
    }

    /** A new window: the totals go, the ids stay. */
    void reset() {
        for (int k = 0; k < KINDS; k++) {
            Arrays.fill(ns[k], 0);
            Arrays.fill(calls[k], 0);
        }
        ticks = 0;
    }

    long nanos(int id) {
        long sum = 0;
        for (int k = 0; k < KINDS; k++)
            sum += ns[k][id];
        return sum;
    }

    long calls(int id) {
        long sum = 0;
        for (int k = 0; k < KINDS; k++)
            sum += calls[k][id];
        return sum;
    }

    long calls(int id, int kind) {
        return calls[kind][id];
    }

    /**
     * {@code [Goals] 200 ticks, 7.9 ms/tick in 31 goal classes: MeleeAttackUnitGoal 2.10 ms (tick 1.62, canUse 0.31,
     * canContinue 0.12, start 0.05; 410 calls/t, 5.1 us/call) | ... (+19 smaller)}. Heaviest first; a kind under
     * 0.005 ms/tick is left out of a row. Null when no tick closed.
     */
    String line(String tag) {
        if (ticks == 0)
            return null;
        Integer[] order = new Integer[count];
        int used = 0;
        long totalNs = 0;
        for (int i = 0; i < count; i++) {
            long n = nanos(i);
            if (calls(i) == 0)
                continue;
            order[used++] = i;
            totalNs += n;
        }
        Integer[] rows = Arrays.copyOf(order, used);
        Arrays.sort(rows, (a, b) -> Long.compare(nanos(b), nanos(a)));
        StringBuilder out = new StringBuilder();
        out.append(tag).append(' ').append(ticks).append(" ticks, ")
            .append(String.format(Locale.ROOT, "%.2f ms/tick in %d goal classes", totalNs / 1.0e6 / ticks, used));
        if (used == 0)
            return out.append(": nothing timed").toString();
        out.append(": ");
        int shown = 0;
        for (int r = 0; r < rows.length; r++) {
            int id = rows[r];
            double ms = nanos(id) / 1.0e6 / ticks;
            if (shown >= TOP || ms < MIN_MS)
                break;
            if (shown > 0)
                out.append(" | ");
            out.append(names[id]).append(String.format(Locale.ROOT, " %.2f ms (", ms));
            boolean first = true;
            for (int k = 0; k < KINDS; k++) {
                double kms = ns[k][id] / 1.0e6 / ticks;
                if (calls[k][id] == 0 || kms < 0.005)
                    continue;
                out.append(first ? "" : ", ").append(KIND_NAMES[k]).append(String.format(Locale.ROOT, " %.2f", kms));
                first = false;
            }
            long c = calls(id);
            out.append(first ? "" : "; ").append(String.format(Locale.ROOT, "%.0f calls/t, %.1f us/call)",
                (double) c / ticks, nanos(id) / 1.0e3 / c));
            shown++;
        }
        if (used > shown)
            out.append(" (+").append(used - shown).append(" smaller)");
        return out.toString();
    }
}
