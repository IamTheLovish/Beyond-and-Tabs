package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.fogofwar.FrozenChunkClientboundPacket;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * What becomes of a defeated side's base. The commander's death is the loss condition (design decision), and RoN
 * simply made the loser's units and buildings ownerless - so in lovish's clip the winner read "You are victorious!"
 * while the bot's Jade Pagoda still stood at 310/400 as a live, shootable target. Instead, once a side is out, its
 * base goes the way of BAR's: every building becomes a neutral wreck holding half its metal (reclaimable by anyone)
 * and comes down quietly, and its units die (leaving their own wrecks through WreckServerEvents.onDeath). It is
 * spread over a few seconds - {@link #BUILDINGS_PER_STEP} buildings every {@link #STEP_TICKS} ticks and a handful of
 * units per tick - so a 40-building base does not take the server down in one tick.
 * <p>
 * Server side only; PlayerServerEvents.defeat hands over the lists it collects while it strips ownership.
 */
public class DefeatCleanupServerEvents {

    public static final int STEP_TICKS = 4;
    public static final int BUILDINGS_PER_STEP = 2;
    public static final int UNITS_PER_TICK = 6;
    /** Share of a building's metal cost its wreck holds (same share as a fallen unit). */
    public static final float BUILDING_WRECK_SHARE = WreckServerEvents.WRECK_SHARE;

    private static final class Job {
        final ServerLevel level;
        final String owner;
        final Deque<BuildingPlacement> buildings;
        final Deque<LivingEntity> units;
        int ticks = 0;

        Job(ServerLevel level, String owner, List<BuildingPlacement> buildings, List<LivingEntity> units) {
            this.level = level;
            this.owner = owner;
            this.buildings = new ArrayDeque<>(buildings);
            this.units = new ArrayDeque<>(units);
        }

        boolean done() {
            return buildings.isEmpty() && units.isEmpty();
        }
    }

    private static final List<Job> jobs = new ArrayList<>();

    /** True while a defeated side's base is still being taken down (game tests wait on it). */
    public static boolean isCleaningUp(String owner) {
        for (Job j : jobs)
            if (j.owner.equals(owner) && !j.done())
                return true;
        return false;
    }

    /** Queue a defeated side's leftovers; the buildings nearest the capitol go last so the heart of the base falls last. */
    public static void begin(ServerLevel level, String owner, List<BuildingPlacement> buildings, List<LivingEntity> units) {
        if (level == null || (buildings.isEmpty() && units.isEmpty()))
            return;
        List<BuildingPlacement> ordered = new ArrayList<>(buildings);
        BlockPos heart = null;
        for (BuildingPlacement b : ordered)
            if (b.isCapitol) {
                heart = b.centrePos;
                break;
            }
        if (heart != null) {
            final BlockPos h = heart;
            ordered.sort((a, b) -> Double.compare(b.centrePos.distSqr(h), a.centrePos.distSqr(h)));
        }
        jobs.add(new Job(level, owner, ordered, units));
        ReignOfNether.LOGGER.info("[Defeat] {} leaves {} building(s) and {} unit(s) to clean up", owner, buildings.size(), units.size());
    }

    /** One building to a wreck: a heap at its centre with half its metal, then the structure comes down quietly. */
    static void demolish(ServerLevel level, BuildingPlacement b) {
        if (!BuildingServerEvents.getBuildings().remove(b))
            return;   // already destroyed in the meantime
        FrozenChunkClientboundPacket.setBuildingDestroyedServerside(b.originPos);
        float metal = b.getBuilding().cost != null ? b.getBuilding().cost.metal() * BUILDING_WRECK_SHARE : 0;
        if (metal >= WreckServerEvents.MIN_COST * BUILDING_WRECK_SHARE) {
            BlockPos c = b.centrePos;
            // the wreck sits on the building's ground level (minCorner is the foundation layer)
            WreckServerEvents.spawnWreck(level, c.getX() + 0.5, b.minCorner.getY(), c.getZ() + 0.5, metal, b.getFaction());
        }
        b.demolish(level);
        BuildingClientboundPacket.removeBuilding(b.originPos);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || jobs.isEmpty())
            return;
        Iterator<Job> it = jobs.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            job.ticks++;
            for (int i = 0; i < UNITS_PER_TICK && !job.units.isEmpty(); i++) {
                LivingEntity le = job.units.poll();
                if (le == null || le.isRemoved() || !le.isAlive())
                    continue;
                // a unit the loser no longer owns (captured, converted) is somebody else's now
                if (le instanceof Unit u && !u.getOwnerName().isEmpty() && !u.getOwnerName().equals(job.owner))
                    continue;
                le.kill();
                if (le.isAlive())   // something refused the kill (invulnerable summon): take it out of the world
                    le.discard();
            }
            if (job.ticks % STEP_TICKS == 0)
                for (int i = 0; i < BUILDINGS_PER_STEP && !job.buildings.isEmpty(); i++) {
                    BuildingPlacement b = job.buildings.poll();
                    if (b != null)
                        try {
                            demolish(job.level, b);
                        } catch (Exception e) {
                            ReignOfNether.LOGGER.error("[Defeat] demolishing {} failed", b.getBuilding().structureName, e);
                        }
                }
            if (job.done())
                it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        jobs.clear();
    }
}
