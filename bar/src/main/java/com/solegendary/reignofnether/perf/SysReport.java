package com.solegendary.reignofnether.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one-line text of a {@link SysTimer} window, for the {@code [Sys]} (server tick) and {@code [FrameParts]} (client
 * frame) log lines. No Minecraft types; unit-tested (SysTimerTest).
 *
 * Shape: {@code [Sys] 200 ticks, mean 78.4 ms/tick; ours 51.2 ms (65%): attacker 12.3 (max 40.1, 610/t) | unit 8.8 ... ;
 * elsewhere 27.2 ms}. Each row is the section's self milliseconds per tick, the most one tick spent there, and its
 * calls per tick; sections are sorted heaviest first and anything under {@link #MIN_MS} per tick is folded into a
 * count. "elsewhere" is the mean whole tick minus ours: vanilla's own work, the mods we do not time and GC.
 */
final class SysReport {

    /** A section under this many milliseconds per tick is left out of the line. */
    static final double MIN_MS = 0.02;
    static final int TOP = 14;

    private SysReport() { }

    /**
     * @param tag     the log prefix, "[Sys]"
     * @param per     what a tick is called, "tick" or "frame"
     * @param names   section names, indexed like the timer's sections
     * @param wholeMs the mean milliseconds of a whole tick (frame) in the window, or a negative number when unknown
     */
    static String line(String tag, String per, String[] names, SysTimer t, double wholeMs) {
        int n = Math.max(1, t.ticks);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.length && i < t.sections(); i++)
            if (t.calls[i] > 0)
                order.add(i);
        order.sort((a, b) -> Long.compare(t.selfNs[b], t.selfNs[a]));
        StringBuilder rows = new StringBuilder();
        int shown = 0, hidden = 0;
        for (int i : order) {
            double ms = t.selfNs[i] / 1.0e6 / n;
            if (ms < MIN_MS || shown >= TOP) {
                hidden++;
                continue;
            }
            if (shown++ > 0)
                rows.append(" | ");
            rows.append(String.format(Locale.ROOT, "%s %.2f (max %.1f, %s/%c)", names[i], ms, t.worstTickNs[i] / 1.0e6,
                perTick((double) t.calls[i] / n), per.charAt(0)));
        }
        double oursMs = t.totalSelfNs() / 1.0e6 / n;
        StringBuilder out = new StringBuilder();
        out.append(tag).append(' ').append(t.ticks).append(' ').append(per).append("s, ");
        if (wholeMs >= 0)
            out.append(String.format(Locale.ROOT, "mean %.1f ms/%s; ours %.1f ms (%.0f%%): ", wholeMs, per, oursMs,
                wholeMs <= 0 ? 0 : 100.0 * oursMs / wholeMs));
        else
            out.append(String.format(Locale.ROOT, "ours %.1f ms/%s: ", oursMs, per));
        out.append(rows.length() == 0 ? "nothing timed" : rows.toString());
        if (hidden > 0)
            out.append(" (+").append(hidden).append(" smaller)");
        if (wholeMs >= 0)
            out.append(String.format(Locale.ROOT, "; elsewhere %.1f ms", Math.max(0, wholeMs - oursMs)));
        if (t.overflow > 0 || t.unbalanced > 0)
            out.append(" [timer: ").append(t.overflow).append(" overflow, ").append(t.unbalanced).append(" unbalanced]");
        return out.toString();
    }

    private static String perTick(double calls) {
        if (calls >= 1000)
            return String.format(Locale.ROOT, "%.1fk", calls / 1000);
        return calls >= 10 ? String.format(Locale.ROOT, "%.0f", calls) : String.format(Locale.ROOT, "%.1f", calls);
    }
}
