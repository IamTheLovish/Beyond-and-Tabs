package com.solegendary.reignofnether.unit.modelling.motion;

/**
 * Combat feel, part 3 (lovish approved Oct 28): a dying unit's bones collapse physically instead of vanilla's red flash
 * and stiff 90-degree tip. No ragdoll library drives our bodies (no GeckoLib here - every body is vanilla
 * {@code ModelPart}s, design/animation-foundation-audit.md), so this is our own physics-lite, pure and headless-tested
 * (DeathCollapseTest); {@code DeathCollapseClient} puts it on the body through the living army's bone layout.
 *
 * <p>The model, per collapse (one slot of {@link #STRIDE} floats in a pooled slab, {@link DeathCollapsePool}):
 * <ul>
 *   <li><b>The root</b> tips about the feet like an inverted pendulum: a start speed from the killing blow, gravity
 *   pulling it over ({@code sin(tilt)}), the ground at 90 degrees (a small bounce, then it rests). It falls along
 *   {@link #fallDir} - away from where the blow came from, with a little randomness - and slides a little along it.</li>
 *   <li><b>Each bone</b> (torso, head, arms, legs, crossed arms; {@link Motion}'s ids) swings in the fall plane and
 *   across it. The blow gives it a start speed (the torso folds, the head whips back, the arms lag); gravity pulls a
 *   hanging limb towards the world's down and tips an upright one (torso, head) over; the ground stops any bone whose
 *   tip would go under it (from its pivot's height above the ground and its length) and takes its speed with a small
 *   bounce and friction; joints have limits. Across the plane each bone springs (critically damped) to a splay.</li>
 *   <li>It settles by ~1 s, then from {@link #SINK_START} sinks away into the ground until {@link #DURATION} - the fade
 *   that works with every layer (team colour, glow, armour), where an alpha fade would need every pass translucent.</li>
 * </ul>
 * Fixed steps of {@link #STEP} s, so a death looks the same at any frame rate, and advancing to the same time twice (a
 * shader pack's shadow pass, then the view) changes nothing. Allocation-free.
 *
 * <p>Sign conventions match {@link Motion}: in the body's frame forward is +, right is +; a fall direction is radians
 * from forward towards the right (0 = falls forward, PI/2 = to its right). A bone's swing moves its free end (a hanging
 * limb's hand or foot, an upright bone's top) along the fall direction (+) or across it, to the fall's right (+).
 */
public final class DeathCollapse {

    private DeathCollapse() {}

    // the living army's bone ids and channels (DeathCollapseBonesTest keeps them equal to Motion's)
    public static final int ROOT = 0, BODY = 1, HEAD = 2, ARM_R = 3, ARM_L = 4, LEG_R = 5, LEG_L = 6, ARMS = 7;
    public static final int BONES = 8, CH = 6, OUT = BONES * CH, RX = 0, RZ = 2;

    /** Seconds: the whole collapse (the approved ~1.2-1.5 s), and when the sink starts. */
    public static final float DURATION = 1.4f, SINK_START = 1.05f;
    /** Fixed physics step, seconds. */
    public static final float STEP = 1f / 120f;
    /** At most this many at once; past it the plain death plays (DeathCollapseClient). */
    public static final int MAX_CONCURRENT = 40;
    public static final int STRIDE = 64;

    // ---- slot layout
    static final int N = 0, TILT = 1, SPIN = 2, DIR = 3, SLIDE = 4, SLIDE_V = 5, LIFT = 6, SINK = 7, RESTING = 8;
    static final int BONE0 = 10, BW = 5, SA = 0, VA = 1, SC = 2, VC = 3, SC_TARGET = 4;

