package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.guiscreen.PauseFlow;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** guiscreen/PauseFlow: Esc opens the match menu from the RTS screen only, so Esc inside the menu closes it. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PauseFlowGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void esc_in_the_match_menu_closes_it(GameTestHelper helper) {
        if (!PauseFlow.escOpensMenu(true))
            helper.fail("Esc on the RTS screen must open the match menu");
        else if (PauseFlow.escOpensMenu(false))
            helper.fail("Esc on any other screen (the match menu itself) must not re-arm the menu");
        else
            helper.succeed();
    }
}
