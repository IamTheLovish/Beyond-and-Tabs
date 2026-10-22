package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.hud.TooltipColours;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.units.monsters.PhantomSummon;
import com.solegendary.reignofnether.unit.units.villagers.RavagerUnit;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public interface AttackerUnit {

    // how often an engaged (non-forced) attacker re-checks for a closer target; must be a multiple of 4 (the
    // cadence of the combat-AI block in tick())
    int RETARGET_TICKS = 12;

    public static final float ATTACK_DAMAGE_REDUCTION_PER_WEAK = 0.2f;
    public static final float ATTACK_DAMAGE_INCREASE_PER_STRENGTH = 0.2f;

    public boolean getWillRetaliate();
    public default float getAttacksPerSecond() {
        return 20f / getAttackCooldown();
    }
    public default float getAttackCooldown() {
        return ((20 / getNonBaseAttacksPerSecond()) * getAttackCooldownMultiplier());
    }
    public boolean getAggressiveWhenIdle();
    public default float getNonBaseAttacksPerSecond() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());
        return (float) (attr != null ?  attr.getValue() : AttributeRegistrar.ATTACKS_PER_SECOND.get().getDefaultValue());
    }
    public default float getBaseAttacksPerSecond() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());
        return (float) (attr != null ?  attr.getBaseValue() : AttributeRegistrar.ATTACKS_PER_SECOND.get().getDefaultValue());
    }
    public default float getAggroRange() {
        float attackRange = getAttackRange();
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.AGGRO_RANGE.get());
        float aggroRange = (float) (attr != null ?  attr.getValue() : AttributeRegistrar.AGGRO_RANGE.get().getDefaultValue());
        return Math.max(attackRange, aggroRange);
    }
    public default float getAttackRange() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.ATTACK_RANGE.get());
        return (float) (attr != null ?  attr.getValue() : AttributeRegistrar.ATTACK_RANGE.get().getDefaultValue());
    }
    public default float getBaseAttackRange() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.ATTACK_RANGE.get());
        return (float) (attr != null ?  attr.getBaseValue() : AttributeRegistrar.ATTACK_RANGE.get().getDefaultValue());
    }
    public default float getBaseUnitAttackDamage() {
        float bonus = 0;
        if (this instanceof HeroUnit heroUnit) {
            bonus = heroUnit.getAttackBonusPerLevel() * heroUnit.getHeroLevel();
        }
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get());
        return (float) (attr != null ?  attr.getBaseValue() : AttributeRegistrar.ATTACK_DAMAGE.get().getDefaultValue()) + bonus;
    }
    public default float getUnitAttackDamage() {
        float bonus = 0;
        if (this instanceof HeroUnit heroUnit) {
            bonus = heroUnit.getAttackBonusPerLevel() * heroUnit.getHeroLevel();
        }
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get());
        float value = (float) (attr != null ?  attr.getValue() : AttributeRegistrar.ATTACK_DAMAGE.get().getDefaultValue()) + bonus;

        MobEffectInstance weakMei = ((LivingEntity) this).getEffect(MobEffects.WEAKNESS);
        float weak = 0;
        if (weakMei != null && weakMei.getDuration() > 0) {
            weak = (weakMei.getAmplifier() + 1) * ATTACK_DAMAGE_REDUCTION_PER_WEAK;
        }
        MobEffectInstance strMei = ((LivingEntity) this).getEffect(MobEffects.DAMAGE_BOOST);
        float str = 0;
        if (strMei != null && strMei.getDuration() > 0) {
            str = (strMei.getAmplifier() + 1) * ATTACK_DAMAGE_INCREASE_PER_STRENGTH;
        }
        return Math.max(0, value * (1 - weak + str));
    }

    public default float getCriticalChance() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());
        return (float) (attr != null ? attr.getValue() : AttributeRegistrar.CRITICAL_HIT_CHANCE.get().getDefaultValue());
    }
    public default float getExplosiveChance() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.EXPLOSIVE_HIT_CHANCE.get());
        return (float) (attr != null ? attr.getValue() : AttributeRegistrar.EXPLOSIVE_HIT_CHANCE.get().getDefaultValue());
    }
    public default float getBuildingDamageBonus() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get());
        return (float) (attr != null ? (attr.getValue()) : (AttributeRegistrar.BUILDING_DAMAGE_BONUS.get().getDefaultValue()));
    }
    public default float getBuildingDamageMultiplier() {
        return getBuildingDamageBonus() + 1.0f;
    }
    public default float getLifeStealPercent() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.LIFESTEAL.get());
        return (float) (attr != null ? attr.getValue() : AttributeRegistrar.LIFESTEAL.get().getDefaultValue());
    }
    public default float getManaOnHitPercent() {
        AttributeInstance attr = ((LivingEntity) this).getAttribute(AttributeRegistrar.MANA_ON_HIT.get());
        return (float) (attr != null ? attr.getValue() : AttributeRegistrar.MANA_ON_HIT.get().getDefaultValue());
    }

    public BlockPos getAttackMoveTarget();
    public boolean canAttackBuildings();

    public Goal getAttackGoal(); // not necessarily the same goal, eg. could be melee or ranged
    public Goal getAttackBuildingGoal();

    public EnemySearchBehaviour getEnemySearchBehaviour(); // not necessarily the same goal, eg. could be melee or ranged
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour);

    // chase and attack the target ignoring all else until it is dead or out of sight
    public default void setUnitAttackTarget(@Nullable LivingEntity target) {
        if (target != null) {
            MiscUtil.addUnitCheckpoint(((Unit) this), target.getId(), false);
            Goal attackBuildingGoal = this.getAttackBuildingGoal();
            if (attackBuildingGoal instanceof RangedAttackBuildingGoal<?> rabg)
                rabg.stop();
            else if (attackBuildingGoal instanceof MeleeAttackBuildingGoal mabg)
                mabg.stopAttacking();
        }
        ((Unit) this).getTargetGoal().setTarget(target);
    }

    // chase and attack the target ignoring all else until it is dead or out of sight
    public default void setUnitAttackTargetForced(@Nullable LivingEntity target) {
        setUnitAttackTarget(target);
        if (target != null) {
            Goal attackBuildingGoal = this.getAttackBuildingGoal();
            if (attackBuildingGoal instanceof RangedAttackBuildingGoal<?> rabg)
                rabg.stop();
            else if (attackBuildingGoal instanceof MeleeAttackBuildingGoal mabg)
                mabg.stopAttacking();
            ((Unit) this).getTargetGoal().forced = true;
        }
    }

    public default void setAttackBuildingTarget(BlockPos preselectedBlockPos) {
        setAttackBuildingTarget(preselectedBlockPos, true);
    }

    // move to a building and start attacking it
    public default void setAttackBuildingTarget(BlockPos preselectedBlockPos, boolean forced) {
        if (this.canAttackBuildings()) {
            Goal attackBuildingGoal = this.getAttackBuildingGoal();
            if (attackBuildingGoal instanceof RangedAttackBuildingGoal<?> rabg) {
                rabg.setBuildingTarget(preselectedBlockPos);
                rabg.forced = forced;
            } else if (attackBuildingGoal instanceof MeleeAttackBuildingGoal mabg) {
                mabg.setBuildingTarget(preselectedBlockPos);
                mabg.forced = forced;
            }
        } else {
            Level level = ((LivingEntity) this).level();
            BuildingPlacement building = BuildingUtils.findBuilding(level.isClientSide(), preselectedBlockPos);

            if (building != null) {
                BlockPos groundCentrePos = new BlockPos(building.centrePos.getX(), building.originPos.getY() + 1, building.centrePos.getZ());
                BlockPos targetPos = MyMath.getXZRangeLimitedBlockPos(
                        new BlockPos(groundCentrePos),
                        ((LivingEntity) this).getOnPos(),
                        getAttackRange() - 5
                );
                while (!level.getBlockState(targetPos.above()).isAir())
                    targetPos = targetPos.above();

                ((Unit) this).setMoveTarget(targetPos);
                if (((LivingEntity) this).level().isClientSide)
                    MiscUtil.addUnitCheckpoint((Unit) this, groundCentrePos, false);
            }
        }
    }

    static void resetBehaviours(AttackerUnit unit) {
        unit.setUnitAttackTarget(null);
        unit.setAttackMoveTarget(null);

        Goal attackGoal = unit.getAttackGoal();
        if (attackGoal instanceof MeleeWindupAttackUnitGoal mwaug)
            mwaug.resetWindup();

        Goal attackBuildingGoal = unit.getAttackBuildingGoal();
        if (attackBuildingGoal instanceof RangedAttackBuildingGoal<?> rabg)
            rabg.stop();
        else if (attackBuildingGoal instanceof MeleeAttackBuildingGoal mabg)
            mabg.stopAttacking();

        unit.setEnemySearchBehaviour(EnemySearchBehaviour.NONE);
    }

    // this setter sets a Unit field and so can't be defaulted
    // move to a block but chase/attack a target if there is one close by (for a limited distance)
    void setAttackMoveTarget(@Nullable BlockPos bp);

    static boolean isAttackingBuilding(AttackerUnit attackerUnit) {
        boolean isAttackingBuilding = false;
        Goal attackBuildingGoal = attackerUnit.getAttackBuildingGoal();
        if (attackBuildingGoal instanceof RangedAttackBuildingGoal<?> rabg)
            isAttackingBuilding = rabg.getBuildingTarget() != null;
        else if (attackBuildingGoal instanceof MeleeAttackBuildingGoal mabg)
            isAttackingBuilding = mabg.getBuildingTarget() != null;
        return isAttackingBuilding;
    }

    /** Extra blocks past its aggro / attack range an attack-moving unit chases a target it picked up on the way. */
    float ATTACK_MOVE_LEASH_EXTRA = 8f;

    /** How far an attack-moving unit follows a target it picked up on the way before carrying on (BAR's Fight). */
    static double attackMoveLeash(AttackerUnit attackerUnit) {
        return Math.max(attackerUnit.getAggroRange(), attackerUnit.getAttackRange()) + ATTACK_MOVE_LEASH_EXTRA;
    }

    /**
     * Whether the unit has an order queued behind the one it is carrying out (Shift+order, CommandQueues). Such a unit
     * is idle for one moment only, between finishing an order and the queue starting the next at the end of the
     * server tick (CommandQueues.tick, after every unit has ticked). A unit that picks a fight of its own in that
     * moment - its own kill is the usual cause, the new target being whoever stands closest, in a scrum often not the
     * one it just killed - is busy when the queue looks, and the queued order waits until that fight is over. That
     * happened on the kills that landed on a tick the idle check runs (tickCount % 4), one in four. Idle and
     * retaliating pick-ups leave it to the queue.
     */
    private static boolean ordersWaiting(Mob unitMob) {
        return CommandQueues.queuedCount(unitMob.getId()) > 0;
    }

    /**
     * The contact rule. A unit on a plain move order used to ignore the enemy completely until the move ended: no idle
     * aggro and no retaliation, both of which wait for {@link Unit#isIdle}. A move that runs into the enemy army never
     * ends on time - the unit is body-blocked short of its formation cell, Separation keeps it twitching (so isIdle's
     * "standing still" test fails too), and the move only gives up after three repaths that end on the same node:
     * 7 s at best, far longer in a crowd. So in lovish's Oct 22 1v1 (hundreds a side) his right-clicked army stood
     * pressed into the bot's wave, taking hits and not swinging back. BAR's move does not chase, but its units still
     * fire at whatever is in range; here a unit whose move has stopped getting anywhere ({@link #CONTACT_STALL_TICKS},
     * a shooter {@link #RANGED_STALL_TICKS}) fights: it answers whoever hits it and takes the nearest enemy within
     * {@link #contactRange} - arm's length for melee, weapon range for a shooter. The blocked move is dropped (it
     * would have given up anyway); a unit that is getting closer keeps walking past the enemy, as before (a move away
     * from a fight still works).
     */
    int CONTACT_STALL_TICKS = 30;
    int RANGED_STALL_TICKS = 60;
    /** Past the melee swing (two body widths): the rule takes an enemy the unit is touching, not one a step off. */
    double CONTACT_MARGIN = 1.0;

    static boolean isMelee(AttackerUnit attackerUnit) {
        return attackerUnit.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal;
    }

    /** Arm's length for melee (its swing plus {@link #CONTACT_MARGIN}), weapon range for a shooter. */
    static double contactRange(AttackerUnit attackerUnit) {
        if (!isMelee(attackerUnit))
            return attackerUnit.getAttackRange();
        Mob mob = (Mob) attackerUnit;
        return Math.max(attackerUnit.getAttackRange(),
            mob.getBbWidth() * 2.0 + attackerUnit.getBonusMeleeRange() + CONTACT_MARGIN);
    }

    /** On a plain move order that stopped getting closer (see the contact rule above) and doing nothing else. */
    static boolean blockedOnMove(AttackerUnit attackerUnit) {
        Unit unit = (Unit) attackerUnit;
        Mob mob = (Mob) attackerUnit;
        MoveToTargetBlockGoal moveGoal = unit.getMoveGoal();
        if (moveGoal == null || moveGoal.getMoveTarget() == null)
            return false;
        if (attackerUnit.getAttackMoveTarget() != null || unit.hasLivingTarget() || isAttackingBuilding(attackerUnit)
                || unit.getFollowTarget() != null || unit.getHoldPosition() || mob.isPassenger()
                || GarrisonableBuildingAddon.getGarrison(unit) != null)
            return false;
        return moveGoal.ticksWithoutProgress() >= (isMelee(attackerUnit) ? CONTACT_STALL_TICKS : RANGED_STALL_TICKS);
    }

    /** True when the building being attacked was ordered (forced), not picked up on the way. */
    static boolean isBuildingTargetForced(AttackerUnit attackerUnit) {
        Goal attackBuildingGoal = attackerUnit.getAttackBuildingGoal();
        if (attackBuildingGoal instanceof RangedAttackBuildingGoal<?> rabg)
            return rabg.forced;
        if (attackBuildingGoal instanceof MeleeAttackBuildingGoal mabg)
            return mabg.forced;
        return false;
    }

    static void tick(AttackerUnit attackerUnit) {
        int sys = Sys.begin(Sys.ATTACKER);
        try {
            tickBody(attackerUnit);
        } finally {
            Sys.end(sys);
        }
    }

    private static void tickBody(AttackerUnit attackerUnit) {
        Mob unitMob = (Mob) attackerUnit;
        Unit unit = (Unit) attackerUnit;

        if (!unitMob.level().isClientSide && !unit.isEatingFood()) {
            if (attackerUnit.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal meleeAttackUnitGoal) {
                meleeAttackUnitGoal.tickAttackCooldown();
                // doesn't tick on its own for some reason?
                if (((Mob) attackerUnit).isVehicle())
                    meleeAttackUnitGoal.tick();
                if (meleeAttackUnitGoal instanceof MeleeWindupAttackUnitGoal goal)
                    goal.checkAndPerformAttackWithWindup();
            }
            else if (attackerUnit.getAttackGoal() instanceof UnitRangedAttackGoal rangedAttackGoal)
                rangedAttackGoal.tickAttackCooldown();
            else if (attackerUnit.getAttackGoal() instanceof UnitBowAttackGoal rangedAttackGoal)
                rangedAttackGoal.tickAttackCooldown();

            if (attackerUnit.getAttackBuildingGoal() != null && attackerUnit.canAttackBuildings())
                attackerUnit.getAttackBuildingGoal().tick();
        }
        else if (unit instanceof RangedAttackerUnit rangedAttackerUnit) {
            int revealDuration = rangedAttackerUnit.getFogRevealDuration();
            if (revealDuration > 0)
                rangedAttackerUnit.setFogRevealDuration(revealDuration - 1);
        }

        if (!unitMob.level().isClientSide && unitMob.tickCount % 4 == 0) {
            if (((LivingEntity) unit).getEffect(MobEffectRegistrar.STUN.get()) != null ||
                ((LivingEntity) unit).getEffect(MobEffectRegistrar.FREEZE.get()) != null) {
                Unit.fullResetBehaviours(unit);
                return;
            }

            boolean isAttackingBuilding = isAttackingBuilding(attackerUnit);

            // BAR fire state (EngagementStance): Hold fire never picks a target on its own and Return fire only
            // answers whoever hit it; both still obey an explicit attack order (forced target). Fire at will is RoN's
            // usual behaviour. One persistent-data read per 4 ticks per unit.
            boolean fireAtWill = EngagementStance.mayAutoAcquire(unitMob);

            // enact attack moving
            // prioritises units and will chase them, resuming attack move once dead or out of range/sight
            if (attackerUnit.getAttackMoveTarget() != null && !unit.hasLivingTarget() && !isAttackingBuilding) {
                if (fireAtWill) {
                    int sysAm = Sys.begin(Sys.ATTACK_MOVE);
                    try {
                        attackerUnit.attackClosestEnemy((ServerLevel) unitMob.level());
                    } finally {
                        Sys.end(sysAm);
                    }
                }

                if (unit.getTargetGoal().getTarget() == null &&
                    unit.getMoveGoal().getMoveTarget() == null &&
                    !isAttackingBuilding(attackerUnit))
                    unit.setMoveTarget(attackerUnit.getAttackMoveTarget());
            }
            // BAR's Fight puts units before buildings: a unit that attack-moved into a building it picked itself (not
            // an ordered target) turns on an enemy unit that comes into range. Before, it kept hitting the wall while
            // that unit cut down its friends, and only a hit on itself turned it round (Lane C playtest #26, Oct 20)
            else if (attackerUnit.getAttackMoveTarget() != null && !unit.hasLivingTarget() && isAttackingBuilding
                    && fireAtWill && !isBuildingTargetForced(attackerUnit)) {
                LivingEntity foe;
                int sysFoe = Sys.begin(Sys.ENTITY_SCAN);
                try {
                    foe = MiscUtil.findClosestAttackableEntity(unitMob, attackerUnit.getAggroRange(),
                        (ServerLevel) unitMob.level());
                } finally {
                    Sys.end(sysFoe);
                }
                if (foe != null) {
                    if (!unitMob.isPassenger())
                        unit.getMoveGoal().stopMoving();
                    attackerUnit.setUnitAttackTarget(foe);   // also stops the building attack
                }
            }
            // ... and keeps going: a target it picked up on the way that runs off past the leash is let go, and the
            // unit carries on to where it was sent instead of chasing it across the map. An ordered target (forced)
            // is chased as before.
            else if (attackerUnit.getAttackMoveTarget() != null && unit.hasLivingTarget()
                    && !unit.getTargetGoal().forced && unit.getTargetGoal().getTarget() != null) {
                LivingEntity chased = unit.getTargetGoal().getTarget();
                double leash = attackMoveLeash(attackerUnit);
                if (chased.distanceToSqr(unitMob) > leash * leash) {
                    attackerUnit.setUnitAttackTarget(null);
                    unitMob.setTarget(null);
                    unit.setMoveTarget(attackerUnit.getAttackMoveTarget());
                }
            }

            boolean isCasting = unit.isCasting();
            boolean forced1 = ((Unit) attackerUnit).getTargetGoal().forced;
            Goal attackBuildingGoal = attackerUnit.getAttackBuildingGoal();
            boolean forced2 = attackBuildingGoal instanceof RangedAttackBuildingGoal<?> rabg && rabg.forced;
            boolean forced3 = attackBuildingGoal instanceof MeleeAttackBuildingGoal mabg && mabg.forced;
            boolean forced = forced1 || forced2 || forced3;

            // a plain move that ran into something and stopped getting closer (the contact rule, blockedOnMove)
            boolean blocked = !isCasting && blockedOnMove(attackerUnit);

            // retaliate against a mob that damaged us UNLESS already on another command - and a command waiting in the
            // unit's queue counts: see ordersWaiting. A move order that is blocked does not count (the contact rule)
            if (unitMob.getLastDamageSource() != null &&
                    attackerUnit.getWillRetaliate() && EngagementStance.mayRetaliate(unitMob) &&
                    unit.getTargetGoal().getTarget() == null &&
                    !isCasting && ((unit.isIdle() && !ordersWaiting(unitMob)) || (isAttackingBuilding && !forced) || blocked)) {

                Entity lastDSEntity = unitMob.getLastDamageSource().getEntity();

                boolean isMeleeAttackedByFlyingOrGarrisoned = false;
                if (lastDSEntity instanceof Unit unitDS &&
                    (unitDS.isFlyingUnit() ||
                        GarrisonableBuildingAddon.getGarrison(unitDS) != null ||
                        unitDS instanceof PhantomSummon ||
                        unitDS instanceof Vex) &&
                    attackerUnit.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal) {
                    isMeleeAttackedByFlyingOrGarrisoned = true;
                }
                Relationship rs = UnitServerEvents.getUnitToEntityRelationship(unit, lastDSEntity);

                if (!isMeleeAttackedByFlyingOrGarrisoned &&
                    lastDSEntity instanceof LivingEntity &&
                    !(lastDSEntity instanceof Player player && player.isCreative()) &&
                    (rs == Relationship.NEUTRAL || rs == Relationship.HOSTILE)) {

                    if (blocked)
                        unit.getMoveGoal().stopMoving();   // the blocked move is over: it fights where it stands
                    attackerUnit.setUnitAttackTarget((LivingEntity) lastDSEntity);
                }
            }
            // the contact rule: a blocked mover takes the nearest enemy it touches (a shooter: in weapon range)
            if (blocked && fireAtWill && attackerUnit.getAggressiveWhenIdle() && unit.getTargetGoal().getTarget() == null) {
                LivingEntity foe;
                int sysContact = Sys.begin(Sys.ENTITY_SCAN);
                try {
                    foe = MiscUtil.findClosestAttackableEntity(unitMob, (float) contactRange(attackerUnit),
                        (ServerLevel) unitMob.level());
                } finally {
                    Sys.end(sysContact);
                }
                if (foe != null) {
                    unit.getMoveGoal().stopMoving();
                    attackerUnit.setUnitAttackTarget(foe);
                }
            }
            // idle auto-aggression
            if (unit.isIdle() && !isCasting && attackerUnit.getAggressiveWhenIdle() && fireAtWill && !ordersWaiting(unitMob)) {
                int sysIdle = Sys.begin(Sys.IDLE_AGGRO);
                try {
                    attackerUnit.attackClosestEnemy((ServerLevel) unitMob.level());
                } finally {
                    Sys.end(sysIdle);
                }
            }

            // if attacking another unit as melee, retarget the closest unit periodically unless forced on a target
            // Throttled from every 4 to every RETARGET_TICKS ticks: this runs for every unit already in combat (the
            // bulk of units in a big fight) and each call is an entity query + sort + line-of-sight checks.
            // tickCount differs per unit (spawn time), so the scans stay staggered across ticks. Idle aggro and
            // attack-move acquisition above still run every 4 ticks, so reaction time to new enemies is unchanged.
            if (!forced && fireAtWill && unitMob.tickCount % RETARGET_TICKS == 0) {
                int sysRetarget = Sys.begin(Sys.RETARGET);
                try {
                    attackerUnit.retargetToClosestUnit((ServerLevel) unitMob.level());
                } finally {
                    Sys.end(sysRetarget);
                }
            }
        }

        if (!unitMob.level().isClientSide && unitMob.tickCount % 40 == 0) {
            if (attackerUnit.getAttackMoveTarget() != null && attackerUnit.getEnemySearchBehaviour() == EnemySearchBehaviour.NONE) {
                boolean hasNoTargets = ((Unit) attackerUnit).getTargetGoal().getTarget() == null;
                if (attackerUnit.getAttackBuildingGoal() instanceof MeleeAttackBuildingGoal mabg && mabg.getBuildingTarget() != null)
                    hasNoTargets = false;
                else if (attackerUnit.getAttackBuildingGoal() instanceof RangedAttackBuildingGoal<?> rabg && rabg.getBuildingTarget() != null)
                    hasNoTargets = false;
                if (hasNoTargets && unitMob.distanceToSqr(attackerUnit.getAttackMoveTarget().getCenter()) < 4)
                    attackerUnit.setAttackMoveTarget(null);
            }
            if (attackerUnit.getAttackMoveTarget() == null || unit.isIdle()) {
                int sysSearch = Sys.begin(Sys.ATTACK_MOVE);
                try {
                    switch (attackerUnit.getEnemySearchBehaviour()) {
                        case NEAREST_ENEMY_BUILDING -> attackerUnit.attackMoveNearestEnemyBuilding();
                        case NEAREST_ENEMY_UNIT -> attackerUnit.attackMoveNearestEnemyUnit(false);
                        case NEAREST_ENEMY_WORKER -> attackerUnit.attackMoveNearestEnemyUnit(true);
                    }
                } finally {
                    Sys.end(sysSearch);
                }
            }
        }
    }

    // if the nearest target is closer than the current target, retarget to the nearest
    default void retargetToClosestUnit(ServerLevel level) {
        float aggroRange = this.getAggroRange();
        BuildingPlacement garrPlacement = GarrisonableBuildingAddon.getGarrison((Unit) this);
        GarrisonableBuildingAddon garr = garrPlacement != null ? garrPlacement.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class) : null;
        if (garr != null) {
            aggroRange = garr.getAttackRange();
        }
        boolean isAttackingBuilding = isAttackingBuilding(this);
        LivingEntity currentTarget = ((Mob) this).getTarget();
        if (currentTarget == null && !isAttackingBuilding) return;
        LivingEntity closestTarget;
        int sysScan = Sys.begin(Sys.ENTITY_SCAN);
        try {
            closestTarget = MiscUtil.findClosestAttackableEntity((Mob) this, aggroRange, level);
        } finally {
            Sys.end(sysScan);
        }
        if (closestTarget == null) return;
        double distClosestTarget =  ((Mob) this).distanceToSqr(closestTarget.position());
        double distCurrentTarget = isAttackingBuilding ? (aggroRange / 2) : ((Mob) this).distanceToSqr(currentTarget.position());

        if (distClosestTarget < distCurrentTarget) {
            if (!((LivingEntity) this).isPassenger())
                ((Unit) this).getMoveGoal().stopMoving();
            setUnitAttackTarget(closestTarget);
        }
    }

    default void attackClosestEnemy(ServerLevel level) {
        float aggroRange = this.getAggroRange();
        BuildingPlacement garrPlacement = GarrisonableBuildingAddon.getGarrison((Unit) this);
        GarrisonableBuildingAddon garr = garrPlacement != null ? garrPlacement.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class) : null;
        if (garr != null) {
            aggroRange = garr.getAttackRange();
        }
        LivingEntity entity;
        int sysScan = Sys.begin(Sys.ENTITY_SCAN);
        try {
            entity = MiscUtil.findClosestAttackableEntity((Mob) this, aggroRange, level);
        } finally {
            Sys.end(sysScan);
        }
        if (entity != null) {
            if (!((LivingEntity) this).isPassenger())
                ((Unit) this).getMoveGoal().stopMoving();
            setUnitAttackTarget(entity);
            return;
        }
        if (canAttackBuildings() && !(this instanceof RavagerUnit && ((LivingEntity) this).isVehicle()) &&
                (!(((Unit) this).getOwnerName()).isEmpty() || level.getGameRules().getRule(GameRuleRegistrar.NEUTRAL_AGGRO).get()))
        {
            BuildingPlacement closestBuilding;
            int sysBuilding = Sys.begin(Sys.BUILDING_SCAN);
            try {
                closestBuilding = MiscUtil.findClosestAttackableBuilding((Mob) this, aggroRange);
            } finally {
                Sys.end(sysBuilding);
            }
            if (closestBuilding != null) {
                if (!((LivingEntity) this).isPassenger())
                    ((Unit) this).getMoveGoal().stopMoving();
                setAttackBuildingTarget(closestBuilding.originPos, false);
            }
        }
    }

    static double getWeaponDamageModifier(AttackerUnit attackerUnit) {
        ItemStack itemStack = ((LivingEntity) attackerUnit).getItemBySlot(EquipmentSlot.MAINHAND);

        if (!itemStack.isEmpty())
            for(AttributeModifier attr : itemStack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))
                if (attr.getOperation() == AttributeModifier.Operation.ADDITION)
                    return attr.getAmount();
        return 0;
    }

    /**
     * A held weapon that is only for show. getWeaponDamageModifier above adds the mainhand item's attack-damage
     * modifier to every melee hit, so a plain vanilla weapon is NOT visual: an iron sword adds +5 and a trident +8 on
     * top of the unit's stat sheet (the Leafblade hit for 10 instead of 5, the Reef Guard for 13). An explicit +0
     * modifier replaces the item's own, the way the Halberdier's axe and the Brute's sword have always been set up.
     */
    static ItemStack cosmeticWeapon(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.addAttributeModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
            java.util.UUID.randomUUID().toString(), 0, AttributeModifier.Operation.ADDITION), EquipmentSlot.MAINHAND);
        return stack;
    }

    public default @Nullable SoundAction getAttackSound() { return null; }

    default float getBonusMeleeRange() {
        return 0f;
    }

    public default int getDamageTooltipColour() {
        return TooltipColours.WHITE;
    }

    public default boolean hasBonusRange() {
        return getAttackRange() > getBaseAttackRange();
    }

    public default float getAttackCooldownMultiplier() {
        MobEffectInstance disarm = ((LivingEntity) (this)).getEffect(MobEffectRegistrar.DISARM.get());
        if (disarm != null) {
            return 999999;
        }
        MobEffectInstance attackSlowdown = ((LivingEntity) (this)).getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get());
        int attackSlowdownAmp = attackSlowdown != null ? attackSlowdown.getAmplifier() + 1 : 0;

        MobEffectInstance bloodlust = ((LivingEntity) (this)).getEffect(MobEffectRegistrar.BLOODLUST.get());

        return (1 + (attackSlowdownAmp * 0.05f)) / (bloodlust != null ? 1.6f : 1.0f);
    }

    public default void attackMoveNearestEnemyBuilding() {
        // the nearest enemy building through the BuildingGrid (it used to copy, filter and sort the whole list)
        BuildingPlacement nearest = MiscUtil.nearestEnemyBuildingForAttackMove((Unit) this);
        if (nearest != null)
            setAttackMoveTarget(nearest.getClosestGroundPos(((Entity) this).getOnPos(), 1));
    }

    public default void attackMoveNearestEnemyUnit(boolean workersOnly) {
        Mob mob = (Mob) this;
        Unit unit = (Unit) this;
        List<LivingEntity> units;
        if (mob.level().isClientSide())
            units = UnitClientEvents.getAllUnits();
        else
            units = UnitServerEvents.getAllUnits();

        ArrayList<LivingEntity> eligibleTargets = new ArrayList<>();
        List<LivingEntity> unitsCopy = new ArrayList<>(units); // defensive copy
        for (LivingEntity entity : unitsCopy) {
            if (entity instanceof Unit otherUnit &&
                    (!workersOnly || entity instanceof WorkerUnit) &&
                    !unit.getOwnerName().equals(otherUnit.getOwnerName()) &&
                    !AlliancesServerEvents.isAllied(unit.getOwnerName(), otherUnit.getOwnerName()) &&
                    !otherUnit.getOwnerName().isBlank()) {
                eligibleTargets.add(entity);
            }
        }
        eligibleTargets.sort(Comparator.comparing(e -> e.position().distanceToSqr(((Entity) unit).position())));

        if (!eligibleTargets.isEmpty())
            setAttackMoveTarget(eligibleTargets.get(0).getOnPos());
    }
}
