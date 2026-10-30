package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroBot;
import com.solegendary.reignofnether.hero.HeroKits;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-learn (design-draft-heroes-better-2026-10-30 #1; lovish, Oct 30: "Auto after 20 s"): a player's hero walked out
 * with every ability greyed out and stayed a plain unit until the player found the rank-up menu - the Hero AI never
 * spends a player's points. A point left unspent for 20 s is now spent the way a bot's hero spends it (HeroBot
 * .spendPoints), unless the rank-up menu is open or the player turned "Auto-learn hero abilities" off; the player's
 * own choice inside the 20 s always wins. Written before the code (tests first).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroAutoLearnGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static int ranks(FactionHero hero) {
        int n = 0;
        for (FactionHeroAbility a : hero.heroCore().abilities)
            n += a.getRank(hero);
        return n;
    }

    /**
     * A fresh player hero, nobody touching it: its first point is still unspent at 15 s (the player's window) and spent
     * by 23 s, on the first ability (the lowest-ranked learnable, as a bot's hero picks).
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hero_auto_learn_spends_a_point_left_unspent_for_20_s(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "autolearn");
        Mob witch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, me, 3, 3);
        if (!(witch instanceof FactionHero w)) {
            helper.fail("could not spawn the test hero");
            return;
        }
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(300, () -> {
            if (ranks(w) != 0 || w.getSkillPoints() != 1)
                problems.add("the point was spent before the player's 20 s were up (ranks " + ranks(w)
                    + ", points " + w.getSkillPoints() + " at 15 s)");
            helper.runAfterDelay(160, () -> {
                if (w.getSkillPoints() != 0)
                    problems.add("the point was still unspent at 23 s (" + w.getSkillPoints() + " points)");
                if (HeroKits.ability(w, 0).getRank(w) != 1)
                    problems.add("the first ability was not learnt at 23 s (ranks "
                        + HeroKits.ability(w, 0).getRank(w) + "/" + HeroKits.ability(w, 1).getRank(w) + "/"
                        + HeroKits.ability(w, 2).getRank(w) + "/" + HeroKits.ability(w, 3).getRank(w) + ")");
                witch.discard();
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            });
        });
    }

    /**
     * Guard, either way of holding auto-learn back for 23 s: the player's setting off, or the rank-up menu open (as the
     * server hears it from the client, HeroServerboundPacket RANK_MENU_OPEN). Neither spends the point.
     */
    static void heldBack(GameTestHelper helper, String tag, boolean settingOff) {
        String me = HeroGameTests.owner(helper, tag);
        if (settingOff)
            HeroBot.setAutoLearn(me, false);
        Mob witch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, me, 3, 3);
        if (!(witch instanceof FactionHero w)) {
            HeroBot.setAutoLearn(me, true);
            helper.fail("could not spawn the test hero");
            return;
        }
        if (!settingOff)
            w.heroCore().rankUpMenuOpen = true;
        helper.runAfterDelay(460, () -> {
            List<String> problems = new ArrayList<>();
            if (ranks(w) != 0 || w.getSkillPoints() != 1)
                problems.add("auto-learn spent the point " + (settingOff ? "with the player's setting off" : "while the rank-up menu was open")
                    + " (ranks " + ranks(w) + ", points " + w.getSkillPoints() + ")");
            witch.discard();
            HeroBot.setAutoLearn(me, true);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hero_auto_learn_off_keeps_the_point_for_the_player(GameTestHelper helper) {
        heldBack(helper, "autolearn_off", true);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hero_auto_learn_waits_while_the_rank_menu_is_open(GameTestHelper helper) {
        heldBack(helper, "autolearn_menu", false);
    }
}
