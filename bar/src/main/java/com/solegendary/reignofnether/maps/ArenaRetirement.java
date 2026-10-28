package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.fogofwar.FrozenChunkClientboundPacket;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Takes the old match's arena down when the world moves on to a new one (NewMatch). Everything of the old match is
 * unregistered in the tick of the request - so the new match starts from empty lists and no stale unit, building or
 * owner can touch it. Its units leave the world in that tick too: one left queued whose chunk unloaded before its turn
 * was skipped as removed, saved ownerless with the chunk, and joined the unit list again when the chunk loaded
 * (Mode A review of #198). Only the work that costs - quietly demolishing dozens of buildings block by block - is
 * spread over the following ticks, at the rate a defeated base is cleared (player.DefeatCleanupServerEvents), so a big
 * end-of-match never stalls the server tick. No wrecks and no effects: the ground is being left behind, nobody is
 * watching it come down.
 * <p>
 * Each building is already ownerless when it is demolished: a demolition that found its owner still a player of the
 * NEXT match (same name, new game) could defeat them (BuildingPlacement.destroy checks the owner's last building).
 * What is still queued when the server stops is finished before the final save - a building's chunk forcing is only
 * released by demolishing it, and a forced chunk would otherwise stay loaded in the saved world for good.
 */
public final class ArenaRetirement {
    private ArenaRetirement() { }

    static final int STEP_TICKS = 4;
    static final int BUILDINGS_PER_STEP = 2;

    private static final Deque<BuildingPlacement> buildings = new ArrayDeque<>();
    // the level a building is demolished in when it has none of its own (resetRTS names the overworld)
    private static ServerLevel level = null;
    private static int ticks = 0;

    /** How much one {@link #retireAll} took out of play. */
    public record Counts(int units, int buildings) { }

    /** True while an old arena is still being taken down. */
    public static boolean busy() {
        return !buildings.isEmpty();
    }

    /**
     * Unregisters every unit and building of the match that was played: the units are discarded at once, the buildings
     * (ownerless at once) are demolished over the next ticks.
     */
    public static Counts retireAll(ServerLevel lvl) {
        List<LivingEntity> us = new ArrayList<>(UnitServerEvents.getAllUnits());
        UnitServerEvents.getAllUnits().clear();
        for (LivingEntity le : us) {
            if (le instanceof Unit u)
                u.setOwnerName("");
            discard(le);
        }
        List<BuildingPlacement> bs = new ArrayList<>(BuildingServerEvents.getBuildings());
        BuildingServerEvents.getBuildings().clear();
        for (BuildingPlacement b : bs) {
            b.ownerName = "";
            FrozenChunkClientboundPacket.setBuildingDestroyedServerside(b.originPos);
            BuildingClientboundPacket.removeBuilding(b.originPos);
            buildings.add(b);
        }
        level = lvl;
        ticks = 0;
        if (!us.isEmpty() || !bs.isEmpty())
            ReignOfNether.LOGGER.info("[NewMatch] removed {} unit(s) of the old arena; its {} building(s) come down over the next ticks",
                us.size(), bs.size());
        return new Counts(us.size(), bs.size());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || !busy())
            return;
        ticks++;
        if (ticks % STEP_TICKS == 0)
            for (int i = 0; i < BUILDINGS_PER_STEP && !buildings.isEmpty(); i++)
                demolishNextBuilding();
    }

    private static void discard(LivingEntity le) {
        if (le.isRemoved())
            return;
        try {
            le.discard();
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[NewMatch] removing {} of the old arena failed", le.getType().toShortString(), e);
        }
    }

    private static void demolishNextBuilding() {
        BuildingPlacement b = buildings.poll();
        if (b == null)
            return;
        try {
            // in its own level, as resetRTS demolishes (the request names the overworld)
            b.demolish(b.getLevel() instanceof ServerLevel own ? own : level);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[NewMatch] demolishing {} of the old arena failed", b.getBuilding().structureName, e);
        }
    }

    /** Everything still queued, now (server stop, game tests). */
    public static void finishAll() {
        while (!buildings.isEmpty())
            demolishNextBuilding();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        if (busy())
            ReignOfNether.LOGGER.info("[NewMatch] finishing the old arena's removal before the world is saved");
        finishAll();
    }
}
