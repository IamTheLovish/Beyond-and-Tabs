package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.KeyedSwing;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

/**
 * A melee blow held from the swing's first tick to its clip's hit key (KeyedSwing.keyedHitTicks): the Halberdier's
 * thrust is an 18-tick clip whose blade arrives at THRUST_HIT_P (0.27, tick 5), and with the damage on the swing's
 * tick the target flinched ~5 ticks before the blade got there (lovish, PR 342: "Move the hit"). Only the damage
 * waits: the swing, its animate packet and the attack cooldown still start on the swing's tick, so the attack rate
 * is unchanged. The attacker ticks it (server side) before its own tick, so the blow lands exactly keyedHitTicks
 * ticks after the swing. The blow is dropped if the attacker or the target dies, the attack loses its order, or the
 * target steps more than {@link #REACH_TOLERANCE} past the reach it was struck at.
 */
public final class HeldBlow {

    /** How far past its reach a target may step during the wind-up and still be struck: a walking unit's 5 ticks. */
    public static final double REACH_TOLERANCE = 1.0;

    @Nullable private LivingEntity target;
    private int ticksLeft;
    private double maxDist;
    private BooleanSupplier ordered = () -> false;

    /**
     * Strikes {@code target} now, or - for a keyed swing - holds the blow to the clip's hit key. {@code reach} is the
     * centre-to-centre distance the attack reached from; {@code ordered} says whether the attack still stands.
     */
    public static void strike(Mob attacker, LivingEntity target, double reach, BooleanSupplier ordered) {
        if (attacker instanceof KeyedSwing keyed && keyed.keyedHitTicks() > 0 && keyed.heldBlow() != null)
            keyed.heldBlow().hold(attacker, target, keyed.keyedHitTicks(), reach, ordered);
        else
            attacker.doHurtTarget(target);
    }

    /** Holds a blow for {@code ticks}; one still held lands first if it still may (no swing loses its damage). */
    public void hold(Mob attacker, LivingEntity target, int ticks, double reach, BooleanSupplier ordered) {
        if (this.target != null) {
            if (stands(attacker))
                land(attacker);
            else
                cancel();
        }
        this.target = target;
        this.ticksLeft = ticks;
        this.maxDist = reach + REACH_TOLERANCE;
        this.ordered = ordered;
    }

    /** Called once a tick from the attacker's tick, before the rest of it (server side only). */
    public void tick(Mob attacker) {
        if (target == null)
            return;
        if (!stands(attacker)) {
            cancel();
            return;
        }
        if (--ticksLeft <= 0)
            land(attacker);
    }

    private void cancel() {
        target = null;
        ordered = () -> false;
    }

    private boolean stands(Mob attacker) {
        return target != null && attacker.isAlive() && target.isAlive() && !target.isRemoved()
            && target.level() == attacker.level() && ordered.getAsBoolean()
            && attacker.distanceToSqr(target) <= maxDist * maxDist;
    }

    private void land(Mob attacker) {
        LivingEntity struck = target;
        cancel();
        attacker.doHurtTarget(struck);
    }
}
