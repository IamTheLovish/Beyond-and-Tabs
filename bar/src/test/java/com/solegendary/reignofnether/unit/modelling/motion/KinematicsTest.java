package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared motion snapshot (design/living_army.md, section 3): speed, acceleration, turn rate, time since a stop,
 * exertion, the last hit's direction and size, and the springs the lean, the start/stop rock and the flinch ride on -
 * one tick at a time from what the client sees of a unit (its movement this tick, its turn, its health).
 */
class KinematicsTest {

    private static final int B = 0;

    private static float[] fresh() {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, B, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        return s;
    }

    private static void walk(float[] s, int ticks, float speed, float turnDeg) {
        for (int t = 0; t < ticks; t++)
            Kinematics.step(s, B, 0f, 0f, speed, turnDeg, 20f, 20f, false, false);
    }

    @Test
    void straightWalkHasSpeedAndNoLean() {
        float[] s = fresh();
        walk(s, 40, 0.2f, 0f);
        assertEquals(0.2f, s[B + AnimState.SPEED], 1e-4f);
        assertEquals(0f, s[B + AnimState.YAW_RATE], 1e-4f);
        assertEquals(0f, s[B + AnimState.SP_LEAN], 0.05f);
    }

    @Test
    void turningRightLeansRightWithinTheCeiling() {
        float[] s = fresh();
        float peak = 0f;
        for (int t = 0; t < 40; t++) {
            walk(s, 1, 0.2f, 6f);
            peak = Math.max(peak, s[B + AnimState.SP_LEAN]);
        }
        assertTrue(s[B + AnimState.SP_LEAN] > 1f, "leans into a right turn: " + s[B + AnimState.SP_LEAN]);
        assertTrue(peak <= Kinematics.LEAN_MAX_DEG + 1e-3f, "never past 12 deg: " + peak);
    }

    @Test
    void aHardStopRocksBackThenForwardThenStill() {
        float[] s = fresh();
        walk(s, 40, 0.2f, 0f);
        float min = 0f, maxAfterMin = 0f;
        boolean pastMin = false;
        for (int t = 0; t < 40; t++) {
            walk(s, 1, 0f, 0f);
            float p = s[B + AnimState.SP_PITCH];
            if (p < min)
                min = p;
            else if (min < -0.5f)
                pastMin = true;
            if (pastMin)
                maxAfterMin = Math.max(maxAfterMin, p);
        }
        assertTrue(min < -1f, "the brake pitches back: " + min);
        assertTrue(maxAfterMin > 0.05f, "then rocks past upright once: " + maxAfterMin);
        assertEquals(0f, s[B + AnimState.SP_PITCH], 0.2f, "and is still by tick 40");
        assertTrue(s[B + AnimState.SINCE_STOP] >= 39f, "counts ticks since the stop: " + s[B + AnimState.SINCE_STOP]);
    }

    @Test
    void aTeleportIsNotASprint() {
        float[] s = fresh();
        walk(s, 20, 0.2f, 0f);
        float speed = s[B + AnimState.SPEED], accel = s[B + AnimState.ACCEL];
        Kinematics.step(s, B, 10f, 0f, 0f, 0f, 20f, 20f, false, false);
        assertEquals(speed, s[B + AnimState.SPEED], 1e-6f);
        assertEquals(accel, s[B + AnimState.ACCEL], 1e-6f);
    }

    @Test
    void hitDirectionIsRelativeToTheBody() {
        // body yaw 0 faces +z; its right hand points to -x
        assertEquals(0f, Kinematics.hitDir(0, 1, 0f), 1e-4f, "from the front");
        assertEquals((float) Math.PI / 2, Kinematics.hitDir(-1, 0, 0f), 1e-4f, "from the right");
        assertEquals((float) -Math.PI / 2, Kinematics.hitDir(1, 0, 0f), 1e-4f, "from the left");
        assertEquals((float) Math.PI, Math.abs(Kinematics.hitDir(0, -1, 0f)), 1e-4f, "from behind");
        // turned to face +x (yaw -90): a source at +x is in front
        assertEquals(0f, Kinematics.hitDir(1, 0, -90f), 1e-4f, "turned");
    }

    @Test
    void aSmallDropFlinchesABigDropStaggers() {
        float[] s = fresh();
        Kinematics.onHit(s, B, (float) Math.PI / 2);
        Kinematics.step(s, B, 0f, 0f, 0f, 0f, 19f, 20f, false, false);
        Kinematics.step(s, B, 0f, 0f, 0f, 0f, 19f, 20f, false, false);
        assertEquals(AnimState.FLINCH, s[B + AnimState.HIT_SIZE]);
        assertEquals((float) Math.PI / 2, s[B + AnimState.HIT_DIR], 1e-4f);

        float[] h = fresh();
        Kinematics.onHit(h, B, 0f);
        Kinematics.step(h, B, 0f, 0f, 0f, 0f, 17f, 20f, false, false);   // the health arrives a tick after the event
        assertEquals(AnimState.STAGGER, h[B + AnimState.HIT_SIZE], "15% of max health staggers");
    }

    @Test
    void healingIsNeverAHit() {
        float[] s = fresh();
        Kinematics.step(s, B, 0f, 0f, 0f, 0f, 10f, 20f, false, false);
        Kinematics.step(s, B, 0f, 0f, 0f, 0f, 20f, 20f, false, false);
        assertEquals(AnimState.NONE, s[B + AnimState.HIT_SIZE]);
    }

    @Test
    void exertionBuildsInARunAndSettlesAfter() {
        float[] s = fresh();
        walk(s, 200, 0.3f, 0f);
        float run = s[B + AnimState.EXERTION];
        assertTrue(run > 0.8f, "blown after a long run: " + run);
        walk(s, 70, 0f, 0f);
        assertTrue(s[B + AnimState.EXERTION] < run * 0.55f, "half settled within ~70 ticks: " + s[B + AnimState.EXERTION]);
    }

    @Test
    void garbageInStaysFinite() {
        float[] s = fresh();
        Kinematics.step(s, B, Float.NaN, 0f, Float.POSITIVE_INFINITY, Float.NaN, Float.NaN, 20f, false, false);
        for (int i = 0; i < AnimState.STRIDE; i++)
            assertTrue(Float.isFinite(s[B + i]), "slot " + i + " = " + s[B + i]);
    }
}
