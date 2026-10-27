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
 * Tidewrought combat engineer - the <b>Salvager</b> (design/combat_engineers.md): a wreck diver with a brass helmet
 * ring and a coiled hook line, trained at the Drydock. Quick (the raiding faction) and middling in health; its trick is
 * <b>Salvage Hook</b> - it reels a wreck to its feet from sixteen blocks away, out of the enemy's reach.
 */
public class SalvagerUnit extends CombatEngineerUnit {

    final static public float maxHealth = 100.0f;
    final static public float movementSpeed = 0.31f;

    public SalvagerUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.SALVAGER; }

    public static AttributeSupplier.Builder createAttributes() {
        return CombatEngineerUnit.createAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed);
    }
}
