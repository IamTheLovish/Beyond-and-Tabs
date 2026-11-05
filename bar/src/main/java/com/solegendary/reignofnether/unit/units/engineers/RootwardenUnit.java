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
 * Verdant Court combat engineer - the <b>Rootwarden</b> (design/combat_engineers.md): an elf warden in bark mail with
 * moss on the shoulders and a root crook, trained at the Circle of Elders. The Court is the mobility faction, so it is
 * the fastest engineer and among the lightest; its trick is <b>Rootwalk</b> - it sinks into the roots and rises beside
 * one of its side's buildings.
 */
public class RootwardenUnit extends CombatEngineerUnit {

    final static public float maxHealth = 90.0f;
    final static public float movementSpeed = 0.32f;

    public RootwardenUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.ROOTWARDEN; }

    public static AttributeSupplier.Builder createAttributes() {
        return CombatEngineerUnit.createAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed);
    }
}
