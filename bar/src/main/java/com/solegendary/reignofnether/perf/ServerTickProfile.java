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
        if (recording != null) {
            recording.add(lastMs);
            if (--recordTicksLeft <= 0)
                finishRecording(server);
        }
        if (captureTicksLeft > 0 && --captureTicksLeft == 0)
            finishCapture(server);
        if (++ticks % LOG_EVERY_TICKS != 0)
            return;
        int units = UnitServerEvents.getAllUnits().size();
        if (units == 0)
            return;   // nothing of ours is running: the lobby, a menu world
        double[] sorted = sortedMs(times);
        ReignOfNether.LOGGER.info("[Tick] p50 {} ms, p95 {} ms, max {} ms over the last {} ticks; {} units, {} buildings, {} players",
            fmt(percentile(sorted, 0.50)), fmt(percentile(sorted, 0.95)), fmt(sorted[sorted.length - 1]), sorted.length,
            units, BuildingServerEvents.getBuildings().size(), server.getPlayerCount());
        double p95 = percentile(sorted, 0.95);
        if (p95 >= AUTO_P95_MS && units >= AUTO_MIN_UNITS && captureTicksLeft == 0
                && ticks - lastCaptureAt >= AUTO_COOLDOWN_TICKS && !server.isTimeProfilerRunning()) {
            lastCaptureAt = ticks;
            startCapture(server, AUTO_CAPTURE_TICKS, "p95 " + fmt(p95) + " ms with " + units + " units");
        }
    }

    // ---- automatic lag capture (lovish Oct 20: "can you just add this to the game so its automatic") --------------
    // When a match gets slow, switch on vanilla's own time profiler (the one behind /debug start: it splits every tick
    // into sections - per level, per entity TYPE, and inside each mob "goalSelector", "targetSelector", "navigation",
    // "push", "travel"...) for AUTO_CAPTURE_TICKS, then write its full report to logs/perf-auto-<time>.txt and log the
    // heaviest sections on one [PerfAuto] line, which is all a session needs from latest.log. At most once every
    // AUTO_COOLDOWN_TICKS; never while someone runs /debug themselves. The profiler's own cost is only paid during
    // the 10 s capture.
    static final double AUTO_P95_MS = 50.0;
    static final int AUTO_MIN_UNITS = 60;
    static final int AUTO_CAPTURE_TICKS = 200;
    static final int AUTO_COOLDOWN_TICKS = 20 * 60 * 5;
    static final int SUMMARY_TOP = 12;
    private static int captureTicksLeft = 0;
    private static int lastCaptureAt = -AUTO_COOLDOWN_TICKS;
    private static String captureReason = "";
    public static volatile String lastCaptureSummary = null;

    /** Starts a capture of {@code ticksToCapture} ticks (also used by the GameTest). False if one cannot start now. */
    public static boolean startCapture(MinecraftServer server, int ticksToCapture, String reason) {
        if (captureTicksLeft > 0 || server.isTimeProfilerRunning())
            return false;
        server.startTimeProfiler();
        captureTicksLeft = ticksToCapture + 1;   // the profiler starts on the next tick
        captureReason = reason;
        ReignOfNether.LOGGER.info("[PerfAuto] the server is slow ({}): profiling the next {} s", reason, ticksToCapture / 20);
        return true;
    }

    private static void finishCapture(MinecraftServer server) {
        net.minecraft.util.profiling.ProfileResults results = server.stopTimeProfiler();
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
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(SUMMARY_TOP, leaves.size()); i++) {
            if (i > 0)
                top.append(" | ");
            top.append(leaves.get(i)[0]).append(' ').append(fmt(Double.parseDouble(leaves.get(i)[1]))).append('%');
        }
        double msPerTick = results.getTickDuration() <= 0 ? 0 : results.getNanoDuration() / 1.0e6 / results.getTickDuration();
        String summary = "[PerfAuto] " + captureReason + "; " + results.getTickDuration() + " ticks profiled, "
            + fmt(msPerTick) + " ms per tick; heaviest: " + top + (saved ? " -> " + out.getPath() : "");
        lastCaptureSummary = summary;
        ReignOfNether.LOGGER.info(summary);
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
        lastCaptureAt = -AUTO_COOLDOWN_TICKS;
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
