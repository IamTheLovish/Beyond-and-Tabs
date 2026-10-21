package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.resources.Wind;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * resources/Wind (BAR wind, Oct 21): the wind stays in its range, moves smoothly, averages the old flat income over a
 * match, and only the wind-driven generators follow it. Uses Wind.State, never the live wind other tests run under.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WindGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void wind_stays_in_range_moves_smoothly_and_averages_out(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (long seed : new long[] { 1, 42, 1234567 }) {
            Wind.State w = new Wind.State(seed);
            double sum = 0;
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, prev = w.strength(), maxStep = 0;
            int ticks = 20 * 60 * 120;   // two hours of play
            for (int t = 0; t < ticks; t++) {
                w.tick(t);
                float s = w.strength();
                sum += s;
                lo = Math.min(lo, s);
                hi = Math.max(hi, s);
                maxStep = Math.max(maxStep, Math.abs(s - prev));
                prev = s;
            }
            double mean = sum / ticks;
            if (lo < Wind.MIN - 1e-3 || hi > Wind.MAX + 1e-3)
                problems.add("seed " + seed + ": wind left its range (" + lo + " - " + hi + ")");
            if (maxStep > 0.05f)
                problems.add("seed " + seed + ": wind jumped " + maxStep + " in one tick");
            if (Math.abs(mean - Wind.MEAN) > 2.0)
                problems.add("seed " + seed + ": two hours averaged " + mean + ", the economy assumes " + Wind.MEAN);
            if (hi - lo < (Wind.MAX - Wind.MIN) * 0.5f)
                problems.add("seed " + seed + ": the wind barely moved (" + lo + " - " + hi + ")");
        }
        if (!Buildings.WIND_GENERATOR_VILLAGERS.isWindDriven())
            problems.add("the White Angel windmill should earn with the wind");
        if (Buildings.WIND_GENERATOR_TIDE.isWindDriven() || Buildings.WIND_GENERATOR_BRASS.isWindDriven())
            problems.add("the Tidewrought waterwheel and the Brassworks boiler should run steady");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
