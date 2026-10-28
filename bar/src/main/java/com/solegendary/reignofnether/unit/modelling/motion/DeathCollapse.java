package com.solegendary.reignofnether.unit.modelling.motion;

/** The death collapse (stub: the tests come first). */
public final class DeathCollapse {
    public static final int ROOT = 0, BODY = 1, HEAD = 2, ARM_R = 3, ARM_L = 4, LEG_R = 5, LEG_L = 6, ARMS = 7;
    public static final int BONES = 8, CH = 6, OUT = BONES * CH, RX = 0, RZ = 2;
    public static final float DURATION = 1.4f, SINK_START = 1.05f;
    public static final int MAX_CONCURRENT = 40, STRIDE = 64;

    public static void start(float[] s, int b, float dir, float strength, int seed, float liftPx, float sinkPx) { }
    public static void advance(float[] s, int b, float t) { }
    public static void pose(float[] s, int b, float[] out) { }
    public static float tilt(float[] s, int b) { return 0f; }
    public static float fallDir(float[] s, int b) { return 0f; }
    public static float slide(float[] s, int b) { return 0f; }
    public static float lift(float[] s, int b) { return 0f; }
    public static float swing(float[] s, int b, int bone) { return 0f; }
    public static float across(float[] s, int b, int bone) { return 0f; }
    public static boolean done(float[] s, int b) { return false; }
}
