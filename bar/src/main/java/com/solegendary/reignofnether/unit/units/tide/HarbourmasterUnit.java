package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.abilities.RaiseGreatTidepool;
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
 * Tidewrought T2 constructor - the <b>Harbourmaster</b> (BAR's T2 constructor; design/tidewrought_plan.md, slice 3).
 * Trained at the Drydock, builds at double speed with twice a Shipwright's health (like the Royal Architect, Embalmer,
 * Bonewright and Elder Druid), and is the only Tidewrought worker that may raise a T3 lab (T2Workers) once the faction
 * has one. Its own power is {@link RaiseGreatTidepool}: a radius-3 tidepool that lasts 45 s, through the same
 * registry and caps as the Tide Priest's.
 * <p>
 * A subclass of the Shipwright (the Elder Druid / Seedshaper pattern): nothing finds the Shipwright by instanceof, so
 * the worker plumbing, amphibious body and TIDAL pathing carry over unchanged.
 */
public class HarbourmasterUnit extends ShipwrightUnit {

    public static final float BUILD_POWER = 2.0f;

    // ShipwrightUnit clones its shared static set, so adding to that would hand every Shipwright the power: the
    // Harbourmaster keeps its own. No initializer on purpose - ShipwrightUnit's constructor fills it through
    // updateAbilityButtons() before this class' field initializers would run, and one would wipe it again.
    private Abilities harbourAbilities;

    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        // added only when missing: a re-run (client cooldown sync) keeps the previous one and any commander abilities
        Abilities prev = harbourAbilities;
        harbourAbilities = Abilities.cloneKeepingExtras(ShipwrightUnit.ABILITIES, prev);
        boolean has = false;
        for (var a : harbourAbilities.get())
            if (a instanceof RaiseGreatTidepool)
                has = true;
        if (!has)
            harbourAbilities.add(new RaiseGreatTidepool(), Keybindings.hotkey9);   // the build menu holds slots 1/5/6/9 and hotkeys 5-8
    }

    @Override
    public Abilities getAbilities() {
        if (harbourAbilities == null)
            updateAbilityButtons();
        return harbourAbilities;
    }

    public HarbourmasterUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.HARBOURMASTER; }

    @Override
    public float getBuildPower() {
        return com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(this) ? COMMANDER_BUILD_POWER : BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return ShipwrightUnit.createAttributes().add(Attributes.MAX_HEALTH, ShipwrightUnit.maxHealth * 2);
    }
}
