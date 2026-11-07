package com.solegendary.reignofnether.unit.modelling.animations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hover body's motion (HoverMotion: the Light Grid's drones, design/ai_faction.md, PR 379), written from the rules
 * before the code. The craft rules it holds (BAT animation craft): idle is never still (a slow bob, out of step with
 * its neighbours); the body leans into what it does (nose down with speed and acceleration, up when it brakes, banked
 * into a turn) through springs that never snap; the work reaches toward the job; the strike is anticipated, committed
 * and held for a few ticks so it reads at 20 tps from the RTS camera; the thrust cuts out when it dies.
 */
class HoverMotionTest {

    static final HoverMotion.Params P = HoverMotion.Params.drone();
    static final float CRUISE = 0.25f;   // blocks per tick: the T1 worker's walking pace

    /** Steps a fresh state n ticks with constant inputs and returns it. */
    static float[] settle(float speed, float accel, float yawRate, boolean working, int n) {
        float[] s = HoverMotion.newState();
        for (int i = 0; i < n; i++)
            HoverMotion.step(P, s, speed, accel, yawRate, working, 1f);
        return s;
    }

    static HoverMotion.Pose pose(float[] s, float age, int id) {
        HoverMotion.Pose out = new HoverMotion.Pose();
        HoverMotion.pose(P, s, 1f, age, id, -1f, -1f, out);
        return out;
    }

    @Test
    void idleBobsGentlyAroundItsRestHeight() {
        float[] s = settle(0f, 0f, 0f, false, 40);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        int crossings = 0;
        float prev = pose(s, 0f, 7).lift;
        for (int t = 1; t <= 240; t++) {
            float lift = pose(s, t, 7).lift;
            lo = Math.min(lo, lift);
            hi = Math.max(hi, lift);
            if ((prev < 0f) != (lift < 0f))
                crossings++;
            prev = lift;
        }
        assertTrue(hi - lo >= 1.2f * P.bobPx, "the idle drone should bob, peak to peak " + (hi - lo));
        assertTrue(hi <= P.bobPx * 1.3f && lo >= -P.bobPx * 1.3f, "the bob stays gentle: " + lo + " .. " + hi);
        // 240 ticks of a 30-70 tick cycle cross the rest height 7-16 times
        assertTrue(crossings >= 7 && crossings <= 16, "bob period off: " + crossings + " crossings in 240 ticks");
    }

    @Test
    void twoDronesSideBySideNeverBobInStep() {
        float[] s = settle(0f, 0f, 0f, false, 40);
        double dot = 0, a2 = 0, b2 = 0;
        for (int t = 0; t < 240; t++) {
            float a = pose(s, t, 1).lift, b = pose(s, t, 2).lift;
            dot += a * b;
            a2 += a * a;
            b2 += b * b;
        }
        double corr = dot / Math.sqrt(a2 * b2 + 1e-9);
        assertTrue(corr < 0.9, "neighbouring drones bob in unison (correlation " + corr + ")");
    }

    @Test
    void forwardSpeedPitchesTheNoseDownAndIsCapped() {
        float cruise = pose(settle(CRUISE, 0f, 0f, false, 60), 0f, 1).pitch;
        assertTrue(cruise > 3f, "a cruising drone should lean its nose into the move, pitch " + cruise);
        assertTrue(cruise <= P.maxPitch, "cruise pitch over the cap: " + cruise);
        float flat = pose(settle(5f, 1f, 0f, false, 60), 0f, 1).pitch;
        assertTrue(flat <= P.maxPitch + 1e-3f, "pitch must be capped at any speed: " + flat);
        float still = pose(settle(0f, 0f, 0f, false, 60), 0f, 1).pitch;
        assertEquals(0f, still, 0.05f, "a hovering drone at rest is level");
    }

    @Test
    void brakingLiftsTheNose() {
        float[] s = settle(CRUISE, 0f, 0f, false, 60);
        float cruise = pose(s, 0f, 1).pitch;
        float min = cruise;
        for (int i = 0; i < 12; i++) {       // a hard stop: the speed falls to 0 over 3 ticks, then holds
            float speed = Math.max(0f, CRUISE * (1f - (i + 1) / 3f));
            float accel = i < 3 ? -CRUISE / 3f : 0f;
            HoverMotion.step(P, s, speed, accel, 0f, false, 1f);
            min = Math.min(min, pose(s, 0f, 1).pitch);
        }
        assertTrue(min < -1f, "braking from cruise should lift the nose (min pitch " + min + ")");
    }

