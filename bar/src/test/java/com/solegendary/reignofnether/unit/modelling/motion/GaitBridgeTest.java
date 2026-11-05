package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gait bridge (troop animation overhaul, gain 3: design-draft-troop-animation-overhaul-2026-10-30 section 3): the
 * ~60 troop types still on vanilla's one-bone legs (zombies, skeletons, piglins, illagers, our villager bodies) walk
 * with vanilla's pendulum - the legs swing as a cosine at an amplitude set by the speed, so a slow unit shuffles and a
 * fast one strides past its own feet, the planted sole slides, and with no dip of the hips both soles rise off the
 * ground at every stride's end (8 px at a run). Each walk here drives vanilla's own walk clock the way 1.20.1 does
 * (LivingEntity.updateWalkAnimation: target min(4 x blocks a tick, 1), WalkAnimationState.update(target, 0.4)),
 * poses vanilla's legs as its models do (HumanoidModel cos(ls x 0.6662) x 1.4 x amount; IllagerModel, VillagerModel
 * and our VillagerUnitModel half that), adds {@link Motion}'s offsets, and measures the feet from the side.
 *
 * The numbers the bounds come from (tools-free Python model of the same walk, .scratch/gait_sim.py of the
 * Characters builder, Nov 5): vanilla stance slip 0.36-0.82 of the ground speed, the lower sole off the ground by up to 0.06-7.96 px, swing
 * clearance 0.06-4.13 px; the bridge 0.27-0.30, 0 px, ~1.3 px at every speed and in both families.
 */
class GaitBridgeTest {

    private static final float W = 0.6662f;
    private static final float LEG = 12f;
    private static final float[] SPEEDS = {0.05f, 0.09f, 0.12f, 0.16f, 0.22f};
    private static final float HUMANOID = 1.4f, ILLAGER = 0.7f;

    /** One frame's feet: [x forward px, sole height px] for the right then the left foot, and the vanilla walk. */
    private record Frame(float xr, float yr, float xl, float yl, float body, float speed) {}

    private static Frame[] walk(float blocksPerTick, float gain, boolean bridge, int ticks, int stopAt) {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        float ls = 0f, amt = 0f, body = 0f;
        Frame[] out = new Frame[ticks];
        float[] off = new float[Motion.OUT];
        float[] input = new float[Motion.WALK_LEN];
        for (int t = 0; t < ticks; t++) {
            float v = t < stopAt ? blocksPerTick : 0f;
            amt += (Math.min(4f * v, 1f) - amt) * 0.4f;
            ls += amt;
            body += v * 16f;
            // vanilla's legs, in Motion's semantic convention (+ = the foot forward; the part's xRot is the negative)
            float vr = -(float) Math.cos(ls * W) * gain * amt, vl = (float) Math.cos(ls * W) * gain * amt;
            float r = vr, l = vl, rootTy = 0f, liftR = 0f, liftL = 0f;
            if (bridge) {
                input[Motion.WALK_POS] = ls;
                input[Motion.WALK_SPEED] = amt;
                input[Motion.WALK_R] = vr;
                input[Motion.WALK_L] = vl;
                Motion.pose(s, 0, 42, t, 0f, 20f, new float[7], false, null, input, off);
                r += off[Motion.LEG_R * Motion.CH + Motion.RX];
                l += off[Motion.LEG_L * Motion.CH + Motion.RX];
                rootTy = off[Motion.ROOT * Motion.CH + Motion.TY];
                liftR = off[Motion.LEG_R * Motion.CH + Motion.TY];
                liftL = off[Motion.LEG_L * Motion.CH + Motion.TY];
            }
            out[t] = new Frame(body + LEG * (float) Math.sin(r), rootTy + liftR + LEG * (1f - (float) Math.cos(r)),
                body + LEG * (float) Math.sin(l), rootTy + liftL + LEG * (1f - (float) Math.cos(l)), body, v);
        }
        return out;
    }

    /**
     * {mean stance slip / ground speed, worst height of the LOWER sole px (0 = one foot always on the ground), mean swing
     * sole height px, lowest sole px (under 0 = through the ground)} over the steady walk.
     */
    private static float[] measure(Frame[] f) {
        float slip = 0f, worstFloat = 0f, clear = 0f, lowest = 0f;
        int n = 0;
        for (int t = 61; t < f.length; t++) {
            Frame a = f[t - 1], b = f[t];
            if (b.speed() <= 0f)
                continue;
            // the planted foot is the one moving BACK under the body
            boolean right = (b.xr() - b.body()) - (a.xr() - a.body()) < (b.xl() - b.body()) - (a.xl() - a.body());
            float dx = right ? b.xr() - a.xr() : b.xl() - a.xl();
            slip += Math.abs(dx) / (b.speed() * 16f);
            worstFloat = Math.max(worstFloat, Math.min(b.yr(), b.yl()));
            lowest = Math.min(lowest, Math.min(b.yr(), b.yl()));
            clear += right ? b.yl() : b.yr();
            n++;
        }
        return new float[] {slip / n, worstFloat, clear / n, lowest};
    }

