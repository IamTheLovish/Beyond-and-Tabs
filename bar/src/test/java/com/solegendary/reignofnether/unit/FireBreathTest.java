package com.solegendary.reignofnether.unit;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fire breath's rules (FireBreath; lovish, Nov 5: "the bone dragon should get a blue fire breath attack from the air
 * not the current ghast sound effects and projectiles", "the ground effect is very choppy and currently weak it was
 * barely able to hit anything without bugging out", "bone dragon can use this fire breath on the ground as well").
 * The old breath was one tick of damage every 3.3 s and its flames ended ~8 blocks above the ground they burnt; these
 * pin a breath that hurts every half second for its whole length, aims where it burns and draws flames that land there.
 * Distances are blocks, the jaws at the origin unless a test says otherwise.
 */
class FireBreathTest {

    /** The jaws of a dragon fighting from its usual height: FlyingMoveToTargetGoal's 10 over the ground, + its head. */
    private static final double AIR_JAWS_UP = 12.8;

    @Test
    void a_breath_hurts_every_half_second_from_its_first_tick_to_its_last() {
        int pulses = 0, last = -1, worstGap = 0;
        for (int t = -5; t < FireBreath.TICKS + 20; t++) {
            if (!FireBreath.pulse(t))
                continue;
            assertTrue(t >= 0 && t < FireBreath.TICKS, "a damage tick outside the breath: " + t);
            if (last >= 0)
                worstGap = Math.max(worstGap, t - last);
            last = t;
            pulses++;
        }
        assertTrue(FireBreath.pulse(0), "the first tick of a breath burns");
        assertTrue(pulses >= 2 * FireBreath.TICKS / 20, "a breath of " + FireBreath.TICKS + " ticks burns at least twice a"
            + " second, burnt " + pulses + " times");
        assertTrue(worstGap <= 10, "the longest gap between two burns is " + worstGap + " ticks (want <= 10: no second"
            + " of the breath without a burn)");
        assertTrue(FireBreath.TICKS - 1 - last <= 10, "the breath ends " + (FireBreath.TICKS - 1 - last)
            + " ticks after its last burn (a tail of flame that hurts nothing)");
        assertTrue(FireBreath.TICKS >= 40, "a breath lasts at least 2 s, is " + FireBreath.TICKS + " ticks");
    }

    @Test
    void the_sweep_runs_from_start_to_end_without_a_jump() {
        assertEquals(0.0, FireBreath.sweep(0), 1e-9, "the sweep starts at 0");
        assertEquals(1.0, FireBreath.sweep(FireBreath.TICKS - 1), 1e-9, "the sweep ends at 1");
        double prev = FireBreath.sweep(0), worst = 0;
        for (int t = 1; t < FireBreath.TICKS; t++) {
            double s = FireBreath.sweep(t);
            assertTrue(s >= prev - 1e-12, "the sweep turned back at tick " + t);
            worst = Math.max(worst, s - prev);
            prev = s;
        }
        // eased in and out: no tick moves it more than 1.6x an even sweep's step (a smoothstep peaks at 1.5x)
        assertTrue(worst <= 1.6 / (FireBreath.TICKS - 1), "the sweep jumps " + worst + " in one tick");
        assertEquals(FireBreath.sweep(10), FireBreath.sweep(10.0), 1e-12, "the client's fractional sweep agrees on whole ticks");
    }

    @Test
    void from_the_air_the_target_burns_on_every_pulse_of_the_strafe() {
        for (double up : new double[] { 10.8, AIR_JAWS_UP, 16.8 }) {
            for (double ahead : new double[] { 0, 3, 6 }) {
                // the target on the ground `ahead` blocks in front of the jaws, the dragon flying along +x
                double[] off = new double[3];
                FireBreath.airOffset(1, 0, off);
                assertEquals(FireBreath.AIR_SWEEP, Math.hypot(off[0], off[2]), 1e-9, "the strafe runs along the heading");
                assertEquals(0.0, off[1], 1e-12, "the strafe stays on the ground");
                for (int t = 0; t < FireBreath.TICKS; t++) {
                    if (!FireBreath.pulse(t))
                        continue;
                    double s = FireBreath.sweep(t);
                    double ax = FireBreath.along(ahead, off[0], s), ay = -up, az = FireBreath.along(0, off[2], s);
                    double[] d = unit(ax, ay, az);
                    double len = FireBreath.length(false, Math.sqrt(ax * ax + ay * ay + az * az));
                    assertTrue(FireBreath.reaches(0, 0, 0, d[0], d[1], d[2], len, FireBreath.tanHalf(false),
                            ahead, -up + 0.9, 0, 0.5),
                        String.format("tick %d: a foot soldier %.0f below and %.0f ahead of the jaws is not burnt", t, up, ahead));
                }
            }
        }
    }