    // ---- the root
    private static final float HALF_PI = (float) (Math.PI / 2);
    /** Gravity over the body's height for the tip (1/s^2): a ~0.5 s topple. */
    private static final float ROOT_K = 10f, ROOT_DAMP = 0.5f, ROOT_BOUNCE = 0.25f, ROOT_REST_SPIN = 0.5f;
    private static final float SLIDE_FRICTION = 2.5f;
    /** The fall direction's randomness either side of the push, radians. */
    private static final float DIR_JITTER = 0.35f;

    // ---- the bones, by id (ROOT unused): pivot height above the feet and length (model px, a humanoid's), hanging
    // (arms, legs) or upright (torso, head), joint limit (rad), gravity (1/s^2), the blow's start swing (rad/s at
    // strength 1; + = along the fall) and across-swing, and the splay the bone settles to across the fall (rad)
    private static final float[] PIVOT = {0f, 12f, 24f, 22f, 22f, 12f, 12f, 20f};
    private static final float[] LENGTH = {0f, 12f, 8f, 12f, 12f, 12f, 12f, 8f};
    private static final boolean[] HANGING = {false, false, false, true, true, true, true, true};
    private static final float[] LIMIT = {0f, 0.9f, 0.9f, 2.4f, 2.4f, 0.8f, 0.8f, 1.2f};
    private static final float[] GRAVITY = {0f, 14f, 16f, 14f, 14f, 10f, 10f, 12f};
    private static final float[] KICK = {0f, 1.2f, -2.0f, -2.2f, -2.2f, -0.4f, -0.4f, -1.5f};
    private static final float[] KICK_ACROSS = {0f, 0.6f, 1.5f, 2.0f, 2.0f, 0.6f, 0.6f, 1.0f};
    private static final float[] SPLAY = {0f, 0f, 0.2f, 0.5f, -0.5f, 0.15f, -0.15f, 0.2f};   // + = to the unit's right
    private static final float BONE_DAMP = 3f, ACROSS_K = 30f, ACROSS_DAMP = 11f, ACROSS_LIMIT = 1.2f;
    private static final float GROUND_BOUNCE = 0.2f, GROUND_FRICTION = 8f;
    /** The upper body (head, arms, crossed arms) rides on the torso; the waist is the torso's pivot. */
    private static final float WAIST = 12f;

    private static final int[] ORDER = {BODY, HEAD, ARM_R, ARM_L, LEG_R, LEG_L, ARMS};

    // ------------------------------------------------------------------ start / step

    /**
     * Starts a collapse in slot {@code b}.
     *
     * @param dir      the push's direction in the body frame (radians, 0 = forward, PI/2 = right): the way it falls
     * @param strength the blow, ~1 for an ordinary kill, up to 3 (bad values read as 0)
     * @param seed     the randomness (the entity id): the same seed gives the same death
     * @param liftPx   how far the lying body sits above its feet' plane (half its thickness), model px
     * @param sinkPx   how deep it sinks away at the end, model px
     */
    public static void start(float[] s, int b, float dir, float strength, int seed, float liftPx, float sinkPx) {
        java.util.Arrays.fill(s, b, b + STRIDE, 0f);
        if (!Float.isFinite(dir))
            dir = 0f;
        float st = Float.isNaN(strength) ? 0f : Math.max(0f, Math.min(3f, strength));
        float sf = 0.5f + 0.5f * st;
        s[b + DIR] = wrap(dir + (rand(seed, 0) * 2f - 1f) * DIR_JITTER);
        s[b + SPIN] = (0.6f + 1.0f * st) * (0.85f + 0.3f * rand(seed, 1));
        s[b + SLIDE_V] = (0.5f + 0.6f * st) * (0.85f + 0.3f * rand(seed, 2));
        s[b + LIFT] = finitePos(liftPx);
        s[b + SINK] = finitePos(sinkPx);
        float c = (float) Math.cos(s[b + DIR]);   // the unit's right, seen across the fall
        for (int bone : ORDER) {
            int k = b + BONE0 + bone * BW;
            s[k + VA] = KICK[bone] * sf * (0.6f + 0.8f * rand(seed, 10 + bone));
            s[k + VC] = KICK_ACROSS[bone] * sf * (rand(seed, 20 + bone) * 2f - 1f);
            // the splay is to the unit's right/left; across the fall that is its component on the fall's right
            float splay = SPLAY[bone] * (0.6f + 0.8f * rand(seed, 30 + bone));
            if (bone == HEAD)
                splay *= rand(seed, 40) < 0.5f ? -1f : 1f;
            s[k + SC_TARGET] = splay * c;
        }
    }

