package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.matchstart.MatchEndLayout;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** matchstart/MatchEndScreen layout rules. Own batch: pure checks with no world, so they never change defaultBatch's line-up. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MatchEndLayoutGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20, batch = "match_end_layout")
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
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20, batch = "match_end_layout")
    public static void match_end_party_of_one_has_no_party_total(GameTestHelper helper) {
        if (MatchEndLayout.showsPartyTotal(1))
            helper.fail("a party of one still gets a Party total row");
        else if (!MatchEndLayout.showsPartyTotal(2))
            helper.fail("a party of two lost its Party total row");
        else
            helper.succeed();
    }

    // lovish 2026-10-27: "game gets closed immediately for devs they cant see the graphs to see the differences
    // between players" - the host's Back to lobby reopened the lobby on every client, and that cleared every
    // non-host's results and closed their graphs popup at once (LobbyClientEvents.onPacket). The rule is looked up
    // by name so this test compiles on the code before it: there it is missing, and the popup always closed.
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20, batch = "match_end_layout")
    public static void match_end_results_stay_up_when_the_host_goes_back_to_lobby(GameTestHelper helper) {
        java.lang.reflect.Method rule;
        try {
            rule = MatchEndLayout.class.getMethod("lobbyKeepsResults", boolean.class, boolean.class);
        } catch (NoSuchMethodException e) {
            helper.fail("no MatchEndLayout.lobbyKeepsResults(popupOpen, popupPending): a lobby reopening over a decided"
                + " match clears every client's results and closes the graphs popup at once");
            return;
        }
        List<String> problems = new ArrayList<>();
        try {
            if (!(boolean) rule.invoke(null, true, false))
                problems.add("the graphs popup on screen is closed by the host's Back to lobby");
            if (!(boolean) rule.invoke(null, false, true))
                problems.add("results waiting to open (title still up, or the player in a menu) are thrown away");
            if ((boolean) rule.invoke(null, false, false))
                problems.add("results nobody is looking at (popup closed) are kept into the next match");
        } catch (ReflectiveOperationException e) {
            problems.add("lobbyKeepsResults threw " + e);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
