package com.solegendary.reignofnether.unit.modelling.animations;

import net.minecraft.util.Mth;

/**
 * Client only. Quadruped gaits as pure functions of the entity's walk distance (Muybridge's horse and ox plates; the
 * animation standard's creature rules): where each of the four legs is in its stride, how a planted foot moves, when
 * a foot lands. Generic for any four-legged rig ({@link Quadruped}); nothing here knows a bone.
 *
 * A stride cycle is phase 0..1 per leg. Phase 0 is the foot's touchdown at the front of its arc; it stays planted
 * (stance) for the gait's duty fraction, sweeping back at a constant rate - the body moves over it, so the foot does
 * not slide - then swings forward through the air (swing) with a lift.
 */
public final class Gait {

    private Gait() {}

    public static final int FRONT_LEFT = 0, FRONT_RIGHT = 1, HIND_LEFT = 2, HIND_RIGHT = 3;

    /** The walk: four beats, lateral sequence - left hind, left front, right hind, right front, a quarter apart. */
    private static final float[] WALK = {0.25f, 0.75f, 0f, 0.5f};
    /** The trot: diagonal pairs - left front with right hind, right front with left hind - half a cycle apart. */
    private static final float[] TROT = {0f, 0.5f, 0.5f, 0f};
    /** Stance share of the cycle: a walk keeps each foot down ~60%, a trot ~45% (there is a moment of suspension). */
    public static final float WALK_DUTY = 0.6f, TROT_DUTY = 0.45f;

    /**
     * The stride cycle in limbSwing units (vanilla's walk distance, ~4 per block) for a leg `legBlocks` long swinging
     * +-`ampDeg` with the given duty: the body covers the planted foot's chord 2 L sin(amp) during the stance, so the
     * whole cycle covers that over duty. Keeps the planted feet still at a walk.
     */
    public static float strideUnits(float legBlocks, float ampDeg, float duty) {
        return 4f * 2f * legBlocks * Mth.sin(ampDeg * Mth.DEG_TO_RAD) / duty;
    }

    /** Where leg `leg` is in its cycle, 0..1, the gait blended from walk (run = 0) to trot (run = 1). */
    public static float phase(float limbSwing, float strideUnits, int leg, float run) {
        float off = Mth.lerp(run, WALK[leg], TROT[leg]);
        float p = limbSwing / strideUnits + off;
        return p - Mth.floor(p);
    }

    /** The duty fraction for the blend walk -> trot. */
    public static float duty(float run) {
        return Mth.lerp(run, WALK_DUTY, TROT_DUTY);
    }

    /**
     * Fore-aft position of the foot, +1 (front of the arc, touchdown) .. -1 (back, lift-off): linear through the
     * stance (planted), eased through the swing. Drives the upper leg's angle.
     */
    public static float swing(float phase, float duty) {
        if (phase < duty)
            return 1f - 2f * (phase / duty);
        float s = (phase - duty) / (1f - duty);
        return -1f + 2f * Life.smooth(s);
    }

    /** 0..1 lift of the foot: 0 while planted, a sine arch through the swing. Drives the knee / elbow fold. */
    public static float lift(float phase, float duty) {
        if (phase < duty)
            return 0f;
        return Mth.sin((phase - duty) / (1f - duty) * (float) Math.PI);
    }

    /** 1 at the instant of touchdown, 0 by `width` of the cycle later: the thump a heavy foot gives the body. */
    public static float impact(float phase, float width) {
        return phase < width ? Mth.sin(phase / width * (float) Math.PI) : 0f;
    }

    /** 0..1 how far through the stance the foot is (0 at touchdown, 1 at lift-off; 0 in the swing): the push phase. */
    public static float stance(float phase, float duty) {
        return phase < duty ? phase / duty : 0f;
    }

    /** 0..1 run factor from vanilla's limbSwingAmount (speed x 4, clamped): a walk below ~0.45, a run above ~0.8. */
    public static float run(float limbSwingAmount) {
        return Life.smooth((limbSwingAmount - 0.45f) / 0.35f);
    }
}
