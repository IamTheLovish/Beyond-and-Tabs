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
 * owner can touch it - but the work that costs (discarding hundreds of units, quietly demolishing dozens of buildings
 * block by block) is spread over the following ticks, at the rate a defeated base is cleared
 * (player.DefeatCleanupServerEvents), so a big end-of-match never stalls the server tick. No wrecks and no effects: the
 * ground is being left behind, nobody is watching it come down.
 * <p>
 * Each building is already ownerless when it is demolished: a demolition that found its owner still a player of the
 * NEXT match (same name, new game) could defeat them (BuildingPlacement.destroy checks the owner's last building).
 * What is still queued when the server stops is finished before the final save - a building's chunk forcing is only
 * released by demolishing it, and a forced chunk would otherwise stay loaded in the saved world for good.
 */
public final class ArenaRetirement {
    private ArenaRetirement() { }

    static final int UNITS_PER_TICK = 8;
    static final int STEP_TICKS = 4;
    static final int BUILDINGS_PER_STEP = 2;

    private static final Deque<LivingEntity> units = new ArrayDeque<>();
    private static final Deque<BuildingPlacement> buildings = new ArrayDeque<>();
    private static ServerLevel level = null;
    private static int ticks = 0;

    /** How much one {@link #retireAll} took out of play. */
    public record Counts(int units, int buildings) { }

    /** True while an old arena is still being taken down. */
    public static boolean busy() {
        return !units.isEmpty() || !buildings.isEmpty();
    }

    /**
     * Unregisters every unit and building of the match that was played and queues their removal. Owners are cleared
     * at once; the entities and blocks follow over the next ticks.
     */
    public static Counts retireAll(ServerLevel lvl) {
        List<LivingEntity> us = new ArrayList<>(UnitServerEvents.getAllUnits());
        UnitServerEvents.getAllUnits().clear();
        for (LivingEntity le : us) {
            if (le instanceof Unit u)
                u.setOwnerName("");
            units.add(le);
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
            ReignOfNether.LOGGER.info("[NewMatch] retiring {} unit(s) and {} building(s) of the old arena over the next ticks",
                us.size(), bs.size());
        return new Counts(us.size(), bs.size());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || !busy())
            return;
        ticks++;
        for (int i = 0; i < UNITS_PER_TICK && !units.isEmpty(); i++)
            removeNextUnit();
        if (ticks % STEP_TICKS == 0)
            for (int i = 0; i < BUILDINGS_PER_STEP && !buildings.isEmpty(); i++)
                demolishNextBuilding();
    }

    private static void removeNextUnit() {
        LivingEntity le = units.poll();
        if (le == null || le.isRemoved())
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
            b.demolish(level);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[NewMatch] demolishing {} of the old arena failed", b.getBuilding().structureName, e);
        }
    }

    /** Everything still queued, now (server stop, game tests). */
    public static void finishAll() {
        while (!units.isEmpty())
            removeNextUnit();
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
