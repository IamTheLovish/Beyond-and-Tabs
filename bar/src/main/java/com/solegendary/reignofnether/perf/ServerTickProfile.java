package com.solegendary.reignofnether.perf;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * The server's whole-tick profile (inbox/spec-5-performance section 3, instrument 1). Nothing in the game was ever
 * measured as a whole tick - only our own systems in isolation (the 8v8 stress test) - so the first thing every
 * performance change needs is this baseline. Reads {@code MinecraftServer.tickTimes}, vanilla's own ring of the last
 * 100 tick durations (what the F3 pie and the server's "Can't keep up" warning use), so the cost of measuring is
 * nothing. Two outputs:
 * <ul>
 *   <li>a {@code [Tick]} log line every 10 s while units exist: p50 / p95 / max of the last 100 ticks, live units,
 *       buildings, players;</li>
 *   <li>{@code /rts-perf <seconds>} (op): records every tick for that long, then writes
 *       {@code logs/perf-<date>-<time>.json} next to the game's logs (every sample, the percentiles, the counts) and
 *       answers with one chat line. This is what lovish runs at minute 15 of a 4v4 and hands to a session.</li>
 * </ul>
 * Since Oct 21 the capture below runs vanilla's metrics recorder (what /perf uses), the only profiler that splits the
 * tick into sections, and our own systems appear in it as {@code bat:<name>} sections (see {@link Sys}). The old
 * time profiler behind /debug start is only a tick-rate timer: it returned an empty profile, which the lag capture
 * printed as "heaviest:  0.0%" while its test passed.
 * No behaviour change: this class only reads.
 */
public final class ServerTickProfile {
    private ServerTickProfile() { }

    static final int LOG_EVERY_TICKS = 200;
    static final int MAX_RECORD_SECONDS = 600;

    private static int ticks = 0;
    // /rts-perf: tick durations (ms) being recorded, and how many ticks are left
    private static List<Double> recording = null;
    private static int recordTicksLeft = 0;
    private static CommandSourceStack recorder = null;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rts-perf")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_RECORD_SECONDS))
                .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))
            .executes(ctx -> start(ctx.getSource(), 30)));
    }

    private static int start(CommandSourceStack source, int seconds) {
        if (recording != null) {
            source.sendFailure(Component.literal("rts-perf: a recording is already running (" + (recordTicksLeft / 20) + " s left)"));
            return 0;
        }
        recording = new ArrayList<>(seconds * 20);
        recordTicksLeft = seconds * 20;
        recorder = source;
        Sys.capture(true);   // the [Sys] lines run for the whole recording
        source.sendSuccess(() -> Component.literal("rts-perf: recording " + seconds + " s of server ticks..."), true);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        long[] times = server.tickTimes;
        int tick = server.getTickCount();
        double lastMs = times[Math.floorMod(tick - 1, times.length)] / 1.0e6;
        Sys.onServerTickEnd(server, lastMs);
        if (recording != null) {
            recording.add(lastMs);
            if (--recordTicksLeft <= 0)
                finishRecording(server);
        }
        pollCapture(server);
        if (++ticks % LOG_EVERY_TICKS != 0)
            return;
        int units = UnitServerEvents.getAllUnits().size();
        double p95 = 0;
        if (units > 0) {   // nothing of ours is running otherwise: the lobby, a menu world
            double[] sorted = sortedMs(times);
            p95 = percentile(sorted, 0.95);
            ReignOfNether.LOGGER.info("[Tick] p50 {} ms, p95 {} ms, max {} ms over the last {} ticks; {} units, {} buildings, {} players",
                fmt(percentile(sorted, 0.50)), fmt(p95), fmt(sorted[sorted.length - 1]), sorted.length,
                units, BuildingServerEvents.getBuildings().size(), server.getPlayerCount());
        }
        Sys.report();   // the per-system line, while the server is slow
        if (units > 0 && p95 >= AUTO_P95_MS && units >= AUTO_MIN_UNITS && captureTicksLeft == 0 && !captureAwaiting()
                && ticks - lastCaptureAt >= AUTO_COOLDOWN_TICKS && !server.isTimeProfilerRunning()
                && !server.isRecordingMetrics()) {
            lastCaptureAt = ticks;
            startCapture(server, AUTO_CAPTURE_TICKS, "p95 " + fmt(p95) + " ms with " + units + " units");
        }
    }

    // ---- automatic lag capture (lovish Oct 20: "can you just add this to the game so its automatic") --------------
    // When a match gets slow, run vanilla's metrics recorder (the profiler behind /perf start: it splits every tick
    // into sections - per level, per entity TYPE, and inside each mob "goalSelector", "targetSelector", "navigation",
    // "push", "travel"... - and our own systems as "bat:<name>", see Sys) for AUTO_CAPTURE_TICKS, then write its report
    // to logs/perf-auto-<time>.txt and log our sections and the heaviest ones on one [PerfAuto] line, which is all a
    // session needs from latest.log. The recorder also saves its own zip under debug/profiling. At most once every
    // AUTO_COOLDOWN_TICKS; never while someone runs /debug or /perf themselves. The profiler's own cost is only paid
    // during the 10 s capture. (It used to start the time profiler behind /debug start, which only times the tick
    // rate: an empty profile, "heaviest:  0.0%".)
    static final double AUTO_P95_MS = 50.0;
    static final int AUTO_MIN_UNITS = 60;
    static final int AUTO_CAPTURE_TICKS = 200;
    static final int AUTO_COOLDOWN_TICKS = 20 * 60 * 5;
    static final int SUMMARY_TOP = 12;
    /** How long after asking the recorder to stop its results may take to arrive. */
    static final int RESULTS_WAIT_TICKS = 200;
    /** A section must hold this share of the tick to count as part of a profile (an empty profile has none). */
    static final double MIN_SECTION_PCT = 1.0;
    private static int captureTicksLeft = 0;
    private static int resultsWaitLeft = 0;
    private static int lastCaptureAt = -AUTO_COOLDOWN_TICKS;
    private static String captureReason = "";
    // the recorder hands its results to these (any thread); the next tick end picks them up
    private static volatile net.minecraft.util.profiling.ProfileResults pendingResults = null;
    private static volatile java.nio.file.Path pendingReport = null;
    public static volatile String lastCaptureSummary = null;
    /** The last capture came back with no section above {@link #MIN_SECTION_PCT} (the GameTest fails on it). */
    public static volatile boolean lastCaptureEmpty = false;
    /** Leaves of the last capture's tree at or above {@link #MIN_SECTION_PCT} of the tick. */
    public static volatile int lastCaptureSections = 0;
    /** The bat: sections found in the last capture ("bat:attacker", ...). */
    public static volatile List<String> lastCaptureBat = List.of();

    private static boolean captureAwaiting() {
        return resultsWaitLeft > 0;
    }

    /** Starts a capture of {@code ticksToCapture} ticks (also used by the GameTest). False if one cannot start now. */
    public static boolean startCapture(MinecraftServer server, int ticksToCapture, String reason) {
        if (captureTicksLeft > 0 || captureAwaiting() || server.isTimeProfilerRunning() || server.isRecordingMetrics())
            return false;
        pendingResults = null;
        pendingReport = null;
        server.startRecordingMetrics(results -> pendingResults = results, path -> pendingReport = path);
        captureTicksLeft = ticksToCapture + 2;   // the recorder starts on the next tick
        captureReason = reason;
        Sys.capture(true);
        ReignOfNether.LOGGER.info("[PerfAuto] the server is slow ({}): profiling the next {} s", reason, ticksToCapture / 20);
        return true;
    }

    /** Every tick end: stops the recorder when its time is up, and writes the report when its results arrive. */
    private static void pollCapture(MinecraftServer server) {
        java.nio.file.Path report = pendingReport;
        if (report != null) {
            pendingReport = null;
            ReignOfNether.LOGGER.info("[PerfAuto] the recorder's own report (zip with the section tree and metrics): {}", report);
        }
        net.minecraft.util.profiling.ProfileResults results = pendingResults;
        if (results != null) {
            pendingResults = null;
            captureTicksLeft = 0;
            resultsWaitLeft = 0;
            Sys.capture(false);
            finishCapture(server, results);
            return;
        }
        if (captureTicksLeft > 0 && --captureTicksLeft == 0) {
            // time is up: the recorder ends on this call (it also ends itself after 10 s), results follow at a tick end
            if (server.isRecordingMetrics())
                server.finishRecordingMetrics();
            resultsWaitLeft = RESULTS_WAIT_TICKS;
        } else if (resultsWaitLeft > 0 && --resultsWaitLeft == 0) {
            Sys.capture(false);
            fail("the metrics recorder returned no results " + RESULTS_WAIT_TICKS + " ticks after it was stopped");
        }
    }

    private static void fail(String why) {
        lastCaptureEmpty = true;
        lastCaptureSections = 0;
        lastCaptureBat = List.of();
        String summary = "[PerfAuto] " + captureReason + "; EMPTY PROFILE: " + why;
        lastCaptureSummary = summary;
        ReignOfNether.LOGGER.warn(summary);
    }

    private static void finishCapture(MinecraftServer server, net.minecraft.util.profiling.ProfileResults results) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date());
        File out = new File(new File(server.getServerDirectory(), "logs"), "perf-auto-" + stamp + ".txt");
        boolean saved = false;
        try {
            saved = results.saveResults(out.toPath());
        } catch (RuntimeException e) {
            ReignOfNether.LOGGER.error("[PerfAuto] could not write {}", out, e);
        }
        List<String[]> leaves = new ArrayList<>();
        collectLeaves(results, "root", 0, leaves);
        leaves.sort((a, b) -> Double.compare(Double.parseDouble(b[1]), Double.parseDouble(a[1])));
        int sections = 0;
        for (String[] leaf : leaves)
            if (Double.parseDouble(leaf[1]) >= MIN_SECTION_PCT)
                sections++;
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(SUMMARY_TOP, leaves.size()); i++) {
            if (i > 0)
                top.append(" | ");
            top.append(leaves.get(i)[0]).append(' ').append(fmt(Double.parseDouble(leaves.get(i)[1]))).append('%');
        }
        List<BatSections.Row> bat = BatSections.aggregate(path -> children(results, path));
        List<String> batNames = new ArrayList<>();
        for (BatSections.Row row : bat)
            batNames.add(row.name());
        double msPerTick = results.getTickDuration() <= 0 ? 0 : results.getNanoDuration() / 1.0e6 / results.getTickDuration();
        boolean empty = sections == 0;
        String summary = "[PerfAuto] " + captureReason + "; " + results.getTickDuration() + " ticks profiled, "
            + fmt(msPerTick) + " ms per tick; " + (empty ? "EMPTY PROFILE (no section above " + fmt(MIN_SECTION_PCT) + "%); " : "")
            + "ours: " + (bat.isEmpty() ? "none" : BatSections.text(bat, 10)) + "; heaviest: " + top
            + (saved ? " -> " + out.getPath() : "");
        lastCaptureEmpty = empty;
        lastCaptureSections = sections;
        lastCaptureBat = batNames;
        lastCaptureSummary = summary;
        if (empty)
            ReignOfNether.LOGGER.warn(summary);
        else
            ReignOfNether.LOGGER.info(summary);
    }

    /** The children of the node at {@code path} with their share of the whole tick (BatSections reads the tree through this). */
    private static List<BatSections.Node> children(net.minecraft.util.profiling.ProfileResults results, String path) {
        List<net.minecraft.util.profiling.ResultField> fields = results.getTimes(path);
        // getTimes lists the node itself first, then its children
        if (fields.size() <= 1)
            return List.of();
        List<BatSections.Node> out = new ArrayList<>(fields.size() - 1);
        for (int i = 1; i < fields.size(); i++)
            out.add(new BatSections.Node(fields.get(i).name, fields.get(i).globalPercentage));
        return out;
    }

    // the leaves of vanilla's section tree with their share of the whole tick; a leaf is named by its last 3 sections
    // ("reignofnether:thornbow_unit > push") so the line reads without the file
    private static void collectLeaves(net.minecraft.util.profiling.ProfileResults results, String path, int depth,
                                      List<String[]> out) {
        List<net.minecraft.util.profiling.ResultField> fields = results.getTimes(path);
        // getTimes lists the node itself first, then its children
        if (fields.size() <= 1 || depth > 12) {
            String[] parts = path.split(String.valueOf(net.minecraft.util.profiling.ProfileResults.PATH_SEPARATOR));
            StringBuilder name = new StringBuilder();
            for (int i = Math.max(1, parts.length - 3); i < parts.length; i++)
                name.append(name.length() == 0 ? "" : " > ").append(parts[i]);
            double global = fields.isEmpty() ? 0 : fields.get(0).globalPercentage;
            out.add(new String[] { name.toString(), Double.toString(global) });
            return;
        }
        for (int i = 1; i < fields.size(); i++) {
            net.minecraft.util.profiling.ResultField f = fields.get(i);
            if (f.globalPercentage < 0.5)
                continue;   // noise
            if (f.name.equals("unspecified")) {
                String[] parts = path.split(String.valueOf(net.minecraft.util.profiling.ProfileResults.PATH_SEPARATOR));
                out.add(new String[] { parts[parts.length - 1] + " (own)", Double.toString(f.globalPercentage) });
                continue;
            }
            collectLeaves(results, path + net.minecraft.util.profiling.ProfileResults.PATH_SEPARATOR + f.name, depth + 1, out);
        }
    }

    private static void finishRecording(MinecraftServer server) {
        List<Double> samples = recording;
        CommandSourceStack source = recorder;
        recording = null;
        recorder = null;
        Sys.capture(false);
        double[] sorted = new double[samples.size()];
        for (int i = 0; i < sorted.length; i++)
            sorted[i] = samples.get(i);
        Arrays.sort(sorted);
        double p50 = percentile(sorted, 0.50), p95 = percentile(sorted, 0.95), max = sorted.length == 0 ? 0 : sorted[sorted.length - 1];
        int over50 = 0;
        for (double d : sorted)
            if (d > 50)
                over50++;
        int units = UnitServerEvents.getAllUnits().size();
        int buildings = BuildingServerEvents.getBuildings().size();
        String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmm").format(new Date());
        File dir = new File(server.getServerDirectory(), "logs");
        File out = new File(dir, "perf-" + stamp + ".json");
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"when\": \"").append(stamp).append("\",\n");
        json.append("  \"ticks\": ").append(sorted.length).append(",\n");
        json.append("  \"tick_ms\": {\"p50\": ").append(fmt(p50)).append(", \"p95\": ").append(fmt(p95))
            .append(", \"max\": ").append(fmt(max)).append(", \"over_50ms\": ").append(over50).append("},\n");
        json.append("  \"units\": ").append(units).append(", \"buildings\": ").append(buildings)
            .append(", \"players\": ").append(server.getPlayerCount()).append(",\n");
        Runtime rt = Runtime.getRuntime();
        json.append("  \"heap_mb\": {\"used\": ").append((rt.totalMemory() - rt.freeMemory()) >> 20)
            .append(", \"max\": ").append(rt.maxMemory() >> 20).append("},\n");
        json.append("  \"samples_ms\": [");
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0)
                json.append(i % 20 == 0 ? ",\n    " : ", ");
            json.append(fmt(samples.get(i)));
        }
        json.append("]\n}\n");
        String summary = "rts-perf: tick p50 " + fmt(p50) + " ms, p95 " + fmt(p95) + " ms, max " + fmt(max) + " ms ("
            + over50 + " of " + sorted.length + " ticks over 50 ms); " + units + " units, " + buildings + " buildings";
        try {
            if (!dir.isDirectory() && !dir.mkdirs())
                throw new IOException("cannot create " + dir);
            try (FileWriter w = new FileWriter(out)) {
                w.write(json.toString());
            }
            summary += " -> " + out.getPath();
        } catch (IOException e) {
            ReignOfNether.LOGGER.error("[Perf] could not write {}", out, e);
            summary += " (file not written: " + e.getMessage() + ")";
        }
        ReignOfNether.LOGGER.info("[Perf] {}", summary);
        if (source != null) {
            String line = summary;
            source.sendSuccess(() -> Component.literal(line), true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        recording = null;
        recorder = null;
        recordTicksLeft = 0;
        ticks = 0;
        captureTicksLeft = 0;
        resultsWaitLeft = 0;
        pendingResults = null;
        pendingReport = null;
        lastCaptureAt = -AUTO_COOLDOWN_TICKS;
        Sys.onServerStopping();
        com.solegendary.reignofnether.unit.Terrain.resetMemo();
    }

    static double[] sortedMs(long[] nanos) {
        int n = 0;
        for (long v : nanos)
            if (v > 0)
                n++;
        double[] out = new double[Math.max(1, n)];
        int i = 0;
        for (long v : nanos)
            if (v > 0)
                out[i++] = v / 1.0e6;
        Arrays.sort(out);
        return out;
    }

    static double percentile(double[] sorted, double p) {
        if (sorted.length == 0)
            return 0;
        int i = (int) Math.min(sorted.length - 1, Math.round(p * (sorted.length - 1)));
        return sorted[i];
    }

    static String fmt(double ms) {
        return String.format(java.util.Locale.ROOT, "%.1f", ms);
    }
}
