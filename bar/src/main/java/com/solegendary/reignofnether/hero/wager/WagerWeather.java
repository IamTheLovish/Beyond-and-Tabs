package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Weather from a wager, ended on our own clock (Mode A B1). The mod's load function turns doWeatherCycle off, so
 * vanilla's rain timer never runs out in a match: a "60 s drizzle" would rain for the rest of it. Here the sky the level
 * had before the first wager weather is remembered, and when the last wager's time is up (or the server stops) it is
 * put back. Vanilla thunder is never set: its lightning bolts strike at random across the loaded map, damage units and
 * start fires; the Thunderstorm's strikes are our own LightningFx bolts instead.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class WagerWeather {
    private WagerWeather() { }

    /** The sky before the first wager weather (kept while wagers overlap) and when the current one ends. */
    private record Active(boolean rainWas, boolean thunderWas, long until) { }

    private static final Map<ResourceKey<Level>, Active> ACTIVE = new HashMap<>();

    /** Rain (or a clear sky) for {@code ticks}, at most a phenomenon's 90 s; a later wager replaces the earlier one's end. */
    public static void start(ServerLevel level, boolean rain, int ticks) {
        int t = Math.max(1, Math.min(ticks, Phenomena.MAX_TICKS));
        Active prev = ACTIVE.get(level.dimension());
        boolean rainWas = prev != null ? prev.rainWas : level.getLevelData().isRaining();
        boolean thunderWas = prev != null ? prev.thunderWas : level.getLevelData().isThundering();
        level.setWeatherParameters(rain ? 0 : t, rain ? t : 0, rain, false);
        ACTIVE.put(level.dimension(), new Active(rainWas, thunderWas, level.getGameTime() + t));
    }

    /** The game time the wager weather in this level ends, -1 when none is running. */
    public static long until(ServerLevel level) {
        Active a = ACTIVE.get(level.dimension());
        return a == null ? -1 : a.until;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ACTIVE.isEmpty())
            return;
        for (ServerLevel level : evt.getServer().getAllLevels()) {
            Active a = ACTIVE.get(level.dimension());
            if (a != null && level.getGameTime() >= a.until)
                restore(level, a);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        for (ServerLevel level : evt.getServer().getAllLevels()) {
            Active a = ACTIVE.get(level.dimension());
            if (a != null)
                restore(level, a);
        }
        ACTIVE.clear();
    }

    private static void restore(ServerLevel level, Active a) {
        // zero timers: with the cycle off the sky stays as it was; with it on, vanilla draws new timers next tick
        level.setWeatherParameters(0, 0, a.rainWas, a.thunderWas);
        ACTIVE.remove(level.dimension());
    }
}
