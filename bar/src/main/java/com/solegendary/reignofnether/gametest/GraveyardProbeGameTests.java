package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.solegendary.reignofnether.gametest.GraveyardExitGameTests.ARENA;
import static com.solegendary.reignofnether.gametest.GraveyardExitGameTests.TIMEOUT;

/**
 * DIAGNOSTIC, not for main: the graveyard walk-out tests (open gate, and a building across half the gate) repeated,
 * three batches of eight, so that one stress run catches the stall a few times with the goal's path counters
 * (pathStats) in the failure line. Each batch has the mix the original batch has (the four levels of both cases).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class GraveyardProbeGameTests {

    @GameTest(template = ARENA, batch = "graveyard_probe_1", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_1_l1_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 0, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_1", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_1_overflowing_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 1, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_1", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_1_l2_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 2, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_1", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_1_l3_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 3, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_1", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_1_l1_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 0, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_1", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_1_overflowing_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 1, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_1", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_1_l2_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 2, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_1", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_1_l3_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 3, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_2", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_2_l1_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 0, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_2", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_2_overflowing_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 1, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_2", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_2_l2_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 2, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_2", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_2_l3_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 3, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_2", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_2_l1_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 0, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_2", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_2_overflowing_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 1, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_2", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_2_l2_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 2, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_2", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_2_l3_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 3, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_3", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_3_l1_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 0, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_3", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_3_overflowing_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 1, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_3", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_3_l2_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 2, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_3", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_3_l3_open(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 3, GraveyardExitGameTests.Gate.OPEN); }

    @GameTest(template = ARENA, batch = "graveyard_probe_3", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_3_l1_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 0, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_3", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_3_overflowing_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 1, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_3", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_3_l2_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 2, GraveyardExitGameTests.Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = "graveyard_probe_3", timeoutTicks = TIMEOUT)
    public static void graveyard_probe_3_l3_half(GameTestHelper helper) { GraveyardExitGameTests.run(helper, 3, GraveyardExitGameTests.Gate.HALF_BLOCKED); }
}
