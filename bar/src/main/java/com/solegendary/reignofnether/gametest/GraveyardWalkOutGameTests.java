package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.solegendary.reignofnether.gametest.GraveyardExitGameTests.ARENA;
import static com.solegendary.reignofnether.gametest.GraveyardExitGameTests.BATCH;
import static com.solegendary.reignofnether.gametest.GraveyardExitGameTests.TIMEOUT;

/**
 * The Graveyard's units walking out on open ground, at each level: a group of five Zombies raised in the yard must all
 * be out of the footprint and at the rally point within GraveyardExitGameTests.LIMIT ticks (the run is
 * GraveyardExitGameTests.run, the same as the blocked-gate cases').
 *
 * These stall now and then on main's move goal, with none of PR #177's code: 4 failures in 28 runner-runs (stress
 * 40824369634, 40824379303, 40824403225), the group standing in the yard with its order, or one unit slow on its way
 * round the wall. They sit in their own PR (the group stall) so that PR #177's latency and blocked-gate work is not
 * gated on them.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class GraveyardWalkOutGameTests {

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l1_units_walk_out_to_the_rally(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 0, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void overflowing_graveyard_units_walk_out_to_the_rally(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 1, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l2_units_walk_out_to_the_rally(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 2, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l3_units_walk_out_to_the_rally(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 3, GraveyardExitGameTests.Gate.OPEN); }
}
