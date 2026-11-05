package com.solegendary.reignofnether.unit.modelling.animations;

import java.util.Arrays;
import java.util.function.Function;

/**
 * The Tithe Binder's rig: a 1:1 port of tools/tithe_binder_anim.py, the clip driver (THE spec; design sheet
 * .scratch/unit1/design-sheet.md sections 3.2-3.3, 4.3, 5). Same state variables, same order of operations, the same
 * seeded LCG; TitheBinderRigTest replays the driver's harness and must land on every number of
 * design/fixtures/tithe_binder_clips.json within 0.01. Change the driver first, regenerate the fixture, then this.
 *
 * <pre>
 *   State st = new State(n, seed);  Inputs in = new Inputs();  Pose pose = new Pose();     // once per unit
 *   in.clip = ...; in.t = tick inside the clip (0 = its first); in.speed; in.target;    // every game tick (20 tps)
 *   TitheBinderRig.step(st, in, pose);                                                   // st and pose written
 *   TitheBinderRig.apply(pose, bones);                                                   // after the model's restPose
 * </pre>
 *
 * Pure maths: no Minecraft class anywhere in this file (the JUnit needs no game); {@link #apply} writes through the
 * tiny {@link Part} interface, which a client adapter implements over ModelPart. No allocation per step: the state
 * owns every scratch array.
 *
 * Conventions (design/models/README.md): DSL degrees, +x swings a hanging part forward / tips a standing part back,
 * +y turns the front toward -x, rotation order x then y then z; every bone rotation / offset in the pose is a DELTA
 * on the generated rest pose. A docked slot is absolute: its azimuth (0 points -z, + turns toward -x), the pommel's
 * radius / lift and the weapon's own pitch / roll. Flying and falling weapons are in model space (DSL px, y up from
 * the feet): the renderer draws them, not the bone tree. r4: the ring bone carries the tilt only; the wheel's spin is
 * inside every docked slot's angle.
 */
public final class TitheBinderRig {

    public enum Clip { IDLE, FIDGET, MOVE, VOLLEY, BIND, HIT, DEATH }

    // ------------------------------------------------------------------ constants (the driver's, 1:1) ------------
    static final double K_CHAIN = 0.22, C_CHAIN = 0.18;          // sheet 4.3: chains, tatters, cape, lantern
    static final double K_CHAIN_B = 0.30;                        // a chain's second segment
    static final double K_RADIAL = 0.25, C_RADIAL = 0.20;        // a docked weapon's radial spring (px)
    static final double K_ROLL = 0.15, C_ROLL = 0.12;            // its roll / pitch-kick spring (deg)
    static final double K_LEAN = 0.20, C_LEAN = 0.25;            // the body's lean in the move
    static final double K_SPACE = 0.18, C_SPACE = 0.25;          // slot angles re-spacing after a bind
    static final double K_TILT = 0.15, C_TILT = 0.12;            // the ring's tilt (kicked by hits)
    static final double DRIVE = 6.0;                             // deg per px/tick^2
    static final double HEM_FRONT_LO = 0.0, HEM_FRONT_HI = 40.0, HEM_BACK_LO = -40.0, HEM_BACK_HI = 0.0;
    static final double HEM_B_LO = -40.0, HEM_B_HI = 40.0, TATTER_LO = -60.0, TATTER_HI = 10.0;
    static final double LANTERN_LO = -6.0, LANTERN_HI = 25.0, CAPE_LO = -50.0, CAPE_HI = 5.0;
    static final double SHACKLE_LO = -80.0, SHACKLE_HI = 80.0;
    static final double GRAVITY = 1.28;                          // px/tick^2: falling weapons at death
    static final double DEATH_DROP = 10.5;                       // px: the crumple lands the robe on the ground
    /** tools/gen_tithe_binder.py: LIFT 5, RING r 18 at y 36 + LIFT, RING_TILT -6, BASE_YAW -18. */
    static final double LIFT = 5.0, RING_R = 18.0, RING_TILT = -6.0, BASE_YAW = -18.0;
    static final double[] RING_PIVOT = {0.0, 36.0 + LIFT, 0.0};
    static final double[] HUB = {0.0, 27.0 + LIFT, 0.0};         // where the soul chains leave the ribs
    /** Flight length per weapon k (scale 1.0, px): G.length_of(G.prims_of(k)). */
    static final double[] LENGTH = {0.0, 16.330127018922195, 14.214891670142245, 13.0, 18.748528137423854,
        29.131370849898477, 20.0, 20.0, 23.73531505608399, 34.80208152801713, 37.0, 37.0, 37.0};
    /** G.DOCK_SCALE: the docked part scale per weapon k. */
    static final double[] DOCK_SCALE = {1.0, 0.6124, 0.7387, 0.8462, 0.6134, 0.4119, 0.625, 0.65, 0.5688, 0.4023,
        0.3919, 0.3919, 0.3919};
    static final int TUMBLE = 0, ROLL = 1, FLAT = 2;
    static final int[] SPIN_KIND = {TUMBLE, TUMBLE, TUMBLE, TUMBLE, ROLL, ROLL, TUMBLE, TUMBLE, FLAT, ROLL, TUMBLE,
        TUMBLE, TUMBLE};
    static final double[] SPIN_RATE = {0, 40, 60, 50, 20, 15, 30, 25, 30, 10, 20, 20, 20};
    static final double[] LANTERN = {0.0, 23.0 + LIFT, -6.5};    // the lantern's core at rest (the bind's orb)
    // r4 volley: the strike at t16, a release every 3 ticks, sling 1, flight 6, each hit held 5, reel 4
    static final int VOLLEY_RELEASE = 16, RELEASE_GAP = 3;
    static final int SLING = 1, FLIGHT = 6, EMBED = 5, REEL = 4;
    static final double DOCK_OVERSHOOT = 2.0;
    static final double FLIGHT_SCALE = 1.25;                     // part scale in flight
    static final double LATERAL = 16.0, ARC_UP_1 = 8.0, ARC_UP_2 = 6.0;   // px: arc controls off the bearing, up
    static final double VICTIM_R = 5.0;                          // px: the tip stops 2 px inside a victim this wide
    static final double HUB_R = 6.0;                             // px: a chain leaves the ribs toward its weapon
    static final double WEAVE_X = 12.0, WEAVE_Z = 6.0, WEAVE_FORE = 10.0;  // the idle conductor weave

    /** The bones the driver moves (DSL names = the generated TitheBinderModel's). Pose.rot / Pose.off index these. */
    public static final String[] BONES = {"hover", "body", "chest", "mantle", "head", "right_arm", "left_arm",
        "right_hand", "left_hand", "right_forearm", "left_forearm", "tatter_f", "tatter_b", "tatter_l", "tatter_r",
        "cape", "hem_chain_1", "hem_chain_1_b", "hem_chain_2", "hem_chain_2_b", "hem_chain_3", "hem_chain_3_b",
        "lantern_chain", "right_shackle_chain", "left_shackle_chain", "ring"};
    static final int HOVER = 0, BODY = 1, CHEST = 2, MANTLE = 3, HEAD = 4, R_ARM = 5, L_ARM = 6, R_HAND = 7,
        L_HAND = 8, R_FORE = 9, L_FORE = 10, TATTER_F = 11, CAPE = 15, HEM_1 = 16, LANTERN_CHAIN = 22,
        R_SHACKLE = 23, L_SHACKLE = 24, RING = 25;

    /** Weapon slots 1..MAX_WEAPONS (arrays are indexed by k, index 0 unused). The model carries 10. */
    public static final int MAX_WEAPONS = 12;
    /** Pose.flyState values: FLY_STATES[state] is the driver's name. */
    public static final int FLY_NONE = 0, FLY_SLUNG = 1, FLY_FLYING = 2, FLY_EMBED = 3, FLY_REEL = 4,
        FLY_FALLING = 5, FLY_SHATTER = 6;
    public static final String[] FLY_STATES = {"", "SLUNG", "FLYING", "EMBED", "REEL", "FALLING", "SHATTER"};

    // a slot's machine (the driver's mode strings)
    static final int M_DOCKED = 0, M_FORGING = 1, M_SLUNG = 2, M_FLYING = 3, M_EMBED = 4, M_REEL = 5,
        M_FALLING = 6, M_GONE = 7;
    // the driver's springs dict, by name
    static final int S_HEM_1 = 0, S_HEM_1_B = 3, S_LANTERN_CHAIN = 6, S_CAPE = 7, S_BODY_LEAN = 8,
        S_TILT_X = 9, S_TILT_Z = 10, S_R_SHACKLE_X = 11, S_R_SHACKLE_Z = 12, S_L_SHACKLE_X = 13,
        S_L_SHACKLE_Z = 14, S_TATTER = 15, SPRINGS = 19;
    private static final double NO_LO = Double.NEGATIVE_INFINITY, NO_HI = Double.POSITIVE_INFINITY;
    private static final int NO_GROUND = Integer.MIN_VALUE;

    private TitheBinderRig() {}

    /** BONES index of a DSL bone name, -1 if the rig does not move it. */
    public static int bone(String name) {
        for (int i = 0; i < BONES.length; i++)
            if (BONES[i].equals(name))
                return i;
        return -1;
    }

