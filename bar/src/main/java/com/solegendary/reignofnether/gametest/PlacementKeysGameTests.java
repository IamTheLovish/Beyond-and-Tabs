package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.PlacementKeys;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Placement key rules (building/PlacementKeys): Shift+Alt keeps the ghost for a grid, Alt alone still drops it. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlacementKeysGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void shift_alt_keeps_the_ghost_for_a_grid(GameTestHelper helper) {
        if (PlacementKeys.altCancelsPlacement(true))
            helper.fail("Alt with Shift held is the grid modifier: it must not drop the building being placed");
        else if (!PlacementKeys.altCancelsPlacement(false))
            helper.fail("Alt alone should still drop the building being placed");
        else
            helper.succeed();
    }
}
