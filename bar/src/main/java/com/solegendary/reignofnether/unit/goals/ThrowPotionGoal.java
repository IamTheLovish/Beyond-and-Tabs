package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.unit.support.PotionDelivery;
import com.solegendary.reignofnether.unit.support.SupportBrain;
import com.solegendary.reignofnether.unit.support.SupportKind;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import com.solegendary.reignofnether.util.MyMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * The Field Medic's throw (WitchUnit): walk into range of the target - a unit it keeps aiming at, or a spot - stop for
 * a short wind-up facing it ({@link #WINDUP_TICKS}), then throw. This goal only carries a throw out; who to throw at is
 * decided by a click (the potion abilities' use()), or by the medic herself - her attack order and her autocast, which
 * the support brain chooses and unit/support/PotionDelivery hands over (claude/healer-ai-oct21.md). The old goal also
 * chose its own autocast targets here, only when fully idle and harming first, onto allies in melee with an enemy.
 * <p>
 * A throw pauses the medic's move for the wind-up (this goal holds the MOVE flag, above the move goal); the move goal
 * picks its order back up after the release - a throw never cancels an order. A throw the medic chose herself is
 * checked once more at release: a harming cloud a friend walked under during the wind-up is not thrown (and costs no
 * cooldown). The server throws; on the client a predicted click just ends in range, as it always did.
 */
public class ThrowPotionGoal extends MoveToTargetBlockGoal {

    /** The wind-up before the potion leaves: she stops and faces the target (about the throw animation's lead-in). */
    public static final int WINDUP_TICKS = 6;

    private Potion potion = null;
    private LivingEntity targetEntity = null;
    /** Chosen by the medic herself (autocast or her attack order), not clicked by the player. */
    private boolean selfDecided = false;
    /** What the brain chose this throw as (null for a click), and the area kept clear of friends at release (0: none). */
    @Nullable private SupportKind kind = null;
    private double recheckRadius = 0;
    /** A spot throw with an exact aim (the harming cloud's offset centre); else the target block's centre. */
    private boolean hasAim = false;
    private double aimX, aimZ;
    /** The game time the wind-up releases the potion; -1 while walking (or idle). */
    private long releaseAt = -1;

    public ThrowPotionGoal(Mob mob) {
        super(mob, false, 0);
    }

    public void setPotion(Potion potion) {
        this.potion = potion;
    }

    // A click: throw at this unit (the medic keeps aiming at it as it moves) or at this spot. Either replaces whatever
    // throw was under way.
    public void setTarget(LivingEntity entity) {
        this.targetEntity = entity;
        this.selfDecided = false;
        this.kind = null;
        this.recheckRadius = 0;
        this.hasAim = false;
        this.releaseAt = -1;
    }

    public void setTarget(BlockPos bpTarget) {
        this.setTarget((LivingEntity) null);
        this.setMoveTarget(bpTarget);
    }

    /**
     * The medic's own throw at a unit (her autocast, or her attack order), as {@code kind}; with {@code recheckRadius}
     * above 0 no friend may stand within it of the aim at release.
     */
    public void selfThrow(Potion potion, LivingEntity target, SupportKind kind, double recheckRadius) {
        setPotion(potion);
        setTarget(target);
        selfDecided = true;
        this.kind = kind;
        this.recheckRadius = recheckRadius;
    }

    /** The medic's own throw at a spot: walks to {@code at} if out of range, aims at exactly (x, z). */
    public void selfThrow(Potion potion, BlockPos at, double x, double z, SupportKind kind, double recheckRadius) {
        setPotion(potion);
        setTarget(at);
        selfDecided = true;
        this.kind = kind;
        this.recheckRadius = recheckRadius;
        hasAim = true;
        aimX = x;
        aimZ = z;
    }

    /** A throw is under way (walking to it or winding up). */
    public boolean isBusy() {
        return potion != null && (targetEntity != null || moveTarget != null);
    }

    public boolean isWindingUp() {
        return releaseAt >= 0;
    }

    public boolean isSelfDecided() {
        return selfDecided;
    }

    /** Drops a throw the medic chose herself that has not begun its wind-up (she is stepping out of danger). */
    public void cancelSelfThrow() {
        if (selfDecided && releaseAt < 0 && isBusy())
            stop();
    }

    @Override
    public boolean canContinueToUse() {
        if (releaseAt >= 0)
            return true;   // standing for the wind-up: no re-path toward the target
        return super.canContinueToUse();
    }

    @Override
    public void start() {
        if (releaseAt >= 0) {
            this.mob.getNavigation().stop();
            return;
        }
        super.start();
    }

    @Override
    public void tick() {
        if (potion == null) {
            if (targetEntity != null || moveTarget != null)
                stop();
            return;
        }
        if (this.targetEntity != null) {
            if (!targetEntity.isAlive() || targetEntity.isRemoved()) {
                stop();
                return;
            }
            this.setMoveTarget(this.targetEntity.getOnPos());
        }
        if (moveTarget == null)
            return;
        WitchUnit witch = (WitchUnit) this.mob;
        boolean inRange = MyMath.distance(this.mob.getX(), this.mob.getZ(), moveTarget.getX(), moveTarget.getZ())
            <= witch.getPotionThrowRange();
        if (!(this.mob.level() instanceof ServerLevel level)) {
            if (inRange)
                stop();   // the client's prediction of a click ends here; the server throws
            return;
        }
        long now = level.getGameTime();
        if (releaseAt < 0) {
            if (!inRange)
                return;   // still walking into range
            releaseAt = now + WINDUP_TICKS;
            this.mob.getNavigation().stop();
        }
        this.mob.getLookControl().setLookAt(aimX(), moveTarget.getY() + 1, aimZ());
        if (now >= releaseAt)
            release(level, witch);
    }

    // a unit target is led by where it walks during the potion's flight (LEAD_TICKS, at most MAX_LEAD blocks): a heal
    // thrown at a friend on the march used to land where it had been
    private double aimX() {
        return targetEntity != null ? targetEntity.getX() + lead(targetEntity.getDeltaMovement().x)
            : hasAim ? aimX : moveTarget.getX() + 0.5;
    }

    private double aimZ() {
        return targetEntity != null ? targetEntity.getZ() + lead(targetEntity.getDeltaMovement().z)
            : hasAim ? aimZ : moveTarget.getZ() + 0.5;
    }

    /** Ticks of walking a thrown potion leads a moving target by (its flight is about 20). */
    static final int LEAD_TICKS = 15;
    static final double MAX_LEAD = 3.0;

    private static double lead(double velocity) {
        return Math.max(-MAX_LEAD, Math.min(MAX_LEAD, velocity * LEAD_TICKS));
    }

    private void release(ServerLevel level, WitchUnit witch) {
        double x = aimX(), z = aimZ();
        double y = targetEntity != null ? targetEntity.getY() : moveTarget.getY() + 1;
        if (selfDecided && recheckRadius > 0 && kind != null
                && !SupportBrain.harmSafeAt(level, witch, witch, x, z, recheckRadius, PotionDelivery.undeadRule(kind))) {
            stop();   // a friend walked under the aim during the wind-up: no throw, no cooldown; the next look decides again
            return;
        }
        witch.throwPotion(new Vec3(x, y, z), potion);
        // the four potions share the throw: each goes on its own cooldown
        for (Ability potionAbility : witch.getAbilities().get())
            AbilityClientboundPacket.sendSetCooldownPacket(this.mob.getId(), potionAbility.action, potionAbility.cooldownMax);
        SupportBrain.onCast(witch, witch, kind != null ? kind : PotionDelivery.kindOf(potion), selfDecided, x, z);
        stop();
    }

    /** The nearest fire block within 10 blocks across and 4 up or down (the water potion's other job), or null. */
    @Nullable
    public BlockPos findNearbyFireBlock() {
        ArrayList<BlockPos> bps = new ArrayList<>();
        for (int x = -10; x < 10; x++)
            for (int y = -4; y < 4; y++)
                for (int z = -10; z < 10; z++) {
                    BlockPos bp = mob.blockPosition().offset(x, y, z);
                    if (mob.level().getBlockState(bp).getBlock() == Blocks.FIRE)
                        bps.add(bp);
                }
        bps.sort(Comparator.comparing(bp -> bp.distSqr(this.mob.blockPosition())));
        if (!bps.isEmpty())
            return bps.get(0);
        else
            return null;
    }

    @Override
    public void stop() {
        this.stopMoving();
        this.targetEntity = null;
        this.setPotion(null);
        this.selfDecided = false;
        this.kind = null;
        this.recheckRadius = 0;
        this.hasAim = false;
        this.releaseAt = -1;
    }
}
