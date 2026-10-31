package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * A {@code [Spike]} line for every frame of {@link Limiter#THRESHOLD_MS} or more, at most one a second (Oct 22: the
 * game "feels choppy" and the 10 s {@code [Frame]} line only shows p95 / max, never which frame or why). The line says
 * how long the frame was, units known, zoom, which of our render handlers ran longest in THAT frame
 * ({@link FrameParts}), and whether a {@code [GC]} pause shared the second; tools/lag_report.py counts them per minute.
 *
 * Cost on a normal frame: one nanoTime, one subtraction and one compare - no allocation. Everything else (config read,
 * string building) happens only on a spike. Runs after {@link FrameWindow} (lowest priority) so that frame's
 * per-handler times are already closed into {@code lastTickNs}. Config {@code log_frame_spikes}, default true.
 */
public final class FrameSpikes {
    private FrameSpikes() { }

    private static final Limiter LIMITER = new Limiter();
    private static long lastFrameStart = 0;

    /**
     * The pure part: is this frame a spike, and may it be logged now? No Minecraft types, no clock of its own, so a
     * GameTest checks it without rendering (FrameSpikeGameTests).
     */
    public static final class Limiter {
        public static final double THRESHOLD_MS = 50.0;
        public static final long MIN_GAP_MS = 1000;
        /** Returned by {@link #offer} when nothing should be logged. */
        public static final int NO_LOG = -1;

        private long lastLogMs = Long.MIN_VALUE;
        private int skipped = 0;

        /**
         * @return {@link #NO_LOG}, or the number of spikes skipped since the last logged one (0 = none) when this one
         *         is to be logged
         */
        public int offer(long nowMs, double frameMs) {
            if (frameMs < THRESHOLD_MS)
                return NO_LOG;
            if (lastLogMs != Long.MIN_VALUE && nowMs - lastLogMs < MIN_GAP_MS) {
                skipped++;
                return NO_LOG;
            }
            int result = skipped;
            skipped = 0;
            lastLogMs = nowMs;
            return result;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        long now = System.nanoTime();
        long prev = lastFrameStart;
        lastFrameStart = now;
        if (prev == 0)
            return;
        double frameMs = (now - prev) / 1.0e6;
        if (frameMs < Limiter.THRESHOLD_MS)
            return;                                  // the normal frame ends here
        onSpike(frameMs);
    }

    private static void onSpike(double frameMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)   // loading screens and menus are slow by nature
            return;
        try {
            if (!ReignOfNetherClientConfigs.LOG_FRAME_SPIKES.get())
                return;
        } catch (IllegalStateException e) {
            // config not loaded yet: log anyway
        }
        long nowMs = System.currentTimeMillis();
        int skipped = LIMITER.offer(nowMs, frameMs);
        if (skipped == Limiter.NO_LOG)
            return;
        StringBuilder sb = new StringBuilder(160);
        sb.append(String.format(Locale.ROOT, "[Spike] %.0f ms frame; %d units known, zoom %d, %s view", frameMs,
            UnitClientEvents.getAllUnits().size(), Math.round(OrthoviewClientEvents.getZoom()),
            OrthoviewClientEvents.isEnabled() ? "RTS" : "first-person"));
        String parts = topParts(FrameParts.frameTimer().lastTickNs, 3);
        if (!parts.isEmpty())
            sb.append("; our handlers: ").append(parts);
        long gcAt = GcLog.lastPauseAtMs();
        if (gcAt != 0 && Math.abs(nowMs - gcAt) <= 1000)
            sb.append("; GC pause ").append(GcLog.lastPauseMs()).append(" ms in the same second");
        if (skipped > 0)
            sb.append("; ").append(skipped).append(" more spikes skipped since the last line");
        ReignOfNether.LOGGER.info(sb.toString());
    }

    /** The {@code top} biggest sections of one frame, "hud 12.1 ms, unitBodies 3.0 ms"; empty when none reached 0.5 ms. */
    static String topParts(long[] frameNs, int top) {
        StringBuilder sb = new StringBuilder();
        boolean[] used = new boolean[frameNs.length];
        for (int k = 0; k < top; k++) {
            int best = -1;
            for (int i = 0; i < frameNs.length && i < FrameParts.FRAME_SECTIONS; i++)
                if (!used[i] && frameNs[i] >= 500_000L && (best < 0 || frameNs[i] > frameNs[best]))
                    best = i;
            if (best < 0)
                break;
            used[best] = true;
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(FrameParts.name(best)).append(String.format(Locale.ROOT, " %.1f ms", frameNs[best] / 1.0e6));
        }
        return sb.toString();
    }
}
