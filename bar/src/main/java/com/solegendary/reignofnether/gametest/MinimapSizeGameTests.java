package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.minimap.MinimapSize;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** The minimap's three sizes: the M cycle, the expanded square on screen at every GUI scale, the old config flag. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinimapSizeGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void size_cycles_small_large_expanded_and_back(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (MinimapSize.next(0) != 1 || MinimapSize.next(1) != 2 || MinimapSize.next(2) != 0)
            problems.add("M should cycle 0 -> 1 -> 2 -> 0");
        if (MinimapSize.restoreLevel(2) != 1 || MinimapSize.restoreLevel(0) != 0)
            problems.add("an expanded map restores as large, small stays small");
        finish(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void expanded_square_fits_the_screen_at_gui_scale_2_3_4(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int physicalW = 1920, physicalH = 1080;
        for (int scale = 2; scale <= 4; scale++) {
            int w = physicalW / scale, h = physicalH / scale;
            int[] r = MinimapSize.expandedRect(w, h);
            if (r[0] < 0 || r[1] < 0 || r[0] + r[2] > w || r[1] + r[2] > h)
                problems.add("scale " + scale + ": square " + r[2] + " at " + r[0] + "," + r[1] + " leaves a " + w + "x" + h + " screen");
            if (r[2] != (int) (Math.min(w, h) * 0.85))
                problems.add("scale " + scale + ": side should be 85% of the short side");
            if (Math.abs((r[0] * 2 + r[2]) - w) > 1 || Math.abs((r[1] * 2 + r[2]) - h) > 1)
                problems.add("scale " + scale + ": square not centred");
            if (!MinimapSize.inside(r, w / 2.0, h / 2.0) || MinimapSize.inside(r, 0, 0))
                problems.add("scale " + scale + ": inside() wrong");
        }
        finish(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void old_large_flag_maps_to_large(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (MinimapSize.fromConfig(0, true) != 1)
            problems.add("hud_minimap_large=true with no saved size should be 1");
        if (MinimapSize.fromConfig(0, false) != 0)
            problems.add("both unset should be 0");
        if (MinimapSize.fromConfig(2, false) != 2 || MinimapSize.fromConfig(1, false) != 1)
            problems.add("a saved size wins");
        finish(helper, problems);
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
