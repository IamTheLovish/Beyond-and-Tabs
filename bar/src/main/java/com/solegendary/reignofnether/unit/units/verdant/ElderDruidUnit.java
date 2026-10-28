package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Verdant Court T2 constructor - the <b>Elder Druid</b> (BAR's T2 constructor; design/verdant_court_plan.md, slice 4).
 * Trained at the Circle of Elders, builds at double speed with twice a Seedshaper's health (like the Royal Architect,
 * Embalmer and Bonewright), and is the only Court worker that may raise a T3 lab (T2Workers). Its Awaken Thicket power was removed (2026-10-26).
 */
public class ElderDruidUnit extends SeedshaperUnit {

    public static final float BUILD_POWER = 2.0f;

    public ElderDruidUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.ELDER_DRUID; }

    @Override
    public float getBuildPower() {
        return com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(this) ? COMMANDER_BUILD_POWER : BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return SeedshaperUnit.createAttributes().add(Attributes.MAX_HEALTH, SeedshaperUnit.maxHealth * 2);
    }
}
