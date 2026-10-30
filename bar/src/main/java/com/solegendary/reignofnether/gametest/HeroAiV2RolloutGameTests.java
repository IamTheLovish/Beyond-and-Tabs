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
 * dropped): PR 418 gives its spells their AI. Review r1 (lovish: "add per-hero tests + fix hold") adds the move, hold
 * and tower rules once per hero; the hold tests are aimed at v2's forward ground casts (Raise the Fallen, Deploy Turret)
 * walking a held hero to the edge of their range (review r1). Written before the code (tests first). Each test has a batch of its own
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

    // ------------------------------------------------------------------------------- the player's order, every hero
    // (review r1 on PR 416, lovish: "add per-hero tests + fix hold") - the move, hold and tower rules once per hero

    /** The kit's first three abilities at rank 1: what a hero has by level 5. */
    static void learnThree(FactionHero h) {
        for (int slot = 0; slot < 3; slot++)
            HeroAiGameTests.learn(h, slot);
    }

    /**
     * Hold position for {@code kit}: the hero (at block hx, hz), its first three abilities learnt, holds its spot with
     * an enemy at block (fx, fz) and never leaves it by more than {@code tolerance} - it casts only from where it stands.
     * <p>
     * The Gravecaller and the Arch-Engineer have a forward ground cast (Raise the Fallen, range 10; Deploy Turret, 8)
     * aimed at the target's block. GenericTargetedSpellGoal.isInRange measures to that block's lowest corner, so with the
     * enemy just inside the range by its centre but that corner just outside it - the enemy toward -x/-z - a held hero
     * walks until the corner is in range: about half a block (the first red, 41034472996, saw 1.1 once in three under a
     * 1-block tolerance). Their tests stand the enemy so and allow 0.3 blocks; the others keep the Paladin test's 1.
     */
    static void keepsHold(GameTestHelper helper, HeroKit kit, int hx, int hz, int fx, int fz, double tolerance, int castSlot, String tag) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2r_hold_" + tag), them = me + "_foe";
        Mob h = hero(helper, kit, me, hx, hz);
        Mob foe = HeroAiV2GameTests.dummy(helper, them, fx, fz);
        List<Mob> mobs = HeroAiV2GameTests.mobs(h, foe);
        if (!(h instanceof FactionHero fh) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        learnThree(fh);
        FactionHeroAbility mustCast = castSlot < 0 ? null : HeroKits.ability(fh, castSlot);
        double[] spot = new double[2];
        double[] worst = { 0 };
        boolean[] cast = { false };
        helper.runAfterDelay(2, () -> {
            spot[0] = h.getX();
            spot[1] = h.getZ();
            HeroAiV2GameTests.order(helper, me, h, UnitAction.HOLD, BlockPos.ZERO);
        });
        for (int t = 3; t <= 160; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                worst[0] = Math.max(worst[0], HeroAiV2GameTests.flat(h, spot[0], spot[1]));
                cast[0] |= mustCast != null && !mustCast.isOffCooldown(fh);
                if (tick < 160)
                    return;
                List<String> problems = new ArrayList<>();
                // a hero that never casts on Hold never moves either: the cast must have happened (review r2)
                if (mustCast != null && !cast[0])
                    problems.add(String.format("%s never cast %s from its hold spot", kit.itemName, mustCast.spec.id));
                if (worst[0] > tolerance)
                    problems.add(String.format("%s left its hold spot by %.2f blocks (allowed %.1f; an enemy %.1f blocks off)", kit.itemName,
                        worst[0], tolerance, Math.hypot(fx - hx, fz - hz)));
                HeroAiGameTests.finish(helper, problems, mobs, null);
            });
        }
    }

    /**
     * A move order for {@code kit}: the hero walks its lane past an enemy six blocks off it and arrives, never leaving
     * the lane by more than 2.5 blocks (HeroAiV2GameTests' Paladin rule; the Warchief has its own test above).
     */
    static void keepsMove(GameTestHelper helper, HeroKit kit, String tag) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2r_move_" + tag), them = me + "_foe";
        Mob h = hero(helper, kit, me, 3, 1);
        Mob foe = HeroAiV2GameTests.dummy(helper, them, 9, 8);
        List<Mob> mobs = HeroAiV2GameTests.mobs(h, foe);
        if (!(h instanceof FactionHero fh) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        learnThree(fh);
        BlockPos dest = HeroAiV2GameTests.at(helper, 3, 15);
        double lane = h.getX();
        double[] worst = { 0 };
        double[] closest = { Double.MAX_VALUE };
        int[] arrived = { -1 };
        helper.runAfterDelay(2, () -> HeroAiV2GameTests.order(helper, me, h, UnitAction.MOVE, dest));
        for (int t = 3; t <= 260; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (arrived[0] < 0) {
                    worst[0] = Math.max(worst[0], Math.abs(h.getX() - lane));
                    closest[0] = Math.min(closest[0], HeroAiV2GameTests.flat(h, dest.getX() + 0.5, dest.getZ() + 0.5));
                    if (closest[0] <= 1.5)
                        arrived[0] = tick;
                }
                if (tick < 260)
                    return;
                List<String> problems = new ArrayList<>();
                if (worst[0] > 2.5)
                    problems.add(String.format("%s left its lane by %.1f blocks during a move order (the enemy was 6 off it)", kit.itemName, worst[0]));
                if (arrived[0] < 0)
                    problems.add(String.format("%s never got to where it was sent (%.1f blocks short at best)", kit.itemName, closest[0]));
                HeroAiGameTests.finish(helper, problems, mobs, null);
            });
        }
    }

    /**
     * {@code kit} stands just outside an enemy raid tower's reach with an enemy inside it (HeroAiV2GameTests' Paladin
     * test): neither its abilities nor its own chase take it under the guns. The Ash Shaman's goes into PR 418 once
     * its spells have their AI (lovish).
     */
    static void staysOutOfTower(GameTestHelper helper, HeroKit kit, String tag) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2r_tower_" + tag), them = me + "_foe";
        var tower = HeroAiV2GameTests.raidTower(helper, them);
        if (tower == null) {
            HeroAiGameTests.finish(helper, List.of("setup: the raid tower is not a DefenceTowerPlacement"), new ArrayList<>(), null);
            return;
        }
        Runnable cleanup = () -> com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(tower);
        double tx = tower.centrePos.getX() + 0.5, tz = tower.centrePos.getZ() + 0.5;
        int range = tower.tower.kind.range;
        Mob h = hero(helper, kit, me, 15, 15);
        Mob foe = HeroAiV2GameTests.dummy(helper, them, 11, 11);
        List<Mob> mobs = HeroAiV2GameTests.mobs(h, foe);
        if (!(h instanceof FactionHero fh) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, cleanup);
            return;
        }
        if (HeroAiV2GameTests.flat(h, tx, tz) <= range + 2 || HeroAiV2GameTests.flat(foe, tx, tz) >= range - 1) {
            HeroAiGameTests.finish(helper, List.of(String.format("setup: hero %.1f and enemy %.1f from the tower (range %d)",
                HeroAiV2GameTests.flat(h, tx, tz), HeroAiV2GameTests.flat(foe, tx, tz), range)), mobs, cleanup);
            return;
        }
        learnThree(fh);
        double[] closest = { Double.MAX_VALUE };
        for (int t = 1; t <= 200; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                closest[0] = Math.min(closest[0], HeroAiV2GameTests.flat(h, tx, tz));
                if (tick < 200)
                    return;
                List<String> problems = new ArrayList<>();
                if (closest[0] <= range)
                    problems.add(String.format("%s went %.1f blocks from an enemy raid tower that reaches %d", kit.itemName, closest[0], range));
                HeroAiGameTests.finish(helper, problems, mobs, cleanup);
            });
        }
    }

    // hold: every hero; the two with a forward ground cast face an enemy at that cast's full range
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_hold_gravecaller", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_gravecaller_keeps_hold_position(GameTestHelper helper) { keepsHold(helper, HeroKits.GRAVECALLER, 13, 13, 6, 6, 0.3, 2, "grave"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_hold_engineer", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_arch_engineer_keeps_hold_position(GameTestHelper helper) { keepsHold(helper, HeroKits.ARCH_ENGINEER, 13, 13, 8, 7, 0.3, 2, "engineer"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_hold_druid", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_druid_keeps_hold_position(GameTestHelper helper) { keepsHold(helper, HeroKits.THORNHEART_DRUID, 4, 2, 4, 8, 1.0, -1, "druid"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_hold_corsair", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_corsair_keeps_hold_position(GameTestHelper helper) { keepsHold(helper, HeroKits.CORSAIR_CAPTAIN, 4, 2, 4, 8, 1.0, -1, "corsair"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_hold_witch", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_storm_witch_keeps_hold_position(GameTestHelper helper) { keepsHold(helper, HeroKits.STORM_WITCH, 4, 2, 4, 8, 1.0, -1, "witch"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_hold_warchief", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_warchief_keeps_hold_position(GameTestHelper helper) { keepsHold(helper, HeroKits.HORDE_WARCHIEF, 4, 2, 4, 8, 1.0, -1, "warchief"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_hold_shaman", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_ash_shaman_keeps_hold_position(GameTestHelper helper) { keepsHold(helper, HeroKits.ASH_SHAMAN, 4, 2, 4, 8, 1.0, -1, "shaman"); }

    // move: every hero but the Warchief (its own test above, with the leap check)
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_move_gravecaller", timeoutTicks = 300)
    public static void hero_ai_v2_rollout_gravecaller_keeps_its_move_order(GameTestHelper helper) { keepsMove(helper, HeroKits.GRAVECALLER, "grave"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_move_engineer", timeoutTicks = 300)
    public static void hero_ai_v2_rollout_arch_engineer_keeps_its_move_order(GameTestHelper helper) { keepsMove(helper, HeroKits.ARCH_ENGINEER, "engineer"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_move_druid", timeoutTicks = 300)
    public static void hero_ai_v2_rollout_druid_keeps_its_move_order(GameTestHelper helper) { keepsMove(helper, HeroKits.THORNHEART_DRUID, "druid"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_move_corsair", timeoutTicks = 300)
    public static void hero_ai_v2_rollout_corsair_keeps_its_move_order(GameTestHelper helper) { keepsMove(helper, HeroKits.CORSAIR_CAPTAIN, "corsair"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_move_witch", timeoutTicks = 300)
    public static void hero_ai_v2_rollout_storm_witch_keeps_its_move_order(GameTestHelper helper) { keepsMove(helper, HeroKits.STORM_WITCH, "witch"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_move_shaman", timeoutTicks = 300)
    public static void hero_ai_v2_rollout_ash_shaman_keeps_its_move_order(GameTestHelper helper) { keepsMove(helper, HeroKits.ASH_SHAMAN, "shaman"); }

    // tower: every hero but the Ash Shaman (PR 418, after this one merges)
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_tower_gravecaller", timeoutTicks = 240)
    public static void hero_ai_v2_rollout_gravecaller_never_goes_under_an_enemy_tower(GameTestHelper helper) { staysOutOfTower(helper, HeroKits.GRAVECALLER, "grave"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_tower_engineer", timeoutTicks = 240)
    public static void hero_ai_v2_rollout_arch_engineer_never_goes_under_an_enemy_tower(GameTestHelper helper) { staysOutOfTower(helper, HeroKits.ARCH_ENGINEER, "engineer"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_tower_druid", timeoutTicks = 240)
    public static void hero_ai_v2_rollout_druid_never_goes_under_an_enemy_tower(GameTestHelper helper) { staysOutOfTower(helper, HeroKits.THORNHEART_DRUID, "druid"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_tower_corsair", timeoutTicks = 240)
    public static void hero_ai_v2_rollout_corsair_never_goes_under_an_enemy_tower(GameTestHelper helper) { staysOutOfTower(helper, HeroKits.CORSAIR_CAPTAIN, "corsair"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_tower_witch", timeoutTicks = 240)
    public static void hero_ai_v2_rollout_storm_witch_never_goes_under_an_enemy_tower(GameTestHelper helper) { staysOutOfTower(helper, HeroKits.STORM_WITCH, "witch"); }
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_tower_warchief", timeoutTicks = 240)
    public static void hero_ai_v2_rollout_warchief_never_goes_under_an_enemy_tower(GameTestHelper helper) { staysOutOfTower(helper, HeroKits.HORDE_WARCHIEF, "warchief"); }

    /**
     * Hold truly holds for a ground cast whose target stands where the cast goal can't aim at its block (review r2 and r3
     * on PR 416, lovish: "Hold truly holds"). GenericTargetedSpellGoal.isInRange measures flat from the hero to the aimed
     * block's lowest corner, so a target inside the AI's reach by its centre (range - 0.5) can have its block out of the
     * goal's: aimed at it, the goal walks a held hero there. The hero holds at ({@code hx}, {@code hz}) exactly, the
     * target stands at ({@code tx}, {@code tz}) exactly - in the AI's reach by its centre, its block's corner past the
     * ability's range. The hero must cast ability {@code slot} where its effect still reaches the target (an area within
     * its radius, a line through it) - the strike must hurt the enemy, the heal must mend the friend - and move at most
     * 0.3. {@code healFriend}: the target is a hurt friend of the hero, else an enemy dummy.
     */
    static void castsFromItsHoldSpot(GameTestHelper helper, HeroKit kit, int slot, boolean healFriend, double hx, double hz,
                                     double tx, double tz, String tag) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2r_holdcast_" + tag), them = me + "_foe";
        Mob h = hero(helper, kit, me, (int) hx, (int) hz);
        Mob target = healFriend ? HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, (int) tx, (int) tz)
            : HeroAiV2GameTests.dummy(helper, them, (int) tx, (int) tz);
        List<Mob> mobs = HeroAiV2GameTests.mobs(h, target);
        if (!(h instanceof FactionHero fh) || target == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        h.teleportTo(o.getX() + hx, h.getY(), o.getZ() + hz);
        target.setNoAi(true);
        target.teleportTo(o.getX() + tx, target.getY(), o.getZ() + tz);
        if (healFriend)
            target.setHealth(target.getMaxHealth() * 0.4f);
        float health = target.getHealth();
        double centre = Math.hypot(hx - tx, hz - tz), corner = Math.hypot(hx - Math.floor(tx), hz - Math.floor(tz));
        FactionHeroAbility ability = HeroAiGameTests.learn(fh, slot);
        double[] spot = new double[2];
        double[] worst = { 0 };
        boolean[] cast = { false };
        helper.runAfterDelay(2, () -> {
            spot[0] = h.getX();
            spot[1] = h.getZ();
            HeroAiV2GameTests.order(helper, me, h, UnitAction.HOLD, BlockPos.ZERO);
        });
        for (int t = 3; t <= 160; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                worst[0] = Math.max(worst[0], HeroAiV2GameTests.flat(h, spot[0], spot[1]));
                cast[0] |= !ability.isOffCooldown(fh);
                if (tick < 160)
                    return;
                List<String> problems = new ArrayList<>();
                String what = String.format("the %s %.2f blocks off (its block's corner %.2f, range %.0f)",
                    healFriend ? "hurt friend" : "enemy", centre, corner, ability.range);
                if (!cast[0])
                    problems.add(String.format("%s never cast %s at %s", kit.itemName, ability.spec.id, what));
                else if (healFriend ? target.getHealth() <= health : target.getHealth() >= health)
                    problems.add(String.format("%s cast %s but it missed %s", kit.itemName, ability.spec.id, what));
                if (worst[0] > 0.3)
                    problems.add(String.format("%s left its hold spot by %.2f blocks to cast %s (allowed 0.3)", kit.itemName,
                        worst[0], ability.spec.id));
                HeroAiGameTests.finish(helper, problems, mobs, null);
            });
        }
    }

    // the hero 9.48 blocks from the friend's centre, 10.6 from its block's corner (Rejuvenate: range 10, radius 6)
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_holdcast_druid", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_druid_heals_from_its_hold_spot(GameTestHelper helper) {
        castsFromItsHoldSpot(helper, HeroKits.THORNHEART_DRUID, 2, true, 13.5, 13.5, 6.8, 6.8, "druid");
    }

    // 11.17 from the enemy's centre, 12.52 from its block's corner (Thorn Volley: range 12, radius 3)
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_holdcast_druid_volley", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_druid_volleys_from_its_hold_spot(GameTestHelper helper) {
        castsFromItsHoldSpot(helper, HeroKits.THORNHEART_DRUID, 0, false, 13.85, 13.85, 5.95, 5.95, "volley");
    }

    // 13.15 from the enemy's centre, 14.50 from its block's corner (Bone Spear: range 14, a line)
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_holdcast_gravecaller_spear", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_gravecaller_spears_from_its_hold_spot(GameTestHelper helper) {
        castsFromItsHoldSpot(helper, HeroKits.GRAVECALLER, 0, false, 15.25, 15.25, 5.95, 5.95, "spear");
    }

    /** The entity the hero's cast goal is aimed at, if any (HeroCore.castGoal, read by name: the tests come first). */
    @javax.annotation.Nullable
    static net.minecraft.world.entity.LivingEntity castGoalTarget(FactionHero fh) {
        try {
            java.lang.reflect.Field f = fh.heroCore().getClass().getDeclaredField("castGoal");
            f.setAccessible(true);
            return f.get(fh.heroCore()) instanceof com.solegendary.reignofnether.unit.goals.GenericTargetedSpellGoal g
                ? g.getTargetEntity() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Hold truly holds when the target walks off (review r3 on PR 416): GenericTargetedSpellGoal follows an entity
     * target, re-aiming at its block every tick and walking after it once it is out of range, so a held hero striking an
     * enemy that steps away followed it. The Arch-Engineer holds at block (13, 13) with an enemy dummy 5.5 blocks off;
     * the moment its Rivet Burst is aimed at the dummy (before it lands), the dummy is moved 18 blocks away, out of reach.
     * The hero must let the strike go and move at most 0.3.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_rollout_holdcast_engineer_walks_off", timeoutTicks = 220)
    public static void hero_ai_v2_rollout_arch_engineer_drops_a_strike_whose_target_walks_off(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2r_walkoff"), them = me + "_foe";
        Mob h = hero(helper, HeroKits.ARCH_ENGINEER, me, 13, 13);
        Mob target = HeroAiV2GameTests.dummy(helper, them, 8, 13);
        List<Mob> mobs = HeroAiV2GameTests.mobs(h, target);
        if (!(h instanceof FactionHero fh) || target == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        target.setNoAi(true);
        FactionHeroAbility ability = HeroAiGameTests.learn(fh, 0);   // Rivet Burst, range 10
        double[] spot = new double[2];
        double[] worst = { 0 };
        int[] aimedAt = { -1 };
        helper.runAfterDelay(2, () -> {
            spot[0] = h.getX();
            spot[1] = h.getZ();
            HeroAiV2GameTests.order(helper, me, h, UnitAction.HOLD, BlockPos.ZERO);
        });
        for (int t = 3; t <= 160; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                worst[0] = Math.max(worst[0], HeroAiV2GameTests.flat(h, spot[0], spot[1]));
                if (aimedAt[0] < 0 && castGoalTarget(fh) == target && ability.isOffCooldown(fh)) {
                    aimedAt[0] = tick;
                    target.teleportTo(o.getX() + 0.5, target.getY(), o.getZ() + 0.5);   // 18.4 blocks off: out of reach
                }
                if (tick < 160)
                    return;
                List<String> problems = new ArrayList<>();
                if (aimedAt[0] < 0)
                    problems.add("the Arch-Engineer never aimed Rivet Burst at the enemy 5.5 blocks off");
                if (worst[0] > 0.3)
                    problems.add(String.format("the Arch-Engineer left its hold spot by %.2f blocks after the enemy it "
                        + "aimed Rivet Burst at walked off (allowed 0.3)", worst[0]));
                HeroAiGameTests.finish(helper, problems, mobs, null);
            });
        }
    }
}