    /** Steps the collapse to {@code t} seconds after the death (never backwards, never past the end). */
    public static void advance(float[] s, int b, float t) {
        if (!Float.isFinite(t))
            return;
        int target = (int) Math.floor(Math.min(t, DURATION + 0.05f) / STEP + 1e-3);
        int n = (int) s[b + N];
        for (; n < target; n++)
            step(s, b);
        s[b + N] = Math.max(s[b + N], n);
    }

    private static void step(float[] s, int b) {
        final float dt = STEP;
        // the root: tip about the feet, the ground at 90 degrees
        if (s[b + RESTING] == 0f) {
            float tilt = s[b + TILT], spin = s[b + SPIN];
            spin += (ROOT_K * (float) Math.sin(tilt) - ROOT_DAMP * spin) * dt;
            tilt += spin * dt;
            if (tilt >= HALF_PI) {
                tilt = HALF_PI;
                if (spin > 0f)
                    spin = -spin * ROOT_BOUNCE;
                if (Math.abs(spin) < ROOT_REST_SPIN) {
                    spin = 0f;
                    s[b + RESTING] = 1f;
                }
            } else if (tilt < 0f) {
                tilt = 0f;
                spin = Math.max(0f, spin) + 0.05f;   // a corpse never stands back up: it keeps going over
            }
            s[b + TILT] = tilt;
            s[b + SPIN] = spin;
        }
        float v = Math.max(0f, s[b + SLIDE_V] - SLIDE_FRICTION * dt);
        s[b + SLIDE_V] = v;
        s[b + SLIDE] += v * dt;

        float tilt = s[b + TILT];
        int bodyBase = b + BONE0 + BODY * BW;
        for (int bone : ORDER) {
            int k = b + BONE0 + bone * BW;
            boolean upper = bone == HEAD || bone == ARM_R || bone == ARM_L || bone == ARMS;
            float bodySwing = s[bodyBase + SA];
            float parent = upper ? tilt + bodySwing : tilt;
            // the pivot's height above the ground: up the legs to the waist, then up the (bent) torso
            float h = upper ? WAIST * (float) Math.cos(tilt) + (PIVOT[bone] - WAIST) * (float) Math.cos(tilt + bodySwing)
                : PIVOT[bone] * (float) Math.cos(tilt);
            float sa = s[k + SA], va = s[k + VA];
            float acc = HANGING[bone] ? GRAVITY[bone] * (float) Math.sin(parent - sa)   // hangs towards the world's down
                : GRAVITY[bone] * (float) Math.sin(parent + sa);                       // an upright bone tips over
            va += (acc - BONE_DAMP * va) * dt;
            sa += va * dt;
            boolean contact = false;
            float r = Math.max(-1f, Math.min(1f, h / LENGTH[bone]));
            if (HANGING[bone]) {
                // its tip is under the ground while |parent - sa| < acos(h / L): it rests on the trailing side
                float alpha = (float) Math.acos(r);
                if (alpha > 0f && sa > parent - alpha && sa < parent + alpha) {
                    sa = parent - alpha;
                    contact = true;
                }
            } else {
                float beta = (float) Math.acos(-r);
                if (parent + sa > beta) {
                    sa = beta - parent;
                    contact = true;
                }
            }
            if (contact && va > 0f)
                va = -va * GROUND_BOUNCE;
            if (sa > LIMIT[bone]) {
                sa = LIMIT[bone];
                va = Math.min(0f, va) * GROUND_BOUNCE;
            } else if (sa < -LIMIT[bone]) {
                sa = -LIMIT[bone];
                va = Math.max(0f, va) * GROUND_BOUNCE;
            }
            float sc = s[k + SC], vc = s[k + VC];
            vc += (-ACROSS_K * (sc - s[k + SC_TARGET]) - ACROSS_DAMP * vc) * dt;
            if (contact) {
                vc *= Math.max(0f, 1f - GROUND_FRICTION * dt);
                va *= Math.max(0f, 1f - GROUND_FRICTION * dt);
            }
            sc = Math.max(-ACROSS_LIMIT, Math.min(ACROSS_LIMIT, sc + vc * dt));
            s[k + SA] = sa;
            s[k + VA] = va;
            s[k + SC] = sc;
            s[k + VC] = vc;
        }
    }

