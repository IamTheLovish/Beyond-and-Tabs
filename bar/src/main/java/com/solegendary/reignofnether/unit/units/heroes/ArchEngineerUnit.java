package com.solegendary.reignofnether.unit.units.heroes;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.HeroCore;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.units.brass.RifleSquadUnit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Brassworks hero - the <b>Arch-Engineer</b>: the Rifle Squad's dwarven musketeer body (its hit-scan musket) in a
 * guild master's brass harness and rocket boots (a skirmisher). Rivet Burst, Rocket Jump, Deploy Turret and, from level
 * 6, Field Repair for every machine nearby.
 * <p>
 * A body plus a kit (hero/FactionHero explains the pattern): everything hero - level, boons, ranks, mana, abilities,
 * cast goal - lives in its {@link HeroCore}; the numbers live in {@link HeroKits#ARCH_ENGINEER}.
 */
public class ArchEngineerUnit extends RifleSquadUnit implements FactionHero {

    // no initializer on purpose: the body's constructor asks for abilities before this class's fields exist, and an
    // initializer would wipe the core heroCore() built then (the Harbourmaster pattern)
    private HeroCore heroCore;

    public ArchEngineerUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public HeroCore heroCore() {
        if (heroCore == null)
            heroCore = new HeroCore(HeroKits.ARCH_ENGINEER, this);
        return heroCore;
    }

    @Override
    public Abilities getAbilities() { return heroCore().abilitySet; }

    // the hero's abilities are its own instances (ranks and cooldowns are keyed by them): never re-cloned
    @Override
    public void updateAbilityButtons() { }

    @Nullable
    @Override
    public ResourceCost getCost() { return HeroKits.ARCH_ENGINEER.cost; }

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
        return FactionHero.heroAttributes(RifleSquadUnit.createAttributes(), HeroKits.ARCH_ENGINEER);
    }
}
