package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Brassworks T2 constructor - the <b>Engineer-Captain</b> (BAR's T2 constructor; design/brassworks_plan.md, slice 2).
 * Trained at the Assembly Works, builds at double speed with twice a Tinker's health (like the Ley Master,
 * Harbourmaster, Elder Druid, Royal Architect, Embalmer and Bonewright), and is the only Brassworks worker that may
 * raise a T3 lab (T2Workers) once the faction has one. Its own trade is the machines: it mends a damaged machine next
 * to it at {@link com.solegendary.reignofnether.brass.MachineRepair#ENGINEER_MULT}x a Tinker's rate
 * (MachineRepair.hpPerSecond), and keeps the Tinker's Field Upgrade.
 * <p>
 * A subclass of the Tinker (the Ley Master / Harbourmaster pattern): the worker plumbing, the Field Upgrade and the
 * machine repair carry over; what finds the Tinker by instanceof (the bot's plating habit) is happy to use it too.
 */
public class EngineerCaptainUnit extends TinkerUnit {

    public static final float BUILD_POWER = 2.0f;

    public EngineerCaptainUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.ENGINEER_CAPTAIN; }

    @Override
    public float getBuildPower() {
        return com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(this) ? COMMANDER_BUILD_POWER : BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TinkerUnit.createAttributes().add(Attributes.MAX_HEALTH, TinkerUnit.maxHealth * 2);
    }
}
