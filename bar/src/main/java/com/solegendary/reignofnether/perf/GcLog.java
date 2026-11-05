package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@code [GC]} line for every stop-the-world garbage collection that paused the JVM for {@link #PAUSE_MS} or more,
 * plus a summary line when a 10 s window spent {@link #WINDOW_LOG_MS} or more in GC. lovish's frame log showed spikes
 * of 170-698 ms with nothing to tell a GC stall from a chunk-rebuild burst or the pause menu saving - and a LAN host
 * puts the server and the client in one JVM, so one heap and one collector. With these lines a spike either has a
 * {@code [GC]} line at the same second or it does not.
 *
 * Reads the JVM's own notifications (javax.management; no com.sun types, so it also loads where those are hidden); a
 * JVM or collector that does not send them just logs nothing. Idempotent: client and server registrars both call it.
 */
public final class GcLog {
    private GcLog() { }

    static final long PAUSE_MS = 50, WINDOW_MS = 10_000, WINDOW_LOG_MS = 100;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static long windowStart = 0, windowGcMs = 0;
    private static int windowCount = 0;
    /** Pause milliseconds since the last [Sys] line (Sys takes and clears it with each line). */
    private static long sysWindowMs = 0;

    static long takeSysWindowMs() {
        synchronized (GcLog.class) {
            long ms = sysWindowMs;
            sysWindowMs = 0;
            return ms;
        }
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true))
            return;
        try {
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean instanceof NotificationEmitter emitter)
                    emitter.addNotificationListener((notification, handback) -> {
                        try {
                            onNotification(notification.getType(), notification.getUserData());
                        } catch (RuntimeException e) {
                            // a collector with another notification layout: say nothing, never disturb the JVM's notifier thread
                        }
                    }, null, null);
            }
        } catch (Throwable t) {
            ReignOfNether.LOGGER.warn("[GC] could not listen for garbage collections: {}", t.toString());
        }
    }

    static void onNotification(String type, Object userData) {
        if (!"com.sun.management.gc.notification".equals(type) || !(userData instanceof CompositeData data))
            return;
        String name = String.valueOf(data.get("gcName"));
        // concurrent phases (G1 Concurrent GC, ZGC Cycles) run beside the game: not a pause
        if (name.contains("Concurrent") || name.contains("Cycles"))
            return;
        long ms = (Long) ((CompositeData) data.get("gcInfo")).get("duration");
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
        synchronized (GcLog.class) {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MS) {
                if (windowGcMs >= WINDOW_LOG_MS)
                    ReignOfNether.LOGGER.info("[GC] {} collections took {} ms of the last {} s", windowCount, windowGcMs, (now - windowStart) / 1000);
                windowStart = now;
                windowGcMs = 0;
                windowCount = 0;
            }
            windowGcMs += ms;
            sysWindowMs += ms;
            windowCount++;
        }
        if (ms >= PAUSE_MS)
            ReignOfNether.LOGGER.info("[GC] pause {} ms ({}, {}: {}); heap now {} MB of {} MB", ms, name, data.get("gcAction"),
                data.get("gcCause"), usedMb, rt.maxMemory() >> 20);
    }
}
