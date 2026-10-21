package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.ExtractorSnap;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** building/ExtractorSnap: the extractor ghost snaps to the nearest FREE patch in range, else stays on the cursor. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ExtractorSnapGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void extractor_ghost_snaps_to_the_nearest_free_patch(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BlockPos near = new BlockPos(5, 64, 0), far = new BlockPos(10, 64, 0), outOfRange = new BlockPos(40, 64, 0);
        List<BlockPos> patches = List.of(far, near, outOfRange);
        BlockPos cursor = new BlockPos(0, 64, 0);
        BlockPos got = ExtractorSnap.nearestFree(patches, c -> false, cursor, ExtractorSnap.RANGE);
        if (!near.equals(got))
            problems.add("should snap to the nearest patch " + near + ", got " + got);
        Set<BlockPos> taken = Set.of(near);
        got = ExtractorSnap.nearestFree(patches, taken::contains, cursor, ExtractorSnap.RANGE);
        if (!far.equals(got))
            problems.add("a taken patch must be skipped for the next free one " + far + ", got " + got);
        got = ExtractorSnap.nearestFree(List.of(outOfRange), c -> false, cursor, ExtractorSnap.RANGE);
        if (got != null)
            problems.add("a patch beyond RANGE must not pull the ghost, got " + got);
        if (ExtractorSnap.nearestFree(patches, c -> false, null, ExtractorSnap.RANGE) != null)
            problems.add("no cursor, no snap");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