    /** Easing: smoothstep s(x) = x x (3 - 2 x), clamped. */
    public static double smooth(double x) {
        x = Math.max(0.0, Math.min(1.0, x));
        return x * x * (3.0 - 2.0 * x);
    }

    // ------------------------------------------------------------------ inputs / state / pose -------------------

    /** One tick's inputs (the driver's inputs dict). Reused by the caller. */
    public static final class Inputs {
        public Clip clip = Clip.IDLE;
        /** Tick inside the clip, 0 = its first (t == 0 runs the clip's entry bookkeeping). */
        public int t;
        /** Move: v / 0.25 blocks per tick, 0..1. Read only by the MOVE clip. */
        public double speed;
        /** DSL px in model space: volley = the victim's chest, bind = the corpse. Read at the clip's t == 0. */
        public final double[] target = {0.0, 20.0, -80.0};
        /** Hit: where the blow comes from (x, z unit vector). The driver takes it but does not use it yet. */
        public final double[] hitDir = {0.0, -1.0};
    }

    /** Small key table builder over preallocated arrays: keys.of(t0, v0).k(t1, v1)...at(t). */
    static final class Keys {
        private final double[] kt = new double[12], kv = new double[12];
        private int n;

        Keys of(double t0, double v0) {
            n = 0;
            return k(t0, v0);
        }

        Keys k(double t, double v) {
            kt[n] = t;
            kv[n] = v;
            n++;
            return this;
        }

        /** The driver's keys(): piecewise smoothstep through the table; held before the first / after the last. */
        double at(double t) {
            if (t <= kt[0])
                return kv[0];
            for (int i = 0; i + 1 < n; i++) {
                double t0 = kt[i], t1 = kt[i + 1];
                if (t <= t1) {
                    double u = t1 > t0 ? smooth((t - t0) / (t1 - t0)) : 1.0;
                    return kv[i] + (kv[i + 1] - kv[i]) * u;
                }
            }
            return kv[n - 1];
        }
    }

    /** One unit's animation state (the driver's state dict) plus every scratch array the step needs. */
    public static final class State {
        int n;
        long seed;
        int tick;
        double bob, breath, look;
        int fidgetIn, fidgetT;
        double ringYaw, ringYawV;
        final double[] th = new double[SPRINGS], om = new double[SPRINGS];
        final double[][] root = new double[3][3];
        double rootZ, speed, leanX, leanV, lantern;
        // per slot
        final double[] angle = new double[MAX_WEAPONS + 1], angV = new double[MAX_WEAPONS + 1];
        final double[] radTh = new double[MAX_WEAPONS + 1], radOm = new double[MAX_WEAPONS + 1];
        final double[] rollTh = new double[MAX_WEAPONS + 1], rollOm = new double[MAX_WEAPONS + 1];
        final double[] kickTh = new double[MAX_WEAPONS + 1], kickOm = new double[MAX_WEAPONS + 1];
        final int[] mode = new int[MAX_WEAPONS + 1], since = new int[MAX_WEAPONS + 1];
        final boolean[] hasForge = new boolean[MAX_WEAPONS + 1];
        final double[] forge = new double[MAX_WEAPONS + 1];
        final double[][] p0 = new double[MAX_WEAPONS + 1][3], radDir = new double[MAX_WEAPONS + 1][3],
            tangent = new double[MAX_WEAPONS + 1][3], pEnd = new double[MAX_WEAPONS + 1][3];
        final double[][] q0 = new double[MAX_WEAPONS + 1][4], qEnd = new double[MAX_WEAPONS + 1][4];
        /** The driver's D["p_end"] present (set in flight, popped at the dock): the reel's chain hub aims at it. */
        final boolean[] hasPEnd = new boolean[MAX_WEAPONS + 1];
        /** The flight's cubic, fixed at release (the driver's D["P"]): [sling_end, P1, P2, P3]. */
        final double[][][] path = new double[MAX_WEAPONS + 1][4][3];
        // the volley's release order (-1: not in it), its last release / end, the target and its bearing
        final int[] orderOf = new int[MAX_WEAPONS + 1];
        int lastRelease, fEnd;
        final double[] target = new double[3];
        double bearing;
        // the falling weapons (death)
        final boolean[] fallOn = new boolean[MAX_WEAPONS + 1];
        final double[][] fallP = new double[MAX_WEAPONS + 1][3], fallV = new double[MAX_WEAPONS + 1][3];
        final double[][] fallQ = new double[MAX_WEAPONS + 1][4];
        final int[] fallGround = new int[MAX_WEAPONS + 1];
        // this tick's ring transform and hub (the driver's ring_now / hub_now)
        final double[] ringM = new double[9], ringO = new double[3], hub = new double[3];
        // this tick's channels (the driver's ch dict)
        double chTatter, chCape, chTilt0, chTilt1, chRingSpin, chCock, chLantern, chAlpha, chLeanTarget, chSpeed;
        double chRattle, chTrail, chLeanKick, chTiltKick, chPitchKick, chTatterKick;
        boolean chTatterHold;
        final double[] chRingOff = new double[3], chTatterAdd = new double[4];
        // scratch
        final Keys keys = new Keys();
        final double[] hov = new double[3], rootV = new double[3], acc = new double[3];
        final double[] va = new double[3], vb = new double[3], vc = new double[3], vd = new double[3],
            ve = new double[3], vf = new double[3], vg = new double[3], vh = new double[3], vi = new double[3],
            vj = new double[3];
        final double[] ma = new double[9], mb = new double[9], mc = new double[9], md = new double[9];
        final double[] qa = new double[4], qb = new double[4], qc = new double[4], qd = new double[4];
        final double[] diff = new double[MAX_WEAPONS + 1];
        final int[] left = new int[MAX_WEAPONS], right = new int[MAX_WEAPONS];

        /** The driver's initial_state(n, seed). */
        public State(int n, long seed) {
            if (n < 1 || n > MAX_WEAPONS)
                throw new IllegalArgumentException("weapons " + n + " (1.." + MAX_WEAPONS + ")");
            this.seed = seed;
            double r = lcg(this);
            this.n = n;
            for (int k = 1; k <= n; k++)
                newSlot(this, k, BASE_YAW + 360.0 / n * (k - 1), M_DOCKED);
            bob = breath = look = 0.0;
            fidgetIn = 200 + (int) (200 * r);
            fidgetT = -1;
            ringYaw = 0.0;
            ringYawV = 0.6;
            lantern = 1.0;
            Arrays.fill(orderOf, -1);
        }

        /** Bound weapons now (a bind adds one). */
        public int n() {
            return n;
        }

        /** The ring's yaw now (DSL degrees), for an effect that wants the wheel's phase. */
        public double ringYaw() {
            return ringYaw;
        }
    }

    /** One tick's output (the driver's pose dict), written in place by {@link #step}. */
    public static final class Pose {
        /** Per BONES index: DSL degrees / px ADDED to the rest pose. */
        public final double[][] rot = new double[BONES.length][3], off = new double[BONES.length][3];
        /** Docked weapons (the driver's "slots"): azimuth deg, pommel radius delta px, lift px, pitch / roll deg. */
        public final boolean[] slotOn = new boolean[MAX_WEAPONS + 1];
        public final double[] slotAngle = new double[MAX_WEAPONS + 1], slotRadial = new double[MAX_WEAPONS + 1],
            slotY = new double[MAX_WEAPONS + 1], slotPitch = new double[MAX_WEAPONS + 1],
            slotRoll = new double[MAX_WEAPONS + 1], slotScale = new double[MAX_WEAPONS + 1];
        /** Flying / falling weapons ("flying"): FLY_* state, pommel in model px, world DSL degrees, part scale. */
        public final int[] flyState = new int[MAX_WEAPONS + 1];
        public final double[][] flyPos = new double[MAX_WEAPONS + 1][3], flyRot = new double[MAX_WEAPONS + 1][3];
        public final double[] flyScale = new double[MAX_WEAPONS + 1];
        /** "hidden": weapon_k (forging, shattered), lantern_core (the lantern cracked below 0.5). */
        public final boolean[] weaponHidden = new boolean[MAX_WEAPONS + 1];
        public boolean lanternCoreHidden;
        /** Body alpha (the death dissolve) and the lantern's glow 0..2.5. */
        public double alpha = 1.0, lantern = 1.0;
        /**
         * The soul chains: a quadratic Bezier hub -> end with control point ctrl (model px), width px; the first
         * {@link #ribbons} rows are live.
         */
        public int ribbons;
        public final double[][] ribbonHub = new double[MAX_WEAPONS][3], ribbonCtrl = new double[MAX_WEAPONS][3],
            ribbonEnd = new double[MAX_WEAPONS][3];
        public final double[] ribbonWidth = new double[MAX_WEAPONS];
        /** fx.halo: the count halo's lit dots (docked + flying non-shattered; in death N minus one per 2 ticks). */
        public int halo;
        /** Bind fx (model px): the lash lantern -> corpse (t0-15), the soul orb, the flash, the forge line. */
        public boolean lashOn, orbOn, flashOn, forgeOn;
        public final double[] lashA = new double[3], lashB = new double[3], orb = new double[3],
            flash = new double[3], forgeA = new double[3], forgeB = new double[3];

