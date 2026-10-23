package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.buildings.shared.FusionReactor;
import com.solegendary.reignofnether.building.buildings.shared.FusionReactor.Kind;
import com.solegendary.reignofnether.building.buildings.shared.SolarGenerator;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.resources.Wind;
import com.solegendary.reignofnether.unit.T2Workers;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Fusion Reactor (T2) and Advanced Fusion Reactor (T3): both exist for all seven factions, read their numbers
 * from one place (FusionReactor.Kind), out-earn the Advanced Solar, rank Fusion < Advanced Fusion in output, price and
 * blast, and need a T2 constructor. Plus the pure wind formula behind the wind generator's "E/s now" readout.
 * Own class: they touch no live building list beyond reading the registry.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FusionGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void fusion_both_reactors_exist_for_every_faction_with_config_numbers(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var factions = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT, Factions.STORMCALLERS, Factions.BRASSWORKS);
        for (var f : factions)
            for (Kind k : Kind.values()) {
                FusionReactor r = FusionReactor.of(f, k);
                if (r == null) {
                    problems.add(f.getName() + " has no " + k);
                    continue;
                }
                if (r.getEnergyIncome() != k.energyPerSecond || r.isWindDriven())
                    problems.add(f.getName() + " " + k + " does not earn a flat " + k.energyPerSecond);
                if (r.getRelativeBlockData(helper.getLevel()) == null || r.getRelativeBlockData(helper.getLevel()).isEmpty())
                    problems.add(k + " has no structure blocks");
                if (!T2Workers.needsT2Builder(r))
                    problems.add(f.getName() + " " + k + " should need a T2 constructor");
                if (r.blastRadius() != k.explodeRadius)
                    problems.add(f.getName() + " " + k + " blast radius is not read from its Kind");
            }
        int n = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (b instanceof FusionReactor)
                n++;
        if (n != 14)
            problems.add(n + " fusion reactors registered, expected 14 (7 factions x 2)");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void fusion_outputs_prices_and_blasts_are_ordered(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float adv = SolarGenerator.Kind.ADVANCED.energyPerSecond;
        if (!(Kind.FUSION.energyPerSecond > adv * 8))
            problems.add("Fusion should out-earn eight Advanced Solars");
        if (!(Kind.ADVANCED.energyPerSecond > Kind.FUSION.energyPerSecond))
            problems.add("Advanced Fusion should out-earn Fusion");
        float ratio = Kind.ADVANCED.energyPerSecond / Kind.FUSION.energyPerSecond;
        if (ratio < 2.5f || ratio > 3.5f)
            problems.add("Advanced Fusion output should be about 3x Fusion, is " + ratio);
        float priceRatio = (float) Kind.ADVANCED.cost.ore / Kind.FUSION.cost.ore;
        if (priceRatio < 3f || priceRatio > 4f)
            problems.add("Advanced Fusion metal should be about 3.5x Fusion, is " + priceRatio);
        int solarsOfMetal = Kind.FUSION.cost.ore / SolarGenerator.Kind.ADVANCED.cost.ore;
        if (solarsOfMetal < 6 || solarsOfMetal > 8)
            problems.add("Fusion should cost 6-8 Advanced Solars of metal, is " + solarsOfMetal);
        // better output per metal than the Advanced Solar, or nobody would build it
        if (!(Kind.FUSION.energyPerSecond / Kind.FUSION.cost.ore > adv / SolarGenerator.Kind.ADVANCED.cost.ore))
            problems.add("Fusion should earn more per metal than the Advanced Solar");
        if (!(Kind.ADVANCED.explodeRadius > Kind.FUSION.explodeRadius && Kind.FUSION.explodeRadius > 2.0f))
            problems.add("blast radius ordering is wrong (default 2 < Fusion < Advanced Fusion)");
        if (!Kind.ADVANCED.needsTier3 || Kind.FUSION.needsTier3)
            problems.add("only the Advanced Fusion Reactor needs Tier 3");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void fusion_wind_energy_formula_is_linear_in_strength(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (Math.abs(Wind.energyAt(14f, Wind.MEAN) - 14f) > 0.001f)
            problems.add("average wind should give the base 14");
        if (Math.abs(Wind.energyAt(14f, Wind.MIN) - 4f) > 0.001f || Math.abs(Wind.energyAt(14f, Wind.MAX) - 24f) > 0.001f)
            problems.add("wind 4 / 24 should give 4 / 24 E/s");
        if (Math.abs(Wind.energyAt(14f, 9f) - 9f) > 0.001f)
            problems.add("a generator earns the wind strength at base 14");
        WindGenerator mill = new WindGenerator();
        if (Math.abs(mill.currentEnergy() - Wind.energyAt(14f, Wind.strength())) > 0.001f)
            problems.add("currentEnergy does not follow the live wind");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
