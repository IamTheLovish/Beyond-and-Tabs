package com.solegendary.reignofnether.unit.units.monsters;

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
 * The Soul Pyre, Gravebound's T2 siege unit (design/defences_tiers.md; lovish, Nov 5: "we can do moving massive flame
 * pit that conjurs magic and souls that can be a tier 2 seige unit"). Trained at the Dungeon. The counter audit
 * (DefenceCounterGameTests) found Gravebound the one faction that could not out-reach its own T1-lab / Tier 2 towers;
 * the Pyre lobs a conjured soul from {@link SoulPyreTuning#ATTACK_RANGE} blocks, beyond the anti-siege tower's 20.
 * <p>
 * The T1 siege lobber's frame (SiegeLobberUnit: the bow-paced goal, the nearest-block building goal, Attack Ground,
 * the lobbed BombardShell that hits defences at the full rate, FieldArtillery's no-air and set-up) with every number
 * from {@link SoulPyreTuning} and a soul-fire launch. The body is a placeholder (the lobber's illager, its own skin)
 * until the animation lane draws the pit.
 */
public class SoulPyreUnit extends SiegeLobberUnit {

    final static public float attackDamage = SoulPyreTuning.DAMAGE;
    final static public float attacksPerSecond = SoulPyreTuning.ATTACKS_PER_SECOND;
    final static public float maxHealth = SoulPyreTuning.MAX_HEALTH;
    final static public float armorValue = SoulPyreTuning.ARMOR;
    final static public float movementSpeed = SoulPyreTuning.MOVEMENT_SPEED;
    final static public float attackRange = SoulPyreTuning.ATTACK_RANGE;
    final static public float aggroRange = SoulPyreTuning.ATTACK_RANGE;

    public SoulPyreUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    @Nullable
    public ResourceCost getCost() { return ResourceCosts.SOUL_PYRE; }

    @Override public float getMinRange() { return SoulPyreTuning.MIN_RANGE; }
    @Override public int getSetUpTicks() { return SoulPyreTuning.SET_UP_TICKS; }
    @Override protected float shellSplash() { return SoulPyreTuning.SPLASH_RADIUS; }
    @Override protected int shellFlightTicks() { return SoulPyreTuning.FLIGHT_TICKS; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, SoulPyreUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, SoulPyreUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, SoulPyreUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), SoulPyreTuning.SIGHT_RANGE)   // below its range: it needs spotters
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), SoulPyreTuning.BUILDING_DAMAGE_BONUS)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** A soul torn out of the pit: a low roar and a plume of soul fire, not the lobber's puff. */
    @Override
    protected void launchFx(ServerLevel sl) {
        this.playSound(SoundEvents.SOUL_ESCAPE, 1.6F, 0.6F + getRandom().nextFloat() * 0.2F);
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 0.5F);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getEyeY() + 0.4, getZ(), 10, 0.3, 0.2, 0.3, 0.03);
        sl.sendParticles(ParticleTypes.SOUL, getX(), getEyeY() + 0.6, getZ(), 3, 0.2, 0.2, 0.2, 0.02);
    }
}
