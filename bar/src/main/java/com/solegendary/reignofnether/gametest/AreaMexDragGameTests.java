package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.AreaMexCircle;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** building/AreaMexCircle: BAR's area mex - a dragged circle picks the free patches inside, nearest the builders first. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AreaMexDragGameTests {

    private static void verdict(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void area_mex_circle_takes_the_free_patches_inside(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BlockPos centre = new BlockPos(100, 64, 100);
        BlockPos inA = new BlockPos(105, 64, 100), inB = new BlockPos(100, 64, 92), onEdge = new BlockPos(110, 64, 100);
        BlockPos outside = new BlockPos(111, 64, 100), far = new BlockPos(150, 64, 150), taken = new BlockPos(102, 64, 102);
        List<BlockPos> all = List.of(inA, outside, inB, far, onEdge, taken);
        List<BlockPos> got = AreaMexCircle.patchesIn(all, taken::equals, centre, 10);
        if (!got.equals(List.of(inA, inB, onEdge)))
            problems.add("inside the radius-10 circle: " + got + " (the edge counts, taken and outside do not)");
        if (!AreaMexCircle.patchesIn(all, p -> false, centre, 0).isEmpty())
            problems.add("a zero-radius circle holds no patch");
        // height does not matter, only the ground plan
        if (AreaMexCircle.patchesIn(List.of(new BlockPos(103, 90, 100)), p -> false, centre, 5).size() != 1)
            problems.add("the circle is flat: a patch on a hill still counts");
        // duplicates in the synced list are one patch
        if (AreaMexCircle.patchesIn(List.of(inA, inA), p -> false, centre, 10).size() != 1)
            problems.add("a patch listed twice is one patch");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void area_mex_orders_nearest_first_from_the_builders(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BlockPos a = new BlockPos(0, 64, 0), b = new BlockPos(20, 64, 0), c = new BlockPos(40, 64, 0);
        List<BlockPos> scrambled = List.of(c, a, b);
        if (!AreaMexCircle.nearestFirst(scrambled, -5, 0).equals(List.of(a, b, c)))
            problems.add("builders west of the patches: should walk a, b, c");
        if (!AreaMexCircle.nearestFirst(scrambled, 50, 0).equals(List.of(c, b, a)))
            problems.add("builders east of the patches: should walk c, b, a");
        if (!AreaMexCircle.nearestFirst(scrambled, 19, 3).equals(List.of(b, a, c)))
            problems.add("builders by the middle patch: b first, then the nearer of the others");
        // equal distances break by x then z, so the order never flickers frame to frame
        BlockPos p = new BlockPos(5, 64, 0), q = new BlockPos(-5, 64, 0), r = new BlockPos(0, 64, 5);
        List<BlockPos> tie = AreaMexCircle.nearestFirst(List.of(p, r, q), 0, 0);
        if (!tie.equals(List.of(q, r, p)))
            problems.add("ties should order by x then z: " + tie);
        // the input list is not changed
        List<BlockPos> input = new ArrayList<>(scrambled);
        AreaMexCircle.nearestFirst(input, 0, 0);
        if (!input.equals(scrambled))
            problems.add("nearestFirst must not reorder its input");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void area_mex_drag_is_a_circle_and_a_short_press_is_a_click(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BlockPos centre = new BlockPos(0, 64, 0);
        if (Math.abs(AreaMexCircle.radius(centre, new BlockPos(3, 70, 4)) - 5.0) > 1e-9)
            problems.add("radius is the flat distance (3-4-5)");
        if (AreaMexCircle.radius(centre, new BlockPos(500, 64, 0)) != AreaMexCircle.MAX_RADIUS)
            problems.add("the circle is capped at MAX_RADIUS");
        if (!AreaMexCircle.isClick(0, 0) || !AreaMexCircle.isClick(3, 3))
            problems.add("a press and release a few pixels apart is a click");
        if (AreaMexCircle.isClick(6, 0) || AreaMexCircle.isClick(0, -9))
            problems.add("a drag of several pixels is not a click");
        // the end to end rule: a click picks nothing by itself (radius 0), a drag picks what it encloses
        Set<BlockPos> patches = Set.of(new BlockPos(4, 64, 0));
        if (!AreaMexCircle.patchesIn(patches, p -> false, centre, AreaMexCircle.radius(centre, centre)).isEmpty())
            problems.add("a zero drag encloses nothing");
        if (AreaMexCircle.patchesIn(patches, p -> false, centre, AreaMexCircle.radius(centre, new BlockPos(0, 64, 6))).size() != 1)
            problems.add("a radius-6 drag encloses the patch 4 away");
        verdict(helper, problems);
    }

    /** The drag replaced the old Shift + wheel ring: no wheel radius is left, and the hints name the drag. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void area_mex_hints_describe_the_drag_not_a_wheel_ring(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        try (var in = AreaMexDragGameTests.class.getResourceAsStream("/assets/reignofnether/lang/en_us.json")) {
            String lang = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String key : new String[]{ "hud.reignofnether.patch_hint", "hud.reignofnether.area_mex_circle",
                    "onboarding.reignofnether.controls.area_mex" }) {
                int at = lang.indexOf("\"" + key + "\"");
                if (at < 0) {
                    problems.add(key + " is missing");
                    continue;
                }
                String line = lang.substring(at, lang.indexOf('\n', at)).toLowerCase();
                if (line.contains("wheel") || line.contains("scroll"))
                    problems.add(key + " still talks about the wheel: " + line);
                if (!line.contains("drag") && !line.contains("circle"))
                    problems.add(key + " does not name the drag circle");
            }
        } catch (Exception e) {
            problems.add("could not read the lang file: " + e);
        }
        // the circle radius comes only from the drag distance (no wheel state): same drag, same circle
        BlockPos c = new BlockPos(0, 64, 0), d = new BlockPos(6, 64, 8);
        if (AreaMexCircle.radius(c, d) != 10.0)
            problems.add("a 6-8-10 drag gives radius 10");
        verdict(helper, problems);
    }
}
