package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.bot.BotTuning;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.TierGate;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2 is an investment (resources/TierGate; lovish, Oct 22). The cost and time constants, the server-side gate on every
 * faction's T2 lab (placement and production), the extractors' energy drain (a T2 extractor's metal drops when energy is
 * short and recovers when it is not), the converter as a last resort, and a bot skirmish that checks bots tech after they
 * field T1 units, never before minute 6, and within a window.
 * <p>
 * Own holder. Placements are made with getNewBuildingPlacement and only put in the world's building list inside one
 * synchronous call (and taken out again), so no other test can see them.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TierGateGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Metal/s of the opener the gate is tuned against: the capitol + 7 T1 extractors. */
    static final float OPENER_METAL_PER_SECOND = 16f;

    // ---------------------------------------------------------------------------------------------- the numbers

    /** The costs the game really charges are the constants of TierGate, and the gate is dear and slow in the way it claims. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void tier_gate_costs_and_times_are_the_tuning_constants(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var t2 = ResourceCosts.RESEARCH_TIER_2;
        if (t2.metal() != TierGate.T2_RESEARCH_METAL || t2.energy() != TierGate.T2_RESEARCH_ENERGY
                || t2.ticks != TierGate.T2_RESEARCH_SECONDS * 20)
            problems.add("Tier 2 research costs " + t2.metal() + "m/" + t2.energy() + "e/" + t2.ticks / 20 + "s, TierGate says "
                + TierGate.T2_RESEARCH_METAL + "/" + TierGate.T2_RESEARCH_ENERGY + "/" + TierGate.T2_RESEARCH_SECONDS);
        var refit = ResourceCosts.UPGRADE_EXTRACTOR;
        if (refit.metal() != TierGate.UPGRADE_EXTRACTOR_METAL || refit.energy() != TierGate.UPGRADE_EXTRACTOR_ENERGY
                || refit.ticks != TierGate.UPGRADE_EXTRACTOR_SECONDS * 20)
            problems.add("the extractor refit costs " + refit.metal() + "m/" + refit.energy() + "e/" + refit.ticks / 20 + "s");
        // every faction's T2 lab: the same price and the same build time (the art's block counts differ 6x)
        for (Building lab : TierGate.t2Labs()) {
            if (lab.cost.metal() != TierGate.T2_LAB_METAL || lab.cost.energy() != TierGate.T2_LAB_ENERGY)
                problems.add(lab.name + " costs " + lab.cost.metal() + "m/" + lab.cost.energy() + "e, not "
                    + TierGate.T2_LAB_METAL + "/" + TierGate.T2_LAB_ENERGY);
            if (lab.getBuildTimeBlocks() != TierGate.T2_LAB_BUILD_BLOCKS || lab.getBuildTimeModifier() != 1f)
                problems.add(lab.name + " builds in " + lab.getBuildTimeBlocks() + " blocks x " + lab.getBuildTimeModifier()
                    + ", not " + TierGate.T2_LAB_BUILD_BLOCKS + " x 1");
        }
        if (TierGate.t2Labs().size() != 7)
            problems.add("seven factions, but " + TierGate.t2Labs().size() + " T2 labs");
        // lovish, Oct 24: tier 2 came too fast and its economy jump made tier 1 useless - a softer refit (4.5 metal/s, 300
        // metal, 75 s), a 180 s research, and the bots a minute later. These are his numbers, not tuning room.
        if (MetalExtractor.T2_METAL_INCOME != 4.5f)
            problems.add("a T2 extractor makes " + MetalExtractor.T2_METAL_INCOME + " metal/s, lovish set 4.5");
        if (TierGate.UPGRADE_EXTRACTOR_METAL != 300 || TierGate.UPGRADE_EXTRACTOR_ENERGY != 600 || TierGate.UPGRADE_EXTRACTOR_SECONDS != 75)
            problems.add("the refit is " + TierGate.UPGRADE_EXTRACTOR_METAL + "m/" + TierGate.UPGRADE_EXTRACTOR_ENERGY + "e/"
                + TierGate.UPGRADE_EXTRACTOR_SECONDS + "s, lovish set 300/600/75");
        // lovish, Oct 29: the research's and the T2 lab's energy trimmed by a quarter (energy is spent everywhere now)
        if (TierGate.T2_RESEARCH_METAL != 1000 || TierGate.T2_RESEARCH_ENERGY != 7560 || TierGate.T2_RESEARCH_SECONDS != 180)
            problems.add("Tier 2 research is " + TierGate.T2_RESEARCH_METAL + "m/" + TierGate.T2_RESEARCH_ENERGY + "e/"
                + TierGate.T2_RESEARCH_SECONDS + "s, lovish set 1000/7560/180");
        if (TierGate.T2_LAB_ENERGY != 4500)
            problems.add("the T2 lab costs " + TierGate.T2_LAB_ENERGY + " energy, lovish set 4500");
        if (BotTuning.EASY.t2Minute() != 9 || BotTuning.MEDIUM.t2Minute() != 7 || BotTuning.HARD.t2Minute() != 5)
            problems.add("bots start Tier 2 at minutes " + BotTuning.EASY.t2Minute() + "/" + BotTuning.MEDIUM.t2Minute() + "/"
                + BotTuning.HARD.t2Minute() + " (easy/medium/hard), lovish set 9/7/5");
        // tier 1 stays worth having: a refit no more than ~2.25x's a T1 extractor, and it pays its metal back in no less
        // than two minutes of the extra income (counting the drill's own downtime while it refits)
        float t1 = Buildings.METAL_EXTRACTOR_VILLAGERS.getMetalIncome();
        if (MetalExtractor.T2_METAL_INCOME > 2.25f * t1)
            problems.add("a T2 extractor is " + MetalExtractor.T2_METAL_INCOME / t1 + "x a T1 one");
        float payback = (TierGate.UPGRADE_EXTRACTOR_METAL + t1 * TierGate.UPGRADE_EXTRACTOR_SECONDS) / (MetalExtractor.T2_METAL_INCOME - t1);
        if (payback < 120f)
            problems.add("a refit pays its metal back in " + payback + " s");
        // the package is a real investment: at least 100 s of the 16 metal/s opener (it was 40 s), and Tier 2 stays under Tier 3
        float seconds = TierGate.T2_PACKAGE_METAL / OPENER_METAL_PER_SECOND;
        if (seconds < 100f)
            problems.add("the Tier 2 package is only " + seconds + " s of the opener's income");
        if (t2.metal() >= ResourceCosts.RESEARCH_TIER_3.metal())
            problems.add("Tier 2 research (" + t2.metal() + "m) is not under Tier 3's (" + ResourceCosts.RESEARCH_TIER_3.metal() + "m)");
        // energy is a real gate: the research alone pulls more energy per second than a 3-wind base (capitol + 3 x 14)
        // makes. Was "4-wind" (56/s) until lovish trimmed the research to 42/s (Oct 29): units and labs now cost energy too,
        // so the research no longer has to be the whole gate on its own
        float researchEnergyPerSecond = TierGate.T2_RESEARCH_ENERGY / (float) TierGate.T2_RESEARCH_SECONDS;
        if (researchEnergyPerSecond < 3 * 14f)
            problems.add("the research drains only " + researchEnergyPerSecond + " energy/s");
        if (TierGate.T2_EXTRACTOR_ENERGY_UPKEEP <= TierGate.T1_EXTRACTOR_ENERGY_UPKEEP)
            problems.add("a T2 extractor must drain more energy than a T1 one");
        // assisting speeds the lab up (BAR): builder-seconds are fixed, so twice the build power is half the time
        float work = TierGate.T2_LAB_BUILD_BLOCKS * Building.BUILD_TICKS_PER_BLOCK / 20f;
        if (work / 1f < 360f)
            problems.add("one worker would raise the T2 lab in " + work + " s - assisting should matter");
        if (Math.abs(work / 12f - (work / 6f) / 2f) > 0.01f || work / 6f > 120f)
            problems.add("builder-seconds " + work + ": 6 build power " + work / 6f + " s");
        // the converter: a last resort, at most half a metal per second per building, 100 energy a metal
        if (EconomyServerEvents.CONVERSION_RATIO != TierGate.CONVERTER_ENERGY_PER_METAL)
            problems.add("the converter ratio is " + EconomyServerEvents.CONVERSION_RATIO);
        float metalPerSecond = TierGate.CONVERTER_ENERGY_PER_SECOND / TierGate.CONVERTER_ENERGY_PER_METAL;
        if (metalPerSecond > 0.5f || Buildings.ENERGY_CONVERTER_VILLAGERS.getEnergyConversion() != TierGate.CONVERTER_ENERGY_PER_SECOND)
            problems.add("a converter makes " + metalPerSecond + " metal/s (building says "
                + Buildings.ENERGY_CONVERTER_VILLAGERS.getEnergyConversion() + " energy/s)");
        // and cannot replace extractors: one converter's metal costs more building metal per metal/s than an extractor's
        float converterBuildMetalPerMetalSecond = (ResourceCosts.ENERGY_CONVERTER.metal() + 100f / 14f * ResourceCosts.WIND_GENERATOR.metal()) / metalPerSecond;
        float extractorBuildMetalPerMetalSecond = ResourceCosts.METAL_EXTRACTOR.metal() / 2f;
        if (converterBuildMetalPerMetalSecond < 5 * extractorBuildMetalPerMetalSecond)
            problems.add("a converter's metal costs " + converterBuildMetalPerMetalSecond + " building-metal per metal/s against an extractor's "
                + extractorBuildMetalPerMetalSecond);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ---------------------------------------------------------------------------------- the server-side lab gate

    private static ProductionPlacement stamped(GameTestHelper helper, Building b, String owner) {
        var bp = BuildingUtils.getNewBuildingPlacement(b, helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)),
            Rotation.NONE, owner, false);
        if (!(bp instanceof ProductionPlacement pp))
            return null;
        pp.isBuilt = true;
        return pp;
    }

    /**
     * Every faction's T2 lab: refused on placement without Tier 2, refused without the finished T1 lab even with it, allowed
     * with both - through the rule and through the real placeBuilding (a crafted packet skips the build button's grey-out).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void t2_lab_is_refused_server_side_for_every_faction_without_tier_2_and_its_t1_lab(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        List<BuildingPlacement> added = new ArrayList<>();
        String salt = Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        try {
            for (Building lab : TierGate.t2Labs()) {
                String owner = "gametest_tier_gate_" + lab.structureName + "_" + salt;
                Building t1 = TierGate.t1LabFor(lab);
                if (t1 == null) {
                    problems.add(lab.name + " has no T1 lab in the table");
                    continue;
                }
                if (!TierGate.NEEDS_TIER_2.equals(TierGate.placementError(owner, lab)))
                    problems.add(lab.name + " without research: " + TierGate.placementError(owner, lab));
                int before = BuildingServerEvents.getBuildings().size();
                // the real path: a crafted placement packet (not a command, not the sandbox, no cheat)
                var placed = BuildingServerEvents.placeBuilding(lab, helper.absolutePos(new BlockPos(8, 1, 8)), Rotation.NONE,
                    owner, new int[0], false, false, false, true);
                if (placed != null) {
                    BuildingServerEvents.getBuildings().remove(placed);
                    problems.add("placeBuilding accepted " + lab.name + " from an owner without Tier 2");
                }
                ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
                if (!TierGate.NEEDS_T1_LAB.equals(TierGate.placementError(owner, lab)))
                    problems.add(lab.name + " with research but no " + t1.name + ": " + TierGate.placementError(owner, lab));
                var refused = BuildingServerEvents.placeBuilding(lab, helper.absolutePos(new BlockPos(8, 1, 8)), Rotation.NONE,
                    owner, new int[0], false, false, false, true);
                if (refused != null) {
                    BuildingServerEvents.getBuildings().remove(refused);
                    problems.add("placeBuilding accepted " + lab.name + " with Tier 2 but no finished " + t1.name);
                }
                // an UNFINISHED T1 lab does not count; a finished one does (its owner's, only)
                var t1Site = stamped(helper, t1, owner);
                if (t1Site == null) {
                    problems.add(t1.name + " is not a production placement");
                    continue;
                }
                t1Site.isBuilt = false;
                BuildingServerEvents.getBuildings().add(t1Site);
                added.add(t1Site);
                if (!TierGate.NEEDS_T1_LAB.equals(TierGate.placementError(owner, lab)))
                    problems.add(lab.name + " accepted an unfinished " + t1.name);
                t1Site.isBuilt = true;
                if (TierGate.placementError(owner, lab) != null)
                    problems.add(lab.name + " refused with Tier 2 and a finished " + t1.name + ": " + TierGate.placementError(owner, lab));
                if (TierGate.placementError(owner + "_other", lab) == null)
                    problems.add(lab.name + " let another owner use " + owner + "'s " + t1.name);
                if (BuildingServerEvents.getBuildings().size() != before + 1)
                    problems.add("placeBuilding left a stray building behind for " + lab.name);
                ResearchServerEvents.removeAllResearchFor(owner);
                // anything that is not a T2 lab is never held by the gate
                if (TierGate.placementError(owner, t1) != null)
                    problems.add("the gate held " + t1.name + ", which is not a T2 lab");
            }
        } finally {
            for (BuildingPlacement bp : added)
                BuildingServerEvents.getBuildings().remove(bp);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * A T2 lab produces nothing without Tier 2 on any faction, and the extractor refit needs it too - on the server, where
     * a stale card or a crafted packet cannot skip it (before: four factions' unit items checked, the Sunforged, Gravebound
     * and Horde labs and the refit did not).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void t2_lab_production_and_extractor_refit_need_tier_2_on_the_server(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = "gametest_tier_gate_prod_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        Resources pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        try {
            for (Building lab : TierGate.t2Labs()) {
                var pp = stamped(helper, lab, owner);
                if (pp == null) {
                    problems.add(lab.name + " is not a production placement");
                    continue;
                }
                ProductionItem item = ((com.solegendary.reignofnether.building.production.ProductionBuilding) lab).productions.get().get(0);
                if (pp.startProductionItem(item) || !pp.productionQueue.isEmpty())
                    problems.add(lab.name + " queued " + item.getItemName() + " without Tier 2");
                if (!TierGate.NEEDS_TIER_2.equals(TierGate.productionError(pp, item)))
                    problems.add(lab.name + " productionError without Tier 2 was " + TierGate.productionError(pp, item));
            }
            var mex = stamped(helper, Buildings.METAL_EXTRACTOR_VILLAGERS, owner);
            if (mex == null) {
                problems.add("the extractor is not a production placement");
            } else {
                var refit = ProductionItems.UPGRADE_EXTRACTOR;
                if (mex.startProductionItem(refit) || !mex.productionQueue.isEmpty())
                    problems.add("the extractor refit started without Tier 2");
                ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
                if (TierGate.productionError(mex, refit) != null)
                    problems.add("the refit is held with Tier 2: " + TierGate.productionError(mex, refit));
                if (!mex.startProductionItem(refit))
                    problems.add("the refit did not start with Tier 2");
                for (Building lab : TierGate.t2Labs()) {
                    var pp = stamped(helper, lab, owner);
                    ProductionItem item = ((com.solegendary.reignofnether.building.production.ProductionBuilding) lab).productions.get().get(0);
                    if (pp != null && TierGate.productionError(pp, item) != null)
                        problems.add(lab.name + " is held with Tier 2: " + TierGate.productionError(pp, item));
                }
            }
        } finally {
            ResearchServerEvents.removeAllResearchFor(owner);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ---------------------------------------------------------------------------------- energy under the extractors

    /**
     * A T2 extractor's output drops when energy is short and recovers when it is not (BAR: mohos need energy). Two refitted
     * extractors on their own pool and economy: 9 metal/s for 40 energy/s. Dry pool: nothing; half the drain: half the
     * metal; plenty: all of it; and the HUD's smoothed figure follows both ways.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void t2_extractor_output_drops_when_energy_is_short_and_recovers_when_it_is_not(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = "gametest_tier_gate_energy_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        var eco = EconomyServerEvents.getEconomy(owner);
        final float t2Pair = 2 * MetalExtractor.T2_METAL_INCOME;   // two T2 extractors (4.5 metal/s each)
        eco.metalIncome = 2f + t2Pair;   // + the capitol
        eco.extractorMetal = t2Pair;
        eco.extractorUpkeep = 2 * TierGate.T2_EXTRACTOR_ENERGY_UPKEEP;
        eco.extractorEfficiency = 1f;
        float drainPerTick = eco.extractorUpkeep / 20f;
        Resources res = new Resources(owner, 0, 0, 0);
        try {
            // no energy: the extractors make none of their metal
            float share = EconomyServerEvents.payExtractorUpkeep(res, eco);
            if (share != 0f || res.getEnergy() != 0f)
                problems.add("a dry pool paid " + share + " of the drain");
            float metalPerSecond = 2f + t2Pair - eco.extractorMetal * (1f - share);
            if (Math.abs(metalPerSecond - 2f) > 0.001f)
                problems.add("with no energy the economy makes " + metalPerSecond + " metal/s, expected the capitol's 2");
            // half the drain in the pool: half the extractors' metal
            res.addEnergy(drainPerTick / 2f);
            share = EconomyServerEvents.payExtractorUpkeep(res, eco);
            if (Math.abs(share - 0.5f) > 0.01f)
                problems.add("half the drain in the pool gave a share of " + share);
            // plenty of energy: all of it, and the drain is really taken from the pool
            res.addEnergy(1000f);
            float before = res.getEnergy();
            share = EconomyServerEvents.payExtractorUpkeep(res, eco);
            if (share != 1f || Math.abs((before - res.getEnergy()) - drainPerTick) > 0.001f)
                problems.add("a full pool paid " + share + " and lost " + (before - res.getEnergy()) + " (drain " + drainPerTick + ")");
            // the HUD figure follows the share both ways: starve it for 2 s, then feed it for 2 s
            eco.extractorEfficiency = 1f;
            res.addEnergy(-res.getEnergy());
            for (int i = 0; i < 40; i++)
                EconomyServerEvents.payExtractorUpkeep(res, eco);
            float starved = eco.effectiveMetalIncome();
            if (starved > 2f + t2Pair * 0.2f)
                problems.add("after 2 s without energy the income still shows " + starved + " metal/s");
            res.addEnergy(5000f);
            for (int i = 0; i < 60; i++)
                EconomyServerEvents.payExtractorUpkeep(res, eco);
            float recovered = eco.effectiveMetalIncome();
            if (recovered < 2f + t2Pair * 0.95f)
                problems.add("3 s after energy returned the income is only " + recovered + " metal/s");
            // T1 extractors drain TierGate's T1 figure and are never throttled below it: no upkeep, no change
            eco.extractorUpkeep = 0f;
            eco.extractorMetal = 0f;
            res.addEnergy(-res.getEnergy());
            if (EconomyServerEvents.payExtractorUpkeep(res, eco) != 1f)
                problems.add("an economy with no draining extractors was throttled");
        } finally {
            eco.metalIncome = 0f;
            eco.extractorMetal = 0f;
            eco.extractorUpkeep = 0f;
            eco.extractorEfficiency = 1f;
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The same chain from real placements: a finished T1 extractor drains TierGate's T1 figure, the same one refitted to the
     * T2 tower drains the T2 figure and earns 4.5 metal/s, and the economy's recalculation puts both into the owner's economy.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void refitted_extractor_drains_energy_and_the_economy_counts_it(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = "gametest_tier_gate_mex_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        Resources pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        var mex = stamped(helper, Buildings.METAL_EXTRACTOR_VILLAGERS, owner);
        if (mex == null) {
            ResourcesServerEvents.resourcesList.remove(pool);
            helper.fail("the extractor is not a production placement");
            return;
        }
        try {
            if (mex.getEnergyUpkeep() != TierGate.T1_EXTRACTOR_ENERGY_UPKEEP)
                problems.add("a T1 extractor drains " + mex.getEnergyUpkeep());
            mex.changeStructure("metal_extractor_t2");
            if (mex.getUpgradeLevel() <= 0) {
                problems.add("the refit structure did not read as Tier 2");
            } else {
                if (mex.getEnergyUpkeep() != TierGate.T2_EXTRACTOR_ENERGY_UPKEEP)
                    problems.add("a T2 extractor drains " + mex.getEnergyUpkeep() + ", TierGate says " + TierGate.T2_EXTRACTOR_ENERGY_UPKEEP);
                BuildingServerEvents.getBuildings().add(mex);
                try {
                    EconomyServerEvents.recalculateIncomeAndStorage();
                } finally {
                    BuildingServerEvents.getBuildings().remove(mex);
                }
                var eco = EconomyServerEvents.getEconomy(owner);
                // (4.5 metal/s on a plain patch; a richer patch nearby, stamped by another test, only raises it)
                if (Math.abs(eco.extractorUpkeep - TierGate.T2_EXTRACTOR_ENERGY_UPKEEP) > 0.01f
                        || eco.extractorMetal < MetalExtractor.T2_METAL_INCOME - 0.01f || Math.abs(eco.extractorMetal - mex.getMetalIncome()) > 0.01f)
                    problems.add("the economy counts " + eco.extractorMetal + " metal/s for " + eco.extractorUpkeep + " energy/s, expected "
                        + MetalExtractor.T2_METAL_INCOME + "+ for "
                        + TierGate.T2_EXTRACTOR_ENERGY_UPKEEP);
                // refit running: the drill is stopped, so it neither earns nor drains
                var refit = ProductionItems.UPGRADE_EXTRACTOR;
                mex.productionQueue.add(new com.solegendary.reignofnether.building.production.ActiveProduction(refit, false, owner));
                if (mex.getEnergyUpkeep() != 0f || mex.getMetalIncome() != 0f)
                    problems.add("an extractor in the middle of a refit drains " + mex.getEnergyUpkeep() + " and earns " + mex.getMetalIncome());
                mex.productionQueue.clear();
                EconomyServerEvents.getEconomy(owner).extractorMetal = 0f;
                EconomyServerEvents.getEconomy(owner).extractorUpkeep = 0f;
            }
        } finally {
            BuildingServerEvents.getBuildings().remove(mex);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ------------------------------------------------------------------------------------------------ the bots

    /**
     * Game-minutes the bot skirmish may run: the latest bot (easy) techs from minute 9, researches 3 min (longer when it
     * stalls: on main 48f6bbc the easy bot's 120 s research took 146 s and its lab 2:52 more, done at 13:19), then raises
     * the lab while its refits stop drills for 75 s each - ~15:30-16:30 now, so 18 leaves room (Oct 24: was 15).
     */
    static final int BOT_T2_MINUTES = 18;
    /** The earliest game-minute any bot may hold Tier 2 (lovish, Oct 24: was 5). */
    static final int BOT_T2_EARLIEST_MINUTE = 6;
    static final int BOT_T2_TICKS = 20 * 60 * BOT_T2_MINUTES;

    private static int fighters(String owner) {
        int n = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof Unit u && owner.equals(u.getOwnerName()) && le.isAlive()
                    && le instanceof AttackerUnit && !(le instanceof WorkerUnit))
                n++;
        return n;
    }

    /**
     * Skirmish: a hard Sunforged, a medium Horde and an easy Gravebound bot (allies, so nothing interrupts the tech) play
     * until each has its T2 lab or {@value #BOT_T2_MINUTES} game-minutes pass. Each must have started the research with a
     * fighting force fielded (T1 units first), no earlier than its tuning's minute, finished it no earlier than minute 6 and
     * raised the T2 lab inside the window - under the new cost the bots still tech, in their own order (hard, medium, easy).
     * The Horde rides along because its Flame Sanctuary needs a Hoglin Stables the bot has to raise for the server's gate.
     * Logs a {@code [T2Timing]} line (minutes) so the nightly bot ladder's numbers can be compared.
     */
    @GameTest(template = ARENA, timeoutTicks = BOT_T2_TICKS + 1200)
    public static void bots_reach_tier_2_after_fielding_t1_units_and_not_before_minute_6(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String[] names = { "gametest_t2_hard", "gametest_t2_medium", "gametest_t2_easy" };
        Faction[] factions = { Factions.VILLAGERS, Factions.PIGLINS, Factions.MONSTERS };
        BotPlayer.Difficulty[] levels = { BotPlayer.Difficulty.HARD, BotPlayer.Difficulty.MEDIUM, BotPlayer.Difficulty.EASY };
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        // the soak's own scan (16 steps along each of 8 lines): the first step of each line alone never held a dry row
        Vec3[] starts = SkirmishGameTests.soakAnyRow(level, origin.getX() + 1500, origin.getZ(), 3);
        if (starts == null) {
            helper.fail("no dry ground for three bot bases near x=" + (origin.getX() + 1500) + " (8 lines x 16 steps tried)");
            return;
        }
        var bots = BotServerEvents.brains;
        var failures = BotServerEvents.thinkFailures;
        final int count = names.length;
        for (int i = 0; i < count; i++) {
            failures.remove(names[i]);
            PlayerServerEvents.startRTSBot(level, names[i], starts[i], factions[i], 0);
            bots.put(names[i], new BotPlayer(names[i], factions[i], levels[i], BlockPos.containing(starts[i])));
        }
        for (int i = 0; i < count; i++)
            for (int j = i + 1; j < count; j++)
                com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(names[i], names[j]);
        long t0 = level.getGameTime();
        long[] researchStart = { -1, -1, -1 }, researchDone = { -1, -1, -1 }, labDone = { -1, -1, -1 };
        int[] fightersAtStart = new int[count];
        boolean[] finished = { false };
        Runnable finish = () -> {
            if (finished[0])
                return;
            finished[0] = true;
            long now = level.getGameTime() - t0;
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                BotTuning tuning = BotTuning.of(levels[i]);
                String tag = names[i].substring(12) + " (" + levels[i] + " " + factions[i].getName() + ")";
                if (failures.get(names[i]) != null)
                    problems.add(tag + " think threw " + failures.get(names[i]));
                if (researchStart[i] < 0) {
                    problems.add(tag + " never started Tier 2 in " + BOT_T2_MINUTES + " minutes (blocked "
                        + (bots.get(names[i]) == null ? "?" : bots.get(names[i]).lastBlockedReason) + ")");
                    continue;
                }
                // T1 units first: the army is out before the research starts (half of the tuning's number on the patience path)
                int wantFighters = Math.max(2, tuning.t2MinArmy() / 2);
                if (fightersAtStart[i] < wantFighters)
                    problems.add(tag + " started Tier 2 with " + fightersAtStart[i] + " fighters, wanted " + wantFighters);
                // not before its tuning's earliest minute (the bot's clock starts at its first think, a few seconds in)
                if (researchStart[i] < tuning.t2Minute() * 1200L - 400)
                    problems.add(tag + " started Tier 2 at " + minutes(researchStart[i]) + ", before its minute " + tuning.t2Minute());
                if (researchDone[i] < 0)
                    problems.add(tag + " never finished the Tier 2 research (started " + minutes(researchStart[i]) + ")");
                else if (researchDone[i] < BOT_T2_EARLIEST_MINUTE * 1200L)
                    problems.add(tag + " had Tier 2 at " + minutes(researchDone[i]) + ", before minute " + BOT_T2_EARLIEST_MINUTE);
                if (labDone[i] < 0)
                    problems.add(tag + " never finished its T2 lab (research done " + minutes(researchDone[i]) + ", at " + minutes(now) + ")");
            }
            // hard first, then medium, then easy: the difficulties keep their identities
            if (researchStart[0] >= 0 && researchStart[1] >= 0 && researchStart[0] > researchStart[1] + 1200)
                problems.add("the hard bot started Tier 2 (" + minutes(researchStart[0]) + ") later than the medium (" + minutes(researchStart[1]) + ")");
            if (researchStart[1] >= 0 && researchStart[2] >= 0 && researchStart[1] > researchStart[2] + 1200)
                problems.add("the medium bot started Tier 2 (" + minutes(researchStart[1]) + ") later than the easy (" + minutes(researchStart[2]) + ")");
            for (int i = 0; i < count; i++)
                ReignOfNether.LOGGER.info("[T2Timing] {} {} research start {} done {} lab {} fighters at start {} mex richness {} (game minutes, standard pace)",
                    names[i].substring(12), levels[i], minutes(researchStart[i]), minutes(researchDone[i]), minutes(labDone[i]), fightersAtStart[i],
                    // the battlefield rolls it unseeded (BattlefieldSetup, 0.6-1.6): a lean roll is the easy bot's 16 metal/s red runs
                    String.format(java.util.Locale.ROOT, "%.2f", com.solegendary.reignofnether.startpos.StartAreaClearing.mexRichness));
            List<LivingEntity> leftovers = new ArrayList<>();
            for (LivingEntity le : UnitServerEvents.getAllUnits())
                if (le instanceof Unit u && List.of(names).contains(u.getOwnerName()))
                    leftovers.add(le);
            for (String n : names) {
                PlayerServerEvents.defeat(n, "gametest finished");
                bots.remove(n);
                failures.remove(n);
            }
            for (int i = 0; i < count; i++)
                for (int j = i + 1; j < count; j++)
                    com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(names[i], names[j]);
            leftovers.forEach(net.minecraft.world.entity.Entity::discard);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        // a fresh Runnable per sample (GameTestInfo keys its delayed tasks by the Runnable itself)
        for (int t = 20; t <= BOT_T2_TICKS; t += 20) {
            final int tick = t;
            helper.runAfterDelay(t, new Runnable() {
                @Override
                public void run() {
                    if (finished[0])
                        return;
                    long now = level.getGameTime() - t0;
                    boolean all = true;
                    for (int i = 0; i < count; i++) {
                        var kit = BotPlayer.kitFor(factions[i]);
                        if (researchStart[i] < 0 && (ProductionItems.RESEARCH_TIER_2.itemIsBeingProduced(false, names[i])
                                || ResearchServerEvents.playerHasResearch(names[i], ProductionItems.RESEARCH_TIER_2))) {
                            researchStart[i] = now;
                            fightersAtStart[i] = fighters(names[i]);
                        }
                        if (researchDone[i] < 0 && ResearchServerEvents.playerHasResearch(names[i], ProductionItems.RESEARCH_TIER_2))
                            researchDone[i] = now;
                        if (labDone[i] < 0 && kit != null && TierGate.ownsFinished(names[i], kit.t2Lab()))
                            labDone[i] = now;
                        all &= labDone[i] >= 0;
                        if (tick % 1200 == 0 && kit != null)
                            ReignOfNether.LOGGER.info(snapshot(names[i], kit, bots.get(names[i]), now, fighters(names[i])));
                    }
                    if (all || now >= BOT_T2_TICKS - 20)
                        finish.run();
                }
            });
        }
    }

    /** One bot's tech state, for the CI notice: what it has, what it earns and what last held it back. */
    private static String snapshot(String owner, BotPlayer.Kit kit, BotPlayer bot, long now, int fighters) {
        int[] c = new int[10];   // winds built/total, T1 lab, T1 req, T2 lab (built/total each), extractors built/total
        Building t1Req = kit.t2Lab() == null ? null : TierGate.t1LabFor(kit.t2Lab());
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (!owner.equals(bp.ownerName))
                continue;
            Building b = bp.getBuilding();
            int[] idx = b == kit.wind() ? new int[] { 0 } : b == kit.armyBuilding() ? new int[] { 2 }
                : b == t1Req ? new int[] { 4 } : b == kit.t2Lab() ? new int[] { 6 }
                : b instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor ? new int[] { 8 } : null;
            if (idx != null) {
                c[idx[0] + 1]++;
                if (bp.isBuilt)
                    c[idx[0]]++;
            }
        }
        // the T2 lab site, while it rises: paid progress, builders on it, and whether it is still only a blueprint - the
        // easy bot's red runs (40638286450, 40639915026) placed it by minute 13 and never finished it with the pool at 0
        String t2Site = "";
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
            if (owner.equals(bp.ownerName) && bp.getBuilding() == kit.t2Lab() && !bp.isBuilt && !bp.isDestroyedServerside)
                t2Site = String.format(java.util.Locale.ROOT, " t2site %.2f b%d", bp.buildProgress, bp.getBuilders().size());
        for (BuildingPlacement bp : com.solegendary.reignofnether.building.Blueprints.pendingOf(owner))
            if (bp.getBuilding() == kit.t2Lab())
                t2Site = " t2site blueprint";
        var eco = EconomyServerEvents.getEconomy(owner);
        Resources res = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (owner.equals(r.ownerName))
                res = r;
        return String.format("[T2Snap] %s min %d fighters %d metal/s %.1f energy/s %.0f (wind x%.2f) pool %.0f/%.0f winds %d/%d lab %d/%d t1req %d/%d t2lab %d/%d%s stall %.2f mex %d/%d blk %s",
            owner.substring(12).toLowerCase(java.util.Locale.ROOT), now / 1200, fighters, eco.effectiveMetalIncome(), eco.energyIncome,
            com.solegendary.reignofnether.resources.Wind.multiplier(), res == null ? 0f : res.getMetal(), res == null ? 0f : res.getEnergy(),
            c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], t2Site, eco.stall, c[8], c[9], bot == null ? "?" : bot.lastBlockedReason.substring(0, Math.min(70, bot.lastBlockedReason.length())));
    }

    private static String minutes(long ticks) {
        if (ticks < 0)
            return "never";
        long s = ticks / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
