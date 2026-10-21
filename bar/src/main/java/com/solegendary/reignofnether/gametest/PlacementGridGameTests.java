package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.PlacementGrid;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * building/PlacementGrid (#38 item 10, lovish's "rune lattice" + "patch glow"): the dots stay within RADIUS and fade
 * out by it, the rune circle clears the footprint's corners, and the glyph count stays balanced.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlacementGridGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void placement_grid_is_quiet_bounded_and_clears_the_footprint(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (PlacementGrid.dotAlpha(0) != PlacementGrid.DOT_ALPHA)
            problems.add("the dot under the cursor must be at full alpha");
        if (PlacementGrid.dotAlpha(PlacementGrid.RADIUS) != 0f || PlacementGrid.dotAlpha(PlacementGrid.RADIUS + 5) != 0f)
            problems.add("dots must vanish at and beyond RADIUS");
        if (!(PlacementGrid.dotAlpha(3) > PlacementGrid.dotAlpha(7)))
            problems.add("dots must fade with distance");
        if (PlacementGrid.DOT_ALPHA > 0.6f)
            problems.add("the grid must stay quiet at RTS zoom (alpha " + PlacementGrid.DOT_ALPHA + ")");
        List<int[]> cells = PlacementGrid.cells();
        boolean hasCursor = false;
        for (int[] c : cells) {
            if (c[0] * c[0] + c[1] * c[1] >= PlacementGrid.RADIUS * PlacementGrid.RADIUS)
                problems.add("a cell outside RADIUS: " + c[0] + "," + c[1]);
            if (c[0] == 0 && c[1] == 0)
                hasCursor = true;
        }
        if (!hasCursor)
            problems.add("the cursor's own cell must have a dot");
        for (int[] size : new int[][]{ {5, 5}, {3, 3}, {11, 7}, {1, 1} }) {
            double r = PlacementGrid.runeRadius(size[0], size[1]);
            double corner = Math.sqrt(size[0] * size[0] + size[1] * size[1]) / 2.0;
            if (r <= corner)
                problems.add("the rune circle must clear the corners of a " + size[0] + "x" + size[1] + " footprint");
            double inner = r - PlacementGrid.runeGap(r);
            if (inner <= 0 || inner >= r)
                problems.add("the inner ring must sit inside the outer one for " + size[0] + "x" + size[1]);
            int n = PlacementGrid.glyphCount(r);
            if (n % 4 != 0 || n < 8 || n > 32)
                problems.add("glyph count " + n + " must be a multiple of 4 in 8..32");
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
