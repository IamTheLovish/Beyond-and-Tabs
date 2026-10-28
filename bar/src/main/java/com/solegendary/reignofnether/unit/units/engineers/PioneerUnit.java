package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Brassworks combat engineer - the <b>Pioneer</b> (design/combat_engineers.md): an armoured dwarf in a riveted brass
 * cuirass with a steam rig on his back, trained at the Assembly Works. The sturdiest and slowest engineer (a dwarf in
 * plate). No ability of its own (builder abilities were removed for every faction, 2026-10-26).
 */
public class PioneerUnit extends CombatEngineerUnit {

    final static public float maxHealth = 120.0f;
    final static public float movementSpeed = 0.28f;

    public PioneerUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.PIONEER; }

    public static AttributeSupplier.Builder createAttributes() {
        return CombatEngineerUnit.createAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed);
    }
}
