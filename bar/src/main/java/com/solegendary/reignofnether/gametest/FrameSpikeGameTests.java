package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.perf.FrameSpikes;
import com.solegendary.reignofnether.perf.GfxLog;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The pure rules behind the [Spike] and [Gfx] log lines (perf/FrameSpikes, perf/GfxLog), without rendering: when a
 * frame counts as a spike, the one-a-second rate limit and its skipped count, and the integrated-GPU check.
 * In its own class: check_guards.py stops new tests going into SkirmishGameTests.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FrameSpikeGameTests {

    private static void check(boolean ok, String why) {
        if (!ok)
            throw new GameTestAssertException(why);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void frame_spike_limiter_threshold_and_rate(GameTestHelper helper) {
        FrameSpikes.Limiter l = new FrameSpikes.Limiter();
        check(l.offer(0, 16.6) == FrameSpikes.Limiter.NO_LOG, "a 16.6 ms frame is not a spike");
        check(l.offer(10, 49.9) == FrameSpikes.Limiter.NO_LOG, "49.9 ms is under the threshold");
        check(l.offer(20, 50.0) == 0, "the first 50 ms frame logs, with 0 skipped");
        check(l.offer(300, 120) == FrameSpikes.Limiter.NO_LOG, "a second spike within 1 s is skipped");
        check(l.offer(900, 80) == FrameSpikes.Limiter.NO_LOG, "a third spike within 1 s is skipped");
        check(l.offer(950, 10) == FrameSpikes.Limiter.NO_LOG, "a normal frame never logs and is not counted");
        check(l.offer(1020, 60) == 2, "a spike 1 s after the last line logs and reports the 2 skipped");
        check(l.offer(3000, 70) == 0, "the skipped count starts again after a line");
        helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void gfx_log_flags_integrated_gpus(GameTestHelper helper) {
        check(GfxLog.looksIntegrated("AMD Radeon(TM) 890M Graphics", "ATI Technologies Inc."), "Radeon 890M is integrated");
        check(GfxLog.looksIntegrated("AMD Radeon(TM) 8060S Graphics", "ATI Technologies Inc."), "Radeon(TM) 8 series is integrated");
        check(GfxLog.looksIntegrated("Intel(R) UHD Graphics 630", "Intel"), "Intel UHD is integrated");
        check(GfxLog.looksIntegrated("llvmpipe (LLVM 15.0.7, 256 bits)", "Mesa"), "software rendering is flagged");
        check(!GfxLog.looksIntegrated("NVIDIA GeForce RTX 4070/PCIe/SSE2", "NVIDIA Corporation"), "an RTX card is discrete");
        check(!GfxLog.looksIntegrated("AMD Radeon RX 7800 XT", "ATI Technologies Inc."), "an RX card is discrete");
        check(!GfxLog.looksIntegrated(null, null), "unknown strings are not flagged");
        helper.succeed();
    }
}
