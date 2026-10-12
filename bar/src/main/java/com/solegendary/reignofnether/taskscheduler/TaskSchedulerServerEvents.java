package com.solegendary.reignofnether.taskscheduler;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-thread tasks in game ticks ({@link TickScheduler} has the guarantees). Use this, never
 * {@code CompletableFuture.delayedExecutor}, to do something on the server later: that runs on a pool thread, in wall
 * time, even after the world it captured has been closed.
 * <p>
 * Registered on the Forge bus by ServerEventRegistrar (dedicated) and ClientEventRegistrar (integrated server).
 */
public class TaskSchedulerServerEvents {

    private static final TickScheduler SCHEDULER = new TickScheduler("server tasks");

    /** Runs {@code task} on the server thread at the end of the {@code delayTicks}-th server tick from now. Any thread. */
    public static void schedule(int delayTicks, Runnable task) {
        SCHEDULER.schedule(delayTicks, task);
    }

    /** Tasks waiting to run (for tests and the debug overlay). */
    public static int pending() {
        return SCHEDULER.pending();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase == TickEvent.Phase.END)
            SCHEDULER.tick();
    }

    // a task must never outlive its world: singleplayer can close one world and open another in the same JVM, and a
    // leftover task would run against the old ServerLevel it captured
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        SCHEDULER.clear();
    }
}
