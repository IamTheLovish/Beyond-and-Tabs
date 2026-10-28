package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.AreaMex;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** building/AreaMex: the circle picks only free patches inside it, and two builders share them without overlap. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AreaMexGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void area_mex_queues_each_free_patch_in_the_circle_once(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BlockPos a = new BlockPos(-6, 64, 0), b = new BlockPos(6, 64, 0), c = new BlockPos(0, 64, 8),
            taken = new BlockPos(0, 64, -5), outside = new BlockPos(30, 64, 0);
        BlockPos cursor = new BlockPos(0, 64, 0);
        List<BlockPos> free = AreaMex.freeInside(List.of(a, b, c, taken, outside), Set.of(taken)::contains, cursor, 12);
        if (free.size() != 3 || free.contains(taken) || free.contains(outside))
            problems.add("only the three free in-circle patches should count, got " + free);
        Map<Integer, List<BlockPos>> plan = AreaMex.split(free,
            List.of(new AreaMex.Builder(1, -20, 0), new AreaMex.Builder(2, 20, 0)), cursor);
        List<BlockPos> all = new ArrayList<>();
        plan.values().forEach(all::addAll);
        if (all.size() != 3 || all.stream().distinct().count() != 3)
            problems.add("every patch must be queued exactly once, got " + plan);
        if (!plan.get(1).contains(a) || !plan.get(2).contains(b))
            problems.add("each builder should take the patch beside it first, got " + plan);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
