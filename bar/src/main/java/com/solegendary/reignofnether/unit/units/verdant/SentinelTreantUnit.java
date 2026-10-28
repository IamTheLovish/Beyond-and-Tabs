package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Verdant Court tank - the <b>Sentinel Treant</b> (design/verdant_court_plan.md, slice 1): a walking oak on the Iron
 * Golem's body and brain (the Sun Colossus extends it the same way), drawn with its own bark-and-moss texture by
 * SentinelTreantRenderer. Tougher than the Kingdom's golem and soaks arrows, but hits softer and walks slower: it holds
 * the line while Thornbows root what comes at it. "Takes root" (a stationary armour toggle) waits for a later slice.
 */
public class SentinelTreantUnit extends IronGolemUnit {

    final static public float attackDamage = 10.0f;   // 8 -> 10 with 190M -> 170M (balance-pass): it lost to every raider 1.6-1.8x
    final static public float attacksPerSecond = 0.4f;
    final static public float maxHealth = 140.0f;
    final static public float movementSpeed = 0.20f;
    final static public float attackRange = 3;
    final static public float aggroRange = 10;
    final static public float rangedDamageResist = 0.4f;

    // ---- Take Root (vehicles-design 2026-10-28, 3.4): STUB for the red run, filled in by the next commit ----------------
    public record RootTimes(int rootTicks, int uprootTicks) { }
    public static final RootTimes ROOT_TIMES = new RootTimes(30, 20);
    public static final float ROOTED_TAKEN_MULT = 0.7f;
    public static final float LINE_LENGTH = 8f;
    public static final float LINE_DAMAGE = 8f;
    public static final int LINE_EVERY = 40;
    public static final int LINE_TELEGRAPH_TICKS = 6;
    public int linesFired = 0;
    public void toggleRoot() { }
    public void tickRoot(net.minecraft.server.level.ServerLevel sl) { }
    public boolean isRootWanted() { return false; }
    public boolean isRootWantedClient() { return false; }
    public boolean isRooted() { return false; }
    public boolean isImmobile() { return false; }
    public int strikeRootLine(net.minecraft.server.level.ServerLevel sl, double dx, double dz) { return 0; }

    public SentinelTreantUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.SENTINEL_TREANT; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), com.solegendary.reignofnether.raid.RaidTuning.vehicleBuildingBonus(attackDamage, attacksPerSecond));   // T1 vehicle: breaks buildings (RaidTuning)
    }

    // creaking wood rather than clanking iron
    @Override
    protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AZALEA_LEAVES_BREAK;
    }
}
