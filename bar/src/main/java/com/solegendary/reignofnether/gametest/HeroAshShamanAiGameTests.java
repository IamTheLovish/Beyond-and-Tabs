package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroBot;
import com.solegendary.reignofnether.hero.HeroKits;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ash Shaman's AI (design-draft-heroes-better-2026-10-30 #5): all four of its specs were Ai.NONE, and HeroBot
 * .isEscape took any ground-aimed NONE ability for "the way out". So in a fight the AI skipped Firewall, Lava Geyser and
 * Ember Totem, a hurt one held them back as its escape, and under 30% it threw one at a spot away from the enemies. Its spells now strike at
 * the enemy, and only abilities marked as escapes are escapes. Written before the code (tests first).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroAshShamanAiGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * Three enemies five blocks off and all three spells learnt: in 10 s the AI casts each of them (one a second, as
     * its fight brain decides; review r1: "any one" let a Shaman that only ever cast Firewall pass).
     */
    @GameTest(template = ARENA, batch = "hero_ai_ash_shaman_fight", timeoutTicks = 260)
    public static void hero_ai_ash_shaman_casts_in_a_fight(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "shaman_fight"), them = me + "_foe";
        HeroBot.setAiEnabled(me, true);
        Mob shaman = HeroGameTests.spawnHero(helper, HeroKits.ASH_SHAMAN, me, 5, 3);
        List<Mob> mobs = HeroAiV2GameTests.mobs(shaman,
            HeroAiV2GameTests.dummy(helper, them, 3, 8),
            HeroAiV2GameTests.dummy(helper, them, 5, 8),
            HeroAiV2GameTests.dummy(helper, them, 7, 8));
        if (!(shaman instanceof FactionHero s) || mobs.contains(null)) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        List<FactionHeroAbility> spells = List.of(HeroAiGameTests.learn(s, 0), HeroAiGameTests.learn(s, 1),
            HeroAiGameTests.learn(s, 2));
        helper.runAfterDelay(200, () -> {
            List<String> problems = new ArrayList<>();
            List<String> notCast = new ArrayList<>();
            for (FactionHeroAbility a : spells)
                if (a.isOffCooldown(s))
                    notCast.add(a.spec.id);
            if (notCast.size() == spells.size())
                problems.add("in 10 s with three enemies five blocks off, the Ash Shaman's AI cast none of Firewall, Lava Geyser, Ember Totem");
            else if (!notCast.isEmpty())
                problems.add("in 10 s with three enemies five blocks off, the Ash Shaman's AI never cast " + notCast);
            HeroAiGameTests.finish(helper, problems, mobs, null);
        });
    }

    /**
     * A hurt Shaman (45% health: above the flee line, below the 60% where the AI keeps its escape's mana back; legs held,
     * so the only thing it can do is cast) with Firewall learnt and three enemies seven blocks off, in its reach: it
     * burns them. Main takes Firewall for the Shaman's escape, so a hurt Shaman holds it back - and the time it does cast
     * it (under 30%) it throws it at a spot away from the enemies.
     */
    @GameTest(template = ARENA, batch = "hero_ai_ash_shaman_escape", timeoutTicks = 200)
    public static void hero_ai_ash_shaman_hurt_still_burns_the_enemy_in_reach(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "shaman_escape"), them = me + "_foe";
        HeroBot.setAiEnabled(me, true);
        Mob shaman = HeroGameTests.spawnHero(helper, HeroKits.ASH_SHAMAN, me, 7, 3);
        Mob a = HeroAiV2GameTests.dummy(helper, them, 5, 10);
        Mob b = HeroAiV2GameTests.dummy(helper, them, 7, 10);
        Mob c = HeroAiV2GameTests.dummy(helper, them, 9, 10);
        List<Mob> mobs = HeroAiV2GameTests.mobs(shaman, a, b, c);
        if (!(shaman instanceof FactionHero s) || a == null || b == null || c == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        List<Mob> foes = List.of(a, b, c);
        for (Mob m : foes)
            m.setInvulnerable(false);   // the fire must be able to land (they still stand on Hold fire)
        AttributeInstance speed = shaman.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
            speed.setBaseValue(0);
        FactionHeroAbility firewall = HeroAiGameTests.learn(s, 0);
        float[] hp = new float[3];
        helper.runAfterDelay(5, () -> {   // past the first tick, when the start level's stats are applied
            shaman.setHealth(shaman.getMaxHealth() * 0.45f);
            for (int i = 0; i < 3; i++)
                hp[i] = foes.get(i).getHealth();
        });
        helper.runAfterDelay(160, () -> {
            List<String> problems = new ArrayList<>();
            boolean hurt = false;
            for (int i = 0; i < 3; i++)
                hurt |= foes.get(i).getHealth() < hp[i];
            if (firewall.isOffCooldown(s))
                problems.add("the hurt Ash Shaman (45% health) never cast Firewall at the enemies seven blocks off: it keeps it as an escape");
            else if (!hurt)
                problems.add("the hurt Ash Shaman cast Firewall and no enemy burned: it went away from them, as an escape");
            HeroAiGameTests.finish(helper, problems, mobs, null);
        });
    }
}
