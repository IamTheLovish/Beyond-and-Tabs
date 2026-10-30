package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Kill XP paid in full (design-draft-heroes-better-2026-10-30 #8; lovish, Oct 30: "same pace, fix + show XP"). A kill
 * near a hero is worth (pop + 1) x 5 points, sent as orbs of 2 and a last orb of 1; a player's orb pays half its value
 * (int), so that last orb paid nothing - an even-pop kill lost 5 XP every time. And the XP rode on the orbs: a hero that
 * fell before they reached it never got it. The kill now pays at the kill, exactly; the orbs are only the look.
 * Written before the code (tests first).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroXpGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** XP a hero should get for killing {@code victim} (a player's unit): (pop + 1) x 5 points at 5 XP a point. */
    static int expectedXp(Mob victim) {
        return (((Unit) victim).getCost().population + 1) * 25;
    }

    /** A Spider (2 population, an even pop: the old split lost its last point) killed beside a still hero. */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void hero_xp_pays_exactly_for_an_even_pop_kill(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "xp_exact"), them = HeroGameTests.owner(helper, "xp_exact_victim");
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.HORDE_WARCHIEF, me, 4, 4);
        Mob victim = HeroGameTests.spawnUnit(helper, EntityRegistrar.SPIDER_UNIT.get(), them, 5, 4);
        if (hero == null || victim == null) {
            helper.fail("could not spawn the hero or its victim");
            return;
        }
        int pop = ((Unit) victim).getCost().population;
        if (pop <= 0 || pop % 2 != 0) {
            hero.discard();
            victim.discard();
            helper.fail("setup: the Spider's population is " + pop + ", the test needs an even one");
            return;
        }
        int want = expectedXp(victim);
        hero.setNoAi(true);   // stands still: the orbs come to it
        helper.runAfterDelay(3, victim::kill);
        helper.runAfterDelay(200, () -> {
            int got = ((HeroUnit) hero).getExperience();
            hero.discard();
            if (got != want)
                helper.fail("a " + pop + "-pop kill paid " + got + " XP, not " + want);
            else
                helper.succeed();
        });
    }

    /**
     * The hero falls two ticks after its kill, before the orbs (eight blocks off) can reach it: the fallen hero's
     * record - what the revive brings back - still holds the kill's XP.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hero_xp_is_kept_when_the_hero_falls_right_after_the_kill(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "xp_fall"), them = HeroGameTests.owner(helper, "xp_fall_victim");
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.HORDE_WARCHIEF, me, 3, 3);
        Mob victim = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 11, 3);
        if (hero == null || victim == null) {
            helper.fail("could not spawn the hero or its victim");
            return;
        }
        int want = expectedXp(victim);
        String type = hero.getType().getDescriptionId();
        hero.setNoAi(true);
        helper.runAfterDelay(3, victim::kill);
        helper.runAfterDelay(5, hero::kill);
        helper.runAfterDelay(30, () -> {
            HeroUnitSave saved = null;
            for (HeroUnitSave s : HeroServerEvents.fallenHeroes)
                if (s.ownerName.equals(me) && s.name.equals(type))
                    saved = s;
            HeroServerEvents.fallenHeroes.removeIf(s -> s.ownerName.equals(me));
            if (saved == null)
                helper.fail("setup: the fallen hero was not recorded");
            else if (saved.experience != want)
                helper.fail("the hero fell with " + saved.experience + " XP, not the " + want + " its kill was worth");
            else
                helper.succeed();
        });
    }

    /**
     * A fallen hero earns nothing: the hero is killed, and two ticks later an enemy dies beside its body (a dead hero
     * stays in the unit list for a moment, longer while bodies are kept). Neither the body nor the fallen hero's record
     * - what the revive brings back - gains XP. On main the orbs discarded themselves for a dying hero; paying at the
     * kill must ask the same (review r1 on PR 419).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hero_xp_a_fallen_hero_earns_none(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "xp_dead"), them = HeroGameTests.owner(helper, "xp_dead_victim");
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.HORDE_WARCHIEF, me, 3, 3);
        Mob victim = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 5, 3);
        if (hero == null || victim == null) {
            if (hero != null) hero.discard();
            if (victim != null) victim.discard();
            helper.fail("could not spawn the hero or its victim");
            return;
        }
        String type = hero.getType().getDescriptionId();
        hero.setNoAi(true);
        helper.runAfterDelay(3, hero::kill);
        helper.runAfterDelay(5, victim::kill);
        helper.runAfterDelay(30, () -> {
            int body = ((HeroUnit) hero).getExperience();
            HeroUnitSave saved = null;
            for (HeroUnitSave s : HeroServerEvents.fallenHeroes)
                if (s.ownerName.equals(me) && s.name.equals(type))
                    saved = s;
            HeroServerEvents.fallenHeroes.removeIf(s -> s.ownerName.equals(me));
            if (!hero.isRemoved())
                hero.discard();
            if (body != 0)
                helper.fail("the fallen hero's body gained " + body + " XP from a kill two ticks after it died");
            else if (saved != null && saved.experience != 0)
                helper.fail("the fallen hero's record holds " + saved.experience + " XP");
            else
                helper.succeed();
        });
    }
}
