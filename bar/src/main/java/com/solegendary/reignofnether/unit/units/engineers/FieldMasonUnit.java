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
 * Sunforged combat engineer - the <b>Field Mason</b> (design/combat_engineers.md): a mason of the cathedral works in
 * white plate with gold trim and a pavise slung on his back, trained at the Arcane Tower. Plate makes it the sturdiest
 * and among the slowest of the engineers; its trick is <b>Bulwark</b> - a friendly building takes half damage from
 * attackers while the Mason stands by it.
 */
public class FieldMasonUnit extends CombatEngineerUnit {

    final static public float maxHealth = 110.0f;
    final static public float movementSpeed = 0.29f;

    public FieldMasonUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.FIELD_MASON; }

    public static AttributeSupplier.Builder createAttributes() {
        return CombatEngineerUnit.createAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed);
    }
}
