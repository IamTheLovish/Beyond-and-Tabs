package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.ClickDeselect;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A plain left click on empty ground lets go of the selection (playtest Oct 20 item 16), and nothing else does:
 * a click on a unit or building, on the HUD, with an order or a building ghost armed, with a modifier, or a drag.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClickDeselectGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void only_a_plain_click_on_empty_ground_deselects(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // hasSelection, overHud, orderArmed, placingBuilding, unitUnderCursor, buildingUnderCursor, shift, ctrl, alt
        if (!ClickDeselect.pressMayDeselect(true, false, false, false, false, false, false, false, false))
            problems.add("a plain click on empty ground should deselect");
        String[] names = { "nothing selected", "over the HUD", "an order armed", "a building ghost out",
            "a unit under the cursor", "a building under the cursor", "Shift", "Ctrl", "Alt" };
        for (int i = 0; i < names.length; i++) {
            boolean[] f = { true, false, false, false, false, false, false, false, false };
            f[i] = !f[i];
            if (ClickDeselect.pressMayDeselect(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8]))
                problems.add("a click with " + names[i] + " should keep the selection");
        }
        if (!ClickDeselect.releaseDeselects(true, 100, 100, 102, 101))
            problems.add("a release a pixel or two from the press is still a click");
        if (ClickDeselect.releaseDeselects(true, 100, 100, 130, 100))
            problems.add("a drag is a box selection, not a deselecting click");
        if (ClickDeselect.releaseDeselects(false, 100, 100, 100, 100))
            problems.add("a release whose press could not deselect must not deselect");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
