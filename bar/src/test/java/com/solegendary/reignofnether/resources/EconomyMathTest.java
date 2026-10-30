package com.solegendary.reignofnether.resources;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flow economy's arithmetic (EconomyServerEvents' class comment is the spec - BAR's rules): nothing is paid
 * upfront; every consumer asks for its share each tick, a player who spends more than they have slows ALL their
 * production evenly (the stall), storage caps income and what overflows goes to allies, and resources never go
 * negative. Written from those rules before the arithmetic was lifted out of EconomyServerEvents (which touches the
 * world, so it cannot run in plain JUnit): the cheapest place for a rounding or sign slip to hide is here, and the
 * costliest place to find it is a 4v4 where nobody can build.
 */
class EconomyMathTest {

    private static final float EPS = 1e-4f;

    // ---------------------------------------------------------------- what a consumer is granted

    @Test
    void aConsumerWithEnoughInThePoolGetsEverythingItAsksFor() {
        assertEquals(1f, EconomyMath.granted(1f, 100f, 50f, 100f, 50f), EPS);
        assertEquals(1f, EconomyMath.granted(1f, 50f, 50f, 50f, 50f), EPS, "exactly enough is enough");
        assertEquals(1f, EconomyMath.granted(1f, 1000f, 0.001f, 1000f, 0f), EPS);
    }

    @Test
    void aShortPoolGrantsTheFractionItCanPayAndTheScarcerResourceDecides() {
        assertEquals(0.5f, EconomyMath.granted(1f, 25f, 50f, 100f, 50f), EPS, "half the metal");
        assertEquals(0.25f, EconomyMath.granted(1f, 100f, 50f, 10f, 40f), EPS, "a quarter of the energy");
        assertEquals(0.2f, EconomyMath.granted(1f, 10f, 50f, 10f, 40f), EPS, "metal 0.2 beats energy 0.25");
        assertEquals(0f, EconomyMath.granted(1f, 0f, 50f, 100f, 50f), EPS);
        assertEquals(0f, EconomyMath.granted(1f, -30f, 50f, 100f, 50f), EPS, "a negative pool (refund glitch) pays nothing");
    }

    @Test
    void aResourceTheJobDoesNotNeedNeverBlocksIt() {
        assertEquals(1f, EconomyMath.granted(1f, 0f, 0f, 100f, 50f), EPS, "an energy-only job runs with no metal");
        assertEquals(1f, EconomyMath.granted(1f, 100f, 50f, 0f, 0f), EPS, "a metal-only job runs with no energy");
    }

    @Test
    void theStallSlowsEveryoneEvenlyAndNobodyGetsMoreThanItAsked() {
        assertEquals(0.5f, EconomyMath.granted(0.5f, 1000f, 50f, 1000f, 50f), EPS, "plenty in the pool, but the stall holds");
        assertEquals(0.3f, EconomyMath.granted(0.5f, 15f, 50f, 1000f, 50f), EPS, "the pool can pay less than the stall allows");
        assertEquals(1f, EconomyMath.granted(7f, 1000f, 50f, 1000f, 50f), EPS, "a stall above 1 is clamped");
        assertEquals(0f, EconomyMath.granted(-2f, 1000f, 50f, 1000f, 50f), EPS, "a stall below 0 is clamped");
    }

    @Test
    void twoConsumersDrainThePoolToZeroAndNeverBelow() {
        // BAR: the second one asking in the same tick is clamped to what is really left
        float metal = 100f, energy = 100f;
        float g1 = EconomyMath.granted(1f, metal, 80f, energy, 10f);
        metal -= 80f * g1;
        energy -= 10f * g1;
        assertEquals(1f, g1, EPS);
        assertEquals(20f, metal, EPS);
        float g2 = EconomyMath.granted(1f, metal, 80f, energy, 10f);
        assertEquals(0.25f, g2, EPS);
        metal -= 80f * g2;
        assertEquals(0f, metal, EPS);
        assertEquals(0f, EconomyMath.granted(1f, metal, 80f, energy, 10f), EPS, "the third one gets nothing");
    }

    @Test
    void grantedIsAlwaysAFractionAndThePaymentNeverOverdrawsThePool() {
        Random rng = new Random(1);
        for (int i = 0; i < 20_000; i++) {
            float stall = rng.nextFloat() * 1.4f - 0.2f;
            float metal = rng.nextFloat() * 300f - 20f, energy = rng.nextFloat() * 300f - 20f;
            float wantMetal = rng.nextInt(3) == 0 ? 0f : rng.nextFloat() * 200f;
            float wantEnergy = rng.nextInt(3) == 0 ? 0f : rng.nextFloat() * 200f;
            float g = EconomyMath.granted(stall, metal, wantMetal, energy, wantEnergy);
            assertTrue(g >= 0f && g <= 1f, "granted " + g);
            assertTrue(g <= Math.max(0f, stall) + EPS, "granted more than the stall allows");
            assertTrue(metal - wantMetal * g >= -0.01f || metal < 0f, "metal overdrawn: pool " + metal + " want " + wantMetal + " got " + g);
            assertTrue(energy - wantEnergy * g >= -0.01f || energy < 0f, "energy overdrawn");
        }
    }

