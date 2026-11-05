package com.solegendary.reignofnether.unit.units.heroes;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.HeroCore;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.units.piglins.BruteUnit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Horde T2-lab hero - the <b>Ash Shaman</b> (H13a, design/heroes-t2-lab.md section 4): trained at the Flame Sanctuary,
 * spawns at level 3, a fire caster. Firewall lays a burning line, Lava Geyser erupts after a telegraph, Ember Totem
 * burns whatever stands near it, and Kindle (passive) sets every target it hits alight.
 * <p>
 * PLACEHOLDER BODY: the Brute's piglin body and the Warchief's skin (HeroRenderers.AshShaman) until the real model
 * arrives. A body plus a kit, as {@link HordeWarchiefUnit}; the numbers live in {@link HeroKits#ASH_SHAMAN}.
 */
public class AshShamanUnit extends BruteUnit implements FactionHero {

    // no initializer on purpose (see HordeWarchiefUnit): the body's constructor asks for abilities first
    private HeroCore heroCore;

    public AshShamanUnit(EntityType<? extends PiglinBrute> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public HeroCore heroCore() {
        if (heroCore == null)
            heroCore = new HeroCore(HeroKits.ASH_SHAMAN, this);
        return heroCore;
    }

    @Override
    public Abilities getAbilities() { return heroCore().abilitySet; }

    @Override
    public void updateAbilityButtons() { }

    @Nullable
    @Override
    public ResourceCost getCost() { return HeroKits.ASH_SHAMAN.cost; }

    /** Kindle (passive, ability 4): every hit sets the target alight for a (2 s, +1 s per rank). */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le && !level().isClientSide()) {
            int rank = Math.max(1, Math.min(3, heroCore().abilities.get(3).getRank(this)));
            le.setSecondsOnFire(Math.round(HeroKits.ASH_SHAMAN.abilities[3].a[rank - 1]));
        }
        return hit;
    }

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
        return FactionHero.heroAttributes(BruteUnit.createAttributes(), HeroKits.ASH_SHAMAN);
    }
}
