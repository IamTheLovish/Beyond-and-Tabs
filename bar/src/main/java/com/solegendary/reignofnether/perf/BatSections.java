package com.solegendary.reignofnether.perf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Our own named sections ("bat:attacker", "bat:attacker/buildingScan", ...) summed out of a vanilla profile tree.
 *
 * Vanilla files a unit's tick under its entity type, so "bat:attacker" shows up once per unit type: 60-odd branches
 * of the tree, none of them heavy on its own. The profile report is only readable once those are added up by name,
 * which is what this does: the self share of every {@code bat:} node (its share of the whole tick minus its children's),
 * and its inclusive share, summed over every place it appears. No Minecraft types - the tree is a {@link Tree} the
 * caller adapts from ProfileResults - so the sums are unit-tested (SysTimerTest).
 */
final class BatSections {

    static final String PREFIX = "bat:";
    /** The separator vanilla puts between the names of a path (ProfileResults.PATH_SEPARATOR). */
    static final char SEP = '\u001e';
    static final String UNSPECIFIED = "unspecified";
    /** A node under this share of the whole tick is not walked: nothing named below it can matter. */
    static final double MIN_PCT = 0.02;
    static final int MAX_DEPTH = 16, MAX_NODES = 20000;

    private BatSections() { }

    /** One child of a profile node and its share of the whole tick, in percent. */
    record Node(String name, double globalPct) { }

    /** One summed section: its own share of the tick (children taken out) and its share including children. */
    record Row(String name, double selfPct, double inclusivePct) { }

    /** The children of the node at {@code path} ("root", then "root" + SEP + "tick", ...); empty for a leaf. */
    interface Tree {
        List<Node> children(String path);
    }

    /** Every bat: section in the tree, summed by name, heaviest self share first. */
    static List<Row> aggregate(Tree tree) {
        Map<String, double[]> acc = new LinkedHashMap<>();
        walk(tree, "root", 0, acc, new int[] { MAX_NODES });
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acc.entrySet())
            rows.add(new Row(e.getKey(), e.getValue()[0], e.getValue()[1]));
        rows.sort((a, b) -> Double.compare(b.selfPct(), a.selfPct()));
        return rows;
    }

    /** Walks below {@code path}; returns the summed share of its real children (the "unspecified" remainder aside). */
    private static double walk(Tree tree, String path, int depth, Map<String, double[]> acc, int[] budget) {
        double sum = 0;
        for (Node child : tree.children(path)) {
            if (UNSPECIFIED.equals(child.name()))
                continue;
            sum += child.globalPct();
            if (depth >= MAX_DEPTH || budget[0]-- <= 0 || child.globalPct() < MIN_PCT)
                continue;
            double below = walk(tree, path + SEP + child.name(), depth + 1, acc, budget);
            if (child.name().startsWith(PREFIX)) {
                double[] a = acc.computeIfAbsent(child.name(), k -> new double[2]);
                a[0] += Math.max(0, child.globalPct() - below);
                a[1] += child.globalPct();
            }
        }
        return sum;
    }

    /** "bat:attacker 12.3% | bat:unit 8.1% ..." for the heaviest {@code top} rows; empty when there are none. */
    static String text(List<Row> rows, int top) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(top, rows.size()); i++) {
            if (i > 0)
                out.append(" | ");
            Row r = rows.get(i);
            out.append(r.name()).append(String.format(Locale.ROOT, " %.1f%%", r.selfPct()));
            if (r.inclusivePct() - r.selfPct() >= 0.5)
                out.append(String.format(Locale.ROOT, " (%.1f%% with what it calls)", r.inclusivePct()));
        }
        return out.toString();
    }
}