    // ---------------------------------------------------------------- the stall for the next tick

    @Test
    void withNoDemandOrEnoughInThePoolThereIsNoStall() {
        assertEquals(1f, EconomyMath.stall(0f, 0f, 0f, 0f), EPS);
        assertEquals(1f, EconomyMath.stall(500f, 0f, 500f, 0f), EPS);
        assertEquals(1f, EconomyMath.stall(500f, 500f, 500f, 500f), EPS, "demand equal to the pool is met");
        assertEquals(1f, EconomyMath.stall(500f, 100f, 5f, 0f), EPS, "demand for metal only: a nearly empty energy pool does not stall it");
    }

    @Test
    void overspendingStallsEveryConsumerByTheSameShare() {
        assertEquals(0.5f, EconomyMath.stall(100f, 200f, 100f, 0f), EPS, "asked twice what there is");
        assertEquals(0.25f, EconomyMath.stall(100f, 0f, 100f, 400f), EPS);
        assertEquals(0.25f, EconomyMath.stall(100f, 250f, 100f, 400f), EPS, "metal would allow 0.4, energy only 0.25: the scarcer decides");
    }

    @Test
    void anEmptyPoolWithDemandStallsEverythingToZeroAndNeverNegative() {
        assertEquals(0f, EconomyMath.stall(0f, 10f, 100f, 0f), EPS);
        assertEquals(0f, EconomyMath.stall(-50f, 10f, 100f, 0f), EPS);
        assertEquals(0f, EconomyMath.stall(100f, 0f, 0f, 10f), EPS);
    }

    // ---------------------------------------------------------------- income and storage

    @Test
    void incomeFillsStorageAndNothingPastIt() {
        assertEquals(10f, EconomyMath.banked(10f, 1000f, 0f), EPS);
        assertEquals(10f, EconomyMath.banked(10f, 1000f, 989.99f), EPS);
        assertEquals(4f, EconomyMath.banked(10f, 1000f, 996f), EPS, "only the room that is left");
        assertEquals(0f, EconomyMath.banked(10f, 1000f, 1000f), EPS);
        assertEquals(0f, EconomyMath.banked(10f, 1000f, 1200f), EPS, "a pool above the cap (a refund) banks nothing, and never goes negative");
        assertEquals(0f, EconomyMath.banked(0f, 1000f, 10f), EPS);
    }

    @Test
    void whatIsNotBankedIsTheOverflowThatGoesToAllies() {
        float income = 10f, storage = 1000f, pool = 996f;
        float banked = EconomyMath.banked(income, storage, pool);
        assertEquals(6f, income - banked, EPS);
    }

    @Test
    void shrinkingStorageLosesWhatWasHeldInTheLostPartButNotMore() {
        assertEquals(500f, EconomyMath.lostFromPool(2000f, 1500f, 1000f), EPS, "only what no longer fits is lost");
        assertEquals(100f, EconomyMath.lostFromPool(1600f, 1500f, 1000f), EPS);
        assertEquals(0f, EconomyMath.lostFromPool(1500f, 1500f, 1000f), EPS, "a pool at the new cap loses nothing");
        assertEquals(0f, EconomyMath.lostFromPool(800f, 1500f, 1000f), EPS, "a pool under the new cap loses nothing");
        assertEquals(200f, EconomyMath.lostFromPool(2400f, 1500f, 200f), EPS, "never more than the storage that died");
        assertEquals(0f, EconomyMath.lostFromPool(2400f, 1500f, 0f), EPS);
        assertEquals(0f, EconomyMath.lostFromPool(2400f, 1500f, -300f), EPS, "storage that GREW loses nothing");
    }

    // ---------------------------------------------------------------- energy converters

    @Test
    void convertersOnlyRunOnEnergyAboveTheThresholdAndAreCappedByTheirCapacityPerTick() {
        // 1000/1000 energy, slider 50%: 500 spare; capacity 100 per tick
        assertEquals(100f, EconomyMath.energyToConvert(1000f, 1000f, 0.5f, 800f, 100f, 50f), EPS);
        // a small spare is converted in full
        assertEquals(30f, EconomyMath.energyToConvert(530f, 1000f, 0.5f, 800f, 100f, 50f), EPS);
        // exactly at the threshold, or under it: nothing (they never starve construction)
        assertEquals(0f, EconomyMath.energyToConvert(500f, 1000f, 0.5f, 800f, 100f, 50f), EPS);
        assertEquals(0f, EconomyMath.energyToConvert(120f, 1000f, 0.5f, 800f, 100f, 50f), EPS);
        // a 100% slider never converts
        assertEquals(0f, EconomyMath.energyToConvert(1000f, 1000f, 1f, 800f, 100f, 50f), EPS);
    }

