package com.solegendary.reignofnether.unit.modelling.animations;

import com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal;
import com.solegendary.reignofnether.unit.interfaces.ArmSwingingUnit;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * What the human bodies that replaced the villager body (Oct 21: the Dome Cleric, the Royal Architect, the Field
 * Mason, the Dawn Paladin - tools/human_kit.py) still do the villager model's way, at its old timing, until the shared
 * human base has clips for them (design/ANIMATION-STANDARD: no unit gets an attack or a work clip of its own before
 * its class has a base one): the work swing of a builder (ArmSwingingUnit's keyed arm rotations, advanced per frame
 * exactly as VillagerUnitModel advances them) and the illager's overhead chop (AnimationUtils.swingWeaponDown's curve
 * over attackTime), both applied ON TOP of the base pose instead of replacing a vanilla arm's rotation.
 */
@OnlyIn(Dist.CLIENT)
public final class HumanActs {
    private HumanActs() { }

    /** A worker building or gathering (VillagerUnitModel's BUILDING / GATHERING arm poses). */
    public static boolean working(Entity entity) {
        return entity instanceof WorkerUnit w
            && ((w.getBuildRepairGoal() != null && w.getBuildRepairGoal().isBuilding())
                || (w.getGatherResourceGoal() != null && w.getGatherResourceGoal().isGathering()));
    }

    /** It has a target (a unit, or a building it is hitting) - VillagerUnitModel's ATTACKING arm pose. */
    public static boolean fighting(Entity entity) {
        if (entity instanceof Unit unit && unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null)
            return true;
        return entity instanceof AttackerUnit a && a.getAttackBuildingGoal() instanceof MeleeAttackBuildingGoal g
            && g.getBuildingTarget() != null;
    }

    /**
     * The work swing (VillagerUnitModel: ArmSwingingUnit.getNextArmRot, its swing time advanced each frame and reset
     * at SWING_TIME_MAX): the keyed rotations ADDED to the tool arm, so the swing starts from the base pose. Returns
     * true while it swings.
     */
    public static boolean workSwing(Entity entity, ModelPart arm) {
        if (!(entity instanceof ArmSwingingUnit swinger) || arm == null)
            return false;
        if (!swinger.isSwingingArmRepeatedly() && !swinger.isSwingingArmOnce())
            return false;
        List<Float> rot = swinger.getNextArmRot();
        arm.xRot += rot.get(0);
        arm.yRot += rot.get(1);
        arm.zRot += rot.get(2);
        int swingTime = swinger.getSwingTime();
        swinger.setSwingTime(swingTime + 1);
        if (swingTime >= ArmSwingingUnit.SWING_TIME_MAX) {
            swinger.setSwingTime(0);
            if (swinger.isSwingingArmOnce())
                swinger.setSwingingArmOnce(false);
        }
        return true;
    }

    /**
     * The overhead chop at the illager's timing (AnimationUtils.swingWeaponDown: the raise sin((1 - (1 - t)^2) pi),
     * the blow sin(t pi)), added to the weapon arm from the base pose; the forearm opens through the blow and the
     * trunk turns into it.
     */
    public static void chop(float attackTime, ModelPart arm, ModelPart fore, ModelPart body) {
        if (attackTime <= 0f || arm == null)
            return;
        float blow = Mth.sin(attackTime * Mth.PI);
        float raise = Mth.sin((1f - (1f - attackTime) * (1f - attackTime)) * Mth.PI);
        arm.xRot += -2.2f * raise + 1.1f * blow;
        arm.yRot += 0.15f * raise;
        if (fore != null)
            fore.xRot += -0.6f * raise + 0.3f * blow;
        if (body != null) {
            body.yRot += 0.25f * raise - 0.35f * blow;
            body.xRot += 0.12f * blow;
        }
    }
}
