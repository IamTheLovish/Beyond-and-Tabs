//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.units.villagers.ScoutCatUnit;
import com.solegendary.reignofnether.unit.units.villagers.ScoutDogUnit;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class RandomLookAroundUnitGoal extends Goal {
    private final Mob mob;
    private double relX;
    private double relZ;
    private int lookTime;

    public RandomLookAroundUnitGoal(Mob pMob) {
        this.mob = pMob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public boolean canUse() {
        if (mob.hasEffect(MobEffectRegistrar.FREEZE.get()) ||
            mob.hasEffect(MobEffectRegistrar.STUN.get()))
            return false;

        if (mob instanceof ScoutDogUnit scoutDogUnit && scoutDogUnit.isInSittingPose())
            return false;
        if (mob instanceof ScoutCatUnit scoutCatUnit && scoutCatUnit.isInSittingPose())
            return false;

        if (mob instanceof AttackerUnit attackerUnit)
            if (attackerUnit.getAttackBuildingGoal() instanceof MeleeAttackBuildingGoal mabg && mabg.isAttacking())
                return false;

        if (mob instanceof AttackerUnit attackerUnit)
            if (attackerUnit.getAttackBuildingGoal() instanceof MeleeAttackBuildingGoal mabg && mabg.isAttacking())
                return false;

        if (ordered())
            return false;

        return this.mob.getRandom().nextFloat() < 0.02F;
    }

    public boolean canContinueToUse() {
        return this.lookTime >= 0 && !ordered();
    }

    /**
     * A move order is in hand: the move goal has a target, or its RTS path is still on its way. No glance then. Several
     * units (Vindicator, Enchanter, Royal Guard, Slime) register their move goal in the targetSelector, which ticks
     * before this goal's goalSelector, so a running glance overwrote the head the move goal aimed every tick, and the
     * idle body followed it off the line while the path was pending (review round 1 on #512).
     */
    private boolean ordered() {
        if (!(mob instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit))
            return false;
        MoveToTargetBlockGoal move = unit.getMoveGoal();
        return move != null && (move.getMoveTarget() != null || move.isPathPending());
    }

    public void start() {
        double $$0 = 6.283185307179586 * this.mob.getRandom().nextDouble();
        this.relX = Math.cos($$0);
        this.relZ = Math.sin($$0);
        this.lookTime = 20 + this.mob.getRandom().nextInt(20);
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        --this.lookTime;
        this.mob.getLookControl().setLookAt(this.mob.getX() + this.relX, this.mob.getEyeY(), this.mob.getZ() + this.relZ);
    }
}
