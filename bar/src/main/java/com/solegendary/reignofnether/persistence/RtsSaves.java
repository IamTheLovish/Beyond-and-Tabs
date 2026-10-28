package com.solegendary.reignofnether.persistence;

import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * When the RTS saved data reaches the disk (plan 014, the save hitch).
 * <p>
 * Before: three 30 s timers (units, buildings, resources), a 60 s one (players) and every research, nether-zone
 * change, join, defeat and reset called {@code getDataStorage().save()} - a synchronous gzip write of every dirty
 * saved-data file - on the server thread, mid-fight (a match reset wrote it seven times in a row).
 * <p>
 * Now: during play the RTS saves only copy live state into their SavedData and mark it dirty. Minecraft's own world
 * save - the autosave every 6000 ticks, /save-all, the singleplayer pause, the stop - calls {@link #snapshot} first
 * (mixin/ServerLevelMixin, head of ServerLevel.save) and then writes everything once. The RTS data is therefore
 * written in step with the chunks and entities it describes: a crash loses the same five minutes of both, where the
 * old 30 s writes could save a building list whose blocks the last chunk save never saw.
 * <p>
 * The stop is special: Forge fires ServerStoppingEvent before the final world save (see MatchStateSaveData), and
 * the stopping handlers save their state and then clear the live lists. {@link #onStoppingFirst} turns the
 * snapshot off before any of them runs, so the final save cannot overwrite their data with empty lists, and
 * {@link #onStoppingLast} writes once after all of them - even with /save-off, as the old per-class writes did.
 */
public final class RtsSaves {

    private RtsSaves() {}

    private static volatile boolean stopping = false;
    private static final long SLOW_SNAPSHOT_MS = 20;

    /** Copies every RTS registry that changes without a save call into its saved data (marks it dirty, no write). */
    public static void snapshot(ServerLevel overworld) {
        if (stopping || overworld == null || overworld.dimension() != Level.OVERWORLD)
            return;
        long start = System.nanoTime();
        try {
            PlayerServerEvents.saveRTSPlayers();
            BuildingServerEvents.saveBuildings(overworld);
            BuildingServerEvents.saveNetherZones(overworld);
            UnitServerEvents.saveGatherTargets(overworld);
            UnitServerEvents.saveFallenHeroUnits(overworld);
            ResourcesServerEvents.saveResources(overworld);
            ResearchServerEvents.saveResearch();
            CustomBuildingServerEvents.saveCustomBuildings(overworld);
            com.solegendary.reignofnether.survival.SurvivalServerEvents.saveData(overworld);
        } finally {
            // it now runs once per world save (5 min) instead of every 30 s; still worth seeing when it grows
            long ms = (System.nanoTime() - start) / 1_000_000L;
            if (ms >= SLOW_SNAPSHOT_MS)
                ReignOfNether.LOGGER.info("[Save] RTS snapshot took {} ms", ms);
        }
    }

    /**
     * Writes every dirty saved-data file now, on this thread. Only for moments with no match running on the tick:
     * the server stopping and the scenario editor's explicit Save. Everything else waits for the world save.
     */
    public static void flush(ServerLevel level) {
        if (level != null)
            level.getDataStorage().save();
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent evt) {
        stopping = false;   // a singleplayer client opens the next world in the same JVM
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStoppingFirst(ServerStoppingEvent evt) {
        stopping = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStoppingLast(ServerStoppingEvent evt) {
        flush(evt.getServer().getLevel(Level.OVERWORLD));
    }
}
