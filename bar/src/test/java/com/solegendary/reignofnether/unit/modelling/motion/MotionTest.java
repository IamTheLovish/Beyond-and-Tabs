package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The living army's pose (design/living_army.md, sections 2-3): from one unit's motion snapshot to per-bone offsets,
 * in the semantic convention {@link Motion} documents (rx + = forward, rz + = rolled to the unit's right, ry + = turned
 * right; tx + = right, tz + = forward, ty + = up). Pure - the same slot posed twice gives the same bones (a shader
 * pack's shadow pass and the view agree) - and every behaviour answers its own slider and the zoom gain.
 */
class MotionTest {

    private static final int ID = 42;
    private static final float[] ALL = {1, 1, 1, 1, 1, 1, 1};

    private static float[] slot() {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        return s;
    }

    private static float[] pose(float[] s, float tickF, float zoom, float[] sliders, boolean flinchOnly) {
        float[] out = new float[Motion.OUT];
        Motion.pose(s, 0, ID, tickF, 1f, zoom, sliders, flinchOnly, out);
        return out;
    }

    private static float v(float[] out, int bone, int ch) {
        return out[bone * Motion.CH + ch];
    }

    private static float[] only(int slider) {
        float[] s = new float[7];
        s[slider] = 1f;
        return s;
    }

    @Test
    void everySliderAtZeroIsStillness() {
        float[] s = slot();
        Kinematics.onHit(s, 0, 1f);
        for (int t = 0; t < 5; t++)
            Kinematics.step(s, 0, 0f, 0f, 0.3f, 8f, 15f, 20f, true, false);
        float[] out = pose(s, 105f, 40f, new float[7], false);
        for (int i = 0; i < Motion.OUT; i++)
            assertEquals(0f, out[i], 0f, "channel " + i);
    }

    @Test
    void theSameFramePosedTwiceIsIdentical() {
        float[] s = slot();
        for (int t = 0; t < 30; t++)
            Kinematics.step(s, 0, 0f, 0f, 0.2f, 5f, 20f, 20f, false, false);
        Kinematics.onHit(s, 0, 2f);
        Kinematics.step(s, 0, 0f, 0f, 0.2f, 5f, 16f, 20f, false, false);
        assertArrayEquals(pose(s, 77.3f, 50f, ALL, false), pose(s, 77.3f, 50f, ALL, false));
    }

    @Test
    void aBlowBendsTheTorsoAwayFromItsSide() {
        for (int k = 0; k < 8; k++) {
            float dir = (float) (k * Math.PI / 4 - Math.PI);
            float[] s = slot();
            Kinematics.onHit(s, 0, dir);
            Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 19.5f, 20f, false, false);
            Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 19.5f, 20f, false, false);
            float[] out = pose(s, 2f, 20f, only(Motion.FLINCH), false);
            float sin = (float) Math.sin(dir), cos = (float) Math.cos(dir);
            if (Math.abs(sin) > 0.1f)
                assertEquals(-Math.signum(sin), Math.signum(v(out, Motion.BODY, Motion.RZ)), "sector " + k + " roll");
            if (Math.abs(cos) > 0.1f)
                assertEquals(-Math.signum(cos), Math.signum(v(out, Motion.BODY, Motion.RX)), "sector " + k + " pitch");
        }
    }

    @Test
    void aRightTurnRollsTheBodyRight() {
        float[] s = slot();
        for (int t = 0; t < 30; t++)
            Kinematics.step(s, 0, 0f, 0f, 0.2f, 6f, 20f, 20f, false, false);
        assertTrue(v(pose(s, 30f, 20f, only(Motion.LEAN), false), Motion.ROOT, Motion.RZ) > 0f);
    }

    @Test
    void aHardStopPitchesTheBodyBack() {
        float[] s = slot();
        for (int t = 0; t < 30; t++)
            Kinematics.step(s, 0, 0f, 0f, 0.2f, 0f, 20f, 20f, false, false);
        for (int t = 0; t < 3; t++)
            Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, false);
        assertTrue(v(pose(s, 33f, 20f, only(Motion.ACCEL), false), Motion.ROOT, Motion.RX) < 0f);
    }

    @Test
    void extremeStateStaysInsideTheJointLimits() {
        float[] s = slot();
        for (int i = AnimState.SP_LEAN; i < AnimState.SP_WEIGHT + 3; i++)
            s[i] = 1e4f;
        s[AnimState.EXERTION] = 50f;
        s[AnimState.HIT_DIR] = 1f;
        float[] out = pose(s, 10f, 200f, new float[] {2, 2, 2, 2, 2, 2, 2}, false);
        for (int bone = 0; bone < Motion.BONES; bone++)
            for (int ch = 0; ch < Motion.CH; ch++) {
                float lim = Motion.limit(bone, ch);
                assertTrue(Math.abs(v(out, bone, ch)) <= lim + 1e-5f, "bone " + bone + " ch " + ch + " = "
                    + v(out, bone, ch) + " (limit " + lim + ")");
            }
    }

    @Test
    void theFlinchIsThreeTimesLargerFromTheRtsCamera() {
        float[] s = slot();
        Kinematics.onHit(s, 0, (float) Math.PI / 2);
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 19.5f, 20f, false, false);
        float near = v(pose(s, 1f, 20f, only(Motion.FLINCH), false), Motion.BODY, Motion.RZ);
        float far = v(pose(s, 1f, 70f, only(Motion.FLINCH), false), Motion.BODY, Motion.RZ);
        assertTrue(Math.abs(near) > 1e-4f, "the flinch shows up close: " + near);
        assertEquals(3f, far / near, 0.01f);
    }

    @Test
    void aBlownUnitBreathesFaster() {
        float[] rest = slot(), blown = slot();
        blown[AnimState.EXERTION] = 1f;
        assertEquals(60f / 25f, (float) crossings(blown) / crossings(rest), 0.45f);
    }

    private static int crossings(float[] s) {
        int n = 0;
        float last = 0f;
        for (int t = 0; t < 600; t++) {
            float x = v(pose(s, t, 20f, only(Motion.BREATH), false), Motion.BODY, Motion.RX);
            if (t > 0 && (x > 0f) != (last > 0f))
                n++;
            last = x;
        }
        return n;
    }

    @Test
    void keyframeUnitsOnlyBreatheAndFlinch() {
        float[] s = slot();
        for (int t = 0; t < 30; t++)
            Kinematics.step(s, 0, 0f, 0f, 0.2f, 6f, 20f, 20f, false, false);
        float[] out = pose(s, 30f, 20f, ALL, true);
        assertEquals(0f, v(out, Motion.ROOT, Motion.RZ), 0f, "no lean on a keyframe unit");
    }
}