    @Test
    void convertersStopWhenMetalStorageIsFullAndNeverMakeMoreMetalThanFits() {
        assertEquals(0f, EconomyMath.energyToConvert(1000f, 1000f, 0.5f, 0f, 100f, 50f), EPS);
        assertEquals(0f, EconomyMath.energyToConvert(1000f, 1000f, 0.5f, -5f, 100f, 50f), EPS);
        // 1 metal of room costs 50 energy (CONVERSION_RATIO)
        assertEquals(50f, EconomyMath.energyToConvert(1000f, 1000f, 0.5f, 1f, 100f, 50f), EPS);
        assertEquals(10f, EconomyMath.energyToConvert(1000f, 1000f, 0.5f, 0.2f, 100f, 50f), EPS);
    }

    @Test
    void convertersWithNoCapacityDoNothing() {
        assertEquals(0f, EconomyMath.energyToConvert(1000f, 1000f, 0.5f, 800f, 0f, 50f), EPS);
    }

    // ---------------------------------------------------------------- the two sliders

    @Test
    void aSliderLevelIsClampedIntoItsRange() {
        assertEquals(0.05f, EconomyMath.clampLevel(0f, 0.05f), EPS, "a converter threshold of 0 would drain the pool: it keeps a sliver");
        assertEquals(0.05f, EconomyMath.clampLevel(-3f, 0.05f), EPS);
        assertEquals(0.4f, EconomyMath.clampLevel(0.4f, 0.05f), EPS);
        assertEquals(1f, EconomyMath.clampLevel(1f, 0.05f), EPS);
        assertEquals(1f, EconomyMath.clampLevel(9f, 0.05f), EPS);
        assertEquals(0f, EconomyMath.clampLevel(-1f, 0f), EPS, "the share slider may go to 0");
    }

    @Test
    void onlyARealNumberIsAcceptedFromTheNetwork() {
        assertTrue(EconomyMath.isRealLevel(0f));
        assertTrue(EconomyMath.isRealLevel(0.73f));
        assertTrue(EconomyMath.isRealLevel(-4f), "out of range is clamped, not refused");
        assertFalse(EconomyMath.isRealLevel(Float.NaN));
        assertFalse(EconomyMath.isRealLevel(Float.POSITIVE_INFINITY));
        assertFalse(EconomyMath.isRealLevel(Float.NEGATIVE_INFINITY));
    }

    @Test
    void aDragSendsFasterThanTheLimitAndOnlyTheSlowEnoughChangesPass() {
        assertTrue(EconomyMath.sliderTooFast(100, 99, 2));
        assertFalse(EconomyMath.sliderTooFast(100, 98, 2));
        assertFalse(EconomyMath.sliderTooFast(100, 10, 2));
        assertTrue(EconomyMath.sliderTooFast(100, 100, 2), "two changes in one tick");
        assertFalse(EconomyMath.sliderTooFast(5, -1000, 2), "the first change of a player always passes");
    }

    // ---------------------------------------------------------------- sharing metal above the slider

    @Test
    void theShareSliderGivesAwayOnlyTheMetalAboveItsLevelAtALimitedRate() {
        float perTick = 300f / 20f;
        assertEquals(perTick, EconomyMath.shareExcess(800f, 0.5f, 1000f, perTick), EPS, "300 over, but 15 a tick at most");
        assertEquals(5f, EconomyMath.shareExcess(505f, 0.5f, 1000f, perTick), EPS);
        assertEquals(0f, EconomyMath.shareExcess(500f, 0.5f, 1000f, perTick), EPS);
        assertEquals(0f, EconomyMath.shareExcess(100f, 0.5f, 1000f, perTick), EPS);
        assertEquals(0f, EconomyMath.shareExcess(500.00005f, 0.5f, 1000f, perTick), EPS, "dust is not worth a transfer");
    }

    @Test
    void aSliderAtOneSharesNothingButTheOverflow() {
        assertEquals(0f, EconomyMath.shareExcess(1000f, 1f, 1000f, 15f), EPS);
        assertEquals(0f, EconomyMath.shareExcess(1000f, 0.9995f, 1000f, 15f), EPS, "within 0.1% of 1 counts as 1");
        assertTrue(EconomyMath.shareExcess(1000f, 0.99f, 1000f, 15f) > 0f);
    }

    // ---------------------------------------------------------------- splitting between allies

    private static float sum(float[] a) {
        float s = 0;
        for (float v : a)
            s += v;
        return s;
    }

