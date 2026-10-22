package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.ChatDedupe;
import com.solegendary.reignofnether.unit.AnnounceGate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The announcement spam rules (playtest clip: "has fielded an experimental: Sun Colossus!" 2-3 times stacked, "Boon
 * received" repeating): the server-side once-per-window gate and the client-side collapse of identical lines. Pure
 * rules with a fake clock - no world changes.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AnnouncementFeedGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void second_colossus_does_not_repeat_the_announcement(GameTestHelper helper) {
        AnnounceGate gate = new AnnounceGate();
        long w = 60_000;
        if (!gate.allow("lovish|Sun Colossus", 1000, w))
            helper.fail("the first announcement must go through");
        else if (gate.allow("lovish|Sun Colossus", 5000, w))
            helper.fail("a second Colossus of the same player 4 s later must stay quiet");
        else if (!gate.allow("lovish|Iron Leviathan Engine", 5000, w))
            helper.fail("a different unit type is its own announcement");
        else if (!gate.allow("bot|Sun Colossus", 5000, w))
            helper.fail("another player's Colossus is its own announcement");
        else if (!gate.allow("lovish|Sun Colossus", 1000 + w, w))
            helper.fail("after the window the same unit type may be announced again");
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void identical_chat_lines_collapse_within_ten_seconds(GameTestHelper helper) {
        ChatDedupe d = new ChatDedupe();
        String line = "lovish has fielded an experimental: Sun Colossus!";
        if (d.seen(line, 0) != 1)
            helper.fail("a fresh line is shown (count 1)");
        else if (d.seen(line, 3000) != 2 || d.seen(line, 9000) != 3)
            helper.fail("repeats inside 10 s count up, so the caller drops them");
        else if (d.seen("Boon received (level 3)", 9000) != 1)
            helper.fail("a different line is not collapsed into it");
        else if (d.seen(line, 10_000) != 1)
            helper.fail("10 s after the first, the line is news again");
        else if (!ChatDedupe.isAnnouncement("server.reignofnether.t3_arrived") || !ChatDedupe.isAnnouncement("hud.hero.reignofnether.boon")
                || ChatDedupe.isAnnouncement("chat.type.text"))
            helper.fail("only the mod's own announcement keys are collapsed, never player chat");
        else
            helper.succeed();
    }
}
