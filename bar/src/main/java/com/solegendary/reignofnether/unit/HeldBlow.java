package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.KeyedSwing;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * A melee blow held from the swing's first tick to its clip's hit key (KeyedSwing.keyedHitTicks): the Halberdier's
 * thrust is an 18-tick clip whose blade arrives at THRUST_HIT_P (0.27, tick 5), and with the damage on the swing's
 * tick the target flinched ~5 ticks before the blade got there (lovish, PR 342: "Move the hit"). Only the damage
 * waits: the swing, its animate packet and the attack cooldown still start on the swing's tick, so the attack rate
 * is unchanged. The attacker ticks it (server side), so the blow lands exactly keyedHitTicks ticks after the swing.
 * Every other melee body holds its blow the same way to the frame its weapon strikes (BlowTiming; lovish, Oct 30:
 * "hit on the strike"), on a HeldBlow kept here and ticked from AttackerUnit's tick. The blow is dropped if the
 * attacker or the target dies, the attack loses its order, or the target steps more than {@link #REACH_TOLERANCE}
 * past the reach it was struck at.
 */
public final class HeldBlow {

    /** How far past its reach a target may step during the wind-up and still be struck: a walking unit's 5 ticks. */
    public static final double REACH_TOLERANCE = 1.0;

    @Nullable private LivingEntity target;
    private int ticksLeft;
    private int heldAt = Integer.MIN_VALUE;
    private double maxDist;
    private BooleanSupplier ordered = () -> false;

    /**
     * Strikes {@code target} now, or - for a keyed swing - holds the blow to the clip's hit key. {@code reach} is the
     * centre-to-centre distance the attack reached from; {@code ordered} says whether the attack still stands.
     */
    public static void strike(Mob attacker, LivingEntity target, double reach, BooleanSupplier ordered) {
        if (attacker instanceof KeyedSwing keyed && keyed.heldBlow() != null) {
            if (keyed.keyedHitTicks() > 0)
                keyed.heldBlow().hold(attacker, target, keyed.keyedHitTicks(), reach, ordered);
            else
                attacker.doHurtTarget(target);
            return;
        }
        // any other body: held to the frame its weapon strikes (BlowTiming), ticked by AttackerUnit's tick
        int ticks = BlowTiming.hitTicks(attacker);
        if (ticks > 0 && !attacker.level().isClientSide())
            HELD.computeIfAbsent(attacker, m -> new HeldBlow()).hold(attacker, target, ticks, reach, ordered);
        else
            attacker.doHurtTarget(target);
    }

    /** The blows of attackers with no HeldBlow of their own (not a KeyedSwing): one each, kept while it lives. */
    private static final Map<Mob, HeldBlow> HELD = new WeakHashMap<>();

    /** Ticks {@code attacker}'s blow held by {@link #strike}, if any (AttackerUnit's tick, server side). */
    public static void tickHeld(Mob attacker) {
        HeldBlow blow = HELD.get(attacker);
        if (blow != null)
            blow.tick(attacker);
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
        this.heldAt = attacker.tickCount;
        this.maxDist = reach + REACH_TOLERANCE;
        this.ordered = ordered;
    }

    /** Called once a tick from the attacker's tick (server side only); a blow held this tick waits for the next. */
    public void tick(Mob attacker) {
        if (target == null || attacker.tickCount == heldAt)
            return;   // (a blow held earlier in this same tick - by a goal, before the attacker's tick ran - waits)
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
