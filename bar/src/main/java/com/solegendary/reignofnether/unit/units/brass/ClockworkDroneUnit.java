package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * A <b>clockwork drone</b> launched by the Iron Leviathan Engine (design/brassworks_plan.md, slice 4): a little rotor
 * drone on the Gyrocopter Gunship's flight frame (the parrot body, drawn small by ClockworkDroneRenderer in the Gyro
 * Scout's brass skin) with a {@link #attackDamage}-damage hit-scan pop gun and no strafing run. It never heats (a toy
 * next to the machines), costs nothing and takes no population (the Deployed Turret's zero cost: no wreck, never counted
 * in the army - {@link #isScout}).
 * <p>
 * It belongs to its engine: it joins the engine's fight when it has none of its own, drifts back to the engine when
 * idle and too far off, and falls apart the moment the engine is gone - each drone checks its engine once a tick (one
 * field read), and the engine scraps its drones when it dies. A drone loaded back from a save has lost its engine and
 * falls apart on its first tick; the engine launches new ones.
 */
public class ClockworkDroneUnit extends GyrocopterGunshipUnit {

    final static public float attackDamage = 3.0f;
    final static public float attacksPerSecond = 0.8f;
    final static public float attackRange = 6.0f;
    final static public float aggroRange = 10.0f;
    final static public float maxHealth = 20.0f;
    final static public float movementSpeed = 0.34f;
    /** Idle drones further than this from their engine fly back to it. */
    static final double LEASH = 8.0;

    @Nullable private IronLeviathanEngineUnit engine;

    public ClockworkDroneUnit(EntityType<? extends Parrot> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createDroneAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.FLYING_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, 0)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    public void setEngine(@Nullable IronLeviathanEngineUnit engine) { this.engine = engine; }
    @Nullable public IronLeviathanEngineUnit getEngine() { return engine; }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.DEPLOYED_TURRET; }   // free, no population, no wreck

    @Override public int heatPerShot() { return 0; }
    @Override public boolean isRangedMachine() { return false; }   // no Long Barrel on a toy

    @Override
    public boolean isScout() { return true; }   // left out of army counts and "select all army" (Unit.isScout)

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel) || !isAlive())
            return;
        IronLeviathanEngineUnit e = engine;
        if (e == null || !e.isAlive() || e.isRemoved()) {
            fallApart();
            return;
        }
        // once a second (staggered by id): help the engine in its fight, or come home when idle and far off
        if ((tickCount + getId()) % 20 != 0)
            return;
        LivingEntity t = e.getTarget();
        if (getTarget() == null && t != null && t.isAlive() && distanceToSqr(t) < 16 * 16)
            getTargetGoal().setTarget(t);
        else if (isIdle() && distanceToSqr(e) > LEASH * LEASH)
            getMoveGoal().setMoveTarget(BlockPos.containing(e.getX(), e.getY() + e.getBbHeight() + 1, e.getZ()));
    }

    /** Falls out of the sky in a puff of steam: no death event, so no wreck, no kill credit, no veterancy. */
    public void fallApart() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.04);
            sl.playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 0.6f, 1.6f);
        }
        discard();
    }

    /** A single pop-gun round - no strafing run, no heat. */
    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        fire(pTarget);
        if (pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        engine = null;   // loaded from a save: its engine link is gone, so it falls apart on its first tick
    }
}
