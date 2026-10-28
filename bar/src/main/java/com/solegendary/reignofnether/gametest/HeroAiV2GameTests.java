package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroBot;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.ai.HeroBrain;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hero AI v2, PR 1 (design/hero_mode.md "Hero AI"; lovish Oct 24: "act extremely smart when there is no hero player";
 * his requirement 14: "player move/attack/hold/retreat always wins ... never leaves a move order to chase; hold = cast
 * only from where it stands"; decision D030, Oct 27 00:21: "a near-death hero MAY flee on its own at first. Once the
 * player selects or commands it again, the player's order wins"). The brain that drives an unpossessed hero: its
 * OrderLock (the player's order first), Perception (only what its side can see), FightEval (who is winning, where the
 * enemy towers reach) and the combo rule (a gap closer only when its follow-up is ready; the stun before the damage).
 * <p>
 * Written before the code (design/LANES.md, tests first); each one says what main does wrong. The Dawn Paladin is the
 * one hero v2 is proven on first (lesson L005: one hero, in game, before the other six). Each test runs in a batch of its
 * own: arenas of one batch sit side by side, a hero looks 16 blocks round itself, and every other test's units belong
 * to another owner - an enemy in reach (AGENTS.md: "a test's arena sits beside its batch neighbours'"). The enemies
 * here are on Hold fire and invulnerable unless a test needs otherwise, so that nothing but the hero decides anything.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroAiV2GameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** An enemy that stands where it is put and never fights back: a Vindicator on Hold fire, invulnerable. */
    static Mob dummy(GameTestHelper helper, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, x, z);
        if (m != null) {
            EngagementStance.set(m, EngagementStance.HOLD_FIRE);
            m.setInvulnerable(true);
        }
        return m;
    }

    /** A plain player order, exactly as the server takes one from the player's client (UnitActionServerboundPacket). */
    static void order(GameTestHelper helper, String owner, Mob unit, UnitAction action, BlockPos at) {
        CommandQueues.issue(helper.getLevel(), owner, action, -1, new int[] { unit.getId() }, at, BlockPos.ZERO,
            QueueMode.REPLACE);
    }

    /** The test's units, nulls allowed (a failed spawn): HeroAiGameTests.finish discards the rest. */
    static List<Mob> mobs(Mob... ms) {
        return new ArrayList<>(Arrays.asList(ms));
    }

    static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 2, z));
    }

    static double flat(Mob m, double x, double z) {
        double dx = m.getX() - x, dz = m.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static Mob paladin(GameTestHelper helper, String owner, int x, int z) {
        HeroBot.setAiEnabled(owner, true);
        return HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, owner, x, z);
    }

    // ------------------------------------------------------------------------------------------ obeying orders

    /**
     * A move order is carried out as given: the hero walks its lane past an enemy and still fights from it (Judgement
     * in reach), but never leaves the lane to chase. Main: the brain fires Shield Charge (a gap closer) at the enemy six
     * blocks off the lane, so the hero lands beside it, off the route the player gave.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_move", timeoutTicks = 300)
    public static void hero_ai_v2_a_move_order_is_not_overridden(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_move"), them = me + "_foe";
        Mob hero = paladin(helper, me, 3, 1);
        Mob foe = dummy(helper, them, 9, 8);
        if (!(hero instanceof FactionHero p) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs(hero, foe), null);
            return;
        }
        HeroAiGameTests.learn(p, 0);   // Shield Charge
        FactionHeroAbility judgement = HeroAiGameTests.learn(p, 1);
        BlockPos dest = at(helper, 3, 15);
        double lane = hero.getX();
        double[] worst = { 0 };
        double[] closest = { Double.MAX_VALUE };
        boolean[] cast = { false };
        int[] arrived = { -1 };
        helper.runAfterDelay(2, () -> order(helper, me, hero, UnitAction.MOVE, dest));
        for (int t = 3; t <= 260; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                // the order is judged while it runs: once the hero is there it is done, and the hero may fight
                if (arrived[0] < 0) {
                    worst[0] = Math.max(worst[0], Math.abs(hero.getX() - lane));
                    cast[0] |= !judgement.isOffCooldown(p);
                    closest[0] = Math.min(closest[0], flat(hero, dest.getX() + 0.5, dest.getZ() + 0.5));
                    if (closest[0] <= 1.5)
                        arrived[0] = tick;
                }
                if (tick < 260)
                    return;
                List<String> problems = new ArrayList<>();
                if (worst[0] > 2.5)
                    problems.add(String.format("the hero left its lane by %.1f blocks during a move order (the enemy was 6 off it)", worst[0]));
                if (arrived[0] < 0)
                    problems.add(String.format("the hero never got to where it was sent (%.1f blocks short at best)", closest[0]));
                if (!cast[0])
                    problems.add("the hero cast nothing while it walked past an enemy in Judgement's reach (it should fight while it obeys)");
                HeroAiGameTests.finish(helper, problems, mobs(hero, foe), null);
            });
        }
    }

    /**
     * Hold position: the hero fights only from where it stands - Judgement at the enemy in reach, yes; Shield Charge
     * onto it, no. Main: the brain charges the enemy six blocks away and the hero leaves its spot.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_hold", timeoutTicks = 200)
    public static void hero_ai_v2_hold_position_is_respected(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_hold"), them = me + "_foe";
        Mob hero = paladin(helper, me, 4, 3);
        Mob foe = dummy(helper, them, 4, 9);
        if (!(hero instanceof FactionHero p) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs(hero, foe), null);
            return;
        }
        HeroAiGameTests.learn(p, 0);   // Shield Charge
        FactionHeroAbility judgement = HeroAiGameTests.learn(p, 1);
        double[] spot = new double[2];
        double[] worst = { 0 };
        boolean[] cast = { false };
        helper.runAfterDelay(2, () -> {
            spot[0] = hero.getX();
            spot[1] = hero.getZ();
            order(helper, me, hero, UnitAction.HOLD, BlockPos.ZERO);
        });
        for (int t = 3; t <= 150; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                worst[0] = Math.max(worst[0], flat(hero, spot[0], spot[1]));
                cast[0] |= !judgement.isOffCooldown(p);
                if (tick < 150)
                    return;
                List<String> problems = new ArrayList<>();
                if (worst[0] > 1.0)
                    problems.add(String.format("the hero left its hold spot by %.1f blocks", worst[0]));
                if (!cast[0])
                    problems.add("the hero on hold never cast Judgement at the enemy in its reach (hold = cast from where it stands)");
                HeroAiGameTests.finish(helper, problems, mobs(hero, foe), null);
            });
        }
    }

    /** A player's attack order on {@code target}, as the server takes one from the player's client. */
    static void attack(GameTestHelper helper, String owner, Mob unit, Mob target) {
        CommandQueues.issue(helper.getLevel(), owner, UnitAction.ATTACK, target.getId(), new int[] { unit.getId() },
            BlockPos.ZERO, BlockPos.ZERO, QueueMode.REPLACE);
    }

    /**
     * An attack order on an enemy seven blocks off: the hero opens with Shield Charge onto that same enemy (Judgement,
     * its follow-up, is ready), not onto the nearer enemy beside it. Its legs are held (speed 0) so that only the charge
     * can bring it there. Main (Mode A, Oct 28, item 3): ATTACK lets the brain move nothing, so a melee hero ordered onto
     * an enemy walks the whole way and never uses its charge on the target it was given.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_attack", timeoutTicks = 200)
    public static void hero_ai_v2_an_attack_order_opens_with_the_charge_on_its_target(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_attack"), them = me + "_foe";
        Mob hero = paladin(helper, me, 4, 3);
        Mob target = dummy(helper, them, 4, 10);
        Mob other = dummy(helper, them, 10, 5);
        List<Mob> mobs = mobs(hero, target, other);
        if (!(hero instanceof FactionHero p) || target == null || other == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        target.setInvulnerable(false);   // a target the order may hit (it stands on Hold fire all the same)
        AttributeInstance speed = hero.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
            speed.setBaseValue(0);
        FactionHeroAbility charge = HeroAiGameTests.learn(p, 0);
        HeroAiGameTests.learn(p, 1);   // Judgement, the charge's follow-up
        int[] chargedAt = { -1 };
        double[] landed = { Double.MAX_VALUE, Double.MAX_VALUE };
        helper.runAfterDelay(2, () -> attack(helper, me, hero, target));
        for (int t = 3; t <= 160; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (chargedAt[0] < 0 && !charge.isOffCooldown(p))
                    chargedAt[0] = tick;
                if (chargedAt[0] >= 0 && tick == chargedAt[0] + 20) {
                    landed[0] = hero.distanceTo(target);
                    landed[1] = hero.distanceTo(other);
                }
                if (tick < 160)
                    return;
                List<String> problems = new ArrayList<>();
                if (chargedAt[0] < 0)
                    problems.add(String.format("under an attack order the hero never used Shield Charge on its target (still %.1f blocks off)",
                        hero.distanceTo(target)));
                else if (landed[0] > 3 || landed[1] < landed[0])
                    problems.add(String.format("the charge did not land on the ordered target (%.1f blocks from it, %.1f from the other enemy)",
                        landed[0], landed[1]));
                HeroAiGameTests.finish(helper, problems, mobs, null);
            });
        }
    }

    /**
     * Right after a move order is done the hero is the brain's again, and it goes for the enemy in its reach. The move
     * test only asks that the hero got to its spot (Mode A, Oct 28, item 5: nothing tested the aggression after it):
     * here an enemy stands five blocks past the spot, on Hold fire, and the hero must have it by the end. Before the
     * spot the hero keeps to its lane (no chase while the order runs).
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_after_move", timeoutTicks = 360)
    public static void hero_ai_v2_a_hero_fights_right_after_its_move_order(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_after_move"), them = me + "_foe";
        Mob hero = paladin(helper, me, 3, 1);
        Mob foe = dummy(helper, them, 8, 9);
        List<Mob> mobs = mobs(hero, foe);
        if (!(hero instanceof FactionHero) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        BlockPos dest = at(helper, 3, 8);
        double lane = hero.getX();
        double[] worst = { 0 };
        double[] closest = { Double.MAX_VALUE };
        int[] arrived = { -1 }, reachedFoe = { -1 };
        helper.runAfterDelay(2, () -> order(helper, me, hero, UnitAction.MOVE, dest));
        for (int t = 3; t <= 320; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (arrived[0] < 0) {
                    worst[0] = Math.max(worst[0], Math.abs(hero.getX() - lane));
                    closest[0] = Math.min(closest[0], flat(hero, dest.getX() + 0.5, dest.getZ() + 0.5));
                    if (closest[0] <= 1.5)
                        arrived[0] = tick;
                } else if (reachedFoe[0] < 0 && hero.distanceTo(foe) <= 2.5) {
                    reachedFoe[0] = tick;
                }
                if (tick < 320)
                    return;
                List<String> problems = new ArrayList<>();
                if (worst[0] > 2.5)
                    problems.add(String.format("the hero left its lane by %.1f blocks during the move order", worst[0]));
                if (arrived[0] < 0)
                    problems.add(String.format("the hero never got to where it was sent (%.1f blocks short at best)", closest[0]));
                else if (reachedFoe[0] < 0)
                    problems.add(String.format("after its move order the hero never went for the enemy %.1f blocks off (state %s)",
                        hero.distanceTo(foe), state(hero)));
                HeroAiGameTests.finish(helper, problems, mobs, null);
            });
        }
    }

    /**
     * The player takes the hero (possession, or the Hero AI switched off): the brain lets go of what it saw too, so it
     * keeps no hold on units that may die or unload while the player has the hero. Main (Mode A note, Oct 28): off()
     * dropped the combo but kept the last scan's lists of enemies, friends and towers.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_off", timeoutTicks = 160)
    public static void hero_ai_v2_switched_off_the_brain_forgets_what_it_saw(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_off"), them = me + "_foe";
        Mob hero = paladin(helper, me, 4, 3);
        Mob foe = dummy(helper, them, 4, 9);
        List<Mob> mobs = mobs(hero, foe);
        if (!(hero instanceof FactionHero p) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        int[] offAt = { -1 };
        boolean[] done = { false };
        Runnable cleanup = () -> HeroBot.setAiEnabled(me, true);
        for (int t = 1; t <= 120; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                HeroBrain b = p.heroCore().brainIfAny();
                if (offAt[0] < 0) {
                    if (b != null && b.active() && !b.seen.enemies.isEmpty()) {
                        offAt[0] = tick;
                        HeroBot.setAiEnabled(me, false);   // the player switches the Hero AI off
                    } else if (tick == 100) {
                        done[0] = true;
                        HeroAiGameTests.finish(helper, List.of("setup: the brain never saw the enemy"), mobs, cleanup);
                    }
                } else if (tick == offAt[0] + 5) {
                    done[0] = true;
                    List<String> problems = new ArrayList<>();
                    if (b == null || b.active())
                        problems.add("the brain did not let go of the hero when the Hero AI was switched off");
                    else if (!b.seen.enemies.isEmpty() || !b.seen.friends.isEmpty() || !b.seen.towers.isEmpty())
                        problems.add(String.format("switched off, the brain still holds its last scan (%d enemies, %d friends, %d towers)",
                            b.seen.enemies.size(), b.seen.friends.size(), b.seen.towers.size()));
                    HeroAiGameTests.finish(helper, problems, mobs, cleanup);
                }
            });
        }
    }

    // ------------------------------------------------------------------------------------------ retreat (D030)

    /**
     * The retreat threshold and D030. The hero walks its move order toward four enemies: at half health it keeps
     * going; dropped below the threshold it breaks off and runs before it reaches them (a near-death hero may flee on
     * its own at first); the player then orders it on again and it obeys, walking right past them at the same low
     * health (a fresh command clears the flight until the next new danger). Main: the hero never breaks off a move order
     * - it walks into the enemies at a quarter of its health.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_retreat", timeoutTicks = 400)
    public static void hero_ai_v2_flees_below_the_retreat_threshold_until_the_player_orders_again(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_flee"), them = me + "_foe";
        Mob hero = paladin(helper, me, 8, 1);
        List<Mob> mobs = new ArrayList<>();
        mobs.add(hero);
        for (int[] xz : new int[][] { { 5, 11 }, { 11, 11 }, { 5, 13 }, { 11, 13 } })
            mobs.add(dummy(helper, them, xz[0], xz[1]));
        if (!(hero instanceof FactionHero p) || mobs.contains(null)) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        HeroAiGameTests.learn(p, 1);   // Judgement: something to fight with
        BlockPos dest = at(helper, 8, 15);
        double front = helper.absolutePos(new BlockPos(0, 2, 11)).getZ();   // the enemies' near row (block corner)
        double startZ = hero.getZ();
        double[] z30 = { 0 };
        double[] deepest = { Double.NEGATIVE_INFINITY };
        double[] later = { 0 };
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            hero.setHealth(hero.getMaxHealth() * 0.5f);
            order(helper, me, hero, UnitAction.MOVE, dest);
        });
        helper.runAfterDelay(30, () -> {
            z30[0] = hero.getZ();
            if (z30[0] < startZ + 0.5)
                problems.add(String.format("at half health the hero did not follow its move order (%.1f -> %.1f)", startZ, z30[0]));
            hero.setHealth(hero.getMaxHealth() * 0.25f);   // below the retreat threshold: near death
        });
        for (int t = 31; t <= 150; t++)
            helper.runAfterDelay(t, () -> deepest[0] = Math.max(deepest[0], hero.getZ()));
        helper.runAfterDelay(150, () -> {
            if (deepest[0] >= front)
                problems.add(String.format("at a quarter health the hero walked on into the enemies (reached z %.1f, their row at %.1f) instead of fleeing",
                    deepest[0] - helper.absolutePos(BlockPos.ZERO).getZ(), front - helper.absolutePos(BlockPos.ZERO).getZ()));
            else if (hero.getZ() > deepest[0] - 1.5)
                problems.add("at a quarter health the hero stopped short of the enemies but never ran from them");
            later[0] = hero.getZ();
            // the player takes it back: the same order again, at the same low health
            order(helper, me, hero, UnitAction.MOVE, dest);
        });
        // obeyed = it got there; once there the order is done and the hero may turn on the enemies beside the spot
        double[] closest = { Double.MAX_VALUE };
        for (int t = 151; t <= 360; t++)
            helper.runAfterDelay(t, () -> closest[0] = Math.min(closest[0], flat(hero, dest.getX() + 0.5, dest.getZ() + 0.5)));
        helper.runAfterDelay(360, () -> {
            if (hero.isAlive() && closest[0] > 1.5)
                problems.add(String.format("after the player ordered it on again the hero still did not obey (%.1f blocks short of the spot at best, from z %.1f)",
                    closest[0], later[0] - helper.absolutePos(BlockPos.ZERO).getZ()));
            HeroAiGameTests.finish(helper, problems, mobs, null);
        });
    }

    // ------------------------------------------------------------------------------------------ what it knows

    /**
     * Target pick sees only what the hero's side sees: a cloaked Shade Ranger it could finish with one Judgement is not
     * there for it; Judgement goes to the enemy in plain view. Main: the brain reads every unit round the hero, hidden
     * or not, and spends Judgement on the cloaked one (the kill it can finish scores highest).
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_target", timeoutTicks = 260)
    public static void hero_ai_v2_target_pick_skips_an_enemy_its_side_cannot_see(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_target"), them = me + "_foe";
        Mob ranger = HeroGameTests.spawnUnit(helper, EntityRegistrar.SHADE_RANGER_UNIT.get(), them, 8, 11);
        Mob plain = dummy(helper, them, 4, 11);
        List<Mob> mobs = mobs(ranger, plain);
        if (ranger == null || plain == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the enemies"), mobs, null);
            return;
        }
        EngagementStance.set(ranger, EngagementStance.HOLD_FIRE);
        ((Unit) ranger).setHoldPosition(true);
        // a Shade Ranger cloaks after standing still with no target for its cloak delay; the hero comes after that.
        // Every check is scheduled now, from the top: a runnable that schedules more of them while the test's delayed
        // runnables are being run breaks the map they sit in (a NullPointerException in GameTestInfo.tickInternal)
        int spawnAt = ShadeRangerUnit.CLOAK_DELAY_TICKS + 15, last = spawnAt + 120;
        Mob[] hero = { null };
        FactionHeroAbility[] judgement = { null };
        boolean[] done = { false };
        helper.runAfterDelay(spawnAt, () -> {
            if (!ShadeRangerUnit.isCloaked(ranger)) {
                done[0] = true;
                HeroAiGameTests.finish(helper, List.of("setup: the Shade Ranger did not cloak"), mobs, null);
                return;
            }
            hero[0] = paladin(helper, me, 6, 5);
            if (!(hero[0] instanceof FactionHero p)) {
                done[0] = true;
                HeroAiGameTests.finish(helper, List.of("could not spawn the hero"), mobs, null);
                return;
            }
            mobs.add(hero[0]);
            judgement[0] = HeroAiGameTests.learn(p, 1);
            ranger.setHealth(4);   // one Judgement (10) finishes it
        });
        for (int t = spawnAt + 1; t <= last; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0] || !(hero[0] instanceof FactionHero p))
                    return;
                if (!judgement[0].isOffCooldown(p)) {
                    done[0] = true;
                    List<String> problems = new ArrayList<>();
                    if (!ranger.isAlive() || ranger.getHealth() < 4)
                        problems.add("Judgement struck the cloaked Shade Ranger, which the hero's side cannot see");
                    HeroAiGameTests.finish(helper, problems, mobs, null);
                } else if (tick == last) {
                    done[0] = true;
                    HeroAiGameTests.finish(helper, List.of("Judgement was never cast at the enemy in plain view"), mobs, null);
                }
            });
        }
    }

    /**
     * Never chase into an enemy tower: an enemy stands inside a raid tower's reach, the hero just outside it. The hero
     * may strike from where it is safe (Judgement reaches), but neither its gap closer nor its own chase takes it under
     * the tower's guns. Main: Shield Charge lands it beside the enemy, inside the tower's reach.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_tower", timeoutTicks = 240)
    public static void hero_ai_v2_never_chases_into_an_enemy_tower(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_tower"), them = me + "_foe";
        BuildingPlacement placed = BuildingUtils.getNewBuildingPlacement(Defences.VILLAGERS.raidTower(), helper.getLevel(),
            helper.absolutePos(new BlockPos(1, 1, 1)), Rotation.NONE, them, false);
        if (!(placed instanceof DefenceTower.DefenceTowerPlacement tower)) {
            HeroAiGameTests.finish(helper, List.of("setup: the raid tower is not a DefenceTowerPlacement"), new ArrayList<>(), null);
            return;
        }
        for (var block : tower.getBlocks())
            if (!block.getBlockState().isAir())
                helper.getLevel().setBlock(block.getBlockPos(), block.getBlockState(), 3);
        tower.initPlacedBlocks();   // a placement with no blocks standing counts as destroyed on its first tick
        tower.isBuilt = true;
        tower.onlyFoe = them + "_nobody";   // it never fires in this test: the hero must stay out on its own judgement
        BuildingServerEvents.getBuildings().add(tower);
        Runnable cleanup = () -> BuildingServerEvents.getBuildings().remove(tower);
        double tx = tower.centrePos.getX() + 0.5, tz = tower.centrePos.getZ() + 0.5;
        int range = tower.tower.kind.range;
        Mob hero = paladin(helper, me, 15, 15);
        Mob foe = dummy(helper, them, 11, 11);
        List<Mob> mobs = mobs(hero, foe);
        if (!(hero instanceof FactionHero p) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, cleanup);
            return;
        }
        if (flat(hero, tx, tz) <= range + 2 || flat(foe, tx, tz) >= range - 1) {
            HeroAiGameTests.finish(helper, List.of(String.format("setup: hero %.1f and enemy %.1f from the tower (range %d)",
                flat(hero, tx, tz), flat(foe, tx, tz), range)), mobs, cleanup);
            return;
        }
        HeroAiGameTests.learn(p, 0);   // Shield Charge
        FactionHeroAbility judgement = HeroAiGameTests.learn(p, 1);
        double[] closest = { Double.MAX_VALUE };
        boolean[] cast = { false };
        for (int t = 1; t <= 200; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                closest[0] = Math.min(closest[0], flat(hero, tx, tz));
                cast[0] |= !judgement.isOffCooldown(p);
                if (tick < 200)
                    return;
                List<String> problems = new ArrayList<>();
                if (closest[0] <= range)
                    problems.add(String.format("the hero went %.1f blocks from an enemy raid tower that reaches %d", closest[0], range));
                if (!cast[0])
                    problems.add("the hero never struck the enemy from outside the tower's reach (Judgement reached it)");
                HeroAiGameTests.finish(helper, problems, mobs, cleanup);
            });
        }
    }

    // ------------------------------------------------------------------------------------------ combos

    /**
     * Combo order: a gap closer only when its follow-up is ready, and the stun before the damage. With Judgement on
     * cooldown the hero keeps Shield Charge; once Judgement is back it charges (the stun) and Judgement lands on the
     * same enemy while it is still stunned. Main: the brain charges at once, Judgement or not. The hero's legs are
     * held (speed 0) so that only the brain moves it.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_combo", timeoutTicks = 260)
    public static void hero_ai_v2_combo_gap_closer_waits_for_its_follow_up_then_stuns_before_the_damage(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_combo"), them = me + "_foe";
        Mob hero = paladin(helper, me, 4, 3);
        Mob foe = dummy(helper, them, 4, 10);
        List<Mob> mobs = mobs(hero, foe);
        if (!(hero instanceof FactionHero p) || foe == null) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        AttributeInstance speed = hero.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
            speed.setBaseValue(0);
        FactionHeroAbility charge = HeroAiGameTests.learn(p, 0);
        charge.setRank(p, 2);          // rank 2: a 0.75 s stun
        charge.updateStatsForRank(p);
        FactionHeroAbility judgement = HeroAiGameTests.learn(p, 1);
        judgement.setCooldown(70, (Unit) hero);   // back at about tick 70
        List<String> problems = new ArrayList<>();
        int[] chargedAt = { -1 }, judgedAt = { -1 };
        boolean[] stunnedThen = { false }, judgementBack = { false };
        helper.runAfterDelay(60, () -> {
            if (!charge.isOffCooldown(p))
                problems.add("Shield Charge was spent while Judgement, its follow-up, was still on cooldown");
        });
        for (int t = 61; t <= 220; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (chargedAt[0] < 0 && !charge.isOffCooldown(p))
                    chargedAt[0] = tick;
                // the test put Judgement on cooldown: its cast is the cooldown starting again once it was back
                judgementBack[0] |= judgement.isOffCooldown(p);
                if (judgedAt[0] < 0 && judgementBack[0] && !judgement.isOffCooldown(p)) {
                    judgedAt[0] = tick;
                    stunnedThen[0] = foe.hasEffect(MobEffectRegistrar.STUN.get());
                }
                if (tick < 220)
                    return;
                if (chargedAt[0] < 0)
                    problems.add("Shield Charge was never cast once Judgement was ready");
                else if (judgedAt[0] < 0)
                    problems.add("Judgement never followed the charge");
                else if (judgedAt[0] <= chargedAt[0])
                    problems.add("Judgement went before the charge's stun (tick " + judgedAt[0] + " vs " + chargedAt[0] + ")");
                else if (judgedAt[0] - chargedAt[0] > 25)
                    problems.add("Judgement came " + (judgedAt[0] - chargedAt[0]) + " ticks after the charge: not a combo");
                else if (!stunnedThen[0])
                    problems.add("Judgement landed after the charge's stun had worn off");
                HeroAiGameTests.finish(helper, problems, mobs, null);
            });
        }
    }

    // ------------------------------------------------------------------------------------------ select (D030)

    /**
     * D030's other half: "once the player selects ... it again, the player's order wins". The hero breaks off its move
     * order near death and runs; the player selects it (HeroBot.onPlayerSelect is what the server runs for the client's
     * HERO_SELECTED) and the hero goes back to the order it had - past the enemies, at the same low health. Written
     * before the select code; it calls the server's entry point for a select, which main does not have.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_select", timeoutTicks = 400)
    public static void hero_ai_v2_selecting_a_fleeing_hero_hands_it_back_to_its_order(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = HeroGameTests.owner(helper, "v2_select"), them = me + "_foe";
        Mob hero = paladin(helper, me, 8, 1);
        List<Mob> mobs = mobs(hero);
        for (int[] xz : new int[][] { { 5, 11 }, { 11, 11 }, { 5, 13 }, { 11, 13 } })
            mobs.add(dummy(helper, them, xz[0], xz[1]));
        if (!(hero instanceof FactionHero p) || mobs.contains(null)) {
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return;
        }
        HeroAiGameTests.learn(p, 1);
        BlockPos dest = at(helper, 8, 15);
        double front = helper.absolutePos(new BlockPos(0, 2, 11)).getZ();
        double[] deepest = { Double.NEGATIVE_INFINITY };
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(2, () -> order(helper, me, hero, UnitAction.MOVE, dest));
        helper.runAfterDelay(25, () -> hero.setHealth(hero.getMaxHealth() * 0.25f));
        for (int t = 26; t <= 130; t++)
            helper.runAfterDelay(t, () -> deepest[0] = Math.max(deepest[0], hero.getZ()));
        helper.runAfterDelay(130, () -> {
            if (deepest[0] >= front || hero.getZ() > deepest[0] - 1.5)
                problems.add("setup: the hero did not break off its order to flee at a quarter health");
            HeroBot.onPlayerSelect(hero);   // the player clicks it
        });
        // back to its order = it got there; once there the order is done and it may turn on the enemies beside the spot
        double[] closest = { Double.MAX_VALUE };
        for (int t = 131; t <= 360; t++)
            helper.runAfterDelay(t, () -> closest[0] = Math.min(closest[0], flat(hero, dest.getX() + 0.5, dest.getZ() + 0.5)));
        helper.runAfterDelay(360, () -> {
            if (hero.isAlive() && closest[0] > 1.5)
                problems.add(String.format("selected, the hero did not go back to its move order (%.1f blocks short at best)",
                    closest[0]));
            HeroAiGameTests.finish(helper, problems, mobs, null);
        });
    }

    // ------------------------------------------------------------------------------------------ a bot's hero

    /**
     * A bot's Dawn Paladin near death with no heal: it runs from the enemy and rests out of reach. Nobody will heal it
     * (the Paladin has no heal, mobs do not regenerate) and no player will give the word, so its owner's attack order
     * must take it out of the rest. Mode A (Oct 28, item 1): every bot army order was wiped while the hero rested, so it
     * stood there for the rest of the match. Returns the bot's name (for the cleanup) or null when setup failed.
     */
    static String botPaladinRests(GameTestHelper helper, String tag, List<Mob> mobs) {
        String bot = HeroGameTests.owner(helper, tag), them = bot + "_foe";
        BotPlayer brain = new BotPlayer(bot, Factions.VILLAGERS, BotPlayer.Difficulty.PASSIVE, helper.absolutePos(new BlockPos(8, 2, 1)));
        brain.enemyFilter = o -> false;
        BotServerEvents.brains.put(bot, brain);
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, bot, 8, 10);
        Mob foe = dummy(helper, them, 8, 15);
        mobs.add(hero);
        mobs.add(foe);
        if (!(hero instanceof FactionHero) || foe == null) {
            BotServerEvents.brains.remove(bot);
            HeroAiGameTests.finish(helper, List.of("could not spawn the test units"), mobs, null);
            return null;
        }
        return bot;
    }

    /**
     * The last word of a bot test, a few ticks before its timeout: if nothing has finished it, it fails here, through
     * finish, so the test bot leaves BotServerEvents.brains (a framework timeout runs no cleanup, and the bot would think
     * on for the rest of the run).
     */
    static void botGuard(GameTestHelper helper, int tick, boolean[] done, List<Mob> mobs, Runnable cleanup) {
        helper.runAfterDelay(tick, () -> {
            if (done[0])
                return;
            done[0] = true;
            HeroAiGameTests.finish(helper, List.of("timed out before the test could decide (the test bot is unregistered)"),
                mobs, cleanup);
        });
    }

    static HeroBrain.State state(Mob hero) {
        HeroBrain b = ((FactionHero) hero).heroCore().brainIfAny();
        return b == null ? HeroBrain.State.OFF : b.state;
    }

    /** The bot's attack order takes its resting hero out of the rest: the hero walks to where its army is sent. */
    @GameTest(template = ARENA, batch = "hero_ai_v2_bot_order", timeoutTicks = 400)
    public static void hero_ai_v2_a_resting_bot_hero_follows_its_bot_order(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Mob> mobs = new ArrayList<>();
        String bot = botPaladinRests(helper, "v2_bot_order", mobs);
        if (bot == null)
            return;
        Mob hero = mobs.get(0);
        long[] restAt = { -1 };
        double[] spot = new double[2];
        double[] moved = { 0 };
        boolean[] done = { false };
        Runnable cleanup = () -> BotServerEvents.brains.remove(bot);
        helper.runAfterDelay(2, () -> hero.setHealth(hero.getMaxHealth() * 0.25f));
        // every check is scheduled here, from the top (a runnable that schedules more breaks the test's runnable map);
        // a rest seen by tick 260 leaves the order its 120 ticks before the guard at 390
        for (int t = 3; t <= 385; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                if (restAt[0] < 0 && state(hero) == HeroBrain.State.REST) {
                    restAt[0] = tick;
                    spot[0] = hero.getX();
                    spot[1] = hero.getZ();
                    // the bot sends its army to the far side of the arena, away from the enemy (no new threat on the way)
                    int x = hero.getX() < helper.absolutePos(new BlockPos(8, 2, 0)).getX() ? 14 : 1;
                    order(helper, bot, hero, UnitAction.ATTACK_MOVE, at(helper, x, 1));
                } else if (restAt[0] >= 0) {
                    moved[0] = Math.max(moved[0], flat(hero, spot[0], spot[1]));
                    if (moved[0] >= 3 || tick - restAt[0] >= 120) {
                        done[0] = true;
                        List<String> problems = new ArrayList<>();
                        if (moved[0] < 3)
                            problems.add(String.format("the resting bot hero ignored its bot's attack order (moved %.1f blocks in %d ticks, state %s)",
                                moved[0], tick - restAt[0], state(hero)));
                        HeroAiGameTests.finish(helper, problems, mobs, cleanup);
                    }
                } else if (tick == 260) {
                    done[0] = true;
                    HeroAiGameTests.finish(helper, List.of("setup: the bot hero at a quarter health never fled and rested (state "
                        + state(hero) + ")"), mobs, cleanup);
                }
            });
        }
        botGuard(helper, 390, done, mobs, cleanup);
    }

    /**
     * With no order and no heal, a bot's resting hero does not rest forever: after HeroBot's BOT_REST_TICKS it goes back
     * to its order (here: none) by itself, and a threat seen again would start a new run.
     */
    @GameTest(template = ARENA, batch = "hero_ai_v2_bot_rest", timeoutTicks = 900)
    public static void hero_ai_v2_a_bot_hero_does_not_rest_forever(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Mob> mobs = new ArrayList<>();
        String bot = botPaladinRests(helper, "v2_bot_rest", mobs);
        if (bot == null)
            return;
        Mob hero = mobs.get(0);
        long[] restAt = { -1 };
        boolean[] done = { false };
        Runnable cleanup = () -> BotServerEvents.brains.remove(bot);
        helper.runAfterDelay(2, () -> hero.setHealth(hero.getMaxHealth() * 0.25f));
        int limit = 20 * 20 + 60;   // BOT_REST_TICKS and a few thinks
        for (int t = 3; t <= 840; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                HeroBrain.State s = state(hero);
                if (restAt[0] < 0) {
                    if (s == HeroBrain.State.REST)
                        restAt[0] = tick;
                    else if (tick >= 360) {
                        done[0] = true;
                        HeroAiGameTests.finish(helper, List.of("setup: the bot hero at a quarter health never fled and rested (state "
                            + s + ")"), mobs, cleanup);
                    }
                } else if (s != HeroBrain.State.REST) {
                    done[0] = true;
                    HeroAiGameTests.finish(helper, new ArrayList<>(), mobs, cleanup);
                } else if (tick - restAt[0] >= limit) {
                    done[0] = true;
                    HeroAiGameTests.finish(helper, List.of(String.format("the bot hero rested %d ticks with no heal and no order (it should go back by itself)",
                        tick - restAt[0])), mobs, cleanup);
                }
            });
        }
        botGuard(helper, 880, done, mobs, cleanup);
    }
}
