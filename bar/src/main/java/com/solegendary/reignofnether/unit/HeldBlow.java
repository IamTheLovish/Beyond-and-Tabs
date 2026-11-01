package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.KeyedSwing;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A melee blow held from the swing's first tick to its clip's hit key (KeyedSwing.keyedHitTicks): the Halberdier's
 * thrust is an 18-tick clip whose blade arrives at THRUST_HIT_P (0.27, tick 5), and with the damage on the swing's
 * tick the target flinched ~5 ticks before the blade got there (lovish, PR 342: "Move the hit"). Only the damage
 * waits: the swing, its animate packet and the attack cooldown still start on the swing's tick, so the attack rate
 * is unchanged. The attacker ticks it (server side), so the blow lands exactly keyedHitTicks ticks after the swing.
 * Every other melee body holds its blow the same way to the frame its weapon strikes (BlowTiming; lovish, Oct 30:
 * "hit on the strike"), on a HeldBlow kept here and ticked from AttackerUnit's tick. The blow is dropped if the
 * attacker or the target dies, the attacker is stunned or frozen, the attack loses its order, or the target steps
 * more than {@link #REACH_TOLERANCE} past the reach it was struck at.
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

    /**
     * The blows held for attackers with no HeldBlow of their own (not a KeyedSwing), only while one is held: an entry
     * leaves when its blow lands or is dropped, when the attacker leaves the level ({@link #forget}: discarded, a
     * Temporary Militia's end, a chunk unloading) and when the server stops. The held blow's order check reaches its
     * goal and so its attacker, so a weak key alone would never let a removed attacker go.
     */
    private static final Map<Mob, HeldBlow> HELD = new HashMap<>();

    /** Ticks {@code attacker}'s blow held by {@link #strike}, if any (AttackerUnit's tick, server side). */
    public static void tickHeld(Mob attacker) {
        HeldBlow blow = HELD.get(attacker);
        if (blow == null)
            return;
        blow.tick(attacker);
        if (blow.target == null)
            HELD.remove(attacker);
    }

    /** The attacker left the level (UnitServerEvents.onEntityLeave): its held blow, if any, is dropped with it. */
    public static void forget(Entity attacker) {
        HELD.remove(attacker);
    }

    /** The server stops: no held blow outlives its level. */
    public static void clearAll() {
        HELD.clear();
    }

    /** Whether a blow is held for this attacker in {@link #HELD} (GameTests: nothing is left behind). */
    public static boolean holds(Entity attacker) {
        return HELD.containsKey(attacker);
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
        // a stun or a freeze stops the swing, as it stops every other attack (AttackerUnit's tick resets the unit's
        // behaviours on its next 4-tick check): the blow is dropped at once rather than landing up to 3 ticks into it
        return target != null && attacker.isAlive() && !attacker.isRemoved() && target.isAlive() && !target.isRemoved()
            && attacker.getEffect(MobEffectRegistrar.STUN.get()) == null
            && attacker.getEffect(MobEffectRegistrar.FREEZE.get()) == null
            && target.level() == attacker.level() && ordered.getAsBoolean()
            && attacker.distanceToSqr(target) <= maxDist * maxDist;
    }

    private void land(Mob attacker) {
        LivingEntity struck = target;
        cancel();
        attacker.doHurtTarget(struck);
    }
}
