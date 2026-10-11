package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.ability.abilities.BlinkStrike;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Stormcallers raider - the <b>Blade Dancer</b> (design/stormcallers_plan.md, slice 1): a dancer in red silk with twin
 * sabres on the illager body (its own skin, BladeDancerRenderer), costed against the Leafblade and the Cutlass Raider.
 * {@link BlinkStrike} teleports it onto an enemy up to {@link BlinkStrike#RANGE} blocks away; in a fight it does so by
 * itself onto the nearest enemy archer (the autocast, paid quietly from the energy pool with a reserve kept for the
 * Thunder Sage). Extends VindicatorUnit for the melee brain and the attack animation sync, as the Leafblade does.
 * <p>
 * Cost at 8v8: the autocast is looked at every {@link #AUTOCAST_TICKS} ticks, only while the Dancer has a target and its
 * Blink Strike is off cooldown, and then costs one grid query into a shared scratch list.
 */
public class BladeDancerUnit extends VindicatorUnit {

    final static public float attackDamage = 5.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float maxHealth = 52.0f;   // between the Leafblade (50) and the Cutlass Raider (55)
    final static public float movementSpeed = 0.32f;
    final static public float attackRange = 2;
    final static public float aggroRange = 10;
    final static public float rangedDamageResist = 0.1f;

    /** How often a fighting Dancer looks for an archer to blink onto. */
    public static final int AUTOCAST_TICKS = 10;
    private static final List<LivingEntity> SCRATCH = new ArrayList<>();   // server thread only

    public BladeDancerUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.BLADE_DANCER; }

    // one Blink Strike per Dancer, kept across refreshes so its cooldown (keyed by the ability) survives them. No
    // initializer on purpose: the first refresh runs inside VindicatorUnit's constructor, before field initializers
    private BlinkStrike blinkStrike;

    /** The Dancer's Blink Strike (null only during construction). Public for the game test. */
    public BlinkStrike getBlinkStrike() { return blinkStrike; }

    /** VindicatorUnit rebuilds its abilities from its shared static set here; add this unit's Blink Strike on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        if (blinkStrike == null)
            blinkStrike = new BlinkStrike();
        if (!getAbilities().get().contains(blinkStrike))
            getAbilities().add(blinkStrike, com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot1);
    }

    @Override
    public void tick() {
        super.tick();
        if (blinkStrike != null && level() instanceof ServerLevel sl && isAlive()
                && (tickCount + getId()) % AUTOCAST_TICKS == 0 && inFight())
            blinkStrike.autocast(sl, this, this, SCRATCH);
    }

    /** In a fight: it has an attack target (the RTS target goal's, or the mob's own while the goal hands over). */
    boolean inFight() {
        return getTarget() != null || (getTargetGoal() != null && getTargetGoal().getTarget() != null);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // a dancer of the dynasty, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    // twin sabres (a gold sword in each hand); visual only - damage is RoN's attack attribute, and cosmeticWeapon
    // pins the item's own damage to +0 so it can never leak into the melee hit
    @Override
    public void setupEquipmentAndUpgradesServer() {
        if (hasAnyEnchant())
            return;
        this.setItemSlot(EquipmentSlot.MAINHAND, com.solegendary.reignofnether.unit.interfaces.AttackerUnit.cosmeticWeapon(Items.GOLDEN_SWORD));
        this.setItemSlot(EquipmentSlot.OFFHAND, com.solegendary.reignofnether.unit.interfaces.AttackerUnit.cosmeticWeapon(Items.GOLDEN_SWORD));
    }
}
