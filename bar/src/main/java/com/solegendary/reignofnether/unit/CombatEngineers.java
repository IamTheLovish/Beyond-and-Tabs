package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.defence.DefenceBuilding;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.CombatEngineer;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules every <b>combat engineer</b> shares (design/combat_engineers.md), in one place because the seven units sit
 * on two vanilla bodies (six illagers, the Horde's piglin) and can't share a base class:
 * <ul>
 *   <li><b>Build power</b> {@link #BUILD_POWER}: more than a worker, less than a T2 constructor - and per metal the
 *       dearest in the game, so engineers never replace workers for the economy.</li>
 *   <li><b>What they may start</b> ({@link #mayStart}): the faction's field defences - wall, gate, both traps and the
 *       anti-raid tower (design/defences.md). Not economy, labs, T2 towers or the anti-T3 emplacement. Assisting is
 *       free (BAR): an engineer helps build or repair any friendly site; only <i>placing</i> is restricted, on the
 *       server by {@link #buildersMayPlace} and on the client by a build menu of field defences only
 *       ({@link CombatEngineerButtons}).</li>
 *   <li><b>Field repair</b> ({@link #tickFieldRepair}): an idle engineer mends the most damaged friendly unit within
 *       {@link #REPAIR_RANGE} for {@link #REPAIR_HP_PER_POWER} HP a second per point of build power, free (BAR repair
 *       costs nothing), one target at a time, with a nanolathe beam; with no unit to mend it puts itself on the
 *       nearest damaged own building within {@link #BUILDING_REPAIR_RANGE} (the ordinary repair path, which only
 *       counts a builder on its own side's building).</li>
 *   <li><b>Reclaim</b> needs nothing here: engineers are workers, so WreckServerEvents strips wrecks for them at
 *       their build power, faction reclaim traits included.</li>
 * </ul>
 * Server thread only, except {@link #mayStart} (pure data, both sides).
 */
public final class CombatEngineers {
    private CombatEngineers() { }

    /** Every engineer's build power (a worker 1, a T2 constructor 2). */
    public static final float BUILD_POWER = 1.5f;
    /** Field repair: HP a second per point of build power (3 HP/s at {@link #BUILD_POWER}). */
    public static final float REPAIR_HP_PER_POWER = 2f;
    /** How close a unit must stand to be mended. */
    public static final double REPAIR_RANGE = 5.0;
    /** How close (to its nearest block) a damaged building must be for an idle engineer to start repairing it. */
    public static final double BUILDING_REPAIR_RANGE = 6.0;
    /** Field repair runs once a second per engineer (staggered by entity id, so a batch never ticks together). */
    public static final int REPAIR_PERIOD_TICKS = 20;

    public static final String MSG_FIELD_DEFENCES_ONLY = "hud.reignofnether.engineer_field_defences_only";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // UnitGrid results, reused (server thread)

    public static boolean isEngineer(@Nullable Entity e) {
        return e instanceof CombatEngineer;
    }

    /**
     * May a combat engineer start this building? The field defences (design/defences.md): every faction's wall, gate,
     * traps and the capitol-tier towers (the T1 anti-raid tower and the Sky Watch - field anti-air for a push). The
     * enhanced walls, traps and towers, the flak and the anti-T3 emplacement are excluded on purpose: they are base
     * commitments behind a lab or a tier, and the anti-T3 one, thrown up next to an experimental, would make T3s
     * unanswerable the other way round.
     */
    public static boolean mayStart(@Nullable Building b) {
        if (!(b instanceof DefenceBuilding d) || d.requiresLab())
            return false;
        return !(b instanceof DefenceTower tower) || tower.kind.gate == DefenceTower.Gate.CAPITOL;
    }

    /**
     * Server side: may these builders place {@code b}? Refused only when there are engineers among them and no other
     * worker while {@code b} is not a field defence: in a mixed group the worker places it and the engineers assist,
     * which BAR allows every builder. Ids that no longer resolve are ignored.
     */
    public static boolean buildersMayPlace(@Nullable ServerLevel level, @Nullable Building b, @Nullable int[] builderUnitIds) {
        if (b == null || mayStart(b) || level == null || builderUnitIds == null)
            return true;
        boolean anyEngineer = false;
        for (int id : builderUnitIds) {
            Entity e = level.getEntity(id);
            if (e instanceof CombatEngineer)
                anyEngineer = true;
            else if (e instanceof WorkerUnit)
                return true;
        }
        return !anyEngineer;
    }

    /** Horizontal distance from {@code e} to the nearest block of {@code b}'s footprint (PlantCharge's measure). */
    public static double distanceToBuilding(Entity e, BuildingPlacement b) {
        BlockPos min = b.minCorner, max = b.maxCorner;
        double cx = Math.max(min.getX(), Math.min(max.getX() + 1, e.getX()));
        double cz = Math.max(min.getZ(), Math.min(max.getZ() + 1, e.getZ()));
        double dx = cx - e.getX(), dz = cz - e.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Own or allied (never neutral: an empty owner is nobody's friend). */
    public static boolean isFriendly(@Nullable String owner, @Nullable String other) {
        return owner != null && !owner.isBlank() && other != null && !other.isBlank()
            && AlliancesServerEvents.isAlliedOrOwned(owner, other);
    }

    /**
     * Once a second from an engineer's tick (server): field repair when idle. Returns the HP mended (0 when busy, out
     * of phase or nothing needed it). An engineer standing on a wreck is reclaiming it (WreckServerEvents strips wrecks
     * for every worker in reach), so it does that instead - one job at a time, as BAR builders do.
     */
    public static float tickFieldRepair(LivingEntity self) {
        if (!(self.level() instanceof ServerLevel sl) || (self.tickCount + self.getId()) % REPAIR_PERIOD_TICKS != 0)
            return 0;
        if (!(self instanceof WorkerUnit worker) || !(self instanceof Unit unit) || !self.isAlive() || !WorkerUnit.isIdle(worker))
            return 0;
        if (nearWreck(self))
            return 0;
        return repairOnce(sl, self, unit.getOwnerName(), worker.getBuildPower());
    }

    /**
     * One second of field repair by {@code self}: the most damaged friendly unit within {@link #REPAIR_RANGE} gets
     * {@code buildPower x} {@link #REPAIR_HP_PER_POWER} HP; with none, a damaged own building in reach becomes the
     * engineer's repair job. Returns the HP mended. Public for the game test.
     */
    public static float repairOnce(ServerLevel sl, LivingEntity self, String owner, float buildPower) {
        if (buildPower <= 0 || owner == null || owner.isBlank())
            return 0;
        LivingEntity best = mostDamagedFriend(sl, self, owner, REPAIR_RANGE);
        if (best != null) {
            float amount = Math.min(best.getMaxHealth() - best.getHealth(), buildPower * REPAIR_HP_PER_POWER);
            best.heal(amount);
            com.solegendary.reignofnether.barfx.BarFx.nano(self, best.getX(), best.getY() + best.getBbHeight() * 0.6, best.getZ(),
                com.solegendary.reignofnether.barfx.BarFx.NANO_INTERVAL, REPAIR_PERIOD_TICKS + 2);
            return amount;
        }
        if (self instanceof WorkerUnit w && w.getBuildRepairGoal() != null) {
            BuildingPlacement b = damagedOwnBuilding(self, owner, BUILDING_REPAIR_RANGE);
            if (b != null)
                w.getBuildRepairGoal().setBuildingTarget(b);
        }
        return 0;
    }

    /**
     * The friendly unit within {@code range} of {@code self} missing the most health (more than half a heart), never
     * {@code self} (a builder can't mend itself, as in BAR) and never a Mirror Mage decoy. Null when none.
     */
    @Nullable
    public static LivingEntity mostDamagedFriend(ServerLevel sl, LivingEntity self, String owner, double range) {
        LivingEntity best = null;
        float bestMissing = 0.5f;
        double r2 = range * range;
        for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), range, scratch)) {
            if (le == self || !le.isAlive() || le.level() != sl || !(le instanceof Unit u) || le.distanceToSqr(self) > r2)
                continue;
            float missing = le.getMaxHealth() - le.getHealth();
            if (missing <= bestMissing || !isFriendly(owner, u.getOwnerName())
                    || com.solegendary.reignofnether.mana.DecoyServerEvents.isDecoy(le))
                continue;
            bestMissing = missing;
            best = le;
        }
        scratch.clear();
        return best;
    }

    /**
     * The nearest built, damaged, repairable building of {@code owner} within {@code range} of {@code self} (to its
     * nearest block), skipping one being decommissioned (repairing it would undo the refund). Own only: RoN's repair
     * loop (BuildRepairGoal.isBuilding) counts a builder on its own side's building and nobody else's. Null when none.
     */
    @Nullable
    public static BuildingPlacement damagedOwnBuilding(LivingEntity self, String owner, double range) {
        BuildingPlacement best = null;
        double bestD = range;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (!b.isBuilt || b.isDestroyedServerside || b.decommissioning || !b.getBuilding().repairable
                    || b.getLevel() != self.level() || !owner.equals(b.ownerName))
                continue;
            double d = distanceToBuilding(self, b);
            if (d > bestD || b.getHealth() >= b.getMaxHealth())
                continue;
            bestD = d;
            best = b;
        }
        return best;
    }

    /** Is a wreck within reclaim range of {@code self} (so WreckServerEvents is stripping it for this worker)? */
    static boolean nearWreck(LivingEntity self) {
        List<Entity> wrecks = WreckServerEvents.getWrecks();
        if (wrecks.isEmpty())
            return false;
        double r2 = WreckServerEvents.RECLAIM_RANGE * WreckServerEvents.RECLAIM_RANGE;
        for (int i = 0, n = wrecks.size(); i < n; i++) {
            Entity w = wrecks.get(i);
            if (!w.isRemoved() && w.level() == self.level() && w.distanceToSqr(self) <= r2)
                return true;
        }
        return false;
    }
}
