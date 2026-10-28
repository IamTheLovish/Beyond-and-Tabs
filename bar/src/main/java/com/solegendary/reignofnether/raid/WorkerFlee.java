package com.solegendary.reignofnether.raid;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Workers flee (lovish, Oct 22: "killing workers early sets people behind" - and the defender needs an answer): a worker
 * that an enemy player's unit has just hurt drops what it is doing and runs to the nearest own built tower or the
 * capitol within {@link RaidTuning#FLEE_RANGE}, where the tower shoots the raiders and the army's defend call arrives.
 * Raiders are faster than workers (0.31-0.34 against 0.25), so the run is not an escape from a party already on top of
 * them - it is a retreat toward cover and away from the work site that stops the whole line standing and dying one
 * by one. Hold position (H) keeps a worker where it is; commanders (they fight) and workers inside a garrison never
 * flee; neutral and wild attackers do not count (only another player's unit). The bot's workers use the same rule,
 * and its idle-worker logic puts them back to work once they are safe.
 * <p>
 * Server side, from Unit.tick: one check per worker every {@link RaidTuning#FLEE_CHECK_TICKS} ticks, nothing unless
 * the worker was hurt in the last {@link RaidTuning#FLEE_MEMORY_TICKS} ticks (one weak-map lookup).
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class WorkerFlee {
    private WorkerFlee() { }

    private static final Map<Mob, Long> HURT_AT = new WeakHashMap<>();
    /** A flight order is let run this long (ticks) before a worker that is still being hit is sent again. */
    private static final int RUN_TICKS = 40;
    private static final Map<Mob, Long> RUNNING_UNTIL = new WeakHashMap<>();
    /** How many flights each worker has started (for the game tests). */
    private static final Map<Mob, Integer> FLIGHTS = new WeakHashMap<>();

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (victim.level().isClientSide() || !(victim instanceof Mob mob) || !(victim instanceof WorkerUnit)
                || !(victim instanceof Unit unit) || evt.getAmount() <= 0)
            return;
        Entity source = evt.getSource().getEntity();
        if (!(source instanceof AttackerUnit) || UnitServerEvents.getUnitToEntityRelationship(unit, source) != Relationship.HOSTILE)
            return;
        HURT_AT.put(mob, mob.level().getGameTime());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        HURT_AT.clear();
        FLIGHTS.clear();
        RUNNING_UNTIL.clear();
    }

    /** For tests and the HUD: has this worker been hurt by an enemy unit in the flee window? */
    public static boolean recentlyHurt(Mob mob) {
        Long at = HURT_AT.get(mob);
        return at != null && mob.level().getGameTime() - at <= RaidTuning.FLEE_MEMORY_TICKS;
    }

    /** For the game tests: how many times this worker has started to flee. */
    public static int flights(Mob mob) {
        return FLIGHTS.getOrDefault(mob, 0);
    }

    /** Server side, every tick from Unit.tick (the caller has excluded clients and possessed units). */
    public static void tick(Mob mob, Unit unit) {
        if (!RaidTuning.WORKERS_FLEE || HURT_AT.isEmpty() || !(unit instanceof WorkerUnit)
                || (mob.tickCount + mob.getId()) % RaidTuning.FLEE_CHECK_TICKS != 0 || !recentlyHurt(mob))
            return;
        if (unit.getHoldPosition() || mob.isPassenger() || CommanderServerEvents.isCommander(mob)
                || unit instanceof com.solegendary.reignofnether.unit.interfaces.CombatEngineer   // engineers build AND fight
                || GarrisonableBuildingAddon.getGarrison(unit) != null)
            return;
        BuildingPlacement refuge = refugeFor(mob, unit);
        if (refuge == null)
            return;
        BlockPos at = refuge.getClosestGroundPos(mob.blockPosition(), 2);
        if (at == null || mob.blockPosition().distSqr(at) <= RaidTuning.FLEE_ARRIVED * RaidTuning.FLEE_ARRIVED)
            return;   // arrived (it stays where it stopped; the owner or the bot sends it back)
        long now = mob.level().getGameTime();
        Long until = RUNNING_UNTIL.get(mob);
        if (until != null && now < until)
            return;   // already on its way: let the walk run before it is re-issued
        Unit.fullResetBehaviours(unit);   // stop building / gathering / fighting back, drop the queue
        unit.setMoveTarget(at);
        RUNNING_UNTIL.put(mob, now + RUN_TICKS);
        FLIGHTS.merge(mob, 1, Integer::sum);
    }

    /** The nearest own built tower or capitol within FLEE_RANGE, or null. */
    @Nullable
    public static BuildingPlacement refugeFor(Mob mob, Unit unit) {
        BuildingPlacement best = null;
        double bestD = RaidTuning.FLEE_RANGE * RaidTuning.FLEE_RANGE;
        String owner = unit.getOwnerName();
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (!bp.isBuilt || bp.ownerName == null || !bp.ownerName.equals(owner) || bp.getLevel() != mob.level())
                continue;
            if (!(bp.getBuilding().isCapitol || bp instanceof DefenceTower.DefenceTowerPlacement))
                continue;
            double d = bp.centrePos.distToCenterSqr(mob.getX(), mob.getY(), mob.getZ());
            if (d < bestD) {
                bestD = d;
                best = bp;
            }
        }
        return best;
    }
}
