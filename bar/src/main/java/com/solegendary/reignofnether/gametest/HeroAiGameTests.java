package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroBot;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroProgression;
import com.solegendary.reignofnether.hero.PossessionServer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The hero AI for players (lovish, Oct 24 2026: "Id allow for heros to be complex ai's that use their abilities and act
 * extremely smart when there is no hero player this can be a toggle players can set if they dont want this option").
 * A human player's hero that nobody possesses casts by itself through HeroBot; the player's Hero AI switch (default on)
 * turns that off; possessing hands it back to the player at once; bot heroes keep their old behaviour (points too).
 * Written before the code (design/LANES.md, tests first). Each test runs the hero's own once-a-second tick, no hooks.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroAiGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static FactionHeroAbility learn(FactionHero hero, int slot) {
        FactionHeroAbility a = HeroKits.ability(hero, slot);
        a.setRank(hero, 1);
        a.updateStatsForRank(hero);
        return a;
    }

    static void finish(GameTestHelper helper, List<String> problems, List<Mob> mobs, Runnable cleanup) {
        for (Mob m : mobs)
            if (m != null)
                m.discard();
        if (cleanup != null)
            cleanup.run();
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** An unpossessed hero of a human player fights by itself: Chain Lightning at the enemy in range. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void hero_ai_casts_for_an_unpossessed_player_hero(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "ai_cast"), them = HeroGameTests.owner(helper, "ai_cast_enemy");
        HeroBot.setAiEnabled(me, true);
        Mob witch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, me, 3, 3);
        Mob enemy = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 3, 10);
        if (!(witch instanceof FactionHero w) || enemy == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        FactionHeroAbility chain = learn(w, 0);
        helper.runAfterDelay(80, () -> {
            List<String> problems = new ArrayList<>();
            if (chain.isOffCooldown(w))
                problems.add("the unpossessed player hero never cast Chain Lightning at an enemy 7 blocks away");
            finish(helper, problems, List.of(witch, enemy), null);
        });
    }

    /** The player's Hero AI switch off: the same hero in the same fight casts nothing by itself. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void hero_ai_off_means_no_casts(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "ai_off"), them = HeroGameTests.owner(helper, "ai_off_enemy");
        HeroBot.setAiEnabled(me, false);
        Mob witch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, me, 3, 3);
        Mob enemy = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 3, 10);
        if (!(witch instanceof FactionHero w) || enemy == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        FactionHeroAbility chain = learn(w, 0);
        helper.runAfterDelay(80, () -> {
            List<String> problems = new ArrayList<>();
            if (HeroBot.isAiEnabled(me))
                problems.add("the switch did not stay off");
            if (!chain.isOffCooldown(w))
                problems.add("Chain Lightning was cast with the player's Hero AI off");
            finish(helper, problems, List.of(witch, enemy), () -> HeroBot.setAiEnabled(me, true));
        });
    }

    /** Possessing the hero stops the AI at once; letting go hands the hero back to it. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void hero_ai_stops_while_possessed_and_resumes_after(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "ai_possess"), them = HeroGameTests.owner(helper, "ai_possess_enemy");
        UUID uuid = UUID.nameUUIDFromBytes(me.getBytes());
        HeroBot.setAiEnabled(me, true);
        Mob witch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, me, 3, 3);
        Mob enemy = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 3, 10);
        if (!(witch instanceof FactionHero w) || enemy == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        FactionHeroAbility chain = learn(w, 0);
        List<String> problems = new ArrayList<>();
        Runnable cleanup = () -> PossessionServer.release(uuid, null);
        String why = PossessionServer.claim(helper.getLevel(), me, uuid, witch.getId(), true);
        if (why != null) {
            problems.add("the owner could not possess their hero: " + why);
            finish(helper, problems, List.of(witch, enemy), cleanup);
            return;
        }
        helper.runAfterDelay(60, () -> {
            if (!chain.isOffCooldown(w))
                problems.add("the AI cast Chain Lightning while the player possessed the hero");
            PossessionServer.release(uuid, null);
            helper.runAfterDelay(60, () -> {
                if (chain.isOffCooldown(w))
                    problems.add("the AI did not take the hero back after the player let go");
                finish(helper, problems, List.of(witch, enemy), cleanup);
            });
        });
    }

    /**
     * Bot heroes as before: a bot's hero casts and spends its points, whatever a player of the same name chose. A human
     * player's points stay theirs to spend (the AI never ranks abilities for a player).
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void hero_ai_bot_heroes_unchanged_and_player_points_stay_manual(GameTestHelper helper) {
        String bot = HeroGameTests.owner(helper, "ai_bot"), me = HeroGameTests.owner(helper, "ai_human"),
            them = HeroGameTests.owner(helper, "ai_bot_enemy");
        BotPlayer brain = new BotPlayer(bot, Factions.STORMCALLERS, BotPlayer.Difficulty.MEDIUM, helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3)));
        brain.enemyFilter = o -> false;
        BotServerEvents.brains.put(bot, brain);
        HeroBot.setAiEnabled(bot, false);   // a player's switch never reaches a bot
        HeroBot.setAiEnabled(me, true);
        Mob botWitch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, bot, 3, 3);
        Mob myWitch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, me, 12, 3);
        Mob enemy = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 3, 10);
        if (!(botWitch instanceof FactionHero b) || !(myWitch instanceof FactionHero h) || enemy == null) {
            BotServerEvents.brains.remove(bot);
            helper.fail("could not spawn the test units");
            return;
        }
        FactionHeroAbility chain = learn(b, 0);
        h.addExperience(HeroProgression.xpForLevel(3));
        h.setSkillPoints(2);
        int ranksBefore = 0;
        for (FactionHeroAbility a : h.heroCore().abilities)
            ranksBefore += a.getRank(h);
        b.setSkillPoints(1);
        final int before = ranksBefore;
        helper.runAfterDelay(80, () -> {
            List<String> problems = new ArrayList<>();
            if (chain.isOffCooldown(b))
                problems.add("the bot's hero did not cast Chain Lightning");
            if (b.getSkillPoints() != 0)
                problems.add("the bot's hero did not spend its point");
            int after = 0;
            for (FactionHeroAbility a : h.heroCore().abilities)
                after += a.getRank(h);
            if (after != before || h.getSkillPoints() != 2)
                problems.add("the AI spent a human player's points (" + before + " -> " + after + " ranks)");
            finish(helper, problems, List.of(botWitch, myWitch, enemy), () -> {
                BotServerEvents.brains.remove(bot);
                HeroBot.setAiEnabled(bot, true);
            });
        });
    }

    /** Focus fire: with two enemies in reach, the shot goes to the one it can finish, not merely the nearest. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void hero_ai_focuses_the_target_it_can_finish(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "ai_focus"), them = HeroGameTests.owner(helper, "ai_focus_enemy");
        HeroBot.setAiEnabled(me, true);
        Mob corsair = HeroGameTests.spawnHero(helper, HeroKits.CORSAIR_CAPTAIN, me, 3, 3);
        Mob near = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 3, 8);
        Mob weak = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 9, 10);
        if (!(corsair instanceof FactionHero c) || near == null || weak == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        FactionHeroAbility pistol = learn(c, 0);
        weak.setHealth(4);
        float nearHp = near.getHealth();
        List<String> problems = new ArrayList<>();
        boolean[] done = { false };
        // watch tick by tick: the first shot is what counts, before either side's plain attacks blur it
        for (int t = 1; t <= 80; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                if (!pistol.isOffCooldown(c)) {
                    done[0] = true;
                    if (weak.isAlive() && weak.getHealth() >= 4)
                        problems.add("Pistol Shot was not spent on the enemy it could finish (near one " + nearHp + " -> " + near.getHealth() + ")");
                    finish(helper, problems, List.of(corsair, near, weak), null);
                } else if (tick == 80) {
                    done[0] = true;
                    problems.add("Pistol Shot was never cast");
                    finish(helper, problems, List.of(corsair, near, weak), null);
                }
            });
        }
    }

    /**
     * Saving the ultimate: Aegis of Dawn (the paladin's 75 s fourth) is not burnt on one stray enemy at full health; it
     * goes off when the fight is real (a crowd around the hero).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void hero_ai_saves_its_ultimate_for_a_real_fight(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "ai_ult"), them = HeroGameTests.owner(helper, "ai_ult_enemy");
        HeroBot.setAiEnabled(me, true);
        Mob paladin = HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, me, 8, 8);
        Mob first = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 8, 12);
        if (!(paladin instanceof FactionHero p) || first == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        FactionHeroAbility aegis = learn(p, 3);
        List<Mob> mobs = new ArrayList<>(List.of(paladin, first));
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(60, () -> {
            if (!aegis.isOffCooldown(p))
                problems.add("Aegis of Dawn was burnt on a single enemy at full health");
            for (int i = 0; i < 4; i++)
                mobs.add(HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 5 + i * 2, 4));
            helper.runAfterDelay(80, () -> {
                if (aegis.isOffCooldown(p))
                    problems.add("Aegis of Dawn was not cast with five enemies on the hero");
                finish(helper, problems, mobs, null);
            });
        });
    }
}
