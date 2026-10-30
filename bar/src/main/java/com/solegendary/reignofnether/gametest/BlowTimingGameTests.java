package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.goals.MeleeWindupAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;

/**
 * A melee blow lands when the weapon visibly strikes, not on the swing's first tick (lovish, Oct 30: "hit on the
 * strike"; design-draft-combat-feel-2026-10-30.md, gain 1). The plain melee path swung and struck in the same tick, so
 * the target's flinch, the hit spark, the hit sound and the attacker's contact recoil all came 3-9 ticks before the
 * arm got there. The blow is now held (HeldBlow) to the body's hit key, as the Halberdier's has been since PR 342:
 * <ul>
 *   <li>vanilla's 6-tick arm swing (humanoid, illager and villager-model bodies, piglins): the downstroke at tick 3;</li>
 *   <li>the generated chop (HumanActs.chop, the Dawn Paladin): tick 4;</li>
 *   <li>the Mossback Bison's 16-tick horn toss: the drive at P 0.55, tick 9;</li>
 *   <li>bodies whose attack clip starts on the damage event itself (the iron golem's entity event 4): unchanged.</li>
 * </ul>
 * The cooldown still runs from the swing, so the rate of blows is the same; a target that steps out of reach before the
 * hit key is not struck. A wind-up attacker (Royal Guard, Marauder...) checks its reach again when its wind-up ends.
 * The expected ticks are written here, not read from the code, so the tests stand on main's code (tests first).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlowTimingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** Vanilla's arm swing: the downstroke's contact frame. */
    static final int SWING_HIT = 3;
    /** HumanActs.chop: the axe passes the downstroke around t 0.6 of the 6-tick swing. */
    static final int CHOP_HIT = 4;
    /** The Mossback Bison's horn toss: the drive key at P 0.55 of its 16-tick clip (animations/Quadruped). */
    static final int BISON_HIT = 9;

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void zombie_blow_lands_on_its_arm_swing(GameTestHelper helper) {
        blowTest(helper, "zombie", EntityRegistrar.ZOMBIE_UNIT.get(), SWING_HIT, Upset.NONE);
    }

    /** A villager-model body on the Vindicator base class (not a VindicatorUnit). */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void militia_blow_lands_on_its_swing(GameTestHelper helper) {
        blowTest(helper, "militia", EntityRegistrar.MILITIA_UNIT.get(), SWING_HIT, Upset.NONE);
    }

    /** A VindicatorUnit subclass with no keyed thrust: it holds its blow on the Halberdier's own HeldBlow. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void cutlass_raider_blow_lands_on_its_swing(GameTestHelper helper) {
        blowTest(helper, "raider", EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), SWING_HIT, Upset.NONE);
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void piglin_grunt_blow_lands_on_its_swing(GameTestHelper helper) {
        blowTest(helper, "grunt", EntityRegistrar.GRUNT_UNIT.get(), SWING_HIT, Upset.NONE);
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void dawn_paladin_blow_lands_on_its_chop(GameTestHelper helper) {
        blowTest(helper, "paladin", EntityRegistrar.DAWN_PALADIN_UNIT.get(), CHOP_HIT, Upset.NONE);
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void mossback_bison_blow_lands_on_its_horn_drive(GameTestHelper helper) {
        blowTest(helper, "bison", EntityRegistrar.MOSSBACK_BISON_UNIT.get(), BISON_HIT, Upset.NONE);
    }

    /** The iron golem's swing starts on the damage event (entity event 4): its blow must not be held as well. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void iron_golem_blow_still_lands_with_its_swing(GameTestHelper helper) {
        blowTest(helper, "golem", EntityRegistrar.IRON_GOLEM_UNIT.get(), 0, Upset.NONE);
    }

    /** A target that steps well out of reach before the downstroke is not struck. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void zombie_blow_is_dropped_when_the_target_steps_out_of_reach(GameTestHelper helper) {
        blowTest(helper, "away", EntityRegistrar.ZOMBIE_UNIT.get(), SWING_HIT, Upset.STEPS_AWAY);
    }

    /**
     * An attacker removed without dying while its blow is held (discarded, a Temporary Militia's end, a chunk
     * unloading) lands no blow, and HeldBlow keeps nothing of it: the held blow's order check reaches the attacker, so
     * an entry left behind would pin the unit, its target and its level.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void zombie_discarded_mid_blow_leaves_nothing_held(GameTestHelper helper) {
        blowTest(helper, "discard", EntityRegistrar.ZOMBIE_UNIT.get(), SWING_HIT, Upset.DISCARDED);
    }

    /** A stun stops the swing as it stops any attack: the held blow is dropped, not landed a few ticks into it. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void zombie_blow_is_dropped_when_the_attacker_is_stunned(GameTestHelper helper) {
        blowTest(helper, "stun", EntityRegistrar.ZOMBIE_UNIT.get(), SWING_HIT, Upset.STUNNED);
    }

    /** What happens one tick into the swing, before the hit key. */
    enum Upset { NONE, STEPS_AWAY, DISCARDED, STUNNED }

    /**
     * Holding the blow does not slow the attack: over 4 attack intervals of the goal's own attacks (attempted every
     * tick, as the goal does in a fight) a zombie lands exactly one blow per interval.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void zombie_lands_one_blow_per_attack_interval(GameTestHelper helper) {
        String base = "gametest_blowrate_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob[] pair = pair(helper, base, EntityRegistrar.ZOMBIE_UNIT.get());
        if (pair == null)
            return;
        Mob attacker = pair[0], target = pair[1];
        AbstractMeleeAttackUnitGoal goal = (AbstractMeleeAttackUnitGoal) ((AttackerUnit) attacker).getAttackGoal();
        int interval = (int) ((AttackerUnit) attacker).getAttackCooldown();
        int span = interval * 4;
        int[] start = { -1 }, blows = { 0 };
        boolean[] done = { false };
        float[] health = { 0 };
        double tx = target.getX(), ty = target.getY(), tz = target.getZ();
        helper.onEachTick(() -> {
            if (done[0])
                return;
            try {
                if (start[0] < 0) {
                    if (attacker.tickCount < ArenaReady.TICKS || target.tickCount < ArenaReady.TICKS)
                        return;
                    start[0] = attacker.tickCount;
                    aim(attacker, target);
                    health[0] = target.getMaxHealth();
                    target.setHealth(health[0]);
                }
                if (target.getHealth() < health[0]) {
                    blows[0]++;
                    target.setHealth(health[0]);   // keep it alive and count the next blow the same way
                    // and back where it stood: the blow's knockback must not carry it out of reach over four blows
                    target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    target.teleportTo(tx, ty, tz);
                }
                int ticks = attacker.tickCount - start[0];
                if (ticks >= span + SWING_HIT + 2) {
                    done[0] = true;
                    attacker.discard();
                    target.discard();
                    if (blows[0] != 4)
                        throw new GameTestAssertException(blows[0] + " blows landed in " + span + " ticks (4 attack "
                            + "intervals of " + interval + "): holding the blow to the strike must not change the "
                            + "rate of blows");
                    helper.succeed();
                    return;
                }
                if (ticks < span)
                    goal.checkAndPerformAttackIgnoreDist(target);
            } catch (GameTestAssertException e) {
                done[0] = true;
                attacker.discard();
                target.discard();
                throw e;
            }
        });
    }

    /**
     * A wind-up attacker (the Royal Guard's 12-tick wind-up) whose target walks away during the wind-up does not strike
     * it from across the field: the reach is checked again when the wind-up ends. Its attack still completes (the
     * swing and the cooldown), it only lands no damage.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void windup_blow_is_dropped_when_the_target_walked_away(GameTestHelper helper) {
        windupTest(helper, "away", true);
    }

    /** The control: a target that stays put is struck when the wind-up ends. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void windup_blow_lands_when_the_target_stays(GameTestHelper helper) {
        windupTest(helper, "stays", false);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Gives the attacker its target as an order does: through its SelectedTargetGoal as well as vanilla's field, since
     * many units (piglins among them) answer getTarget() from that goal, and the held blow's order check asks it.
     */
    static void aim(Mob attacker, net.minecraft.world.entity.LivingEntity target) {
        attacker.setTarget(target);
        if (attacker instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit && unit.getTargetGoal() != null)
            unit.getTargetGoal().setTarget(target);
    }

    /**
     * The attacker (of {@code type}) and a still zombie target of another owner, touching at arm's length on the east
     * side. Both have no AI: nothing swings or moves but the test. Null (and the test failed) if either is missing.
     */
    static Mob[] pair(GameTestHelper helper, String base, EntityType<? extends Mob> type) {
        Mob attacker = BigUnitGameTests.spawn(helper, type, base + "_a", 4.5, 4.5);
        if (attacker == null) {
            helper.fail("could not create the attacker " + type);
            return null;
        }
        Mob target = BigUnitGameTests.spawn(helper, EntityRegistrar.ZOMBIE_UNIT.get(), base + "_b",
            4.5 + attacker.getBbWidth() / 2 + 0.3 + 0.3, 4.5);
        if (target == null) {
            attacker.discard();
            helper.fail("could not create the target");
            return null;
        }
        attacker.setNoAi(true);
        target.setNoAi(true);
        if (target.getAttribute(Attributes.MAX_HEALTH) != null)
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        return new Mob[] { attacker, target };
    }

    /**
     * Once both have ticked, the attacker's melee goal attacks the target; the blow must land exactly {@code hit} ticks
     * after the swing (0: in the swing's own tick). With an {@code upset} one tick into the swing (the target moved 4
     * blocks away, the attacker discarded or stunned) no blow may land. Ticks are counted from the swing on the
     * attacker's own tickCount, or on the test's own count once the attacker is gone.
     */
    static void blowTest(GameTestHelper helper, String tag, EntityType<? extends Mob> type, int hit, Upset upset) {
        String base = "gametest_blowtime_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob[] pair = pair(helper, base, type);
        if (pair == null)
            return;
        Mob attacker = pair[0], target = pair[1];
        if (!(((AttackerUnit) attacker).getAttackGoal() instanceof AbstractMeleeAttackUnitGoal goal)) {
            attacker.discard();
            target.discard();
            helper.fail(type + " has no melee attack goal");
            return;
        }
        int[] swingTick = { -1 }, since = { 0 };
        boolean[] done = { false };
        float[] health = { 0 };
        Runnable cleanUp = () -> {
            done[0] = true;
            attacker.discard();
            target.discard();
        };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            try {
                if (swingTick[0] < 0) {
                    if (attacker.tickCount < ArenaReady.TICKS || target.tickCount < ArenaReady.TICKS)
                        return;
                    health[0] = target.getHealth();
                    aim(attacker, target);
                    swingTick[0] = attacker.tickCount;
                    goal.checkAndPerformAttackIgnoreDist(target);
                    if (!attacker.swinging && attacker.attackAnim <= 0)
                        throw new GameTestAssertException(type + "'s attack did not swing");
                }
                else
                    since[0]++;
                int ticksIn = upset == Upset.DISCARDED ? since[0] : attacker.tickCount - swingTick[0];
                boolean hurt = target.getHealth() < health[0];
                if (upset != Upset.NONE) {
                    if (hurt) {
                        String why = upset == Upset.STEPS_AWAY ? "the target had stepped 4 blocks out of reach"
                            : upset == Upset.DISCARDED ? "the attacker had been discarded" : "the attacker had been stunned";
                        throw new GameTestAssertException("the blow landed " + ticksIn + " ticks after the swing "
                            + "although " + why + " at tick 1 (target health " + target.getHealth() + " of "
                            + health[0] + ")");
                    }
                    if (ticksIn == 1) {
                        switch (upset) {
                            case STEPS_AWAY -> target.teleportTo(target.getX() + 4, target.getY(), target.getZ());
                            case DISCARDED -> attacker.discard();
                            default -> attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get(), 60));
                        }
                    }
                    if (ticksIn >= hit + 3) {
                        boolean held = com.solegendary.reignofnether.unit.HeldBlow.holds(attacker);
                        cleanUp.run();
                        if (upset == Upset.DISCARDED && held)
                            throw new GameTestAssertException("HeldBlow still held a blow for the discarded attacker "
                                + (hit + 2) + " ticks after it left the level");
                        helper.succeed();
                    }
                    return;
                }
                if (hurt) {
                    if (ticksIn != hit)
                        throw new GameTestAssertException(type + "'s blow landed " + ticksIn + " ticks after the "
                            + "swing; its weapon strikes at tick " + hit + (ticksIn < hit
                            ? ": the target flinches before the weapon gets there" : ""));
                    cleanUp.run();
                    helper.succeed();
                    return;
                }
                if (ticksIn > hit + 2)
                    throw new GameTestAssertException(type + "'s blow never landed (" + ticksIn + " ticks after the "
                        + "swing, target " + (target.isAlive() ? "alive" : "dead") + ")");
            } catch (GameTestAssertException e) {
                cleanUp.run();
                throw e;
            }
        });
    }

    /**
     * A Royal Guard next to a still zombie, its target set: its own tick runs the wind-up (AttackerUnit.tickBody). Two
     * ticks into the wind-up the target is moved 4 blocks away ({@code walkAway}) or left; the wind-up must still
     * finish (the cooldown starts) and the target is struck only if it stayed.
     */
    static void windupTest(GameTestHelper helper, String tag, boolean walkAway) {
        String base = "gametest_windup_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob[] pair = pair(helper, base, EntityRegistrar.ROYAL_GUARD_UNIT.get());
        if (pair == null)
            return;
        Mob attacker = pair[0], target = pair[1];
        if (!(((AttackerUnit) attacker).getAttackGoal() instanceof MeleeWindupAttackUnitGoal goal)) {
            attacker.discard();
            target.discard();
            helper.fail("the Royal Guard has no wind-up attack goal");
            return;
        }
        Field windupField, cooldownField;
        try {
            windupField = MeleeWindupAttackUnitGoal.class.getDeclaredField("windupTicksLeft");
            windupField.setAccessible(true);
            cooldownField = AbstractMeleeAttackUnitGoal.class.getDeclaredField("ticksUntilNextAttack");
            cooldownField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            attacker.discard();
            target.discard();
            helper.fail("no wind-up state on the goal: " + e);
            return;
        }
        int full = ((com.solegendary.reignofnether.unit.interfaces.KeyframeAnimated) attacker).getAttackWindupTicks();
        int[] started = { -1 };
        boolean[] done = { false }, armed = { false };
        float[] health = { 0 };
        Runnable cleanUp = () -> {
            done[0] = true;
            attacker.discard();
            target.discard();
        };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            try {
                if (!armed[0]) {
                    if (attacker.tickCount < ArenaReady.TICKS || target.tickCount < ArenaReady.TICKS)
                        return;
                    health[0] = target.getHealth();
                    cooldownField.setInt(goal, 0);
                    goal.resetWindup();
                    aim(attacker, target);
                    armed[0] = true;
                    return;
                }
                boolean hurt = target.getHealth() < health[0];
                int left = windupField.getInt(goal);
                if (started[0] < 0) {
                    if (left < full)
                        started[0] = attacker.tickCount - (full - left);
                    else if (hurt)
                        throw new GameTestAssertException("the Royal Guard struck without a wind-up");
                    else if (attacker.tickCount > ArenaReady.TICKS + 20)
                        throw new GameTestAssertException("the Royal Guard's wind-up never started");
                    else
                        return;
                }
                int ticksIn = attacker.tickCount - started[0];
                if (walkAway && ticksIn == 2)
                    target.teleportTo(target.getX() + 4, target.getY(), target.getZ());
                if (walkAway && hurt)
                    throw new GameTestAssertException("the Royal Guard struck its target " + ticksIn + " ticks into its "
                        + full + "-tick wind-up although the target had walked 4 blocks away at tick 2");
                if (!walkAway && hurt) {
                    cleanUp.run();
                    helper.succeed();
                    return;
                }
                if (ticksIn >= full + 3) {
                    boolean finished = cooldownField.getInt(goal) > 0;
                    cleanUp.run();
                    if (!finished)
                        throw new GameTestAssertException("the Royal Guard's attack never finished (no cooldown "
                            + full + " ticks into its wind-up)");
                    if (!walkAway)
                        throw new GameTestAssertException("the Royal Guard never struck a target that stayed put");
                    helper.succeed();
                }
            } catch (GameTestAssertException | IllegalAccessException e) {
                cleanUp.run();
                throw e instanceof GameTestAssertException g ? g : new GameTestAssertException(e.toString());
            }
        });
    }
}
