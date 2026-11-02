package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The idle fidgets on the body (design/living_army.md, section 2; slice 5): a standing unit adjusts its grip, glances
 * at what the scheduler chose, rolls a shoulder or looks around; nothing fidgets on the move; the fidget slider scales
 * it all and zero is stillness.
 */
class MotionFidgetTest {

    private static final int ID = 7;

    private static float[] only(int slider) {
        float[] s = new float[7];
        s[slider] = 1f;
        return s;
    }

    private static float[] pose(float[] s, float t, float[] sliders) {
        float[] out = new float[Motion.OUT];
        Motion.pose(s, 0, ID, t, 1f, 20f, sliders, false, out);
        return out;
    }

    private static float v(float[] out, int bone, int ch) {
        return out[bone * Motion.CH + ch];
    }

    /** A standing unit stepped tick by tick until it starts `kind`; returns the start tick (or -1). */
    private static int until(float[] s, int kind, int maxTicks) {
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        for (int t = 0; t < maxTicks; t++) {
            Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, false);
            if (Idle.step(s, 0, ID, t, false, false) == kind)
                return t;
        }
        return -1;
    }

    @Test
    void aGlanceTurnsTheHeadToItsTarget() {
        float[] s = new float[AnimState.STRIDE];
        int t0 = until(s, Idle.GLANCE, 20000);
        assertTrue(t0 >= 0, "a glance comes");
        s[AnimState.GLANCE_YAW] = 40f;
        s[AnimState.GLANCE_PITCH] = 0f;
        float len = s[AnimState.IDLE_LEN];
        for (int t = t0 + 1; t < t0 + (int) (len / 2); t++) {
            Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, false);
            Idle.step(s, 0, ID, t, false, false);
        }
        float yawDeg = v(pose(s, t0 + len / 2, only(Motion.FIDGET)), Motion.HEAD, Motion.RY) * 57.29578f;
        assertEquals(40f, yawDeg, 6f, "at the middle of the glance the head is on its target");
    }

    @Test
    void everyFidgetMovesSomethingAndZeroSliderIsStill() {
        for (int kind = Idle.GRIP; kind <= Idle.LOOK_AROUND; kind++) {
            float[] s = new float[AnimState.STRIDE];
            int t0 = until(s, kind, 40000);
            assertTrue(t0 >= 0, "kind " + kind + " comes");
            s[AnimState.GLANCE_YAW] = 30f;
            float mid = t0 + s[AnimState.IDLE_LEN] / 2;
            for (int t = t0 + 1; t <= (int) mid; t++) {
                Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, false);
                Idle.step(s, 0, ID, t, false, false);
            }
            float[] out = pose(s, mid, only(Motion.FIDGET));
            float sum = 0f;
            for (float x : out)
                sum += Math.abs(x);
            assertTrue(sum > 0.02f, "fidget " + kind + " moves the body: " + sum);
            float[] still = pose(s, mid, new float[7]);
            for (float x : still)
                assertEquals(0f, x, 0f);
        }
    }

    @Test
    void noFidgetOnTheMove() {
        float[] s = new float[AnimState.STRIDE];
        int t0 = until(s, Idle.GRIP, 40000);
        assertTrue(t0 >= 0);
        s[AnimState.SPEED] = 0.2f;   // it set off mid-fidget
        float[] out = pose(s, t0 + 5, only(Motion.FIDGET));
        assertEquals(0f, v(out, Motion.ARM_R, Motion.RX), 1e-6f);
        assertEquals(0f, v(out, Motion.BODY, Motion.RZ), 1e-6f);
    }
}
