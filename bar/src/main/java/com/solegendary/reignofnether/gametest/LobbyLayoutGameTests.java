package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.matchstart.LobbyLayout;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The lobby screen's layout rules (matchstart/LobbyLayout), headless: the panels scroll inside their bounds, a widget
 * is shown only when it fits whole in its panel (so the settings column can never sit under the pinned Hide button
 * again - Joisco's lobby, Oct 20), and names and faction labels are cut to their slot with "...". Pure arithmetic with
 * a fixed-width stand-in for the font, so it needs no client.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class LobbyLayoutGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** 6 px per character, like most of Minecraft's font. */
    static final ToIntFunction<String> MONO = s -> s.length() * 6;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void lobby_panels_scroll_within_bounds_and_hide_partial_widgets(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (LobbyLayout.clampScroll(40, 100, 200) != 0)
            problems.add("content that fits must not scroll");
        if (LobbyLayout.clampScroll(-20, 300, 200) != 0)
            problems.add("scroll must not go above the top");
        if (LobbyLayout.clampScroll(500, 300, 200) != 100)
            problems.add("scroll must stop at content - view (100), got " + LobbyLayout.clampScroll(500, 300, 200));
        if (LobbyLayout.clampScroll(60, 300, 200) != 60)
            problems.add("a scroll inside the range must be kept");
        // the column ends 26 px above the panel bottom, where Hide sits: a button reaching into it must be hidden
        int viewTop = 28, viewBottom = 292;
        if (!LobbyLayout.inView(28, 18, viewTop, viewBottom) || !LobbyLayout.inView(274, 18, viewTop, viewBottom))
            problems.add("buttons wholly inside the panel must show");
        if (LobbyLayout.inView(276, 18, viewTop, viewBottom))
            problems.add("a button that runs past the panel bottom (into Hide) must be hidden");
        if (LobbyLayout.inView(20, 18, viewTop, viewBottom))
            problems.add("a button scrolled half above the panel must be hidden");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void lobby_names_and_factions_are_cut_to_their_slot(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (!LobbyLayout.fit("Death", 60, MONO).equals("Death"))
            problems.add("a label that fits must stay whole");
        String f = LobbyLayout.fit("Brassworks Artificers", 60, MONO);
        if (MONO.applyAsInt(f) > 60 || !f.endsWith("..."))
            problems.add("a long faction must fit 60 px and end with ...: '" + f + "'");
        if (!LobbyLayout.fit("Brassworks", 12, MONO).isEmpty())
            problems.add("when not even ... fits, draw nothing");
        String n = LobbyLayout.fitName("AVERYLONGNAME12", " (you)", 90, MONO);
        if (MONO.applyAsInt(n) > 90 || !n.endsWith("... (you)"))
            problems.add("a long name keeps its ' (you)' and is cut first: '" + n + "'");
        if (!LobbyLayout.fitName("Sam", "", 90, MONO).equals("Sam"))
            problems.add("a short name must stay whole");
        String tight = LobbyLayout.fitName("AVERYLONGNAME12", " (you)", 40, MONO);
        if (MONO.applyAsInt(tight) > 40)
            problems.add("in a very narrow slot the whole name must still fit: '" + tight + "'");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