        void clear() {
            lashOn = orbOn = flashOn = forgeOn = false;
            halo = 0;
            for (int b = 0; b < BONES.length; b++) {
                Arrays.fill(rot[b], 0.0);
                Arrays.fill(off[b], 0.0);
            }
            Arrays.fill(slotOn, false);
            Arrays.fill(flyState, FLY_NONE);
            Arrays.fill(weaponHidden, false);
            lanternCoreHidden = false;
            alpha = 1.0;
            lantern = 1.0;
            ribbons = 0;
        }

        void ribbon(double[] hub, double[] ctrl, double[] end, double width) {
            System.arraycopy(hub, 0, ribbonHub[ribbons], 0, 3);
            System.arraycopy(ctrl, 0, ribbonCtrl[ribbons], 0, 3);
            System.arraycopy(end, 0, ribbonEnd[ribbons], 0, 3);
            ribbonWidth[ribbons] = width;
            ribbons++;
        }
    }

    // ------------------------------------------------------------------ small maths ------------------------------

    /** The seeded random source (the driver's lcg): advances the state's seed, returns 0..1. */
    static double lcg(State st) {
        st.seed = (st.seed * 1103515245L + 12345L) & 0x7FFFFFFFL;
        return st.seed / (double) 0x7FFFFFFF;
    }

    /** Python's float %: the result takes the divisor's sign. */
    static double pmod(double a, double m) {
        double r = a % m;
        if (r != 0.0 && (r < 0.0) != (m < 0.0))
            r += m;
        return r;
    }

    /** One tick of a spring [th[i], om[i]] toward target (the driver's spring()). */
    static void spring(double[] th, double[] om, int i, double target, double k, double c, double drive,
                       double lo, double hi) {
        double t = th[i], o = om[i];
        o = o + (-k * (t - target) - c * o + drive);
        t = t + o;
        if (t < lo) {
            t = lo;
            o = 0.0;
        }
        if (t > hi) {
            t = hi;
            o = 0.0;
        }
        th[i] = t;
        om[i] = o;
    }

    /** rot_mat: Minecraft rotation (degrees), x first, then y, then z: Rz * Ry * Rx, row-major. */
    static void rotMat(double rx, double ry, double rz, double[] m) {
        double cx = Math.cos(Math.toRadians(rx)), sx = Math.sin(Math.toRadians(rx));
        double cy = Math.cos(Math.toRadians(ry)), sy = Math.sin(Math.toRadians(ry));
        double cz = Math.cos(Math.toRadians(rz)), sz = Math.sin(Math.toRadians(rz));
        m[0] = cz * cy;
        m[1] = cz * sy * sx - sz * cx;
        m[2] = cz * sy * cx + sz * sx;
        m[3] = sz * cy;
        m[4] = sz * sy * sx + cz * cx;
        m[5] = sz * sy * cx - cz * sx;
        m[6] = -sy;
        m[7] = cy * sx;
        m[8] = cy * cx;
    }

    /** out = a * b (out must not be a or b). */
    static void matMul(double[] a, double[] b, double[] out) {
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                out[3 * i + j] = a[3 * i] * b[j] + a[3 * i + 1] * b[3 + j] + a[3 * i + 2] * b[6 + j];
    }

    /** euler(): the DSL rotation (degrees, x then y then z) of a rotation matrix (rot_mat's inverse). */
    static void euler(double[] m, double[] out) {
        double ry = Math.asin(Math.max(-1.0, Math.min(1.0, -m[6])));
        double rx = Math.atan2(m[7], m[8]);
        double rz = Math.atan2(m[3], m[0]);
        out[0] = Math.toDegrees(rx);
        out[1] = Math.toDegrees(ry);
        out[2] = Math.toDegrees(rz);
    }

    static void qFromAxis(double ax, double ay, double az, double deg, double[] q) {
        double n = Math.sqrt(ax * ax + ay * ay + az * az);
        if (n == 0.0)
            n = 1.0;
        double h = Math.toRadians(deg) / 2;
        double s = Math.sin(h);
        q[0] = Math.cos(h);
        q[1] = ax / n * s;
        q[2] = ay / n * s;
        q[3] = az / n * s;
    }

    /** out = a * b (out may alias a or b). */
    static void qMul(double[] a, double[] b, double[] out) {
        double w1 = a[0], x1 = a[1], y1 = a[2], z1 = a[3], w2 = b[0], x2 = b[1], y2 = b[2], z2 = b[3];
        out[0] = w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2;
        out[1] = w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2;
        out[2] = w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2;
        out[3] = w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2;
    }

    static void qFromMat(double[] r, double[] q) {
        double tr = r[0] + r[4] + r[8];
        if (tr > 0) {
            double s = Math.sqrt(tr + 1.0) * 2;
            q[0] = 0.25 * s;
            q[1] = (r[7] - r[5]) / s;
            q[2] = (r[2] - r[6]) / s;
            q[3] = (r[3] - r[1]) / s;
            return;
        }
        int i = 0;                                          // max(range(3), key=diagonal): the first largest
        if (r[4] > r[0])
            i = 1;
        if (r[8] > r[4 * i])
            i = 2;
        int j = (i + 1) % 3, k = (i + 2) % 3;
        double s = Math.sqrt(1.0 + r[4 * i] - r[4 * j] - r[4 * k]) * 2;
        q[0] = (r[3 * k + j] - r[3 * j + k]) / s;
        q[1 + i] = 0.25 * s;
        q[1 + j] = (r[3 * j + i] + r[3 * i + j]) / s;
        q[1 + k] = (r[3 * k + i] + r[3 * i + k]) / s;
    }

    static void qToMat(double[] q, double[] m) {
        double w = q[0], x = q[1], y = q[2], z = q[3];
        m[0] = 1 - 2 * (y * y + z * z);
        m[1] = 2 * (x * y - w * z);
        m[2] = 2 * (x * z + w * y);
        m[3] = 2 * (x * y + w * z);
        m[4] = 1 - 2 * (x * x + z * z);
        m[5] = 2 * (y * z - w * x);
        m[6] = 2 * (x * z - w * y);
        m[7] = 2 * (y * z + w * x);
        m[8] = 1 - 2 * (x * x + y * y);
    }

    /** out = slerp(a, b, u), normalised (out may alias a; b is not modified). */
    static void qSlerp(double[] a, double[] b, double u, double[] out) {
        double d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        double sign = 1.0;
        if (d < 0) {
            sign = -1.0;
            d = -d;
        }
        double w0, w1;
        if (d > 0.9995) {
            w0 = 1 - u;
            w1 = u;
        } else {
            double th = Math.acos(d);
            w0 = Math.sin((1 - u) * th) / Math.sin(th);
            w1 = Math.sin(u * th) / Math.sin(th);
        }
        double n2 = 0.0;
        for (int i = 0; i < 4; i++) {
            double bi = sign * b[i];
            out[i] = d > 0.9995 ? a[i] + (bi - a[i]) * u : a[i] * w0 + bi * w1;
            n2 += out[i] * out[i];
        }
        double n = Math.sqrt(n2);
        for (int i = 0; i < 4; i++)
            out[i] /= n;
    }

    /** frame_along: the world rotation whose local -z points along T, local y as near world up as it can be. */
    static void frameAlong(double[] t, double[] m) {
        double n = Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
        if (n == 0.0)
            n = 1.0;
        double ez0 = -t[0] / n, ez1 = -t[1] / n, ez2 = -t[2] / n;
        double ex0 = ez2, ex1 = 0.0, ex2 = -ez0;            // up (0, 1, 0) x ez
        double nx = Math.sqrt(ex0 * ex0 + ex1 * ex1 + ex2 * ex2);
        if (nx < 1e-6) {
            ex0 = 1.0;
            ex1 = 0.0;
            ex2 = 0.0;
        } else {
            ex0 /= nx;
            ex1 /= nx;
            ex2 /= nx;
        }
        double ey0 = ez1 * ex2 - ez2 * ex1, ey1 = ez2 * ex0 - ez0 * ex2, ey2 = ez0 * ex1 - ez1 * ex0;
        m[0] = ex0;
        m[1] = ey0;
        m[2] = ez0;
        m[3] = ex1;
        m[4] = ey1;
        m[5] = ez1;
        m[6] = ex2;
        m[7] = ey2;
        m[8] = ez2;
    }

    static void bezier(double[] p0, double[] p1, double[] p2, double[] p3, double u, double[] out) {
        double a = (1 - u) * (1 - u) * (1 - u), b = 3 * (1 - u) * (1 - u) * u, c = 3 * (1 - u) * u * u,
            d = u * u * u;
        for (int i = 0; i < 3; i++)
            out[i] = a * p0[i] + b * p1[i] + c * p2[i] + d * p3[i];
    }

    static void bezierD(double[] p0, double[] p1, double[] p2, double[] p3, double u, double[] out) {
        double a = 3 * (1 - u) * (1 - u), b = 6 * (1 - u) * u, c = 3 * u * u;
        for (int i = 0; i < 3; i++)
            out[i] = a * (p1[i] - p0[i]) + b * (p2[i] - p1[i]) + c * (p3[i] - p2[i]);
    }

