package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ability.abilities.CallAngel;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.goals.MeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * The <b>Sword Seraph</b> (CallAngel, big tier: 20 essence, 60 s cooldown, lives 30 s): a flying melee engager. It
 * hovers until it finds an enemy, then dives onto it - the bee's melee plumbing (MeleeAttackUnitGoal: a flyer's chase
 * path is the cell over its target) - and cuts at it up close for {@link #attackDamage}. Sturdy for a summon: it is
 * the angel that holds the line. It cannot hurt buildings.
 */
public class SwordSeraphUnit extends AngelUnit implements AttackerUnit {

    // against the T2 melee line (Vindicator ~ 9 dmg, Royal Guard): a hard hitter that only lasts 30 s
    final static public float attackDamage = 9.0f;
    final static public float attacksPerSecond = 0.8f;
    final static public float attackRange = 2; // only used by ranged units or melee building attackers
    final static public float aggroRange = 12.0f;
    final static public int sightRange = 16;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 70.0f;
    final static public float armorValue = 2.0f;
    final static public float movementSpeed = 0.34f;

    private MeleeAttackUnitGoal attackGoal;
    private BlockPos attackMoveTarget = null;
    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;

    public SwordSeraphUnit(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public CallAngel.Kind kind() { return CallAngel.Kind.SWORD; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.FLYING_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), sightRange)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    public boolean getWillRetaliate() {return willRetaliate;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle;}
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() {return false;}
    public Goal getAttackGoal() { return attackGoal; }
    public Goal getAttackBuildingGoal() { return null; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }

    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    @Override
    public void tick() {
        super.tick();
        if (isAlive() && !isRemoved())
            AttackerUnit.tick(this);
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                1, 0, 0, 0, 0);
        return hit;
    }

    @Override
    public void initialiseGoals() {
        super.initialiseGoals();
        this.attackGoal = new MeleeAttackUnitGoal(this, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, attackGoal);
    }
}
