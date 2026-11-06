package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.units.storm.RocketFlight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rocket Battery's rocket flies faster than the mortar's lob (lovish, Nov 5: "change the rocket bots motor shots to
 * another animation and projectile"). The mortar's flight time is written out here as it was on Nov 5 - 20 ticks plus
 * the flat distance, kept between 20 and 44 - so the test does not read the number back from the code it checks.
 */
class RocketFlightTest {

    /** TODAY's mortar rule (BombardShell.flightTicks), spelled out. */
    private static int mortarTicks(double flat) {
        return (int) Math.max(20, Math.min(44, 20 + flat));
    }

    @Test
    void aRocketIsInTheAirAtMostTwoThirdsAsLongAsAMortarShellAtEveryRange() {
        for (int flat = 7; flat <= 27; flat++) {
            int rocket = RocketFlight.flightTicks(flat), mortar = mortarTicks(flat);
            assertTrue(rocket <= mortar * 0.66, "at " + flat + " blocks a rocket flies " + rocket + " ticks, a mortar shell " + mortar);
        }
    }

    @Test
    void aRocketStillLeavesAQuickUnitHalfASecondToStepOutOfTheSplash() {
        for (int flat = 7; flat <= 27; flat++)
            assertTrue(RocketFlight.flightTicks(flat) >= 12, "at " + flat + " blocks a rocket flies only " + RocketFlight.flightTicks(flat) + " ticks");
    }

    @Test
    void aRocketNeverHangsLongerThanAMortarShellAtItsShortestRange() {
        for (int flat = 7; flat <= 40; flat++)
            assertTrue(RocketFlight.flightTicks(flat) <= 26, "at " + flat + " blocks a rocket flies " + RocketFlight.flightTicks(flat) + " ticks");
    }

    @Test
    void theFurtherTheShotTheLongerTheFlight() {
        int last = 0;
        for (int flat = 7; flat <= 27; flat++) {
            int t = RocketFlight.flightTicks(flat);
            assertTrue(t >= last, "the flight time fell from " + last + " to " + t + " at " + flat + " blocks");
            last = t;
        }
        assertTrue(RocketFlight.flightTicks(27) > RocketFlight.flightTicks(7), "range makes no difference to the flight time");
    }

    @Test
    void aRocketFallsHarderThanAMortarShellBecauseItHasLessTimeToDoIt() {
        // the same arc in fewer ticks needs a steeper pull; this is what keeps a 3-block apex at 24 blocks
        assertTrue(RocketFlight.GRAVITY > 0.03, "gravity " + RocketFlight.GRAVITY);
    }
}
