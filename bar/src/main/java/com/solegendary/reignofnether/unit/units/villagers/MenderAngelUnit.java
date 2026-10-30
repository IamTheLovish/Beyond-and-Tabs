package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ability.abilities.CallAngel;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.interfaces.SupportUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.support.SupportBrain;
import com.solegendary.reignofnether.unit.support.SupportKind;
import com.solegendary.reignofnether.unit.support.SupportState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The <b>Mender Angel</b> (CallAngel, cheap tier: 5 essence, 12 s cooldown, lives 20 s): a flying healer. It runs the
 * support brain every healer shares (unit/support/SupportBrain: attack orders hold a standoff, it steps out of melee,
 * it keeps healing while it moves) with one cast, a SINGLE_HEAL: a beam of light that mends {@link #HEAL} HP on the
 * hurt friend the brain picks within {@link #HEAL_RANGE} blocks (measured flat, so it heals from the air), once every
 * {@link #HEAL_INTERVAL_TICKS} ticks. It does not fight.
 */
public class MenderAngelUnit extends AngelUnit implements SupportUnit {

    final static public float maxHealth = 30.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.30f;
    final static public int sightRange = 16;

    /** Over the fight, out of melee: it heals from the air (its reach is measured flat). */
    public static final int FLIGHT_HEIGHT = 6;

    public static final float HEAL = 5.0f;
    public static final int HEAL_INTERVAL_TICKS = 40;
    public static final double HEAL_RANGE = 8.0;

    private final SupportState supportState = new SupportState();
    @Override public SupportState getSupportState() { return supportState; }
    @Override public double getSupportReach() { return HEAL_RANGE; }

    private int healCooldown = 0;

    public MenderAngelUnit(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public CallAngel.Kind kind() { return CallAngel.Kind.MENDER; }

    @Override
    public int flightHeight() { return FLIGHT_HEIGHT; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.FLYING_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, armorValue)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), sightRange)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(Attributes.ATTACK_DAMAGE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (healCooldown > 0)
            healCooldown--;
        if (isAlive() && !isRemoved())
            SupportBrain.tick(this, this);
    }

    // ------------------------------------------------------------------ the one cast: a single heal

    @Override
    public boolean canCastSupport(ServerLevel level, SupportKind kind, boolean autocast) {
        return kind == SupportKind.SINGLE_HEAL && healCooldown <= 0;
    }

    @Override
    public double supportCastRange(SupportKind kind) { return HEAL_RANGE; }

    @Override
    public boolean castSupport(ServerLevel level, SupportKind kind, @Nullable LivingEntity target, double x, double y, double z,
                               boolean ordered) {
        if (kind != SupportKind.SINGLE_HEAL || target == null || healCooldown > 0)
            return false;
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (dx * dx + dz * dz > HEAL_RANGE * HEAL_RANGE) {
            getMoveGoal().setMoveTarget(target.getOnPos());   // a little further: fly over to it, the next look heals
            return false;
        }
        return heal(level, target);
    }

    /** Mends {@code target} by {@link #HEAL}, with the beam; false for a dead or unhurt one. Public for the game test. */
    public boolean heal(ServerLevel level, LivingEntity target) {
        if (!target.isAlive() || target.getHealth() >= target.getMaxHealth())
            return false;
        target.heal(HEAL);
        healCooldown = HEAL_INTERVAL_TICKS;
        BarFx.shot(level, getEyePosition(), target.getEyePosition(), BarFx.K_MAGIC, this);
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(),
            2, 0.25, 0.15, 0.25, 0.0);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8f, 1.8f);
        return true;
    }
}
