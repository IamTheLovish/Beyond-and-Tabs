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

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void esc_on_the_rts_screen_cancels_before_anything_else(GameTestHelper helper) {
        PauseFlow.EscAction a;
        if ((a = PauseFlow.onRtsEsc(true, true, true, false)) != PauseFlow.EscAction.DROP_BUILDING)
            helper.fail("the building cursor goes first, got " + a);
        else if ((a = PauseFlow.onRtsEsc(false, true, true, false)) != PauseFlow.EscAction.CANCEL_ORDER)
            helper.fail("then an armed order, got " + a);
        else if ((a = PauseFlow.onRtsEsc(false, false, true, false)) != PauseFlow.EscAction.DESELECT)
            helper.fail("then the selection, got " + a);
        else if ((a = PauseFlow.onRtsEsc(false, false, false, false)) != PauseFlow.EscAction.NONE)
            helper.fail("with nothing to cancel Esc must not open the menu, got " + a);
        else if ((a = PauseFlow.onRtsEsc(false, false, true, true)) != PauseFlow.EscAction.OPEN_MENU)
            helper.fail("Shift+Esc opens the menu, got " + a);
        else
            helper.succeed();
    }
}
