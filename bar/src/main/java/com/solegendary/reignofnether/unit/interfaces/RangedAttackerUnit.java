package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.unit.goals.RangedAttackGroundGoal;
import net.minecraft.world.entity.LivingEntity;

public interface RangedAttackerUnit {

    int FOG_REVEAL_TICKS_MAX = 60;

    int getFogRevealDuration();
    void setFogRevealDuration(int duration);

    default RangedAttackGroundGoal<?> getRangedAttackGroundGoal() { return null; }

    /** UnitRangedAttackGoal starts a wind-up at {@code target} (server, its first tick); a unit that animates the draw
     *  itself hears it here (the Seraph Archer's client rig cue). */
    default void onRangedWindup(LivingEntity target) { }

    default void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        double x = pTarget.getX();
        double y = pTarget.getY();
        double z = pTarget.getZ();
        performUnitRangedAttack(x, y, z, velocity);
    }

    // attack ground
    default void performUnitRangedAttack(double x, double y, double z, float velocity) { }
}