    @Test
    void from_the_air_it_spares_what_is_level_with_it_behind_it_and_off_its_line() {
        double[] d = unit(4, -AIR_JAWS_UP, 0);
        double len = FireBreath.length(false, Math.hypot(4, AIR_JAWS_UP)), tan = FireBreath.tanHalf(false);
        assertTrue(FireBreath.reaches(0, 0, 0, d[0], d[1], d[2], len, tan, 4, -AIR_JAWS_UP + 0.9, 0, 0.5), "the aim burns");
        assertFalse(FireBreath.reaches(0, 0, 0, d[0], d[1], d[2], len, tan, 3, -1.9, 0, 0.5),
            "a unit level with the dragon 3 blocks ahead (on a tower) is burnt by a breath aimed at the ground");
        assertFalse(FireBreath.reaches(0, 0, 0, d[0], d[1], d[2], len, tan, -7, -AIR_JAWS_UP + 0.9, 0, 0.5),
            "a unit behind the dragon is burnt");
        assertFalse(FireBreath.reaches(0, 0, 0, d[0], d[1], d[2], len, tan, 4, -AIR_JAWS_UP + 0.9, 7, 0.5),
            "a unit 7 blocks off the line of fire is burnt");
        assertFalse(FireBreath.reaches(0, 0, 0, d[0], d[1], d[2], len, tan, 4 + d[0] * 6, -AIR_JAWS_UP - 6, 0, 0.5),
            "the cone reaches 6 blocks into the ground past its aim");
        assertTrue(FireBreath.reaches(0, 0, 0, d[0], d[1], d[2], len, tan, 0.3, 0.2, 0.4, 0.3),
            "something at the jaws is burnt whichever way it faces");
    }

    @Test
    void landed_the_cone_burns_along_the_ground_in_front_and_nothing_above_or_beside_it() {
        // the jaws 2.7 up, the target on the ground 6 ahead along +x: a ground-level cone sweeping side to side
        double jy = 2.7;
        double[] off = new double[3];
        FireBreath.groundOffset(0, 0, 6, 0, off);
        assertEquals(0.0, off[0], 1e-9, "the landed sweep goes side to side, not to and fro");
        assertEquals(6 * Math.tan(Math.toRadians(FireBreath.GROUND_SWEEP_DEG)), Math.abs(off[2]), 1e-9, "the landed sweep's width");
        for (int t = 0; t < FireBreath.TICKS; t++) {
            if (!FireBreath.pulse(t))
                continue;
            double s = FireBreath.sweep(t);
            double ax = FireBreath.along(6, off[0], s), az = FireBreath.along(0, off[2], s);
            double[] d = unit(ax, -jy, az);
            double len = FireBreath.length(true, Math.sqrt(ax * ax + jy * jy + az * az)), tan = FireBreath.tanHalf(true);
            assertEquals(FireBreath.GROUND_LENGTH, len, 1e-9, "the landed cone has its own length");
            assertTrue(FireBreath.reaches(0, jy, 0, d[0], d[1], d[2], len, tan, 6, 0.9, 0, 0.5),
                "tick " + t + ": the target in front of the landed dragon is not burnt");
            assertTrue(FireBreath.reaches(0, jy, 0, d[0], d[1], d[2], len, tan, 9, 0.9, 1, 0.5),
                "tick " + t + ": a unit behind the target, in the cone, is not burnt");
            assertFalse(FireBreath.reaches(0, jy, 0, d[0], d[1], d[2], len, tan, 3, 6.9, 0, 0.5),
                "tick " + t + ": a unit 6 blocks up over the line of fire is burnt");
            assertFalse(FireBreath.reaches(0, jy, 0, d[0], d[1], d[2], len, tan, 2, 0.9, 6, 0.5),
                "tick " + t + ": a unit 70 degrees off the line of fire is burnt");
            assertFalse(FireBreath.reaches(0, jy, 0, d[0], d[1], d[2], len, tan, 14, 0.9, 0, 0.5),
                "tick " + t + ": a unit past the cone's 12 blocks is burnt");
        }
    }

    @Test
    void a_building_in_the_cone_burns_and_one_behind_does_not() {
        double[] d = unit(6, -2.7, 0);
        double len = FireBreath.length(true, Math.hypot(6, 2.7)), tan = FireBreath.tanHalf(true);
        assertTrue(FireBreath.reachesBox(0, 2.7, 0, d[0], d[1], d[2], len, tan, 5, 0, -2, 9, 4, 2),
            "a building standing on the aim is not burnt");
        assertFalse(FireBreath.reachesBox(0, 2.7, 0, d[0], d[1], d[2], len, tan, -9, 0, -2, -5, 4, 2),
            "a building behind the dragon is burnt");
        assertFalse(FireBreath.reachesBox(0, 2.7, 0, d[0], d[1], d[2], len, tan, 2, 0, 9, 6, 4, 13),
            "a building far off to the side is burnt");
    }

    @Test
    void every_flame_drawn_lands_on_the_ground_at_the_aim_inside_the_cone() {
        Random rng = new Random(511);
        double[] v = new double[3];
        for (boolean landed : new boolean[] { false, true }) {
            double jy = landed ? 2.7 : AIR_JAWS_UP, ax = landed ? 6 : 3, az = 1;
            double dist = Math.sqrt(ax * ax + jy * jy + az * az), tan = FireBreath.tanHalf(landed);
            for (int i = 0; i < 2000; i++) {
                FireBreath.flame(0, jy, 0, ax, 0, az, tan, rng.nextDouble(), rng.nextDouble(), v);
                double x = v[0] * FireBreath.FLAME_SECONDS, y = jy + v[1] * FireBreath.FLAME_SECONDS, z = v[2] * FireBreath.FLAME_SECONDS;
                assertEquals(0.0, y, 1e-6, (landed ? "landed" : "airborne") + ": a flame ends " + y + " blocks above the ground");
                double off = Math.hypot(x - ax, z - az);
                assertTrue(off <= dist * tan + 1e-6, (landed ? "landed" : "airborne") + ": a flame lands " + off
                    + " from the aim, outside the cone's " + dist * tan);
            }
        }
    }

    private static double[] unit(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[] { x / l, y / l, z / l };
    }
}
