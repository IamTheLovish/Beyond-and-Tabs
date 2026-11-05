package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.perf.ServerTickProfile;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.perf.TickGates;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Oct 26 perf plan: vanilla's share of the tick is timed ([Sys] "vanilla" group), and the TickGates skip the work
 * of bodies with nothing to do. These tests guard the behaviour the gates must keep (a broken floor still drops a
 * unit, a knocked-back unit moves, a hit unit fights, an animal near a unit thinks) and count the moves they save; no
 * test asserts a time. The 300-unit window logs a [SysBench] line, the before/after reading (BAT_TICK_GATES=false
 * for before).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfTickGatesGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    private static VindicatorUnit unit(GameTestHelper helper, String owner, double x, double y, double z, List<Entity> made) {
        VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
        if (v == null)
            return null;
        v.moveTo(x, y, z, 0, 0);
        v.setOwnerName(owner);
        helper.getLevel().addFreshEntity(v);
        made.add(v);
        return v;
    }

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_gates_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static void cleanUp(List<Entity> made) {
        for (Entity e : made) {
            UnitServerEvents.clearQueuedActions(e.getId());
            e.discard();
        }
    }

    /**
     * 300 idle units on one pad, measured for 100 ticks: the [Sys] sections, ours and vanilla's, must account for at
     * least 90% of the mean tick, so "elsewhere" no longer hides the cost. Its own batch: no other test's work in the
     * window.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "perf_bench300")
    public static void a_300_unit_window_accounts_for_the_tick(GameTestHelper helper) {
        BlockPos pad = ArenaReady.pad(helper, 190);
        String owner = owner(helper, "bench");
        List<Entity> made = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            if (unit(helper, owner, pad.getX() + 0.5 + (i % 18) * 0.88, pad.getY(), pad.getZ() + 0.5 + (i / 18) * 0.88, made) == null) {
                cleanUp(made);
                helper.fail("could not create the units");
                return;
            }
        }
        Object hold = new Object();
        helper.runAfterDelay(40, () -> {
            Sys.force(hold, true);
            Sys.snapshot();   // a fresh window from here
        });
        helper.runAfterDelay(140, () -> {
            String line = Sys.snapshot();
            double covered = Sys.lastCovered;
            Sys.force(hold, false);
            cleanUp(made);
            ReignOfNether.LOGGER.info("[SysBench] 300 idle units, gates {}: {}", TickGates.ENABLED ? "on" : "off", line);
            if (line == null)
                helper.fail("no [Sys] line came back");
            else if (covered < 0.90)
                helper.fail(String.format(Locale.ROOT, "the timed sections cover only %.0f%% of the tick: %s", covered * 100, line));
            else
                helper.succeed();
        });
    }

    /**
     * A GameTest's measuring window (Sys.force, then Sys.snapshot) counts every tick it was open. The periodic [Sys]
     * report (every ServerTickProfile.LOG_EVERY_TICKS ticks) restarted the shared window, so a window that crossed it
     * counted only its tail: a_300_unit_window_accounts_for_the_tick read "[Sys] 48 ticks" of its 100 and failed "the
     * timed sections cover only 87% of the tick" (PR 488, run 41624764514), and "no [Sys] line came back" when the
     * report fell on its last tick (PR 409, run 41599940498); PerfPushGameTests had worked round it with lifetime
     * counters. Held for longer than the report's interval, the window must still count every tick. Its own batch: no
     * other test's snapshot in the window.
     */
    @GameTest(template = ARENA, timeoutTicks = ServerTickProfile.LOG_EVERY_TICKS + 120, batch = "perf_sys_window")
    public static void a_held_sys_window_counts_every_tick_across_the_periodic_report(GameTestHelper helper) {
        Object hold = new Object();
        TestCleanup.atEnd(helper, () -> Sys.force(hold, false));
        int span = ServerTickProfile.LOG_EVERY_TICKS + 20;
        long[] from = { -1 };
        helper.runAfterDelay(10, () -> {
            Sys.force(hold, true);
            Sys.snapshot();   // a fresh window from here
            from[0] = helper.getTick();
        });
        helper.runAfterDelay(10 + span, () -> {
            int ticks = Sys.windowTicks();
            long open = helper.getTick() - from[0];
            String line = Sys.snapshot();
            Sys.force(hold, false);
            ReignOfNether.LOGGER.info("[SysWindow] GameTest: window open {} ticks, measured {}: {}", open, ticks, line);
            if (ticks < open - 2)
                helper.fail("the held [Sys] window counted " + ticks + " of the " + open + " ticks it was open (the periodic report"
                    + " every " + ServerTickProfile.LOG_EVERY_TICKS + " ticks restarted it): " + line);
            else
                helper.succeed();
        });
    }

    static final long SPIKE_BEFORE_MS = 2000, SPIKE_INSIDE_MS = 500;
    static final int TICK_MS_WINDOW = 20;

    /**
     * A [Sys] window's mean tick is the time of ITS OWN ticks. Sys was handed MinecraftServer.tickTimes[tick - 1], the
     * previous tick's time, at each tick's end and added it to the current tick's window: the mean counted the tick before
     * the window opened and missed its last tick, so a slow tick just before a held window (a GC, the batch setting up)
     * landed in "elsewhere" and dragged the coverage down (a_300_unit_window_accounts_for_the_tick: "[Sys] 100 ticks ...
     * cover only 80%", flake-stress 41810956897). An untimed stall in the tick before the window must not count; one
     * inside it must. Its own batch: the stalls hold up nothing else.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "perf_sys_tick_ms")
    public static void a_sys_window_counts_its_own_ticks_time_not_the_tick_before_it(GameTestHelper helper) {
        Object hold = new Object();
        TestCleanup.atEnd(helper, () -> Sys.force(hold, false));
        helper.runAfterDelay(39, () -> spin(SPIKE_BEFORE_MS));   // the tick before the window
        helper.runAfterDelay(40, () -> {
            Sys.force(hold, true);
            Sys.snapshot();   // a fresh window from here
        });
        helper.runAfterDelay(50, () -> spin(SPIKE_INSIDE_MS));   // a tick inside it
        helper.runAfterDelay(40 + TICK_MS_WINDOW, () -> {
            String line = Sys.snapshot();
            Sys.force(hold, false);
            ReignOfNether.LOGGER.info("[SysWindow] GameTest tick ms: stall {} ms before the window, {} ms inside it: {}",
                SPIKE_BEFORE_MS, SPIKE_INSIDE_MS, line);
            java.util.regex.Matcher m = line == null ? null
                : java.util.regex.Pattern.compile("\\[Sys\\] (\\d+) ticks, mean ([0-9.]+) ms/tick").matcher(line);
            if (m == null || !m.find()) {
                helper.fail("no [Sys] line came back: " + line);
                return;
            }
            int ticks = Integer.parseInt(m.group(1));
            double mean = Double.parseDouble(m.group(2));
            double inside = SPIKE_INSIDE_MS / (double) TICK_MS_WINDOW;
            double before = SPIKE_BEFORE_MS / (double) TICK_MS_WINDOW;
            if (ticks != TICK_MS_WINDOW)
                helper.fail("the window counted " + ticks + " of its " + TICK_MS_WINDOW + " ticks (cut, not the case under test): " + line);
            else if (mean >= inside + before / 2)
                helper.fail(String.format(Locale.ROOT, "the window's mean tick %.1f ms counted the %d ms stall in the tick before it opened"
                    + " (its own ticks: about %.0f ms plus idle): %s", mean, SPIKE_BEFORE_MS, inside, line));
            else if (mean < inside * 0.8)
                helper.fail(String.format(Locale.ROOT, "the window's mean tick %.1f ms missed the %d ms stall inside it: %s", mean,
                    SPIKE_INSIDE_MS, line));
            else
                helper.succeed();
        });
    }

    static final int RELEASED_HOLD = 30, RELEASED_WATCH = 10, RELEASED_TRIES = 6;

    /**
     * A hold that ends without a snapshot leaves the window to the periodic report, and the ticks after it - not
     * measured - must not book their time into it. Review round 3 on #490 (af6b658c): Sys kept owing "the last tick's
     * time" after measuring stopped, so every later tick added its time to the window until the report and the live
     * [Sys] line's mean was diluted by up to ~180 unmeasured ticks (ours and vanilla's share inflated, elsewhere near 0).
     * The rule, whatever the runner's speed: a window never holds more tick times than ticks it measured
     * (Sys.windowMsTicks() <= Sys.windowTicks()), checked on each tick after the hold while measuring is off. A busy
     * runner keeps measuring on by itself (the slow-server rule, tick p95 >= 25 ms: the first red of this test,
     * 42015339793, measured 110-150 ticks), which is not the case under test: the hold is tried again, and the test says
     * so if measuring never went off. Its own batch: no other holder.
     */
    @GameTest(template = ARENA, timeoutTicks = RELEASED_TRIES * (RELEASED_HOLD + RELEASED_WATCH + 5) + 60, batch = "perf_sys_released")
    public static void a_released_sys_window_does_not_count_the_ticks_after_its_hold(GameTestHelper helper) {
        Object hold = new Object();
        TestCleanup.atEnd(helper, () -> Sys.force(hold, false));
        int[] phase = { 0 }, tries = { 0 }, offTicks = { 0 };
        long[] at = { -1 };
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (phase[0] == 0) {   // hold the timer for RELEASED_HOLD ticks
                Sys.force(hold, true);
                Sys.snapshot();   // a fresh window from here
                at[0] = tick;
                phase[0] = 1;
            } else if (phase[0] == 1) {
                if (tick < at[0] + RELEASED_HOLD)
                    return;
                Sys.force(hold, false);   // released without a snapshot
                at[0] = tick;
                offTicks[0] = 0;
                phase[0] = 2;
            } else if (phase[0] == 2) {
                int ticks = Sys.windowTicks(), msTicks = Sys.windowMsTicks();
                if (!Sys.isMeasuring()) {
                    offTicks[0]++;
                    if (msTicks > ticks) {
                        phase[0] = 3;
                        helper.fail("the released [Sys] window holds the time of " + msTicks + " ticks but measured " + ticks
                            + ": " + offTicks[0] + " unmeasured tick(s) after the hold booked their time into it");
                        return;
                    }
                }
                if (tick < at[0] + RELEASED_WATCH)
                    return;
                if (offTicks[0] >= 2) {
                    phase[0] = 3;
                    ReignOfNether.LOGGER.info("[SysWindow] GameTest released: {} unmeasured ticks watched after a {}-tick hold, window"
                        + " {} ticks / {} tick times", offTicks[0], RELEASED_HOLD, ticks, msTicks);
                    helper.succeed();
                } else if (++tries[0] < RELEASED_TRIES) {
                    phase[0] = 0;   // measuring stayed on (a slow runner): not the case under test, hold again
                } else {
                    phase[0] = 3;
                    helper.fail("measuring never went off after a hold in " + RELEASED_TRIES + " tries (the server is slow: tick p95"
                        + " >= 25 ms), so the case under test never happened");
                }
            }
        });
    }

    /** Busy for the given milliseconds, outside every timed section (the GameTest's own tick work). */
    private static void spin(long ms) {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < end)
            Thread.onSpinWait();
    }

    /** 40 idle units call Entity.move at most 160 times in 40 ticks: one full travel in 10 each (1600 without F1). */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void idle_units_rarely_move(GameTestHelper helper) {
        BlockPos pad = ArenaReady.pad(helper, 170);
        String owner = owner(helper, "idle");
        List<Entity> made = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            if (unit(helper, owner, pad.getX() + 1.5 + (i % 8) * 1.9, pad.getY(), pad.getZ() + 1.5 + (i / 8) * 1.9, made) == null) {
                cleanUp(made);
                helper.fail("could not create the units");
                return;
            }
        }
        helper.runAfterDelay(40, () -> TickGates.watchMoves(made));
        helper.runAfterDelay(80, () -> {
            int moves = TickGates.unwatchMoves(made);
            cleanUp(made);
            if (moves > 160)
                helper.fail("40 idle units moved " + moves + " times in 40 ticks (at most 160)");
            else
                helper.succeed();
        });
    }

    /** A resting unit whose floor is broken falls within 10 ticks (the full travel every 10th tick finds the hole). */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void a_resting_unit_falls_when_its_floor_breaks(GameTestHelper helper) {
        BlockPos pad = ArenaReady.pad(helper, 160);
        List<Entity> made = new ArrayList<>();
        VindicatorUnit v = unit(helper, owner(helper, "fall"), pad.getX() + 8.5, pad.getY(), pad.getZ() + 8.5, made);
        if (v == null) {
            helper.fail("could not create the unit");
            return;
        }
        double[] y0 = { 0 };
        helper.runAfterDelay(43, () -> {   // not a multiple of 10: the break lands between two full travels
            y0[0] = v.getY();
            BlockPos under = BlockPos.containing(v.getX(), v.getY() - 0.5, v.getZ());
            helper.getLevel().setBlock(under, Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(43 + 10, () -> {
            double fell = y0[0] - v.getY();
            cleanUp(made);
            if (fell < 0.05)
                helper.fail("the unit did not start to fall within 10 ticks of its floor breaking (fell " + fell + ")");
            else
                helper.succeed();
        });
    }

    /** A resting unit that is knocked back still slides. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void a_knocked_back_resting_unit_still_moves(GameTestHelper helper) {
        BlockPos pad = ArenaReady.pad(helper, 150);
        List<Entity> made = new ArrayList<>();
        VindicatorUnit v = unit(helper, owner(helper, "knock"), pad.getX() + 4.5, pad.getY(), pad.getZ() + 8.5, made);
        if (v == null) {
            helper.fail("could not create the unit");
            return;
        }
        double[] x0 = { 0 };
        helper.runAfterDelay(41, () -> {
            x0[0] = v.getX();
            v.knockback(1.0, -1, 0);   // a push towards +x
        });
        helper.runAfterDelay(41 + 10, () -> {
            double moved = v.getX() - x0[0];
            cleanUp(made);
            if (moved < 0.3)
                helper.fail("the knocked-back unit moved only " + moved + " blocks in 10 ticks");
            else
                helper.succeed();
        });
    }

    /** An idle unit (thinking every 4th tick) that is hit is fighting back within 10 ticks. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void an_idle_unit_that_is_hit_fights_within_10_ticks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = ArenaReady.pad(helper, 140);
        List<Entity> made = new ArrayList<>();
        VindicatorUnit v = unit(helper, owner(helper, "hit"), pad.getX() + 6.5, pad.getY(), pad.getZ() + 8.5, made);
        Cow cow = EntityType.COW.create(level);
        if (v == null || cow == null) {
            cleanUp(made);
            helper.fail("could not create the mobs");
            return;
        }
        cow.moveTo(pad.getX() + 9.5, pad.getY(), pad.getZ() + 8.5, 0, 0);
        cow.setNoAi(true);
        level.addFreshEntity(cow);
        made.add(cow);
        int[] hitAt = { -1 };
        helper.runAfterDelay(41, () -> {
            v.hurt(level.damageSources().mobAttack(cow), 1.0f);
            hitAt[0] = v.tickCount;
        });
        helper.runAfterDelay(41 + 10, () -> {
            boolean fighting = v.getTargetGoal().getTarget() == cow || v.getTarget() == cow;
            String why = "target goal " + v.getTargetGoal().getTarget() + ", mob target " + v.getTarget();
            cleanUp(made);
            if (!fighting)
                helper.fail("the idle unit was not fighting its attacker 10 ticks after the hit (" + why + ")");
            else
                helper.succeed();
        });
    }

    /** A cow 5 blocks from a unit still thinks: its navigation walks it to a point 4 blocks away. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void a_cow_near_a_unit_still_moves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = ArenaReady.pad(helper, 130);
        List<Entity> made = new ArrayList<>();
        VindicatorUnit v = unit(helper, owner(helper, "cow"), pad.getX() + 3.5, pad.getY(), pad.getZ() + 3.5, made);
        Cow cow = EntityType.COW.create(level);
        if (v == null || cow == null) {
            cleanUp(made);
            helper.fail("could not create the mobs");
            return;
        }
        cow.moveTo(pad.getX() + 8.5, pad.getY(), pad.getZ() + 3.5, 0, 0);
        level.addFreshEntity(cow);
        made.add(cow);
        double[] x0 = { 0 };
        helper.runAfterDelay(45, () -> {   // past an awake-area rebuild (once a second)
            x0[0] = cow.getX();
            cow.getNavigation().moveTo(cow.getX() + 4, cow.getY(), cow.getZ() + 8, 1.0);
        });
        helper.runAfterDelay(45 + 60, () -> {
            double moved = Math.abs(cow.getX() - x0[0]) + Math.abs(cow.getZ() - (pad.getZ() + 3.5));
            cleanUp(made);
            if (moved < 1.0)
                helper.fail("the cow 5 blocks from a unit did not move in 60 ticks (moved " + moved + ")");
            else
                helper.succeed();
        });
    }
}
