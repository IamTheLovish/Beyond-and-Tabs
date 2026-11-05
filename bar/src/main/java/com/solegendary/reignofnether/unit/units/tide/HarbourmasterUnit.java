package com.solegendary.reignofnether.unit.units.tide;

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
 * has one. Its Raise Great Tidepool power was removed (2026-10-26).
 * <p>
 * A subclass of the Shipwright (the Elder Druid / Seedshaper pattern): nothing finds the Shipwright by instanceof, so
 * the worker plumbing, amphibious body and TIDAL pathing carry over unchanged.
 */
public class HarbourmasterUnit extends ShipwrightUnit {

    public static final float BUILD_POWER = 2.0f;

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
