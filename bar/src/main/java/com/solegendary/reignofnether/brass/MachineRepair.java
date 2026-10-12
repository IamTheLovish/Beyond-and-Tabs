package com.solegendary.reignofnether.brass;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.brass.EngineerCaptainUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Field repair of the Brassworks' machines (design/brassworks_plan.md, slice 2): an idle Tinker mends the most damaged
 * own or allied {@link Machine} within {@link #REACH} at {@link #TINKER_HP_PER_SECOND}; the T2 constructor, the
 * <b>Engineer-Captain</b>, at {@link #ENGINEER_MULT}x that. Reign of Nether's workers only repair buildings
 * (BuildRepairGoal), so a machine's repair is its own small pass: once a second from the worker's tick, one grid query
 * per idle worker, nothing allocated (the scratch list is reused, server thread only). Crews are people: only machines
 * are mended (the Repair Crawler's aura follows the same rule, {@link #isMachine}).
 */
public final class MachineRepair {
    private MachineRepair() { }

    public static final float TINKER_HP_PER_SECOND = 2f;
    public static final float ENGINEER_MULT = 2f;
    public static final double REACH = 4.0;

    private static final List<LivingEntity> scratch = new ArrayList<>();

    /** A Brassworks machine (anything carrying Heat), alive. */
    public static boolean isMachine(Entity e) {
        return e instanceof Machine && e instanceof LivingEntity le && le.isAlive();
    }

    /** HP a second this worker mends a machine for: the Engineer-Captain twice a Tinker's. */
    public static float hpPerSecond(LivingEntity worker) {
        return worker instanceof EngineerCaptainUnit ? TINKER_HP_PER_SECOND * ENGINEER_MULT : TINKER_HP_PER_SECOND;
    }

    /** Is {@code e} a machine on {@code owner}'s side (own or allied)? */
    public static boolean isFriendlyMachine(Entity e, String owner) {
        if (!isMachine(e) || !(e instanceof Unit u) || owner == null || owner.isBlank())
            return false;
        String o = u.getOwnerName();
        return owner.equals(o) || AlliancesServerEvents.isAllied(owner, o);
    }

    /**
     * Once a second from a Brassworks worker's tick (server): an idle worker mends the most damaged friendly machine in
     * reach. Returns the HP it mended (0 when busy or nothing needs it). Public for the game test.
     */
    public static float tickWorker(WorkerUnit worker, LivingEntity self) {
        if (!(self.level() instanceof ServerLevel sl) || self.tickCount % 20 != 0 || !WorkerUnit.isIdle(worker))
            return 0;
        return repairOnce(sl, self, ((Unit) worker).getOwnerName());
    }

    /** One second's repair by {@code self} on the most damaged friendly machine within {@link #REACH}. Public for the game test. */
    public static float repairOnce(ServerLevel sl, LivingEntity self, String owner) {
        LivingEntity best = null;
        float bestMissing = 0.5f;
        for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), REACH, scratch)) {
            if (le == self || le.level() != sl || !isFriendlyMachine(le, owner) || le.distanceToSqr(self) > REACH * REACH)
                continue;
            float missing = le.getMaxHealth() - le.getHealth();
            if (missing > bestMissing) {
                bestMissing = missing;
                best = le;
            }
        }
        scratch.clear();
        if (best == null)
            return 0;
        float amount = Math.min(bestMissing, hpPerSecond(self));
        best.heal(amount);
        self.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, best.getX(), best.getY() + best.getBbHeight() * 0.6, best.getZ(),
            3, 0.25, 0.2, 0.25, 0.05);
        return amount;
    }
}