    // ------------------------------------------------------------------ geometry --------------------------------

    static void newSlot(State st, int k, double angle, int mode) {
        st.angle[k] = angle;
        st.angV[k] = 0.0;
        st.radTh[k] = st.radOm[k] = 0.0;
        st.rollTh[k] = st.rollOm[k] = 0.0;
        st.kickTh[k] = st.kickOm[k] = 0.0;
        st.mode[k] = mode;
        st.since[k] = 0;
        st.hasForge[k] = false;
        st.forge[k] = 1.0;
        st.orderOf[k] = -1;
        st.fallOn[k] = false;
        st.hasPEnd[k] = false;
    }

    /** hub_toward: where a soul chain leaves the ribs - HUB_R px from the hub toward its end (horizontal). */
    static void hubToward(State st, double[] end, double[] out) {
        double[] h = st.hub;
        double dx = end[0] - h[0], dz = end[2] - h[2];
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d == 0.0)
            d = 1.0;
        out[0] = h[0] + HUB_R * dx / d;
        out[1] = h[1];
        out[2] = h[2] + HUB_R * dz / d;
    }

    /** sag_ctrl: the quadratic ribbon's control point for a chain sagging {@code sag} px between a and b. */
    static void sagCtrl(double[] a, double[] b, double sag, double[] out) {
        out[0] = (a[0] + b[0]) / 2;
        out[1] = (a[1] + b[1]) / 2 - 2 * sag;
        out[2] = (a[2] + b[2]) / 2;
    }

    /** slot_world: a slot's pommel in model space from this tick's ring transform; the angle includes the spin. */
    static void slotWorld(State st, double angle, double radial, double[] out) {
        double a = Math.toRadians(angle), r = RING_R + radial;
        double px = -Math.sin(a) * r, py = 0.0, pz = -Math.cos(a) * r;
        double[] m = st.ringM;
        for (int i = 0; i < 3; i++)
            out[i] = st.ringO[i] + (m[3 * i] * px + m[3 * i + 1] * py + m[3 * i + 2] * pz);
    }

    /** slot_rot_world: ring * yaw(angle) * pitch, into out (uses st.ma / st.mb / st.mc: out must be another). */
    static void slotRotWorld(State st, double angle, double pitch, double[] out) {
        rotMat(0, angle, 0, st.ma);
        rotMat(pitch, 0, 0, st.mb);
        matMul(st.ma, st.mb, st.mc);
        matMul(st.ringM, st.mc, out);
    }

    private static void rot(Pose o, int b, double x, double y, double z) {
        o.rot[b][0] += x;
        o.rot[b][1] += y;
        o.rot[b][2] += z;
    }

    private static void off(Pose o, int b, double x, double y, double z) {
        o.off[b][0] += x;
        o.off[b][1] += y;
        o.off[b][2] += z;
    }

    // ------------------------------------------------------------------ the body per clip -----------------------

    /** body_targets: direct (eased) bone deltas into the pose + this tick's channels into the state. */
    static void bodyTargets(State st, Clip clip, int t, Inputs in, Pose o) {
        int n = st.n;
        Keys K = st.keys;
        st.chTatter = -0.0;
        st.chCape = 0.0;
        st.chTilt0 = st.chTilt1 = 0.0;
        Arrays.fill(st.chRingOff, 0.0);
        st.chRingSpin = 0.6;
        st.chCock = 0.0;
        st.chLantern = 1.0;
        st.chAlpha = 1.0;
        st.chLeanTarget = 0.0;
        st.chSpeed = 0.0;
        st.chRattle = st.chTrail = st.chLeanKick = st.chTiltKick = st.chPitchKick = st.chTatterKick = 0.0;
        st.chTatterHold = false;
        // ---- the idle layer (always on, faded by the clips that own the body)
        double idleW = clip == Clip.DEATH ? 0.0 : 1.0;
        if (clip == Clip.VOLLEY)
            idleW = 1.0 - K.of(0, 0.0).k(4, 1.0).k(st.fEnd, 1.0).k(st.fEnd + 6, 0.0).at(t);
        if (clip == Clip.DEATH)
            idleW = 1.0 - K.of(0, 0.0).k(3, 1.0).at(t);
        double bob = st.bob, br = st.breath, look = st.look;
        double amp = 2.0 - 0.5 * st.speed;
        off(o, HOVER, 0, amp * Math.sin(bob) * idleW, 0);
        rot(o, BODY, 2 * Math.sin(br + 0.5) * idleW, 0, 0);
        rot(o, CHEST, 3 * Math.sin(br) * idleW, 0, 0);
        off(o, MANTLE, 0, 0.5 * Math.max(0.0, Math.sin(br)) * idleW, 0);
        rot(o, HEAD, 0, 10 * Math.sin(look) * idleW, 0);
        // the conductor's weave (r4: x12 / z6 / forearm 10)
        rot(o, R_ARM, WEAVE_X * Math.sin(bob + Math.PI / 2) * idleW, 0, WEAVE_Z * Math.sin(bob) * idleW);
        rot(o, L_ARM, WEAVE_X * Math.sin(bob + 3 * Math.PI / 2) * idleW, 0, WEAVE_Z * Math.sin(bob) * idleW);
        rot(o, R_FORE, WEAVE_FORE * Math.sin(bob) * idleW, 0, 0);
        rot(o, L_FORE, -WEAVE_FORE * Math.sin(bob) * idleW, 0, 0);
        double curl = (10 + 10 * Math.sin(2 * Math.PI * st.tick / 40.0)) * idleW;
        rot(o, R_HAND, curl, 0, 0);
        rot(o, L_HAND, curl, 0, 0);
        for (int i = 0; i < 4; i++)
            st.chTatterAdd[i] = 4 * Math.sin(2 * Math.PI * st.tick / 30.0 + i * Math.PI / 2) * idleW;

        if (clip == Clip.FIDGET) {     // "counting": the left hand lifts, the wheel spins one fast turn, a rattle
            rot(o, L_ARM, K.of(0, 0).k(6, 45).k(9, 45).k(15, 0).at(t), 0, 0);
            st.chRingSpin = K.of(0, 0.6).k(8, 12.0).k(20, 0.6).at(t);
            st.chRattle = t == 8 ? 15.0 : 0.0;
        }

        if (clip == Clip.MOVE) {
            double v = in.speed;
            st.chSpeed = v;
            st.chLeanTarget = -10.0 * v;
            off(o, HOVER, 0, -2.0 * v, 0);
            rot(o, HEAD, 10 * v, 0, 0);
            for (int side = 0; side < 2; side++) {
                rot(o, side == 0 ? R_ARM : L_ARM, -40 * v, 0, 0);
                rot(o, side == 0 ? R_FORE : L_FORE, -25 * v, 0, 0);
                rot(o, side == 0 ? R_HAND : L_HAND, -15 * v, 0, 0);
            }
            st.chTatter = -35.0 * v + 6 * Math.sin(2 * Math.PI * t / 12.0) * v;
            st.chCape = -30.0 * v;
            st.chTilt0 = -8.0 * v;
            st.chTilt1 = 0.0;
            st.chRingSpin = 0.6 + 0.6 * v;
            st.chTrail = 10.0 * v;
        }

        if (clip == Clip.VOLLEY) {
            // r4: t0-8 turn to the target, t8-10 CROUCH, t10-12 the arm SNAPS up, t12-15 HOLD, t16 STRIKE; the body
            // keeps facing the target afterwards
            int fe = st.fEnd, last = st.lastRelease;
            int hold = Math.max(last + 2, 18);
            double b = st.bearing;
            double sb = Math.sin(Math.toRadians(b)), cb = Math.cos(Math.toRadians(b));   // "back" = (sb, 0, cb)
            double hy = K.of(0, 0).k(8, 0).k(10, -2).k(12, 3).k(15, 3).k(16, -1).k(fe, 0).at(t);
            double hb = K.of(0, 0).k(8, 0).k(10, 1).k(12, 3).k(15, 3).k(16, -4).k(fe, 0).at(t);
            off(o, HOVER, hb * sb, hy, hb * cb);
            double by = K.of(0, 0).k(8, b).k(10, b + 8).k(12, b + 25).k(15, b + 25).k(16, b - 15).k(fe, b).at(t);
            rot(o, BODY, K.of(0, 0).k(8, 0).k(10, -6).k(12, 14).k(15, 14).k(16, -11).k(fe, 0).at(t), by, 0);
            rot(o, HEAD, K.of(0, 0).k(10, 0).k(12, -20).k(15, -20).k(16, 0).k(fe + 2, 0).at(t), b - by, 0);
            rot(o, R_ARM, K.of(0, 0).k(10, 0).k(12, 180).k(15, 180).k(16, 40).k(hold, 40).k(fe, 0).at(t), 0,
                K.of(0, 0).k(10, 0).k(12, 42).k(15, 42).k(16, 0).at(t));
            double fx = K.of(0, 0).k(10, -10).k(12, 25).k(15, 25).k(16, -25).k(fe, 0).at(t);
            double fz = 0.0;
            if (n >= 3 && 16 < t && t <= last + 1) {        // one crack of the whip per release
                double ph = 2 * Math.PI * (t - VOLLEY_RELEASE) / (double) RELEASE_GAP;
                fx = 20 * Math.sin(ph);
                fz = 15 * Math.sin(ph);
            }
            rot(o, R_FORE, fx, 0, fz);
            rot(o, R_HAND, K.of(0, 0).k(10, 0).k(12, 20).k(15, 20).k(16, 0).at(t), 0, 0);
            // the aim hand: abs x 75, under the ring's front edge when it dips at the strike; no yaw
            rot(o, L_ARM, K.of(0, 0).k(8, 30).k(12, 55).k(hold, 55).k(fe, 0).at(t), 0, 0);
            rot(o, L_FORE, K.of(0, 0).k(12, -30).k(hold, -30).k(fe, 0).at(t), 0, 0);
            off(o, MANTLE, 0, K.of(0, 0).k(10, 0).k(12, 1).k(15, 1).k(16, 0).at(t), 0);
            st.chTatter = K.of(0, 0).k(10, 4).k(12, -8).k(15, -8).k(16, -15).k(fe, 0).at(t);
            st.chCape = K.of(0, 0).k(10, 2).k(12, -6).k(15, -6).k(16, -12).k(fe, 0).at(t);
            double tl = K.of(0, 0).k(8, 0).k(10, 12).k(15, 12).k(16, -18).k(fe, 0).at(t);
            st.chTilt0 = tl * cb;                                          // dips toward the target's bearing
            st.chTilt1 = -tl * sb;
            double ro = K.of(0, 0).k(8, 0).k(10, 4).k(15, 4).k(16, -5).k(fe, 0).at(t);
            st.chRingOff[0] = ro * sb;
            st.chRingOff[1] = K.of(0, 0).k(8, 0).k(10, 1).k(15, 1).k(16, -2).k(fe, 0).at(t);
            st.chRingOff[2] = ro * cb;
            st.chRingSpin = K.of(0, 0.6).k(12, 6.0).k(last, 6.0).k(last + 15, 0.6).at(t);
            st.chCock = K.of(0, 0).k(10, 25).k(15, 25).k(16, 10).k(fe, 0).at(t);
            int released = 0;
            for (int k = 1; k <= MAX_WEAPONS; k++)
                if (st.orderOf[k] >= 0 && VOLLEY_RELEASE + RELEASE_GAP * st.orderOf[k] <= t)
                    released++;
            st.chLantern = K.of(0, 1.0).k(12, 1.6).k(last, 1.6).k(fe, 1.0).at(t);
            if (t <= last)
                st.chLantern = Math.max(1.0, st.chLantern - 0.1 * released);     // -0.1 per release
        }

        if (clip == Clip.BIND) {       // the corpse at the front-right (inputs.target); n -> n + 1
            rot(o, HEAD, K.of(0, 0).k(6, 0).k(15, 16).k(18, 16).k(31, 0).at(t),
                K.of(0, 0).k(3, 25).k(15, 12).k(18, 12).k(31, 0).at(t), 0);
            rot(o, BODY, K.of(0, 0).k(6, 0).k(15, 8).k(18, 8).k(31, 0).at(t),
                K.of(0, 0).k(3, 10).k(15, 25).k(18, 25).k(31, 0).at(t), 0);
            rot(o, CHEST, K.of(0, 0).k(15, 4).k(18, 4).k(31, 0).at(t), 0, 0);
            rot(o, L_ARM, K.of(0, 0).k(6, 0).k(15, -60).k(18, -60).k(31, 0).at(t), 0,
                K.of(0, 0).k(6, 0).k(15, 24).k(18, 24).k(31, 0).at(t));
            rot(o, L_FORE, K.of(0, 0).k(15, -15).k(18, -15).k(31, 0).at(t), 0, 0);
            off(o, MANTLE, 0, K.of(0, 0).k(15, 1).k(18, 1).k(31, 0).at(t), 0);
            off(o, HOVER, 0, K.of(0, 0).k(15, 2).k(16, 2).k(17, 5).k(19, 2).k(31, 0).at(t), 0);
            st.chTilt0 = 0.0;
            st.chTilt1 = K.of(0, 0).k(6, 0).k(15, 10).k(18, 10).k(31, 0).at(t);
            st.chRingOff[0] = K.of(0, 0).k(15, -2).k(18, -2).k(31, 0).at(t);
            st.chRingOff[1] = 0.0;
            st.chRingOff[2] = K.of(0, 0).k(15, -2).k(18, -2).k(31, 0).at(t);
            st.chLantern = K.of(0, 1.8).k(16, 2.0).k(17, 2.0).k(31, 1.0).at(t);
        }

        if (clip == Clip.HIT) {        // sheet 5.5
            double on = t >= 0 ? 1.0 : 0.0;
            off(o, HOVER, 0, K.of(0, -3).k(2, -3).k(8, 0).at(t) * on, 0);
            rot(o, HEAD, K.of(0, 15).k(2, 15).k(8, 0).at(t), 0, 0);
            st.chLeanKick = t == 0 ? 12.0 : 0.0;
            st.chTiltKick = t == 0 ? 18.0 : 0.0;
            st.chRattle = t == 0 ? 15.0 : 0.0;
            st.chPitchKick = t == 0 ? 30.0 : 0.0;
            st.chTatterKick = t == 0 ? -6.0 : 0.0;
        }

        if (clip == Clip.DEATH) {      // sheet 5.6; r4: a landing squash held t16-19 before the crumple (t19-33)
            double fall = 0.0;
            if (6 <= t && t <= 16) {
                double u = (t - 6) / 10.0;
                fall = -DEATH_DROP * u * u;                                     // gravity ease-in
            } else if (t > 16) {
                fall = -DEATH_DROP + K.of(16, -1.5).k(19, -1.5).k(21, 0.0).at(t);
            }
            off(o, HOVER, 0, fall, 0);
            rot(o, HEAD, K.of(0, 0).k(3, 35).k(6, 35).k(16, 35).k(19, 28).k(33, -36).at(t), 0, 0);
            rot(o, BODY, K.of(0, 0).k(3, 8).k(6, 8).k(16, 8).k(17, -8).k(19, -8).k(33, -26).at(t), 0, 0);
            rot(o, CHEST, K.of(0, 0).k(3, 6).k(6, 6).k(16, 6).k(17, -4).k(19, -4).k(33, -40).at(t), 0, 0);
            // the arms fall limp and IN, along the body
            rot(o, R_ARM, K.of(0, 0).k(6, 0).k(19, 0).k(33, 20).at(t), 0,
                K.of(0, 0).k(3, -58).k(6, -58).k(16, -58).k(19, -40).k(33, 10).at(t));
            rot(o, L_ARM, K.of(0, 0).k(6, 0).k(19, 0).k(33, 20).at(t), 0,
                K.of(0, 0).k(3, 58).k(6, 58).k(16, 58).k(19, 40).k(33, -10).at(t));
            for (int side = 0; side < 2; side++)
                rot(o, side == 0 ? R_FORE : L_FORE, K.of(0, 0).k(3, -25).k(19, -25).k(33, -35).at(t), 0, 0);
            double fold = K.of(0, 0.0).k(19, 0.0).k(33, 1.0).at(t);
            double splay = K.of(0, 0.0).k(16, 0.0).k(17, 1.0).k(19, 1.0).k(21, 0.0).at(t);   // the squash
            rot(o, TATTER_F, 80 * fold + 15 * splay, 0, 0);
            rot(o, TATTER_F + 1, -80 * fold - 15 * splay, 0, 0);
            rot(o, TATTER_F + 2, 0, 0, 80 * fold + 15 * splay);
            rot(o, TATTER_F + 3, 0, 0, -80 * fold - 15 * splay);
            st.chTatterHold = t >= 16;
            st.chLantern = K.of(0, 1.0).k(1, 2.5).k(6, 2.5).k(8, 0.3).k(30, 0.3).k(40, 0.0).at(t);
            st.chAlpha = K.of(0, 1.0).k(35, 1.0).k(50, 0.0).at(t);
            st.chRingSpin = 0.6;
        }
    }

    // ------------------------------------------------------------------ the step --------------------------------

    /** start_clip: clip-entry bookkeeping (t == 0): the volley's release order, the bind's new slot, the death. */
    static void startClip(State st, Clip clip, Inputs in) {
        int n = st.n;
        if (clip == Clip.VOLLEY) {
            double[] tgt = in.target;
            double bearing = Math.toDegrees(Math.atan2(-tgt[0], -tgt[2]));   // the slot-yaw convention
            for (int k = 1; k <= n; k++)                                      // ~ the spin at t16
                st.diff[k] = pmod(st.angle[k] + st.ringYaw + 12 * 4.0 - bearing + 180, 360) - 180;
            int first = 1;
            for (int k = 2; k <= n; k++)
                if (Math.abs(st.diff[k]) < Math.abs(st.diff[first]))
                    first = k;
            int nl = 0, nr = 0;
            for (int k = 1; k <= n; k++) {                  // stable insertion: left ascending, right descending
                if (st.diff[k] > st.diff[first]) {
                    int i = nl++;
                    while (i > 0 && st.diff[st.left[i - 1]] > st.diff[k]) {
                        st.left[i] = st.left[i - 1];
                        i--;
                    }
                    st.left[i] = k;
                } else if (st.diff[k] < st.diff[first]) {
                    int i = nr++;
                    while (i > 0 && st.diff[st.right[i - 1]] < st.diff[k]) {
                        st.right[i] = st.right[i - 1];
                        i--;
                    }
                    st.right[i] = k;
                }
            }
            Arrays.fill(st.orderOf, -1);
            int pos = 0;
            st.orderOf[first] = pos++;
            int li = 0, ri = 0, i = 0;
            while (li < nl || ri < nr) {
                boolean fromLeft = (i % 2 == 0 && li < nl) || ri >= nr;
                st.orderOf[fromLeft ? st.left[li++] : st.right[ri++]] = pos++;
                i++;
            }
            st.lastRelease = VOLLEY_RELEASE + RELEASE_GAP * (pos - 1);
            st.fEnd = st.lastRelease + 2 + 10;
            System.arraycopy(tgt, 0, st.target, 0, 3);
            st.bearing = bearing;
        }
        if (clip == Clip.BIND && n < MAX_WEAPONS) {
            int k = n + 1;
            newSlot(st, k, BASE_YAW + 360.0 / k * (k - 1), M_FORGING);
            st.n = k;
        }
        if (clip == Clip.DEATH)
            Arrays.fill(st.fallOn, false);
    }

    /**
     * One tick (20 tps): advances the state in place and writes this tick's pose into {@code out}. The driver's
     * step(), line for line.
     */
    public static void step(State st, Inputs in, Pose out) {
        Clip clip = in.clip;
        int t = in.t;
        if (t == 0)
            startClip(st, clip, in);
        int n = st.n;
        double v = clip == Clip.MOVE ? in.speed : 0.0;
        // ---- phases (kept as state; the move shortens the bob to 40 ticks)
        st.speed = v;
        st.bob = pmod(st.bob + 2 * Math.PI / (60.0 - 20.0 * v), 2 * Math.PI);
        st.breath = pmod(st.breath + 2 * Math.PI / 80.0, 2 * Math.PI);
        st.look = pmod(st.look + 2 * Math.PI / 160.0, 2 * Math.PI);
        lcg(st);
        out.clear();
        bodyTargets(st, clip, t, in, out);
        // ---- the root: velocity / acceleration in model space (drives the springs)
        double[] hov = st.hov, rootV = st.rootV, acc = st.acc;
        System.arraycopy(out.off[HOVER], 0, hov, 0, 3);
        rootV[0] = 0.0;
        rootV[1] = 0.0;
        rootV[2] = -v * 4.0;                                             // 0.25 blocks/tick = 4 px/tick forward
        double[][] prev = st.root;
        for (int i = 0; i < 3; i++)
            acc[i] = (hov[i] - 2 * prev[0][i] + prev[1][i]) + (rootV[i] - prev[2][i]);
        System.arraycopy(prev[0], 0, prev[1], 0, 3);
        System.arraycopy(hov, 0, prev[0], 0, 3);
        System.arraycopy(rootV, 0, prev[2], 0, 3);
        double driveX = DRIVE * acc[2];          // accelerating toward -z swings hanging parts back (-x)
        double driveZ = -DRIVE * acc[0];
        double[] th = st.th, om = st.om;
        // ---- the body's lean (move: starts late, overshoots on the stop; hit: kicked)
        if (st.chLeanKick != 0.0) {
            th[S_BODY_LEAN] = st.chLeanKick;
            om[S_BODY_LEAN] = 0.0;
        }
        spring(th, om, S_BODY_LEAN, st.chLeanTarget, K_LEAN, C_LEAN, 0.0, NO_LO, NO_HI);
        out.rot[BODY][0] += th[S_BODY_LEAN];
        double bodyX = out.rot[BODY][0];
        double lean = bodyX + out.rot[CHEST][0];
        // ---- cloth and chains
        for (int i = 0; i < 4; i++) {
            if (st.chTatterHold)
                continue;
            int s = S_TATTER + i;
            if (st.chTatterKick != 0.0)
                om[s] += st.chTatterKick;
            double tgt = st.chTatter + st.chTatterAdd[i] - 0.5 * bodyX;
            spring(th, om, s, tgt, K_CHAIN, C_CHAIN, driveX, TATTER_LO, TATTER_HI);
            out.rot[TATTER_F + i][0] += th[s];
        }
        spring(th, om, S_CAPE, st.chCape - 0.5 * bodyX, K_CHAIN, C_CHAIN, driveX, CAPE_LO, CAPE_HI);
        out.rot[CAPE][0] += th[S_CAPE];
        for (int k = 1; k <= 3; k++) {
            int a = S_HEM_1 + (k - 1), bb = S_HEM_1_B + (k - 1);
            double lo = k < 3 ? HEM_FRONT_LO : HEM_BACK_LO, hi = k < 3 ? HEM_FRONT_HI : HEM_BACK_HI;
            double trail = k == 3 ? -st.chTrail * 3 : 0.0;
            spring(th, om, a, -bodyX + trail, K_CHAIN, C_CHAIN, driveX, lo, hi);
            // the second segment follows the first, pushed back by the first's swing: the tip lags ~3 ticks
            spring(th, om, bb, 0.0, K_CHAIN_B, C_CHAIN, driveX - 0.5 * om[a], HEM_B_LO, HEM_B_HI);
            out.rot[HEM_1 + 2 * (k - 1)][0] += th[a];
            out.rot[HEM_1 + 2 * (k - 1) + 1][0] += th[bb];
        }
        spring(th, om, S_LANTERN_CHAIN, -lean, K_CHAIN, C_CHAIN, driveX, LANTERN_LO, LANTERN_HI);
        out.rot[LANTERN_CHAIN][0] += th[S_LANTERN_CHAIN];
        for (int side = 0; side < 2; side++) {
            double[] arm = out.rot[side == 0 ? R_ARM : L_ARM], fore = out.rot[side == 0 ? R_FORE : L_FORE];
            double tx = -(lean + arm[0] + fore[0]);
            double tz = -arm[2];
            int sx = side == 0 ? S_R_SHACKLE_X : S_L_SHACKLE_X, sz = side == 0 ? S_R_SHACKLE_Z : S_L_SHACKLE_Z;
            spring(th, om, sx, tx, K_CHAIN, C_CHAIN, driveX, SHACKLE_LO, SHACKLE_HI);
            spring(th, om, sz, tz, K_CHAIN, C_CHAIN, driveZ, SHACKLE_LO, SHACKLE_HI);
            double[] sh = out.rot[side == 0 ? R_SHACKLE : L_SHACKLE];
            sh[0] = th[sx];
            sh[1] = 0.0;
            sh[2] = th[sz];
        }
        // ---- the wheel
        st.ringYawV = st.chRingSpin;
        st.ringYaw = pmod(st.ringYaw + st.ringYawV, 360.0);
        // the tilt: the clip's keyed tilt + a spring perturbation (kicked by hits, driven by the root's acceleration)
        if (st.chTiltKick != 0.0)
            om[S_TILT_X] += st.chTiltKick * 0.6;
        spring(th, om, S_TILT_X, 0.0, K_TILT, C_TILT, -0.5 * driveX, NO_LO, NO_HI);
        spring(th, om, S_TILT_Z, 0.0, K_TILT, C_TILT, -0.5 * driveZ, NO_LO, NO_HI);
        double tilt0 = st.chTilt0 + th[S_TILT_X], tilt1 = st.chTilt1 + th[S_TILT_Z];
        out.rot[RING][0] = tilt0;                     // r4: the ring carries the tilt only; spin in the slots
        out.rot[RING][1] = 0.0;
        out.rot[RING][2] = tilt1;
        System.arraycopy(st.chRingOff, 0, out.off[RING], 0, 3);
        rotMat(RING_TILT + tilt0, 0.0, tilt1, st.ringM);
        for (int i = 0; i < 3; i++) {
            st.ringO[i] = RING_PIVOT[i] + (hov[i] + st.chRingOff[i]);
            st.hub[i] = HUB[i] + hov[i];
        }
        // ---- every slot
        for (int k = 1; k <= n; k++) {
            if (clip == Clip.BIND) {
                // re-space: every angle springs to the new even spacing (about one overshoot)
                double target = BASE_YAW + 360.0 / n * (k - 1);
                st.angV[k] += -K_SPACE * (st.angle[k] - target) - C_SPACE * st.angV[k];
                st.angle[k] += st.angV[k];
                if (t == 18)
                    st.radOm[k] += 1.0;                                  // the wheel pulses out 2 px and back
            }
            double alt = k % 2 != 0 ? 1 : -1;
            if (st.chRattle != 0.0)
                st.rollOm[k] += st.chRattle * alt * 0.5;
            if (st.chPitchKick != 0.0)
                st.kickOm[k] += st.chPitchKick * alt * 0.5;
            spring(st.radTh, st.radOm, k, 0.0, K_RADIAL, C_RADIAL, 0.0, NO_LO, NO_HI);
            spring(st.rollTh, st.rollOm, k, 0.0, K_ROLL, C_ROLL, 0.0, NO_LO, NO_HI);
            spring(st.kickTh, st.kickOm, k, 0.0, K_ROLL, C_ROLL, 0.0, NO_LO, NO_HI);
            double bobK = Math.sin(2 * Math.PI * st.tick / 40.0 + Math.toRadians(36 * k));
            double pitch = st.chCock + st.kickTh[k] + 3 * bobK;
            double roll = st.rollTh[k] + 8 * bobK;
            double radial = st.radTh[k] + bobK;
            int mode = st.mode[k];
            if (clip == Clip.VOLLEY && st.orderOf[k] >= 0)
                mode = weaponFlight(st, k, t, out);
            if (clip == Clip.DEATH && t >= 6 + 2 * (k - 1))
                mode = weaponFall(st, k, t, out);
            if (mode == M_FORGING) {
                if (t < 18) {
                    out.weaponHidden[k] = true;
                } else {
                    double g = smooth((t - 18) / 6.0);
                    st.forge[k] = 0.4 + 0.6 * g;                            // sheet 5.4: outline 0.4 -> 1
                    st.hasForge[k] = true;
                    if (t >= 24) {
                        st.mode[k] = M_DOCKED;
                        mode = M_DOCKED;
                    }
                }
            }
            if ((mode == M_DOCKED || mode == M_FORGING) && !out.weaponHidden[k]) {
                out.slotOn[k] = true;
                out.slotAngle[k] = st.angle[k] + st.ringYaw;
                out.slotRadial[k] = radial;
                out.slotY[k] = 0.0;
                out.slotPitch[k] = pitch;
                out.slotRoll[k] = roll;
                out.slotScale[k] = mode == M_FORGING ? (st.hasForge[k] ? st.forge[k] : 1.0) : 1.0;
                if (clip != Clip.DEATH || t < 6) {
                    double[] end = st.va, hub = st.vi, ctrl = st.vj;
                    slotWorld(st, st.angle[k] + st.ringYaw, radial, end);
                    hubToward(st, end, hub);
                    sagCtrl(hub, end, 2.0, ctrl);
                    out.ribbon(hub, ctrl, end, 1.2);
                }
            }
        }
        st.tick += 1;
        if (clip == Clip.BIND)
            bindFx(st, t, in, out);
        if (clip != Clip.DEATH || t < 6) {
            int lit = 0;
            for (int k = 1; k <= MAX_WEAPONS; k++)
                if (out.slotOn[k] || (out.flyState[k] != FLY_NONE && out.flyState[k] != FLY_SHATTER))
                    lit++;
            out.halo = lit;
        } else {
            out.halo = Math.max(0, n - Math.max(0, (t - 6) / 2 + 1));
        }
        out.alpha = st.chAlpha;
        out.lantern = st.chLantern;
        if (st.chLantern < 0.5)
            out.lanternCoreHidden = true;
    }

    /**
     * bind_fx (sheet 5.4): the lash from the lantern to the corpse (t0-3 growing, held to t15), the soul orb lifted
     * (t4-5) and dragged along it to the lantern (t6-15), the flash (t16-17), the forge line to the new slot (t18-23).
     */
    static void bindFx(State st, int t, Inputs in, Pose out) {
        double[] h = st.hov, c = in.target;
        double l0 = LANTERN[0] + h[0], l1 = LANTERN[1] + h[1], l2 = LANTERN[2] + h[2];
        if (t <= 15) {
            double u = smooth((t + 1) / 3.0);
            out.lashOn = true;
            out.lashA[0] = l0;
            out.lashA[1] = l1;
            out.lashA[2] = l2;
            out.lashB[0] = l0 + (c[0] - l0) * u;
            out.lashB[1] = l1 + (c[1] - l1) * u;
            out.lashB[2] = l2 + (c[2] - l2) * u;
        }
        if (4 <= t && t <= 5) {
            out.orbOn = true;
            out.orb[0] = c[0];
            out.orb[1] = c[1] + 16.0 * smooth((t - 3) / 2.0);
            out.orb[2] = c[2];
        } else if (6 <= t && t <= 15) {
            double u = smooth((t - 5) / 10.0);
            double top1 = c[1] + 16.0;
            out.orbOn = true;
            out.orb[0] = c[0] + (l0 - c[0]) * u;
            out.orb[1] = top1 + (l1 - top1) * u;
            out.orb[2] = c[2] + (l2 - c[2]) * u;
        }
        if (16 <= t && t <= 17) {
            out.flashOn = true;
            out.flash[0] = l0;
            out.flash[1] = l1;
            out.flash[2] = l2;
        }
        if (18 <= t && t <= 23) {
            int k = st.n;
            out.forgeOn = true;
            out.forgeA[0] = l0;
            out.forgeA[1] = l1;
            out.forgeA[2] = l2;
            slotWorld(st, st.angle[k] + st.ringYaw, 0.0, out.forgeB);
        }
    }

    /**
     * weapon_flight (r4): DOCKED -> SLUNG (1) -> FLYING (6, a lateral arc on alternating sides) -> EMBED (5, held)
     * -> REEL (4) -> DOCKED (+2 px radial overshoot). The arc is fixed at release.
     */
    static int weaponFlight(State st, int k, int t, Pose out) {
        int o = st.orderOf[k];
        int tr = VOLLEY_RELEASE + RELEASE_GAP * o;
        int tau = t - tr;
        if (tau < 0)
            return M_DOCKED;
        double[] tgt = st.target;
        double latSign = o % 2 == 0 ? 1.0 : -1.0;
        double[] p0 = st.p0[k], radial = st.radDir[k], tangent = st.tangent[k];
        double[][] path = st.path[k];
        if (tau == 0) {
            double ang = st.angle[k] + st.ringYaw;
            slotWorld(st, ang, st.radTh[k], p0);
            double a = Math.toRadians(ang);
            radial[0] = -Math.sin(a);
            radial[1] = 0.0;
            radial[2] = -Math.cos(a);
            tangent[0] = -Math.cos(a);
            tangent[1] = 0.0;
            tangent[2] = Math.sin(a);
            double hx = tgt[0] - st.hub[0], hz = tgt[2] - st.hub[2];
            double hd = Math.sqrt(hx * hx + hz * hz);
            if (hd == 0.0)
                hd = 1.0;
            double dh0 = hx / hd, dh2 = hz / hd;                         // the bearing, horizontal
            double[] lat = st.va;                                        // its left-hand perpendicular
            lat[0] = -dh2;
            lat[1] = 0.0;
            lat[2] = dh0;
            double[] se = path[0], p1 = path[1], p2 = path[2], p3 = path[3], surf = st.vb, tv = st.vc;
            for (int i = 0; i < 3; i++)
                se[i] = p0[i] + tangent[i] * 2 + radial[i] * 2;
            se[1] += 1.0;
            double size = LENGTH[k] * DOCK_SCALE[k] * FLIGHT_SCALE;
            surf[0] = tgt[0] - dh0 * VICTIM_R;
            surf[1] = tgt[1];
            surf[2] = tgt[2] - dh2 * VICTIM_R;
            // the last leg comes in from the arc's side: its direction sets where the pommel stops (tip 2 px in)
            for (int i = 0; i < 3; i++)
                p2[i] = se[i] + (surf[i] - se[i]) * 0.75 + lat[i] * latSign * LATERAL;
            p2[1] += ARC_UP_2;
            for (int i = 0; i < 3; i++)
                tv[i] = surf[i] - p2[i];
            double nT = Math.sqrt(tv[0] * tv[0] + tv[1] * tv[1] + tv[2] * tv[2]);
            for (int i = 0; i < 3; i++) {
                tv[i] = tv[i] / nT;
                p3[i] = surf[i] + tv[i] * 2.0 - tv[i] * size;
            }
            for (int i = 0; i < 3; i++)
                p1[i] = se[i] + (p3[i] - se[i]) * 0.33 + lat[i] * latSign * LATERAL;
            p1[1] += ARC_UP_1;
            slotRotWorld(st, ang, 0.0, st.md);
            qFromMat(st.md, st.q0[k]);
        }
        double scale = 1.0 + (FLIGHT_SCALE - 1.0) * smooth((tau + 1) / (double) (SLING + 1));
        int kind = SPIN_KIND[k];
        double rate = SPIN_RATE[k];
        double[] hub = st.vi, ctrl = st.vj, pos = st.vf, q = st.qa;
        hubToward(st, st.hasPEnd[k] ? st.pEnd[k] : path[3], hub);
        int state;
        if (tau < SLING) {
            double u = smooth((tau + 1) / (double) SLING);
            for (int i = 0; i < 3; i++)
                pos[i] = p0[i] + (path[0][i] - p0[i]) * u;
            System.arraycopy(st.q0[k], 0, q, 0, 4);
            state = FLY_SLUNG;
            sagCtrl(hub, pos, 2.0, ctrl);
        } else if (tau < SLING + FLIGHT) {
            double u = (tau - SLING + 1) / (double) FLIGHT;
            bezier(path[0], path[1], path[2], path[3], u, pos);
            bezierD(path[0], path[1], path[2], path[3], u, st.vh);
            frameAlong(st.vh, st.mc);
            double[] base = st.qb;
            qFromMat(st.mc, base);
            double ang = rate * (tau - SLING + 1);
            if (tau >= SLING + FLIGHT - 2) {                             // the last 2 ticks: turn point-first
                double ang0 = rate * (FLIGHT - 2);
                ang = ang0 + ((Math.ceil(ang0 / 360.0) * 360.0) - ang0)
                    * smooth((tau - (SLING + FLIGHT - 3)) / 2.0);
            }
            double[] spin = st.qc;
            if (kind == ROLL) {
                qFromAxis(0, 0, 1, ang, spin);
                qMul(base, spin, q);
            } else if (kind == FLAT) {
                qFromAxis(0, 1, 0, ang, spin);
                qMul(spin, base, q);
            } else {
                qFromAxis(1, 0, 0, ang, spin);
                qMul(base, spin, q);
            }
            if (u >= 1.0)                                                // exactly point-first at the hit
                System.arraycopy(base, 0, q, 0, 4);
            state = FLY_FLYING;
            System.arraycopy(q, 0, st.qEnd[k], 0, 4);
            System.arraycopy(pos, 0, st.pEnd[k], 0, 3);
            st.hasPEnd[k] = true;
            // the chain pays out along the flown path: the quadratic through the path's halfway point
            double[] mid = st.va;
            bezier(path[0], path[1], path[2], path[3], u / 2.0, mid);
            for (int i = 0; i < 3; i++)
                ctrl[i] = 2 * mid[i] - (hub[i] + pos[i]) / 2;
        } else if (tau < SLING + FLIGHT + EMBED) {
            System.arraycopy(st.pEnd[k], 0, pos, 0, 3);
            System.arraycopy(st.qEnd[k], 0, q, 0, 4);
            state = FLY_EMBED;
            sagCtrl(hub, pos, 0.0, ctrl);                                // taut: the hit
        } else if (tau < SLING + FLIGHT + EMBED + REEL) {
            double u = (tau - (SLING + FLIGHT + EMBED) + 1) / (double) REEL;
            double e = u * u;                                             // slow start, fast arrival
            double ang = st.angle[k] + st.ringYaw;
            double[] dock = st.vh, mid = st.va, pEnd = st.pEnd[k];
            slotWorld(st, ang, 0.0, dock);
            for (int i = 0; i < 3; i++)
                mid[i] = (pEnd[i] + dock[i]) / 2;
            mid[1] += 6.0;
            for (int i = 0; i < 3; i++)
                pos[i] = (1 - e) * (1 - e) * pEnd[i] + 2 * (1 - e) * e * mid[i] + e * e * dock[i];
            slotRotWorld(st, ang, 0.0, st.md);
            double[] qd = st.qb, tumble = st.qc;
            qFromMat(st.md, qd);
            qSlerp(st.qEnd[k], qd, e, st.qd);
            qFromAxis(tangent[0], tangent[1], tangent[2], 360.0 * e, tumble);
            qMul(st.qd, tumble, q);
            scale = FLIGHT_SCALE + (1.0 - FLIGHT_SCALE) * e;
            state = FLY_REEL;
            sagCtrl(hub, pos, 3.0, ctrl);
            if (u >= 1.0) {
                st.radTh[k] = DOCK_OVERSHOOT;
                st.radOm[k] = 0.0;
                st.mode[k] = M_DOCKED;
                st.hasPEnd[k] = false;
                return M_DOCKED;
            }
        } else {
            return M_DOCKED;
        }
        qToMat(q, st.mc);
        out.flyState[k] = state;
        System.arraycopy(pos, 0, out.flyPos[k], 0, 3);
        euler(st.mc, out.flyRot[k]);
        out.flyScale[k] = scale;
        out.ribbon(hub, ctrl, pos, 1.6 * (tau == SLING + FLIGHT ? 1.3 : 1.0));
        return state == FLY_SLUNG ? M_SLUNG : state == FLY_FLYING ? M_FLYING : state == FLY_EMBED ? M_EMBED : M_REEL;
    }

    /** weapon_fall: death - the weapon lets go (one every 2 ticks in ring order), flung out, falls, shatters. */
    static int weaponFall(State st, int k, int t, Pose out) {
        if (!st.fallOn[k]) {
            slotWorld(st, st.angle[k] + st.ringYaw, 0.0, st.fallP[k]);
            double a = Math.toRadians(st.angle[k] + st.ringYaw);
            double[] vel = st.fallV[k];
            vel[0] = -Math.sin(a) * 1.2 - Math.cos(a) * 0.6;
            vel[1] = 0.0;
            vel[2] = -Math.cos(a) * 1.2 + Math.sin(a) * 0.6;
            slotRotWorld(st, st.angle[k] + st.ringYaw, 0.0, st.md);
            qFromMat(st.md, st.fallQ[k]);
            st.fallGround[k] = NO_GROUND;
            st.fallOn[k] = true;
        }
        double[] p = st.fallP[k], vel = st.fallV[k], q = st.fallQ[k];
        if (st.fallGround[k] != NO_GROUND && t - st.fallGround[k] >= 3) {
            out.weaponHidden[k] = true;
            return M_GONE;
        }
        if (st.fallGround[k] == NO_GROUND) {
            vel[1] -= GRAVITY;
            for (int i = 0; i < 3; i++)
                p[i] = p[i] + vel[i];
            qFromAxis(1, 0, 0, 25.0, st.qc);
            qMul(st.qc, q, q);
            if (p[1] <= 1.0) {
                p[1] = 1.0;
                st.fallGround[k] = t;
            }
        }
        out.flyState[k] = st.fallGround[k] == NO_GROUND ? FLY_FALLING : FLY_SHATTER;
        System.arraycopy(p, 0, out.flyPos[k], 0, 3);
        qToMat(q, st.mc);
        euler(st.mc, out.flyRot[k]);
        out.flyScale[k] = 1.0;
        return M_FALLING;
    }

    // ------------------------------------------------------------------ onto the model --------------------------

    /**
     * One model part as {@link #apply} writes it, in ModelPart's own terms: radians (MC signs) and model px (y down).
     * A client adapter implements it over ModelPart (xRot/yRot/zRot, x/y/z, xScale/yScale/zScale, visible).
     */
    public interface Part {
        void addRotation(float xRad, float yRad, float zRad);

        void addOffset(float x, float y, float z);

        void setOffset(float x, float y, float z);

        void setRotation(float xRad, float yRad, float zRad);

        void setScale(float s);

        void setVisible(boolean visible);
    }

    /** The resolved parts (null where the body has no such bone), like Quadruped.Rig / Dragon.Rig. */
    public static final class Bones {
        final Part[] parts = new Part[BONES.length];
        final Part[] slot = new Part[MAX_WEAPONS + 1], weapon = new Part[MAX_WEAPONS + 1];
        final Part lanternCore;

        public Bones(Function<String, ? extends Part> lookup) {
            for (int b = 0; b < BONES.length; b++)
                parts[b] = lookup.apply(BONES[b]);
            for (int k = 1; k <= MAX_WEAPONS; k++) {
                slot[k] = lookup.apply("slot_" + k);
                weapon[k] = lookup.apply("weapon_" + k);
            }
            lanternCore = lookup.apply("lantern_core");
        }
    }

    private static final float D2R = (float) (Math.PI / 180.0);

    /**
     * Writes a pose onto the parts (after the model's restPose). The sign mapping is tools/dsl_to_game.py's: a DSL
     * rotation (x, y, z) degrees goes on as (-x, +y, -z) radians, a DSL move (x, y, z) px as (+x, -y, +z) (model y is
     * down). Bones get their deltas added; a docked slot is placed absolutely (azimuth, pommel radius on the ring,
     * lift) with its weapon's pitch / roll and part scale; a slot that is not docked (flying, falling, unbound) is
     * hidden - the renderer draws flying weapons from Pose.fly* in model space.
     */
    public static void apply(Pose pose, Bones bones) {
        for (int b = 0; b < BONES.length; b++) {
            Part p = bones.parts[b];
            if (p == null)
                continue;
            double[] r = pose.rot[b], o = pose.off[b];
            p.addRotation((float) -r[0] * D2R, (float) r[1] * D2R, (float) -r[2] * D2R);
            p.addOffset((float) o[0], (float) -o[1], (float) o[2]);
        }
        for (int k = 1; k <= MAX_WEAPONS; k++) {
            Part s = bones.slot[k], w = bones.weapon[k];
            if (s != null) {
                s.setVisible(pose.slotOn[k]);
                if (pose.slotOn[k]) {
                    double a = Math.toRadians(pose.slotAngle[k]), rr = RING_R + pose.slotRadial[k];
                    s.setOffset((float) (-Math.sin(a) * rr), (float) -pose.slotY[k], (float) (-Math.cos(a) * rr));
                    s.setRotation(0f, (float) a, 0f);
                }
            }
            if (w != null) {
                w.setVisible(!pose.weaponHidden[k]);
                if (pose.slotOn[k]) {
                    w.addRotation((float) -pose.slotPitch[k] * D2R, 0f, (float) -pose.slotRoll[k] * D2R);
                    w.setScale((float) pose.slotScale[k]);
                }
            }
        }
        if (bones.lanternCore != null)
            bones.lanternCore.setVisible(!pose.lanternCoreHidden);
    }
}
