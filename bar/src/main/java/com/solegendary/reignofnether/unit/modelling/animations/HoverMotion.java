package com.solegendary.reignofnether.unit.modelling.animations;

/**
 * RED STUB (the tests come first): the hover body's motion for the Light Grid's drones. Nothing moves here, as on
 * main, so HoverMotionTest fails for the stated reasons; the real rules follow in the next commit.
 */
public final class HoverMotion {

    private HoverMotion() {}

    public static final int STATE = 12;

    public static final class Params {
        public float hoverPx, bobPx = 0.8f, bobTicks, pitchPerSpeed, pitchPerAccel, maxPitch = 14f, bankPerYawRate,
            maxBank = 18f, hitP = 0.45f;

        public static Params drone() {
            return new Params();
        }
    }

    public static final class Pose {
        public float lift, pitch, roll, yaw, armL, armR, emit, thrust;
    }

    public static float[] newState() {
        return new float[STATE];
    }

    public static void step(Params p, float[] s, float speed, float accel, float yawRate, boolean working, float dt) {
    }

    public static void pose(Params p, float[] s, float partial, float age, int id, float attackP, float deathT, Pose out) {
        out.lift = out.pitch = out.roll = out.yaw = out.armL = out.armR = out.emit = out.thrust = 0f;
    }
}
