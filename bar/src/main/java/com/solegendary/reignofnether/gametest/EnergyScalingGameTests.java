package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.EnergyCosts;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.TierGate;
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
 * Energy scales like BAR (lovish, Oct 29) in the running game: the baked prices are the energy table's, builders' build
 * powers add up on one site (and so does its energy drain), a stall slows a crowded site, higher-tier labs train faster
 * and drain more energy a second, and the converter's ratio follows its owner's tier. tools/test_energy_scaling.py holds
 * the opening model; this holds the code paths it models.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnergyScalingGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "energy_scaling";

    /** What EnergyCosts.bake wrote is what the game charges: the opening prices and energy per metal by tier. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void energy_costs_are_the_tables_opening_and_tier_prices(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ResourceCosts.METAL_EXTRACTOR.energy() != EnergyCosts.METAL_EXTRACTOR_ENERGY
                || ResourceCosts.WIND_GENERATOR.energy() != EnergyCosts.WIND_GENERATOR_ENERGY
                || ResourceCosts.VILLAGER.energy() != EnergyCosts.WORKER_ENERGY
                || Buildings.BARRACKS.cost.energy() != EnergyCosts.T1_LAB_ENERGY)
            problems.add("opening prices: extractor " + ResourceCosts.METAL_EXTRACTOR.energy() + ", wind "
                + ResourceCosts.WIND_GENERATOR.energy() + ", worker " + ResourceCosts.VILLAGER.energy() + ", Barracks "
                + Buildings.BARRACKS.cost.energy());
        for (var c : EnergyCosts.T1_LABS)
            if (c.energy() != EnergyCosts.T1_LAB_ENERGY)
                problems.add(c.id + " costs " + c.energy() + " energy, not the T1 lab's " + EnergyCosts.T1_LAB_ENERGY);
        if (ResourceCosts.BASIC_PORTAL.energy() + ResourceCosts.RESEARCH_MILITARY_PORTAL.energy() != EnergyCosts.T1_LAB_ENERGY)
            problems.add("the Horde's portal + Military refit cost " + (ResourceCosts.BASIC_PORTAL.energy()
                + ResourceCosts.RESEARCH_MILITARY_PORTAL.energy()) + " energy, not a T1 lab's");
        checkRatio(problems, EnergyCosts.T1_UNITS, EnergyCosts.T1_UNIT_ENERGY_PER_METAL, "T1");
        checkRatio(problems, EnergyCosts.T2_UNITS, EnergyCosts.T2_UNIT_ENERGY_PER_METAL, "T2");
        checkRatio(problems, EnergyCosts.T3_UNITS, EnergyCosts.T3_UNIT_ENERGY_PER_METAL, "T3");
        if (!(EnergyCosts.T1_UNIT_ENERGY_PER_METAL < EnergyCosts.T2_UNIT_ENERGY_PER_METAL
                && EnergyCosts.T2_UNIT_ENERGY_PER_METAL < EnergyCosts.T3_UNIT_ENERGY_PER_METAL))
            problems.add("energy per metal does not grow with the tier");
        finish(helper, problems);
    }

    private static void checkRatio(List<String> problems, List<com.solegendary.reignofnether.resources.ResourceCost> costs,
                                   float ratio, String tier) {
        for (var c : costs)
            if (c.metal() > 0 && c.energy() != EnergyCosts.energyFor(c.metal(), ratio))
                problems.add(tier + " " + c.id + ": " + c.metal() + "m / " + c.energy() + "e, want x" + ratio);
    }

    /**
     * Build powers add (lovish: "the more workers the faster something should build"): two workers on one extractor
     * advance it twice as far a second as one, and draw twice the energy a second; the cost per site does not change.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void two_builders_build_twice_as_fast_and_draw_twice_the_energy(GameTestHelper helper) {
        Crew one = new Crew(helper, "one", 0, 1, 50000);
        Crew two = new Crew(helper, "two", 1, 2, 50000);
        if (!one.ok() || !two.ok()) {
            one.undo();
            two.undo();
            helper.fail("(setup) could not raise the two sites: " + one.why + " / " + two.why);
            return;
        }
        helper.runAfterDelay(40, () -> {
            List<String> problems = new ArrayList<>();
            try {
                float p1 = one.site.buildProgress, p2 = two.site.buildProgress;
                float e1 = one.energySpent(), e2 = two.energySpent();
                if (one.site.getBuilders().size() != 1 || two.site.getBuilders().size() != 2)
                    problems.add("(setup) builders on the sites: " + one.site.getBuilders().size() + " / " + two.site.getBuilders().size());
                else if (p1 <= 0 || Math.abs(p2 / p1 - 2f) > 0.25f)
                    problems.add("two builders made " + p2 + " progress against one builder's " + p1 + " (want 2x)");
                else if (e1 <= 0 || Math.abs(e2 / e1 - 2f) > 0.25f)
                    problems.add("two builders drew " + e2 + " energy against one builder's " + e1 + " (want 2x)");
                else if (Math.abs(one.site.energySpent / p1 - two.site.energySpent / p2) > 1f)
                    problems.add("energy per unit of progress differs: " + one.site.energySpent / p1 + " vs " + two.site.energySpent / p2);
            } finally {
                one.undo();
                two.undo();
            }
            finish(helper, problems);
        });
    }

    /**
     * The stall: five workers on one extractor with too little energy for their drain are slowed by it (the site rises
     * at the share the pool can pay, not at their full build power), and the pool never goes below zero.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void five_builders_on_too_little_energy_are_slowed_by_the_stall(GameTestHelper helper) {
        Crew rich = new Crew(helper, "rich", 0, 5, 50000);
        Crew poor = new Crew(helper, "poor", 1, 5, POOR_ENERGY);
        if (!rich.ok() || !poor.ok()) {
            rich.undo();
            poor.undo();
            helper.fail("(setup) could not raise the two sites: " + rich.why + " / " + poor.why);
            return;
        }
        helper.runAfterDelay(40, () -> {
            List<String> problems = new ArrayList<>();
            try {
                float full = rich.site.buildProgress, starved = poor.site.buildProgress;
                if (rich.site.getBuilders().size() != 5 || poor.site.getBuilders().size() != 5)
                    problems.add("(setup) builders on the sites: " + rich.site.getBuilders().size() + " / " + poor.site.getBuilders().size());
                else if (!(starved < full * 0.6f))
                    problems.add("five builders on " + POOR_ENERGY + " energy made " + starved + " progress, with plenty " + full
                        + " - the stall did not slow them");
                if (poor.pool.getEnergy() < -0.01f)
                    problems.add("the starved pool went negative: " + poor.pool.getEnergy());
            } finally {
                rich.undo();
                poor.undo();
            }
            finish(helper, problems);
        });
    }

    /** Energy the starved crew starts with and never gets more of: a few ticks of five workers' drain on an extractor. */
    static final int POOR_ENERGY = 20;

    /** One owner's site on its own pad high over the arena ({@code slot} 0 or 1 side by side), built by {@code n} workers. */
    static final class Crew {
        final ServerLevel level;
        final String owner;
        final Resources pool;
        final List<Entity> made = new ArrayList<>();
        final int cx, cz, y;
        BuildingPlacement site;
        String why = "";

        Crew(GameTestHelper helper, String tag, int slot, int n, int energy) {
            level = helper.getLevel();
            owner = "gametest_escale_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
            cx = base.getX() + slot * 16 - 8;
            cz = base.getZ();
            y = 186;   // this holder's own height (BuilderAssistGameTests 200, the annex tests 222+)
            pool = new Resources(owner, 0, energy, 50000);
            ResourcesServerEvents.resourcesList.add(pool);
            CombatEngineerGameTests.skyPad(level, cx, y, cz, 6, 14, false);
            site = BuildingServerEvents.placeBuilding(Buildings.METAL_EXTRACTOR_VILLAGERS, new BlockPos(cx - 1, y + 1, cz - 1),
                Rotation.NONE, owner, new int[0], false, false, true, true);
            if (site == null) {
                why = "placement refused";
                return;
            }
            site.freeBuild = false;   // command-placed for the test, paid for by flow like a player's site
            // the workers stand on the pad beside the footprint, in build range, and are told to build it
            for (int i = 0; i < n; i++) {
                Mob m = CombatEngineerGameTests.spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), owner,
                    new BlockPos(site.minCorner.getX() - 1, y + 1, site.minCorner.getZ() + i), made);
                if (!(m instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit w) || w.getBuildRepairGoal() == null) {
                    why = "no worker";
                    return;
                }
                w.getBuildRepairGoal().setBuildingTarget(site);
            }
        }

        boolean ok() {
            return site != null && why.isEmpty();
        }

        float energySpent() {
            return site.energySpent;
        }

        void undo() {
            if (site != null && BuildingServerEvents.getBuildings().remove(site)) {
                site.forceChunk(false);
                for (var b : site.getBlocks())
                    level.setBlock(b.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            CombatEngineerGameTests.cleanUp(made, List.of(pool), List.of(owner));
            CombatEngineerGameTests.skyPad(level, cx, y, cz, 6, 14, true);
        }
    }

    /**
     * Higher-tier labs build faster (BAR): one tick of a unit at a T2 lab advances TierGate.T2_LAB_BUILD_POWER ticks of its
     * production, a T1 lab's one - and so drains that much more energy a second. A research at a T2 lab is not sped up.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void t2_lab_trains_its_units_faster_and_drains_more_energy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_escale_labs_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = new Resources(owner, 0, 100000, 100000);
        ResourcesServerEvents.resourcesList.add(pool);
        List<String> problems = new ArrayList<>();
        try {
            float t1 = oneTick(level, owner, Buildings.BARRACKS, ProductionItems.VINDICATOR, problems);
            float t2 = oneTick(level, owner, Buildings.ARCANE_TOWER, ProductionItems.RAVAGER, problems);
            if (Math.abs(t1 - TierGate.T1_LAB_BUILD_POWER) > 1e-3)
                problems.add("a T1 lab advanced a unit " + t1 + " ticks in one tick");
            if (Math.abs(t2 - TierGate.T2_LAB_BUILD_POWER) > 1e-3)
                problems.add("a T2 lab advanced a unit " + t2 + " ticks in one tick, TierGate says " + TierGate.T2_LAB_BUILD_POWER);
            if (!(TierGate.T1_LAB_BUILD_POWER < TierGate.T2_LAB_BUILD_POWER && TierGate.T2_LAB_BUILD_POWER < TierGate.T3_LAB_BUILD_POWER))
                problems.add("lab build power does not grow with the tier");
            if (TierGate.labTierBuildPower(Buildings.CASTLE) != TierGate.T3_LAB_BUILD_POWER)
                problems.add("the Castle is not a T3 lab for its build power");
            float e1 = ResourceCosts.VINDICATOR.energy() / (float) ResourceCosts.VINDICATOR.ticks * 20f * TierGate.T1_LAB_BUILD_POWER;
            float e2 = ResourceCosts.RAVAGER.energy() / (float) ResourceCosts.RAVAGER.ticks * 20f * TierGate.T2_LAB_BUILD_POWER;
            if (!(e2 > 4 * e1))
                problems.add("a T2 lab on Ravagers draws " + e2 + " energy/s, a T1 lab on Halberdiers " + e1);
        } finally {
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        finish(helper, problems);
    }

    /** Ticks of production one server tick of {@code item} at a finished, unregistered {@code lab} advances. */
    private static float oneTick(ServerLevel level, String owner, com.solegendary.reignofnether.building.Building lab,
                                 ProductionItem item, List<String> problems) {
        var bp = BuildingUtils.getNewBuildingPlacement(lab, level, new BlockPos(0, 250, 0), Rotation.NONE, owner, false);
        if (!(bp instanceof ProductionPlacement pp)) {
            problems.add(lab.name + " is not a production placement");
            return -1;
        }
        pp.isBuilt = true;
        var a = new ActiveProduction(item, false, owner);
        float t0 = a.ticksLeft;
        item.tick(pp, a);
        return t0 - a.ticksLeft;
    }

    /** The converter gets better with its owner's tier: 100 energy a metal at Tier 1, then TierGate's T2 and T3 ratios. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void converter_ratio_improves_with_the_owners_tier(GameTestHelper helper) {
        String owner = "gametest_escale_conv_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        List<String> problems = new ArrayList<>();
        try {
            float m1 = convertOneSecond(owner, problems);
            ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
            float m2 = convertOneSecond(owner, problems);
            ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_3);
            float m3 = convertOneSecond(owner, problems);
            float want1 = TierGate.CONVERTER_ENERGY_PER_SECOND / TierGate.CONVERTER_ENERGY_PER_METAL;
            float want2 = TierGate.CONVERTER_ENERGY_PER_SECOND / TierGate.T2_CONVERTER_ENERGY_PER_METAL;
            float want3 = TierGate.CONVERTER_ENERGY_PER_SECOND / TierGate.T3_CONVERTER_ENERGY_PER_METAL;
            if (Math.abs(m1 - want1) > 0.03f || Math.abs(m2 - want2) > 0.03f || Math.abs(m3 - want3) > 0.03f)
                problems.add("one converter-second made " + m1 + " / " + m2 + " / " + m3 + " metal at T1/T2/T3, want "
                    + want1 + " / " + want2 + " / " + want3);
            if (!(m1 < m2 && m2 < m3))
                problems.add("the converter does not improve with the tier: " + m1 + " / " + m2 + " / " + m3);
        } finally {
            ResearchServerEvents.removeAllResearchFor(owner);
            ResourcesServerEvents.resourcesList.remove(pool);
            var eco = EconomyServerEvents.getEconomy(owner);
            eco.conversionCapacity = 0;
        }
        finish(helper, problems);
    }

    /** Metal one converter makes in one second from a pool well above the threshold, after the economy's recalc. */
    private static float convertOneSecond(String owner, List<String> problems) {
        var eco = EconomyServerEvents.getEconomy(owner);
        EconomyServerEvents.recalculateIncomeAndStorage();
        eco.conversionCapacity = TierGate.CONVERTER_ENERGY_PER_SECOND;
        var res = new Resources(owner, 0, 0, 0);
        res.addEnergy(eco.energyStorage);
        for (int i = 0; i < 20; i++)
            EconomyServerEvents.convertEnergy(res, eco);
        if (Math.abs(eco.energyStorage - res.getEnergy() - TierGate.CONVERTER_ENERGY_PER_SECOND) > 1f)
            problems.add("a converter-second burned " + (eco.energyStorage - res.getEnergy()) + " energy");
        return res.getMetal();
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
