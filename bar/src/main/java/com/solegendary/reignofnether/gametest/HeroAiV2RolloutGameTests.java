package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroBot;
import com.solegendary.reignofnether.hero.HeroKit;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Hero AI v2 for every hero (design-draft-heroes-better-2026-10-30 #2; lovish, Oct 30: "fix what exists first"). v2 -
 * the player's order first, only what the hero's side sees, never under an enemy tower's guns - was proven on the Dawn
 * Paladin alone (HeroBrain.ROLLED_OUT); the other seven heroes still ran v1, which reads every unit round the hero
 * (cloaked or not), fires its gap closer off a move order and lets the hero's own chase walk it under a tower. One test
 * per hero that v1 and v2 tell apart: the five with a strike skip an enemy their side cannot see; the Warchief keeps
 * its move order past an enemy in Savage Leap's reach. The Ash Shaman is rolled out too, but no test here tells v1 from
 * v2 for it while every one of its spells is Ai.NONE (its tower test passed on main, red run 41002933229, so it was
 * dropped): PR 418 gives its spells their AI. Written before the code (tests first). Each test has a batch of its own
 * (HeroAiV2GameTests: a hero looks 16 blocks round itself, and a neighbour's units are enemies in reach).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroAiV2RolloutGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static Mob hero(GameTestHelper helper, HeroKit kit, String owner, int x, int z) {
        HeroBot.setAiEnabled(owner, true);
        return HeroGameTests.spawnHero(helper, kit, owner, x, z);
    }

    // ------------------------------------------------------------------------------- only what its side can see

    /**
     * HeroAiV2GameTests.hero_ai_v2_target_pick_skips_an_enemy_its_side_cannot_see for {@code kit}'s strike in slot
     * {@code slot}: a cloaked Shade Ranger that one cast would finish, and an enemy in plain view seven blocks from it
     * (out of every splash, chain and line of the five strikes). The strike must go to the one in plain view. v1 scores
     * the finishable cloaked ranger highest and casts at it.
     */
    static void skipsTheUnseen(GameTestHelper helper, HeroKit kit, int slot, String tag) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2r_" + tag), them = me + "_foe";
        Mob ranger = HeroGameTests.spawnUnit(helper, EntityRegistrar.SHADE_RANGER_UNIT.get(), them, 11, 11);
        Mob plain = HeroAiV2GameTests.dummy(helper, them, 4, 11);
        List<Mob> mobs = HeroAiV2GameTests.mobs(ranger, plain);
        if (ranger == null || plain == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the enemies"), mobs, null);
            return;
        }
        int padY = HeroAiV2GameTests.skyPad(helper);
        HeroAiV2GameTests.onPad(ranger, padY);
        HeroAiV2GameTests.onPad(plain, padY);
        EngagementStance.set(ranger, EngagementStance.HOLD_FIRE);
        ((Unit) ranger).setHoldPosition(true);
        // every check is scheduled now, from the top (a runnable that schedules more while the test's runnables run
        // breaks GameTestInfo's map - see the v2 original)
        int spawnAt = ShadeRangerUnit.CLOAK_DELAY_TICKS + 15, last = spawnAt + 120;
        Mob[] h = { null };
        FactionHeroAbility[] strike = { null };
        boolean[] done = { false };
        helper.runAfterDelay(spawnAt, () -> {
            if (!ShadeRangerUnit.isCloaked(ranger)) {
                done[0] = true;
                HeroAiGameTests.finish(helper, List.of("setup: the Shade Ranger did not cloak"
                    + HeroAiV2GameTests.cloakDiag(ranger, them)), mobs, null);
                return;
            }
            h[0] = hero(helper, kit, me, 6, 5);
            if (!(h[0] instanceof FactionHero fh)) {
                done[0] = true;
                HeroAiGameTests.finish(helper, List.of("could not spawn the hero"), mobs, null);
                return;
            }
            HeroAiV2GameTests.onPad(h[0], padY);
            mobs.add(h[0]);
            strike[0] = HeroAiGameTests.learn(fh, slot);
            ranger.setHealth(4);   // one cast of any of the five strikes finishes it
        });
        for (int t = spawnAt + 1; t <= last; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0] || !(h[0] instanceof FactionHero fh))
                    return;
                if (!strike[0].isOffCooldown(fh)) {
                    done[0] = true;
                    List<String> problems = new ArrayList<>();
                    if (!ranger.isAlive() || ranger.getHealth() < 4)
                        problems.add(kit.itemName + "'s " + strike[0].spec.id + " struck the cloaked Shade Ranger, which the hero's side cannot see");
                    HeroAiGameTests.finish(helper, problems, mobs, null);
                } else if (tick == last) {
                    done[0] = true;
                    HeroAiGameTests.finish(helper, List.of(kit.itemName + "'s " + strike[0].spec.id
                        + " was never cast at the enemy in plain view"), mobs, null);
                }
            });
        }
    }

    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_gravecaller", timeoutTicks = 260)
    public static void hero_ai_v2_rollout_gravecaller_skips_an_enemy_its_side_cannot_see(GameTestHelper helper) {
        skipsTheUnseen(helper, HeroKits.GRAVECALLER, 0, "grave");   // Bone Spear
    }

    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_druid", timeoutTicks = 260)
    public static void hero_ai_v2_rollout_druid_skips_an_enemy_its_side_cannot_see(GameTestHelper helper) {
        skipsTheUnseen(helper, HeroKits.THORNHEART_DRUID, 0, "druid");   // Thorn Volley
    }

    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_corsair", timeoutTicks = 260)
    public static void hero_ai_v2_rollout_corsair_skips_an_enemy_its_side_cannot_see(GameTestHelper helper) {
        skipsTheUnseen(helper, HeroKits.CORSAIR_CAPTAIN, 0, "corsair");   // Pistol Shot
    }

    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_witch", timeoutTicks = 260)
    public static void hero_ai_v2_rollout_storm_witch_skips_an_enemy_its_side_cannot_see(GameTestHelper helper) {
        skipsTheUnseen(helper, HeroKits.STORM_WITCH, 0, "witch");   // Chain Lightning
    }

    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_engineer", timeoutTicks = 260)
    public static void hero_ai_v2_rollout_arch_engineer_skips_an_enemy_its_side_cannot_see(GameTestHelper helper) {
        skipsTheUnseen(helper, HeroKits.ARCH_ENGINEER, 0, "engineer");   // Rivet Burst
    }

    // ------------------------------------------------------------------------------- the player's order first

    /**
     * The Warchief under a move order walks its lane past an enemy six blocks off it and arrives; Savage Leap (in
     * reach, with Cleave ready as its follow-up) is not fired off the lane. v1 leaps onto the enemy (a melee hero,
     * healthy, a target 4+ blocks off), off the route the player gave. HeroAiV2GameTests' Paladin version also asks for
     * a cast on the way; the Warchief has nothing that reaches six blocks without leaving the lane, so this one does not.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_warchief", timeoutTicks = 300)
    public static void hero_ai_v2_rollout_warchief_keeps_its_move_order(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2r_warchief"), them = me + "_foe";
        Mob h = hero(helper, HeroKits.HORDE_WARCHIEF, me, 3, 1);
        Mob foe = HeroAiV2GameTests.dummy(helper, them, 9, 8);
        if (!(h instanceof FactionHero w) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), HeroAiV2GameTests.mobs(h, foe), null);
            return;
        }
        FactionHeroAbility leap = HeroAiGameTests.learn(w, 0);
        HeroAiGameTests.learn(w, 2);   // Cleave, the leap's follow-up
        BlockPos dest = HeroAiV2GameTests.at(helper, 3, 15);
        double lane = h.getX();
        double[] worst = { 0 };
        double[] closest = { Double.MAX_VALUE };
        boolean[] leapt = { false };
        int[] arrived = { -1 };
        helper.runAfterDelay(2, () -> HeroAiV2GameTests.order(helper, me, h, UnitAction.MOVE, dest));
        for (int t = 3; t <= 260; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (arrived[0] < 0) {
                    worst[0] = Math.max(worst[0], Math.abs(h.getX() - lane));
                    leapt[0] |= !leap.isOffCooldown(w);
                    closest[0] = Math.min(closest[0], HeroAiV2GameTests.flat(h, dest.getX() + 0.5, dest.getZ() + 0.5));
                    if (closest[0] <= 1.5)
                        arrived[0] = tick;
                }
                if (tick < 260)
                    return;
                List<String> problems = new ArrayList<>();
                if (worst[0] > 2.5)
                    problems.add(String.format("the Warchief left its lane by %.1f blocks during a move order (the enemy was 6 off it)", worst[0]));
                if (leapt[0])
                    problems.add("the Warchief fired Savage Leap while its move order ran");
                if (arrived[0] < 0)
                    problems.add(String.format("the Warchief never got to where it was sent (%.1f blocks short at best)", closest[0]));
                HeroAiGameTests.finish(helper, problems, HeroAiV2GameTests.mobs(h, foe), null);
            });
        }
    }
}
