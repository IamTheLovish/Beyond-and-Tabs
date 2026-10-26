package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.matchstart.MatchEndLayout;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** matchstart/MatchEndScreen: the compact party-total line never runs into the units total. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MatchEndLayoutGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void match_end_compact_total_falls_back_before_overlapping(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // "Party total" 60 px, "Resources harvested: 301,360" 150 px, "Harvested 301,360" 90 px
        if (MatchEndLayout.compactTotalForm(60, 150, 90, 220) != 0)
            problems.add("full text fits in 220 px");
        if (MatchEndLayout.compactTotalForm(60, 150, 90, 170) != 1)
            problems.add("170 px: the short text after the label, not the full one (60+8+150 > 170)");
        if (MatchEndLayout.compactTotalForm(60, 150, 90, 120) != 2)
            problems.add("120 px: the short text alone");
        // the case in Joisco's screenshot: the full text ran ~40 px into the units column
        int room = 140;
        int form = MatchEndLayout.compactTotalForm(60, 150, 90, room);
        int used = form == 0 ? 60 + 8 + 150 : form == 1 ? 60 + 8 + 90 : Math.min(90, room);
        if (used > room)
            problems.add("chosen form " + form + " uses " + used + " px of " + room);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // Joisco 2026-10-25: a one-player party showed a "Party total" row with the same numbers as the player's row
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void match_end_party_of_one_has_no_party_total(GameTestHelper helper) {
        if (MatchEndLayout.showsPartyTotal(1))
            helper.fail("a party of one still gets a Party total row");
        else if (!MatchEndLayout.showsPartyTotal(2))
            helper.fail("a party of two lost its Party total row");
        else
            helper.succeed();
    }
}
