package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.ButtonLabelFit;
import com.solegendary.reignofnether.matchstart.CycleStep;
import com.solegendary.reignofnether.matchstart.LobbyLayout;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The one button theme (tester, Oct 26), headless in the game: themed labels fit inside their frame and are centred,
 * cycling buttons step both ways with wrap, and the skirmish settings column stays clear of Start Battle at the
 * smallest screen heights. Pure arithmetic (hud/ButtonLabelFit, matchstart/CycleStep, LobbyLayout), no client.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButtonThemeGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void themed_labels_fit_centred_and_cycles_wrap(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (int bw : new int[]{16, 24, 54, 98, 200}) {
            float s = ButtonLabelFit.fitScale(150f, 13f, bw, 20);
            float w = 150f * s;
            if (w > ButtonLabelFit.innerWidth(bw) + 1e-3f)
                problems.add("label overflows a " + bw + " wide button");
            float left = ButtonLabelFit.left(10, bw, w);
            if (Math.abs((left - 10) - (10 + bw - left - w)) > 1f)
                problems.add("label off-centre in a " + bw + " wide button");
        }
        if (CycleStep.step(0, 3, true) != 2 || CycleStep.step(2, 3, false) != 0)
            problems.add("cycle must wrap both ways");
        for (int avail = 112; avail <= 260; avail++) {
            int[] c = LobbyLayout.settingsColumn(avail, 7);
            if (c[0] + 7 * c[1] > avail) {
                problems.add("settings rows run under Start Battle at " + avail);
                break;
            }
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
