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

    // ---- T2 beasts ---------------------------------------------------------------------------------------------------

    /** Spawns a unit of {@code type} for {@code owner} at the helper-relative spot; null if it could not be created. */
    static Mob spawn(GameTestHelper helper, net.minecraft.world.entity.EntityType<? extends Mob> type, String owner, double x, double z) {
        Mob m = type.create(helper.getLevel());
        if (m == null)
            return null;
        BlockPos p = helper.absolutePos(new BlockPos(0, 2, 0));
        m.moveTo(p.getX() + x, p.getY(), p.getZ() + z, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        helper.getLevel().addFreshEntity(m);
        return m;
    }

    /**
     * The four beasts are trained at their factions' T2 labs, priced at ~2-2.5x their faction's T2 heavy, belong to
     * their factions, and the bots' T2 mixes use them.
     */
    @GameTest(template = ARENA)
    public static void t2_beasts_are_trained_at_the_t2_labs_and_priced_like_beasts(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        record Row(com.solegendary.reignofnether.building.production.ProductionBuilding lab, com.solegendary.reignofnether.building.production.ProductionItem beast,
                   com.solegendary.reignofnether.resources.ResourceCost heavy, com.solegendary.reignofnether.faction.Faction faction) { }
        var rows = List.of(
            new Row(Buildings.ARCANE_TOWER, ProductionItems.ORIFLAMME_ELEPHANT, com.solegendary.reignofnether.resources.ResourceCosts.RAVAGER, Factions.VILLAGERS),
            new Row(Buildings.FLAME_SANCTUARY, ProductionItems.GOREHORN_RHINO, com.solegendary.reignofnether.resources.ResourceCosts.WITHER_SKELETON, Factions.PIGLINS),
            new Row(Buildings.DUNGEON, ProductionItems.OSSUARY_BEHEMOTH, com.solegendary.reignofnether.resources.ResourceCosts.WARDEN, Factions.MONSTERS),
            new Row(Buildings.CIRCLE_OF_ELDERS, ProductionItems.MOSSBACK_BISON, com.solegendary.reignofnether.resources.ResourceCosts.STAG_LANCER, Factions.VERDANT_COURT));
        for (Row r : rows) {
            String n = r.beast().getItemName();
            if (!r.lab().productions.get().contains(r.beast()))
                problems.add(r.lab().name + " does not train " + n);
            var type = EntityRegistrar.getEntityType(n);
            if (type == null) {
                problems.add(n + " has no entity type");
                continue;
            }
            Entity probe = type.create(helper.getLevel());   // never added to the level
            if (!(probe instanceof Unit pu) || !r.faction().equals(Factions.getFaction(pu)))
                problems.add(n + " is not registered to " + r.faction().getName());
            if (probe != null)
                probe.discard();
            float ratio = (float) r.beast().defaultCost.metal() / Math.max(1, r.heavy().metal());
            if (ratio < 1.7f || ratio > 2.6f)
                problems.add(n + " costs " + ratio + "x its faction's T2 heavy (want ~2-2.5x)");
            var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(r.faction());
            if (kit == null || !kit.t2Army().contains(r.beast()))
                problems.add("the " + r.faction().getName() + " bot does not field the " + n);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Ossuary Behemoth: Grave Feast eats the nearest wreck in range (it is gone, not reclaimable), gains a Bone Stack
     * (+HP, +damage, a bigger hitbox), ignores wrecks out of range, and caps at MAX_STACKS.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void ossuary_behemoth_eats_wrecks_and_grows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "ossuary");
        List<String> problems = new ArrayList<>();
        var b = (com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit)
            spawn(helper, EntityRegistrar.OSSUARY_BEHEMOTH_UNIT.get(), owner, 4.5, 8.5);
        if (b == null) {
            helper.fail("could not create the Ossuary Behemoth");
            return;
        }
        List<Entity> wrecks = new ArrayList<>();
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        try {
            Entity w1 = WreckServerEvents.spawnWreck(level, o.getX() + 6.5, o.getY(), o.getZ() + 8.5, 40, Factions.VILLAGERS);
            Entity w2 = WreckServerEvents.spawnWreck(level, o.getX() + 4.5, o.getY(), o.getZ() + 11.5, 40, Factions.PIGLINS);
            Entity far = WreckServerEvents.spawnWreck(level, o.getX() + 14.5, o.getY(), o.getZ() + 14.5, 40, Factions.PIGLINS);
            for (Entity w : new Entity[] { w1, w2, far })
                if (w != null)
                    wrecks.add(w);
            if (wrecks.size() != 3) {
                helper.fail("could not lay the test wrecks");
                return;
            }
            float hp0 = b.getMaxHealth();
            double dmg0 = b.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            float w0 = b.getBbWidth();
            if (b.feast(level) != w1)
                problems.add("the behemoth should eat the nearest wreck first");
            if (b.getStacks() != 1 || !w1.isRemoved())
                problems.add("eating a wreck should give a stack and remove the wreck");
            if (b.feast(level) != w2 || b.getStacks() != 2)
                problems.add("the second wreck in range should be eaten too");
            if (b.feast(level) != null || b.getStacks() != 2 || far.isRemoved())
                problems.add("a wreck out of range must not be eaten");
            float hp = b.getMaxHealth();
            double dmg = b.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (Math.abs(hp - hp0 - 2 * com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit.HP_PER_STACK) > 0.01f)
                problems.add("two stacks should add 2 x HP_PER_STACK max HP (" + hp0 + " -> " + hp + ")");
            if (Math.abs(dmg - dmg0 - 2 * com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit.DAMAGE_PER_STACK) > 0.01)
                problems.add("two stacks should add 2 x DAMAGE_PER_STACK damage (" + dmg0 + " -> " + dmg + ")");
            if (b.getBbWidth() <= w0)
                problems.add("the hitbox should grow with the stacks");
            b.setStacks(99);
            if (b.getStacks() != com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit.MAX_STACKS)
                problems.add("stacks must cap at MAX_STACKS");
        } finally {
            b.discard();
            for (Entity w : wrecks)
                if (!w.isRemoved())
                    w.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Gorehorn Rhino: Breach Charge waits out its wind-up (nobody hurt), then ploughs down the lane - every enemy on it
     * is hit once, a friend on it and an enemy off it are not, and the rhino travels; a wall stops a second charge short.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void gorehorn_rhino_breach_charge_hits_the_lane_and_stops_at_a_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "rhino"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 2, 14, 14);
        var vill = EntityRegistrar.VILLAGER_UNIT.get();
        var rhino = (com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit)
            spawn(helper, EntityRegistrar.GOREHORN_RHINO_UNIT.get(), owner, 2.5, 8.5);
        Mob foeA = spawn(helper, vill, foeOwner, 5.5, 8.5), foeB = spawn(helper, vill, foeOwner, 9.5, 8.5);
        Mob friend = spawn(helper, vill, owner, 7.5, 8.5), off = spawn(helper, vill, foeOwner, 7.5, 13.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { rhino, foeA, foeB, friend, off })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 5) {
                helper.fail("could not create the Breach Charge test units");
                return;
            }
            float a0 = foeA.getHealth(), b0 = foeB.getHealth(), f0 = friend.getHealth(), o0 = off.getHealth();
            double x0 = rhino.getX();
            BlockPos aim = helper.absolutePos(new BlockPos(14, 2, 8));
            if (!rhino.startBreach(net.minecraft.world.phys.Vec3.atBottomCenterOf(aim)))
                problems.add("the charge did not start");
            if (rhino.startBreach(net.minecraft.world.phys.Vec3.atBottomCenterOf(aim)))
                problems.add("a second charge started while charging");
            for (int t = 0; t < com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit.BREACH_WINDUP_TICKS; t++)
                rhino.tickBreach(level);
            if (foeA.getHealth() < a0 || rhino.getX() - x0 > 0.5)
                problems.add("the rhino moved or hit during its wind-up");
            for (int t = 0; t < 40 && (rhino.breachSteps > 0 || rhino.breachWindup > 0); t++)
                rhino.tickBreach(level);
            if (foeA.getHealth() >= a0 || foeB.getHealth() >= b0)
                problems.add("the charge should hit both enemies in the lane (hits " + rhino.lastBreachHits + ")");
            if (friend.getHealth() < f0)
                problems.add("the charge hit a friend");
            if (off.getHealth() < o0)
                problems.add("the charge hit an enemy off the lane");
            if (rhino.getX() - x0 < 6)
                problems.add("the rhino should have travelled down the lane (moved " + (rhino.getX() - x0) + ")");
            // a wall: a second rhino's charge stops at it
            for (int y = 2; y <= 4; y++)
                for (int z = 5; z <= 12; z++)
                    level.setBlock(helper.absolutePos(new BlockPos(8, y, z)), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            var rhino2 = (com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit)
                spawn(helper, EntityRegistrar.GOREHORN_RHINO_UNIT.get(), owner, 3.5, 8.5);
            if (rhino2 == null) {
                helper.fail("could not create the second rhino");
                return;
            }
            all.add(rhino2);
            rhino2.startBreach(net.minecraft.world.phys.Vec3.atBottomCenterOf(aim));
            for (int t = 0; t < 40 && (rhino2.breachSteps > 0 || rhino2.breachWindup > 0); t++)
                rhino2.tickBreach(level);
            double wallX = helper.absolutePos(new BlockPos(8, 2, 8)).getX();
            if (rhino2.getX() + rhino2.getBbWidth() * 0.5 > wallX + 0.05)
                problems.add("the wall should stop the charge (rhino at " + rhino2.getX() + ", wall at " + wallX + ")");
            if (rhino2.breachSteps != 0)
                problems.add("a charge that hit a wall should end");
        } finally {
            for (Mob m : all)
                m.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Mossback Bison: Embark boards the nearest friendly infantry up to its four seats - never an enemy, never a body too
     * big for the grove; riders take half damage and heal; Eject drops them all.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void mossback_bison_carries_four_infantry_that_take_half_damage(GameTestHelper helper) {
        String owner = owner(helper, "bison"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var vill = EntityRegistrar.VILLAGER_UNIT.get();
        var bison = (com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit)
            spawn(helper, EntityRegistrar.MOSSBACK_BISON_UNIT.get(), owner, 7.5, 7.5);
        List<Mob> all = new ArrayList<>();
        List<Mob> friends = new ArrayList<>();
        if (bison != null)
            all.add(bison);
        for (int i = 0; i < 5; i++) {
            Mob m = spawn(helper, vill, owner, 4.5 + i * 1.5, 10.5);
            if (m != null) {
                all.add(m);
                friends.add(m);
            }
        }
        Mob foe = spawn(helper, vill, foeOwner, 8.5, 4.5);
        Mob golem = spawn(helper, EntityRegistrar.IRON_GOLEM_UNIT.get(), owner, 10.5, 6.5);
        for (Mob m : new Mob[] { foe, golem })
            if (m != null)
                all.add(m);
        try {
            if (bison == null || friends.size() != 5 || foe == null || golem == null) {
                helper.fail("could not create the Hollow Grove test units");
                return;
            }
            int boarded = bison.embarkNearby();
            if (boarded != com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit.SEATS || bison.getPassengers().size() != boarded)
                problems.add("Embark should fill all " + com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit.SEATS + " seats (boarded " + boarded + ")");
            if (foe.isPassenger() || golem.isPassenger())
                problems.add("an enemy or a golem boarded the grove");
            if (bison.embarkNearby() != 0)
                problems.add("a full bison boarded more");
            Mob rider = null;
            for (Mob m : friends)
                if (m.getVehicle() == bison)
                    rider = m;
            if (rider != null) {
                float h0 = rider.getHealth();
                rider.hurt(helper.getLevel().damageSources().generic(), 8f);
                float lost = h0 - rider.getHealth();
                if (Math.abs(lost - 8f * com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit.RIDER_DAMAGE_MULT) > 0.5f)
                    problems.add("a rider should take half damage (lost " + lost + " of 8)");
                float h1 = rider.getHealth();
                bison.healRiders();
                if (rider.getHealth() <= h1)
                    problems.add("riders should heal");
            }
            bison.ejectPassengers();
            if (!bison.getPassengers().isEmpty())
                problems.add("Eject should drop every rider");
        } finally {
            for (Mob m : all)
                m.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Oriflamme Elephant: friendly units within its aura deal x1.2 and take x0.85 (measured on real hits against a
     * control outside the aura); enemies near it and friends far from it are unaffected.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void oriflamme_elephant_aura_buffs_friends_near_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "oriflamme"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        var vill = EntityRegistrar.VILLAGER_UNIT.get();
        var elephant = (com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantUnit)
            spawn(helper, EntityRegistrar.ORIFLAMME_ELEPHANT_UNIT.get(), owner, 4.5, 4.5);
        Mob near = spawn(helper, vill, owner, 7.5, 6.5), far = spawn(helper, vill, owner, 4.5, 40.5);
        Mob foeNear = spawn(helper, vill, foeOwner, 6.5, 2.5), foeFar = spawn(helper, vill, foeOwner, 6.5, 40.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { elephant, near, far, foeNear, foeFar })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 5) {
                helper.fail("could not create the Oriflamme test units");
                return;
            }
            com.solegendary.reignofnether.unit.BigUnitServerEvents.trackElephant(elephant);
            if (com.solegendary.reignofnether.unit.BigUnitServerEvents.auraDamageMult(near) != com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantUnit.AURA_DAMAGE_MULT
                    || com.solegendary.reignofnether.unit.BigUnitServerEvents.auraTakenMult(near) != com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantUnit.AURA_TAKEN_MULT)
                problems.add("a friend in the aura should be buffed");
            if (com.solegendary.reignofnether.unit.BigUnitServerEvents.auraDamageMult(far) != 1f || com.solegendary.reignofnether.unit.BigUnitServerEvents.auraDamageMult(foeNear) != 1f)
                problems.add("a friend outside the aura or an enemy inside it must not be buffed");
            // real hits: the same foe type strikes a friend inside and one outside; friends strike the same foe type
            float n0 = near.getHealth(), f0 = far.getHealth();
            near.hurt(level.damageSources().mobAttack(foeFar), 10f);
            far.hurt(level.damageSources().mobAttack(foeFar), 10f);
            float lostNear = n0 - near.getHealth(), lostFar = f0 - far.getHealth();
            if (lostFar <= 0 || lostNear > lostFar * 0.9f || lostNear < lostFar * 0.8f)
                problems.add("a friend in the aura should take ~85% damage (" + lostNear + " vs " + lostFar + ")");
            float a0 = foeNear.getHealth(), b0 = foeFar.getHealth();
            foeNear.hurt(level.damageSources().mobAttack(near), 8f);
            foeFar.hurt(level.damageSources().mobAttack(far), 8f);
            float dealtNear = a0 - foeNear.getHealth(), dealtFar = b0 - foeFar.getHealth();
            if (dealtFar <= 0 || dealtNear < dealtFar * 1.1f || dealtNear > dealtFar * 1.3f)
                problems.add("a friend in the aura should deal ~120% damage (" + dealtNear + " vs " + dealtFar + ")");
        } finally {
            for (Mob m : all)
                m.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The bot's game-ender rules (bot/BotGameEnders): every faction has a ward to answer with, the two built game enders
     * are known; a medium or hard bot with Tier 2 wants a ward per enemy game ender (an easy one never); only a hard bot
     * floating with its T3 lab up and Tier 3, and without one already, starts its own.
     */
    @GameTest(template = ARENA)
    public static void bots_answer_game_enders_with_wards_and_only_hard_bots_build_them(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (var f : List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
                Factions.TIDEWROUGHT, Factions.STORMCALLERS, Factions.BRASSWORKS))
            if (com.solegendary.reignofnether.bot.BotGameEnders.wardFor(f) == null
                    || !f.equals(com.solegendary.reignofnether.bot.BotGameEnders.wardFor(f).getFaction()))
                problems.add("no Ward Spire of its own for " + f.getName());
        if (com.solegendary.reignofnether.bot.BotGameEnders.gameEnderFor(Factions.VILLAGERS) != Buildings.SUN_SPIRE
                || com.solegendary.reignofnether.bot.BotGameEnders.gameEnderFor(Factions.MONSTERS) != Buildings.NECROPOLIS)
            problems.add("the bots should know the Sun Spire and the Necropolis");
        var E = com.solegendary.reignofnether.bot.BotPlayer.Difficulty.EASY;
        var M = com.solegendary.reignofnether.bot.BotPlayer.Difficulty.MEDIUM;
        var H = com.solegendary.reignofnether.bot.BotPlayer.Difficulty.HARD;
        if (com.solegendary.reignofnether.bot.BotGameEnders.wardsWanted(M, true, 1) != 1
                || com.solegendary.reignofnether.bot.BotGameEnders.wardsWanted(H, true, 2) != 2)
            problems.add("a bot with Tier 2 should want a ward per enemy game ender");
        if (com.solegendary.reignofnether.bot.BotGameEnders.wardsWanted(E, true, 1) != 0
                || com.solegendary.reignofnether.bot.BotGameEnders.wardsWanted(H, false, 1) != 0
                || com.solegendary.reignofnether.bot.BotGameEnders.wardsWanted(H, true, 0) != 0)
            problems.add("no ward on easy, without Tier 2, or without a threat");
        if (!com.solegendary.reignofnether.bot.BotGameEnders.wantsGameEnder(H, true, true, true, 0))
            problems.add("a hard bot floating at Tier 3 with its lab should build a game ender");
        if (com.solegendary.reignofnether.bot.BotGameEnders.wantsGameEnder(M, true, true, true, 0)
                || com.solegendary.reignofnether.bot.BotGameEnders.wantsGameEnder(H, false, true, true, 0)
                || com.solegendary.reignofnether.bot.BotGameEnders.wantsGameEnder(H, true, false, true, 0)
                || com.solegendary.reignofnether.bot.BotGameEnders.wantsGameEnder(H, true, true, false, 0)
                || com.solegendary.reignofnether.bot.BotGameEnders.wantsGameEnder(H, true, true, true, 1))
            problems.add("only a hard, floating bot with its T3 lab, Tier 3 and no game ender yet should start one");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ---- beasts-2: the other three T2 beasts and five game enders ----------------------------------------------------

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** A resource pool for a test owner (removed by the caller). */
    static com.solegendary.reignofnether.resources.Resources testPool(String owner, int metal, int energy) {
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addMetal(metal);
        pool.addEnergy(energy);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        return pool;
    }

    /** A charged Ward Spire of {@code owner} at {@code at} (a placement, never put in the world). */
    static WardSpirePlacement chargedWard(ServerLevel level, BlockPos at, String owner) {
        var w = (WardSpirePlacement) BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_VILLAGERS, level, at, Rotation.NONE, owner, false);
        w.isBuilt = true;
        w.charges = 1;
        return w;
    }

    /**
     * The round-2 beasts are trained at their T2 labs, priced ~2x their faction's T2 heavy, belong to their factions and
     * are in the bots' T2 mixes; the five new game enders need a T2 constructor, belong to their factions and ship
     * their structures; every faction's bot knows its game ender.
     */
    @GameTest(template = ARENA)
    public static void round2_beasts_and_game_enders_are_registered_priced_and_known_to_bots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        record Row(com.solegendary.reignofnether.building.production.ProductionBuilding lab, com.solegendary.reignofnether.building.production.ProductionItem beast,
                   com.solegendary.reignofnether.resources.ResourceCost heavy, com.solegendary.reignofnether.faction.Faction faction) { }
        var rows = List.of(
            new Row(Buildings.DRYDOCK, ProductionItems.SHELLBACK_TURTLE, com.solegendary.reignofnether.resources.ResourceCosts.MORTAR_GALLEY, Factions.TIDEWROUGHT),
            new Row(Buildings.CELESTIAL_OBSERVATORY, ProductionItems.JADE_QILIN, com.solegendary.reignofnether.resources.ResourceCosts.EARTHSHAKER, Factions.STORMCALLERS),
            new Row(Buildings.ASSEMBLY_WORKS, ProductionItems.STEAM_HAULER, com.solegendary.reignofnether.resources.ResourceCosts.STEAM_JUGGERNAUT, Factions.BRASSWORKS));
        for (Row r : rows) {
            String n = r.beast().getItemName();
            if (!r.lab().productions.get().contains(r.beast()))
                problems.add(r.lab().name + " does not train " + n);
            var type = EntityRegistrar.getEntityType(n);
            if (type == null) {
                problems.add(n + " has no entity type");
                continue;
            }
            Entity probe = type.create(level);   // never added to the level
            if (!(probe instanceof Unit pu) || !r.faction().equals(Factions.getFaction(pu)))
                problems.add(n + " is not registered to " + r.faction().getName());
            if (probe != null)
                probe.discard();
            float ratio = (float) r.beast().defaultCost.metal() / Math.max(1, r.heavy().metal());
            if (ratio < 1.7f || ratio > 2.6f)
                problems.add(n + " costs " + ratio + "x its faction's T2 heavy (want ~2-2.5x)");
            var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(r.faction());
            if (kit == null || !kit.t2Army().contains(r.beast()))
                problems.add("the " + r.faction().getName() + " bot does not field the " + n);
        }
        record Ender(com.solegendary.reignofnether.building.Building b, com.solegendary.reignofnether.faction.Faction f, String nbt) { }
        var enders = List.of(new Ender(Buildings.SUN_SPIRE, Factions.VILLAGERS, "sun_spire"),
            new Ender(Buildings.NECROPOLIS, Factions.MONSTERS, "necropolis"),
            new Ender(Buildings.DOOM_FORGE, Factions.PIGLINS, "doom_forge"),
            new Ender(Buildings.WORLD_SEED, Factions.VERDANT_COURT, "world_seed"),
            new Ender(Buildings.MAELSTROM_ENGINE, Factions.TIDEWROUGHT, "maelstrom_engine"),
            new Ender(Buildings.HEAVENS_BELL, Factions.STORMCALLERS, "heavens_bell"),
            new Ender(Buildings.EARTHCRACKER, Factions.BRASSWORKS, "earthcracker"));
        for (Ender e : enders) {
            if (!com.solegendary.reignofnether.unit.T2Workers.needsT2Builder(e.b()))
                problems.add(e.nbt() + " must need a T2 constructor");
            if (!e.f().equals(e.b().getFaction()))
                problems.add(e.nbt() + " is not registered to " + e.f().getName());
            if (com.solegendary.reignofnether.building.BuildingBlockData.getBuildingNbt(e.nbt(), level.getServer().getResourceManager()) == null)
                problems.add("the structure " + e.nbt() + " is missing from data/");
            if (com.solegendary.reignofnether.bot.BotGameEnders.gameEnderFor(e.f()) != e.b())
                problems.add("the " + e.f().getName() + " bot does not know its game ender " + e.nbt());
        }
        if (!com.solegendary.reignofnether.bot.BotGameEnders.turtleWantsMoor(1, 0)
                || com.solegendary.reignofnether.bot.BotGameEnders.turtleWantsMoor(0, 0)
                || com.solegendary.reignofnether.bot.BotGameEnders.turtleWantsMoor(2, 1))
            problems.add("a bot turtle should moor with an enemy in its battery's band and nobody at its shell, else not");
        finish(helper, problems);
    }

    /**
     * Shellback Siege-Turtle: Moor takes MOOR_TICKS to anchor (immobile from the first tick), then the turtle takes half
     * damage, raises its tidepool and its battery hits the nearest enemy in its band - not a friend, not an enemy in the
     * dead zone at its shell. Weighing anchor takes as long, drains the pool and frees it to move.
     */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void shellback_turtle_moors_into_a_fort_with_a_pool_and_a_deck_battery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "shellback"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var vill = EntityRegistrar.VILLAGER_UNIT.get();
        var turtle = (com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit)
            spawn(helper, EntityRegistrar.SHELLBACK_TURTLE_UNIT.get(), owner, 4.5, 4.5);
        Mob foeBand = spawn(helper, vill, foeOwner, 12.5, 4.5);   // 8 blocks: in the band
        Mob foeShell = spawn(helper, vill, foeOwner, 4.5, 7.0);   // 2.5 blocks: in the dead zone
        Mob friend = spawn(helper, vill, owner, 10.5, 4.5);       // nearer than the foe in the band, but ours
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { turtle, foeBand, foeShell, friend })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 4) {
                helper.fail("could not create the Shellback test units");
                return;
            }
            turtle.toggleMoor();
            for (int t = 0; t < com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit.MOOR_TICKS - 1; t++)
                turtle.tickMoor(level);
            if (turtle.isMoored() || !turtle.isAnchored())
                problems.add("mooring should take MOOR_TICKS, immobile from the start (moored " + turtle.isMoored() + ")");
            if (turtle.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0.0001)
                problems.add("an anchoring turtle must not be able to move");
            turtle.tickMoor(level);
            if (!turtle.isMoored())
                problems.add("the turtle should be moored after MOOR_TICKS");
            var pool = turtle.getPool();
            if (pool == null || !pool.isActive())
                problems.add("a moored turtle should raise its tidepool (" + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal() + ")");
            float h0 = turtle.getHealth();
            turtle.hurt(level.damageSources().generic(), 20f);
            float lost = h0 - turtle.getHealth();
            if (Math.abs(lost - 20f * com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit.MOORED_TAKEN_MULT) > 0.5f)
                problems.add("a moored turtle should take half damage (lost " + lost + " of 20)");
            // the first moored tick already fired once: clear the hit-cooldown so this shot's damage shows
            foeBand.invulnerableTime = 0;
            float b0 = foeBand.getHealth(), s0 = foeShell.getHealth(), f0 = friend.getHealth();
            if (!turtle.fireBattery(level) || turtle.lastBatteryUnit != foeBand)
                problems.add("the battery should hit the enemy in its band (hit " + turtle.lastBatteryUnit + ")");
            if (foeBand.getHealth() >= b0)
                problems.add("the battery did not hurt the enemy in its band");
            if (foeShell.getHealth() < s0 || friend.getHealth() < f0)
                problems.add("the battery hit a friend or an enemy in its dead zone");
            turtle.toggleMoor();
            for (int t = 0; t < com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit.MOOR_TICKS; t++)
                turtle.tickMoor(level);
            if (turtle.isAnchored())
                problems.add("weighing anchor should take MOOR_TICKS");
            if (turtle.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) <= 0.0001)
                problems.add("an unmoored turtle should move again");
            if (pool != null && pool.isActive() && !pool.isDraining())
                problems.add("weighing anchor should drain the turtle's pool");
        } finally {
            for (Mob m : all)
                m.discard();
        }
        finish(helper, problems);
    }

    /**
     * Jade Qilin: a spell cast near a friendly qilin counts as cast in a Mana Well (cost discount), far from it or for an
     * enemy it doesn't; Sealed it gives nothing; its Mana Spring banks energy for its owner.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void jade_qilin_is_a_walking_mana_well_and_a_seal_silences_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "qilin"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        var qilin = (com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit)
            spawn(helper, EntityRegistrar.JADE_QILIN_UNIT.get(), owner, 7.5, 7.5);
        if (qilin == null) {
            helper.fail("could not create the Jade Qilin");
            return;
        }
        var pool = testPool(owner, 0, 0);
        try {
            com.solegendary.reignofnether.unit.BigUnitServerEvents.trackQilin(qilin);
            BlockPos near = qilin.blockPosition().offset(4, 0, 2), far = qilin.blockPosition().offset(40, 0, 0);
            if (!com.solegendary.reignofnether.mana.ManaServerEvents.insideWell(owner, near)
                    || com.solegendary.reignofnether.mana.ManaServerEvents.spellCostAt(owner, near) != com.solegendary.reignofnether.mana.ManaServerEvents.WELL_DISCOUNT)
                problems.add("a spell near the qilin should count as cast in a Mana Well");
            if (com.solegendary.reignofnether.mana.ManaServerEvents.insideWell(owner, far))
                problems.add("a spell far from the qilin must not");
            if (com.solegendary.reignofnether.mana.ManaServerEvents.insideWell(foeOwner, near))
                problems.add("an enemy's spell near the qilin must not");
            float banked = qilin.manaSpring();
            if (Math.abs(banked - com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit.SPRING_ENERGY_PER_SECOND) > 0.01f
                    || Math.abs(pool.getEnergy() - banked) > 0.01f)
                problems.add("the Mana Spring should bank " + com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit.SPRING_ENERGY_PER_SECOND
                    + " energy (banked " + banked + ", pool " + pool.getEnergy() + ")");
            com.solegendary.reignofnether.mana.SealServerEvents.seal(level, qilin, 1f);
            if (com.solegendary.reignofnether.mana.ManaServerEvents.insideWell(owner, near))
                problems.add("a Sealed qilin must not be a well");
            if (qilin.manaSpring() > 0)
                problems.add("a Sealed qilin's spring must not flow");
        } finally {
            qilin.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
        finish(helper, problems);
    }

    /**
     * Steam Hauler: one foundry pulse mends every damaged friendly machine in range (not an enemy's, not a friendly
     * non-machine) and heats it; a second hauler can't mend the same machines in the same second; it drops a free
     * turret for its owner (more heat); venting, it neither mends nor drops.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void steam_hauler_mends_every_machine_round_it_and_drops_turrets_until_it_vents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "hauler"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var jug = EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get();
        var hauler = (com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit) spawn(helper, EntityRegistrar.STEAM_HAULER_UNIT.get(), owner, 4.5, 4.5);
        var hauler2 = (com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit) spawn(helper, EntityRegistrar.STEAM_HAULER_UNIT.get(), owner, 4.5, 11.5);
        Mob m1 = spawn(helper, jug, owner, 9.5, 4.5), m2 = spawn(helper, jug, owner, 4.5, 9.5);
        Mob foeM = spawn(helper, jug, foeOwner, 9.5, 8.5);
        Mob person = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 7.5, 6.5);
        List<Entity> all = new ArrayList<>();
        for (Mob m : new Mob[] { hauler, hauler2, m1, m2, foeM, person })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 6) {
                helper.fail("could not create the Steam Hauler test units");
                return;
            }
            for (Mob m : new Mob[] { m1, m2, foeM, person })
                m.setHealth(m.getMaxHealth() - 20);
            float a = m1.getHealth(), b = m2.getHealth(), e = foeM.getHealth(), p = person.getHealth();
            int mended = hauler.foundryPulse(level);
            if (mended != 2 || m1.getHealth() <= a || m2.getHealth() <= b)
                problems.add("one pulse should mend both damaged friendly machines (mended " + mended + ")");
            if (foeM.getHealth() > e || person.getHealth() > p)
                problems.add("the foundry mended an enemy machine or a friendly non-machine");
            if (hauler.heat() != com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit.HEAT_PER_REPAIR_SECOND)
                problems.add("a second of repairs should add HEAT_PER_REPAIR_SECOND heat (heat " + hauler.heat() + ")");
            float a1 = m1.getHealth();
            hauler2.setPos(hauler.getX(), hauler.getY(), hauler.getZ() + 2);
            if (hauler2.foundryPulse(level) != 0 || m1.getHealth() > a1)
                problems.add("two haulers must not mend the same machine in the same second");
            int h0 = hauler.heat();
            var turret = hauler.dropTurret(level, hauler.blockPosition().offset(3, 0, 0));
            if (turret == null || !owner.equals(turret.getOwnerName()))
                problems.add("Drop Turret should set a turret for the hauler's owner");
            else
                all.add(turret);
            if (hauler.heat() != h0 + com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit.TURRET_HEAT)
                problems.add("a turret drop should add TURRET_HEAT heat");
            hauler.setVentTicks(60);
            hauler.setHeat(com.solegendary.reignofnether.brass.Heat.MAX);
            m1.setHealth(m1.getMaxHealth() - 20);
            if (hauler.foundryPulse(level) != 0 || hauler.dropTurret(level, hauler.blockPosition().offset(0, 0, 3)) != null)
                problems.add("a venting hauler must neither mend nor drop turrets");
        } finally {
            for (Entity en : all)
                en.discard();
        }
        finish(helper, problems);
    }

    /**
     * Doom Forge: built and at Tier 3 it waits while its owner can't stoke it, then burns STOKE_METAL and charges; its
     * meteor is intercepted by the victim's charged ward, then lands - the enemy at the target is hurt, a friend is not -
     * and the crater burns on.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void doom_forge_waits_for_metal_then_its_meteor_lands_unless_a_ward_intercepts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "doomforge"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        var forge = (com.solegendary.reignofnether.building.gameender.DoomForgePlacement) BuildingUtils.getNewBuildingPlacement(
            Buildings.DOOM_FORGE, level, helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, owner, false);
        forge.pool = new ArrayList<>();
        forge.stageTicks = 10;
        forge.isBuilt = true;
        var pool = testPool(owner, 0, 0);
        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_3);
        BlockPos target = helper.absolutePos(new BlockPos(8, 2, 8));
        Mob foe = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner, 9.5, 8.5);
        Mob friend = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 7.5, 9.5);
        try {
            if (foe == null || friend == null) {
                helper.fail("could not create the Doom Forge test units");
                return;
            }
            for (int t = 0; t < 30; t++)
                forge.tickGameEnder(level);
            if (forge.charge() > 0)
                problems.add("an unstoked forge charged without metal");
            pool.addMetal(1000);
            for (int t = 0; t < 30 && forge.stageProgress == 0 && forge.stage == 0; t++)
                forge.tickGameEnder(level);
            if (forge.charge() <= 0)
                problems.add("with metal the forge should stoke and charge");
            if (Math.abs(pool.getMetal() - (1000 - com.solegendary.reignofnether.building.gameender.DoomForgePlacement.STOKE_METAL)) > 0.5f)
                problems.add("the first stage should burn STOKE_METAL (metal " + pool.getMetal() + ")");
            WardSpirePlacement ward = chargedWard(level, target.offset(12, 0, 0), foeOwner);
            forge.pool = new ArrayList<>(List.of(ward));
            float f0 = foe.getHealth(), m0 = friend.getHealth();
            forge.aimAt(level, target, foeOwner);
            forge.resolveStrike(level);
            if (forge.strikesIntercepted != 1 || foe.getHealth() < f0 || forge.aftermath != 0)
                problems.add("the charged ward should have stopped the meteor");
            forge.aimAt(level, target, foeOwner);
            forge.resolveStrike(level);
            if (forge.strikesLanded != 1 || foe.isAlive() && foe.getHealth() >= f0)
                problems.add("with the ward spent the meteor should land on the enemy at the target");
            if (friend.getHealth() < m0)
                problems.add("the meteor hurt a friend");
            if (forge.aftermath != com.solegendary.reignofnether.building.gameender.DoomForgePlacement.CRATER_TICKS)
                problems.add("the crater should burn after the impact");
            if (foe.isAlive()) {
                float f1 = foe.getHealth();
                foe.invulnerableTime = 0;
                forge.aftermath = 20;
                forge.tickAftermath(level);
                if (foe.getHealth() >= f1)
                    problems.add("the crater should burn the enemy in it");
            }
        } finally {
            if (foe != null)
                foe.discard();
            if (friend != null)
                friend.discard();
            ResearchServerEvents.removeAllResearchFor(owner);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
        finish(helper, problems);
    }

    /**
     * World Seed: a charged ward stops the seed (no Heartroot); unwarded, a rooted Heartroot of the seed's owner rises at
     * the target (tagged to crumble), and each second of grove hurts the enemy near it and heals a hurt friend; killing
     * the Heartroot ends the grove at once.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void world_seed_grows_a_grove_round_a_heartroot_until_the_heartroot_dies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "worldseed"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var seed = (com.solegendary.reignofnether.building.gameender.WorldSeedPlacement) BuildingUtils.getNewBuildingPlacement(
            Buildings.WORLD_SEED, level, helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, owner, false);
        BlockPos target = helper.absolutePos(new BlockPos(7, 1, 7));
        WardSpirePlacement ward = chargedWard(level, target.offset(20, 1, 0), foeOwner);   // outside the grove: never damaged
        seed.pool = new ArrayList<>(List.of(ward));
        Mob foe = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner, 10.5, 7.5);
        Mob friend = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 4.5, 7.5);
        try {
            if (foe == null || friend == null) {
                helper.fail("could not create the World Seed test units");
                return;
            }
            seed.aimAt(level, target, foeOwner);
            seed.resolveStrike(level);
            if (seed.strikesIntercepted != 1 || seed.heartroot != null)
                problems.add("the charged ward should have stopped the seed");
            seed.aimAt(level, target, foeOwner);
            seed.resolveStrike(level);
            var root = seed.heartroot;
            if (root == null || !seed.groveAlive()) {
                problems.add("the seed should raise a Heartroot at the target");
            } else {
                if (!(root instanceof Unit u) || !owner.equals(u.getOwnerName()))
                    problems.add("the Heartroot should belong to the seed's owner");
                if (!NecropolisPlacement.isRisen(root))
                    problems.add("the Heartroot should be tagged to crumble");
                if (root.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0.0001)
                    problems.add("the Heartroot should be rooted");
                double rd = root.distanceToSqr(target.getX() + 0.5, root.getY(), target.getZ() + 0.5);
                if (rd > 8 * 8 || Math.abs(root.getY() - (target.getY() + 1)) > 1.5)
                    problems.add("the Heartroot should rise on the ground beside the target (at " + root.position() + ")");
                friend.setHealth(friend.getMaxHealth() - 6);
                float f0 = foe.getHealth(), m0 = friend.getHealth();
                seed.overgrow(level);
                if (foe.getHealth() >= f0 || seed.lastFoesHurt < 1)
                    problems.add("the grove should hurt the enemy near the Heartroot");
                if (friend.getHealth() <= m0)
                    problems.add("the grove should heal a hurt friend");
                root.kill();
                seed.tickAftermath(level);
                float f1 = foe.getHealth();
                foe.invulnerableTime = 0;
                seed.overgrow(level);
                if (seed.groveAlive() || seed.aftermath != 0 || foe.getHealth() < f1)
                    problems.add("killing the Heartroot should end the grove");
            }
        } finally {
            if (seed.heartroot != null)
                seed.heartroot.discard();
            if (foe != null)
                foe.discard();
            if (friend != null)
                friend.discard();
        }
        finish(helper, problems);
    }

    /**
     * Maelstrom Engine: its flood ring paces the charge - three live pools full rate, one drained two thirds, all
     * drained nothing; a ward stops the whirlpool; unwarded the whirlpool drags the enemy toward its centre and batters
     * it, a friend is spared.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void maelstrom_engine_charges_off_its_flood_ring_and_its_whirlpool_drags_enemies_in(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "maelstrom"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var engine = (com.solegendary.reignofnether.building.gameender.MaelstromEnginePlacement) BuildingUtils.getNewBuildingPlacement(
            Buildings.MAELSTROM_ENGINE, level, helper.absolutePos(new BlockPos(3, 2, 3)), Rotation.NONE, owner, false);
        engine.pool = new ArrayList<>();
        BlockPos target = helper.absolutePos(new BlockPos(7, 2, 7));
        Mob foe = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner, 12.5, 7.5);
        Mob friend = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 3.5, 7.5);
        try {
            if (foe == null || friend == null) {
                helper.fail("could not create the Maelstrom test units");
                return;
            }
            engine.flood(level);
            if (engine.livePools() != com.solegendary.reignofnether.building.gameender.MaelstromEnginePlacement.FLOOD_POOLS)
                problems.add("the engine should flood its ring of pools (live " + engine.livePools() + ", "
                    + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal() + ")");
            else {
                engine.isBuilt = true;
                float r3 = engine.chargeRate(level);
                com.solegendary.reignofnether.tide.TidepoolServerEvents.drain(engine.floodPools[0]);
                float r2 = engine.chargeRate(level);
                for (var p : engine.floodPools)
                    com.solegendary.reignofnether.tide.TidepoolServerEvents.drain(p);
                float r0 = engine.chargeRate(level);
                if (Math.abs(r3 - 1f) > 0.01f || Math.abs(r2 - 2f / 3f) > 0.01f || r0 != 0f)
                    problems.add("the charge rate should follow the live pools (3: " + r3 + ", 2: " + r2 + ", 0: " + r0 + ")");
            }
            WardSpirePlacement ward = chargedWard(level, target.offset(14, 0, 0), foeOwner);
            engine.pool = new ArrayList<>(List.of(ward));
            engine.aimAt(level, target, foeOwner);
            engine.resolveStrike(level);
            if (engine.strikesIntercepted != 1 || engine.aftermath != 0)
                problems.add("the charged ward should have stopped the whirlpool");
            engine.aimAt(level, target, foeOwner);
            engine.resolveStrike(level);
            if (engine.strikesLanded != 1 || engine.aftermath != com.solegendary.reignofnether.building.gameender.MaelstromEnginePlacement.WHIRL_TICKS)
                problems.add("unwarded the whirlpool should open");
            foe.setDeltaMovement(0, 0, 0);
            engine.aftermath = 44;   // a pull tick that is not a damage tick (those are % 20)
            engine.tickAftermath(level);
            double inward = (target.getX() + 0.5 - foe.getX()) * foe.getDeltaMovement().x + (target.getZ() + 0.5 - foe.getZ()) * foe.getDeltaMovement().z;
            if (inward <= 0)
                problems.add("the whirlpool should drag the enemy toward its centre");
            float f0 = foe.getHealth(), m0 = friend.getHealth();
            engine.whirl(level);
            if (foe.getHealth() >= f0)
                problems.add("the whirlpool should batter the enemy in it");
            if (friend.getHealth() < m0)
                problems.add("the whirlpool hurt a friend");
        } finally {
            for (var p : engine.floodPools)
                if (p != null)
                    com.solegendary.reignofnether.tide.TidepoolServerEvents.drain(p);
            if (foe != null)
                foe.discard();
            if (friend != null)
                friend.discard();
        }
        finish(helper, problems);
    }

    /**
     * Heaven's Bell: it waits while its owner can't pay a stage's energy, then pays STAGE_ENERGY and charges; a ward
     * stops the storm; unwarded each bolt strikes an enemy unit in the storm, never a friend.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void heavens_bell_waits_for_energy_then_its_storm_strikes_enemy_units(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "heavensbell"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        var bell = (com.solegendary.reignofnether.building.gameender.HeavensBellPlacement) BuildingUtils.getNewBuildingPlacement(
            Buildings.HEAVENS_BELL, level, helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, owner, false);
        bell.pool = new ArrayList<>();
        bell.stageTicks = 10;
        bell.isBuilt = true;
        var pool = testPool(owner, 0, 0);
        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_3);
        BlockPos target = helper.absolutePos(new BlockPos(8, 2, 8));
        Mob foe = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner, 10.5, 8.5);
        Mob friend = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 6.5, 8.5);
        try {
            if (foe == null || friend == null) {
                helper.fail("could not create the Heaven's Bell test units");
                return;
            }
            for (int t = 0; t < 30; t++)
                bell.tickGameEnder(level);
            if (bell.charge() > 0)
                problems.add("a bell whose owner has no energy charged");
            pool.addEnergy(1000);
            for (int t = 0; t < 30 && bell.stageProgress == 0 && bell.stage == 0; t++)
                bell.tickGameEnder(level);
            if (bell.charge() <= 0)
                problems.add("with energy the bell should toll and charge");
            if (Math.abs(pool.getEnergy() - (1000 - com.solegendary.reignofnether.building.gameender.HeavensBellPlacement.STAGE_ENERGY)) > 0.5f)
                problems.add("the first stage should cost STAGE_ENERGY (energy " + pool.getEnergy() + ")");
            WardSpirePlacement ward = chargedWard(level, target.offset(14, 0, 0), foeOwner);
            bell.pool = new ArrayList<>(List.of(ward));
            bell.aimAt(level, target, foeOwner);
            bell.resolveStrike(level);
            if (bell.strikesIntercepted != 1 || bell.aftermath != 0)
                problems.add("the charged ward should have stopped the storm");
            bell.aimAt(level, target, foeOwner);
            bell.resolveStrike(level);
            if (bell.strikesLanded != 1 || bell.aftermath != com.solegendary.reignofnether.building.gameender.HeavensBellPlacement.STORM_TICKS)
                problems.add("unwarded the storm should break");
            float f0 = foe.getHealth(), m0 = friend.getHealth();
            bell.bolt(level);
            if (bell.boltsOnUnits != 1 || foe.getHealth() >= f0)
                problems.add("a bolt should strike the enemy unit in the storm");
            if (friend.getHealth() < m0)
                problems.add("a bolt struck a friend");
        } finally {
            if (foe != null)
                foe.discard();
            if (friend != null)
                friend.discard();
            ResearchServerEvents.removeAllResearchFor(owner);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
        finish(helper, problems);
    }

    /**
     * Earthcracker: after a stage it vents (no charge) and a raid on it counts double; a ward stops the fault; unwarded
     * the fault races along the drill's bearing through the target and throws the enemy on the line, not one beside
     * it nor a friend on it; a building on the line is crossed, one beside it is not.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void earthcracker_vents_between_stages_and_its_fault_line_hits_only_the_line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "earthcracker"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        var drill = (com.solegendary.reignofnether.building.gameender.EarthcrackerPlacement) BuildingUtils.getNewBuildingPlacement(
            Buildings.EARTHCRACKER, level, helper.absolutePos(new BlockPos(0, 2, 5)), Rotation.NONE, owner, false);
        drill.pool = new ArrayList<>();
        drill.stageTicks = 10;
        drill.isBuilt = true;
        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_3);
        BlockPos target = new BlockPos(drill.centrePos.getX() + 10, drill.minCorner.getY(), drill.centrePos.getZ());   // due east
        BlockPos rel = target.subtract(helper.absolutePos(BlockPos.ZERO));
        Mob foeOn = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner, rel.getX() + 2.5, rel.getZ() + 0.5);
        Mob foeOff = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner, rel.getX() + 0.5, rel.getZ() + 6.5);
        Mob friend = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, rel.getX() - 1.5, rel.getZ() + 0.5);
        try {
            if (foeOn == null || foeOff == null || friend == null) {
                helper.fail("could not create the Earthcracker test units");
                return;
            }
            for (int t = 0; t < 30 && drill.stage == 0; t++)
                drill.tickGameEnder(level);
            if (drill.stage != 1 || drill.vent != com.solegendary.reignofnether.building.gameender.EarthcrackerPlacement.VENT_TICKS)
                problems.add("a completed stage should start the vent (stage " + drill.stage + ", vent " + drill.vent + ")");
            for (int t = 0; t < 20; t++)
                drill.tickGameEnder(level);
            if (drill.stage != 1 || drill.stageProgress != 0)
                problems.add("a venting drill must not charge (progress " + drill.stageProgress + ")");
            int step = (int) Math.ceil(drill.getMaxHealth() * GameEnderPlacement.RAID_STEP);
            drill.stage = 2;
            drill.stageProgress = 5;
            drill.onRaided(step / 2 + 1);
            if (drill.stage != 1)
                problems.add("a raid on a venting drill should count double (half a step knocked " + (2 - drill.stage) + " stages off)");
            drill.vent = 0;
            // the fault's geometry: a building on the line is crossed, one beside it is not
            var onLine = BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_BRASS, level, target.offset(9, 0, -1), Rotation.NONE, foeOwner, false);
            var beside = BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_BRASS, level, target.offset(0, 0, 11), Rotation.NONE, foeOwner, false);
            if (!drill.crossesFault(onLine, target) || drill.crossesFault(beside, target))
                problems.add("the fault should cross the building on its line and miss the one beside it");
            WardSpirePlacement ward = chargedWard(level, target.offset(0, 0, 12), foeOwner);
            drill.pool = new ArrayList<>(List.of(ward));
            drill.aimAt(level, target, foeOwner);
            drill.resolveStrike(level);
            if (drill.strikesIntercepted != 1 || drill.aftermath != 0)
                problems.add("the charged ward should have stopped the fault");
            float a0 = foeOn.getHealth(), b0 = foeOff.getHealth(), m0 = friend.getHealth();
            drill.aimAt(level, target, foeOwner);
            drill.resolveStrike(level);
            while (drill.aftermath > 0) {
                drill.aftermath--;
                drill.tickAftermath(level);
            }
            if (drill.strikesLanded != 1 || foeOn.getHealth() >= a0 || drill.lastUnitsHit < 1)
                problems.add("the fault should throw the enemy on its line (hit " + drill.lastUnitsHit + ")");
            if (foeOff.getHealth() < b0)
                problems.add("the fault hit an enemy beside the line");
            if (friend.getHealth() < m0)
                problems.add("the fault hit a friend");
        } finally {
            for (Mob m : new Mob[] { foeOn, foeOff, friend })
                if (m != null)
                    m.discard();
            ResearchServerEvents.removeAllResearchFor(owner);
        }
        finish(helper, problems);
    }
}
