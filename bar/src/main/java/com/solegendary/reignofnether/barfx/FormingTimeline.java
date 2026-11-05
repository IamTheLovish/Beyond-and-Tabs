package com.solegendary.reignofnether.barfx;

/**
 * The forming moment's timeline (lovish, Nov 5: "our own version" of a portal that forms instead of switching on):
 * pure arithmetic, no Minecraft types, so the game test checks it and every client plays the same shape. Four
 * overlapping phases over {@link #DURATION} seconds:
 * <ol>
 *   <li>SPARK - sparks and small flashes jump along the frame (the footprint's rim), front-loaded;</li>
 *   <li>FILL - light runs inward from the frame to the centre ({@link #fillFront}: 1 = at the frame, 0 = the centre);</li>
 *   <li>SWIRL - a spiral turns round the centre, tightening as it rises ({@link #swirlRadius}, {@link #swirlAngle});</li>
 *   <li>SETTLE - one bright pulse at the centre and a ring that spreads and fades: the portal is steady.</li>
 * </ol>
 * Pieces are counted as running totals ({@link #emitted}), so a client at 144 fps, at 7 fps or in one step emits the
 * same number, and nothing after the end (draw once per occurrence, not per frame). A bigger site gets more pieces
 * ({@link #scaleFor}), capped so the biggest stays within {@link #MAX_PIECES}.
 */
public final class FormingTimeline {
    private FormingTimeline() { }

    /** Seconds from the first spark to the settled portal (~1-2 s reads as a build-up, not a delay). */
    public static final float DURATION = 1.6f;
    /** The most pieces one forming may emit, at the biggest site: a few hundred quads, once per activation. */
    public static final int MAX_PIECES = 640;
    /** Site scale bounds: a 2x2 frame still gets every phase, a 40-wide ender no more than the cap. */
    static final float MIN_SCALE = .6f, MAX_SCALE = 2.4f;

    public enum Phase {
        SPARK(0f, .30f, 26),
        FILL(.20f, .95f, 90),
        SWIRL(.65f, 1.45f, 120),
        SETTLE(1.30f, DURATION, 22);

        public final float start, end;
        /** Pieces at scale 1. */
        final int pieces;

        Phase(float start, float end, int pieces) {
            this.start = start;
            this.end = end;
            this.pieces = pieces;
        }
    }

    /** How far through a phase t (seconds since the forming began) is: 0 before it, 1 after it. */
    public static float progress(Phase p, float t) {
        if (t <= p.start)
            return 0f;
        if (t >= p.end)
            return 1f;
        return (t - p.start) / (p.end - p.start);
    }

    /** The share of a phase's pieces out by progress x: the spark and the settle burst early, fill and swirl ease. */
    static float shape(Phase p, float x) {
        return switch (p) {
            case SPARK -> 1f - (1f - x) * (1f - x);
            case SETTLE -> 1f - (1f - x) * (1f - x) * (1f - x);
            default -> x * x * (3f - 2f * x);
        };
    }

    /** Pieces a phase has emitted by time t at a site of the given scale (a running total). */
    public static int emitted(Phase p, float t, float scale) {
        float s = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        return (int) Math.floor(p.pieces * s * shape(p, progress(p, t)) + 1e-4f);
    }

    /** Pieces to emit between t0 and t1 (a frame): the difference of two running totals. */
    public static int between(Phase p, float t0, float t1, float scale) {
        return Math.max(0, emitted(p, t1, scale) - emitted(p, t0, scale));
    }

    /** The fill's front as a fraction of the half size: 1 at the frame when FILL starts, 0 at the centre when it ends. */
    public static float fillFront(float t) {
        float x = progress(Phase.FILL, t);
        return 1f - x * x * (3f - 2f * x);
    }

    /** The swirl's radius as a fraction of the half size: wide when it starts, tight (0.12) as it settles. */
    public static float swirlRadius(float t) {
        float x = progress(Phase.SWIRL, t);
        return .85f - .73f * x * x * (3f - 2f * x);
    }

    /** The swirl's angle in radians: about two and a half turns, slowing as it settles. */
    public static float swirlAngle(float t) {
        float x = progress(Phase.SWIRL, t);
        return (float) (Math.PI * 5) * (1f - (1f - x) * (1f - x));
    }

    /** A site's piece scale from its footprint half size (3 blocks = 1). */
    public static float scaleFor(float halfSize) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, halfSize / 3f));
    }

    /** Over: the client drops the entry (nothing is left behind). */
    public static boolean done(float t) {
        return t >= DURATION;
    }
}
