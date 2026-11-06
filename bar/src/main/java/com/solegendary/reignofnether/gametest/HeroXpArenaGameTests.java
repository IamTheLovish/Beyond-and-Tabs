package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.HeroExperienceOrb;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroProgression;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The suite lays its arenas out far enough apart that no hero XP rule reaches from one test's arena into another's
 * (gametest/GameTestLayout). Vanilla left 5 blocks between arenas (6 between rows), 21 from one arena's spot to the same
 * spot next door, and a hero earns XP from any enemy death within 20 blocks (28 for a decisive kill, capture within 24):
 * WinPays' hero kill at tick 3 paid HeroAutoLearn's witch a second point, so hero_auto_learn_logout_ends_the_menu_hold
 * failed with "points 0, ranks 2" in 9 of 31 CI shard-8 jobs (Nov 1-3; flake-stress 41599142411 traced the 350 XP to
 * the Dawn Paladin dying 20 blocks away), and H13's Shaman read "experience 1250, not 800" (PR 473) the same way.
 * <p>
 * Two tests in a batch of their own, so they stand side by side: each kills an enemy hero in its own arena - the first at
 * tick {@value #FIRST_KILL}, the second at tick {@value #SECOND_KILL} - beside its own hero (hung 4 blocks up, no AI, so
 * it stays where it is put). The other test's victim must stand out of every hero XP reach of this hero, and this
 * hero's XP may change only at its own kill.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroXpArenaGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "hero_xp_arenas";
    static final int FIRST_KILL = 5, SECOND_KILL = 25, CHECK_FIRST = 15, CHECK_END = 40;
    /** Each test's victim, for the other test's layout check: [0] the first test's, [1] the second's. */
    static final Mob[] VICTIMS = new Mob[2];

    @GameTest(template = ARENA, timeoutTicks = 80, batch = BATCH)
    public static void a_kill_in_the_next_arena_pays_this_hero_nothing_first(GameTestHelper helper) {
        twoArenas(helper, 0);
    }

    @GameTest(template = ARENA, timeoutTicks = 80, batch = BATCH)
    public static void a_kill_in_the_next_arena_pays_this_hero_nothing_second(GameTestHelper helper) {
        twoArenas(helper, 1);
    }

    static void twoArenas(GameTestHelper helper, int me) {
        String own = "gametest_xparena_hero_" + me + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String foe = "gametest_xparena_foe_" + me + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.HORDE_WARCHIEF, own, 8, 8);
        Mob victim = HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, foe, 8, 12);
        if (hero == null || victim == null) {
            helper.fail("could not spawn the two heroes");
            return;
        }
        hero.setNoAi(true);
        hero.setNoGravity(true);
        hero.setPos(hero.getX(), hero.getY() + 4, hero.getZ());
        victim.setNoAi(true);
        VICTIMS[me] = victim;
        Vec3 victimAt = victim.position();
        int killAt = me == 0 ? FIRST_KILL : SECOND_KILL;
        List<String> problems = new ArrayList<>();
        int[] xp = { -1 };
        double[] neighbour = { -1 };
        helper.runAfterDelay(killAt - 1, () -> {
            Mob other = VICTIMS[1 - me];
            if (other != null)
                neighbour[0] = Math.sqrt(other.distanceToSqr(hero));
        });
        helper.runAfterDelay(killAt, victim::kill);
        helper.runAfterDelay(CHECK_FIRST, () -> {
            xp[0] = ((HeroUnit) hero).getExperience();
            if (me == 1 && xp[0] != 0)
                problems.add("the hero was paid " + xp[0] + " XP by the next arena's kill at tick " + FIRST_KILL
                    + ", before its own at " + SECOND_KILL);
            if (me == 0 && xp[0] <= 0)
                problems.add("its own kill at tick " + FIRST_KILL + " paid the hero nothing");
        });
        helper.runAfterDelay(CHECK_END, () -> {
            int end = ((HeroUnit) hero).getExperience();
            if (me == 0 && end != xp[0])
                problems.add("the hero was paid " + (end - xp[0]) + " XP by the next arena's kill at tick " + SECOND_KILL
                    + " (its own kill at " + FIRST_KILL + " paid " + xp[0] + ")");
            if (me == 1 && end <= xp[0])
                problems.add("its own kill at tick " + SECOND_KILL + " paid the hero nothing");
            // the layout: the other test's victim out of every hero XP reach of this hero
            double reach = Math.max(HeroProgression.BURST_RANGE, Math.max(HeroProgression.CAPTURE_RANGE, HeroExperienceOrb.RANGE));
            if (neighbour[0] < 0)
                problems.add("the other test's victim was nowhere at this test's kill: the two did not run side by side");
            else if (neighbour[0] <= reach)
                problems.add(String.format("the other test's victim stood %.1f blocks from this hero, inside the %.0f-block reach of"
                    + " a hero's kill XP: the arenas are laid out too close", neighbour[0], reach));
            ReignOfNether.LOGGER.info("[XpArena] GameTest {}: XP {} after its own kill, {} at the end; the other victim {} blocks away",
                me == 0 ? "first" : "second", xp[0], end, String.format("%.1f", neighbour[0]));
            hero.discard();
            for (Entity w : WreckServerEvents.getWrecks())
                if (!w.isRemoved() && w.position().distanceToSqr(victimAt) < 1.5 * 1.5)
                    w.discard();
            HeroServerEvents.fallenHeroes.removeIf(f -> f.ownerName.equals(foe));
            VICTIMS[me] = null;
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
