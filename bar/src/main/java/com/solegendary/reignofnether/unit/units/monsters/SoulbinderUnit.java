package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.SoulChains;
import com.solegendary.reignofnether.ability.abilities.SoulHarvest;
import com.solegendary.reignofnether.ability.abilities.Wail;
import com.solegendary.reignofnether.essence.EssenceCarrier;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Gravebound essence caster - the <b>Soulbinder</b> (placeholder name; design/essence_casters.md, lovish approved draft 2
 * on Oct 29 2026). Trained at the Dungeon, the Gravebound T2 lab. It gathers the essence wisps the dead leave within 8
 * blocks (holds 30) and spends them: {@link Wail} (5), {@link SoulHarvest} (10), {@link SoulChains} (20) and the shared
 * {@link Empower} (15).
 * <p>
 * A Wraith underneath (placeholder look: the Wraith's body, sounds, sunburn and evasion, so it reads as Gravebound at
 * once) with its own abilities in place of Fear, Possess and Crypt Tide, its own cost and a little more health.
 */
public class SoulbinderUnit extends WraithUnit implements EssenceCarrier {

    public static final Abilities SOUL_ABILITIES = new Abilities();
    static {
        SOUL_ABILITIES.add(new Wail(), Keybindings.abilitySlot1);
        SOUL_ABILITIES.add(new SoulHarvest(), Keybindings.abilitySlot2);
        SOUL_ABILITIES.add(new SoulChains(), Keybindings.abilitySlot3);
        SOUL_ABILITIES.add(new Empower(), Keybindings.abilitySlot4);
    }

    /** The Wraith's 35 and a little more: it is worth protecting (the Witch and the Evoker have 40). */
    final static public float maxHealth = 40.0f;

    private Abilities soulAbilities = SOUL_ABILITIES.clone();

    public SoulbinderUnit(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    // the Wraith's constructor calls this before this class's fields are set: it fills them itself
    @Override
    public void updateAbilityButtons() {
        soulAbilities = SOUL_ABILITIES.clone();
    }

    @Override
    public Abilities getAbilities() {
        return soulAbilities;
    }

    @Nullable
    @Override
    public ResourceCost getCost() {
        return ResourceCosts.SOULBINDER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WraithUnit.createAttributes()
            .add(Attributes.MAX_HEALTH, SoulbinderUnit.maxHealth);
    }
}
