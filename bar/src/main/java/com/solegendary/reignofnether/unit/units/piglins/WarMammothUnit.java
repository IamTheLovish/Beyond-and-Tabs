package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitDamage;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.RavagerUnit;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ironhide Horde T3 experimental - the <b>War Mammoth</b>: the Horde's <b>flanker</b> (project doc claude/t3-roles.md;
 * BAR's Marauder is the model - "fast, enough mass to flank while still packing a punch"). A titan built on the Siege
 * Ox's body and brain, drawn at 1.7x scale (3.3 blocks wide). What it opens: a player who fields one can run round the
 * enemy army at raider speed, shove through a thin line and raze the economy behind it, then leave before the army
 * turns round. What the enemy must do: keep the mammoth out with wall lines and towers at home (it never breaks
 * buildings with its charge, and the anti-T3 emplacements hit it three times as hard), slow it (Roar, roots, frost),
 * or catch it in a choke where it cannot turn and focus it down.
 * <ul>
 * <li><b>Fast for its size</b>: {@link #movementSpeed} 0.30 - the Marauder's and Hoglin's pace, ahead of every line
 *     (0.23-0.25). It keeps up with a cavalry raid and no infantry army can catch it in the open.</li>
 * <li><b>Mass</b>: right of way and the plough (unit/BigUnits) - the bodies in its path are shoved aside, it is never
 *     shoved; and it never stops to bite ({@link #isImmobile}: the ravager's 0.5 s freeze after each attack is off).</li>
 * <li><b>Trample</b> ({@link com.solegendary.reignofnether.ability.abilities.Trample}): an aimed charge - a short
 *     wind-up draws the lane, then it runs {@link #CHARGE_LENGTH} blocks at {@link #CHARGE_STEP} a tick; every enemy
 *     it runs through takes {@link #CHARGE_DAMAGE} and is flung aside. A wall or a building stops it short (no building
 *     damage: the Gorehorn Rhino is the gate breaker).</li>
 * <li><b>Big swings</b>: every blow also lands {@link #CLEAVE_SPLASH} of its damage on every enemy within
 *     {@link #CLEAVE_RADIUS} of the target, with a dust ring the RTS camera reads.</li>
 * </ul>
 * Still the T3 rules: ~4.5x a T2 heavy's cost, loses to an equal-cost focused T2 army (its charge is telegraphed, its
 * answer is static defence at home), arrival announced to every player. Placeholder look: the ravager model, scaled.
 * Not done: the archer platform crew; fire panic.
 */
public class WarMammothUnit extends RavagerUnit {

    final static public float attackDamage = 18.0f;
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 1100.0f;
    // 0.17 -> 0.30 (t3-rework 1, lovish Oct 21: "every tier 3 unit I used was extremely slow and almost useless"; the
    // flanker runs at the Horde's raider pace). It really walked at 0.25 before: RavagerUnit.aiStep reset the speed
    // to the Siege Ox's static field every tick (designedMovementSpeed).
    final static public float movementSpeed = 0.30f;
    final static public float attackRange = 3;
    final static public float aggroRange = 12;
    public static final float SCALE = 1.7f;
    /** The cleave: half of every blow lands on the enemies round the target (the Colossus and Walker do the same). */
    public static final float CLEAVE_RADIUS = 3.0f;
    public static final float CLEAVE_SPLASH = 0.5f;
    /** The charge: wind-up (the lane shows), then CHARGE_LENGTH blocks at CHARGE_STEP a tick, hitting what it runs through. */
    public static final float CHARGE_LENGTH = 16f;
    public static final int CHARGE_WINDUP_TICKS = 8;
    public static final double CHARGE_STEP = 1.0;
    /** How far past its own half-width the charge reaches to a side (a body brushed by the flank is still run through). */
    public static final double CHARGE_HIT_REACH = 1.2;
    public static final float CHARGE_DAMAGE = 20f;
    public static final double CHARGE_FLING = 1.4, CHARGE_LIFT = 0.45;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    /** The charge's state: wind-up ticks left, then steps left; 0/0 = not charging. Not saved (a reload cancels it). */
    public int chargeWindup = 0, chargeSteps = 0;
    @Nullable public Vec3 chargeDir = null;
    private final IntOpenHashSet chargeHit = new IntOpenHashSet();
    /** What the last charge did (game test): units hit, and whether it crashed into something. */
    public int lastChargeHits = 0;
    public boolean lastChargeCrashed = false;
    // the standing order the charge interrupted (the charge is an order of its own: the AI must not drag the mammoth
    // back mid-charge), given back once it ends - a Trample through a clump, autocast or clicked, must not cancel the
    // Fight or the building the player sent it at
    @Nullable private BlockPos resumeAttackMove, resumeBuilding;
    @Nullable private LivingEntity resumeTarget;
    private boolean resumeBuildingForced;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public WarMammothUnit(EntityType<? extends Ravager> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * RavagerUnit rebuilds its ability list from the shared static set here; add the Mammoth's own on top - one Trample
     * per mammoth, kept across refreshes so its cooldown (keyed by the instance) survives them. No initializer on the
     * field: this runs from the superclass constructor, before this class's initializers would wipe it.
     */
    private com.solegendary.reignofnether.ability.abilities.Trample trample;

    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        if (trample == null)
            trample = new com.solegendary.reignofnether.ability.abilities.Trample();
        for (var a : getAbilities().get())
            if (a == trample)
                return;
        getAbilities().add(trample);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.WAR_MAMMOTH; }

    @Override
    protected float designedMovementSpeed() {
        return movementSpeed;
    }

    /**
     * A ravager stands still for the ten ticks of every bite (Ravager.isImmobile: attackTick > 0) - a titan that
     * freezes half a second after each swing is what "clunky" felt like. The mammoth bites on the move; a roar or a
     * stun still roots it, as does dying.
     */
    @Override
    protected boolean isImmobile() {
        return this.isDeadOrDying() || this.getStunnedTick() > 0 || this.getRoarTick() > 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.ATTACK_KNOCKBACK, 3.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** The cleave: a landed blow also hits every enemy within {@link #CLEAVE_RADIUS} of the target for half damage. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl) {
            int others = UnitDamage.meleeSplash(sl, this, target, CLEAVE_RADIUS, getUnitAttackDamage() * CLEAVE_SPLASH);
            // the swing read from the RTS camera: a dust ring round the target, wider when the swing caught a group
            com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, target.position(), others > 0 ? CLEAVE_RADIUS : 1.5f);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED)
                && getOwnerName() != null && !getOwnerName().isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, getOwnerName(),
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.war_mammoth_unit"));
        }
        if (level() instanceof ServerLevel sl && isAlive() && isCharging())
            tickCharge(sl);
    }

    public boolean isCharging() {
        return chargeWindup > 0 || chargeSteps > 0;
    }

    /**
     * Orders a Trample toward {@code at}: locks the lane and starts the wind-up. False if already charging or the spot
     * is on top of the mammoth. Public for the game test and the bot.
     */
    public boolean startCharge(Vec3 at) {
        if (isCharging())
            return false;
        Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ());
        if (flat.lengthSqr() < 0.25)
            return false;
        rememberOrder();
        Unit.fullResetBehaviours(this);   // the charge is an order: the AI must not drag the mammoth back mid-charge
        chargeDir = flat.normalize();
        chargeWindup = CHARGE_WINDUP_TICKS;
        chargeSteps = (int) Math.ceil(CHARGE_LENGTH / CHARGE_STEP);
        chargeHit.clear();
        lastChargeHits = 0;
        lastChargeCrashed = false;
        float yaw = (float) Math.toDegrees(Math.atan2(chargeDir.z, chargeDir.x)) - 90f;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        if (level() instanceof ServerLevel sl) {
            drawLane(sl);
            sl.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.6f);
            com.solegendary.reignofnether.sounds.Sfx.BEAST_CHARGE.play(sl, position());
        }
        return true;
    }

    /** One tick of the charge: wind-up (lane telegraph), then a step with hits. Public for the game test. */
    public void tickCharge(ServerLevel sl) {
        if (chargeDir == null) {
            chargeWindup = chargeSteps = 0;
            return;
        }
        getNavigation().stop();
        if (chargeWindup > 0) {
            chargeWindup--;
            if (chargeWindup % 4 == 0)
                drawLane(sl);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 2, 0.8, 0.05, 0.8, 0.01);
            return;
        }
        chargeSteps--;
        double x0 = getX(), z0 = getZ();
        move(MoverType.SELF, new Vec3(chargeDir.x * CHARGE_STEP, -0.1, chargeDir.z * CHARGE_STEP));
        setDeltaMovement(0, getDeltaMovement().y, 0);
        hitAlongCharge(sl);
        double dx = getX() - x0, dz = getZ() - z0;
        if (dx * dx + dz * dz < CHARGE_STEP * CHARGE_STEP * 0.25) {
            // something solid: a wall line, a building, a cliff. The charge ends here - the mammoth does not break
            // buildings (the wall is its counter), it just thumps into them
            lastChargeCrashed = true;
            chargeSteps = 0;
            Vec3 ahead = position().add(chargeDir.scale(getBbWidth() * 0.5 + 0.5));
            com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, ahead, 2f);
            com.solegendary.reignofnether.sounds.Sfx.BEAST_BREACH.play(sl, ahead);
        }
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 3, 0.9, 0.1, 0.9, 0.02);
        if (chargeSteps <= 0) {
            chargeDir = null;
            resumeOrder();
        }
    }

    /** What the mammoth was told to do when the charge began: a building, a forced unit target, or a Fight spot. */
    private void rememberOrder() {
        resumeAttackMove = getAttackMoveTarget();
        resumeBuilding = null;
        resumeTarget = null;
        if (getAttackBuildingGoal() instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal g
                && g.getBuildingTarget() != null) {
            resumeBuilding = g.getBuildingTarget().centrePos;
            resumeBuildingForced = g.forced;
        } else if (getTargetGoal().getTarget() != null && getTargetGoal().forced && getTargetGoal().getTarget().isAlive()) {
            resumeTarget = getTargetGoal().getTarget();
        }
    }

    /** The charge is over: back to the order it interrupted (nothing, if there was none - it then fights as idle). */
    private void resumeOrder() {
        BlockPos building = resumeBuilding, fight = resumeAttackMove;
        LivingEntity target = resumeTarget;
        resumeBuilding = resumeAttackMove = null;
        resumeTarget = null;
        if (building != null)
            setAttackBuildingTarget(building, resumeBuildingForced);
        else if (target != null && target.isAlive())
            setUnitAttackTargetForced(target);
        if (fight != null && building == null && target == null)
            setAttackMoveTarget(fight);
    }

    /**
     * Every enemy unit the body runs through (within its half-width plus {@link #CHARGE_HIT_REACH}) not yet hit this
     * charge: {@link #CHARGE_DAMAGE} as a blow of its own (armour applies) and a fling to the side it stood on, scaled
     * by the victim's knockback resistance - another titan is hit, not thrown.
     */
    void hitAlongCharge(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty() || chargeDir == null)
            return;
        double r = CHARGE_HIT_REACH + getBbWidth() * 0.5;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), r, scratch)) {
            if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u) || chargeHit.contains(le.getId()))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            double ex = le.getX() - getX(), ez = le.getZ() - getZ();
            if (ex * ex + ez * ez > r * r || Math.abs(le.getY() - getY()) > 3)
                continue;
            chargeHit.add(le.getId());
            UnitDamage.hurtExplicit(le, damageSources().mobAttack(this), CHARGE_DAMAGE);
            double side = Math.signum(ex * -chargeDir.z + ez * chargeDir.x);
            if (side == 0)
                side = 1;
            double resist = Math.max(0, Math.min(1, le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
            double fling = CHARGE_FLING * (1 - resist);
            if (fling > 0) {
                Vec3 push = new Vec3(-chargeDir.z * side, 0, chargeDir.x * side).add(chargeDir.scale(0.5)).normalize().scale(fling);
                le.push(push.x, CHARGE_LIFT * (1 - resist), push.z);
                le.hurtMarked = true;
            }
            lastChargeHits++;
        }
        scratch.clear();
    }

    /** The telegraph: a dotted line of dust down the locked lane. */
    void drawLane(ServerLevel sl) {
        if (chargeDir == null)
            return;
        for (double d = 1.5; d <= CHARGE_LENGTH; d += 1.5)
            sl.sendParticles(ParticleTypes.CRIT, getX() + chargeDir.x * d, getY() + 0.15, getZ() + chargeDir.z * d, 2, 0.6, 0, 0.6, 0);
    }
}