    @Test
    void banksIntoTheTurnBothWaysAndIsCapped() {
        float right = pose(settle(CRUISE, 0f, 10f, false, 60), 0f, 1).roll;
        float left = pose(settle(CRUISE, 0f, -10f, false, 60), 0f, 1).roll;
        assertTrue(right > 5f, "a right turn should roll the drone to its right, roll " + right);
        assertTrue(left < -5f, "a left turn should roll the drone to its left, roll " + left);
        float hard = pose(settle(CRUISE, 0f, 90f, false, 60), 0f, 1).roll;
        assertTrue(hard <= P.maxBank + 1e-3f, "bank must be capped: " + hard);
    }

    @Test
    void theSpringsNeverSnap() {
        float[] s = settle(0f, 0f, 0f, false, 20);
        float prevPitch = pose(s, 0f, 1).pitch, prevRoll = pose(s, 0f, 1).roll;
        float worst = 0f;
        for (int i = 0; i < 40; i++) {   // a step change: off at full cruise into a hard turn
            HoverMotion.step(P, s, CRUISE, i == 0 ? CRUISE : 0f, 20f, false, 1f);
            HoverMotion.Pose p = pose(s, 0f, 1);
            worst = Math.max(worst, Math.max(Math.abs(p.pitch - prevPitch), Math.abs(p.roll - prevRoll)));
            prevPitch = p.pitch;
            prevRoll = p.roll;
        }
        assertTrue(worst <= 4f, "a lean moved " + worst + " degrees in one tick (snap)");
        float[] settled = settle(CRUISE, 0f, 20f, false, 40);
        assertEquals(pose(settled, 0f, 1).roll, prevRoll, 1.0f, "the bank should have settled within 40 ticks");
    }

    @Test
    void workReachesTheArmsForwardAndPulsesTheEmitter() {
        float[] s = settle(0f, 0f, 0f, true, 30);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int t = 0; t < 40; t++) {
            HoverMotion.Pose p = pose(s, t, 3);
            assertTrue(p.armL >= 30f && p.armR >= 30f, "working arms should reach forward: " + p.armL + " / " + p.armR);
            lo = Math.min(lo, p.emit);
            hi = Math.max(hi, p.emit);
        }
        assertTrue(hi >= 0.9f && lo <= 0.6f && lo >= 0f, "the emitter should pulse while working: " + lo + " .. " + hi);
        HoverMotion.Pose idle = pose(settle(0f, 0f, 0f, false, 30), 0f, 3);
        assertTrue(idle.armL < 15f && idle.armR < 15f, "idle arms stay folded: " + idle.armL + " / " + idle.armR);
    }

    @Test
    void theStrikeIsAnticipatedCommittedAndHeld() {
        float[] s = settle(0f, 0f, 0f, false, 30);
        HoverMotion.Pose rest = pose(s, 0f, 4);
        java.util.function.Function<Float, HoverMotion.Pose> at = ph -> {
            HoverMotion.Pose out = new HoverMotion.Pose();
            HoverMotion.pose(P, s, 1f, 0f, 4, ph, -1f, out);
            return out;
        };
        assertTrue(at.apply(0.25f).armR < rest.armR - 10f, "the strike should wind back first: " + at.apply(0.25f).armR);
        float strike = at.apply(P.hitP).armR;
        assertTrue(strike >= 45f, "the strike should drive the arms forward, at the hit " + strike);
        // the extreme holds ~2-3 ticks of a 20-tick swing (0.10-0.15 of it)
        for (float ph = P.hitP; ph <= P.hitP + 0.10f; ph += 0.02f)
            assertTrue(Math.abs(at.apply(ph).armR - strike) <= 6f, "the strike pose should hold at " + ph);
        assertEquals(rest.armR, at.apply(1f).armR, 3f, "the arms come back to rest at the end of the swing");
        assertTrue(at.apply(P.hitP).emit >= 0.95f, "the emitter flares on the hit");
    }

    @Test
    void theThrustCutsOutWhenItDies() {
        float[] s = settle(0f, 0f, 0f, false, 20);
        HoverMotion.Pose alive = pose(s, 0f, 5);
        assertEquals(1f, alive.thrust, 1e-4f, "a living drone's thrust is full");
        HoverMotion.Pose out = new HoverMotion.Pose();
        HoverMotion.pose(P, s, 1f, 0f, 5, -1f, 8f, out);
        assertEquals(0f, out.thrust, 1e-4f, "8 ticks into its death the thrust is out");
        HoverMotion.pose(P, s, 1f, 0f, 5, -1f, 0.5f, out);
        assertTrue(out.thrust > 0f, "the thrust sputters out rather than vanishing on the first frame");
        assertEquals(0f, out.lift, 1e-4f, "a dying drone stops bobbing (the death pose drops it)");
    }
}
