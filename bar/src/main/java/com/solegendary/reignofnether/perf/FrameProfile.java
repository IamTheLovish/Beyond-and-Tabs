package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.util.FrameTimer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;

/**
 * The client's frame-time profile (inbox/spec-5-performance section 3, instrument 2): every 10 s in a world, one
 * {@code [Frame]} log line with p50 / p95 / max of vanilla's own frame timer (the last 240 frames, what the F3 chart
 * draws), the zoom, and how many units the client knows about. Zero cost beyond reading a ring vanilla already
 * keeps; nothing drawn, nothing changed. The eyes tour's log carries these lines per scene, lovish's latest.log
 * carries them per match - the relative gate for every later performance change.
 */
public final class FrameProfile {
    private FrameProfile() { }

    static final int LOG_EVERY_TICKS = 200;
    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ticks = 0;
            return;
        }
        if (++ticks % LOG_EVERY_TICKS != 0)
            return;
        FrameTimer timer = mc.getFrameTimer();
        long[] log = timer.getLog();
        int n = 0;
        for (long v : log)
            if (v > 0)
                n++;
        if (n < 20)
            return;
        double[] ms = new double[n];
        int i = 0;
        for (long v : log)
            if (v > 0)
                ms[i++] = v / 1.0e6;
        Arrays.sort(ms);
        double p50 = ms[Math.round((n - 1) * 0.50f)], p95 = ms[Math.round((n - 1) * 0.95f)], max = ms[n - 1];
        ReignOfNether.LOGGER.info("[Frame] p50 {} ms ({} fps), p95 {} ms, max {} ms over {} frames; {} view, zoom {}, {} units known",
            ServerTickProfile.fmt(p50), Math.round(1000 / Math.max(0.1, p50)), ServerTickProfile.fmt(p95),
            ServerTickProfile.fmt(max), n, OrthoviewClientEvents.isEnabled() ? "RTS" : "first-person",
            Math.round(OrthoviewClientEvents.getZoom()), UnitClientEvents.getAllUnits().size());
    }
}
