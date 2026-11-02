package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.PossessionServer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierAnim;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * A body with a keyed attack clip swings for the clip's length (unit/interfaces/KeyedSwing + LivingEntityMixin).
 * Vanilla's swing is 6 ticks: swingTime counts up from the swing and {@code swinging} drops once it reaches the
 * duration, so a unit is still swinging 9 ticks after a swing only when its duration is longer than vanilla's. The
 * Halberdier's thrust is keyed for HalberdierAnim.THRUST_TICKS (18) and was played over vanilla's 6 - every eyes clip
 * showed it as a 3-frame flicker with no wind-up. Red before the fix: the Halberdier had stopped by tick 9.
 * <p>
 * The blow lands at the thrust's hit key, not on the swing's first tick (lovish, PR 342: "Move the hit"): the long
 * thrust otherwise flinched the target ~5 ticks before the blade got there. The cooldown still runs from the swing.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class KeyedSwingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** Vanilla's swing has ended by here (swinging drops on the 7th tick); the keyed 18-tick swing has not. */
    static final int MID = 9;
    /** Past the keyed clip's last tick: the long swing must end too, or the next thrust would never start. */
    static final int AFTER = HalberdierAnim.THRUST_TICKS + 3;
    /** Ticks from the swing's start to the thrust's hit key: where the Halberdier's blow must land (5). */
    static final int HIT = Math.round(HalberdierAnim.THRUST_HIT_P * HalberdierAnim.THRUST_TICKS);

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void halberdier_swing_lasts_its_keyed_thrust_clip(GameTestHelper helper) {
        String owner = "gametest_swing_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob halberdier = BigUnitGameTests.spawn(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4.5, 4.5);
        if (halberdier == null) {
            helper.fail("could not create the Halberdier");
            return;
        }
        halberdier.setNoAi(true);   // nothing but this test's swing moves its arm
        halberdier.swing(InteractionHand.MAIN_HAND);
        helper.runAfterDelay(MID, () -> {
            if (!halberdier.swinging) {
                halberdier.discard();
                helper.fail("the Halberdier's swing had ended " + MID + " ticks in (swingTime " + halberdier.swingTime
                    + "): its thrust is keyed for " + HalberdierAnim.THRUST_TICKS + " ticks, vanilla's 6 rush it");
            }
        });
        helper.runAfterDelay(AFTER, () -> {
            halberdier.discard();
            if (halberdier.swinging)
                helper.fail("the Halberdier was still swinging " + AFTER + " ticks in (swingTime " + halberdier.swingTime
                    + "): the keyed swing must end with its clip");
            helper.succeed();
        });
    }

    /** A VindicatorUnit subclass draws another body with no keyed attack (the Cutlass Raider): vanilla's 6 ticks stay. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void a_body_without_a_keyed_attack_keeps_vanillas_swing(GameTestHelper helper) {
        String owner = "gametest_swing_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob raider = BigUnitGameTests.spawn(helper, EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), owner, 4.5, 4.5);
        if (raider == null) {
            helper.fail("could not create the Cutlass Raider");
            return;
        }
        raider.setNoAi(true);
        raider.swing(InteractionHand.MAIN_HAND);
        helper.runAfterDelay(MID, () -> {
            raider.discard();
            if (raider.swinging)
                helper.fail("the Cutlass Raider was still swinging " + MID + " ticks in (swingTime " + raider.swingTime
                    + "): it has no keyed attack clip and should keep vanilla's 6-tick swing");
            helper.succeed();
        });
    }

    /**
     * The Halberdier's own attack (its melee goal) lands its blow HIT ticks after the swing starts, not before, and the
     * cooldown is the one it always was: set to the full interval on the swing's tick and not restarted by the blow.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void halberdier_blow_lands_at_the_thrust_hit_key(GameTestHelper helper) {
        blowTest(helper, "key", false, Upset.NONE);
    }

    /**
     * A possessed Halberdier's left click holds its blow to the same hit key: the thrust is the same 18-tick clip
     * under a player's hand, and the possession camera is where the early flinch shows the most.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void halberdier_possessed_blow_lands_at_the_thrust_hit_key(GameTestHelper helper) {
        blowTest(helper, "possessed", true, Upset.NONE);
    }

    /** A target that steps well out of reach during the wind-up is not struck: the blade never gets to it. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void halberdier_blow_is_dropped_when_the_target_steps_out_of_reach(GameTestHelper helper) {
        blowTest(helper, "away", false, Upset.STEPS_AWAY);
    }

    /** A Halberdier whose order is dropped during the wind-up (a move order clears its target) lands no blow. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void halberdier_blow_is_dropped_when_its_order_is_lost(GameTestHelper helper) {
        blowTest(helper, "order", false, Upset.ORDER_LOST);
    }

    /** What happens to the attack two ticks into the wind-up, before the hit key. */
    enum Upset { NONE, STEPS_AWAY, ORDER_LOST }

    /**
     * Two still Halberdiers of different owners side by side, 1.2 blocks apart (inside the goal's 1.43-block reach);
     * once both have ticked, the first attacks the second, by its melee goal or by a possessed click. Ticks since the
     * swing are read off the attacker's own swingTime (-1 on the swing, +1 each tick it lives), so the count is the
     * attacker's own clock whatever the test ticker's phase.
     */
    static void blowTest(GameTestHelper helper, String tag, boolean possessed, Upset upset) {
        String base = "gametest_blow_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String owner = base + "_a";
        UUID uuid = UUID.nameUUIDFromBytes(owner.getBytes());
        Mob attacker = BigUnitGameTests.spawn(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4.5, 4.5);
        Mob target = BigUnitGameTests.spawn(helper, EntityRegistrar.VINDICATOR_UNIT.get(), base + "_b", 5.7, 4.5);
        boolean[] done = { false }, swung = { false };
        Runnable cleanUp = () -> {
            done[0] = true;
            if (possessed)
                PossessionServer.release(uuid, null);
            if (attacker != null)
                attacker.discard();
            if (target != null)
                target.discard();
        };
        if (attacker == null || target == null) {
            cleanUp.run();
            helper.fail("could not create the two Halberdiers");
            return;
        }
        attacker.setNoAi(true);   // only this test's attack: no goal picks a target or swings of its own
        target.setNoAi(true);
        Field cooldownField;
        try {
            cooldownField = AbstractMeleeAttackUnitGoal.class.getDeclaredField("ticksUntilNextAttack");
            cooldownField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            cleanUp.run();
            helper.fail("no attack cooldown on the melee goal: " + e);
            return;
        }
        AbstractMeleeAttackUnitGoal goal = (AbstractMeleeAttackUnitGoal) ((AttackerUnit) attacker).getAttackGoal();
        int interval = (int) ((AttackerUnit) attacker).getAttackCooldown();
        float[] health = { 0 };
        // registered from the test body itself: onEachTick must not be scheduled from inside another callback
        helper.onEachTick(() -> {
            if (done[0])
                return;
            try {
                if (!swung[0]) {
                    if (attacker.tickCount < ArenaReady.TICKS || target.tickCount < ArenaReady.TICKS)
                        return;
                    health[0] = target.getHealth();
                    if (possessed) {
                        String why = PossessionServer.claim(helper.getLevel(), owner, uuid, attacker.getId(), true);
                        if (why != null)
                            throw new GameTestAssertException("could not possess the Halberdier: " + why);
                        PossessionServer.attack(uuid, attacker.getId(), -90, 10);   // facing east, at the target
                    } else {
                        attacker.setTarget(target);
                        goal.checkAndPerformAttackIgnoreDist(target);
                        int cooldown = cooldownField.getInt(goal);
                        if (cooldown != interval)
                            throw new GameTestAssertException("the swing set the cooldown to " + cooldown
                                + " ticks, not the attack interval of " + interval);
                    }
                    if (!attacker.swinging)
                        throw new GameTestAssertException("the attack did not swing (swingTime " + attacker.swingTime + ")");
                    swung[0] = true;
                    return;
                }
                int ticksIn = attacker.swingTime + 1;
                boolean hurt = target.getHealth() < health[0];
                if (upset != Upset.NONE) {
                    if (hurt)
                        throw new GameTestAssertException("the blow landed " + ticksIn + " ticks after the swing although "
                            + (upset == Upset.STEPS_AWAY ? "the target had stepped out of reach" : "the order was dropped")
                            + " at tick 2 (target health " + target.getHealth() + " of " + health[0] + ")");
                    if (ticksIn == 2) {
                        if (upset == Upset.STEPS_AWAY)
                            target.teleportTo(target.getX() + 4, target.getY(), target.getZ());
                        else
                            attacker.setTarget(null);
                    }
                    if (ticksIn >= HIT + 3) {
                        cleanUp.run();
                        helper.succeed();
                    }
                    return;
                }
                if (hurt && ticksIn < HIT)
                    throw new GameTestAssertException("the blow landed " + ticksIn + " ticks after the swing, before the "
                        + "thrust's hit key at " + HIT + " (THRUST_HIT_P " + HalberdierAnim.THRUST_HIT_P + " of "
                        + HalberdierAnim.THRUST_TICKS + " ticks): the target flinches before the blade arrives");
                if (hurt) {
                    if (ticksIn != HIT)
                        throw new GameTestAssertException("the blow landed " + ticksIn + " ticks after the swing, past "
                            + "the thrust's hit key at " + HIT);
                    if (!possessed) {
                        int cooldown = cooldownField.getInt(goal);
                        if (cooldown != interval - ticksIn)
                            throw new GameTestAssertException("the cooldown was " + cooldown + " when the blow landed "
                                + ticksIn + " ticks after the swing: it should run from the swing (" + (interval - ticksIn)
                                + "), as it always has");
                    }
                    cleanUp.run();
                    helper.succeed();
                    return;
                }
                if (ticksIn > HIT + 2)
                    throw new GameTestAssertException("the blow never landed (" + ticksIn + " ticks after the swing, "
                        + "target " + (target.isAlive() ? "alive" : "dead") + ", attacker's target "
                        + attacker.getTarget() + ")");
            } catch (GameTestAssertException | IllegalAccessException e) {
                cleanUp.run();
                throw e instanceof GameTestAssertException g ? g : new GameTestAssertException(e.toString());
            }
        });
    }
}
