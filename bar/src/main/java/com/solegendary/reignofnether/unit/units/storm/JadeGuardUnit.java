package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.mana.Blink;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Stormcallers line infantry - the <b>Jade Guard</b> (design/stormcallers_plan.md, slice 1): lacquered lamellar and a
 * jade tower shield on the illager body (JadeGuardRenderer). <b>Jade Mirror</b> (passive): projectile damage
 * (DamageTypeTags.IS_PROJECTILE, the test Bastion Aegis uses) is cut by {@link #MIRROR_CUT}, and {@link #MIRROR_REFLECT}
 * of the hit is sent back to the shooter as indirect magic if it stands within {@link #MIRROR_RANGE} blocks. Never onto
 * a friend (the shooter must be an enemy unit), and never chained: the reflection is magic, not a projectile, so a
 * second Guard does not mirror it again. Melee passes untouched - the Guard is the answer to archers and skirmishers,
 * not a brawler. Extends VindicatorUnit for the melee brain and attack sync, as the Reef Guard does.
 * <p>
 * Cost: one instanceof check per hurt event server-wide ({@link StormUnitEvents}), the rest only for a Guard hit by a
 * projectile. No per-tick work, no packets beyond a few particles per mirrored hit.
 */
public class JadeGuardUnit extends VindicatorUnit {

    final static public float attackDamage = 5.5f;
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 75.0f;
    final static public float movementSpeed = 0.25f;
    final static public float attackRange = 2;
    final static public float aggroRange = 10;
    final static public float rangedDamageResist = 0.0f;   // the shield's work is the Mirror below, not a flat resist

    /** Jade Mirror: share of projectile damage the shield stops ... */
    public static final float MIRROR_CUT = 0.4f;
    /** ... share of the hit sent back to the shooter ... */
    public static final float MIRROR_REFLECT = 0.3f;
    /** ... if it stands this close. */
    public static final double MIRROR_RANGE = 24;

    public JadeGuardUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.JADE_GUARD; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /**
     * Jade Mirror, for one hurt event on {@code guard}: returns the damage that gets through and sends the reflection.
     * Anything but a projectile passes unchanged. Public for the game test (StormUnitEvents calls it for real hits).
     */
    public static float mirror(JadeGuardUnit guard, DamageSource src, float amount) {
        if (amount <= 0 || !src.is(DamageTypeTags.IS_PROJECTILE) || !(guard.level() instanceof ServerLevel sl))
            return amount;
        String owner = guard.getOwnerName();
        Entity shooter = src.getEntity();
        if (shooter instanceof LivingEntity le && le != guard && le.isAlive() && le.level() == sl
                && le instanceof Unit su && owner != null && Blink.isEnemy(owner, su)
                && le.distanceToSqr(guard) <= MIRROR_RANGE * MIRROR_RANGE) {
            // indirect magic with the Guard as its source: not a projectile, so another Guard never mirrors it back
            le.hurt(sl.damageSources().indirectMagic(guard, guard), amount * MIRROR_REFLECT);
            sl.sendParticles(ParticleTypes.GLOW, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
        }
        sl.sendParticles(ParticleTypes.WAX_OFF, guard.getX(), guard.getY() + 1.1, guard.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
        sl.playSound(null, guard.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 0.8f, 1.5f);
        return amount * (1 - MIRROR_CUT);
    }

    // a guard of the dynasty, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.SHIELD_BLOCK;
    }

    // a jian and the tower shield; visual only (damage is RoN's attack attribute, the shield is the Mirror above)
    @Override
    public void setupEquipmentAndUpgradesServer() {
        if (hasAnyEnchant())
            return;
        this.setItemSlot(EquipmentSlot.MAINHAND, com.solegendary.reignofnether.unit.interfaces.AttackerUnit.cosmeticWeapon(Items.IRON_SWORD));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }
}
