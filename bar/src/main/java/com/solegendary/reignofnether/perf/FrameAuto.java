package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The client's automatic lag capture, the twin of ServerTickProfile's: when a client with a crowd is slow, run the
 * client metrics recorder (what F3+L runs: vanilla's section profiler for the client, our own {@code bat:client/...}
 * sections inside it) for its 10 s, then read the report it saves and log the heaviest sections on one
 * {@code [FrameAuto]} line. lovish should not have to know about F3+L to hand a session a client profile.
 *
 * Trigger ({@link #consider}, called from FrameProfile's 10 s [Frame] line): the p95 frame is at least
 * {@link #TRIGGER_P95_MS} with {@link #MIN_UNITS} or more units known, at most once every {@link #COOLDOWN_MS} (5 min).
 * The recorder is vanilla's own debug toggle, so a capture that coincides with the player pressing F3+L ends
 * their recording early; with a 5 minute cooldown that overlap is rare and costs one early-ended profile.
 *
 * The recorder writes {@code debug/profiling/<date>-client.zip} some seconds after it ends; this class looks for a zip
 * newer than the capture's start every second for up to a minute, and reads its profiling.txt ({@link ProfileDump}).
 */
public final class FrameAuto {
    private FrameAuto() { }

    static final double TRIGGER_P95_MS = 25.0;
    static final int MIN_UNITS = 100;
    static final long COOLDOWN_MS = 5 * 60 * 1000L;
    static final long RECORDER_MS = 10_000L, SEARCH_MS = 70_000L;
    static final int SUMMARY_TOP = 12;

    private static long lastStartMs = 0, startedMs = 0;
    private static boolean searching = false;
    private static int ticks = 0;
    public static volatile String lastSummary = null;

    /** Called after each [Frame] line with that window's p95 frame time and the units the client knows. */
    static void consider(double p95Ms, int units, double p50Ms) {
        long now = System.currentTimeMillis();
        if (searching || p95Ms < TRIGGER_P95_MS || units < MIN_UNITS || (lastStartMs != 0 && now - lastStartMs < COOLDOWN_MS))
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        lastStartMs = now;
        startedMs = now;
        searching = true;
        ticks = 0;
        ReignOfNether.LOGGER.info("[FrameAuto] the client is slow (p50 {} ms, p95 {} ms, {} units known): profiling the next {} s with the metrics recorder",
            ServerTickProfile.fmt(p50Ms), ServerTickProfile.fmt(p95Ms), units, RECORDER_MS / 1000);
        try {
            mc.debugClientMetricsStart(text -> ReignOfNether.LOGGER.info("[FrameAuto] recorder: {}", text.getString()));
        } catch (RuntimeException e) {
            searching = false;
            ReignOfNether.LOGGER.warn("[FrameAuto] could not start the client metrics recorder", e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || !searching || ++ticks % 20 != 0)
            return;
        long now = System.currentTimeMillis();
        if (now - startedMs < RECORDER_MS + 1000)
            return;
        if (now - startedMs > SEARCH_MS) {
            searching = false;
            ReignOfNether.LOGGER.warn("[FrameAuto] no report appeared under debug/profiling within {} s of the capture", SEARCH_MS / 1000);
            return;
        }
        File zip = newestZip(new File(Minecraft.getInstance().gameDirectory, "debug/profiling"), startedMs);
        if (zip == null)
            return;
        try {
            String summary = summarise(zip);
            searching = false;
            lastSummary = summary;
            ReignOfNether.LOGGER.info(summary);
        } catch (IOException | RuntimeException e) {
            // the zip may still be being written: try again on the next look until the search runs out
            if (now - startedMs > SEARCH_MS - 5000)
                ReignOfNether.LOGGER.warn("[FrameAuto] could not read {}", zip, e);
        }
    }

    private static File newestZip(File dir, long sinceMs) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".zip"));
        if (files == null)
            return null;
        File best = null;
        for (File f : files)
            if (f.lastModified() >= sinceMs && (best == null || f.lastModified() > best.lastModified()))
                best = f;
        return best;
    }

    private static String summarise(File zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith("profiling.txt"))
                    continue;
                String text = new String(zf.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                List<ProfileDump.Row> rows = ProfileDump.parse(text);
                if (rows.isEmpty())
                    return "[FrameAuto] " + zip.getName() + ": " + entry.getName() + " held no sections (EMPTY PROFILE)";
                List<BatSections.Row> bat = ProfileDump.batSections(rows);
                return "[FrameAuto] " + zip.getName() + ": ours: " + (bat.isEmpty() ? "none" : BatSections.text(bat, 10))
                    + "; heaviest: " + ProfileDump.text(ProfileDump.topSelf(rows, SUMMARY_TOP, 0.5)) + " -> " + zip.getPath();
            }
        }
        return "[FrameAuto] " + zip.getName() + ": no profiling.txt inside (the zip is at " + zip.getPath() + ")";
    }
}
