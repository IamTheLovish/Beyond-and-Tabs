package com.solegendary.reignofnether.taskscheduler;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-thread tasks in client ticks ({@link TickScheduler} has the guarantees): the client twin of
 * {@link TaskSchedulerServerEvents}, for HUD and chat follow-ups that used to run on a pool thread via
 * {@code CompletableFuture.delayedExecutor} (and touched {@code Minecraft.player} there, which may be gone by then).
 * Cleared on logout, so nothing from one session runs in the next.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public class TaskSchedulerClientEvents {

    private static final TickScheduler SCHEDULER = new TickScheduler("client tasks");

    /** Runs {@code task} on the client thread at the end of the {@code delayTicks}-th client tick from now. */
    public static void schedule(int delayTicks, Runnable task) {
        SCHEDULER.schedule(delayTicks, task);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase == TickEvent.Phase.END)
            SCHEDULER.tick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut evt) {
        SCHEDULER.clear();
    }
}
