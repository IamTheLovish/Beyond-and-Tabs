package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
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
