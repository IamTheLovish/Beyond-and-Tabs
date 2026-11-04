package com.solegendary.reignofnether.unit.modelling.motion;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smooth turning (Kinematics.turn / bodyYawAt, the yaw LivingEntityRendererMixin draws): the smoothness trace. The
 * server turns a ground unit with vanilla's MoveControl, up to 90 degrees a tick, and a spell goal sets the yaw
 * outright; the body a frame draws was vanilla's lerp of that - a 180-degree order spun the unit round in a tick or
 * two (lovish, Oct 30: "isnt fluid at all"). Each test replays the server's yaw tick by tick and samples the drawn yaw
 * every third of a tick, as a 60 fps client does, then measures the snap (the largest step between two samples), the
 * jolt (the largest change of that step: a turn that starts or stops dead), how soon it arrives and any overshoot.
 */
class TurnTest {

    private static final int B = 0;
    /** The drawn yaw may move at most this far in a third of a tick (vanilla: 30 on a 90/tick turn, 60 on a snap). */
    private static final float MAX_STEP = 20f;
    /** ... and its step may change by at most this much from one sample to the next (vanilla: 30 and 60). */
    private static final float MAX_JOLT = 10f;

    /** The drawn yaw every third of a tick over `ticks` ticks of the server's yaw (from `start`, already faced). */
    private static float[] trace(IntToDoubleFunction serverYaw, float start, int ticks) {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.turn(s, B, start, true);
        float[] out = new float[ticks * 3 + 1];
        out[0] = Kinematics.bodyYawAt(s, B, start, start, 1f);
        float prev = start;
        for (int t = 0; t < ticks; t++) {
            float now = (float) serverYaw.applyAsDouble(t);
            Kinematics.turn(s, B, now, false);
            for (int k = 1; k <= 3; k++)
                out[t * 3 + k] = Kinematics.bodyYawAt(s, B, prev, now, k / 3f);
            prev = now;
        }
        return out;
    }

    private static float maxStep(float[] y) {
        float m = 0f;
        for (int i = 1; i < y.length; i++)
            m = Math.max(m, Math.abs(Mth.wrapDegrees(y[i] - y[i - 1])));
        return m;
    }

    private static float maxJolt(float[] y) {
        float m = 0f;
        for (int i = 2; i < y.length; i++)
            m = Math.max(m, Math.abs(Mth.wrapDegrees(y[i] - y[i - 1]) - Mth.wrapDegrees(y[i - 1] - y[i - 2])));
        return m;
    }

    /** The tick from which the drawn yaw stays within 5 degrees of `target`. */
    private static float arrives(float[] y, float target) {
        int last = -1;
        for (int i = 0; i < y.length; i++)
            if (Math.abs(Mth.wrapDegrees(y[i] - target)) > 5f)
                last = i;
        return (last + 1) / 3f;
    }

    @Test
    void aHalfTurnOrderSwingsTheBodyRoundInsteadOfSpinningIt() {
        // MoveControl.rotlerp: 90 degrees a tick, so a 180-degree order is done in two ticks
        float[] y = trace(t -> Math.min(180f, 90f * (t + 1)), 0f, 24);
        assertTrue(maxStep(y) <= MAX_STEP, "no snap: the largest step in a third of a tick was " + maxStep(y));
        assertTrue(maxJolt(y) <= MAX_JOLT, "no dead start or stop: the step changed by " + maxJolt(y));
        assertTrue(arrives(y, 180f) <= 7f, "round by 7 ticks (0.35 s), not lagging behind: " + arrives(y, 180f));
        for (float v : y)
            assertTrue(v <= 181f, "no swing past the new heading: " + v);
    }

    @Test
    void aYawSetOutrightIsSmoothedToo() {
        // a spell goal turns the caster to its target in one go (GenericTargetedSpellGoal)
        float[] y = trace(t -> 180f, 0f, 24);
        assertTrue(maxStep(y) <= MAX_STEP, "no snap: " + maxStep(y));
        assertTrue(maxJolt(y) <= MAX_JOLT, "no dead start or stop: " + maxJolt(y));
        assertTrue(arrives(y, 180f) <= 7f, "round by 7 ticks: " + arrives(y, 180f));
    }

    @Test
    void itTurnsTheShortWayAcrossTheWrap() {
        // 170 to -170 is 20 degrees through 180, not 340 through 0
        float[] y = trace(t -> -170f, 170f, 12);
        for (float v : y)
            assertTrue(Math.abs(Mth.wrapDegrees(v)) >= 165f, "never swings round through 0: " + v);
        assertEquals(-170f, Mth.wrapDegrees(y[y.length - 1]), 1f);
    }

    @Test
    void aUnitSeenForTheFirstTimeFacesItsOwnWay() {
        // a unit coming into view must not swing in from facing 0
        float[] y = trace(t -> 120f, 120f, 4);
        for (float v : y)
            assertEquals(120f, v, 1e-3f, "facing its own way from the first frame");
    }

    @Test
    void aSteadyHeadingIsDrawnAsItIs() {
        // walking straight on: the drawn body is exactly the server's, no drift
        float[] y = trace(t -> 45f, 45f, 30);
        assertEquals(45f, y[y.length - 1], 1e-3f);
    }
}