    @Test
    void thePlantedFootSlidesLessThanAThirdOfTheGroundAtEverySpeedInEveryFamily() {
        for (float gain : new float[] {HUMANOID, ILLAGER})
            for (float v : SPEEDS) {
                float[] m = measure(walk(v, gain, true, 240, 240));
                float[] old = measure(walk(v, gain, false, 240, 240));
                assertTrue(m[0] <= 0.32f, "gain " + gain + " speed " + v + ": slip " + m[0] + " (vanilla " + old[0] + ")");
            }
    }

    @Test
    void theHipsDropSoOneSoleIsAlwaysOnTheGroundAndNoneGoesThrough() {
        for (float gain : new float[] {HUMANOID, ILLAGER})
            for (float v : SPEEDS) {
                float[] m = measure(walk(v, gain, true, 240, 240));
                float[] old = measure(walk(v, gain, false, 240, 240));
                assertTrue(m[1] <= 0.1f, "gain " + gain + " speed " + v + ": both soles off the ground by " + m[1]
                    + " px (vanilla " + old[1] + ")");
                assertTrue(m[3] >= -0.1f, "gain " + gain + " speed " + v + ": a sole " + -m[3] + " px through the ground");
            }
    }

    @Test
    void theSwingingFootClearsTheGroundTheSameAtEverySpeed() {
        for (float gain : new float[] {HUMANOID, ILLAGER})
            for (float v : SPEEDS) {
                float[] m = measure(walk(v, gain, true, 240, 240));
                assertTrue(m[2] >= 1.0f && m[2] <= 2.0f, "gain " + gain + " speed " + v + ": swing clearance " + m[2]);
            }
    }

    @Test
    void everyFamilyStridesTheSameAtEverySpeed() {
        float ref = -1f;
        for (float gain : new float[] {HUMANOID, ILLAGER})
            for (float v : SPEEDS) {
                Frame[] f = walk(v, gain, true, 240, 240);
                float most = 0f;
                for (int t = 60; t < f.length; t++)
                    most = Math.max(most, Math.abs(f[t].xr() - f[t].body()));
                if (ref < 0f)
                    ref = most;
                assertEquals(ref, most, 0.4f, "gain " + gain + " speed " + v + ": the stride's reach");
            }
    }

    @Test
    void aStoppedUnitStandsOnBothFeetWithNoGait() {
        Frame[] f = walk(0.12f, HUMANOID, true, 120, 60);
        Frame last = f[f.length - 1];
        assertEquals(0f, last.yr(), 0.05f, "right sole");
        assertEquals(0f, last.yl(), 0.05f, "left sole");
        assertEquals(last.body(), last.xr(), 0.3f, "right foot under the hip");
        assertEquals(last.body(), last.xl(), 0.3f, "left foot under the hip");
    }

    @Test
    void theGaitLeavesTheArmsHeadAndTorsoToTheModel() {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        float[] input = new float[Motion.WALK_LEN];
        input[Motion.WALK_POS] = 37.3f;
        input[Motion.WALK_SPEED] = 0.5f;
        input[Motion.WALK_R] = 0.4f;
        input[Motion.WALK_L] = -0.4f;
        float[] with = new float[Motion.OUT], without = new float[Motion.OUT];
        Motion.pose(s, 0, 42, 10f, 0f, 20f, new float[7], false, null, input, with);
        Motion.pose(s, 0, 42, 10f, 0f, 20f, new float[7], false, null, null, without);
        for (int bone : new int[] {Motion.BODY, Motion.HEAD, Motion.ARM_R, Motion.ARM_L, Motion.ARMS})
            for (int ch = 0; ch < Motion.CH; ch++)
                assertEquals(without[bone * Motion.CH + ch], with[bone * Motion.CH + ch], 0f, "bone " + bone + " ch " + ch);
    }

    @Test
    void aKeyframeRigGetsNoGait() {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        float[] input = new float[Motion.WALK_LEN];
        input[Motion.WALK_POS] = 37.3f;
        input[Motion.WALK_SPEED] = 0.5f;
        float[] out = new float[Motion.OUT];
        Motion.pose(s, 0, 42, 10f, 0f, 20f, new float[7], true, null, input, out);
        for (int i = 0; i < Motion.OUT; i++)
            assertEquals(0f, out[i], 0f, "channel " + i);
    }
}
