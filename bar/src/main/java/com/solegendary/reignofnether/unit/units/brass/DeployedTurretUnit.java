package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.brass.Machine;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * The Guildmaster's <b>Deployed Turret</b> (design/brassworks_plan.md section 3.2): a rifle on a tripod. The Rifle
 * Squad's frame with no legs (speed 0), {@link #LIFETIME_TICKS} of life and then a puff of steam - no death event, so
 * no wreck and no kill credit; its cost is zero with no population, so it is never counted. A {@link Machine} that
 * runs hot ({@link #HEAT_PER_SHOT} a shot: it dies venting), never overclocked. One loaded back from a save is
 * discarded on its first tick: a turret is a 20 s thing.
 */
public class DeployedTurretUnit extends RifleSquadUnit implements Machine {

    public static final int LIFETIME_TICKS = 20 * 20;
    public static final int HEAT_PER_SHOT = 15;
    public static final String TAG = "bt_turret";

    final static public float maxHealth = 40.0f;
    final static public float attackDamage = 5.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float attackRange = 12.0f;
    final static public float movementSpeed = 0.0f;

    public static final EntityDataAccessor<Integer> turretHeatDataAccessor =
            SynchedEntityData.defineId(DeployedTurretUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> turretVentDataAccessor =
            SynchedEntityData.defineId(DeployedTurretUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> turretUpgradeDataAccessor =
            SynchedEntityData.defineId(DeployedTurretUnit.class, EntityDataSerializers.BYTE);

    private int lifeLeft = LIFETIME_TICKS;
    private boolean stale = false;   // loaded from a save: gone on the first tick

    public DeployedTurretUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
        addTag(TAG);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(turretHeatDataAccessor, 0);
        this.entityData.define(turretVentDataAccessor, 0);
        this.entityData.define(turretUpgradeDataAccessor, (byte) 0);
    }

    @Override public int heat() { return this.entityData.get(turretHeatDataAccessor); }
    @Override public void setHeat(int heat) { this.entityData.set(turretHeatDataAccessor, heat); }
    @Override public int ventTicks() { return this.entityData.get(turretVentDataAccessor); }
    @Override public void setVentTicks(int ticks) { this.entityData.set(turretVentDataAccessor, ticks); }
    @Override public byte upgrade() { return this.entityData.get(turretUpgradeDataAccessor); }
    @Override public void setUpgrade(byte upgrade) { this.entityData.set(turretUpgradeDataAccessor, upgrade); }
    @Override public int overclockTicks() { return 0; }
    @Override public void setOverclockTicks(int ticks) { }
    @Override public int heatPerShot() { return HEAT_PER_SHOT; }
    @Override public boolean isRangedMachine() { return true; }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.DEPLOYED_TURRET; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), attackRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Ticks of life left. Public for the game test. */
    public int lifeLeft() { return lifeLeft; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide())
            return;
        Heat.tick(this, this);
        if (stale || --lifeLeft <= 0) {
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                sl.playSound(null, blockPosition(), SoundEvents.ANVIL_BREAK, SoundSource.NEUTRAL, 0.8f, 1.4f);
            }
            discard();   // falls apart: no death event, so no wreck and no kill credit
        }
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (!Heat.canFire(this))
            return;   // venting: the gun says nothing
        super.performUnitRangedAttack(pTarget, velocity);
        Heat.onShot(this, this);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        stale = true;
    }

    @Override
    public boolean isScout() { return true; }   // left out of army counts and "select all army" (Unit.isScout)
}
