package com.solegendary.reignofnether.unit.units.heroes;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.HeroCore;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.units.villagers.WindcallerUnit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Stormcallers hero - the <b>Storm Witch</b>: the Windcaller's body and its storm cloud in sky-silk and lantern
 * gold (a caster that flies). Chain Lightning, Cloud Ride (the Windcaller's flight: up and over walls, a flyer to every
 * anti-air), Frost Nova and, from level 6, Mana Font: energy back and every friend's cooldowns cut.
 * <p>
 * A body plus a kit (hero/FactionHero explains the pattern): everything hero - level, boons, ranks, mana, abilities,
 * cast goal - lives in its {@link HeroCore}; the numbers live in {@link HeroKits#STORM_WITCH}.
 */
public class StormWitchUnit extends WindcallerUnit implements FactionHero {

    // no initializer on purpose: the body's constructor asks for abilities before this class's fields exist, and an
    // initializer would wipe the core heroCore() built then (the Harbourmaster pattern)
    private HeroCore heroCore;

    public StormWitchUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public HeroCore heroCore() {
        if (heroCore == null)
            heroCore = new HeroCore(HeroKits.STORM_WITCH, this);
        return heroCore;
    }

    @Override
    public Abilities getAbilities() { return heroCore().abilitySet; }

    // the hero's abilities are its own instances (ranks and cooldowns are keyed by them): never re-cloned
    @Override
    public void updateAbilityButtons() { }

    @Nullable
    @Override
    public ResourceCost getCost() { return HeroKits.STORM_WITCH.cost; }

    @Override
    public void tick() {
        super.tick();
        FactionHero.tickHero(this);
    }

    @Override
    public void resetBehaviours() {
        super.resetBehaviours();
        FactionHero.resetHero(this);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return super.hurt(source, amount * heroCore().damageTakenMult);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        addHeroUnitSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        FactionHero.loadHero(this, tag);
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        FactionHero.onRemoved(this);
        super.remove(reason);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FactionHero.heroAttributes(WindcallerUnit.createAttributes(), HeroKits.STORM_WITCH);
    }
}
