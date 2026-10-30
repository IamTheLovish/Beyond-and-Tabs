package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ability.abilities.CallAngel;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The <b>Seraph Archer</b> (CallAngel, mid tier: 10 essence, 25 s cooldown, lives 25 s): a flying archer that flies
 * low, {@link #FLIGHT_HEIGHT} blocks up, so melee units and ground towers can hit it (lovish, Oct 29). It shoots
 * from the air on the Wisp Choir's plumbing (UnitRangedAttackGoal; a flyer closes range through its flying move goal,
 * so it shoots from above): each shot is a hit-scan bolt of light, a projectile hit for {@link #attackDamage} (ranged
 * armour applies), with a light trace (BarFx.shot). It cannot hurt buildings.
 */
public class SeraphArcherUnit extends AngelUnit implements AttackerUnit, RangedAttackerUnit {

    // against the T2 ranged line (Pillager 45 HP / 7 dmg / 0.63 aps, Wisp Choir 40 / 4 / 0.6): a little weaker than a
    // pillager per shot, a little more fragile, for 25 s
    final static public float attackDamage = 6.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float attackRange = 12.0f;
    final static public float aggroRange = 12.0f;
    final static public int sightRange = 18;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    /**
     * lovish (revision answers, Oct 29): it "flies low (3 blocks up) so melee and ground towers can hit it" - its height
     * over the ground, here and nowhere else.
     */
    public static final int FLIGHT_HEIGHT = 3;

    final static public float maxHealth = 40.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.30f;

    static final int ATTACK_WINDUP_TICKS = 4;

    private UnitRangedAttackGoal<? extends LivingEntity> attackGoal;
    private BlockPos attackMoveTarget = null;
    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    public SeraphArcherUnit(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public CallAngel.Kind kind() { return CallAngel.Kind.ARCHER; }

    @Override
    public int flightHeight() { return FLIGHT_HEIGHT; }

    // the low flyer (Unit.meleeReachableHeight): melee units and ground towers take it on from the ground under it, and
    // a melee reach (AbstractMeleeAttackUnitGoal: width^2 terms plus this bonus, all squared distances) grows by its
    // height squared plus MELEE_REACH_MARGIN, so an archer that stopped a little high (a move ends within 2 blocks of
    // its cell) or over a 1-block rise is still in reach: a 0.6-wide melee unit reaches ~3.8 blocks up to it
    public static final float MELEE_REACH_MARGIN = 3.0f;

    @Override
    public float meleeReachableHeight() { return FLIGHT_HEIGHT; }

    @Override
    public float getBonusMeleeRangeForAttackers() { return 0.4f + FLIGHT_HEIGHT * FLIGHT_HEIGHT + MELEE_REACH_MARGIN; }

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
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        shoot(pTarget);
        if (pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /** One bolt of light at {@code target}: a projectile hit for the unit's damage. Returns whether it hurt. Public for the game test. */
    public boolean shoot(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl))
            return false;
        // the seraph as both projectile and owner: the mixin takes getUnitAttackDamage and applies ranged armour
        boolean hurt = target.hurt(damageSources().mobProjectile(this, this), getUnitAttackDamage());
        Vec3 from = new Vec3(getX(), getY() + getBbHeight() * 0.6, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        BarFx.shot(sl, from, to, BarFx.K_ARROW, this);
        sl.playSound(null, blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 0.8f, 1.5f);
        return hurt;
    }

    @Override
    public void initialiseGoals() {
        super.initialiseGoals();
        this.attackGoal = new UnitRangedAttackGoal<>(this, ATTACK_WINDUP_TICKS);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, attackGoal);
    }
}
