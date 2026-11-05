package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.PoisonSpiderUnit;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.SpiderUnit;
import com.solegendary.reignofnether.unit.units.monsters.StrayUnit;
import com.solegendary.reignofnether.unit.units.piglins.HeadhunterUnit;
import com.solegendary.reignofnether.unit.units.piglins.HoglinUnit;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import com.solegendary.reignofnether.unit.units.villagers.RavagerUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyMath;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.joml.Vector3d;

import java.util.List;

// allows a unit to be able to mount any target as long as their ability allows them to
public class MountGoal extends MoveToTargetBlockGoal {

    public static final float SEARCH_RANGE = 30;
    public static final float RANGE = 2;
    private LivingEntity targetEntity = null;
    public boolean autofind = false;

    public MountGoal(Mob mob) {
        super(mob, false, 0);
    }

    public void setTarget(LivingEntity entity) {
        this.targetEntity = entity;
        if (entity != null)
            this.lastUnreachableTarget = null;   // a new order (or autofind's pick) starts without the last give-up
    }

    /** The mount this goal is walking to, or null (read by PerfScanGameTests' failure history). */
    @javax.annotation.Nullable
    public LivingEntity getTarget() {
        return targetEntity;
    }

    private boolean isMountableUnit(Mob m) {
        if (!(m instanceof Unit unit))
            return false;

        if (m.isVehicle())
            return false;
        else if (!unit.getOwnerName().equals(((Unit) this.mob).getOwnerName()))
            return false;
        else return (this.mob instanceof PillagerUnit && m instanceof RavagerUnit) ||
                    (this.mob instanceof EvokerUnit && m instanceof RavagerUnit) ||
                    (this.mob instanceof SkeletonUnit && (m instanceof SpiderUnit || m instanceof PoisonSpiderUnit)) ||
                    (this.mob instanceof StrayUnit && (m instanceof SpiderUnit || m instanceof PoisonSpiderUnit)) ||
                    (this.mob instanceof HeadhunterUnit && m instanceof HoglinUnit);
    }

    public void setNearestTarget() {
        if (this.mob.level().isClientSide())
            return;

        if (!(this.mob instanceof Unit))
            return;
        // find the closest mob
        double closestDist = Double.MAX_VALUE;
        Mob closestMob = null;
        for (Mob pfMob : MiscUtil.getEntitiesWithinRange(
                new Vector3d(this.mob.position().x, this.mob.position().y, this.mob.position().z),
                SEARCH_RANGE, Mob.class, this.mob.level())) {
            if (isMountableUnit(pfMob)) {
                double dist = mob.position().distanceTo(pfMob.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    closestMob = pfMob;
                }
            }
        }
        if (closestMob != null)
            setTarget(closestMob);
        else
            stop();
    }

    /**
     * A mount order gives up an unreachable mount as a plain move gives up an unreachable spot (GIVE_UP_AFTER_STUCK_
     * REPATHS stuck repaths): {@link #tick} re-sets the move target every tick, which clears lastUnreachableTarget, so
     * without this a walled-in mount held the rider - not idle, Unit.isIdle - until its next order (review round 1 on
     * #507).
     */
    @Override
    protected boolean givesUpWhenUnreachable() {
        return true;
    }

    /** Only a mount of the rider's own side can be ridden (isMountableUnit, the mount below): any other drops the order. */
    private boolean ownSide(LivingEntity mount) {
        return mount instanceof Unit u && this.mob instanceof Unit rider && u.getOwnerName().equals(rider.getOwnerName());
    }

    @Override
    public void tick() {
        // the move goal gave the mount up as unreachable (onStuckRepath): the order is over, before the move target
        // is set again below
        if (this.targetEntity != null && this.moveTarget == null && this.lastUnreachableTarget != null) {
            this.stop();
            return;
        }
        // a dead or gone mount, or another side's (the Mount cursor sends whichever spider was clicked): nothing to ride
        if (this.targetEntity != null && (!this.targetEntity.isAlive() || !ownSide(this.targetEntity))) {
            this.stop();
            return;
        }
        if (autofind) {
            int sys = Sys.begin(Sys.MOUNT_SCAN);
            try {
                setNearestTarget();
            } finally {
                Sys.end(sys);
            }
        }

        if (this.targetEntity != null)
            this.setMoveTarget(this.targetEntity.getOnPos());

        if (moveTarget != null &&
            this.mob instanceof Unit unit1 &&
            this.targetEntity instanceof Unit unit2) {
            if (MyMath.distance(
                    this.mob.getX(), this.mob.getZ(),
                    moveTarget.getX(), moveTarget.getZ()) <= RANGE &&
                unit1.getOwnerName().equals(unit2.getOwnerName())) {
                Unit.resetBehaviours(unit1);
                this.mob.startRiding(targetEntity);

                if (this.mob.level().isClientSide())
                    HudClientEvents.removeFromControlGroups(this.mob.getId());

                this.stop();
            }
        }
    }

    @Override
    public void stop() {
        this.autofind = false;
        this.stopMoving();
        this.setTarget(null);
    }
}
