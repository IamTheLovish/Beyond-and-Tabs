package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.units.storm.RocketCharge;
import com.solegendary.reignofnether.unit.units.storm.RocketLaunchPose;
import com.solegendary.reignofnether.unit.units.storm.RocketRackGeometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rocket Battery's storm rack (lovish, Nov 5: "the rockets leave from the rack, not the hat ... the rack empties as
 * each bolt leaves"; Nov 7: "B: copper tubes"), as plain numbers: where the four tubes' muzzles are, how the rack fills while it reloads and charges before the next volley, and how each tube empties as
 * its bolt goes. The numbers pinned are the design's, not read back from the curves.
 */
class RocketRackTest {
    private static final float FIRE = RocketRackGeometry.FIRE_TILT;

    // ------------------------------------------------------------------------------------------ where the bolts leave

    @Test
    void theRackIsFourCompactTubesInTwoPodsOverTheShoulders() {
        assertEquals(4, RocketRackGeometry.TUBES);
        // facing +z (yaw 0), standing at the origin: the crew's left is +x, the rack is behind (z < 0)
        double[][] m = new double[4][];
        for (int i = 0; i < 4; i++)
            m[i] = RocketRackGeometry.muzzleWorld(0, 0, 0, 0f, i, FIRE);
        // the volley alternates sides: right pod, left pod, right, left; each pod's two tubes stand parallel (one
        // behind the other, no fan) about a third of a block out, inside the shoulders - not the half-block-wide fan of
        // rods the blind critic read as "loose blue cubes orbiting off the shoulders" (storm round 4)
        assertEquals(-0.34, m[0][0], 0.03);
        assertEquals(0.34, m[1][0], 0.03);
        assertEquals(m[0][0], m[2][0], 1e-3);
        assertEquals(m[1][0], m[3][0], 1e-3);
        for (double[] p : m)
            assertTrue(Math.abs(p[0]) >= 0.3 && Math.abs(p[0]) < 0.45,
                "a muzzle is " + p[0] + " blocks out: outside the head (0.25) and inside the shoulders");
        // the back tube of each pod is a tube's width behind the front one, so from above the pod shows two mouths
        assertTrue(m[2][2] < m[0][2] - 0.12, "the back tube is not behind the front one");
        // lifted to fire, the muzzles come over the shoulders but stay behind the face (the head's front is 0.25 out)
        for (double[] p : m)
            assertTrue(p[2] < 0.2 && p[2] > -0.35, "a muzzle is " + p[2] + " blocks along the facing, not over the shoulders");
    }

    @Test
    void theMuzzlesStandAboveTheHeadSoTheBoltsLeaveClearOfIt() {
        double[] heights = new double[4];
        for (int i = 0; i < 4; i++)
            heights[i] = RocketRackGeometry.muzzleWorld(0, 0, 0, 0f, i, FIRE)[1];
        // the crew's head top is 2.0 blocks above its feet (shoulders at 1.5, an 8 px head)
        for (double h : heights)
            assertTrue(h > 2.0 && h < 2.3, "a muzzle stands " + h + " blocks above the crew's feet");
        assertEquals(heights[0], heights[1], 1e-3);
        assertEquals(heights[2], heights[3], 1e-3);
    }

    @Test
    void theMuzzlesFollowTheCrewsFacingAndPosition() {
        // facing -x (yaw 90): the crew's left is +z, behind it is +x
        double[] left = RocketRackGeometry.muzzleWorld(10, 64, 20, 90f, 1, FIRE);
        double[] right = RocketRackGeometry.muzzleWorld(10, 64, 20, 90f, 0, FIRE);
        assertEquals(20 + 0.34, left[2], 0.03);
        assertEquals(20 - 0.34, right[2], 0.03);
        assertEquals(10.0, left[0], 0.35);
        assertTrue(left[1] > 64 + 2.0 && left[1] < 64 + 2.3);
        // turning the crew round turns the rack with it: the same tube's muzzle is the same distance from the crew
        double[] a = RocketRackGeometry.muzzleWorld(0, 0, 0, 0f, 2, FIRE), b = RocketRackGeometry.muzzleWorld(0, 0, 0, 137f, 2, FIRE);
        assertEquals(Math.hypot(a[0], a[2]), Math.hypot(b[0], b[2]), 1e-4);
        assertEquals(a[1], b[1], 1e-6);
    }

    @Test
    void liftingTheRackSwingsTheMuzzlesForwardOverTheShoulders() {
        for (int i = 0; i < 4; i++) {
            float[] rest = RocketRackGeometry.muzzle(i, RocketRackGeometry.REST_TILT), lifted = RocketRackGeometry.muzzle(i, FIRE);
            // a lift big enough to see from the RTS camera: the muzzle moves at least 4 px forward
            assertTrue(lifted[2] < rest[2] - 4f, "tube " + i + " leaned forward only " + (rest[2] - lifted[2]) + " px when the rack lifted");
            assertEquals(rest[0], lifted[0], 1e-6f, "tube " + i + " moved sideways: the rack tips about x only");
        }
    }

    // ------------------------------------------------------------------------------------------ the rack empties

    @Test
    void eachTubeEmptiesTheTickItsBoltLeavesInTheOrderTheyFire() {
        int gap = RocketLaunchPose.GAP;
        for (int i = 0; i < 4; i++) {
            assertEquals(1f, RocketLaunchPose.loaded(i, i * gap - 0.01f), 1e-3f, "tube " + i + " should still be loaded just before its bolt");
            assertEquals(0f, RocketLaunchPose.loaded(i, i * gap + 1f), 1e-3f, "tube " + i + " should be empty a tick after its bolt");
            float last = 1f;
            for (float age = 0f; age <= RocketLaunchPose.TOTAL_TICKS; age += 0.25f) {
                float l = RocketLaunchPose.loaded(i, age);
                assertTrue(l <= last + 1e-4f, "tube " + i + " refilled at " + age);
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
    void afterAVolleyTheTubesStandEmptyAndTakeTheirBoltsOneAfterAnotherAsTheCooldownRunsOut() {
        for (int i = 0; i < 4; i++)
            assertEquals(0f, RocketCharge.fill(i, RocketCharge.RELOAD_WINDOW), 1e-4f, "tube " + i + " at the start of the reload");
        assertEquals(1f, RocketCharge.fill(0, RocketCharge.RELOAD_WINDOW - 12), 1e-3f, "the first tube is loaded 12 ticks into the reload");
        assertTrue(RocketCharge.fill(3, RocketCharge.RELOAD_WINDOW - 12) < 0.01f, "the last tube is still empty then");
        for (int i = 0; i < 4; i++) {
            assertEquals(1f, RocketCharge.fill(i, RocketCharge.CHARGE_TICKS), 1e-3f, "tube " + i + " loaded for the charge");
            assertEquals(1f, RocketCharge.fill(i, 0), 1e-3f, "tube " + i + " loaded when ready");
            float last = 1f;
            for (int t = 0; t <= RocketCharge.RELOAD_WINDOW + 40; t++) {
                float f = RocketCharge.fill(i, t);
                assertTrue(f >= 0f && f <= 1f, "fill out of range for tube " + i + " at " + t);
                assertTrue(f <= last + 1e-4f, "tube " + i + " emptied as the cooldown ran out, at " + t);
                last = f;
            }
        }
        for (int t = 0; t <= RocketCharge.RELOAD_WINDOW; t++)
            for (int i = 0; i < 3; i++)
                assertTrue(RocketCharge.fill(i, t) >= RocketCharge.fill(i + 1, t) - 1e-4f, "tube " + (i + 1) + " filled before tube " + i + " at " + t);
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
