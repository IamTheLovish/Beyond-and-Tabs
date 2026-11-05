package com.solegendary.reignofnether.unit.goals;

/**
 * A ranged attack goal's shot cooldown, as {@link RangedAttackBuildingGoal} reads it: the bow goal (arrows, tridents,
 * shells) and the hit-scan goal (the Rifle Squad's musket, the Seraph Archer's bolt) both pace a building's shots
 * the way they pace a unit's.
 */
public interface RangedCooldown {
    float getAttackCooldown();
    void setToMaxAttackCooldown();
}
