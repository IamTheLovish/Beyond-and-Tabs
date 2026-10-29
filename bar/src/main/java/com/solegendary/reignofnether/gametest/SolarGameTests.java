package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.shared.SolarGenerator;
import com.solegendary.reignofnether.building.buildings.shared.SolarGenerator.Kind;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.resources.Wind;
import com.solegendary.reignofnether.unit.T2Workers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Solar Collector and Advanced Solar (claude/solar-spec.md, Oct 21): every faction has both, the numbers are the
 * spec's, they earn the same whatever the wind does (a wind generator beside them does not), the Advanced Solar needs a
 * T2 constructor, and the shutters close on a hit for 8 s, earn nothing while shut and halve the damage. Own holder (the CI
 * guard keeps new tests out of SkirmishGameTests); placements are made with getNewBuildingPlacement and never registered in
 * the world's building list, so no other test can see them.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SolarGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static SolarGenerator.SolarPlacement place(GameTestHelper helper, SolarGenerator s, String owner) {
        var bp = BuildingUtils.getNewBuildingPlacement(s, helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 6)),
            Rotation.NONE, owner, false);
        if (!(bp instanceof SolarGenerator.SolarPlacement p))
            return null;
        p.isBuilt = true;
        return p;
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void every_faction_has_both_solar_buildings_with_the_spec_numbers(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var factions = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT, Factions.STORMCALLERS, Factions.BRASSWORKS);
        for (var f : factions)
            for (Kind k : Kind.values()) {
                SolarGenerator s = SolarGenerator.of(f, k);
                if (s == null) {
                    problems.add(f.getName() + " has no " + k);
                    continue;
                }
                if (s.getEnergyIncome() != k.energyPerSecond || s.isWindDriven())
                    problems.add(f.getName() + " " + k + " does not earn a flat " + k.energyPerSecond);
                if (s.getRelativeBlockData(helper.getLevel()) == null || s.getRelativeBlockData(helper.getLevel()).isEmpty())
                    problems.add(k + " has no structure blocks");
                if (T2Workers.needsT2Builder(s) != (k == Kind.ADVANCED))
                    problems.add(f.getName() + " " + k + ": the T2-builder rule is wrong");
            }
        int solar = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (b instanceof SolarGenerator)
                solar++;
        if (solar != 14)
            problems.add(solar + " solar buildings registered, expected 14 (7 factions x 2)");
        // the spec's prices: 150 m / 0 e (lovish, Oct 29: like BAR's solar it costs metal and build time, not energy -
        // resources/EnergyCosts; it was 60 e) and 300 m / 1500 e (ResourceCost.Building: wood = energy, ore = metal)
        if (Kind.BASIC.cost.ore != 150 || Kind.BASIC.cost.wood != com.solegendary.reignofnether.resources.EnergyCosts.SOLAR_COLLECTOR_ENERGY
                || com.solegendary.reignofnether.resources.EnergyCosts.SOLAR_COLLECTOR_ENERGY != 0)
            problems.add("Solar Collector price is " + Kind.BASIC.cost.ore + "m / " + Kind.BASIC.cost.wood + "e");
        if (Kind.ADVANCED.cost.ore != 300 || Kind.ADVANCED.cost.wood != 1500)
            problems.add("Advanced Solar price is " + Kind.ADVANCED.cost.ore + "m / " + Kind.ADVANCED.cost.wood + "e");
        if (Kind.BASIC.energyPerSecond != 20f || Kind.ADVANCED.energyPerSecond != 75f)
            problems.add("solar income is not 20 / 75");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void solar_earns_flat_at_wind_4_and_24_while_a_wind_generator_swings(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float before = Wind.strength();
        try {
            var solar = place(helper, Buildings.SOLAR_COLLECTOR_VILLAGERS, "solar_owner");
            var adv = place(helper, Buildings.ADVANCED_SOLAR_VILLAGERS, "solar_owner");
            var windBp = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, "solar_owner", false);
            if (solar == null || adv == null || windBp == null) {
                helper.fail("could not make the placements");
                return;
            }
            windBp.isBuilt = true;
            float[] windIncome = new float[2];
            int i = 0;
            for (float w : new float[] { Wind.MIN, Wind.MAX }) {
                Wind.setFromServer(w);
                if (solar.getEnergyIncome() != 20f)
                    problems.add("Solar Collector earned " + solar.getEnergyIncome() + " at wind " + w);
                if (adv.getEnergyIncome() != 75f)
                    problems.add("Advanced Solar earned " + adv.getEnergyIncome() + " at wind " + w);
                windIncome[i++] = windBp.getEnergyIncome();
            }
            if (windIncome[1] <= windIncome[0] * 2)
                problems.add("the wind generator should swing (" + windIncome[0] + " at 4, " + windIncome[1] + " at 24)");
        } finally {
            Wind.setFromServer(before);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void a_hit_shuts_the_shutters_for_eight_seconds_no_income_half_damage(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var solar = place(helper, Buildings.SOLAR_COLLECTOR_STORM, "shutter_owner");
        if (solar == null) {
            helper.fail("could not make the placement");
            return;
        }
        var level = helper.getLevel();
        if (solar.isShut())
            problems.add("a fresh collector starts shut");
        if (solar.getEnergyIncome() != 20f)
            problems.add("open collector earns " + solar.getEnergyIncome());
        if (SolarGenerator.damageTaken(false, 10) != 10 || SolarGenerator.damageTaken(true, 10) != 5)
            problems.add("damage while shut should be half");
        solar.destroyRandomBlocks(1.0);
        if (!solar.isShut())
            problems.add("a hit did not shut the shutters");
        if (solar.shutUntil - level.getGameTime() != SolarGenerator.SHUT_TICKS)
            problems.add("shut for " + (solar.shutUntil - level.getGameTime()) + " ticks, not " + SolarGenerator.SHUT_TICKS);
        if (SolarGenerator.SHUT_TICKS != 160)
            problems.add("8 s is 160 ticks");
        if (solar.getEnergyIncome() != 0f)
            problems.add("a shut collector earned " + solar.getEnergyIncome());
        solar.shutUntil = level.getGameTime();   // the 8 s are up
        if (solar.isShut() || solar.getEnergyIncome() != 20f)
            problems.add("it did not reopen: shut=" + solar.isShut() + ", income " + solar.getEnergyIncome());
        // an unbuilt site never shuts (the shutters are the finished building's)
        var site = place(helper, Buildings.SOLAR_COLLECTOR_STORM, "shutter_owner");
        site.isBuilt = false;
        site.destroyRandomBlocks(1.0);
        if (site.isShut())
            problems.add("an unbuilt collector shut");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
