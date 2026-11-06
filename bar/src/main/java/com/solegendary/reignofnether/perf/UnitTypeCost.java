package com.solegendary.reignofnether.perf;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The unit render cost BY TYPE (lovish's standing perf rule, Oct 31: a fix at the shared entry every unit uses, and a
 * counter in the log that names the culprit type). {@link AnimProfile} clocks every unit's whole render in the view
 * (posing, the body and every layer); this files each of those clocks under the unit's type, so a {@code [AnimTypes]}
 * line next to the {@code [Anim]} line says which bodies a big army's frame is spent on - and an A/B of a renderer
 * change (the instanced bodies, #big-army) shows per type what it saved and which types it did not reach.
 * <p>
 * Client thread only (AnimProfile's callers); the type key is whatever the caller passes (the EntityType), held by
 * identity, named once by the caller's namer. Two counters per type: the lifetime (never reset; the eyes army scene
 * differences two readings round each of its slices) and the 10 s window of the log line.
 */
public final class UnitTypeCost {
    private UnitTypeCost() { }

    /** Per type: lifetime ns, lifetime renders, window ns, window renders. */
    private static final Map<Object, long[]> COST = new IdentityHashMap<>();
    private static final Map<Object, String> NAMES = new IdentityHashMap<>();
    private static final int LIFE_NS = 0, LIFE_N = 1, WIN_NS = 2, WIN_N = 3;
    /** The window line lists at most this many types (the costliest first). */
    static final int TOP = 10;

    /** One unit render of {@code type} took {@code nanos}. */
    public static void add(Object type, long nanos) {
        long[] c = COST.get(type);
        if (c == null) {
            c = new long[4];
            COST.put(type, c);
        }
        c[LIFE_NS] += nanos;
        c[LIFE_N]++;
        c[WIN_NS] += nanos;
        c[WIN_N]++;
    }

    /** Lifetime nanoseconds and renders of one type ({0, 0} if never drawn). */
    public static long[] lifetime(Object type) {
        long[] c = COST.get(type);
        return c == null ? new long[2] : new long[] { c[LIFE_NS], c[LIFE_N] };
    }

    /** Every type drawn so far, with its lifetime {ns, renders}: a copy (the caller differences two of them). */
    public static Map<Object, long[]> lifetimeAll() {
        Map<Object, long[]> out = new IdentityHashMap<>(COST.size() * 2);
        for (Map.Entry<Object, long[]> e : COST.entrySet())
            out.put(e.getKey(), new long[] { e.getValue()[LIFE_NS], e.getValue()[LIFE_N] });
        return out;
    }

    /**
     * The window's line, costliest types first, and the window reset; null when no unit was drawn. {@code frames} is
     * the frames drawn in the window (the per-frame numbers' denominator), {@code namer} names a type key.
     */
    public static String windowLine(long frames, Function<Object, String> namer) {
        List<Map.Entry<Object, long[]>> rows = new ArrayList<>();
        long totalNs = 0, totalN = 0;
        for (Map.Entry<Object, long[]> e : COST.entrySet())
            if (e.getValue()[WIN_N] > 0) {
                rows.add(e);
                totalNs += e.getValue()[WIN_NS];
                totalN += e.getValue()[WIN_N];
            }
        if (rows.isEmpty())
            return null;
        rows.sort((a, b) -> Long.compare(b.getValue()[WIN_NS], a.getValue()[WIN_NS]));
        return format(rows, totalNs, totalN, Math.max(1, frames), namer);
    }

    static String format(List<Map.Entry<Object, long[]>> rows, long totalNs, long totalN, long frames,
                         Function<Object, String> namer) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
            "[AnimTypes] %d unit types drawn, %.1f units/frame, %.2f ms/frame in all; costliest first (units/frame, ms/frame, us/unit):",
            rows.size(), (double) totalN / frames, totalNs / 1.0e6 / frames));
        for (int i = 0; i < rows.size() && i < TOP; i++) {
            long[] c = rows.get(i).getValue();
            sb.append(i == 0 ? " " : "; ").append(name(rows.get(i).getKey(), namer)).append(String.format(Locale.ROOT,
                " %.1f, %.2f, %.1f", (double) c[WIN_N] / frames, c[WIN_NS] / 1.0e6 / frames,
                c[WIN_NS] / 1.0e3 / Math.max(1, c[WIN_N])));
        }
        if (rows.size() > TOP)
            sb.append("; ").append(rows.size() - TOP).append(" more");
        for (long[] c : COST.values()) {
            c[WIN_NS] = 0;
            c[WIN_N] = 0;
        }
        return sb.toString();
    }

    /** The type's name, asked of the namer once per type. */
    public static String name(Object type, Function<Object, String> namer) {
        String n = NAMES.get(type);
        if (n == null) {
            n = namer.apply(type);
            NAMES.put(type, n);
        }
        return n;
    }
}
