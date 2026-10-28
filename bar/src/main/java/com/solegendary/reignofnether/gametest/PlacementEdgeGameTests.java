package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.ZoomLimit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Placement at the screen edge: the zoom limit stays inside the loaded radius, and far ground reads as not loaded. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlacementEdgeGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void zoom_limit_does_not_pass_the_loaded_radius(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float floor = 90, hard = 200;
        if (ZoomLimit.max(100, floor, hard) != 100f)
            problems.add("a fit height of 100 should be the limit, not 1.5x");
        if (ZoomLimit.max(500, floor, hard) != hard)
            problems.add("never above the hard limit");
        if (ZoomLimit.max(10, floor, hard) != floor)
            problems.add("never below the floor");
        if (ZoomLimit.oldMax(100, floor, hard) != 150f)
            problems.add("the old rule gave 150 for a fit height of 100");
        for (double fit = 0; fit <= 400; fit += 10)
            if (ZoomLimit.max(fit, floor, hard) > Math.max(fit, floor))
                problems.add("limit passes the fit height at " + fit);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void ground_far_outside_the_loaded_chunks_is_not_loaded(GameTestHelper helper) {
        BlockPos far = helper.absolutePos(new BlockPos(0, 2, 0)).offset(100000, 0, 100000);
        if (helper.getLevel().hasChunkAt(far))
            helper.fail("a block 100000 away should not be in a loaded chunk");
        else if (!helper.getLevel().hasChunkAt(helper.absolutePos(new BlockPos(1, 2, 1))))
            helper.fail("the arena itself should be loaded");
        else
            helper.succeed();
    }
}
