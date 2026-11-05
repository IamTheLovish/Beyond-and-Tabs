package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.units.storm.RocketCharge;
import com.solegendary.reignofnether.unit.units.storm.RocketLaunchPose;
import com.solegendary.reignofnether.unit.units.storm.RocketRackGeometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rocket Battery's storm rack (lovish, Nov 5: "a frame of copper lightning rods instead of gunpowder tubes ... the
 * rockets leave from the rack, not the hat ... the rack empties as each bolt leaves"), as plain numbers: where the four
 * rods' tips are, how the rack fills while it reloads and charges before the next volley, and how each rod empties as
 * its bolt goes. The numbers pinned are the design's, not read back from the curves.
 */
class RocketRackTest {
    private static final float FIRE = RocketRackGeometry.FIRE_TILT;

    // ------------------------------------------------------------------------------------------ where the bolts leave

    @Test
    void theRackIsFourRodsFannedOutBehindTheShoulders() {
        assertEquals(4, RocketRackGeometry.RODS);
        // facing +z (yaw 0), standing at the origin: the crew's left is +x, the rack is behind (z < 0)
        double[][] t = new double[4][];
        for (int i = 0; i < 4; i++)
            t[i] = RocketRackGeometry.tipWorld(0, 0, 0, 0f, i, FIRE);
        // the two outer rods lean out to about 0.85 blocks, the two inner ones to about 0.42, mirrored left to right
        assertEquals(-0.85, t[0][0], 0.1);
        assertEquals(-0.42, t[1][0], 0.1);
        assertEquals(0.42, t[2][0], 0.1);
        assertEquals(0.85, t[3][0], 0.1);
        // the inner tips clear the head (half a block wide) by a margin; nothing sits in front of the face
        assertTrue(t[2][0] > 0.33, "the inner rod's tip is " + t[2][0] + " blocks out, inside the head's width");
        for (double[] p : t)
            assertTrue(p[2] <= 0.05 && p[2] > -0.35, "a rod's tip is " + p[2] + " blocks along the facing, not behind the shoulders");
    }

    @Test
    void theTipsStandAboveTheShouldersAndTheInnerRodsStandTallest() {
        double[] heights = new double[4];
        for (int i = 0; i < 4; i++)
            heights[i] = RocketRackGeometry.tipWorld(0, 0, 0, 0f, i, FIRE)[1];
        for (double h : heights)
            assertTrue(h > 1.6 && h < 2.25, "a tip stands " + h + " blocks above the crew's feet");
        assertTrue(heights[1] > heights[0] + 0.1, "the inner rod is longer than the outer one");
        assertEquals(heights[1], heights[2], 1e-3);
        assertEquals(heights[0], heights[3], 1e-3);
    }

    @Test
    void theTipsFollowTheCrewsFacingAndPosition() {
        // facing -x (yaw 90): the crew's left is +z, behind it is +x
        double[] left = RocketRackGeometry.tipWorld(10, 64, 20, 90f, 3, FIRE);
        double[] right = RocketRackGeometry.tipWorld(10, 64, 20, 90f, 0, FIRE);
        assertEquals(20 + 0.85, left[2], 0.1);
        assertEquals(20 - 0.85, right[2], 0.1);
        assertEquals(10.0, left[0], 0.35);
        assertTrue(left[1] > 64 + 1.6 && left[1] < 64 + 2.25);
        // turning the crew round turns the rack with it: the same rod's tip is the same distance from the crew
        double[] a = RocketRackGeometry.tipWorld(0, 0, 0, 0f, 2, FIRE), b = RocketRackGeometry.tipWorld(0, 0, 0, 137f, 2, FIRE);
        assertEquals(Math.hypot(a[0], a[2]), Math.hypot(b[0], b[2]), 1e-4);
        assertEquals(a[1], b[1], 1e-6);
    }

    @Test
    void liftingTheRackTipsTheRodsForwardAndLowerThem() {
        for (int i = 0; i < 4; i++) {
            float[] rest = RocketRackGeometry.tip(i, RocketRackGeometry.REST_TILT), lifted = RocketRackGeometry.tip(i, FIRE);
            assertTrue(lifted[2] < rest[2], "rod " + i + " did not lean forward when the rack lifted");
            assertTrue(lifted[1] > rest[1], "rod " + i + " did not come down (y runs down) when the rack lifted");
        }
    }

    // ------------------------------------------------------------------------------------------ the rack empties

    @Test
    void eachRodEmptiesTheTickItsBoltLeavesInTheOrderTheyFire() {
        int gap = RocketLaunchPose.GAP;
        for (int i = 0; i < 4; i++) {
            assertEquals(1f, RocketLaunchPose.loaded(i, i * gap - 0.01f), 1e-3f, "rod " + i + " should still be loaded just before its bolt");
            assertEquals(0f, RocketLaunchPose.loaded(i, i * gap + 1f), 1e-3f, "rod " + i + " should be empty a tick after its bolt");
            float last = 1f;
            for (float age = 0f; age <= RocketLaunchPose.TOTAL_TICKS; age += 0.25f) {
                float l = RocketLaunchPose.loaded(i, age);
                assertTrue(l <= last + 1e-4f, "rod " + i + " refilled at " + age);
                assertTrue(l >= 0f && l <= 1f);
                last = l;
            }
        }
    }