    @Test
    void anAmountIsSplitEvenlyBetweenAlliesWithRoom() {
        float[] gives = EconomyMath.splitAmongAllies(90f, new float[] { 100f, 100f, 100f });
        assertEquals(30f, gives[0], EPS);
        assertEquals(30f, gives[1], EPS);
        assertEquals(30f, gives[2], EPS);
        assertEquals(45f, EconomyMath.splitAmongAllies(90f, new float[] { 100f, 100f })[0], EPS);
    }

    @Test
    void whatANearlyFullAllyCannotTakeIsOfferedToTheOthers() {
        float[] gives = EconomyMath.splitAmongAllies(90f, new float[] { 10f, 100f, 100f });
        assertEquals(10f, gives[0], EPS);
        assertEquals(40f, gives[1], EPS);
        assertEquals(40f, gives[2], EPS);
        float[] more = EconomyMath.splitAmongAllies(40f, new float[] { 1f, 1f, 1f, 100f });
        assertEquals(37f, more[3], EPS);
    }

    @Test
    void alliesWithoutRoomGetNothingAndWhatNobodyCanHoldStaysUnshared() {
        float[] none = EconomyMath.splitAmongAllies(50f, new float[] { 0f, -5f, 0.00005f });
        assertEquals(0f, sum(none), 0f);
        float[] small = EconomyMath.splitAmongAllies(100f, new float[] { 10f, 10f });
        assertEquals(20f, sum(small), EPS, "only the room there is; the other 80 is waste");
        assertEquals(0f, sum(EconomyMath.splitAmongAllies(100f, new float[0])), 0f);
        assertEquals(0f, sum(EconomyMath.splitAmongAllies(0f, new float[] { 10f })), 0f);
        assertEquals(0f, sum(EconomyMath.splitAmongAllies(-4f, new float[] { 10f })), 0f);
    }

    @Test
    void aSplitNeverGivesMoreThanTheAmountOrAnAlliesRoom() {
        Random rng = new Random(3);
        for (int i = 0; i < 5_000; i++) {
            float amount = rng.nextFloat() * 200f;
            float[] rooms = new float[1 + rng.nextInt(6)];
            for (int k = 0; k < rooms.length; k++)
                rooms[k] = rng.nextInt(4) == 0 ? -rng.nextFloat() * 10f : rng.nextFloat() * 100f;
            float[] gives = EconomyMath.splitAmongAllies(amount, rooms);
            assertEquals(rooms.length, gives.length);
            assertTrue(sum(gives) <= amount + 0.01f, "gave " + sum(gives) + " of " + amount);
            for (int k = 0; k < rooms.length; k++) {
                assertTrue(gives[k] >= 0f, "negative gift");
                assertTrue(gives[k] <= Math.max(0f, rooms[k]) + 0.01f, "ally " + k + " got " + gives[k] + " with room " + rooms[k]);
            }
        }
    }

    // ---------------------------------------------------------------- RoN prices become metal and energy

    @Test
    void oldFoodWoodOrePricesConvertToMetalRoundedToFiveAndEnergyRoundedToTen() {
        // metal = ore + food * 0.6 + wood * 0.3 ; energy = wood * 1.5 + food * 0.8
        assertEquals(100, ResourceCost.toMetal(0, 0, 100));
        assertEquals(60, ResourceCost.toMetal(100, 0, 0));
        assertEquals(30, ResourceCost.toMetal(0, 100, 0));
        assertEquals(95, ResourceCost.toMetal(50, 50, 50));
        assertEquals(0, ResourceCost.toMetal(0, 0, 0));
        assertEquals(0, ResourceCost.toMetal(0, 0, 2), "rounds down to the nearest 5");
        assertEquals(5, ResourceCost.toMetal(0, 0, 3), "rounds up to the nearest 5");
        assertEquals(150, ResourceCost.toEnergy(0, 100, 0));
        assertEquals(80, ResourceCost.toEnergy(100, 0, 0));
        assertEquals(0, ResourceCost.toEnergy(0, 0, 500), "ore costs no energy");
        assertEquals(0, ResourceCost.toEnergy(0, 3, 0), "4.5 rounds down to the nearest 10");
        assertEquals(10, ResourceCost.toEnergy(0, 4, 0), "6 rounds up to the nearest 10");
    }

    @Test
    void theBarMeaningOfTheReusedFieldsIsOreEqualsMetalAndWoodEqualsEnergy() {
        ResourceCost c = ResourceCost.Research(0, 360, 90, 40);   // food, wood (= energy), ore (= metal), seconds
        assertEquals(90, c.metal());
        assertEquals(360, c.energy());
        assertEquals(40 * ResourceCost.TICKS_PER_SECOND, c.ticks);
    }
}
