package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.onboarding.FactionInfoCard;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** onboarding/FactionInfoCard: a card taller than the screen widens into the free room, then shrinks; never runs off. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FactionCardFitGameTests {

    private static void verdict(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // a stand-in for wrapped text: 6000 px of text in 10 px lines, plus a 40 px header
    private static final IntUnaryOperator TEXT = w -> 40 + 10 * (int) Math.ceil(6000.0 / w);

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void faction_card_widens_until_it_fits_the_screen(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // 196 wide = 350 tall; a 300 px screen needs it wider
        int w = FactionInfoCard.fitWidth(TEXT, 196, 340, 296);
        if (TEXT.applyAsInt(w) > 296)
            problems.add("width " + w + " still " + TEXT.applyAsInt(w) + " tall in 296");
        if (w > 196 && TEXT.applyAsInt(w - FactionInfoCard.WIDEN_STEP) <= 296)
            problems.add("width " + w + " is wider than needed");
        if (FactionInfoCard.fitWidth(TEXT, 196, 340, 1000) != 196)
            problems.add("a card that fits keeps its width");
        if (FactionInfoCard.fitWidth(TEXT, 196, 200, 100) > 200)
            problems.add("never wider than the room beside the list");
        verdict(helper, problems);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void faction_card_shrinks_only_when_widening_is_not_enough(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (FactionInfoCard.fitScale(200, 300) != 1f)
            problems.add("a card that fits is drawn at full size");
        float s = FactionInfoCard.fitScale(600, 300);
        if (Math.round(600 * s) > 300)
            problems.add("a 600 px card scaled by " + s + " still overflows 300 px");
        if (FactionInfoCard.fitScale(0, 300) != 1f || FactionInfoCard.fitScale(500, 0) <= 0f)
            problems.add("degenerate sizes give a usable scale");
        verdict(helper, problems);
    }
}