    @Test
    void theRackHoldsOneBoltFewerAfterEachRocketOfTheVolley() {
        int gap = RocketLaunchPose.GAP;
        for (int shots = 0; shots <= 4; shots++) {
            float age = shots == 0 ? 0f : (shots - 1) * gap + 1.5f;
            int loaded = 0;
            for (int i = 0; i < 4; i++)
                if (RocketLaunchPose.loaded(i, shots == 0 ? -1f : age) > 0.99f)
                    loaded++;
            assertEquals(4 - shots, loaded, "bolts left on the rack after " + shots + " rocket(s)");
        }
    }

    // ------------------------------------------------------------------------------------------ reload and charge

    @Test
    void afterAVolleyTheRodsStandEmptyAndTakeTheirBoltsOneAfterAnotherAsTheCooldownRunsOut() {
        for (int i = 0; i < 4; i++)
            assertEquals(0f, RocketCharge.fill(i, RocketCharge.RELOAD_WINDOW), 1e-4f, "rod " + i + " at the start of the reload");
        assertEquals(1f, RocketCharge.fill(0, RocketCharge.RELOAD_WINDOW - 12), 1e-3f, "the first rod is loaded 12 ticks into the reload");
        assertTrue(RocketCharge.fill(3, RocketCharge.RELOAD_WINDOW - 12) < 0.01f, "the last rod is still empty then");
        for (int i = 0; i < 4; i++) {
            assertEquals(1f, RocketCharge.fill(i, RocketCharge.CHARGE_TICKS), 1e-3f, "rod " + i + " loaded for the charge");
            assertEquals(1f, RocketCharge.fill(i, 0), 1e-3f, "rod " + i + " loaded when ready");
            float last = 1f;
            for (int t = 0; t <= RocketCharge.RELOAD_WINDOW + 40; t++) {
                float f = RocketCharge.fill(i, t);
                assertTrue(f >= 0f && f <= 1f, "fill out of range for rod " + i + " at " + t);
                assertTrue(f <= last + 1e-4f, "rod " + i + " emptied as the cooldown ran out, at " + t);
                last = f;
            }
        }
        for (int t = 0; t <= RocketCharge.RELOAD_WINDOW; t++)
            for (int i = 0; i < 3; i++)
                assertTrue(RocketCharge.fill(i, t) >= RocketCharge.fill(i + 1, t) - 1e-4f, "rod " + (i + 1) + " filled before rod " + i + " at " + t);
    }

    @Test
    void theRackChargesOnlyWithATargetAndOnlyInTheLastMomentsBeforeTheVolley() {
        for (int t = 0; t <= RocketCharge.RELOAD_WINDOW + 50; t++)
            assertEquals(0f, RocketCharge.charge(t, false), 1e-6f, "an idle crew crackled at " + t);
        assertEquals(0f, RocketCharge.charge(RocketCharge.CHARGE_TICKS, true), 1e-4f);
        assertEquals(0f, RocketCharge.charge(RocketCharge.RELOAD_WINDOW, true), 1e-4f);
        assertEquals(1f, RocketCharge.charge(0, true), 1e-4f);
        float half = RocketCharge.charge(RocketCharge.CHARGE_TICKS / 2, true);
        assertTrue(half > 0.3f && half < 0.7f, "half way through the charge it was " + half);
        float last = 1f;
        for (int t = 0; t <= RocketCharge.CHARGE_TICKS + 10; t++) {
            float c = RocketCharge.charge(t, true);
            assertTrue(c >= 0f && c <= 1f && c <= last + 1e-4f, "the charge went wrong at " + t + ": " + c);
            last = c;
        }
    }

    @Test
    void theReloadWindowIsAnAttackCooldownsWorthAtMostAndTheChargeFitsInsideIt() {
        assertEquals(72, RocketCharge.RELOAD_WINDOW);
        assertEquals(24, RocketCharge.CHARGE_TICKS);
        assertTrue(RocketCharge.CHARGE_TICKS < RocketCharge.RELOAD_WINDOW);
        // the battery's cooldown is 120 ticks (one volley every 6 s): the window must fit in it
        assertTrue(RocketCharge.RELOAD_WINDOW <= 120);
    }

    @Test
    void aChargedRackIsAlreadyLiftedAtTheFirstBoltAndSettlesAtTheEnd() {
        assertEquals(1f, RocketLaunchPose.chargedLift(0f), 1e-4f);
        assertEquals(1f, RocketLaunchPose.chargedLift(2f), 1e-4f);
        for (float age = 2f; age <= RocketLaunchPose.TOTAL_TICKS; age += 0.5f)
            assertEquals(RocketLaunchPose.rackLift(age), RocketLaunchPose.chargedLift(age), 1e-5f, "differs from the plain lift at " + age);
        assertEquals(0f, RocketLaunchPose.chargedLift(RocketLaunchPose.TOTAL_TICKS), 1e-4f);
    }
}
