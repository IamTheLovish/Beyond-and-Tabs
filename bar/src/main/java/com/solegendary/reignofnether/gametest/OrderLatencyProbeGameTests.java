package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * DIAGNOSTIC, not for main: the vanilla-pathing move test of {@link OrderLatencyGameTests} (units first moved 4 and 5
 * ticks after the order in 1 of 26 runner-runs on PR 177's head cbc8a7b7, limit 2), repeated so one run catches it. The
 * copies carry the failure-only per-tick trace of the unit's pathing state. Eight run side by side in the default batch
 * (as the original does, next to other tests), eight each in a batch of their own (alone on the server).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class OrderLatencyProbeGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    private static void vanilla(GameTestHelper helper) {
        OrderLatencyGameTests.run(helper, "move (vanilla)", false, EntityRegistrar.VINDICATOR_UNIT.get(), 1, 7, UnitAction.MOVE, 13);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void latency_probe_together_01(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void latency_probe_together_02(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void latency_probe_together_03(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void latency_probe_together_04(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void latency_probe_together_05(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void latency_probe_together_06(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void latency_probe_together_07(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void latency_probe_together_08(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, batch = "latency_probe_alone_01", timeoutTicks = 600)
    public static void latency_probe_alone_01(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, batch = "latency_probe_alone_02", timeoutTicks = 600)
    public static void latency_probe_alone_02(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, batch = "latency_probe_alone_03", timeoutTicks = 600)
    public static void latency_probe_alone_03(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, batch = "latency_probe_alone_04", timeoutTicks = 600)
    public static void latency_probe_alone_04(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, batch = "latency_probe_alone_05", timeoutTicks = 600)
    public static void latency_probe_alone_05(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, batch = "latency_probe_alone_06", timeoutTicks = 600)
    public static void latency_probe_alone_06(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, batch = "latency_probe_alone_07", timeoutTicks = 600)
    public static void latency_probe_alone_07(GameTestHelper helper) {
        vanilla(helper);
    }

    @GameTest(template = ARENA, batch = "latency_probe_alone_08", timeoutTicks = 600)
    public static void latency_probe_alone_08(GameTestHelper helper) {
        vanilla(helper);
    }
}
