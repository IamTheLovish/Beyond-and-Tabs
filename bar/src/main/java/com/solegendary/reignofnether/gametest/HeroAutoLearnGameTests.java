package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroBot;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroProgression;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.hero.HeroServerboundPacket;
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
 * spends a player's points. Each point left unspent for 20 s is now spent the way a bot's hero spends it (HeroBot
 * .spendPoints), one point at a time; the clock waits while the owner's rank-up menu is open (a hold that lapses unless
 * the client refreshes it, and ends when the owner logs out), and never runs with "Auto-learn hero abilities" off; the
 * player's own choice inside the 20 s always wins. The first test was written before the code (tests first).
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

    /** A player for {@code name} as the server sees one: the sender of the hero packets in the menu tests. */
    static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper, String name) {
        return net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
    }

    static void finish(GameTestHelper helper, List<String> problems, Mob hero, String owner) {
        hero.discard();
        HeroBot.setAutoLearn(owner, true);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** A Storm Witch for {@code tag}'s owner, or null (and the test failed). */
    static Mob witch(GameTestHelper helper, String owner) {
        Mob witch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, owner, 3, 3);
        if (!(witch instanceof FactionHero)) {
            helper.fail("could not spawn the test hero");
            return null;
        }
        return witch;
    }

    /** "points P, ranks R" as one fact, for the failure lines. */
    static String state(FactionHero w) {
        return "points " + w.getSkillPoints() + ", ranks " + ranks(w);
    }

    /**
     * Each point waits its own 20 s: the spawn point and a second point that comes 10 s later (a boon) are spent one at
     * a time - the first by 22 s with the second still waiting, the second by 32 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 760)
    public static void hero_auto_learn_gives_each_point_its_own_20_s(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "autolearn_each");
        Mob witch = witch(helper, me);
        if (!(witch instanceof FactionHero w))
            return;
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(200, () -> w.addExperience(HeroProgression.xpForLevel(2)));   // level 2: a second point
        helper.runAfterDelay(440, () -> {
            if (ranks(w) != 1 || w.getSkillPoints() != 1)
                problems.add("at 22 s the spawn point should be spent and the boon's still waiting (" + state(w) + ")");
        });
        helper.runAfterDelay(680, () -> {
            if (ranks(w) != 2 || w.getSkillPoints() != 0)
                problems.add("at 34 s both points should be spent (" + state(w) + ")");
            finish(helper, problems, witch, me);
        });
    }

    /** The player's setting off: the point stays theirs at 23 s. */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hero_auto_learn_off_keeps_the_point_for_the_player(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "autolearn_off");
        HeroBot.setAutoLearn(me, false);
        Mob witch = witch(helper, me);
        if (!(witch instanceof FactionHero w)) {
            HeroBot.setAutoLearn(me, true);
            return;
        }
        helper.runAfterDelay(460, () -> {
            List<String> problems = new ArrayList<>();
            if (ranks(w) != 0 || w.getSkillPoints() != 1)
                problems.add("auto-learn spent the point with the player's setting off (" + state(w) + ")");
            finish(helper, problems, witch, me);
        });
    }

    /**
     * The owner's rank-up menu open, as the client keeps it (RANK_MENU_OPEN through the packet's server side, again
     * every 5 s): nothing is spent at 23 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hero_auto_learn_waits_while_the_rank_menu_is_open(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "autolearn_menu");
        Mob witch = witch(helper, me);
        if (!(witch instanceof FactionHero w))
            return;
        var owner = player(helper, me);
        for (int t = 2; t < 460; t += 100)
            helper.runAfterDelay(t, () -> HeroServerboundPacket.onRankMenu(owner, witch.getId(), true));
        helper.runAfterDelay(460, () -> {
            List<String> problems = new ArrayList<>();
            if (ranks(w) != 0 || w.getSkillPoints() != 1)
                problems.add("auto-learn spent the point while the owner's rank-up menu was open (" + state(w) + ")");
            finish(helper, problems, witch, me);
        });
    }

    /**
     * A menu hold the client never refreshes or closes (a lost packet, a crash) lapses after 15 s, and the point's 20 s
     * then run: still unspent at 15 s, spent by 40 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void hero_auto_learn_menu_hold_lapses_without_a_refresh(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "autolearn_lapse");
        Mob witch = witch(helper, me);
        if (!(witch instanceof FactionHero w))
            return;
        var owner = player(helper, me);
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(2, () -> HeroServerboundPacket.onRankMenu(owner, witch.getId(), true));
        helper.runAfterDelay(300, () -> {
            if (ranks(w) != 0)
                problems.add("the point was spent while the menu hold still stood (" + state(w) + " at 15 s)");
        });
        helper.runAfterDelay(800, () -> {
            if (ranks(w) != 1 || w.getSkillPoints() != 0)
                problems.add("the menu hold never lapsed: the point was still unspent at 40 s (" + state(w) + ")");
            finish(helper, problems, witch, me);
        });
    }

    /**
     * The owner opens the menu and logs out (HeroServerEvents.onPlayerLogout, as the server calls it): the hold ends at
     * once, so the point is spent 20 s later - by 25 s, long before the 15 s hold plus 20 s would allow.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hero_auto_learn_logout_ends_the_menu_hold(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "autolearn_logout");
        Mob witch = witch(helper, me);
        if (!(witch instanceof FactionHero w))
            return;
        var owner = player(helper, me);
        helper.runAfterDelay(2, () -> HeroServerboundPacket.onRankMenu(owner, witch.getId(), true));
        helper.runAfterDelay(40, () -> HeroServerEvents.onPlayerLogout(
            new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(owner)));
        helper.runAfterDelay(500, () -> {
            List<String> problems = new ArrayList<>();
            if (ranks(w) != 1 || w.getSkillPoints() != 0)
                problems.add("after the owner logged out the menu hold stood on: the point was unspent at 25 s (" + state(w) + ")");
            finish(helper, problems, witch, me);
        });
    }

    /** Only the owner's menu holds auto-learn back: another player's RANK_MENU_OPEN on this hero changes nothing. */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hero_auto_learn_ignores_another_players_menu(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "autolearn_ally");
        Mob witch = witch(helper, me);
        if (!(witch instanceof FactionHero w))
            return;
        var other = player(helper, me + "_ally");
        for (int t = 2; t < 460; t += 100)
            helper.runAfterDelay(t, () -> HeroServerboundPacket.onRankMenu(other, witch.getId(), true));
        helper.runAfterDelay(460, () -> {
            List<String> problems = new ArrayList<>();
            if (ranks(w) != 1 || w.getSkillPoints() != 0)
                problems.add("another player's open menu held back the owner's auto-learn (" + state(w) + " at 23 s)");
            finish(helper, problems, witch, me);
        });
    }
}
