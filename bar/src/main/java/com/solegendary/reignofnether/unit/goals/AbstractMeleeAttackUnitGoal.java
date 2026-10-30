package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.unit.Engage;
import com.solegendary.reignofnether.unit.HeldBlow;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import com.solegendary.reignofnether.unit.units.piglins.MarauderUnit;
import com.solegendary.reignofnether.unit.units.piglins.PiglinMerchantUnit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.EnumSet;

// based on MeleeAttackGoal
public abstract class AbstractMeleeAttackUnitGoal extends Goal {
    protected final Mob mob;
    private final boolean followingTargetEvenIfNotSeen;
    private Path path;
    // where the chase is heading (a ring slot, a bank cell beside a wading target, or null = hold): Engage picks it,
    // so an attacker never paths into water after its target and three attackers do not share one cell
    @Nullable private Engage.Approach approach;
    protected int ticksUntilNextPathRecalculation;
    protected final int tickPathRecalcMax = 5;
    protected int ticksUntilNextAttack;
    private long lastCanUseCheck;

    public AbstractMeleeAttackUnitGoal(Mob mob, boolean followingTargetEvenIfNotSeen) {
        this.mob = mob;
        this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public void tickAttackCooldown() {
        if (this.ticksUntilNextAttack > ((AttackerUnit) this.mob).getAttackCooldown())
            this.ticksUntilNextAttack = getAttackInterval();
        if (ticksUntilNextAttack > 0) { // tick down even when not targeting anything
            this.ticksUntilNextAttack -= 1;
        }
    }

    public boolean canUse() {
        long i = this.mob.level().getGameTime();
        if (i - this.lastCanUseCheck < 20L) {
            return false;
        } else {
            this.lastCanUseCheck = i;
            LivingEntity livingentity = this.mob.getTarget();
            if (livingentity == null) {
                return false;
            } else if (!livingentity.isAlive()) {
                return false;
            } else {
                this.path = chasePath(livingentity);
                if (this.path != null) {
                    return true;
                } else {
                    return this.getAttackReachSqr(livingentity) >= this.mob.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
                }
            }
        }
    }

    public boolean canContinueToUse() {
        LivingEntity livingentity = this.mob.getTarget();
        if (livingentity == null) {
            return false;
        } else if (!livingentity.isAlive()) {
            return false;
        } else if (!this.followingTargetEvenIfNotSeen) {
            // Stay on while the target is close, not only while a path is being walked: tick() stops the navigation
            // once the target is in reach, and ending the goal there meant it only came back on canUse's next 20-tick
            // check. Every swing waited for that check (a 0.6/s unit swung 0.5/s, a 0.5/s unit lost a whole check
            // whenever its phase slipped), and a target knocked a step back was not chased until then either - in a
            // scrum melee units spent most of the fight out of reach (gametest melee diagnostics). The leash (reach +
            // 2 blocks) keeps the old give-up when the target runs off and no path can be made.
            double distSqr = this.mob.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
            double leash = Math.sqrt(this.getAttackReachSqr(livingentity)) + 2.0;
            return !this.mob.getNavigation().isDone() || distSqr <= leash * leash;
        } else if (!this.mob.isWithinRestriction(livingentity.blockPosition())) {
            return false;
        } else {
            // Only the keep-going test here. This used to re-run chasePath (Engage.melee + a vanilla createPath) and
            // moveTo on every call - and the goal selector calls it every tick, so every following chaser repathed
            // every tick past tick()'s 5/10/20-tick throttle (the Oct 22 match: Engage classify storm + navigation
            // under tinker, ~4% of the tick). tick() keeps chasing on its throttle, and repaths at once the first
            // tick the target leaves reach (see tick), so the chase answers a step back just as fast.
            return !(livingentity instanceof Player) || !livingentity.isSpectator() && !((Player)livingentity).isCreative();
        }
    }

    public void start() {
        if (!((Unit) this.mob).getHoldPosition())
            this.mob.getNavigation().moveTo(this.path, ((Unit) this.mob).getSpeedModifier());
        this.mob.setAggressive(true);
    }

    public void stop() {
        LivingEntity livingentity = this.mob.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(livingentity)) {
            this.mob.setTarget(null);
        }
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
        Engage.release(this.mob);
        approach = null;
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target != null) {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());

