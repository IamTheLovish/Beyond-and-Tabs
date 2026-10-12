package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.CommanderDGun;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.onboarding.OnboardingServer;
import com.solegendary.reignofnether.onboarding.OnboardingSession;
import com.solegendary.reignofnether.onboarding.OnboardingStep;
import com.solegendary.reignofnether.onboarding.PassiveBot;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of the First Battle tutorial's server side (onboarding/, design/onboarding.md): the step machine
 * (client reports only finish client steps, skip, save / load for Resume), and that each server step finishes on the
 * real thing - the orders a click and a formation drag actually send, finished buildings of the right kind for every
 * faction, two trained fighters, a worker reclaiming a wreck, a fired D-gun and a dead enemy commander - plus the
 * scenario pieces that can run without forming a battlefield (the passive sparring brain, the bot's site, the wreck
 * field). Every test owns its own names and session object (no global session, no global counts), so they run in
 * parallel with the rest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class OnboardingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String what) {
        return "gametest_onb_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob spawn(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = type.create(helper.getLevel());
        if (m == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ((Unit) m).setOwnerName(owner);
        helper.getLevel().addFreshEntity(m);
        return m;
    }

    /** The step machine: Continue and camera/selection reports advance only their own step; skip; Resume round-trip. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void onboarding_step_machine_reports_skips_and_resumes(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        OnboardingSession s = new OnboardingSession(owner(helper, "steps"));
        if (s.step != OnboardingStep.WELCOME)
            problems.add("a new session should start at WELCOME, is " + s.step);
        if (!s.clientReport(OnboardingStep.WELCOME) || s.step != OnboardingStep.ZOOM)
            problems.add("Continue on WELCOME should move to ZOOM, is " + s.step);
        if (s.clientReport(OnboardingStep.PAN) || s.step != OnboardingStep.ZOOM)
            problems.add("a report for a later step must be ignored");
        s.clientReport(OnboardingStep.ZOOM);
        s.clientReport(OnboardingStep.PAN);
        s.clientReport(OnboardingStep.ROTATE);
        s.clientReport(OnboardingStep.OVERVIEW);
        s.clientReport(OnboardingStep.SELECT);
        s.clientReport(OnboardingStep.BOX_SELECT);
        if (s.step != OnboardingStep.MOVE)
            problems.add("the camera and selection reports should reach MOVE, at " + s.step);
        if (s.clientReport(OnboardingStep.MOVE) || s.step != OnboardingStep.MOVE)
            problems.add("a client must not be able to finish a server step (MOVE)");
        if (!s.skip() || s.step != OnboardingStep.FORMATION)
            problems.add("skip should move MOVE -> FORMATION, is " + s.step);
        s.enemy = "Rival";
        s.home = new BlockPos(10, 64, -20);
        s.enemyHome = new BlockPos(110, 66, -20);
        OnboardingSession back = OnboardingSession.load(s.save());
        if (back.step != OnboardingStep.FORMATION || !back.enemy.equals("Rival") || !back.home.equals(s.home)
                || !back.enemyHome.equals(s.enemyHome) || !back.student.equals(s.student))
            problems.add("save/load lost state: " + back.step + " " + back.enemy + " " + back.home + " " + back.enemyHome);
        if (OnboardingStep.byName("NO_SUCH_STEP") != OnboardingStep.WELCOME)
            problems.add("an unknown saved step should resume at WELCOME");
        s.setStep(OnboardingStep.DONE);
        if (s.skip() || s.clientReport(OnboardingStep.DONE) || s.step != OnboardingStep.DONE)
            problems.add("DONE must stay DONE");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * MOVE finishes on a move order; FORMATION only on what a drag sends (single-unit orders to two different spots in
     * one burst), not on a plain right click (one order for the whole selection) nor on two clicks seconds apart;
     * ATTACK_MOVE only on an attack-move.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void onboarding_orders_finish_move_formation_and_attack_move(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        OnboardingSession s = new OnboardingSession(owner(helper, "orders"));
        BlockPos a = new BlockPos(5, 64, 5), b = new BlockPos(8, 64, 5);
        s.setStep(OnboardingStep.MOVE);
        if (s.onOrder(UnitAction.STOP, 3, a, 10))
            problems.add("STOP finished MOVE");
        if (s.onOrder(UnitAction.MOVE, 0, a, 10))
            problems.add("an order with no units left (ownership filter) finished MOVE");
        if (!s.onOrder(UnitAction.MOVE, 3, a, 10) || s.step != OnboardingStep.FORMATION)
            problems.add("a move should finish MOVE, at " + s.step);
        if (s.onOrder(UnitAction.MOVE, 4, a, 20))
            problems.add("a plain right click (one order, 4 units) finished FORMATION");
        if (s.onOrder(UnitAction.MOVE, 1, a, 100) || s.onOrder(UnitAction.MOVE, 1, a, 100))
            problems.add("two slot orders to the same spot finished FORMATION");
        if (s.onOrder(UnitAction.MOVE, 1, b, 140))
            problems.add("slot orders seconds apart finished FORMATION");
        if (!s.onOrder(UnitAction.MOVE, 1, a, 141) || s.step != OnboardingStep.EXTRACTOR)
            problems.add("a drag burst (two slots, two spots, one tick) should finish FORMATION, at " + s.step);
        s.setStep(OnboardingStep.ATTACK_MOVE);
        if (s.onOrder(UnitAction.MOVE, 2, a, 200))
            problems.add("a plain move finished ATTACK_MOVE");
        if (!s.onOrder(UnitAction.ATTACK_MOVE, 2, a, 201) || s.step != OnboardingStep.RECLAIM)
            problems.add("an attack-move should finish ATTACK_MOVE, at " + s.step);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * For every faction with a bot kit: its extractor, energy building and T1 lab each count for their step only (the
     * capitol for none), a placed-but-unfinished one marks the step in progress, and finishing it advances the step.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void onboarding_building_steps_finish_on_each_factions_finished_buildings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 2));
        int i = 0;
        for (Faction f : ReignOfNetherRegistries.FACTIONS) {
            BotPlayer.Kit kit = BotPlayer.kitFor(f);
            if (kit == null || kit.extractor() == null || kit.wind() == null || kit.armyBuilding() == null)
                continue;
            i++;
            String me = owner(helper, "build" + i);
            BuildingPlacement mex = place(level, kit.extractor(), corner, i, 0, me);
            BuildingPlacement wind = place(level, kit.wind(), corner, i, 1, me);
            BuildingPlacement lab = place(level, kit.armyBuilding(), corner, i, 2, me);
            BuildingPlacement cap = place(level, kit.capitol(), corner, i, 3, me);
            if (mex == null || wind == null || lab == null || cap == null) {
                problems.add(f.getName() + ": could not make the placements");
                continue;
            }
            check(problems, f, "extractor", mex, OnboardingServer.Need.EXTRACTOR, true);
            check(problems, f, "extractor", mex, OnboardingServer.Need.ENERGY, false);
            check(problems, f, "extractor", mex, OnboardingServer.Need.LAB, false);
            check(problems, f, "energy", wind, OnboardingServer.Need.ENERGY, true);
            check(problems, f, "energy", wind, OnboardingServer.Need.EXTRACTOR, false);
            check(problems, f, "lab", lab, OnboardingServer.Need.LAB, true);
            check(problems, f, "lab", lab, OnboardingServer.Need.EXTRACTOR, false);
            for (OnboardingServer.Need n : OnboardingServer.Need.values())
                check(problems, f, "capitol", cap, n, false);

            OnboardingSession s = new OnboardingSession(me);
            s.setStep(OnboardingStep.EXTRACTOR);
            List<BuildingPlacement> mine = List.of(cap, mex, wind, lab);
            for (BuildingPlacement bp : mine)
                bp.isBuilt = false;
            cap.isBuilt = true;   // a finished capitol must not finish anything
            if (OnboardingServer.checkBuilding(s, mine, OnboardingServer.Need.EXTRACTOR) || !inProgress(s))
                problems.add(f.getName() + ": an unfinished extractor should mark EXTRACTOR in progress, not finish it");
            mex.isBuilt = true;
            if (!OnboardingServer.checkBuilding(s, mine, OnboardingServer.Need.EXTRACTOR) || s.step != OnboardingStep.ENERGY)
                problems.add(f.getName() + ": a finished extractor should finish EXTRACTOR");
            wind.isBuilt = true;
            if (!OnboardingServer.checkBuilding(s, mine, OnboardingServer.Need.ENERGY) || s.step != OnboardingStep.LAB)
                problems.add(f.getName() + ": a finished energy building should finish ENERGY");
            // someone else's finished lab is not the student's
            BuildingPlacement other = place(level, kit.armyBuilding(), corner, i, 4, me + "_other");
            if (other != null) {
                other.isBuilt = true;
                if (OnboardingServer.checkBuilding(s, List.of(other), OnboardingServer.Need.LAB))
                    problems.add(f.getName() + ": another player's lab finished the student's LAB step");
            }
            lab.isBuilt = true;
            if (!OnboardingServer.checkBuilding(s, mine, OnboardingServer.Need.LAB) || s.step != OnboardingStep.TRAIN)
                problems.add(f.getName() + ": a finished lab should finish LAB");
        }
        if (i < 3)
            problems.add("expected bot kits for at least 3 factions, found " + i);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // the placement objects only (not added to the world's building list), so nothing is built in the arena
    private static BuildingPlacement place(ServerLevel level, Building b, BlockPos corner, int row, int col, String owner) {
        if (b == null)
            return null;
        return BuildingUtils.getNewBuildingPlacement(b, level, corner.offset(col * 30, 0, row * 30), Rotation.NONE, owner, false);
    }

    private static boolean inProgress(OnboardingSession s) {
        return s.isInProgress() && s.step == OnboardingStep.EXTRACTOR;
    }

    private static void check(List<String> problems, Faction f, String what, BuildingPlacement bp, OnboardingServer.Need need, boolean want) {
        if (OnboardingServer.matches(bp, need) != want)
            problems.add(f.getName() + ": " + what + " (" + bp.getBuilding().name + ") " + (want ? "should" : "should not")
                + " count for " + need);
    }

    /** TRAIN: the first look fixes the baseline; two more fighters (not workers, not the commander) finish it. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void onboarding_train_step_finishes_on_two_new_fighters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "train");
        OnboardingSession s = new OnboardingSession(me);
        s.setStep(OnboardingStep.TRAIN);
        Mob worker = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), me, 3, 3);
        List<Mob> made = new ArrayList<>();
        if (worker != null)
            made.add(worker);
        helper.runAfterDelay(3, () -> {
            if (OnboardingServer.check(level, s)) {
                helper.fail("TRAIN finished before any fighter was trained");
                return;
            }
            made.add(spawn(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 5, 3));
        });
        helper.runAfterDelay(6, () -> {
            if (OnboardingServer.check(level, s)) {
                helper.fail("TRAIN finished on one fighter");
                return;
            }
            made.add(spawn(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 7, 3));
        });
        helper.runAfterDelay(9, () -> {
            boolean done = OnboardingServer.check(level, s);
            for (Mob m : made)
                if (m != null)
                    m.discard();
            if (!done || s.step != OnboardingStep.ATTACK_MOVE)
                helper.fail("two new fighters should finish TRAIN (fighters counted: " + OnboardingServer.countFighters(me)
                    + ", step " + s.step + ")");
            else
                helper.succeed();
        });
    }

    /** RECLAIM finishes once one of the student's workers is stripping a wreck - the state an area reclaim leaves. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void onboarding_reclaim_step_finishes_when_a_worker_reclaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "reclaim");
        OnboardingSession s = new OnboardingSession(me);
        s.setStep(OnboardingStep.RECLAIM);
        Mob worker = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), me, 4, 4);
        BlockPos at = helper.absolutePos(new BlockPos(6, 2, 4));
        Entity wreck = WreckServerEvents.spawnWreck(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 40f, Factions.VILLAGERS);
        if (worker == null || wreck == null) {
            helper.fail("could not spawn the worker or the wreck");
            return;
        }
        helper.runAfterDelay(3, () -> {
            if (OnboardingServer.check(level, s)) {
                helper.fail("RECLAIM finished with no worker reclaiming");
                return;
            }
            WreckServerEvents.setReclaimTarget(worker, wreck);
            boolean done = OnboardingServer.check(level, s);
            WreckServerEvents.clearReclaimTarget(worker);
            worker.discard();
            wreck.discard();
            if (!done || s.step != OnboardingStep.DGUN)
                helper.fail("a reclaiming worker should finish RECLAIM, at " + s.step);
            else
                helper.succeed();
        });
    }

    /** DGUN finishes once the student's commander's D-gun is on cooldown (a shot that was actually fired). */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void onboarding_dgun_step_finishes_when_the_dgun_fires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "dgun");
        OnboardingSession s = new OnboardingSession(me);
        s.setStep(OnboardingStep.DGUN);
        Mob com = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), me, 4, 6);
        if (com == null) {
            helper.fail("could not spawn the commander");
            return;
        }
        CommanderServerEvents.makeCommander(com);
        helper.runAfterDelay(3, () -> {
            if (OnboardingServer.check(level, s)) {
                helper.fail("DGUN finished before the D-gun was fired");
                return;
            }
            Ability dgun = null;
            for (Ability a : ((Unit) com).getAbilities().get())
                if (a instanceof CommanderDGun)
                    dgun = a;
            if (dgun == null) {
                com.discard();
                helper.fail("the commander has no D-gun");
                return;
            }
            dgun.setToMaxCooldown((Unit) com);   // what a fired shot leaves behind
            boolean done = OnboardingServer.check(level, s);
            com.discard();
            if (!done || s.step != OnboardingStep.KILL_COMMANDER)
                helper.fail("a fired D-gun should finish DGUN, at " + s.step);
            else
                helper.succeed();
        });
    }

    /**
     * KILL_COMMANDER: the enemy commander is found (and marked), and once it dies the step finishes; a missing commander
     * that was never seen while the enemy is still in the match does not finish it (an unloaded entity is not a kill).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void onboarding_kill_step_finishes_when_the_enemy_commander_dies(GameTestHelper helper) {
        String foe = owner(helper, "foe");
        OnboardingSession s = new OnboardingSession(owner(helper, "killer"));
        s.enemy = foe;
        s.setStep(OnboardingStep.KILL_COMMANDER);
        if (s.onEnemyCommander(false, true)) {
            helper.fail("a never-seen commander finished the step");
            return;
        }
        Mob com = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 8, 8);
        if (com == null) {
            helper.fail("could not spawn the enemy commander");
            return;
        }
        CommanderServerEvents.makeCommander(com);
        helper.runAfterDelay(3, () -> {
            if (OnboardingServer.commanderOf(foe) != com) {
                helper.fail("commanderOf did not find the enemy commander");
                return;
            }
            if (s.onEnemyCommander(true, true)) {
                helper.fail("a living enemy commander finished the step");
                return;
            }
            com.kill();
        });
        helper.runAfterDelay(8, () -> {
            boolean alive = OnboardingServer.commanderOf(foe) != null;
            if (alive) {
                helper.fail("the killed commander is still found");
                return;
            }
            if (!s.onEnemyCommander(false, true) || s.step != OnboardingStep.DONE)
                helper.fail("the enemy commander's death should finish KILL_COMMANDER, at " + s.step);
            else
                helper.succeed();
        });
    }

    /**
     * Scenario pieces that need no battlefield: the sparring partner's brain never builds, it plays another faction
     * than the student, its site is a short walk away, and the RECLAIM step lays six wrecks where it says.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void onboarding_scenario_pieces_passive_bot_site_and_wreck_field(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        String bot = owner(helper, "rival");
        BlockPos home = helper.absolutePos(new BlockPos(8, 1, 8));
        PassiveBot brain = new PassiveBot(bot, Factions.MONSTERS, home);
        for (int t = 0; t < 5; t++)
            brain.think(level, level.getGameTime() + t * 20L);
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
            if (bot.equals(bp.ownerName))
                problems.add("the passive bot placed " + bp.getBuilding().name);
        if (OnboardingServer.sparringFaction(Factions.VILLAGERS) != Factions.MONSTERS
                || OnboardingServer.sparringFaction(Factions.MONSTERS) != Factions.VILLAGERS
                || OnboardingServer.sparringFaction(Factions.PIGLINS) != Factions.MONSTERS)
            problems.add("the sparring bot should play Gravebound, or the Kingdom against Gravebound");
        Vec3 site = OnboardingServer.enemySite(level, home);
        double flat = Math.sqrt((site.x - home.getX()) * (site.x - home.getX()) + (site.z - home.getZ()) * (site.z - home.getZ()));
        if (flat < 70 || flat > 125)
            problems.add("the sparring site is " + (int) flat + " blocks away, expected ~80-110");
        BlockPos toward = home.offset(100, 0, 0);
        BlockPos centre = OnboardingServer.spawnWreckField(level, home, toward, Factions.MONSTERS);
        if (Math.abs(centre.getX() - home.getX() - 14) > 1 || Math.abs(centre.getZ() - home.getZ()) > 1)
            problems.add("the wreck field should sit 14 blocks toward the enemy, at " + centre);
        helper.runAfterDelay(3, () -> {
            List<Entity> near = new ArrayList<>();
            for (Entity w : WreckServerEvents.getWrecks())
                if (!w.isRemoved() && w.distanceToSqr(centre.getX() + 0.5, w.getY(), centre.getZ() + 0.5) <= 16)
                    near.add(w);
            if (near.size() < 6)
                problems.add("expected 6 wrecks round the field's centre, found " + near.size());
            for (Entity w : near)
                w.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
