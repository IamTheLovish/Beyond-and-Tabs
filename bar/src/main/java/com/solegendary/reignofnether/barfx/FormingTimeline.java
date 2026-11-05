package com.solegendary.reignofnether.barfx;

/** STUB (red proof): the forming timeline's signatures only; the real timeline follows in the next commit. */
public final class FormingTimeline {
    private FormingTimeline() { }

    public static final float DURATION = 0f;
    public static final int MAX_PIECES = 0;

    public enum Phase {
        SPARK(0f, 0f), FILL(0f, 0f), SWIRL(0f, 0f), SETTLE(0f, 0f);

        public final float start, end;

        Phase(float start, float end) {
            this.start = start;
            this.end = end;
        }
    }

    public static float progress(Phase p, float t) { return 0f; }

    public static int emitted(Phase p, float t, float scale) { return 0; }

    public static int between(Phase p, float t0, float t1, float scale) { return 0; }

    public static float fillFront(float t) { return 0f; }

    public static float swirlRadius(float t) { return 0f; }

    public static float swirlAngle(float t) { return 0f; }

    public static float scaleFor(float halfSize) { return 1f; }

    public static boolean done(float t) { return true; }
}
