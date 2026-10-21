package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;

/**
 * The client side of {@link FrameParts}: closes each frame and client tick, switches the sections on while a world is
 * open, and every 10 s writes the {@code [FrameParts]} lines (same cadence as {@code [Frame]} and {@code [Anim]}):
 * <pre>
 * [FrameParts] 1180 frames, mean 12.6 ms/frame; ours 4.1 ms (33%): unitOverlays 2.10 (max 6.2, 1.0/f) | ...; elsewhere 8.5 ms; 540 section re-mesh requests/s
 * [FrameParts] 200 ticks, ours 1.1 ms/tick: unitTick 1.10 (max 3.1, 598/t)
 * </pre>
 * "elsewhere" is everything a frame spends outside our handlers: the terrain, the entity renderers, vanilla's GUI, the
 * GPU wait. The mean frame is wall-clock between frame starts, so it includes any frame cap or vsync wait.
 */
public final class FrameWindow {
    private FrameWindow() { }

    static final int LOG_EVERY_TICKS = FrameProfile.LOG_EVERY_TICKS;
    /** A window needs this many frames behind it to be worth a line. */
    static final int MIN_FRAMES = 20;

    private static final String[] TICK_NAMES = Arrays.copyOfRange(FrameParts.NAMES, FrameParts.FRAME_SECTIONS, FrameParts.SECTIONS);
    private static final String[] FRAME_NAMES = Arrays.copyOf(FrameParts.NAMES, FrameParts.FRAME_SECTIONS);

    private static long lastFrameStart = 0, windowFrameNs = 0, windowStart = 0;
    private static int ticks = 0;
    public static volatile String lastFrameLine = null;

    /** A new frame starts: the one before it is over. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        long now = System.nanoTime();
        if (lastFrameStart != 0 && lastFrameStart != now) {
            FrameParts.frameTimer().tickEnd();
            windowFrameNs += now - lastFrameStart;
        }
        lastFrameStart = now;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.level != null && mc.player != null;
        if (FrameParts.profilers == null)
            FrameParts.profilers = () -> Minecraft.getInstance().getProfiler();
        FrameParts.setOn(inWorld);
        if (!inWorld) {
            ticks = 0;
            reset(0);
            return;
        }
        FrameParts.tickTimer().tickEnd();
        if (windowStart == 0)
            windowStart = System.nanoTime();
        if (++ticks % LOG_EVERY_TICKS != 0)
            return;
        report();
    }

    private static void report() {
        SysTimer frames = FrameParts.frameTimer(), tick = FrameParts.tickTimer();
        long now = System.nanoTime();
        double seconds = Math.max(0.001, (now - windowStart) / 1.0e9);
        long dirty = FrameParts.DIRTY_SECTIONS.getAndSet(0);
        if (frames.ticks >= MIN_FRAMES) {
            String line = SysReport.line("[FrameParts]", "frame", FRAME_NAMES, frames, windowFrameNs / 1.0e6 / frames.ticks)
                + String.format(java.util.Locale.ROOT, "; %.0f section re-mesh requests/s", dirty / seconds);
            lastFrameLine = line;
            ReignOfNether.LOGGER.info(line);
        }
        if (tick.ticks > 0 && tick.calls[0] > 0)
            ReignOfNether.LOGGER.info(SysReport.line("[FrameParts]", "tick", TICK_NAMES, tick, -1));
        reset(now);
    }

    private static void reset(long now) {
        FrameParts.frameTimer().reset();
        FrameParts.tickTimer().reset();
        windowFrameNs = 0;
        windowStart = now;
        if (now == 0)
            FrameParts.DIRTY_SECTIONS.set(0);
    }
}
