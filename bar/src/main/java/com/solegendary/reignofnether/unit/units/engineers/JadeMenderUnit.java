package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.abilities.MendingRite;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Stormcallers combat engineer - the <b>Jade Mender</b> (design/combat_engineers.md): a monk-artisan in red lacquer
 * and jade plates with a mender's mallet, trained at the Celestial Observatory. Light, and it pays more energy than the
 * others (energy is mana); its trick is <b>Mending Rite</b> - a channelled jade beam that turns mana into a fast heal on
 * a friend, broken the moment the Mender is hurt.
 */
public class JadeMenderUnit extends CombatEngineerUnit {

    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new MendingRite(), Keybindings.hotkey1);   // V: free on the Stormcallers' build menu
    }

    final static public float maxHealth = 90.0f;
    final static public float movementSpeed = 0.30f;

    public JadeMenderUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected Abilities engineerAbilities() {
        return ABILITIES;
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.JADE_MENDER; }

    public static AttributeSupplier.Builder createAttributes() {
        return CombatEngineerUnit.createAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed);
    }
}
