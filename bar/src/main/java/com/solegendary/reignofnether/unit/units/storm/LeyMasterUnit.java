package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Stormcallers T2 constructor - the <b>Ley Master</b> (BAR's T2 constructor; design/stormcallers_plan.md, slice 3).
 * Trained at the Celestial Observatory, builds at double speed with twice a Geomancer's health (like the Harbourmaster,
 * Elder Druid, Royal Architect, Embalmer and Bonewright), and is the only Stormcaller worker that may raise a T3 lab
 * (T2Workers) once the faction has one (slice 4: Heaven's Gate). Its Mana Well power was removed (2026-10-26).
 * <p>
 * A subclass of the Geomancer (the Harbourmaster / Elder Druid pattern): nothing finds the Geomancer by instanceof
 * except the bot's ley-linking habit, which skips Ley Masters on purpose, so the worker plumbing and Ley Link carry over.
 */
public class LeyMasterUnit extends GeomancerUnit {

    public static final float BUILD_POWER = 2.0f;

    public LeyMasterUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.LEY_MASTER; }

    @Override
    public float getBuildPower() {
        return com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(this) ? COMMANDER_BUILD_POWER : BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GeomancerUnit.createAttributes().add(Attributes.MAX_HEALTH, GeomancerUnit.maxHealth * 2);
    }
}
