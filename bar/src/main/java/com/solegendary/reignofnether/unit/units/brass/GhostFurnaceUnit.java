package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.ability.abilities.GhostFurnace;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.units.monsters.AbstractTotem;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * The Soul Engineer's <b>Ghost Furnace</b> (ability/abilities/GhostFurnace; design/essence_casters.md Revision 1): a
 * spirit-steam furnace that a fallen soul stokes. A still unit of the caster's owner, {@link #HEALTH} HP with no armour
 * and no resists, so the enemy can pick it and knock it down at the unit rate - except siege artillery, which hits it as
 * it hits a defence ({@link #siegeAmount}). Once a second for {@link #LIFETIME_TICKS} it scalds the enemy ground units
 * round it ({@link GhostFurnace#scald}), then it cools and is discarded (not killed). Marked summoned by the spell, so
 * its death drops no essence. Never heats or vents: it is not a {@link com.solegendary.reignofnether.brass.Machine}.
 * <p>
 * A totem's plumbing ({@link AbstractTotem}: no cost, population or goals, unpushable, a lifespan bar) with no aura and
 * no beacon hum. Its age is saved with it, so a furnace that is reloaded keeps its own clock.
 */
public class GhostFurnaceUnit extends AbstractTotem {

    public static final float HEALTH = 150f;
    public static final int LIFETIME_TICKS = 20 * 20;
    static final String KEY_AGE = "bt_ghost_furnace_age";

    private int age = 0;   // server ticks since it was set

    public GhostFurnaceUnit(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        // the totem's own expiry is only a backstop: tick() cools the furnace at its lifetime
        this.lifeTimeTicks = LIFETIME_TICKS + 20;
        // no aura effects and no beacon hum (the tombstone's rule)
        this.auraDurationTicks = Integer.MAX_VALUE;
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

    /** For the game test: jump the clock (a 20 s lifetime is long to wait for). */
    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive() || isRemoved())
            return;
        age++;
        if (age % GhostFurnace.SCALD_TICKS == 0 && age <= LIFETIME_TICKS)
            GhostFurnace.scald(sl, this, getOwnerName(), GhostFurnace.SCALD_DAMAGE);
        if (age >= LIFETIME_TICKS) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 12, 0.4, 0.4, 0.4, 0.05);
            sl.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1f, 0.8f);
            discard();
        }
    }

    /**
     * Revision 1: siege lobbers hit tombstones and Ghost Furnaces "at the defence rate". A shell from artillery with a
     * building damage bonus (the lobber's +3.5) takes off the furnace what it takes off a defence: its unit damage times
     * the shell's building multiple times (1 + the bonus), in HUD health (x2, RaidTuning) - 54 for a lobber's 4 at the
     * centre. Anything else hits it as a unit.
     */
    public static float siegeAmount(DamageSource src, float amount) {
        if (src.getDirectEntity() instanceof BombardShell && src.getEntity() instanceof LivingEntity shooter) {
            AttributeInstance bonus = shooter.getAttribute(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get());
            if (bonus != null && bonus.getValue() > 0)
                return amount * BombardShell.BUILDING_DAMAGE_MULT * (float) (1 + bonus.getValue()) * 2f;
        }
        return amount;
    }

    @Override
    public boolean hurt(@NotNull DamageSource src, float amount) {
        return super.hurt(src, siegeAmount(src, amount));
    }

    /** The ring shows the scald, not the totems' 10-block aura. */
    @Override
    public void updateHighlightBps(Level level) {
        if (level() != null && level().isClientSide())
            setHighlightBps(new HashSet<BlockPos>(MiscUtil.getRangeIndicatorCircleBlocks(blockPosition(), GhostFurnace.RADIUS - 1, level())));
    }

    @Override protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.COPPER_HIT;
    }
    @Override protected SoundEvent getDeathSound() {
        return SoundEvents.COPPER_BREAK;
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
