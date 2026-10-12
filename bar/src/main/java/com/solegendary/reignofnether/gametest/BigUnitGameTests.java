package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.gameender.GameEnderPlacement;
import com.solegendary.reignofnether.building.gameender.NecropolisPlacement;
import com.solegendary.reignofnether.building.gameender.SunSpirePlacement;
import com.solegendary.reignofnether.building.gameender.WardSpirePlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of the big units and game enders (design/big_units.md). Their own holder next to SkirmishGameTests
 * (same arena template, unique owners, no global counts); every test runs in parallel with the rest. Game-ender
 * placements are never put in the world: each test hands them its own building pool, so no other test's buildings
 * can be aimed at or intercept for them.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BigUnitGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    // ---- game enders -------------------------------------------------------------------------------------------------

    /**
     * A game ender is announced on its first server tick, before it is built; once built it charges through its three
     * stages (and not at all while its owner lacks Tier 3); raids knock charge off a stage per 10% of max HP; with
     * nobody to hit it waits at full charge. Also: both game enders are T2-builder-only, belong to their factions and
     * ship their structures in data/, and every faction has a Ward Spire.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void game_ender_announces_on_start_charges_through_stages_and_loses_charge_to_raids(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "ender_stages");
        List<String> problems = new ArrayList<>();
        if (!com.solegendary.reignofnether.unit.T2Workers.needsT2Builder(Buildings.SUN_SPIRE)
                || !com.solegendary.reignofnether.unit.T2Workers.needsT2Builder(Buildings.NECROPOLIS))
            problems.add("game enders must need a T2 constructor");
        if (!Factions.VILLAGERS.equals(Buildings.SUN_SPIRE.getFaction()) || !Factions.MONSTERS.equals(Buildings.NECROPOLIS.getFaction()))
            problems.add("the Sun Spire is Sunforged and the Necropolis Gravebound");
        var wards = List.of(Buildings.WARD_SPIRE_VILLAGERS, Buildings.WARD_SPIRE_MONSTERS, Buildings.WARD_SPIRE_PIGLINS,
            Buildings.WARD_SPIRE_VERDANT, Buildings.WARD_SPIRE_TIDE, Buildings.WARD_SPIRE_STORM, Buildings.WARD_SPIRE_BRASS);
        var factions = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT, Factions.STORMCALLERS, Factions.BRASSWORKS);
        for (int i = 0; i < wards.size(); i++)
            if (!factions.get(i).equals(wards.get(i).getFaction()))
                problems.add("Ward Spire " + i + " is not registered to " + factions.get(i).getName());
        for (String nbt : List.of("sun_spire", "necropolis", "ward_spire"))
            if (com.solegendary.reignofnether.building.BuildingBlockData.getBuildingNbt(nbt, level.getServer().getResourceManager()) == null)
                problems.add("the structure " + nbt + " is missing from data/");

        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.SUN_SPIRE, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof SunSpirePlacement spire)) {
            helper.fail("the Sun Spire is not a SunSpirePlacement: " + bp);
            return;
        }
        spire.pool = new ArrayList<>();   // nobody to aim at
        spire.stageTicks = 20;
        ResearchServerEvents.removeAllResearchFor(owner);
        try {
            spire.tickGameEnder(level);
            if (!spire.announced)
                problems.add("the game ender was not announced on its first tick");
            if (spire.stage != 0 || spire.stageProgress != 0)
                problems.add("an unbuilt game ender charged");
            spire.isBuilt = true;
            for (int t = 0; t < 30; t++)
                spire.tickGameEnder(level);
            if (spire.charge() > 0)
                problems.add("the game ender charged without Tier 3");
            ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_3);
            for (int t = 0; t < 19; t++)
                spire.tickGameEnder(level);
            if (spire.stage != 0)
                problems.add("stage 1 came early: " + spire.stage);
            spire.tickGameEnder(level);
            if (spire.stage != 1)
                problems.add("stage 1 should complete after stageTicks: " + spire.stage);
            for (int t = 0; t < 40; t++)
                spire.tickGameEnder(level);
            if (spire.stage != GameEnderPlacement.STAGES || spire.telegraph != 0)
                problems.add("with nobody to hit it should wait fully charged (stage " + spire.stage + ", telegraph " + spire.telegraph + ")");
            // raids: one step of max HP takes one stage's worth off, a big hit resets it
            int step = (int) Math.ceil(spire.getMaxHealth() * GameEnderPlacement.RAID_STEP);
            spire.stage = 2;
            spire.stageProgress = 5;
            spire.onRaided(step);
            if (spire.stage != 1 || spire.stageProgress != 5)
                problems.add("a 10% raid should knock one stage off (stage " + spire.stage + ", progress " + spire.stageProgress + ")");
            spire.onRaided(step / 2);
            if (spire.stage != 1)
                problems.add("half a raid step should not cost a stage yet");
            spire.onRaided(step * 5);
            if (spire.stage != 0 || spire.stageProgress != 0)
                problems.add("a heavy raid should reset the charge, never below zero");
        } finally {
            ResearchServerEvents.removeAllResearchFor(owner);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The Sun Spire aims at the enemy capitol over a nearer enemy building; its strike is intercepted by the victim's
     * charged Ward Spire within range (the ward's charge is spent, nobody is hurt), but not by a ward out of range or one
     * of a stranger; with the ward spent the next strike lands: the enemy at the target is hurt, a friend standing there
     * is not.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void sun_spire_strike_lands_unless_a_charged_ward_spire_intercepts_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "sunspire"), foeOwner = owner + "_foe", stranger = owner + "_stranger";
        List<String> problems = new ArrayList<>();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var spire = (SunSpirePlacement) BuildingUtils.getNewBuildingPlacement(Buildings.SUN_SPIRE, level, origin, Rotation.NONE, owner, false);
        var near = BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_VILLAGERS, level, origin.offset(6, 0, 0), Rotation.NONE, foeOwner, false);
        var capitol = BuildingUtils.getNewBuildingPlacement(Buildings.TOWN_CENTRE, level, origin.offset(30, 0, 0), Rotation.NONE, foeOwner, false);   // a capitol: TownCentrePlacement
        spire.pool = new ArrayList<>(List.of(near, capitol));
        if (!spire.beginStrike(level) || !capitol.centrePos.equals(spire.strikePos) || !foeOwner.equals(spire.strikeVictim))
            problems.add("the spire should aim at the enemy capitol, not the nearer building (aimed at " + spire.strikePos + ")");
        spire.telegraph = 0;

        BlockPos target = helper.absolutePos(new BlockPos(4, 2, 4));
        var ward = (WardSpirePlacement) BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_MONSTERS, level, target.offset(14, 0, 0), Rotation.NONE, foeOwner, false);
        var farWard = (WardSpirePlacement) BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_MONSTERS, level, target.offset(WardSpirePlacement.RANGE + 20, 0, 0), Rotation.NONE, foeOwner, false);
        var strangerWard = (WardSpirePlacement) BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_PIGLINS, level, target.offset(0, 0, 13), Rotation.NONE, stranger, false);
        // every ward stands outside the strike's radius: the strike's building damage must not touch test placements
        for (var w : List.of(ward, farWard, strangerWard)) {
            w.isBuilt = true;
            w.charges = 1;
        }
        spire.pool = new ArrayList<>(List.of(farWard, strangerWard));
        if (spire.interceptor(target, foeOwner) != null)
            problems.add("a ward out of range or a stranger's ward must not intercept");
        spire.pool = new ArrayList<>(List.of(ward, farWard, strangerWard));
        if (spire.interceptor(target, foeOwner) != ward)
            problems.add("the victim's charged ward in range should intercept");

        var type = EntityRegistrar.VILLAGER_UNIT.get();
        Mob foe = type.create(level), friend = type.create(level);
        if (foe == null || friend == null) {
            helper.fail("could not create the test units");
            return;
        }
        foe.moveTo(target.getX() + 1.5, target.getY(), target.getZ() + 0.5, 0, 0);
        friend.moveTo(target.getX() - 1.5, target.getY(), target.getZ() + 0.5, 0, 0);
        ((Unit) foe).setOwnerName(foeOwner);
        ((Unit) friend).setOwnerName(owner);
        level.addFreshEntity(foe);
        level.addFreshEntity(friend);
        try {
            float f0 = foe.getHealth(), m0 = friend.getHealth();
            spire.aimAt(level, target, foeOwner);
            for (int t = 0; t < GameEnderPlacement.TELEGRAPH_TICKS - 1; t++) {
                spire.isBuilt = true;
                spire.tickGameEnder(level);
            }
            if (foe.getHealth() < f0 || spire.strikesLanded + spire.strikesIntercepted != 0)
                problems.add("the strike resolved before its telegraph ended");
            spire.tickGameEnder(level);   // the last telegraph tick
            if (spire.strikesIntercepted != 1 || ward.charges != 0)
                problems.add("the charged ward should have intercepted (intercepted " + spire.strikesIntercepted + ", ward charges " + ward.charges + ")");
            if (foe.getHealth() < f0)
                problems.add("an intercepted strike hurt the enemy");
            if (spire.stage != 0 || spire.strikePos != null)
                problems.add("after a strike the charge should start over");
            spire.aimAt(level, target, foeOwner);
            spire.resolveStrike(level);
            if (spire.strikesLanded != 1)
                problems.add("with the ward spent the strike should land");
            if (foe.isAlive() && foe.getHealth() >= f0)
                problems.add("the strike did not hurt the enemy at the target");
            if (friend.getHealth() < m0)
                problems.add("the strike hurt a friend");
        } finally {
            foe.discard();
            friend.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The Necropolis raises its host at the target for its owner - ten risen plus one per wreck near the target (the
     * wrecks are eaten) - tagged risen (no wreck, crumbling); a charged Ward Spire of the victim stops the raise.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void necropolis_raises_a_host_at_the_target_unless_a_ward_spire_intercepts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "necro"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var necro = (NecropolisPlacement) BuildingUtils.getNewBuildingPlacement(Buildings.NECROPOLIS, level, origin, Rotation.NONE, owner, false);
        BlockPos target = helper.absolutePos(new BlockPos(5, 1, 5));
        var ward = (WardSpirePlacement) BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_VILLAGERS, level, target.offset(3, 1, 0), Rotation.NONE, foeOwner, false);
        ward.isBuilt = true;
        ward.charges = 1;
        necro.pool = new ArrayList<>(List.of(ward));
        List<Entity> wrecks = new ArrayList<>();
        try {
            necro.aimAt(level, target, foeOwner);
            necro.resolveStrike(level);
            if (necro.strikesIntercepted != 1 || necro.strikesLanded != 0 || !necro.lastRisen.isEmpty())
                problems.add("the ward should have stopped the raise");
            for (int i = 0; i < 2; i++) {
                Entity w = WreckServerEvents.spawnWreck(level, target.getX() + 0.5 + i, target.getY() + 1, target.getZ() + 2.5, 40, Factions.VILLAGERS);
                if (w != null)
                    wrecks.add(w);
            }
            if (wrecks.size() != 2) {
                helper.fail("could not lay the test wrecks");
                return;
            }
            necro.aimAt(level, target, foeOwner);
            necro.resolveStrike(level);
            int n = necro.lastRisen.size();
            if (necro.strikesLanded != 1 || n < NecropolisPlacement.BASE_RISEN + 2 || n > NecropolisPlacement.BASE_RISEN + NecropolisPlacement.MAX_WRECK_BONUS)
                problems.add("expected " + (NecropolisPlacement.BASE_RISEN + 2) + "+ risen, got " + n);
            for (Entity w : wrecks)
                if (!w.isRemoved())
                    problems.add("a wreck near the target was not eaten");
            for (Entity e : necro.lastRisen) {
                if (!(e instanceof Unit u) || !owner.equals(u.getOwnerName()))
                    problems.add("a risen does not belong to the Necropolis's owner");
                if (!NecropolisPlacement.isRisen(e))
                    problems.add("a risen is not tagged to crumble");
                if (e.distanceToSqr(target.getX() + 0.5, e.getY(), target.getZ() + 0.5) > 8 * 8)
                    problems.add("a risen stood up far from the target");
            }
        } finally {
            for (Entity e : necro.lastRisen)
                e.discard();
            for (Entity w : wrecks)
                if (!w.isRemoved())
                    w.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
