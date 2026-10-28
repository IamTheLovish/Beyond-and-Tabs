package com.solegendary.reignofnether.barfx;

/** Trauma-decay camera shake (stub: the tests come first). */
public final class CameraShake {
    public static final int OFF = 0, SUBTLE = 1, FULL = 2;
    public static final int YAW = 0, PITCH = 1, ROLL = 2, OFF_X = 3, OFF_Y = 4, OUT = 5;
    public static final float FP_MAX_DEG = 3f, FP_MAX_ROLL_DEG = 2f, RTS_MAX_OFFSET = 0.3f, MINOR_CAP = 0.3f;

    public static int sanitiseMode(int mode) { return mode; }
    public static float rtsZoomGain(float zoom) { return 0f; }
    public static float rtsRange(float zoom) { return 0f; }
    public static float falloff(float dist, float range) { return 0f; }
    public static float noise(float t, int axis) { return 0f; }
    public static float traumaForBlast(float r) { return 0f; }
    public static float traumaForCollapse(float half) { return 0f; }
    public static float traumaForHeavyImpact(float r) { return 0f; }
    public static float traumaForLanding(float speed) { return 0f; }

    public float trauma() { return 0f; }
    public void setTrauma(float t) { }
    public void add(float amount) { }
    public void addMinor(float amount) { }
    public void step(float dt) { }
    public void output(boolean rts, int mode, float strength, float zoom, float[] out) { }
}