            if (distSqr < this.getAttackReachSqr(target)) {
                this.mob.getNavigation().stop();
                // a following chaser used to repath every tick from canContinueToUse; it now repaths from here only,
                // so the moment the target steps out of reach it gets a fresh path rather than waiting out a cooldown
                if (this.followingTargetEvenIfNotSeen)
                    ticksUntilNextPathRecalculation = 0;
            } else if (!((Unit) this.mob).getHoldPosition()) {
                if (ticksUntilNextPathRecalculation <= 0) {
                    Path path = chasePath(target);
                    this.mob.getNavigation().moveTo(path, ((Unit) this.mob).getSpeedModifier());
                    if (distSqr < 16)
                        ticksUntilNextPathRecalculation = tickPathRecalcMax;
                    else if (distSqr < 64)
                        ticksUntilNextPathRecalculation = tickPathRecalcMax * 2;
                    else
                        ticksUntilNextPathRecalculation = tickPathRecalcMax * 4;
                } else {
                    ticksUntilNextPathRecalculation -= 1;
                }
                // the path is walked but the slot's cell centre is still a hand short of reach: close to the ring point
                Engage.close(this.mob, approach, ((Unit) this.mob).getSpeedModifier());
            }
            this.checkAndPerformAttack(target, distSqr);
        }
    }

    /**
     * The chase path to the target: for a flyer the cell above it (as before); for everyone else the cell Engage
     * picks (a ring slot round the target, or the nearest bank cell for a target in water), null to hold. Vanilla
     * A*, synchronous - see Engage for why not the RTS pathfinder.
     */
    @Nullable
    protected Path chasePath(LivingEntity target) {
        if (((Unit) this.mob).isFlyingUnit())
            return mob.getNavigation().createPath(target.blockPosition().above(), 0);
        approach = Engage.melee(this.mob, target);
        return Engage.path(this.mob, target, approach);
    }

    public void checkAndPerformAttackIgnoreDist(LivingEntity target) {
        checkAndPerformAttack(target, 0);
    }

    protected void checkAndPerformAttack(LivingEntity target, double distSqr) {
        double d = this.getAttackReachSqr(target);
        if (distSqr <= d && this.ticksUntilNextAttack <= 0) {
            this.ticksUntilNextAttack = this.adjustedTickDelay(getAttackInterval());
            this.mob.swing(InteractionHand.MAIN_HAND);
            // a keyed swing (the Halberdier's thrust) lands its blow at the clip's hit key, the cooldown above still
            // running from the swing. A cleared target (a move or stop order) drops a blow still held; a switch to a
            // closer target (AttackerUnit's 12-tick rescan) does not - the thrust already under way still lands, or
            // a scrum would eat blows whose cooldown was spent
            HeldBlow.strike(this.mob, target, Math.sqrt(d), () -> this.mob.getTarget() != null);
        }
    }

    protected int getTicksUntilNextAttack() {
        return this.ticksUntilNextAttack;
    }

    protected int getAttackInterval() {
        return this.adjustedTickDelay((int) ((AttackerUnit) this.mob).getAttackCooldown());
    }

    protected double getAttackReachSqr(LivingEntity target) {
        float width = mob.getBbWidth();
        if (mob instanceof AttackerUnit attackerUnit)
            width += attackerUnit.getBonusMeleeRange();
        float targetWidth = target.getBbWidth();
        if (target instanceof Unit unit) {
            targetWidth += unit.getBonusMeleeRangeForAttackers();
        }
        return width * 2.0F * width * 2.0F + targetWidth;
    }
}
