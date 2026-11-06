package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.units.storm.RocketLaunchPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rocket Battery's launch animation (replacing the mortar crews' lob pose): the crew is thrown back by each rocket
 * and the rack lifts into firing position and settles. The volley is four rockets three ticks apart; every number the
 * tests pin is from that, not read back from the curves.
 */
class RocketLaunchPoseTest {

    @Test
    void theAnimationLastsFromTheFirstRocketToTheLastKickRingingOut() {
        assertEquals(4, RocketLaunchPose.ROCKETS);
        assertEquals(3, RocketLaunchPose.GAP);
        assertEquals(3 * 3 + RocketLaunchPose.KICK_TICKS, RocketLaunchPose.TOTAL_TICKS);
    }

    @Test
    void theAgeRunsFromZeroAtTheLaunchToTheFullLengthAtTheEnd() {
        int total = RocketLaunchPose.TOTAL_TICKS;
        assertEquals(0f, RocketLaunchPose.age(total, 0f), 1e-4f);
        assertEquals(7f, RocketLaunchPose.age(total - 7, 0f), 1e-4f);
        assertEquals(total, RocketLaunchPose.age(0, 0f), 1e-4f);
        // a frame between two ticks is later than the tick it follows
        assertTrue(RocketLaunchPose.age(total - 5, 0.5f) > RocketLaunchPose.age(total - 5, 0f));
        assertEquals(RocketLaunchPose.age(total - 5, 1f), RocketLaunchPose.age(total - 6, 0f), 1e-4f);
    }

    @Test
    void theCrewIsStillBeforeTheFirstRocketAndOnceTheLastKickHasRungOut() {
        assertEquals(0f, RocketLaunchPose.recoil(0f), 1e-4f);
        assertEquals(0f, RocketLaunchPose.recoil(RocketLaunchPose.TOTAL_TICKS), 1e-4f);
        assertEquals(0f, RocketLaunchPose.recoil(RocketLaunchPose.TOTAL_TICKS + 5), 1e-4f);
        assertEquals(0f, RocketLaunchPose.recoil(-3f), 1e-4f);
    }

    @Test
    void aRocketKicksTheCrewHardAtOnce() {
        // one tick after the first rocket the crew is already well back: a telegraph that reads at 20 tps
        assertTrue(RocketLaunchPose.recoil(1f) > 0.6f, "recoil one tick in: " + RocketLaunchPose.recoil(1f));
        assertTrue(RocketLaunchPose.recoil(1f) <= 1f);
    }

    @Test
    void theKickSettlesBetweenTheRocketsAndBuildsAcrossTheVolley() {
        float first = RocketLaunchPose.recoil(1f);
        float afterTheLast = RocketLaunchPose.recoil(3 * RocketLaunchPose.GAP + 1f);
        assertTrue(afterTheLast > first, "the fourth kick (" + afterTheLast + ") should land harder than the first (" + first + ")");
        assertTrue(afterTheLast <= 1f);
        // the gap before the second rocket: it has eased off but not gone (a volley, not four separate shots)
        float between = RocketLaunchPose.recoil(RocketLaunchPose.GAP - 0.01f);
        assertTrue(between < first && between > 0.1f, "between the first two rockets: " + between);
    }

    @Test
    void afterTheLastRocketTheKickDiesAwayWithoutGoingBackwards() {
        float last = Float.MAX_VALUE;
        for (float age = 3 * RocketLaunchPose.GAP + 1f; age <= RocketLaunchPose.TOTAL_TICKS; age += 0.25f) {
            float r = RocketLaunchPose.recoil(age);
            assertTrue(r >= 0f, "recoil went negative at " + age + ": " + r);
            assertTrue(r <= last + 1e-4f, "recoil rose again at " + age + ": " + last + " -> " + r);
            last = r;
        }
    }

    @Test
    void theRackLiftsAtOnceHoldsThroughTheVolleyAndSettlesAtTheEnd() {
        assertEquals(0f, RocketLaunchPose.rackLift(0f), 1e-4f);
        assertEquals(0f, RocketLaunchPose.rackLift(RocketLaunchPose.TOTAL_TICKS), 1e-4f);
        assertTrue(RocketLaunchPose.rackLift(3f) > 0.9f, "lift 3 ticks in: " + RocketLaunchPose.rackLift(3f));
        for (float age = 3f; age <= 3 * RocketLaunchPose.GAP + 1f; age += 0.5f)
            assertTrue(RocketLaunchPose.rackLift(age) > 0.9f, "the rack dropped mid-volley at " + age + ": " + RocketLaunchPose.rackLift(age));
        assertTrue(RocketLaunchPose.rackLift(RocketLaunchPose.TOTAL_TICKS - 2f) < 0.5f);
        for (float age = -2f; age <= RocketLaunchPose.TOTAL_TICKS + 2f; age += 0.5f) {
            float lift = RocketLaunchPose.rackLift(age);
            assertTrue(lift >= 0f && lift <= 1f, "lift out of range at " + age + ": " + lift);
        }
    }

    @Test
    void theCurvesAreFiniteForAnyAge() {
        for (float age : new float[] { -1000f, -1f, 0f, 0.5f, 7.3f, 19f, 1000f }) {
            assertTrue(Float.isFinite(RocketLaunchPose.recoil(age)), "recoil at " + age);
            assertTrue(Float.isFinite(RocketLaunchPose.rackLift(age)), "lift at " + age);
        }
    }
}
