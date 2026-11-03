package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.abilities.SeedOfRenewal;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.units.monsters.AbstractTotem;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * The sapling that the Thornweaver's <b>Seed of Renewal</b> plants (design/essence_casters.md, Revision 1: "the Seed
 * sapling has 100 HP and can be targeted"). It is a unit of the caster's owner: the enemy can see it, pick it and cut it
 * down. It has {@link #HEALTH} HP and no damage resists, so 100 means 100. Once a second for
 * {@link SeedOfRenewal#LIFETIME_TICKS} ticks it heals friends near it ({@link SeedOfRenewal#pulse}), then it withers.
 * When it withers it is discarded, not killed. It is marked summoned, so no death of it drops essence.
 * <p>
 * A totem's plumbing ({@link AbstractTotem}: a still, unpushable unit with no cost, population or goals, and a
 * lifespan bar), with no aura effects. Its age is saved with it, so a sapling that is reloaded keeps its own clock.
 */
public class RenewalSaplingUnit extends AbstractTotem {

    public static final float HEALTH = 100f;
    static final String KEY_AGE = "bt_renewal_age";

    private int age = 0;   // server ticks since it was planted

    public RenewalSaplingUnit(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        // the totem's own expiry is only a backstop: tick() withers the sapling at its lifetime
        this.lifeTimeTicks = SeedOfRenewal.LIFETIME_TICKS + 20;
        // no aura effects; this also keeps the totem's beacon hum to the moment it is planted
        this.auraDurationTicks = this.lifeTimeTicks + 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractTotem.createAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    public int getAge() {
        return age;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive() || isRemoved())
            return;
        age++;
        if (age % SeedOfRenewal.PULSE_TICKS == 0 && age <= SeedOfRenewal.LIFETIME_TICKS)
            SeedOfRenewal.pulse(sl, this, getOwnerName(), SeedOfRenewal.HEAL_PER_SECOND);
        if (age >= SeedOfRenewal.LIFETIME_TICKS) {
            sl.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, getX(), getY() + 0.8, getZ(), 8, 0.4, 0.3, 0.4, 0.0);
            sl.playSound(null, blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 1f, 0.8f);
            discard();
        }
    }

    /** The ring shows the healing circle, not the totems' 10-block aura. */
    @Override
    public void updateHighlightBps(Level level) {
        if (level() != null && level().isClientSide())
            setHighlightBps(new HashSet<BlockPos>(MiscUtil.getRangeIndicatorCircleBlocks(blockPosition(), SeedOfRenewal.RADIUS - 1, level())));
    }

    @Override protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.AZALEA_LEAVES_HIT;
    }
    @Override protected SoundEvent getDeathSound() {
        return SoundEvents.AZALEA_LEAVES_BREAK;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        pCompound.putInt(KEY_AGE, age);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        age = pCompound.getInt(KEY_AGE);
    }
}
