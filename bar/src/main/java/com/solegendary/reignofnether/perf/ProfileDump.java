package com.solegendary.reignofnether.perf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads vanilla's profiling.txt (the section tree the metrics recorder and /perf write, ProfileResults.saveResults):
 * <pre>
 * [00] levels(200/1)- 71.23%/71.23%
 * [01] |   minecraft:overworld(200/1)- 98.10%/69.87%
 * [02] |   |   entities(200/1)- 80.30%/56.11%
 * </pre>
 * Each line is a depth, the name, call count over ticks, the share of its parent and the share of the whole. Used by
 * {@link FrameAuto}: the client recorder only hands over a zip, and its report is what names the heavy sections of a
 * slow client. No Minecraft types; unit-tested (ProfileDumpTest).
 *
 * A node's self share is its share minus the shares of the rows printed below it; vanilla prints an "unspecified" row
 * for the remainder, which is exactly the self time, so it is left out of the sum and never listed on its own.
 */
final class ProfileDump {

    // vanilla writes "name(284/1) - 77.91%/77.91%" (a space before the dash); the dash-only form is kept too. Oct 22:
    // the space-less pattern matched no real dump, so every client capture logged "EMPTY PROFILE".
    private static final Pattern LINE = Pattern.compile(
        "^\\[(\\d+)] ((?:\\|   )*)(.+?)\\((\\d+)/(\\d+)\\)\\s*- ([0-9.]+)%/([0-9.]+)%\\s*$");

    private ProfileDump() { }

    record Row(int depth, String name, double globalPct) { }

    /** One section with its self share, named by the last three names of its path. */
    record Top(String path, String name, double selfPct) { }

    static List<Row> parse(String text) {
        List<Row> rows = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            Matcher m = LINE.matcher(line);
            if (!m.matches())
                continue;
            try {
                rows.add(new Row(Integer.parseInt(m.group(1)), m.group(3), Double.parseDouble(m.group(7))));
            } catch (NumberFormatException ignored) {
                // a row we cannot read is left out
            }
        }
        return rows;
    }

    /** Self share of every row, in the same order (0 for "unspecified" rows: that share is counted in the parent). */
    static double[] selfShares(List<Row> rows) {
        double[] self = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (BatSections.UNSPECIFIED.equals(row.name()))
                continue;
            double children = 0;
            for (int j = i + 1; j < rows.size() && rows.get(j).depth() > row.depth(); j++)
                if (rows.get(j).depth() == row.depth() + 1 && !BatSections.UNSPECIFIED.equals(rows.get(j).name()))
                    children += rows.get(j).globalPct();
            self[i] = Math.max(0, row.globalPct() - children);
        }
        return self;
    }

    /** The {@code n} rows with the biggest self share (at least {@code minPct}), heaviest first. */
    static List<Top> topSelf(List<Row> rows, int n, double minPct) {
        double[] self = selfShares(rows);
        String[] stack = new String[64];
        List<Top> all = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int d = Math.min(row.depth(), stack.length - 1);
            stack[d] = row.name();
            if (self[i] < minPct)
                continue;
            StringBuilder path = new StringBuilder();
            for (int k = Math.max(0, d - 2); k <= d; k++)
                path.append(path.length() == 0 ? "" : " > ").append(stack[k]);
            all.add(new Top(path.toString(), row.name(), self[i]));
        }
        all.sort((a, b) -> Double.compare(b.selfPct(), a.selfPct()));
        return all.subList(0, Math.min(n, all.size()));
    }

    /** The bat: sections summed by name (self share), heaviest first. */
    static List<BatSections.Row> batSections(List<Row> rows) {
        double[] self = selfShares(rows);
        Map<String, double[]> acc = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (!row.name().startsWith(BatSections.PREFIX))
                continue;
            double[] a = acc.computeIfAbsent(row.name(), k -> new double[2]);
            a[0] += self[i];
            a[1] += row.globalPct();
        }
        List<BatSections.Row> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acc.entrySet())
            out.add(new BatSections.Row(e.getKey(), e.getValue()[0], e.getValue()[1]));
        out.sort((a, b) -> Double.compare(b.selfPct(), a.selfPct()));
        return out;
    }

    /** "path 12.3% | path 8.1% ..." for the given rows. */
    static String text(List<Top> top) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            if (i > 0)
                out.append(" | ");
            out.append(top.get(i).path()).append(String.format(Locale.ROOT, " %.1f%%", top.get(i).selfPct()));
        }
        return out.toString();
    }
}