    // ------------------------------------------------------------------ reading it

    /**
     * The bones' pose in {@link Motion}'s layout ({@link #OUT} floats; RX + = the free end forward, RZ + = to the right):
     * every channel written, the root's left at 0 (the renderer tips the whole body on its pose stack).
     */
    public static void pose(float[] s, int b, float[] out) {
        java.util.Arrays.fill(out, 0, OUT, 0f);
        float dir = s[b + DIR];
        float c = (float) Math.cos(dir), sn = (float) Math.sin(dir);
        for (int bone : ORDER) {
            int k = b + BONE0 + bone * BW;
            float a = s[k + SA], x = s[k + SC];
            // along the fall: (cos, sin) in (forward, right); across, to the fall's right: (-sin, cos)
            out[bone * CH + RX] = a * c - x * sn;
            out[bone * CH + RZ] = a * sn + x * c;
        }
    }

    /** Seconds simulated so far. */
    public static float time(float[] s, int b) {
        return s[b + N] * STEP;
    }

    /** How far the body has tipped over, radians (0 standing, PI/2 lying). */
    public static float tilt(float[] s, int b) {
        return s[b + TILT];
    }

    /** The way it falls in the body frame, radians from forward towards the right. */
    public static float fallDir(float[] s, int b) {
        return s[b + DIR];
    }

    /** How far it has slid along the fall, blocks. */
    public static float slide(float[] s, int b) {
        return s[b + SLIDE];
    }

    /** The body's height over its feet' plane, model px (+ up): half its thickness as it lies, minus the final sink. */
    public static float lift(float[] s, int b) {
        float t = time(s, b);
        float sink = 0f;
        if (t > SINK_START) {
            float k = Math.min(1f, (t - SINK_START) / (DURATION - SINK_START));
            sink = s[b + SINK] * k * k * (3f - 2f * k);
        }
        return s[b + LIFT] * (float) Math.sin(s[b + TILT]) - sink;
    }

    /** A bone's swing along the fall (radians, + = its free end towards the fall). */
    public static float swing(float[] s, int b, int bone) {
        return s[b + BONE0 + bone * BW + SA];
    }

    /** A bone's swing across the fall (radians, + = to the fall's right). */
    public static float across(float[] s, int b, int bone) {
        return s[b + BONE0 + bone * BW + SC];
    }

    public static boolean done(float[] s, int b) {
        return time(s, b) >= DURATION - 1e-4f;
    }

    // ------------------------------------------------------------------ helpers

    /** A repeatable random value in [0, 1) for (seed, k). */
    static float rand(int seed, int k) {
        int h = seed * 0x9E3779B1 + k * 0x85EBCA77;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return (h >>> 8) / (float) (1 << 24);
    }

    private static float wrap(float a) {
        return (float) Math.IEEEremainder(a, Math.PI * 2);
    }

    private static float finitePos(float v) {
        return Float.isFinite(v) && v > 0f ? Math.min(v, 64f) : 0f;
    }
}
