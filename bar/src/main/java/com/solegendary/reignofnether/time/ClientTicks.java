package com.solegendary.reignofnether.time;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * The client's own clock for whatever moves or repeats on screen: animations (sails, banners, tree sway, mist, part
 * and statue motion, blinks), periodic client work (every-N-ticks gates) and short client timers and caches. One
 * count per client tick while a level runs - 20 a second, standing still only while the game is paused, never
 * jumping, never running back.
 *
 * <p>Not the level's game time. On the client that is the time of day: mixin/ClientLevelMixin.tickTime sets game
 * time and day time to the target the server's time packets give (mixin/ClientPacketMixin.handleSetTime). In a match
 * the day moves one tick every matchDayCycle game ticks (match/DayClock: every 3rd at the default Slow); it stands
 * still with the cycle Off, before the match starts and once it is decided, and near a night source it walks to
 * midnight and parks there. Animations drawn as gameTime + partialTick ran at a third of their speed at Slow and
 * jumped back each tick (the partial tick swept 0..1 three times over one game-time value), and froze or shivered in
 * place whenever the day stood still; every-N gates fired every tick or never.
 *
 * <p>Day-of-time uses (the mist's density by hour, the sky) stay on the level's day time. Times the server stamps
 * (ground scar decay) compare with {@link TimeClientEvents#getClientTime}, the client's estimate of the server's game
 * time.
 */
public final class ClientTicks {

    private ClientTicks() { }

    private static long ticks = 0;

    /** Client ticks a level has run (paused time excluded). */
    public static long now() {
        return ticks;
    }

    /** {@link #at} wraps every this many ticks (an hour): a float of an unbounded count loses its fractions. */
    public static final long WRAP = 72_000L;

    /**
     * {@link #now} plus a frame's partial tick: the time an animation draws at. Wrapped to an hour, so a float keeps
     * the frame's fraction however long the game stays open (unwrapped it lost it after about 15 hours); an animation
     * sees one jump an hour, as the statues' and parts' own clocks already did.
     */
    public static float at(float partialTick) {
        return (ticks % WRAP) + partialTick;
    }

    /** One client tick: counts when a level is running (loaded and not paused). */
    static void step(boolean running) {
        if (running)
            ticks++;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        step(mc.level != null && !mc.isPaused());
    }

    // the eyes run's [Clock] trace: once a minute, 60 frames of the old clock (the level's game time plus the partial
    // tick, what the animations drew at before) against this one - the old one stalls and falls back, this one runs
    private static final boolean EYES = Boolean.getBoolean("bat.eyes");
    private static final int TRACE_FRAMES = 60, TRACE_EVERY = 1200;
    private static final float[] traceOld = new float[TRACE_FRAMES], traceNew = new float[TRACE_FRAMES];
    private static final long[] traceMs = new long[TRACE_FRAMES];
    private static int traced = -1;
    private static long tracedAt = -TRACE_EVERY;

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (!EYES || evt.phase != TickEvent.Phase.START)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused())
            return;
        if (traced < 0) {
            if (ticks - tracedAt < TRACE_EVERY)
                return;
            traced = 0;
            tracedAt = ticks;
        }
        traceOld[traced] = mc.level.getGameTime() + evt.renderTickTime;
        traceNew[traced] = at(evt.renderTickTime);
        traceMs[traced] = Util.getMillis();
        if (++traced < TRACE_FRAMES)
            return;
        traced = -1;
        int backOld = 0, backNew = 0;
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < TRACE_FRAMES; i++) {
            if (i > 0 && traceOld[i] < traceOld[i - 1])
                backOld++;
            if (i > 0 && traceNew[i] < traceNew[i - 1])
                backNew++;
            raw.append(i == 0 ? "" : " ").append(traceMs[i] - traceMs[0]).append(',')
                .append(String.format(Locale.ROOT, "%.3f", traceOld[i])).append(',')
                .append(String.format(Locale.ROOT, "%.3f", traceNew[i]));
        }
        ReignOfNether.LOGGER.info("[Clock] tick {}: {} frames over {} ms; old clock (game time) advanced {} with {} "
                + "steps back, new clock advanced {} with {} steps back", tracedAt, TRACE_FRAMES,
            traceMs[TRACE_FRAMES - 1] - traceMs[0],
            String.format(Locale.ROOT, "%.2f", traceOld[TRACE_FRAMES - 1] - traceOld[0]), backOld,
            String.format(Locale.ROOT, "%.2f", traceNew[TRACE_FRAMES - 1] - traceNew[0]), backNew);
        ReignOfNether.LOGGER.info("[Clock] frames (ms,old,new): {}", raw);
    }
}
