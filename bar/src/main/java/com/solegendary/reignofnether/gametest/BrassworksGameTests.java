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
        if (units != 7)
            helper.fail("the Assembly Works should train seven units, found " + units);
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
}
