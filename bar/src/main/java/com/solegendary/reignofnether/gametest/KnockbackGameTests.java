package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Focus fire does not bounce its target around (design-draft-combat-feel-2026-10-30.md, gain 5). Every unit hit was a
 * vanilla 0.4 shove, and RTS hits stack (LivingEntityMixin.unitHitsStack clears the hurt window for each attacker),
 * so a unit under six attackers was shoved six times in a few ticks and jittered across the ground. A unit now takes
 * at most one light shove per 5 server ticks; inside that window a shove lands only if it is stronger than the one
 * that opened it (the strongest wins). Heavy shoves (strength 0.5 or more: attack knockback 1.0 or more through
 * Mob.doHurtTarget's x0.5, and every ability's fling) always land, and one blow's own second shove (the attack
 * knockback part that follows the hurt shove in the same doHurtTarget) still lands, so a single attacker hits as it
 * did. The tests read the knockback events themselves (a listener after every handler), not the motion, so they do
 * not depend on the target's knockback resistance or on it moving. The window is written here, not read from the
 * code, so the tests stand on main's code (tests first).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class KnockbackGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** At most one light shove per this many server ticks. */
    static final int WINDOW = 5;
    /** A shove this strong or more always lands. */
    static final double HEAVY = 0.5;
    /** Vanilla's shove for every hit with an attacker (LivingEntity.hurt). */
    static final double HIT = 0.4;

    // ------------------------------------------------------------------ red on main

    /**
     * Six zombies around one target, each starting its attacks a tick after the last: their blows land a tick apart.
     * The light shoves that land are at least {@link #WINDOW} ticks apart, some are dropped, and the target is still
     * shoved (at least twice over two attack intervals).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void focus_fire_shoves_the_target_once_per_5_ticks(GameTestHelper helper) {
        String base = "gametest_knockfocus_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob target = victim(helper, base + "_b", 4.5, 4.5);
        if (target == null)
            return;
        List<Mob> attackers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Mob z = BigUnitGameTests.spawn(helper, EntityRegistrar.ZOMBIE_UNIT.get(), base + "_a",
                4.5 + Math.cos(a), 4.5 + Math.sin(a));
            if (z == null) {
                attackers.forEach(Mob::discard);
                target.discard();
                helper.fail("could not create attacker " + i);
                return;
            }
            z.setNoAi(true);
            attackers.add(z);
        }
        ShoveLog log = ShoveLog.on(target);
        int interval = (int) ((AttackerUnit) attackers.get(0)).getAttackCooldown();
        int span = interval * 2 + 6 + 4;
        int[] start = { -1 };
        boolean[] done = { false };
        double tx = target.getX(), ty = target.getY(), tz = target.getZ();
        Runnable cleanUp = () -> {
            done[0] = true;
            log.close();
            attackers.forEach(Mob::discard);
            target.discard();
        };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            try {
                if (start[0] < 0) {
                    if (target.tickCount < ArenaReady.TICKS
                            || attackers.stream().anyMatch(m -> m.tickCount < ArenaReady.TICKS))
                        return;
                    start[0] = log.now();
                    for (Mob z : attackers)
                        BlowTimingGameTests.aim(z, target);
                }
                int ticks = log.now() - start[0];
                target.setHealth(target.getMaxHealth());
                target.setDeltaMovement(Vec3.ZERO);
                target.teleportTo(tx, ty, tz);   // in reach of all six, whatever the shoves did
                for (int i = 0; i < attackers.size(); i++)
                    if (ticks >= i && ticks < span - 4
                            && ((AttackerUnit) attackers.get(i)).getAttackGoal() instanceof AbstractMeleeAttackUnitGoal g)
                        g.checkAndPerformAttackIgnoreDist(target);
                if (ticks < span)
                    return;
                cleanUp.run();
                List<String> problems = new ArrayList<>();
                if (log.all() < 6)
                    problems.add("only " + log.all() + " shoves were tried (six attackers)");
                if (log.landed.size() < 2)
                    problems.add("only " + log.landed.size() + " shoves landed: focus fire must still shove");
                if (log.dropped == 0)
                    problems.add("no shove was dropped");
                int last = Integer.MIN_VALUE / 2;
                for (double[] s : log.landed) {
                    if (s[1] >= HEAVY)
                        continue;
                    if (s[0] - last < WINDOW)
                        problems.add("light shoves landed " + (int) (s[0] - last) + " ticks apart");
                    last = (int) s[0];
                }
                if (!problems.isEmpty())
                    throw new GameTestAssertException(String.join("; ", problems) + " (" + log + ")");
                helper.succeed();
            } catch (GameTestAssertException e) {
                if (!done[0])
                    cleanUp.run();
                throw e;
            }
        });
    }

    /** Five hit shoves in one tick (five attackers' blows landing together) move the target as one does. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void shoves_in_one_tick_move_the_target_once(GameTestHelper helper) {
        String base = "gametest_knockonce_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob one = victim(helper, base + "_1", 3.5, 4.5);
        Mob five = victim(helper, base + "_5", 6.5, 4.5);
        if (one == null || five == null) {
            if (one != null) one.discard();
            if (five != null) five.discard();
            return;
        }
        ShoveLog log = ShoveLog.on(five);
        ArenaReady.whenTicking(helper, List.of(one, five), 60, t -> {
            try {
                one.setDeltaMovement(Vec3.ZERO);
                five.setDeltaMovement(Vec3.ZERO);
                one.knockback(HIT, 1, 0);
                for (int i = 0; i < 5; i++)
                    five.knockback(HIT, 1, 0);
                double v1 = one.getDeltaMovement().x, v5 = five.getDeltaMovement().x;
                List<String> problems = new ArrayList<>();
                if (v1 >= -0.1)
                    problems.add("one shove gave no push west (vx " + v1 + ")");
                if (Math.abs(v5 - v1) > 1e-6)
                    problems.add("five shoves in one tick gave vx " + v5 + ", one gave " + v1);
                if (log.landed.size() != 1 || log.dropped != 4)
                    problems.add(log.toString());
                if (!problems.isEmpty())
                    throw new GameTestAssertException(String.join("; ", problems));
                helper.succeed();
            } finally {
                log.close();
                one.discard();
                five.discard();
            }
        });
    }

    /** A light shove 4 ticks after one is dropped; one 5 ticks after it lands. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void a_light_shove_inside_5_ticks_is_dropped(GameTestHelper helper) {
        shoveScript(helper, "window", new double[][] { { 0, HIT }, { WINDOW - 1, HIT }, { WINDOW, HIT } },
            new boolean[] { true, false, true });
    }

    // ------------------------------------------------------------------ controls: pass on main and with the gate

    /** A heavy shove lands even right after a light one, and so does a second heavy one. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void a_heavy_shove_always_lands(GameTestHelper helper) {
        shoveScript(helper, "heavy", new double[][] { { 0, HIT }, { 0, 0.75 }, { 1, 0.75 }, { 2, HEAVY } },
            new boolean[] { true, true, true, true });
    }

    /** Inside the window a stronger shove than the one that opened it lands (the strongest wins). */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void a_stronger_shove_inside_the_window_wins(GameTestHelper helper) {
        shoveScript(helper, "stronger", new double[][] { { 0, 0.2 }, { 2, HIT } }, new boolean[] { true, true });
    }

    /** Light shoves 5 ticks apart all land. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void a_shove_5_ticks_later_lands(GameTestHelper helper) {
        shoveScript(helper, "later", new double[][] { { 0, HIT }, { WINDOW, HIT }, { 2 * WINDOW, HIT } },
            new boolean[] { true, true, true });
    }

    /**
     * The War Mammoth's tusk blow (attack knockback 3.0: a 1.5 shove) lands in full on a target that light shoves keep
     * inside a window.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void war_mammoth_still_knocks_back_full(GameTestHelper helper) {
        blowTest(helper, "mammoth", EntityRegistrar.WAR_MAMMOTH_UNIT.get(), true, 3.0 * 0.5);
    }

    /**
     * One Wither Skeleton blow is two shoves in one tick (the hurt shove, then its attack knockback 0.5 as a 0.25
     * shove): both land, as they did before.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void one_blow_keeps_its_attack_knockback_part(GameTestHelper helper) {
        blowTest(helper, "witherskel", EntityRegistrar.WITHER_SKELETON_UNIT.get(), false, 0.25);
    }

    // ------------------------------------------------------------------ helpers

    /** A still zombie unit with plenty of health and no knockback resistance. Null (and the test failed) if missing. */
    static Mob victim(GameTestHelper helper, String owner, double x, double z) {
        Mob m = BigUnitGameTests.spawn(helper, EntityRegistrar.ZOMBIE_UNIT.get(), owner, x, z);
        if (m == null) {
            helper.fail("could not create the target");
            return null;
        }
        m.setNoAi(true);
        if (m.getAttribute(Attributes.MAX_HEALTH) != null)
            m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        m.setHealth(1000);
        if (m.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null)
            m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        return m;
    }

    /**
     * Shoves a still target from the east with {tick, strength} pairs (ticks counted from the first, several may share
     * a tick) and checks which landed.
     */
    static void shoveScript(GameTestHelper helper, String tag, double[][] shoves, boolean[] lands) {
        String base = "gametest_knockscript_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob target = victim(helper, base, 4.5, 4.5);
        if (target == null)
            return;
        ShoveLog log = ShoveLog.on(target);
        int[] start = { -1 }, next = { 0 };
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            if (start[0] < 0) {
                if (target.tickCount < ArenaReady.TICKS)
                    return;
                start[0] = log.now();
            }
            int ticks = log.now() - start[0];
            while (next[0] < shoves.length && shoves[next[0]][0] <= ticks) {
                int before = log.landed.size();
                target.knockback(shoves[next[0]][1], 1, 0);
                log.outcome.add(log.landed.size() > before);
                next[0]++;
            }
            if (next[0] < shoves.length)
                return;
            done[0] = true;
            log.close();
            target.discard();
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < shoves.length; i++)
                if (log.outcome.get(i) != lands[i])
                    problems.add("the " + shoves[i][1] + " shove at tick " + (int) shoves[i][0] + " "
                        + (lands[i] ? "was dropped" : "landed"));
            if (!problems.isEmpty())
                throw new GameTestAssertException(String.join("; ", problems) + " (" + log + ")");
            helper.succeed();
        });
    }

    /**
     * One real blow (doHurtTarget) of {@code type} on a still target. With {@code windowOpen} a light shove lands on the
     * target first in the same tick. The blow's shove of {@code expected} strength must land; with no window open, so
     * must its hurt shove, and nothing may be dropped.
     */
    static void blowTest(GameTestHelper helper, String tag, EntityType<? extends Mob> type, boolean windowOpen,
                         double expected) {
        String base = "gametest_knockblow_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob attacker = BigUnitGameTests.spawn(helper, type, base + "_a", 4.5, 4.5);
        if (attacker == null) {
            helper.fail("could not create the attacker " + type);
            return;
        }
        Mob target = victim(helper, base + "_b", 4.5 + attacker.getBbWidth() / 2 + 0.6, 4.5);
        if (target == null) {
            attacker.discard();
            return;
        }
        attacker.setNoAi(true);
        ShoveLog log = ShoveLog.on(target);
        ArenaReady.whenTicking(helper, List.of(attacker, target), 60, t -> {
            try {
                if (windowOpen)
                    target.knockback(HIT, -1, 0);
                int before = log.landed.size(), droppedBefore = log.dropped;
                BlowTimingGameTests.aim(attacker, target);
                if (!attacker.doHurtTarget(target))
                    throw new GameTestAssertException(type + "'s blow did not land");
                List<String> problems = new ArrayList<>();
                // inside an open window the blow's own light hurt shove may be dropped; with none, nothing may be
                if (!windowOpen && log.dropped != droppedBefore)
                    problems.add("the blow's shoves were dropped");
                boolean found = false, hurtShove = false;
                for (int i = before; i < log.landed.size(); i++) {
                    if (Math.abs(log.landed.get(i)[1] - expected) < 1e-3)
                        found = true;
                    if (Math.abs(log.landed.get(i)[1] - HIT) < 1e-3)
                        hurtShove = true;
                }
                if (!found)
                    problems.add("no " + expected + " shove landed");
                if (!windowOpen && !hurtShove)
                    problems.add("the blow's " + HIT + " hurt shove did not land");
                if (!problems.isEmpty())
                    throw new GameTestAssertException(String.join("; ", problems) + " (" + log + ")");
                helper.succeed();
            } finally {
                log.close();
                attacker.discard();
                target.discard();
            }
        });
    }

    /**
     * Every knockback event on one entity, as the last handler sees it: {server tick, strength} for those that landed,
     * a count of those cancelled. Registered at the lowest priority with cancelled events delivered; close() removes it.
     */
    static final class ShoveLog implements Consumer<LivingKnockBackEvent> {
        final Mob victim;
        final List<double[]> landed = new ArrayList<>();
        final List<Boolean> outcome = new ArrayList<>();
        int dropped;
        boolean closed;

        private ShoveLog(Mob victim) {
            this.victim = victim;
        }

        static ShoveLog on(Mob victim) {
            ShoveLog log = new ShoveLog(victim);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, log);
            return log;
        }

        int now() {
            return victim.getServer() == null ? 0 : victim.getServer().getTickCount();
        }

        int all() {
            return landed.size() + dropped;
        }

        @Override
        public void accept(LivingKnockBackEvent evt) {
            if (closed || evt.getEntity() != victim)
                return;
            if (evt.isCanceled())
                dropped++;
            else
                landed.add(new double[] { now(), evt.getStrength() });
        }

        void close() {
            if (!closed) {
                closed = true;
                MinecraftForge.EVENT_BUS.unregister(this);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("landed");
            int t0 = landed.isEmpty() ? 0 : (int) landed.get(0)[0];
            for (double[] s : landed)
                sb.append(' ').append((int) s[0] - t0).append(':').append(String.format("%.2f", s[1]));
            return sb.append(", dropped ").append(dropped).toString();
        }
    }
}
