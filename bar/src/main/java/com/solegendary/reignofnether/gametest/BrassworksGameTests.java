package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Factions;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of the Brassworks Artificers' slice 2 (design/brassworks_plan.md: the Assembly Works and its T2).
 * Their own holder next to SkirmishGameTests (same arena template, same openGround helper, unique owners, no global
 * counts) so the faction's tests sit together; every test runs in parallel with the rest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BrassworksGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    // ---- Brassworks slice 2: the Assembly Works and its T2 (design/brassworks_plan.md) ---------------------------------

    /**
     * The Assembly Works (Brassworks T2 lab) trains every T2 unit: each of its unit items names an entity type registered
     * to the Brassworks that makes a priced Unit, is refused without Tier 2 (the server gate, BrassT2Prod) and allowed
     * with it; the heaters among them are Machines (the Engineer-Captain is a crew, the Repair Crawler a machine that
     * never heats); the bot's kit uses the Works, the Engineer-Captain and only units the Works trains; the structure and
     * its two levels ship in data/. A placement that is never put in the world; research under a unique owner.
     */
    @GameTest(template = ARENA)
    public static void assembly_works_trains_every_brassworks_t2_unit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_t2_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var lab = Buildings.ASSEMBLY_WORKS;
        var brass = Factions.BRASSWORKS;
        if (!brass.equals(lab.getFaction()))
            helper.fail("the Assembly Works is not a Brassworks building");
        var expected = List.<com.solegendary.reignofnether.building.production.ProductionItem>of(
            com.solegendary.reignofnether.building.production.ProductionItems.ENGINEER_CAPTAIN,
            com.solegendary.reignofnether.building.production.ProductionItems.STEAM_JUGGERNAUT,
            com.solegendary.reignofnether.building.production.ProductionItems.TESLA_WALKER,
            com.solegendary.reignofnether.building.production.ProductionItems.GYROCOPTER_GUNSHIP,
            com.solegendary.reignofnether.building.production.ProductionItems.DRILL_BURROWER,
            com.solegendary.reignofnether.building.production.ProductionItems.REPAIR_CRAWLER,
            com.solegendary.reignofnether.building.production.ProductionItems.CLOCKWORK_SPIDER);
        for (var item : expected)
            if (!lab.productions.get().contains(item))
                helper.fail("the Assembly Works does not train " + item.getItemName());
        var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(brass);
        if (kit == null || kit.t2Lab() != lab || kit.t2Worker() != com.solegendary.reignofnether.building.production.ProductionItems.ENGINEER_CAPTAIN
                || kit.t2Army().isEmpty())
            helper.fail("the Brassworks bot kit does not use the Assembly Works and the Engineer-Captain");
        else
            for (var item : kit.t2Army())
                if (!lab.productions.get().contains(item))
                    helper.fail("the Brassworks bot's T2 army has " + item.getItemName() + ", which the Assembly Works does not train");
        if (lab.maxLevel != com.solegendary.reignofnether.building.BuildingLevels.MAX_LEVEL)
            helper.fail("the Assembly Works should take the lab levels");
        for (String nbt : List.of("assembly_works", "assembly_works_l2", "assembly_works_l3", "rail_battery"))
            if (com.solegendary.reignofnether.building.BuildingBlockData.getBuildingNbt(nbt, level.getServer().getResourceManager()) == null)
                helper.fail("the structure " + nbt + " is missing from data/");
        int units = 0, heaters = 0;
        for (var item : lab.productions.get()) {
            if (!(item instanceof com.solegendary.reignofnether.building.production.IUnitProductionItem))
                continue;
            if (!(item instanceof com.solegendary.reignofnether.unit.units.brass.BrassT2Prod))
                helper.fail(item.getItemName() + " skips the Tier 2 gate (not a BrassT2Prod)");
            var type = com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(item.getItemName());
            if (type == null) {
                helper.fail(item.getItemName() + " names no entity type");
                continue;
            }
            if (!brass.key.equals(Factions.ENTITY_FACTION.get(net.minecraft.world.entity.EntityType.getKey(type))))
                helper.fail(net.minecraft.world.entity.EntityType.getKey(type) + " is not registered to the Brassworks");
            var e = type.create(level);
            if (!(e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u) || u.getCost() == null || u.getCost().metal() <= 0)
                helper.fail(item.getItemName() + " does not make a priced Unit");
            else {
                units++;
                if (e instanceof com.solegendary.reignofnether.brass.Machine m && m.heatPerShot() > 0)
                    heaters++;
            }
            if (e != null)
                e.discard();
        }
        if (units != 8)   // beasts-2 added the Steam Hauler (T2 beast, design/big_units.md 1.7)
            helper.fail("the Assembly Works should train eight units, found " + units);
        if (heaters != 5)
            helper.fail("five of the T2 units heat (Juggernaut, Tesla Walker, Gunship, Drill Burrower, Clockwork Spider), found " + heaters);
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(lab, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
            helper.fail("the Assembly Works is not a production placement: " + bp);
            return;
        }
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            for (var item : expected)
                if (item.canProduce(pp))
                    helper.fail(item.getItemName() + " can be trained without Tier 2");
            if (pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.STEAM_JUGGERNAUT) || !pp.productionQueue.isEmpty())
                helper.fail("the server queued a Steam Juggernaut without Tier 2");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            for (var item : expected)
                if (!item.canProduce(pp))
                    helper.fail(item.getItemName() + " still refused after Tier 2: " + item.getProduceErrorMsg(pp));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        }
    }

    /**
     * Burrow (DrillBurrowerUnit): while tunnelling the Drill Burrower is hidden from its enemies (Concealment - no
     * targeting, no orders on it) but not from its owner, and nothing hurts it; after BURROW_TICKS it surfaces at the
     * spot, visible again, and the enemy standing there is hurt. Own arena, unique owners.
     */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void drill_burrower_is_untargetable_while_burrowed_and_surfaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_drill_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var drill = com.solegendary.reignofnether.registrars.EntityRegistrar.DRILL_BURROWER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (drill == null || foe == null) {
            helper.fail("could not create the Burrow test units");
            return;
        }
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 7)), b = helper.absolutePos(new BlockPos(12, 2, 7));
        drill.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, -90, 0);
        foe.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 90, 0);
        drill.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        foe.setHoldPosition(true);
        level.addFreshEntity(drill);
        level.addFreshEntity(foe);
        float[] foeBefore = { 0 };
        net.minecraft.world.phys.Vec3[] spot = { null };
        helper.runAfterDelay(2, () -> {
            try {
                spot[0] = foe.position();
                foeBefore[0] = foe.getHealth();
                if (!drill.startBurrow(spot[0]))
                    helper.fail("the Drill Burrower refused to burrow toward a spot 10 blocks off");
                if (drill.startBurrow(spot[0]))
                    helper.fail("a burrowed Drill Burrower started a second dig");
                if (!com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit.isBurrowed(drill)
                        || !com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(drill, foeOwner))
                    helper.fail("a burrowed Drill Burrower should be hidden from its enemies");
                if (com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(drill, owner))
                    helper.fail("a burrowed Drill Burrower should stay visible to its owner");
                float hp = drill.getHealth();
                drill.hurt(level.damageSources().generic(), 10f);
                drill.hurt(level.damageSources().mobAttack(foe), 10f);
                if (drill.getHealth() < hp)
                    helper.fail("a burrowed Drill Burrower took damage: " + hp + " -> " + drill.getHealth());
            } catch (RuntimeException ex) {
                drill.discard();
                foe.discard();
                throw ex;
            }
        });
        helper.runAfterDelay(2 + com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit.BURROW_TICKS + 6, () -> {
            try {
                if (com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit.isBurrowed(drill) || drill.isInvisible())
                    helper.fail("the Drill Burrower should have surfaced");
                if (com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(drill, foeOwner))
                    helper.fail("a surfaced Drill Burrower should be targetable again");
                double dx = drill.getX() - spot[0].x, dz = drill.getZ() - spot[0].z;
                if (dx * dx + dz * dz > 2.5 * 2.5)
                    helper.fail("the Drill Burrower surfaced " + Math.sqrt(dx * dx + dz * dz) + " blocks from the spot");
                if (foe.getHealth() >= foeBefore[0])
                    helper.fail("the enemy at the surfacing spot was not hurt");
                helper.succeed();
            } finally {
                drill.discard();
                foe.discard();
            }
        });
    }

    /**
     * Tesla Walker arc (TeslaWalkerUnit.chain): an arc at a foe strikes exactly CHAIN_TARGETS distinct enemies, jumping
     * past a friend that stands closest to the first target (never a link), and leaves a fourth enemy in reach untouched;
     * an arc aimed at a friend does nothing. Own arena, unique owners.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void tesla_chain_hits_three_distinct_enemies_never_allies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_tesla_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var walker = com.solegendary.reignofnether.registrars.EntityRegistrar.TESLA_WALKER_UNIT.get().create(level);
        List<com.solegendary.reignofnether.unit.units.villagers.VillagerUnit> foes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            foes.add(com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level));
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        Runnable cleanup = () -> {
            if (walker != null) walker.discard();
            for (var f : foes) if (f != null) f.discard();
            if (friend != null) friend.discard();
        };
        if (walker == null || friend == null || foes.contains(null)) {
            cleanup.run();
            helper.fail("could not create the Tesla test units");
            return;
        }
        BlockPos w = helper.absolutePos(new BlockPos(2, 2, 7));
        walker.moveTo(w.getX() + 0.5, w.getY(), w.getZ() + 0.5, -90, 0);
        walker.setOwnerName(owner);
        walker.setHoldPosition(true);
        level.addFreshEntity(walker);
        // foes 5, 7.5, 10 and 12.5 blocks east on one line (2.5 apart: each in the next one's jump reach); the friend
        // 1.4 from the first foe, nearer than the second
        double[] dx = { 5, 7.5, 10, 12.5 };
        for (int i = 0; i < 4; i++) {
            var f = foes.get(i);
            f.moveTo(w.getX() + dx[i] + 0.5, w.getY(), w.getZ() + 0.5, 90, 0);
            f.setOwnerName(foeOwner);
            f.setHoldPosition(true);
            level.addFreshEntity(f);
        }
        friend.moveTo(w.getX() + 6 + 0.5, w.getY(), w.getZ() + 1.5, 90, 0);
        friend.setOwnerName(owner);
        friend.setHoldPosition(true);
        level.addFreshEntity(friend);
        helper.runAfterDelay(3, () -> {
            try {
                float[] before = new float[4];
                for (int i = 0; i < 4; i++)
                    before[i] = foes.get(i).getHealth();
                float friendBefore = friend.getHealth();
                int hits = walker.arc(foes.get(0));
                if (hits != com.solegendary.reignofnether.unit.units.brass.TeslaWalkerUnit.CHAIN_TARGETS)
                    helper.fail("the arc struck " + hits + " units, not " + com.solegendary.reignofnether.unit.units.brass.TeslaWalkerUnit.CHAIN_TARGETS);
                for (int i = 0; i < 3; i++)
                    if (foes.get(i).getHealth() >= before[i])
                        helper.fail("enemy " + (i + 1) + " on the line was not struck");
                if (foes.get(3).getHealth() < before[3])
                    helper.fail("the arc struck a fourth enemy");
                if (friend.getHealth() < friendBefore)
                    helper.fail("the arc struck a friend");
                if (walker.arc(friend) != 0 || friend.getHealth() < friendBefore)
                    helper.fail("an arc aimed at a friend should do nothing");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * Repair Crawler aura (RepairCrawlerUnit.mendAround): one second mends an own damaged machine (a Shield Projector)
     * and nothing else - not an own crew (a Sapper), not an enemy machine, not the crawler itself. Own arena, unique owners.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void repair_crawler_heals_only_friendly_machines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_crawler_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var crawler = com.solegendary.reignofnether.registrars.EntityRegistrar.REPAIR_CRAWLER_UNIT.get().create(level);
        var machine = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIELD_PROJECTOR_UNIT.get().create(level);
        var crew = com.solegendary.reignofnether.registrars.EntityRegistrar.SAPPER_UNIT.get().create(level);
        var foeMachine = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIELD_PROJECTOR_UNIT.get().create(level);
        Runnable cleanup = () -> {
            if (crawler != null) crawler.discard();
            if (machine != null) machine.discard();
            if (crew != null) crew.discard();
            if (foeMachine != null) foeMachine.discard();
        };
        if (crawler == null || machine == null || crew == null || foeMachine == null) {
            cleanup.run();
            helper.fail("could not create the Repair Crawler test units");
            return;
        }
        BlockPos c = helper.absolutePos(new BlockPos(7, 2, 7));
        crawler.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
        machine.moveTo(c.getX() + 2.5, c.getY(), c.getZ() + 0.5, 0, 0);
        crew.moveTo(c.getX() - 1.5, c.getY(), c.getZ() + 0.5, 0, 0);
        foeMachine.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 3.5, 0, 0);
        crawler.setOwnerName(owner);
        machine.setOwnerName(owner);
        crew.setOwnerName(owner);
        foeMachine.setOwnerName(foeOwner);
        level.addFreshEntity(crawler);
        level.addFreshEntity(machine);
        level.addFreshEntity(crew);
        level.addFreshEntity(foeMachine);
        helper.runAfterDelay(2, () -> {
            try {
                crawler.setHealth(crawler.getMaxHealth() - 20);
                machine.setHealth(machine.getMaxHealth() - 20);
                crew.setHealth(crew.getMaxHealth() - 10);
                foeMachine.setHealth(foeMachine.getMaxHealth() - 20);
                float self = crawler.getHealth(), m = machine.getHealth(), cr = crew.getHealth(), fm = foeMachine.getHealth();
                int mended = crawler.mendAround();
                if (mended != 1)
                    helper.fail("one second of the aura should mend exactly the one friendly machine, mended " + mended);
                if (Math.abs(machine.getHealth() - m - com.solegendary.reignofnether.unit.units.brass.RepairCrawlerUnit.HEAL_PER_SECOND) > 0.01f)
                    helper.fail("the own machine should gain " + com.solegendary.reignofnether.unit.units.brass.RepairCrawlerUnit.HEAL_PER_SECOND
                        + ", gained " + (machine.getHealth() - m));
                if (crew.getHealth() != cr)
                    helper.fail("the aura mended a crew (people, not machines)");
                if (foeMachine.getHealth() != fm)
                    helper.fail("the aura mended an enemy machine");
                if (crawler.getHealth() != self)
                    helper.fail("the crawler mended itself");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * Machine repair (brass/MachineRepair): the Engineer-Captain is a T2 worker with double build power and mends a
     * machine at ENGINEER_MULT x a Tinker's rate; a damaged crew and a damaged enemy machine in reach are never mended.
     * Each worker stands by its own damaged machine 10 blocks from the other pair. Own arena, unique owners.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void engineer_captain_repairs_machines_twice_as_fast(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_engineer_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var tinker = com.solegendary.reignofnether.registrars.EntityRegistrar.TINKER_UNIT.get().create(level);
        var engineer = com.solegendary.reignofnether.registrars.EntityRegistrar.ENGINEER_CAPTAIN_UNIT.get().create(level);
        var m1 = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIELD_PROJECTOR_UNIT.get().create(level);
        var m2 = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIELD_PROJECTOR_UNIT.get().create(level);
        var foeMachine = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIELD_PROJECTOR_UNIT.get().create(level);
        var crew = com.solegendary.reignofnether.registrars.EntityRegistrar.SAPPER_UNIT.get().create(level);
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>(java.util.Arrays.asList(tinker, engineer, m1, m2, foeMachine, crew));
        Runnable cleanup = () -> {
            for (var e : all)
                if (e != null) e.discard();
        };
        if (all.contains(null)) {
            cleanup.run();
            helper.fail("could not create the repair test units");
            return;
        }
        BlockPos p = helper.absolutePos(new BlockPos(2, 2, 3)), q = helper.absolutePos(new BlockPos(2, 2, 13));
        tinker.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        m1.moveTo(p.getX() + 2.5, p.getY(), p.getZ() + 0.5, 0, 0);
        foeMachine.moveTo(p.getX() - 1.5, p.getY(), p.getZ() + 0.5, 0, 0);   // in the Tinker's reach, never mended
        crew.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 2.5, 0, 0);
        engineer.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, 0, 0);
        m2.moveTo(q.getX() + 2.5, q.getY(), q.getZ() + 0.5, 0, 0);
        tinker.setOwnerName(owner);
        engineer.setOwnerName(owner);
        m1.setOwnerName(owner);
        m2.setOwnerName(owner);
        crew.setOwnerName(owner);
        foeMachine.setOwnerName(foeOwner);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(2, () -> {
            try {
                if (!com.solegendary.reignofnether.unit.T2Workers.isT2Worker(engineer) || com.solegendary.reignofnether.unit.T2Workers.isT2Worker(tinker))
                    helper.fail("the Engineer-Captain (and only it) should be a T2 worker");
                if (engineer.getBuildPower() < tinker.getBuildPower() * 1.99f)
                    helper.fail("the Engineer-Captain should build twice as fast: " + engineer.getBuildPower() + " vs " + tinker.getBuildPower());
                if (Math.abs(com.solegendary.reignofnether.brass.MachineRepair.hpPerSecond(engineer)
                        - com.solegendary.reignofnether.brass.MachineRepair.ENGINEER_MULT * com.solegendary.reignofnether.brass.MachineRepair.hpPerSecond(tinker)) > 0.01f)
                    helper.fail("the Engineer-Captain's repair rate should be " + com.solegendary.reignofnether.brass.MachineRepair.ENGINEER_MULT + "x a Tinker's");
                m1.setHealth(m1.getMaxHealth() - 20);
                m2.setHealth(m2.getMaxHealth() - 20);
                float h1 = m1.getHealth(), h2 = m2.getHealth();
                float t = com.solegendary.reignofnether.brass.MachineRepair.repairOnce(level, tinker, owner);
                float e = com.solegendary.reignofnether.brass.MachineRepair.repairOnce(level, engineer, owner);
                if (t <= 0 || Math.abs(m1.getHealth() - h1 - t) > 0.01f)
                    helper.fail("the Tinker should mend its machine: mended " + t + ", gained " + (m1.getHealth() - h1));
                if (Math.abs(e - com.solegendary.reignofnether.brass.MachineRepair.ENGINEER_MULT * t) > 0.01f || Math.abs(m2.getHealth() - h2 - e) > 0.01f)
                    helper.fail("the Engineer-Captain should mend " + com.solegendary.reignofnether.brass.MachineRepair.ENGINEER_MULT + "x the Tinker: " + e + " vs " + t);
                // its own machine mended full: the damaged crew and enemy machine in reach are never mended
                m1.setHealth(m1.getMaxHealth());
                crew.setHealth(crew.getMaxHealth() - 10);
                foeMachine.setHealth(foeMachine.getMaxHealth() - 10);
                float c0 = crew.getHealth(), f0 = foeMachine.getHealth();
                float none = com.solegendary.reignofnether.brass.MachineRepair.repairOnce(level, tinker, owner);
                if (none != 0 || crew.getHealth() != c0 || foeMachine.getHealth() != f0)
                    helper.fail("a Tinker mended a crew or an enemy machine (" + none + ")");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * The Clockwork Spider climbs low walls only (ClockworkSpiderUnit.wallClimbable): facing a 3-high wall it may climb,
     * facing a 5-high wall it may not, facing open ground there is nothing to stop. Own arena.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void clockwork_spider_climbs_only_low_walls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_spider_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var spider = com.solegendary.reignofnether.registrars.EntityRegistrar.CLOCKWORK_SPIDER_UNIT.get().create(level);
        if (spider == null) {
            helper.fail("could not create the Clockwork Spider");
            return;
        }
        BlockPos s = helper.absolutePos(new BlockPos(7, 2, 7));
        spider.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);   // yaw 0 faces south (+z)
        spider.setOwnerName(owner);
        spider.setHoldPosition(true);
        level.addFreshEntity(spider);
        helper.runAfterDelay(2, () -> {
            try {
                spider.setYRot(0);
                BlockPos front = spider.blockPosition().south();
                if (!spider.wallClimbable())
                    helper.fail("open ground ahead should never stop the spider");
                for (int h = 0; h < 3; h++)
                    level.setBlock(front.above(h), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                if (!spider.wallClimbable())
                    helper.fail("a 3-high wall should be climbable");
                for (int h = 3; h < 5; h++)
                    level.setBlock(front.above(h), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                if (spider.wallClimbable())
                    helper.fail("a 5-high wall should not be climbable");
                helper.succeed();
            } finally {
                spider.discard();
            }
        });
    }

    /**
     * The Rail Battery (Brassworks T2 defence, slice 3): dormant without Tier 2; with it, it locks a line on the nearest
     * enemy and charges (no damage yet), then the rail hits every enemy on that line - the target and one behind it -
     * but not a friend standing on the line nor an enemy off it; then it waits out its cooldown. The placement is never
     * put in the world and its targets are limited to this test's foe; everything happens in one tick and is discarded.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void rail_battery_charges_then_hits_every_enemy_on_its_line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_rail_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.RAIL_BATTERY, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.RailBatteryPlacement rail)) {
            helper.fail("the Rail Battery is not a RailBatteryPlacement: " + bp);
            return;
        }
        if (com.solegendary.reignofnether.bot.BotPlayer.t2TowerFor(Factions.BRASSWORKS) != Buildings.RAIL_BATTERY)
            helper.fail("the Brassworks bot does not raise Rail Batteries");
        rail.isBuilt = true;
        rail.onlyFoe = foeOwner;
        var type = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var near = type.create(level);
        var behind = type.create(level);
        var off = type.create(level);
        var friend = type.create(level);
        if (near == null || behind == null || off == null || friend == null) {
            helper.fail("could not create the Rail Battery test units");
            return;
        }
        BlockPos c = rail.centrePos;
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        near.moveTo(c.getX() + 0.5, gy, c.getZ() + 6.5, 0, 0);
        friend.moveTo(c.getX() + 0.5, gy, c.getZ() + 9.5, 0, 0);
        behind.moveTo(c.getX() + 0.5, gy, c.getZ() + 12.5, 0, 0);
        off.moveTo(c.getX() + 6.5, gy, c.getZ() + 9.5, 0, 0);
        friend.setOwnerName(owner);
        for (var e : List.of(near, behind, off))
            e.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(near, behind, off, friend);
        for (var e : all)
            level.addFreshEntity(e);
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            if (rail.startCharge(level))
                helper.fail("the Rail Battery charged without Tier 2");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            if (!rail.startCharge(level) || rail.charge != com.solegendary.reignofnether.building.buildings.placements.RailBatteryPlacement.CHARGE_TICKS) {
                helper.fail("the Rail Battery did not start charging at an enemy in range");
                return;
            }
            float n0 = near.getHealth(), b0 = behind.getHealth(), o0 = off.getHealth(), f0 = friend.getHealth();
            for (int t = 0; t < com.solegendary.reignofnether.building.buildings.placements.RailBatteryPlacement.CHARGE_TICKS - 1; t++)
                rail.tickRail(level);
            if (near.getHealth() < n0)
                helper.fail("the Rail Battery hurt something before its charge ended");
            rail.tickRail(level);   // the last charge tick: the rail fires
            if (near.getHealth() >= n0 || behind.getHealth() >= b0)
                helper.fail("the rail should hit the target and the enemy behind it on the line");
            if (off.getHealth() < o0)
                helper.fail("the rail hit an enemy off its line");
            if (friend.getHealth() < f0)
                helper.fail("the rail hit a friend on its line");
            if (rail.cooldown != com.solegendary.reignofnether.building.buildings.placements.RailBatteryPlacement.COOLDOWN_TICKS || rail.startCharge(level))
                helper.fail("the Rail Battery should wait out its cooldown after a shot");
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
            for (var e : all)
                e.discard();
        }
    }

    // ---- Brassworks slice 4: the Great Forge and the Iron Leviathan Engine (design/brassworks_plan.md) -----------------

    /**
     * Brassworks T3: the Foundry Hall offers Tier 3; the Great Forge is a Brassworks T3 lab that the server refuses from a
     * Tinker alone and accepts from an Engineer-Captain; it casts the Iron Leviathan Engine only once its owner has Tier
     * 3; it takes the lab levels and its three structures ship in data/; the bot kit techs to it; and the engine costs
     * the standard experimental price (pinned to the World Tree Walker's like the Celestial Dragon), dies with the T3
     * death blast, is a heating Machine with ~5x a Steam Juggernaut's health, and its drones are registered to the
     * faction, free and never counted in the army.
     */
    @GameTest(template = ARENA)
    public static void great_forge_needs_tier_3_and_an_engineer_captain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_t3_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var lab = Buildings.GREAT_FORGE;
        var engineItem = com.solegendary.reignofnether.building.production.ProductionItems.IRON_LEVIATHAN_ENGINE;
        var brass = Factions.BRASSWORKS;
        if (!Buildings.FOUNDRY_HALL.productions.get().contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3))
            helper.fail("the Foundry Hall does not offer Tier 3");
        if (!brass.equals(lab.getFaction()))
            helper.fail("the Great Forge is not a Brassworks building");
        if (!com.solegendary.reignofnether.unit.T2Workers.isT3Lab(lab))
            helper.fail("the Great Forge is not registered as a T3 lab");
        if (!lab.productions.get().contains(engineItem))
            helper.fail("the Great Forge does not cast the Iron Leviathan Engine");
        if (lab.maxLevel != com.solegendary.reignofnether.building.BuildingLevels.MAX_LEVEL)
            helper.fail("the Great Forge should take the lab levels");
        for (String nbt : List.of("great_forge", "great_forge_l2", "great_forge_l3"))
            if (com.solegendary.reignofnether.building.BuildingBlockData.getBuildingNbt(nbt, level.getServer().getResourceManager()) == null)
                helper.fail("the structure " + nbt + " is missing from data/");
        var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(brass);
        if (kit == null || kit.t3Building() != lab || kit.t3Unit() != engineItem)
            helper.fail("the Brassworks bot kit does not tech to the Great Forge and the Iron Leviathan Engine");
        var cost = com.solegendary.reignofnether.resources.ResourceCosts.IRON_LEVIATHAN_ENGINE;
        var wtwCost = com.solegendary.reignofnether.resources.ResourceCosts.WORLD_TREE_WALKER;
        if (cost.metal() != wtwCost.metal() || cost.energy() != wtwCost.energy() || cost.population != wtwCost.population)
            helper.fail("the Iron Leviathan Engine does not cost the standard experimental price: " + cost.metal() + "m vs " + wtwCost.metal() + "m");
        if (com.solegendary.reignofnether.barfx.BarFx.tierOf(cost.metal()) != 3)
            helper.fail("the Iron Leviathan Engine would not die with the T3 death blast");
        var droneType = com.solegendary.reignofnether.registrars.EntityRegistrar.CLOCKWORK_DRONE_UNIT.get();
        var engineType = com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get();
        if (com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(engineItem.getItemName()) != engineType)
            helper.fail("the Iron Leviathan Engine item names the wrong entity type");
        for (var type : List.<net.minecraft.world.entity.EntityType<?>>of(engineType, droneType))
            if (!brass.key.equals(Factions.ENTITY_FACTION.get(net.minecraft.world.entity.EntityType.getKey(type))))
                helper.fail(net.minecraft.world.entity.EntityType.getKey(type) + " is not registered to the Brassworks");

        BlockPos base = helper.absolutePos(new BlockPos(10, 2, 10));
        var tinker = com.solegendary.reignofnether.registrars.EntityRegistrar.TINKER_UNIT.get().create(level);
        var captain = com.solegendary.reignofnether.registrars.EntityRegistrar.ENGINEER_CAPTAIN_UNIT.get().create(level);
        var engine = engineType.create(level);   // never added
        var drone = droneType.create(level);     // never added
        if (tinker == null || captain == null || engine == null || drone == null) {
            helper.fail("could not create the units");
            return;
        }
        tinker.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        captain.moveTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
        level.addFreshEntity(tinker);
        level.addFreshEntity(captain);
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            float juggernaut = com.solegendary.reignofnether.unit.units.brass.SteamJuggernautUnit.maxHealth;
            if (engine.getMaxHealth() < 4 * juggernaut || engine.getMaxHealth() > 6 * juggernaut)
                helper.fail("Iron Leviathan Engine health " + engine.getMaxHealth() + " is not ~5x a Steam Juggernaut's " + juggernaut);
            if (engine.heatPerShot() <= 0)
                helper.fail("the Iron Leviathan Engine should heat up");
            if (drone.getCost() == null || drone.getCost().metal() != 0 || drone.getCost().population != 0 || !drone.isScout())
                helper.fail("a clockwork drone should be free, take no population and stay out of the army counts");
            if (com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, new int[] { tinker.getId() }))
                helper.fail("the Great Forge may be placed by a Tinker alone");
            if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, new int[] { tinker.getId(), captain.getId() }))
                helper.fail("the Great Forge refused an Engineer-Captain among the builders");
            var placed = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(lab, base.offset(-80, 0, -80),
                Rotation.NONE, owner, new int[] { tinker.getId() }, false, false, false, true);
            if (placed != null) {
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placed);
                helper.fail("placeBuilding accepted a Great Forge from a Tinker");
            }
            var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(lab, level,
                helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
                helper.fail("the Great Forge is not a production placement: " + bp);
                return;
            }
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            if (engineItem.canProduce(pp))
                helper.fail("the Iron Leviathan Engine can be cast with only Tier 2");
            if (pp.startProductionItem(engineItem) || !pp.productionQueue.isEmpty())
                helper.fail("the server queued an Iron Leviathan Engine without Tier 3");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
            if (!engineItem.canProduce(pp))
                helper.fail("the Iron Leviathan Engine still refused after Tier 3: " + engineItem.getProduceErrorMsg(pp));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
            tinker.discard();
            captain.discard();
            engine.discard();
            drone.discard();
        }
    }

    /** Advances a machine that is not in the world by {@code ticks} of Heat (cooling, vent countdown) - its own clock. */
    static void heatTicks(com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit e, int ticks) {
        for (int t = 0; t < ticks; t++) {
            e.tickCount++;
            com.solegendary.reignofnether.brass.Heat.tick(e, e);
        }
    }

    /**
     * Overheat (IronLeviathanEngineUnit, brass/Heat): with a shell every 4 s the engine cools between shots but not
     * enough - the first four shells leave it firing, the fifth starts the 6 s vent; while venting the cannon is silent
     * and it takes 25% more damage; when the vent ends it is cold and fires again. The engine is never added to the
     * world (so nothing near it can make it shoot on its own): its Heat clock is advanced by hand; its shells land on
     * empty ground inside this test's arena.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void iron_leviathan_engine_vents_after_sustained_fire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_engine_heat_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var engine = com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get().create(level);
        if (engine == null) {
            helper.fail("could not create the Iron Leviathan Engine");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos aim = helper.absolutePos(new BlockPos(12, 2, 12));
        engine.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        engine.setOwnerName(owner);
        try {
            int gap = (int) (20 / com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.attacksPerSecond);   // 4 s
            for (int shot = 1; shot <= 4; shot++) {
                if (!engine.fireCannon(aim.getX() + 0.5, aim.getY(), aim.getZ() + 0.5)) {
                    helper.fail("shell " + shot + " did not fire (heat " + engine.heat() + ")");
                    return;
                }
                if (com.solegendary.reignofnether.brass.Heat.isVenting(engine)) {
                    helper.fail("the engine vented after only " + shot + " shells (heat " + engine.heat() + ")");
                    return;
                }
                heatTicks(engine, gap);
            }
            if (!engine.fireCannon(aim.getX() + 0.5, aim.getY(), aim.getZ() + 0.5) || !com.solegendary.reignofnether.brass.Heat.isVenting(engine)) {
                helper.fail("the fifth shell of sustained fire should start the vent (heat " + engine.heat() + ", vent " + engine.ventTicks() + ")");
                return;
            }
            if (engine.fireCannon(aim.getX() + 0.5, aim.getY(), aim.getZ() + 0.5))
                helper.fail("the cannon fired while venting");
            // +25% damage while venting: the same armour-piercing hit, venting vs cold
            engine.invulnerableTime = 0;
            float h0 = engine.getHealth();
            engine.hurt(level.damageSources().magic(), 20f);
            float hotTaken = h0 - engine.getHealth();
            heatTicks(engine, com.solegendary.reignofnether.brass.Heat.VENT_TICKS);
            if (com.solegendary.reignofnether.brass.Heat.isVenting(engine) || engine.heat() != 0)
                helper.fail("the vent should end cold after " + com.solegendary.reignofnether.brass.Heat.VENT_TICKS + " ticks (heat " + engine.heat() + ")");
            engine.invulnerableTime = 0;
            float h1 = engine.getHealth();
            engine.hurt(level.damageSources().magic(), 20f);
            float coldTaken = h1 - engine.getHealth();
            if (coldTaken <= 0 || Math.abs(hotTaken - coldTaken * com.solegendary.reignofnether.brass.Heat.VENT_DAMAGE_TAKEN) > 0.6f)
                helper.fail("venting should take " + com.solegendary.reignofnether.brass.Heat.VENT_DAMAGE_TAKEN + "x damage: " + hotTaken + " vs " + coldTaken);
            if (!engine.fireCannon(aim.getX() + 0.5, aim.getY(), aim.getZ() + 0.5))
                helper.fail("the cannon should fire again after the vent");
            helper.succeed();
        } finally {
            engine.discard();
        }
    }

    /**
     * Drones (IronLeviathanEngineUnit.launchDrones): a wave launches two drones owned by the engine's player; a second
     * wave fills the cap of four and a third launches none; a lost drone is replaced by the next wave; and when the engine
     * goes, every drone falls apart with it. The engine is never added; its drones are, and are gone by the end.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void iron_leviathan_engine_drones_spawn_cap_and_die_with_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_engine_drones_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var engine = com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get().create(level);
        if (engine == null) {
            helper.fail("could not create the Iron Leviathan Engine");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        engine.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        engine.setOwnerName(owner);
        List<com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit> mine = new ArrayList<>();
        Runnable collect = () -> {
            mine.clear();
            for (var d : level.getEntitiesOfClass(com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit.class,
                    engine.getBoundingBox().inflate(12)))
                if (d.getEngine() == engine && d.isAlive())
                    mine.add(d);
        };
        try {
            int a = engine.launchDrones(level);
            collect.run();
            if (a != com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.DRONES_PER_WAVE || engine.liveDrones() != a || mine.size() != a)
                helper.fail("a wave should launch two drones: launched " + a + ", live " + engine.liveDrones() + ", found " + mine.size());
            for (var d : mine)
                if (!owner.equals(d.getOwnerName()))
                    helper.fail("a drone is not owned by the engine's player: " + d.getOwnerName());
            int b = engine.launchDrones(level);
            int c = engine.launchDrones(level);
            if (b != 2 || c != 0 || engine.liveDrones() != com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.DRONE_CAP)
                helper.fail("drones should cap at " + com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.DRONE_CAP
                    + ": second wave " + b + ", third " + c + ", live " + engine.liveDrones());
            collect.run();
            if (!mine.isEmpty())
                mine.get(0).discard();   // one shot down
            if (engine.launchDrones(level) != 1 || engine.liveDrones() != 4)
                helper.fail("a lost drone should be replaced by the next wave (live " + engine.liveDrones() + ")");
            collect.run();
            List<com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit> before = new ArrayList<>(mine);
            engine.discard();   // the engine goes: its drones go with it
            for (var d : before)
                if (!d.isRemoved())
                    helper.fail("a drone outlived its engine");
            helper.succeed();
        } finally {
            engine.discard();
            collect.run();
            for (var d : mine)
                d.discard();
        }
    }

    /**
     * Weak from below (IronLeviathanEngineUnit.hurt): the same blow from a Sapper or a Drill Burrower does
     * BELOW_DAMAGE_MULT x what it does from a Rifle Squad. Nothing is added to the world.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void iron_leviathan_engine_takes_bonus_damage_from_sappers_and_burrowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_engine_below_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        var engine = com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get().create(level);
        var sapper = com.solegendary.reignofnether.registrars.EntityRegistrar.SAPPER_UNIT.get().create(level);
        var burrower = com.solegendary.reignofnether.registrars.EntityRegistrar.DRILL_BURROWER_UNIT.get().create(level);
        var rifle = com.solegendary.reignofnether.registrars.EntityRegistrar.RIFLE_SQUAD_UNIT.get().create(level);
        if (engine == null || sapper == null || burrower == null || rifle == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        engine.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        engine.setOwnerName(owner);
        for (var e : List.<net.minecraft.world.entity.Mob>of(sapper, burrower, rifle)) {
            e.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) e).setOwnerName(foeOwner);
        }
        try {
            float plain = blow(level, engine, rifle);
            float fromSapper = blow(level, engine, sapper);
            float fromBurrower = blow(level, engine, burrower);
            float k = com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.BELOW_DAMAGE_MULT;
            if (plain <= 0)
                helper.fail("the control blow did no damage");
            else if (Math.abs(fromSapper - plain * k) > 0.5f || Math.abs(fromBurrower - plain * k) > 0.5f)
                helper.fail("Sappers and Drill Burrowers should hit " + k + "x: sapper " + fromSapper + ", burrower " + fromBurrower + " vs " + plain);
            helper.succeed();
        } finally {
            engine.discard();
            sapper.discard();
            burrower.discard();
            rifle.discard();
        }
    }

    /** One explicit 10-damage melee blow from {@code from} on a healed, cold engine; returns the health it lost. */
    static float blow(ServerLevel level, com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit engine,
                      net.minecraft.world.entity.LivingEntity from) {
        engine.setHealth(engine.getMaxHealth());
        engine.invulnerableTime = 0;
        float h = engine.getHealth();
        // an explicit amount: RoN would otherwise rewrite each attacker's blow to its own attack damage
        com.solegendary.reignofnether.unit.UnitDamage.hurtExplicit(engine, level.damageSources().mobAttack(from), 10f);
        return h - engine.getHealth();
    }
}
