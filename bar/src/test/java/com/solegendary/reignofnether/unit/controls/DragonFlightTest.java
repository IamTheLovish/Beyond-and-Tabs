package com.solegendary.reignofnether.unit.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Bone Dragon's steering ({@link DragonFlight#step}) as numbers, flown tick by tick in empty air: it turns at a
 * capped rate (never the ghast's instant re-aim), flies where its nose points (no sideways slide), speeds up and slows
 * down smoothly (no impulse surges), brakes into its point without overshooting, never circles a point beside it, and
 * crosses the map about as fast as the ghast brain it replaced (no gameplay change: 0.3 movement speed ~ 0.167
 * blocks per tick on average for the ghast's nudges).
 */
class DragonFlightTest {

    private static final double CRUISE = DragonFlight.cruiseSpeed(0.3);

    /** Flies from (px, py, pz) toward (tx, ty, tz) for up to maxTicks; returns {x, y, z, ticks, maxTurn, maxSlip,
     *  maxAccel (speed-up per tick), maxSpeed, overshoot, maxDecel (slow-down per tick)}. */
    private static double[] fly(DragonFlight.State s, double px, double py, double pz, double tx, double ty, double tz,
                                int maxTicks, double stopWithin) {
        double maxTurn = 0, maxSlip = 0, maxAccel = 0, maxDecel = 0, maxSpeed = 0, closest = 1e9, overshoot = 0;
        float lastHeading = s.heading;
        double lastSpeed = s.speed;
        int t = 0;
        for (; t < maxTicks; t++) {
            double dx = tx - px, dy = ty - py, dz = tz - pz;
            double d = Math.sqrt(dx * dx + dz * dz);
            closest = Math.min(closest, d);
            overshoot = Math.max(overshoot, d - closest);
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < stopWithin)
                break;
            double[] v = DragonFlight.step(s, dx, dy, dz, CRUISE, false);
            px += v[0];
            py += v[1];
            pz += v[2];
            maxTurn = Math.max(maxTurn, Math.abs(wrap(s.heading - lastHeading)));
            double h = Math.sqrt(v[0] * v[0] + v[2] * v[2]);
            if (h > 0.02) {
                float moveYaw = (float) Math.toDegrees(-Math.atan2(v[0], v[2]));
                maxSlip = Math.max(maxSlip, Math.abs(wrap(moveYaw - s.heading)));
            }
            maxAccel = Math.max(maxAccel, s.speed - lastSpeed);
            maxDecel = Math.max(maxDecel, lastSpeed - s.speed);
            maxSpeed = Math.max(maxSpeed, h);
            lastHeading = s.heading;
            lastSpeed = s.speed;
        }
        return new double[] {px, py, pz, t, maxTurn, maxSlip, maxAccel, maxSpeed, overshoot, maxDecel};
    }

    private static double wrap(double a) {
        a %= 360;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    @Test
    void crossesTheMapAboutAsFastAsTheGhastBrainAndSmoothly() {
        DragonFlight.State s = new DragonFlight.State(0f);
        double[] r = fly(s, 0, 80, 0, 0, 80, 60, 2000, 2.0);
        // the ghast's nudges averaged ~0.167 b/t: 60 blocks in ~350 ticks; the dragon may lose a little to its take-off
        // and its braking, never much
        assertTrue(r[3] < 420, "60 blocks took " + r[3] + " ticks");
        assertTrue(r[7] <= CRUISE * 1.02, "never faster than cruise: " + r[7]);
        assertTrue(r[6] <= DragonFlight.ACCEL + 1e-6, "no surge: speeds up " + r[6] + " b/t in a tick");
        assertTrue(r[9] <= DragonFlight.DECEL + 1e-6, "no dead stop: slows " + r[9] + " b/t in a tick");
    }

    @Test
    void aPointBehindIsReachedByATurnNotAReversal() {
        DragonFlight.State s = new DragonFlight.State(0f);              // facing +z
        fly(s, 0, 80, 0, 0, 80, 40, 120, 0);                           // up to cruise heading +z
        assertTrue(s.speed > CRUISE * 0.9, "at cruise first: " + s.speed);
        double[] r = fly(s, 0, 80, 0, 0, 80, -40, 2000, 2.0);         // the point is now straight behind
        assertTrue(r[4] <= DragonFlight.TURN_SLOW + 1e-3, "turned at most " + DragonFlight.TURN_SLOW + " deg/tick: " + r[4]);
        assertTrue(r[5] < 1.0, "flies where its nose points (slip " + r[5] + " deg)");
        assertTrue(r[3] < 2000, "got there");
    }

    @Test
    void brakesIntoItsPointWithoutOvershooting() {
        DragonFlight.State s = new DragonFlight.State(0f);
        double[] r = fly(s, 0, 80, 0, 0, 80, 50, 2000, 0.6);
        assertTrue(r[3] < 2000, "arrived");
        assertTrue(r[8] < 1.0, "overshoot " + r[8] + " blocks");
        // and the last few blocks are slow: it comes in under the brake, not at cruise
        DragonFlight.State s2 = new DragonFlight.State(0f);
        fly(s2, 0, 80, 0, 0, 80, 50, 2000, 3.0);
        assertTrue(s2.speed < CRUISE * 0.8, "3 blocks out it is braking: " + s2.speed);
        DragonFlight.State s3 = new DragonFlight.State(0f);
        fly(s3, 0, 80, 0, 0, 80, 50, 2000, 1.2);
        assertTrue(s3.speed < CRUISE * 0.45, "a block out it is nearly hovering: " + s3.speed);
    }

    @Test
    void neverCirclesAPointBesideIt() {
        DragonFlight.State s = new DragonFlight.State(0f);
        fly(s, 0, 80, 0, 0, 80, 40, 120, 0);                           // cruising along +z
        // a point 3 blocks off its left wing (MC yaw 0 = +z, so -x is ... either side will do)
        double[] r = fly(s, 0, 80, 0, 3, 80, 0, 200, 1.5);
        assertTrue(r[3] < 200, "reached the point beside it in " + r[3] + " ticks (no orbit)");
    }

    /** Hovering on its point, a point a few tenths of a block off (a separation push) does not swing the nose round
     *  (#145 review: r1 re-aimed whenever the point was more than 0.05 away, so the hover's nose jittered). */
    @Test
    void aPointInsideTheStopRadiusDoesNotTurnTheNose() {
        DragonFlight.State s = new DragonFlight.State(0f);
        for (int t = 0; t < 20; t++)
            DragonFlight.step(s, -0.3, 0, 0.05, CRUISE, false);        // 0.3 off its left side, inside STOP
        assertTrue(Math.abs(s.heading) < 1e-3, "the nose turned " + s.heading + " deg for a point 0.3 blocks off");
    }

    @Test
    void climbsAndDivesGently() {
        DragonFlight.State s = new DragonFlight.State(0f);
        double lastVy = 0, maxDvy = 0, maxVy = 0;
        double y = 70;
        for (int t = 0; t < 200; t++) {
            double[] v = DragonFlight.step(s, 0, 90 - y, 30, CRUISE, false);
            y += v[1];
            maxDvy = Math.max(maxDvy, Math.abs(v[1] - lastVy));
            maxVy = Math.max(maxVy, Math.abs(v[1]));
            lastVy = v[1];
        }
        assertTrue(maxVy <= DragonFlight.CLIMB_MAX + 1e-6, "climb capped: " + maxVy);
        assertTrue(maxDvy <= DragonFlight.CLIMB_ACCEL + 1e-6, "climb eases in: " + maxDvy);
        assertTrue(Math.abs(y - 90) < 1.5, "got to its height: " + y);
    }

    /** The dive (lovish's land command): it stoops onto a ground spot fast - far quicker down than its climb cap -
     *  reaching the ground near the spot, its nose still turning at the capped rate. */
    @Test
    void theDiveStoopsOntoTheSpot() {
        DragonFlight.State s = new DragonFlight.State(0f);
        fly(s, 0, 80, 0, 0, 80, 40, 120, 0);                           // cruising along +z at 80
        double x = 0, y = 80, z = 0, maxVy = 0, maxTurn = 0;
        float last = s.heading;
        int t = 0;
        for (; t < 200 && y > 70.05; t++) {
            double[] v = DragonFlight.dive(s, 6 - x, 70 - y, 14 - z, CRUISE);   // a spot 10 down, ahead and to the side
            x += v[0];
            y = Math.max(70, y + v[1]);
            z += v[2];
            maxVy = Math.max(maxVy, -v[1]);
            maxTurn = Math.max(maxTurn, Math.abs(wrap(s.heading - last)));
            last = s.heading;
        }
        double off = Math.sqrt((x - 6) * (x - 6) + (z - 14) * (z - 14));
        assertTrue(t < 80, "down in under 4 s: " + t + " ticks");
        assertTrue(maxVy > DragonFlight.CLIMB_MAX * 2, "a stoop, not a sink: " + maxVy);
        assertTrue(maxVy <= DragonFlight.DIVE_VY + 1e-6, "but capped: " + maxVy);
        assertTrue(off < 2.5, "lands near the spot: " + off + " blocks off");
        assertTrue(maxTurn <= DragonFlight.TURN_SLOW + 1e-3, "turns at the capped rate: " + maxTurn);
    }

    /** From a standstill facing away from the spot (a hovering dragon ordered to dive beside it) it turns first and
     *  still comes down AT the spot - #300 CI job 122632111698: paced on its speed, not on how fast it closed on the
     *  spot, it sank while it turned and landed 4.6 blocks off. */
    @Test
    void theDiveFromAStandstillFacingAwayStillLandsOnTheSpot() {
        DragonFlight.State s = new DragonFlight.State(0f);             // facing +z, not moving
        double x = 0, y = 80, z = 0;
        int t = 0;
        for (; t < 200 && y > 71.05; t++) {
            double[] v = DragonFlight.dive(s, 8.5 - x, 71 - y, 0 - z, CRUISE);   // 8.5 to its side (+x), 9 down
            x += v[0];
            y = Math.max(71, y + v[1]);
            z += v[2];
        }
        double off = Math.sqrt((x - 8.5) * (x - 8.5) + z * z);
        assertTrue(off < 2.0, "landed " + off + " blocks from the spot");
        assertTrue(t < 100, "down in " + t + " ticks (want under 5 s)");
    }

    @Test
    void stoppingBrakesSmoothlyAlongTheNose() {
        DragonFlight.State s = new DragonFlight.State(0f);
        fly(s, 0, 80, 0, 0, 80, 40, 120, 0);
        double last = s.speed;
        int t = 0;
        while (s.speed > 0 && t++ < 200) {
            double[] v = DragonFlight.brake(s);
            assertTrue(last - s.speed <= DragonFlight.DECEL + 1e-6, "no dead stop");
            assertTrue(v[2] >= -1e-9, "still moving forward (+z) while it brakes, never backward: " + v[2]);
            last = s.speed;
        }
        assertTrue(t < 60, "stops within 3 s: " + t);
    }
}
