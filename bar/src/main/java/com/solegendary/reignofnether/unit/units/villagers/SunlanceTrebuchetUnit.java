package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The Sunlance Trebuchet, Sunforged's T2 siege unit (design/defences_tiers.md): trained at the Arcane Tower. The
 * counter audit (DefenceCounterGameTests) found Sunforged unable to out-reach its own T1-lab / Tier 2 towers below T3;
 * the Trebuchet hurls a lance of light from {@link SunlanceTrebuchetTuning#ATTACK_RANGE} blocks, beyond the anti-siege
 * tower's 20. Gravebound's T2 siege unit does the same job at the same price.
 * <p>
 * The T1 siege lobber's frame (SiegeLobberUnit: the bow-paced goal, the nearest-block building goal, Attack Ground,
 * the lobbed BombardShell that hits defences at the full rate, FieldArtillery's no-air and set-up) with every number
 * from {@link SunlanceTrebuchetTuning} and a flash of light at the launch. The body is a placeholder (the lobber's
 * illager, its own skin) until the art lane draws the engine.
 */
public class SunlanceTrebuchetUnit extends SiegeLobberUnit {

    final static public float attackDamage = SunlanceTrebuchetTuning.DAMAGE;
    final static public float attacksPerSecond = SunlanceTrebuchetTuning.ATTACKS_PER_SECOND;
    final static public float maxHealth = SunlanceTrebuchetTuning.MAX_HEALTH;
    final static public float armorValue = SunlanceTrebuchetTuning.ARMOR;
    final static public float movementSpeed = SunlanceTrebuchetTuning.MOVEMENT_SPEED;
    final static public float attackRange = SunlanceTrebuchetTuning.ATTACK_RANGE;
    final static public float aggroRange = SunlanceTrebuchetTuning.ATTACK_RANGE;

    public SunlanceTrebuchetUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    @Nullable
    public ResourceCost getCost() { return ResourceCosts.SUNLANCE_TREBUCHET; }

    @Override public float getMinRange() { return SunlanceTrebuchetTuning.MIN_RANGE; }
    @Override public int getSetUpTicks() { return SunlanceTrebuchetTuning.SET_UP_TICKS; }
    @Override protected float shellSplash() { return SunlanceTrebuchetTuning.SPLASH_RADIUS; }
    @Override protected int shellFlightTicks() { return SunlanceTrebuchetTuning.FLIGHT_TICKS; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, SunlanceTrebuchetUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, SunlanceTrebuchetUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, SunlanceTrebuchetUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), SunlanceTrebuchetTuning.SIGHT_RANGE)   // below its range: it needs spotters
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), SunlanceTrebuchetTuning.BUILDING_DAMAGE_BONUS)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** A lance of light loosed: the arm's thud, a bright chime and a spray of light, not the lobber's puff. */
    @Override
    protected void launchFx(ServerLevel sl) {
        this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.4F, 0.5F);
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.4F + getRandom().nextFloat() * 0.2F);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getEyeY() + 0.4, getZ(), 10, 0.3, 0.2, 0.3, 0.04);
        sl.sendParticles(ParticleTypes.WAX_OFF, getX(), getEyeY() + 0.6, getZ(), 4, 0.2, 0.2, 0.2, 0.02);
    }
}
