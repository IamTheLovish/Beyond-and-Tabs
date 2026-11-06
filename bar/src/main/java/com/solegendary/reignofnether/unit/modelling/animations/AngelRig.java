package com.solegendary.reignofnether.unit.modelling.animations;

import java.util.Arrays;
import java.util.function.Function;

/**
 * The Sunforged angels' rig (the Angel Caller's three summons: Mender Angel, Seraph Archer, Sword Seraph): a 1:1 port
 * of tools/angel_anim.py, THE motion spec (design sheet .scratch/angels/design-sheet.md section 3). Same state
 * variables, same order of operations, the same seeded LCG; AngelRigTest replays the driver's harness (run()) and must
 * land on every number of design/fixtures/angel_clips.json within 0.01. Change the driver first, regenerate the
 * fixture, then this.
 *
 * <pre>
 *   State st = new State(kind, seed, arrive);  Inputs in = new Inputs();  Pose pose = new Pose();   // once per unit
 *   in.speed = ...; in.yawRate; in.attackSeq; in.attackIn; in.hurtTime; in.dying;                // every game tick
 *   AngelRig.step(st, in, pose);                                                                 // st and pose written
 *   AngelRig.apply(pose, bones);                                                                 // after restPose
 * </pre>
 *
 * Pure maths: no Minecraft class in this file (the JUnit needs no game); {@link #apply} writes through the tiny
 * {@link Part} interface, which a client adapter (AngelParts) implements over ModelPart.
 *
 * <p>Conventions (design/models/README.md): DSL degrees, rotation order x then y then z (M = Rz Ry Rx), +x swings a
 * hanging part forward / tips a standing part's top back, +y turns the front toward -x. Every bone rotation / move in
 * the pose is a DELTA on the generated rest pose; a scale is absolute (1 = rest).
 */
public final class AngelRig {

    private AngelRig() {}

    /** The three summons, in the driver's KINDS order. */
    public enum Kind { MENDER, ARCHER, SWORD }

    // ------------------------------------------------------------------ constants (tools/angel_anim.py) ---------------
    static final double[] HOVER_T = {30.0, 26.0, 34.0};
    static final double[] FLY_T = {22.0, 20.0, 24.0};
    static final double[] BOB = {1.2, 1.5, 2.0};
    static final double[] FLY_PITCH = {-35.0, -35.0, -42.0};
    /** The attack clip's contact tick (the server's blow / release / heal lands on it). */
    public static final int[] EVENT = {10, 16, 10};
    static final int[] STROKE_S0 = {10, 16, 9}, STROKE_LEN = {5, 5, 4};
    public static final int[] ATTACK_LEN = {24, 33, 25};
    public static final int HIT_LEN = 10, ARRIVE_LEN = 30, DEATH_LEN = 20;
    static final double SUPINATE = -15.0;
    static final double[] UP = {30.0, 30.0, 30.0};
    static final double DN = -35.0;
    static final double DOWN0 = 0.08, DOWN1 = 0.56, BOTTOM1 = 0.60;
    /** Blocks per tick: the full flight pose. */
    public static final double CRUISE = 0.35;
    static final double TEMPO_SPREAD = 0.08;
    static final double BEAT_EASE = 0.12;
    static final double[] FOLD_HAND = {40.0, 40.0, 40.0};
    static final double[] FOLD_FORE = {20.0, 20.0, 20.0};
    static final double FOLD_TIPS = 4.0;
    static final double GLIDE_BOB = 1.2, GLIDE_DIH = 4.0;
    /** The attack downstroke's fore / hand lag (ticks) per kind. */
    static final double[][] STROKE_LAG = {{1.0, 2.0}, {0.5, 1.0}, {1.0, 2.0}};
    /** No flight turns more than this about its own long axis (body frame). */
    static final double PITCH_CAP = 45.0;
    /** The b4 flight cubes' rotations in their bone (left side; the right mirrors to (rx, -ry, -rz)): hand, tips. */
    static final double[][][][] FEATHERS = {
        {{{-20.7, 14.5, 3.6}},
            {{-69.1, 68.5, -58.7}, {-37.8, 56.1, -23.8}, {-27.4, 42.0, -10.1}, {-22.9, 28.6, -2.5}}},
        {{{-20.3, 9.4, 6.5}, {-20.5, 11.8, 5.6}},
            {{-37.8, 56.1, -22.8}, {-27.4, 42.0, -9.2}, {-22.1, 24.7, 0.4}}},
        {{{-20.1, 6.0, 9.8}, {-20.1, 4.4, 10.4}},
            {{-33.1, 51.2, -14.9}, {-25.5, 37.5, -4.2}, {-22.1, 24.6, 2.4}, {-20.6, 13.1, 7.1}}},
    };
    static final double ARRIVE_DROP = 14.0, DEATH_LIFT = 14.0, UNFURL_LEVEL = 5.0;
    static final double LAG_FORE = 2.0, LAG_HAND = 3.0, LAG_BOB = 3.0, LAG_PITCH = 3.0;
    static final int GLIDE_TICKS = 20, GLIDE_AFTER = 2;
    static final double ROLL_PER_YAW = 6.0, ROLL_CAP = 35.0, ROLL_A = 0.45;
    static final int BREATH_BEATS = 4;
    static final int GLANCE_MIN = 3, GLANCE_MAX = 5;
    static final double GLANCE_YAW = 18.0;
    static final double K_CLOTH = 0.15, C_CLOTH = 0.30;
    static final double K_HEM = 0.15, C_HEM = 0.30;
    static final double K_TIPS = 0.25, C_TIPS = 0.32;
    static final double K_HALO = 0.45, C_HALO = 0.50;
    static final double K_LANT = 0.10, C_LANT = 0.10;
    static final double K_LEG = 0.14, C_LEG = 0.40;
    static final double K_PEND = (2.0 * Math.PI / 24.0) * (2.0 * Math.PI / 24.0), C_PEND = 0.06;
    static final double HALO_PITCH = 1.0;
    static final double HEAD_COUNTER = 0.35;
    static final double[] SWORD_CARRY = {-30.0, 0.0, 10.0};
    static final double[][] HALO_OFF = {{0.0, 3.576, 4.914}, {0.0, 5.8, 4.514}, {0.0, 5.8, 4.514}};
    /** Two-bone arms per kind, side (0 l, 1 r): shoulder pivot S, elbow pivot E, hand point G (model px). */
    static final double[][][][] ARM = {
        {{{4.5, 25.196, 0.0}, {4.5, 19.596, 0.0}, {4.405, 14.186, -0.477}},
            {{-4.5, 25.196, 0.0}, {-4.5, 19.596, 0.0}, {-3.424, 12.17, -0.223}}},
        {{{4.5, 26.524, 0.0}, {4.5, 20.924, 0.0}, {3.886, 15.418, -0.585}},
            {{-4.5, 26.524, 0.0}, {-4.5, 20.924, 0.0}, {-3.424, 13.498, -0.223}}},
        {{{4.5, 26.524, 0.0}, {4.5, 20.924, 0.0}, {4.975, 14.11, -0.152}},
            {{-4.5, 26.524, 0.0}, {-4.5, 20.924, 0.0}, {-4.232, 16.052, -1.255}}},
    };
    static final double[] STRING_TIP_TOP = {9.7, 31.392, 1.115}, STRING_TIP_BOT = {-1.928, -0.557, 1.115};
    static final double[] BOW_LONG = {0.342, 0.94, 0.0}, BOW_FACE = {0.0, 0.0, -1.0};
    static final double[] AIM = {0.0, -0.342, -0.94};
    static final double[] SWORD_BLADE = {-0.16, 0.766, 0.643};
    /** The cut: (tick, share of the arc); the guard is reached 2 ticks before the first (held), contact stays t10. */
    static final double[][] SWORD_CUT = {{7, 0.0}, {8, 0.15}, {9, 0.40}, {10, 0.63}, {11, 0.81}, {12, 0.93}};

    // ------------------------------------------------------------------ bones -------------------------------------
    /** Every bone the rig writes, over all three bodies (a body without one skips it). */
    public static final String[] BONES = {"body", "head", "halo",
        "wing_l_arm", "wing_l_fore", "wing_l_hand", "wing_l_tips", "wing_r_arm", "wing_r_fore", "wing_r_hand", "wing_r_tips",
        "left_arm", "left_fore", "right_arm", "right_fore",
        "robe_front", "robe_back", "robe_left", "robe_right", "hem_front", "hem_back", "hem_left", "hem_right",
        "stole_front", "stole_back", "chain", "lantern",
        "tabard_front", "tabard_back", "tabard_left", "tabard_right",
        "left_thigh", "left_shin", "right_thigh", "right_shin",
        "bow", "string_top", "string_bot", "arrow", "sword"};
    public static final int N = BONES.length;

    /** The bone's index in {@link #BONES}, or -1. */
    public static int bone(String name) {
        for (int i = 0; i < N; i++)
            if (BONES[i].equals(name))
                return i;
        return -1;
    }

    static final int BODY = bone("body"), HEAD = bone("head"), HALO = bone("halo");
    static final int[] WING_ARM = {bone("wing_l_arm"), bone("wing_r_arm")};
    static final int[] WING_FORE = {bone("wing_l_fore"), bone("wing_r_fore")};
    static final int[] WING_HAND = {bone("wing_l_hand"), bone("wing_r_hand")};
    static final int[] WING_TIPS = {bone("wing_l_tips"), bone("wing_r_tips")};
    static final int[] ARM_B = {bone("left_arm"), bone("right_arm")};
    static final int[] FORE_B = {bone("left_fore"), bone("right_fore")};
    static final int[] SKIRT_B = {bone("robe_front"), bone("robe_back"), bone("robe_left"), bone("robe_right")};
    static final int[] HEM_B = {bone("hem_front"), bone("hem_back"), bone("hem_left"), bone("hem_right")};
    static final int STOLE_FRONT = bone("stole_front"), STOLE_BACK = bone("stole_back");
    static final int CHAIN = bone("chain"), LANTERN = bone("lantern");
    static final int[] TABARD_B = {bone("tabard_front"), bone("tabard_back"), bone("tabard_left"), bone("tabard_right")};
    static final int[] THIGH_B = {bone("left_thigh"), bone("right_thigh")};
    static final int[] SHIN_B = {bone("left_shin"), bone("right_shin")};
    static final int BOW = bone("bow"), STRING_TOP = bone("string_top"), STRING_BOT = bone("string_bot");
    static final int ARROW = bone("arrow"), SWORD = bone("sword");

    /** Skirt / hem pivot offsets from the skirt centre and their flare signs, in the order front, back, left, right. */
    static final double[][] SKIRT = {{0.0, 0.0, -2.15}, {0.0, 0.0, 1.85}, {2.8, 0.0, -0.15}, {-2.8, 0.0, -0.15}};
    static final double[][] HEM = {{0.0, -7.15, -3.45}, {0.0, -7.15, 3.15}, {4.6, -7.15, -0.15}, {-4.6, -7.15, -0.15}};
    static final double[][] FLARE = {{1.0, 0.0}, {-1.0, 0.0}, {0.0, 1.0}, {0.0, -1.0}};

    /** Rest rotations (tools/gen_angels.py b2 == b4) of the bones the rig poses in a parent frame; null = none. */
    static final double[][][] REST = new double[3][N][];

    static {
        double[][] m = REST[0], a = REST[1], s = REST[2];
        m[ARM_B[0]] = new double[]{-18.0, -12.0, 42.0};
        m[FORE_B[0]] = new double[]{96.0, 0.0, 0.0};
        m[ARM_B[1]] = new double[]{32.0, -32.0, -10.0};
        m[FORE_B[1]] = new double[]{36.0, 0.0, 0.0};
        m[CHAIN] = new double[]{-74.94, -38.49, -21.767};
        m[HALO] = new double[]{35.0, 0.0, 0.0};
        a[ARM_B[0]] = new double[]{18.0, -8.0, 14.0};
        a[FORE_B[0]] = new double[]{56.0, 0.0, 0.0};
        a[ARM_B[1]] = new double[]{4.0, 6.0, 0.0};
        a[FORE_B[1]] = new double[]{54.0, 0.0, 0.0};
        a[BOW] = new double[]{-73.84, -11.264, -11.557};
        a[HALO] = new double[]{25.0, 0.0, 0.0};
        a[STRING_TOP] = new double[]{0.0, 0.0, -20.0};
        a[STRING_BOT] = new double[]{0.0, 0.0, 160.0};
        a[ARROW] = new double[]{-58.141, -3.175, 5.094};
        s[ARM_B[0]] = new double[]{10.0, 8.0, 14.0};
        s[FORE_B[0]] = new double[]{70.0, 0.0, 0.0};
        s[ARM_B[1]] = new double[]{-8.0, 2.0, -18.0};
        s[FORE_B[1]] = new double[]{102.0, 0.0, 0.0};
        s[SWORD] = new double[]{-94.206, 18.094, 0.696};
        s[HALO] = new double[]{25.0, 0.0, 0.0};
        for (double[][] r : new double[][][]{a, s}) {
            r[TABARD_B[0]] = new double[]{6.0, 0.0, 0.0};
            r[TABARD_B[1]] = new double[]{-6.0, 0.0, 0.0};
            r[THIGH_B[0]] = new double[]{-14.0, 0.0, 0.0};
            r[THIGH_B[1]] = new double[]{-14.0, 0.0, 0.0};
            r[SHIN_B[0]] = new double[]{-12.0, 0.0, 0.0};
            r[SHIN_B[1]] = new double[]{-12.0, 0.0, 0.0};
        }
    }

    // ------------------------------------------------------------------ inputs / state / pose ---------------------

    /** What the client knows each game tick (the driver's Inputs). */
    public static final class Inputs {
        /** Blocks per tick, horizontal. */
        public double speed;
        /** Degrees per tick of the body's yaw (+ turns right, Minecraft's convention). */
        public double yawRate;
        /** A cue counter bumped ON the contact tick (the sword's blow, the archer's release, the mender's heal). */
        public int attackSeq;
        /** Ticks until the next contact as the client predicts it, -1 = none due. */
        public int attackIn = -1;
        /** Vanilla hurtTime (10 on a hit, counting down). */
        public int hurtTime;
        /** Dying (deathTime > 0). */
        public boolean dying;
    }

    /** A spring-damper: x, v, and whether it has been made yet (the driver's st.s(name, x0)). */
    static final class Spring {
        double x, v;
        boolean made;

        Spring at(double x0) {
            if (!made) {
                x = x0;
                v = 0.0;
                made = true;
            }
            return this;
        }
    }

    /** One unit's motion state (the driver's State). */
    public static final class State {
        public final Kind kind;
        final int k;
        long rng;
        final double tempo;
        /** Ticks per wing beat (eased toward the state's target): the phase advances 1 / beat a tick. */
        public double beat;
        public double phase;
        int beats;
        int glanceAt = 2, glanceT = -1;
        double glanceYaw;
        double flyW, speedE, roll1, roll2;
        int glide;
        double glideW;
        int flyBeats;
        int attackT = -1, attackSeq, cuts, variant;
        int hitT = -1, sinceHit = 99, hurtLast;
        int arriveT, deathT = -1;
        int age;
        final Spring pitch = new Spring(), tipsL = new Spring(), tipsR = new Spring(), pendX = new Spring(),
            pendZ = new Spring(), lantX = new Spring(), lantZ = new Spring(), clothX = new Spring(), clothZ = new Spring(),
            flare = new Spring(), hemX = new Spring(), hemZ = new Spring(), legL = new Spring(), legR = new Spring(),
            haloX = new Spring(), haloY = new Spring(), haloZ = new Spring();
        double prevPitch = Double.NaN, prevPitchV = Double.NaN, prevBob = Double.NaN, prevBobV = Double.NaN,
            prevBobC = Double.NaN;

        public State(Kind kind, long seed, boolean arrive) {
            this.kind = kind;
            this.k = kind.ordinal();
            this.rng = seed & 0x7fffffffL;
            this.tempo = 1.0 + TEMPO_SPREAD * (2.0 * rand() - 1.0);
            this.beat = HOVER_T[k] * tempo;
            this.arriveT = arrive ? 0 : -1;
        }

        double rand() {
            rng = (rng * 1103515245L + 12345L) & 0x7fffffffL;
            return rng / 2147483648.0;
        }

        /** Ticks into the attack clip (-1 none). */
        public int attackT() {
            return attackT;
        }

        /** Ticks into the arrival (-1 none). */
        public int arriveT() {
            return arriveT;
        }

        /** Ticks into the death (-1 none). */
        public int deathT() {
            return deathT;
        }
    }

    /** The pose step() wrote: per bone a DSL rotation delta (deg), a move (DSL px), a scale, hidden; and the fx. */
    public static final class Pose {
        public final double[][] rot = new double[N][3], move = new double[N][3], scale = new double[N][3];
        public final boolean[] hidden = new boolean[N];
        /** The light overlay (0..1), the body's alpha, the halo's alpha, the kind's glow multiplier. */
        public double white, alpha = 1.0, haloAlpha = 1.0, glow = 1.0;

        public Pose() {
            reset();
        }

        void reset() {
            for (int b = 0; b < N; b++) {
                Arrays.fill(rot[b], 0.0);
                Arrays.fill(move[b], 0.0);
                Arrays.fill(scale[b], 1.0);
                hidden[b] = false;
            }
            white = 0.0;
            alpha = 1.0;
            haloAlpha = 1.0;
            glow = 1.0;
        }

        /** Copies {@code o} into this pose. */
        public void set(Pose o) {
            for (int b = 0; b < N; b++) {
                System.arraycopy(o.rot[b], 0, rot[b], 0, 3);
                System.arraycopy(o.move[b], 0, move[b], 0, 3);
                System.arraycopy(o.scale[b], 0, scale[b], 0, 3);
                hidden[b] = o.hidden[b];
            }
            white = o.white;
            alpha = o.alpha;
            haloAlpha = o.haloAlpha;
            glow = o.glow;
        }
    }

    // ------------------------------------------------------------------ maths -----------------------------------------
    static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }

    public static double smooth(double x) {
        x = clamp(x, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Python's x % 1.0 (never negative). */
    static double frac(double x) {
        double m = x % 1.0;
        if (m != 0.0 && m < 0.0)
            m += 1.0;
        return m;
    }

    /** A monotone cubic (Fritsch-Carlson) through keys {tick, value}: the driver's keys(). */
    static double keys(double t, double[][] ks) {
        int n = ks.length;
        if (t <= ks[0][0])
            return ks[0][1];
        if (t >= ks[n - 1][0])
            return ks[n - 1][1];
        int i = 0;
        while (ks[i + 1][0] < t)
            i++;
        double h = ks[i + 1][0] - ks[i][0];
        double m0 = tangent(ks, i) * h, m1 = tangent(ks, i + 1) * h;
        double v0 = ks[i][1], v1 = ks[i + 1][1];
        double u = (t - ks[i][0]) / h;
        double u2 = u * u, u3 = u * u * u;
        return (2 * u3 - 3 * u2 + 1) * v0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * v1 + (u3 - u2) * m1;
    }

    private static double tangent(double[][] ks, int j) {
        int n = ks.length;
        if (j == 0 || j == n - 1)
            return 0.0;
        double h0 = ks[j][0] - ks[j - 1][0], h1 = ks[j + 1][0] - ks[j][0];
        double d0 = (ks[j][1] - ks[j - 1][1]) / h0, d1 = (ks[j + 1][1] - ks[j][1]) / h1;
        if (d0 * d1 <= 0.0)
            return 0.0;
        double w1 = 2.0 * h1 + h0, w2 = h1 + 2.0 * h0;
        return (w1 + w2) / (w1 / d0 + w2 / d1);
    }

    /** keys() per component: ticks tk, values v[j] = (x, y, z). */
    static double[] vkeys(double t, double[] tk, double[][] v) {
        double[] out = new double[3];
        double[][] ks = new double[tk.length][2];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < tk.length; j++) {
                ks[j][0] = tk[j];
                ks[j][1] = v[j][i];
            }
            out[i] = keys(t, ks);
        }
        return out;
    }

    static double spring(Spring s, double target, double k, double c, double drive) {
        s.v += k * (target - s.x) - c * s.v + drive;
        s.x += s.v;
        return s.x;
    }

    static double spring(Spring s, double target, double k, double c) {
        return spring(s, target, k, c, 0.0);
    }

    static double[][] rmat(double[] r) {
        return rmat(r[0], r[1], r[2]);
    }

    /** DSL rotation matrix (degrees, x then y then z: Rz Ry Rx). */
    static double[][] rmat(double x, double y, double z) {
        double cx = Math.cos(Math.toRadians(x)), sx = Math.sin(Math.toRadians(x));
        double cy = Math.cos(Math.toRadians(y)), sy = Math.sin(Math.toRadians(y));
        double cz = Math.cos(Math.toRadians(z)), sz = Math.sin(Math.toRadians(z));
        return new double[][]{
            {cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx},
            {sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx},
            {-sy, cy * sx, cy * cx}};
    }

    static double[][] mmul(double[][] a, double[][] b) {
        double[][] o = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                o[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
        return o;
    }

    static double[][] mT(double[][] a) {
        double[][] o = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                o[i][j] = a[j][i];
        return o;
    }

    static double[] mv(double[][] a, double[] v) {
        return new double[]{a[0][0] * v[0] + a[0][1] * v[1] + a[0][2] * v[2],
            a[1][0] * v[0] + a[1][1] * v[1] + a[1][2] * v[2],
            a[2][0] * v[0] + a[2][1] * v[1] + a[2][2] * v[2]};
    }

    static double[][] axisMat(double[] ax, double deg) {
        double n = Math.sqrt(ax[0] * ax[0] + ax[1] * ax[1] + ax[2] * ax[2]);
        double x = ax[0] / n, y = ax[1] / n, z = ax[2] / n;
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        double C = 1.0 - c;
        return new double[][]{
            {c + x * x * C, x * y * C - z * s, x * z * C + y * s},
            {y * x * C + z * s, c + y * y * C, y * z * C - x * s},
            {z * x * C - y * s, z * y * C + x * s, c + z * z * C}};
    }

    static double[] euler(double[][] r) {
        double ry = Math.asin(clamp(-r[2][0], -1.0, 1.0));
        return new double[]{Math.toDegrees(Math.atan2(r[2][1], r[2][2])), Math.toDegrees(ry),
            Math.toDegrees(Math.atan2(r[1][0], r[0][0]))};
    }

    static double wrap(double a) {
        while (a > 180.0)
            a -= 360.0;
        while (a < -180.0)
            a += 360.0;
        return a;
    }

    /** The pose extra (degrees added to the rest Euler) that makes a bone's rotation rNew. */
    static void deltaOnRest(double[] rest, double[][] rNew, double[] out) {
        double[] e = euler(rNew);
        for (int i = 0; i < 3; i++)
            out[i] = wrap(e[i] - rest[i]);
    }

    static double[] unit(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / n, v[1] / n, v[2] / n};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] madd(double[] a, double s, double[] b) {
        return new double[]{a[0] + s * b[0], a[1] + s * b[1], a[2] + s * b[2]};
    }

    /** Columns x^, y^ (y made square to x), z^ = x^ cross y^. */
    static double[][] frame(double[] x, double[] y) {
        x = unit(x);
        double d = dot(y, x);
        y = unit(new double[]{y[0] - d * x[0], y[1] - d * x[1], y[2] - d * x[2]});
        double[] z = cross(x, y);
        return new double[][]{{x[0], y[0], z[0]}, {x[1], y[1], z[1]}, {x[2], y[2], z[2]}};
    }

    /** The smallest rotation turning direction a onto direction b. */
    static double[][] align(double[] a, double[] b) {
        a = unit(a);
        b = unit(b);
        double[] ax = cross(a, b);
        double s = Math.sqrt(dot(ax, ax));
        double c = clamp(dot(a, b), -1.0, 1.0);
        if (s < 1e-9)
            return new double[][]{{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
        return axisMat(ax, Math.toDegrees(Math.atan2(s, c)));
    }

    // ------------------------------------------------------------------ the wing cycle ------------------------------
    static double warp(double p) {
        p = frac(p);
        if (p < DOWN0)
            p += 1.0;
        if (p < BOTTOM1)
            return 0.5 * (p - DOWN0) / (BOTTOM1 - DOWN0);
        return 0.5 + 0.5 * (p - BOTTOM1) / (1.0 + DOWN0 - BOTTOM1);
    }

    static double elev0(double p, double up) {
        p = frac(p);
        if (p < DOWN0)
            return up;
        if (p < DOWN1) {
            double u = (p - DOWN0) / (DOWN1 - DOWN0);
            return up + (DN - up) * u * u * (2.2 - 1.2 * u);
        }
        if (p < BOTTOM1)
            return DN;
        return DN + (up - DN) * smooth((p - BOTTOM1) / (1.0 - BOTTOM1));
    }

    static double fold0(double p) {
        p = frac(p);
        if (p < 0.57)
            return 0.0;
        if (p < 0.71)
            return smooth((p - 0.57) / 0.14);
        if (p < 0.86)
            return 1.0;
        return 1.0 - smooth((p - 0.86) / 0.14);
    }

    static double bob0(double p) {
        return -Math.cos(2.0 * Math.PI * warp(p));
    }

    // a wing's channels: arm (elev, sweep), fore (elev, fold), hand (elev, fold), the supination, the tips' fold
    static final int W_ARM = 0, W_FORE = 2, W_HAND = 4, W_SUP = 6, W_TIPS = 7;

    /** The driver's wing_cycle; ampUp NaN = None. */
    static double[] wingCycle(int k, double p, double B, double amp, double sweepAmp, double droop, double foldExtra,
                              double ampUp, double foldK) {
        double au = Double.isNaN(ampUp) ? Math.min(1.0, amp) : ampUp;
        double mid = 0.5 * (UP[k] + DN);
        double ea = e(k, p, mid, au, amp), ef = e(k, p - LAG_FORE / B, mid, au, amp),
            eh = e(k, p - LAG_HAND / B, mid, au, amp);
        double sweep = sweepAmp * (1.0 - 11.0 * Math.cos(2.0 * Math.PI * warp(p)));
        double[] w = new double[8];
        w[W_ARM] = ea + droop;
        w[W_ARM + 1] = sweep;
        w[W_SUP] = SUPINATE * fold0(p - 1.0 / B);
        w[W_FORE] = ef - ea;
        w[W_FORE + 1] = foldK * FOLD_FORE[k] * fold0(p - 1.5 / B);
        w[W_HAND] = eh - ef;
        w[W_HAND + 1] = foldK * FOLD_HAND[k] * fold0(p) + foldExtra;
        w[W_TIPS] = foldK * FOLD_TIPS * fold0(p - 2.0 / B);
        return w;
    }

    private static double e(int k, double q, double mid, double au, double amp) {
        double v = elev0(q, UP[k]) - mid;
        return mid + (v > 0.0 ? au : amp) * v;
    }

    static double[] wingCycle(int k, double p, double B, double amp, double sweepAmp) {
        return wingCycle(k, p, B, amp, sweepAmp, 0.0, 0.0, Double.NaN, 1.0);
    }

    // the death / arrival cocoon (wings closed up over the shoulders), as (arm, fore, hand) pairs, an unused
    // supination slot and the tips' fold
    static final double[] COCOON = {60.0, 5.0, 10.0, 0.0, 5.0, 0.0, 0.0, 0.0};

    static double[] arriveWing(int k, int t, double B) {
        double[] top = wingCycle(k, 0.0, B, 1.0, 1.0);
        double w = smooth((t - 14.0) / 9.0);
        double[] out = new double[8];
        for (int i = 0; i < 6; i++)
            out[i] = lerp(COCOON[i], top[i], w);
        out[W_ARM] = keys(t, new double[][]{{14, COCOON[W_ARM]}, {23, UNFURL_LEVEL}, {25, UNFURL_LEVEL - 10.0},
            {29, top[W_ARM]}});
        out[W_ARM] += 2.0 * Math.sin(2.0 * Math.PI * t / 10.0) * (1.0 - smooth((t - 8.0) / 6.0));
        out[W_TIPS] = lerp(COCOON[W_TIPS], top[W_TIPS], w);
        out[W_SUP] = top[W_SUP] * w;
        return out;
    }

    /** How far a flight (its cube's rotation R in the body frame) is turned about its own long axis off flat. */
    static double featherPitch(double[][] R) {
        double[] n = mv(R, new double[]{0.0, 1.0, 0.0}), l = mv(R, new double[]{0.0, 0.0, 1.0});
        if (Math.abs(l[1]) > 0.985)
            return 0.0;
        double[] n0 = unit(new double[]{-l[1] * l[0], 1.0 - l[1] * l[1], -l[1] * l[2]});
        return Math.toDegrees(Math.atan2(dot(cross(n0, n), l), dot(n0, n)));
    }

    /** The bone's rotation L (under P) twisted back about its flights' mean long axis to the cap; null if none is over. */
    static double[][] capPitch(double[][] P, double[][] L, double[][][] Rc) {
        double[] sum = new double[3];
        for (double[][] R : Rc) {
            double[] z = mv(R, new double[]{0.0, 0.0, 1.0});
            for (int i = 0; i < 3; i++)
                sum[i] += z[i];
        }
        double[] ax = unit(sum);
        boolean changed = false;
        for (int it = 0; it < 3; it++) {
            double[][] PL = mmul(P, L);
            double s = 0.0;
            boolean first = true;
            for (double[][] R : Rc) {
                double f = featherPitch(mmul(PL, R));
                if (first || Math.abs(f) > Math.abs(s))
                    s = f;
                first = false;
            }
            if (Math.abs(s) <= PITCH_CAP + 0.25)
                break;
            L = mmul(L, axisMat(ax, -(s - Math.copySign(PITCH_CAP, s))));
            changed = true;
        }
        return changed ? L : null;
    }

    // ------------------------------------------------------------------ the arms --------------------------------
    /** (posed hand point, posed elbow direction from the shoulder) of the rest pose, body frame. */
    static double[][] restHand(int k, int side) {
        double[][] arm = ARM[k][side];
        double[] S = arm[0], E = arm[1], G = arm[2];
        double[][] Ra = rmat(REST[k][ARM_B[side]]);
        double[] a = sub(E, S), b = sub(G, E);
        double[] fb = mv(rmat(REST[k][FORE_B[side]]), b);
        double[] q = add(a, fb);
        double[] hq = mv(Ra, q);
        return new double[][]{add(S, hq), mv(Ra, a)};
    }

    /** The rotation w of the way from ra to rb (about the one axis of ra^T rb). */
    static double[][] rotLerp(double[][] ra, double[][] rb, double w) {
        double[][] D = mmul(mT(ra), rb);
        double ang = Math.toDegrees(Math.acos(clamp((D[0][0] + D[1][1] + D[2][2] - 1.0) / 2.0, -1.0, 1.0)));
        double[] ax = {D[2][1] - D[1][2], D[0][2] - D[2][0], D[1][0] - D[0][1]};
        if (ang < 1e-6 || dot(ax, ax) < 1e-12)
            return w < 0.5 ? ra : rb;
        return mmul(ra, axisMat(ax, ang * w));
    }

    private static double[][] stow;

    /** The arrow's rotation in the draw hand's fore as it appears at attack t3: where it rests while hidden. */
    static double[][] arrowStow() {
        if (stow == null) {
            Attack a = attackTracks(1, 3, 0);
            double[] phi = new double[1];
            double[][] ra = ikArm(1, 1, a.ikR, a.poleR, phi);
            stow = mmul(mT(mmul(ra, rmat(phi[0], 0.0, 0.0))), align(new double[]{0.0, 0.0, -1.0}, a.aim));
        }
        return stow;
    }

    /** Two-bone IK (the driver's ik_arm): the upper arm's rotation (body frame); phiOut[0] = the fore's hinge (deg). */
    static double[][] ikArm(int k, int side, double[] target, double[] pole, double[] phiOut) {
        double[][] arm = ARM[k][side];
        double[] S = arm[0], E = arm[1], G = arm[2];
        double fx = REST[k][FORE_B[side]][0];
        double[] a = sub(E, S), b = sub(G, E), tv = sub(target, S);
        double la = Math.sqrt(dot(a, a)), lb = Math.sqrt(dot(b, b));
        double D = clamp(Math.sqrt(dot(tv, tv)), Math.abs(la - lb) + 0.05, la + lb - 0.02);
        double c0 = a[0] * b[0];
        double A = a[1] * b[1] + a[2] * b[2];
        double Bq = a[2] * b[1] - a[1] * b[2];
        double R = Math.hypot(A, Bq);
        double g = Math.atan2(Bq, A);
        double cphi = clamp(((D * D - la * la - lb * lb) / 2.0 - c0) / R, -1.0, 1.0);
        double r0 = g + Math.acos(cphi), r1 = g - Math.acos(cphi);
        double ref = Math.toRadians(fx);
        double phi = Math.abs(wrap(Math.toDegrees(r1 - ref))) < Math.abs(wrap(Math.toDegrees(r0 - ref))) ? r1 : r0;
        double[] fb = mv(rmat(Math.toDegrees(phi), 0.0, 0.0), b);
        double[] q = add(a, fb);
        phiOut[0] = Math.toDegrees(phi);
        return mmul(frame(tv, pole), mT(frame(q, a)));
    }

    // ------------------------------------------------------------------ clips -------------------------------------
    /** The attack clip's scripted channels at one tick (the driver's attack_tracks dict). */
    static final class Attack {
        double bodyDy, bodyRx, bodyRy, bodyDz, headRx, headRy;
        double[] ikL, ikR, poleL, poleR, la, aim, blade, planeHd, planeN;
        double kick, glow = Double.NaN, wingW, cup, bow, recoil, string, ampUp = Double.NaN;
        boolean arrow;
    }

    private static double[][] k2(double... tv) {
        double[][] ks = new double[tv.length / 2][2];
        for (int i = 0; i < ks.length; i++) {
            ks[i][0] = tv[2 * i];
            ks[i][1] = tv[2 * i + 1];
        }
        return ks;
    }

    private static double[] v3(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    private static double[] along(double[][] seq, double[] fr, double s) {
        for (int n = 0; n < fr.length - 1; n++)
            if (s <= fr[n + 1] || n == fr.length - 2) {
                double u = clamp((s - fr[n]) / (fr[n + 1] - fr[n]), 0.0, 1.0);
                return new double[]{lerp(seq[n][0], seq[n + 1][0], u), lerp(seq[n][1], seq[n + 1][1], u),
                    lerp(seq[n][2], seq[n + 1][2], u)};
            }
        return null;
    }

    static Attack attackTracks(int k, int t, int variant) {
        int E = EVENT[k];
        Attack o = new Attack();
        if (k == 0) {
            double[][] l = restHand(k, 0), r = restHand(k, 1);
            double[] L0 = l[0], R0 = r[0], eL = l[1], eR = r[1];
            o.bodyDy = keys(t, k2(0, 0.0, 6, 1.0, 9, 1.0, 11, 2.6, 16, 1.4, 24, 0.0));
            o.bodyRx = keys(t, k2(0, 0.0, 6, 4.0, 9, 4.0, 10, -6.0, 14, -2.0, 24, 0.0));
            o.headRx = keys(t, k2(0, 0.0, 6, -6.0, 9, -6.0, 11, -10.0, 24, 0.0));
            double[] tk = {0, 6, 9, 10, 12, 16, 24};
            o.ikL = vkeys(t, tk, new double[][]{L0, v3(2.4, 21.2, -9.2), v3(2.4, 21.2, -9.2), v3(2.6, 23.0, -10.2),
                v3(2.6, 23.6, -10.4), v3(2.6, 23.0, -10.2), L0});
            o.ikR = vkeys(t, tk, new double[][]{R0, v3(0.2, 20.0, -9.8), v3(0.2, 20.0, -9.8), v3(0.0, 22.2, -11.6),
                v3(0.0, 22.8, -12.0), v3(0.0, 22.2, -11.6), R0});
            double[] tp = {0, 6, 16, 24};
            o.poleL = vkeys(t, tp, new double[][]{eL, v3(1.0, -0.6, 0.6), v3(1.0, -0.6, 0.6), eL});
            o.poleR = vkeys(t, tp, new double[][]{eR, v3(-1.0, -0.6, 0.6), v3(-1.0, -0.6, 0.6), eR});
            o.kick = t == E ? 4.0 : 0.0;
            o.glow = keys(t, k2(0, 1.0, 6, 1.0, 9, 1.3, 10, 1.6, 18, 1.0));
            o.wingW = keys(t, k2(0, 0.0, 6, 1.0, 16, 1.0, 24, 0.0));
            o.cup = keys(t, k2(0, 0.0, 6, 8.0, 9, 8.0, 11, 0.0));
        } else if (k == 1) {
            double[][] l = restHand(k, 0), r = restHand(k, 1);
            double[] L0 = l[0], R0 = r[0], eL = l[1], eR = r[1];
            double tw = keys(t, k2(0, 0.0, 4, 10.0, 12, 35.0, 16, 35.0, 18, 32.0, 26, 8.0, 33, 0.0));
            o.bodyRy = tw;
            o.headRy = -tw;
            o.bodyDz = keys(t, k2(0, 0.0, 16, 0.0, 18, 0.8, 26, 0.2, 33, 0.0));
            double[] d35 = mv(rmat(0.0, -35.0, 0.0), AIM);
            double[] cheek = {-1.6, 26.6, -3.4};
            double[] g12 = madd(cheek, 8.4, d35);
            o.ikL = vkeys(t, new double[]{0, 4, 12, 16, 17, 18, 26, 33}, new double[][]{L0, v3(5.6, 23.5, -7.6), g12, g12,
                madd(g12, 0.3, d35), g12, v3(5.8, 22.4, -8.2), L0});
            o.ikR = vkeys(t, new double[]{0, 4, 12, 16, 17, 18, 21, 24, 33}, new double[][]{R0, v3(4.4, 24.0, -5.8), cheek,
                cheek, v3(-3.0, 28.0, -4.4), v3(-3.2, 28.0, -4.6), v3(-5.0, 25.0, -3.0), v3(-5.6, 22.6, 1.2), R0});
            o.poleL = vkeys(t, new double[]{0, 4, 26, 33}, new double[][]{eL, v3(1.0, -0.5, 0.2), v3(1.0, -0.5, 0.2), eL});
            o.poleR = vkeys(t, new double[]{0, 4, 12, 18, 24, 33}, new double[][]{eR, v3(-0.6, -1.0, 0.3),
                v3(-1.0, 0.15, 0.6), v3(-1.0, 0.1, 0.5), v3(-1.0, -0.3, 0.2), eR});
            o.aim = mv(rmat(0.0, -tw, 0.0), AIM);
            o.bow = keys(t, k2(0, 0.0, 4, 0.25, 12, 1.0, 18, 1.0, 26, 0.15, 33, 0.0));
            o.recoil = keys(t, k2(0, 0.0, 16, 0.0, 17, 1.0, 18, 0.0));
            o.string = keys(t, k2(0, 0.0, 3.9, 0.0, 4.6, 1.0, 16, 1.0, 17, -0.12, 18, 0.05, 19, 0.0));
            o.arrow = 3 <= t && t <= 15;
            o.ampUp = keys(t, k2(0, 1.0, 4, 1.0, 12, 0.4, 20, 0.4, 30, 1.0));
            o.wingW = keys(t, k2(0, 0.0, 8, 1.0, 16, 1.0));
            o.cup = 0.0;
        } else {
            double[][] r = restHand(k, 1);
            double[] R0 = r[0], eR = r[1];
            double[][] pts = {v3(-8.0, 30.0, -2.5), v3(-3.0, 24.5, -8.8), v3(-0.8, 21.8, -8.2), v3(0.5, 20.5, -6.5)};
            double[][] dirs = {v3(-0.3, 0.93, 0.1), v3(-0.05, 0.45, -0.9), v3(0.4, -0.1, -0.9), v3(0.5, -0.8, -0.35)};
            if (variant == 1) {
                pts = new double[][]{pts[3], pts[2], pts[1], pts[0]};
                dirs = new double[][]{dirs[3], dirs[2], dirs[1], dirs[0]};
                pts[1] = v3(-1.5, 23.0, -9.0);
                dirs[1] = v3(0.1, 0.4, -0.9);
            }
            double[] cum = new double[pts.length];
            for (int i = 1; i < pts.length; i++) {
                double s = 0.0;
                for (int c = 0; c < 3; c++)
                    s += (pts[i][c] - pts[i - 1][c]) * (pts[i][c] - pts[i - 1][c]);
                cum[i] = cum[i - 1] + Math.sqrt(s);
            }
            double[] fr = new double[cum.length];
            for (int i = 0; i < cum.length; i++)
                fr[i] = cum[i] / cum[cum.length - 1];
            double[] over = unit(new double[]{dirs[3][0] + 0.22 * (dirs[3][0] - dirs[2][0]),
                dirs[3][1] + 0.22 * (dirs[3][1] - dirs[2][1]), dirs[3][2] + 0.22 * (dirs[3][2] - dirs[2][2])});
            double[] D0 = mv(rmat(SWORD_CARRY), SWORD_BLADE);
            int nc = SWORD_CUT.length;
            double[] tkI = new double[nc + 6], tkB = new double[nc + 7];
            double[][] vI = new double[nc + 6][], vB = new double[nc + 7][];
            tkI[0] = 0;
            vI[0] = R0;
            tkI[1] = SWORD_CUT[0][0] - 2;
            vI[1] = pts[0];
            tkB[0] = 0;
            vB[0] = D0;
            tkB[1] = SWORD_CUT[0][0] - 2;
            vB[1] = dirs[0];
            for (int i = 0; i < nc; i++) {
                tkI[2 + i] = tkB[2 + i] = SWORD_CUT[i][0];
                vI[2 + i] = along(pts, fr, SWORD_CUT[i][1]);
                vB[2 + i] = unit(along(dirs, fr, SWORD_CUT[i][1]));
            }
            double[] tI = {13, 16, 20, 25}, tB = {13, 14, 16, 20, 25};
            double[][] wI = {pts[3], pts[3], v3(-3.0, 20.5, -8.0), R0};
            double[][] wB = {over, dirs[3], dirs[3], v3(0.0, 0.3, -0.95), D0};
            for (int i = 0; i < 4; i++) {
                tkI[2 + nc + i] = tI[i];
                vI[2 + nc + i] = wI[i];
            }
            for (int i = 0; i < 5; i++) {
                tkB[2 + nc + i] = tB[i];
                vB[2 + nc + i] = wB[i];
            }
            o.ikR = vkeys(t, tkI, vI);
            o.blade = unit(vkeys(t, tkB, vB));
            o.poleR = vkeys(t, new double[]{0, 6, 20, 25}, new double[][]{eR, v3(-1.0, -0.5, 0.4), v3(-1.0, -0.5, 0.4), eR});
            double[] hd = unit(dirs[0]);
            o.planeHd = hd;
            o.planeN = unit(cross(hd, unit(dirs[1])));
            double sgn = variant == 0 ? 1.0 : -1.0;
            o.bodyRx = keys(t, k2(0, 0.0, 6, 10.0, 8, 10.0, 10, -6.0, 12, -12.0, 16, -6.0, 25, 0.0));
            o.bodyRy = sgn * keys(t, k2(0, 0.0, 6, 15.0, 8, 15.0, 9, 2.0, 10, -16.0, 11, -25.0, 14, -25.0, 16, -16.0,
                25, 0.0));
            o.bodyDz = keys(t, k2(0, 0.0, 8, 0.0, 10, -1.5, 12, -3.0, 16, -2.0, 25, 0.0));
            o.bodyDy = keys(t, k2(0, 0.0, 6, 0.6, 8, 0.6, 10, -0.8, 12, -2.0, 16, -1.2, 25, 0.0));
            o.headRy = -0.6 * o.bodyRy;
            o.la = new double[]{keys(t, k2(0, 0.0, 6, 26.0, 8, 26.0, 11, 6.0, 16, 6.0, 25, 0.0)),
                keys(t, k2(0, 0.0, 6, -10.0, 8, -10.0, 25, 0.0)),
                keys(t, k2(0, 0.0, 6, 4.0, 8, 4.0, 11, 34.0, 16, 30.0, 25, 0.0))};
            o.wingW = keys(t, k2(0, 0.0, 6, 1.0, 9, 1.0));
            o.cup = keys(t, k2(0, 0.0, 6, 8.0, 8, 8.0, 10, 0.0));
        }
        return o;
    }

    private static final double[][] HK = k2(0, 0.0, 2, 1.0, 4, 0.9, 6, -0.25, 10, 0.0);
    private static final double[][] GLANCE = k2(0, 0.0, 10, 1.0, 30, 1.0, 42, 0.0);
    private static final double[][] WOB = k2(0, 0.0, 1, 1.0, 2, -1.0, 3, 0.6, 4, 0.0);

    // ------------------------------------------------------------------ the tick ---------------------------------
    /** Advances {@code st} one game tick on {@code in} and writes the pose into {@code out} (the driver's step()). */
    public static void step(State st, Inputs in, Pose out) {
        int k = st.k;
        st.age++;
        // -- cues
        if (in.dying && st.deathT < 0)
            st.deathT = 0;
        boolean cue = in.attackSeq != st.attackSeq;
        st.attackSeq = in.attackSeq;
        if (st.deathT < 0 && st.arriveT < 0) {
            if (st.attackT < 0 && 0 <= in.attackIn && in.attackIn <= EVENT[k]) {
                st.attackT = EVENT[k] - in.attackIn;
                st.variant = (k == 2 && st.cuts % 3 == 2) ? 1 : 0;
            }
            if (cue) {
                if (st.attackT < 0)
                    st.variant = (k == 2 && st.cuts % 3 == 2) ? 1 : 0;
                st.attackT = EVENT[k];
                st.cuts++;
            }
        }
        if (in.hurtTime > st.hurtLast && st.sinceHit >= 6 && st.deathT < 0) {
            st.hitT = 0;
            st.sinceHit = 0;
        }
        st.hurtLast = in.hurtTime;
        // -- locomotion
        double acc = in.speed - st.speedE;
        st.speedE += 0.25 * acc;
        st.flyW += (smooth(st.speedE / CRUISE) - st.flyW) * 0.15;
        double fw = st.flyW;
        double rollT = clamp(in.yawRate * ROLL_PER_YAW, -ROLL_CAP, ROLL_CAP);
        st.roll1 += ROLL_A * (rollT - st.roll1);
        st.roll2 += ROLL_A * (st.roll1 - st.roll2);
        double roll = st.roll2;
        // -- the wing phase: STATE, advanced by 1 / beatTicks; beatTicks eased between states
        double targetBeat = lerp(HOVER_T[k], FLY_T[k], fw) * st.tempo;
        st.beat += (targetBeat - st.beat) * BEAT_EASE;
        double B = st.beat;
        if (st.arriveT >= 0)      // the arrival keys its own wings; the phase lands on 0 at its last tick
            st.phase = frac((st.arriveT - (ARRIVE_LEN - 1)) / B);
        else if (st.glide > 0)
            st.glide--;
        else
            st.phase += 1.0 / B;
        if (st.phase >= 1.0) {
            st.phase -= 1.0;
            st.beats++;
            st.flyBeats = fw > 0.8 ? st.flyBeats + 1 : 0;
            if (st.flyBeats >= GLIDE_AFTER && st.attackT < 0 && Math.abs(rollT) < 1.0) {
                st.glide = GLIDE_TICKS;
                st.flyBeats = 0;
            }
            if (st.beats >= st.glanceAt && st.glanceT < 0) {
                st.glanceT = 0;
                st.glanceYaw = GLANCE_YAW * (st.rand() < 0.5 ? 1.0 : -1.0);
                st.glanceAt = st.beats + GLANCE_MIN + (int) (st.rand() * (GLANCE_MAX - GLANCE_MIN + 1));
            }
        }
        double p = st.phase;
        boolean gl = st.glide > 0;
        st.glideW += ((gl ? 1.0 : 0.0) - st.glideW) * 0.3;
        double gw = st.glideW;
        // -- clip clocks
        int at = st.attackT;
        Attack A = at >= 0 ? attackTracks(k, at, st.variant) : null;
        int s0 = STROKE_S0[k], sl = STROKE_LEN[k];
        if (A != null && at == s0 + sl) {
            st.phase = BOTTOM1;
            st.glide = 0;
            p = st.phase;
        }
        int hit = st.hitT;
        double hk = hit >= 0 ? keys(hit, HK) : 0.0;
        boolean hkOn = hk != 0.0;
        boolean arr = st.arriveT >= 0, dead = st.deathT >= 0;
        int t = st.arriveT, d = st.deathT;
        // arrive_tracks
        double arDy = 0, arScale = 1, arWhite = 0, arAlpha = 1, arBodyRx = 0, arHeadRx = 0, arLegs = 0;
        if (arr) {
            double u = clamp(t / 20.0, 0.0, 1.0);
            arDy = ARRIVE_DROP * (1.0 - u) * (1.0 - u) + keys(t, k2(0, 0.0, 17, 0.0, 23, 1.5, 29, 0.0));
            arScale = 0.85 + 0.15 * smooth(t / 12.0);
            arWhite = 1.0 - smooth((t - 10.0) / 10.0);
            arAlpha = smooth(t / 6.0);
            arBodyRx = keys(t, k2(0, 0.0, 8, 6.0, 16, 10.0, 22, 4.0, 30, 0.0));
            arHeadRx = keys(t, k2(0, -6.0, 20, -10.0, 30, 0.0));
            arLegs = keys(t, k2(0, 0.0, 8, 0.3, 16, 1.0, 22, 1.0, 30, 0.0));
        }
        // death_tracks
        double dDy = 0, dStretch = 1, dWhite = 0, dAlpha = 1, dHaloAlpha = 1, dHeadRx = 0, dArmsOpen = 0, dWingW = 0;
        if (dead) {
            dDy = DEATH_LIFT * smooth((d - 2.0) / 16.0);
            dStretch = 1.0 + 0.06 * smooth((d - 4.0) / 12.0);
            dWhite = smooth((d - 6.0) / 8.0);
            dAlpha = 1.0 - smooth((d - 9.0) / 11.0);
            dHaloAlpha = dAlpha;
            dHeadRx = keys(d, k2(0, 0.0, 3, -12.0, 8, -4.0, 14, 8.0, 20, 10.0));
            dArmsOpen = keys(d, k2(0, 0.0, 5, 1.0));
            dWingW = keys(d, k2(0, 0.0, 2, 0.0, 8, 1.0));
        }
        out.reset();
        // -- body
        double breath = Math.sin(2.0 * Math.PI * (st.beats + p) / BREATH_BEATS);
        double bobAmp = BOB[k] * lerp(1.0, 0.6, fw);
        double bob = bobAmp * bob0(p - LAG_BOB / B) * (1.0 - gw);
        double gth = 2.0 * Math.PI * (GLIDE_TICKS - st.glide) / GLIDE_TICKS;   // the glide's own breath (one wave)
        bob -= GLIDE_BOB * Math.cos(gth) * gw;
        if (arr)
            bob *= smooth((st.arriveT - 24) / 6.0);
        double brake = clamp(-acc / 0.08, 0.0, 1.0);
        double surge = clamp(acc / 0.08, 0.0, 1.0);
        double pitch = lerp(-6.0, FLY_PITCH[k], fw) - 8.0 * surge + 15.0 * brake;
        pitch += (0.5 - 3.5 * Math.cos(2.0 * Math.PI * warp(p - LAG_PITCH / B))) * (1.0 - 0.5 * fw) + 1.0 * breath;
        st.pitch.at(pitch);
        pitch = spring(st.pitch, pitch, 0.3, 0.75);
        double[] body = {pitch, 0.0, roll};
        double[] dmove = {0.0, bob, 0.0};
        double[] head = {-HEAD_COUNTER * (pitch + 6.0) - 0.6 * breath, clamp(in.yawRate * 3.0, -20.0, 20.0), -0.35 * roll};
        if (st.glanceT >= 0) {
            int g = st.glanceT;
            head[1] += st.glanceYaw * keys(g, GLANCE);
            head[0] += -4.0 * keys(g, GLANCE);
            st.glanceT = g < 42 ? g + 1 : -1;
        }
        if (A != null) {
            body[0] += A.bodyRx;
            body[1] += A.bodyRy;
            dmove[1] += A.bodyDy;
            dmove[2] += A.bodyDz;
            head[0] += A.headRx;
            head[1] += A.headRy;
        }
        if (hkOn) {
            body[0] += 20.0 * hk;
            head[0] += 15.0 * keys(hit - 1, HK);
        }
        if (arr) {
            dmove[1] += arDy;
            head[0] += arHeadRx;
            body[0] += arBodyRx;
            out.white = arWhite;
            out.alpha = arAlpha;
            out.haloAlpha = arAlpha;
            out.scale[BODY][0] = out.scale[BODY][1] = out.scale[BODY][2] = arScale;
        }
        if (dead) {
            dmove[1] += dDy;
            head[0] += dHeadRx;
            out.white = dWhite;
            out.alpha = dAlpha;
            out.haloAlpha = dHaloAlpha;
            out.scale[BODY][0] = 1.0;
            out.scale[BODY][1] = dStretch;
            out.scale[BODY][2] = 1.0;
        }
        if (A != null && !Double.isNaN(A.glow))
            out.glow = A.glow;
        // -- wings
        double amp = lerp(1.0, 1.15, fw);
        double sweepAmp = 1.0 - fw;
        double bank = roll / ROLL_CAP;
        for (int si = 0; si < 2; si++) {
            double side = si == 0 ? 1.0 : -1.0;
            double inner = clamp(-side * bank, 0.0, 1.0);
            double outer = clamp(side * bank, 0.0, 1.0);
            double aSide = amp * (1.0 - 0.3 * inner + 0.1 * outer);
            double ampUp = (A != null && !Double.isNaN(A.ampUp)) ? aSide * A.ampUp : Double.NaN;
            double[] w = wingCycle(k, p, B, aSide, sweepAmp, -12.0 * inner, 15.0 * inner, ampUp, 1.0);
            double sup = w[W_SUP] * (1.0 - gw);
            if (gw > 0.001) {
                double[] gp = {8.0 + GLIDE_DIH * Math.sin(gth), 0.0, 0.0, 0.0, -6.0 + 3.0 * Math.sin(2.0 * Math.PI * st.glide / 30.0), 0.0};
                for (int i = 0; i < 6; i++)
                    w[i] = lerp(w[i], gp[i], gw);
                w[W_TIPS] *= 1.0 - gw;
            }
            w[W_ARM + 1] += 20.0 * brake;
            if (A != null && at < s0 + sl) {
                double[] top = wingCycle(k, 0.04, B, aSide, sweepAmp, 0.0, 0.0, ampUp, 1.0);
                if (at < s0) {
                    double tw = A.wingW;
                    for (int i = 0; i < 6; i++)
                        w[i] = lerp(w[i], top[i], tw);
                    w[W_TIPS] = lerp(w[W_TIPS], 0.0, tw);
                    sup *= 1.0 - tw;
                } else {
                    double bot = wingCycle(k, BOTTOM1 - 0.001, B, aSide, sweepAmp)[W_ARM];
                    double ut = (at - s0 + 1.0) / sl;
                    double lf = STROKE_LAG[k][0], lh = STROKE_LAG[k][1];
                    double ea = esClamp(top[W_ARM], bot, ut), ef = esClamp(top[W_ARM], bot, ut - lf / sl),
                        eh = esClamp(top[W_ARM], bot, ut - lh / sl);
                    w[W_ARM] = ea;
                    w[W_ARM + 1] = top[W_ARM + 1] * (1.0 - ut);
                    w[W_FORE] = ef - ea;
                    w[W_FORE + 1] = 0.0;
                    w[W_HAND] = eh - ef;
                    w[W_HAND + 1] = 0.0;
                    w[W_TIPS] = 0.0;
                    sup = 0.0;
                }
                w[W_ARM + 1] += A.cup;
            }
            if (hkOn) {
                w[W_ARM] += 30.0 * hk;
                w[W_TIPS] += 10.0 * hk;
            }
            if (arr) {
                double[] aw = arriveWing(k, st.arriveT, B);
                System.arraycopy(aw, 0, w, 0, 6);
                w[W_TIPS] = aw[W_TIPS];
                sup = aw[W_SUP];
            }
            if (dead) {
                for (int i = 0; i < 6; i++)
                    w[i] = lerp(w[i], COCOON[i], dWingW);
                w[W_TIPS] = lerp(w[W_TIPS], COCOON[W_TIPS], dWingW);
                sup = lerp(sup, SUPINATE, dWingW);
            }
            double handAbs = w[W_ARM] + w[W_FORE] + w[W_HAND];
            Spring ts = (si == 0 ? st.tipsL : st.tipsR).at(handAbs);
            double tipsRel = clamp(spring(ts, handAbs, K_TIPS, C_TIPS) - handAbs, -20.0, 20.0);
            set(out.rot[WING_ARM[si]], 0.0, side * w[W_ARM + 1], side * w[W_ARM]);
            set(out.rot[WING_FORE[si]], sup, -side * w[W_FORE + 1], side * w[W_FORE]);
            set(out.rot[WING_HAND[si]], 0.0, -side * w[W_HAND + 1], side * w[W_HAND]);
            set(out.rot[WING_TIPS[si]], 0.0, -side * w[W_TIPS], side * tipsRel);
            // the pitch cap: the hand, then the tips, twisted back about their flights so none stands edge-on
            double[][] P = mmul(rmat(out.rot[WING_ARM[si]]), rmat(out.rot[WING_FORE[si]]));
            for (int part = 0; part < 2; part++) {
                int bn = part == 0 ? WING_HAND[si] : WING_TIPS[si];
                double[][] L = rmat(out.rot[bn]);
                double[][] cubes = FEATHERS[k][part];
                double[][][] Rc = new double[cubes.length][][];
                for (int c = 0; c < cubes.length; c++)
                    Rc[c] = rmat(cubes[c][0], side * cubes[c][1], side * cubes[c][2]);
                double[][] L2 = capPitch(P, L, Rc);
                if (L2 != null) {
                    double[] e = euler(L2);
                    set(out.rot[bn], e[0], e[1], e[2]);
                    L = L2;
                }
                P = mmul(P, L);
            }
        }
        // -- arms and props
        double[][] rest = REST[k];
        double[] laD = {0.0, 0.0, 0.0}, raD = {0.0, 0.0, 0.0};
        double breathArm = 1.5 * breath;
        if (A != null && A.la != null)
            laD = A.la.clone();
        if (dead) {
            double o = dArmsOpen;
            laD = new double[]{laD[0] + 20.0 * o, laD[1], laD[2] + 30.0 * o};
            raD = new double[]{raD[0] + 20.0 * o, raD[1], raD[2] - 30.0 * o};
        }
        if (hkOn) {
            if (k == 2)
                laD = new double[]{laD[0] + 30.0 * hk, laD[1], laD[2]};
            else if (k == 0)
                laD = new double[]{laD[0], laD[1], laD[2] - 20.0 * hk};
            else
                laD = new double[]{laD[0] - 8.0 * hk, laD[1], laD[2] + 6.0 * hk};
            raD = new double[]{raD[0] - 8.0 * hk, raD[1], raD[2] - 6.0 * hk};
        }
        laD = new double[]{laD[0] + breathArm, laD[1], laD[2] + 0.5 * fw * 10.0};
        raD = new double[]{raD[0] + breathArm, raD[1], raD[2] - 0.5 * fw * 10.0};
        double[][][] armR = new double[2][][], foreR = new double[2][][];
        double[] phi = new double[1];
        for (int si = 0; si < 2; si++) {
            double[] dl = si == 0 ? laD : raD;
            double[] ik = A == null ? null : si == 0 ? A.ikL : A.ikR;
            double[][] Ra;
            double ph;
            if (ik != null) {
                Ra = ikArm(k, si, ik, si == 0 ? A.poleL : A.poleR, phi);
                Ra = mmul(rmat(dl), Ra);
                ph = phi[0];
            } else {
                Ra = mmul(rmat(dl), rmat(rest[ARM_B[si]]));
                ph = rest[FORE_B[si]][0];
            }
            armR[si] = Ra;
            foreR[si] = rmat(ph, 0.0, 0.0);
            deltaOnRest(rest[ARM_B[si]], Ra, out.rot[ARM_B[si]]);
            set(out.rot[FORE_B[si]], wrap(ph - rest[FORE_B[si]][0]), 0.0, 0.0);
        }
        double[][] R_la = armR[0], R_lf = foreR[0], R_ra = armR[1], R_rf = foreR[1];
        double[][] R_body = rmat(body);
        if (k == 0) {
            double[][] R_chain_w0 = mmul(mmul(rmat(rest[ARM_B[0]]), rmat(rest[FORE_B[0]])), rmat(rest[CHAIN]));
            double pv = or(st.prevPitchV, 0.0);
            double pitchV = body[0] - or(st.prevPitch, body[0]);
            st.prevPitch = body[0];
            st.prevPitchV = pitchV;
            double bvv = bob - or(st.prevBob, bob);
            double drive = -0.35 * (pitchV - pv) - 0.9 * (bvv - or(st.prevBobV, bvv)) + (A != null ? A.kick : 0.0);
            drive += hit == 0 ? 6.0 : 0.0;
            st.prevBob = bob;
            st.prevBobV = bvv;
            Spring sw = st.pendX.at(0.0);
            double ax0 = sw.v;
            spring(sw, -18.0 * fw, K_PEND, C_PEND, drive);
            sw.x = clamp(sw.x, -40.0, 40.0);
            Spring sz = st.pendZ.at(0.0);
            double az0 = sz.v;
            spring(sz, 0.6 * roll, K_PEND, C_PEND, hit == 0 ? 3.0 : 0.0);
            double[][] R_sw = rmat(sw.x, 0.0, sz.x);
            double[][] R_par = mmul(R_body, mmul(R_la, R_lf));
            deltaOnRest(rest[CHAIN], mmul(mT(R_par), mmul(R_sw, R_chain_w0)), out.rot[CHAIN]);
            Spring lx = st.lantX.at(0.0), lz = st.lantZ.at(0.0);
            spring(lx, 0.0, K_LANT, C_LANT, -0.9 * (sw.v - ax0));
            spring(lz, 0.0, K_LANT, C_LANT, -0.9 * (sz.v - az0));
            set(out.rot[LANTERN], clamp(lx.x, -30.0, 30.0), 0.0, clamp(lz.x, -30.0, 30.0));
        }
        if (k == 1)
            archer(A, at, rest, R_la, R_lf, R_ra, R_rf, out);
        if (k == 2) {
            double[][] W0 = mmul(mmul(rmat(rest[ARM_B[1]]), rmat(rest[FORE_B[1]])), rmat(rest[SWORD]));
            double[][] W_carry = mmul(rmat(SWORD_CARRY), W0);
            double[] D0 = mv(rmat(SWORD_CARRY), SWORD_BLADE);
            double[][] W;
            if (A != null) {
                double[] hd = A.planeHd, n = A.planeN, bl = A.blade;
                double th = Math.toDegrees(Math.atan2(dot(cross(hd, bl), n), dot(hd, bl)));
                double[][] R_cut = axisMat(n, th);
                W = mmul(align(mv(R_cut, hd), bl), mmul(R_cut, mmul(align(D0, hd), W_carry)));
            } else {
                W = W_carry;
            }
            deltaOnRest(rest[SWORD], mmul(mT(mmul(R_ra, R_rf)), W), out.rot[SWORD]);
        }
        // -- cloth, legs, halo
        double vy = bob - or(st.prevBobC, bob);
        st.prevBobC = bob;
        Spring csx = st.clothX.at(body[0]), csz = st.clothZ.at(body[2]);
        spring(csx, body[0], K_CLOTH, C_CLOTH);
        spring(csz, body[2], K_CLOTH, C_CLOTH);
        double sx = 1.5 * clamp(csx.x - body[0], -30.0, 30.0) - 25.0 * fw;
        double sz_ = clamp(csz.x - body[2], -25.0, 25.0) * 0.7;
        double flare = spring(st.flare.at(0.0), clamp(-vy * 9.0, -6.0, 6.0) + 6.0 * hk, K_CLOTH, C_CLOTH);
        if (k == 0) {
            double[][] R_sk = rmat(sx, 0.0, sz_);
            Spring hx = st.hemX.at(sx), hz = st.hemZ.at(sz_);
            spring(hx, sx, K_HEM, C_HEM);
            spring(hz, sz_, K_HEM, C_HEM);
            for (int i = 0; i < 4; i++) {
                double[] f = FLARE[i];
                set(out.rot[SKIRT_B[i]], sx + f[0] * flare, 0.0, sz_ + f[1] * flare);
                double[] dd = mv(R_sk, SKIRT[i]);
                set(out.move[SKIRT_B[i]], dd[0] - SKIRT[i][0], dd[1] - SKIRT[i][1], dd[2] - SKIRT[i][2]);
            }
            for (int i = 0; i < 4; i++) {
                double[] f = FLARE[i];
                set(out.rot[HEM_B[i]], hx.x + 1.6 * f[0] * Math.max(0.0, flare), 0.0,
                    hz.x + 1.6 * f[1] * Math.max(0.0, flare));
                double[] dd = mv(R_sk, HEM[i]);
                set(out.move[HEM_B[i]], dd[0] - HEM[i][0], dd[1] - HEM[i][1], dd[2] - HEM[i][2]);
            }
            double a = sx * (sx > 0 ? 1.1 : 0.0);
            set(out.rot[STOLE_FRONT], a + Math.max(0.0, flare) * 0.6, 0.0, 0.5 * sz_);
            a = sx * (sx > 0 ? 0.0 : 1.1);
            set(out.rot[STOLE_BACK], a + Math.min(0.0, -flare) * 0.6, 0.0, 0.5 * sz_);
        } else {
            for (int i = 0; i < 4; i++) {
                double[] f = FLARE[i];
                double tr = i == 0 ? 0.6 : i == 1 ? (k == 1 ? 0.3 : 1.0) : 1.0;
                set(out.rot[TABARD_B[i]], sx * tr + f[0] * flare, 0.0, sz_ + f[1] * flare);
            }
            for (int si = 0; si < 2; si++) {
                double side = si == 0 ? 1.0 : -1.0;
                Spring ls = (si == 0 ? st.legL : st.legR).at(body[0]);
                spring(ls, body[0], K_LEG, C_LEG);
                double lag = clamp(ls.x - body[0], -25.0, 25.0);
                double kick = 0.8 * flare;
                double reach = arr ? arLegs : 0.0;
                set(out.rot[THIGH_B[si]], lag - 25.0 * fw - 6.0 * hk + kick * side * 0.3 + 22.0 * reach, 0.0,
                    side * 2.0 * fw);
                set(out.rot[SHIN_B[si]], 0.5 * lag - 10.0 * fw - 6.0 * hk + 8.0 * reach, 0.0, 0.0);
            }
        }
        Spring[] hs = {st.haloX.at(head[0]), st.haloY.at(head[1]), st.haloZ.at(head[2])};
        for (int i = 0; i < 3; i++)
            spring(hs[i], head[i], K_HALO, C_HALO);
        double wob = hit >= 2 ? 6.0 * keys(hit - 2, WOB) : 0.0;
        double[] haloR = {HALO_PITCH * hs[0].x, hs[1].x, HALO_PITCH * hs[2].x + wob};
        double[][] Hm = rmat(haloR);
        double[] off = HALO_OFF[k];
        double[] dd = mv(Hm, off);
        double breathLate = 0.3 * Math.sin(2.0 * Math.PI * ((st.beats + p) / BREATH_BEATS - 0.1));
        set(out.rot[BODY], body[0], body[1], body[2]);
        set(out.rot[HEAD], head[0], head[1], head[2]);
        set(out.rot[HALO], haloR[0], haloR[1], haloR[2]);
        set(out.move[BODY], dmove[0], dmove[1], dmove[2]);
        set(out.move[HALO], dd[0] - off[0], dd[1] - off[1] + breathLate, dd[2] - off[2]);
        // -- clocks
        if (st.attackT >= 0) {
            st.attackT++;
            if (st.attackT >= ATTACK_LEN[k])
                st.attackT = -1;
        }
        if (st.hitT >= 0) {
            st.hitT++;
            if (st.hitT > HIT_LEN)
                st.hitT = -1;
        }
        st.sinceHit++;
        if (st.arriveT >= 0) {
            st.arriveT++;
            if (st.arriveT >= ARRIVE_LEN) {
                st.arriveT = -1;
                st.phase = 0.0;
            }
        }
        if (st.deathT >= 0 && st.deathT < DEATH_LEN)
            st.deathT++;
    }

    private static double esClamp(double top, double bot, double u) {
        u = clamp(u, 0.0, 1.0);
        return top + (bot - top) * u * u * (2.2 - 1.2 * u);
    }

    private static double or(double v, double dflt) {
        return Double.isNaN(v) ? dflt : v;
    }

    private static void set(double[] o, double x, double y, double z) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
    }

    /** The archer's bow aimed (drawn), its string halves to the draw hand, the arrow on the hand. */
    private static void archer(Attack A, int at, double[][] rest, double[][] R_la, double[][] R_lf, double[][] R_ra,
                               double[][] R_rf, Pose out) {
        int k = 1;
        double bw = A != null ? A.bow : 0.0;
        double[] aim = A != null ? A.aim : AIM;
        double[][] W0 = mmul(mmul(rmat(rest[ARM_B[0]]), rmat(rest[FORE_B[0]])), rmat(rest[BOW]));
        double[] up = {0.0, 1.0, 0.0};
        double du = dot(up, aim);
        double[] upT = unit(new double[]{up[0] - du * aim[0], up[1] - du * aim[1], up[2] - du * aim[2]});
        double[][] R_t = mmul(frame(upT, aim), mT(frame(BOW_LONG, BOW_FACE)));
        double ang = Math.toDegrees(Math.acos(clamp((R_t[0][0] + R_t[1][1] + R_t[2][2] - 1.0) / 2.0, -1.0, 1.0)));
        double[] ax = {R_t[2][1] - R_t[1][2], R_t[0][2] - R_t[2][0], R_t[1][0] - R_t[0][1]};
        double[][] R_b = ang > 1e-6 && dot(ax, ax) > 1e-12 ? axisMat(ax, bw * ang) : rmat(0.0, 0.0, 0.0);
        double rk = A != null ? A.recoil : 0.0;
        R_b = mmul(axisMat(cross(upT, aim), -6.0 * rk), R_b);
        double[][] R_par = mmul(R_la, R_lf);
        double[][] L_bow = mmul(mT(R_par), mmul(R_b, W0));
        deltaOnRest(rest[BOW], L_bow, out.rot[BOW]);
        double[] Sl = ARM[k][0][0], El = ARM[k][0][1], Gl = ARM[k][0][2];
        double[] Sr = ARM[k][1][0], Er = ARM[k][1][1], Gr = ARM[k][1][2];
        double[] hr = add(Er, mv(R_rf, sub(Gr, Er)));
        double[] hand = add(Sr, mv(R_ra, sub(hr, Sr)));
        double[] top = xBow(STRING_TIP_TOP, L_bow, Gl, El, Sl, R_lf, R_la);
        double[] bot = xBow(STRING_TIP_BOT, L_bow, Gl, El, Sl, R_lf, R_la);
        double[] mid = {0.5 * (top[0] + bot[0]), 0.5 * (top[1] + bot[1]), 0.5 * (top[2] + bot[2])};
        double sw = A != null ? A.string : 0.0;
        double[] tgt = {mid[0] + sw * (hand[0] - mid[0]), mid[1] + sw * (hand[1] - mid[1]), mid[2] + sw * (hand[2] - mid[2])};
        double[][] R_pb = mmul(R_par, L_bow);
        for (int i = 0; i < 2; i++) {
            int b = i == 0 ? STRING_TOP : STRING_BOT;
            double[] tip = i == 0 ? top : bot;
            double[][] W_cur = mmul(R_pb, rmat(rest[b]));
            double[] cur = mv(W_cur, new double[]{0.0, -1.0, 0.0});
            double[] want = sub(tgt, tip);
            deltaOnRest(rest[b], mmul(mT(R_pb), mmul(align(cur, want), W_cur)), out.rot[b]);
            double[] dm = sub(mid, tip);
            double L0 = Math.sqrt(dot(dm, dm));
            set(out.scale[b], 1.0, Math.sqrt(dot(want, want)) / L0, 1.0);
        }
        double[] grip = xFore(Gl, El, Sl, R_lf, R_la);
        double[] adir = (A != null && A.bow > 0.0) ? aim : sub(grip, hand);
        double[][] L_arrow = mmul(mT(mmul(R_ra, R_rf)), align(new double[]{0.0, 0.0, -1.0}, adir));
        // hidden, the arrow rests in the hand in the pose it appears in (t3), easing there from the shot over 8 ticks
        double vis = (A != null && A.arrow) ? 1.0 : (A != null && at > 15) ? 1.0 - smooth((at - 15.0) / 8.0) : 0.0;
        if (vis < 1.0)
            L_arrow = rotLerp(arrowStow(), L_arrow, vis);
        deltaOnRest(rest[ARROW], L_arrow, out.rot[ARROW]);
        if (!(A != null && A.arrow))
            out.hidden[ARROW] = true;
    }

    private static double[] xFore(double[] q, double[] El, double[] Sl, double[][] R_lf, double[][] R_la) {
        double[] r = add(El, mv(R_lf, sub(q, El)));
        return add(Sl, mv(R_la, sub(r, Sl)));
    }

    private static double[] xBow(double[] q, double[][] L_bow, double[] Gl, double[] El, double[] Sl, double[][] R_lf,
                                 double[][] R_la) {
        return xFore(add(Gl, mv(L_bow, sub(q, Gl))), El, Sl, R_lf, R_la);
    }

    // ------------------------------------------------------------------ the passes' fade (pure, tested) ------------
    /** Indices into {@link #passFactors}' output: the body, the halo, the lantern, the lantern's extra light. */
    public static final int F_BODY = 0, F_HALO = 1, F_LANTERN = 2, F_LANTERN_EXTRA = 3;

    /**
     * How strongly each group of parts draws in one pass of the body (the paint, the team mask, the glow): the body at
     * the pose's alpha, the halo at its own halo alpha, the lantern with the body - and in the glow pass also at the
     * kind's glow (the Mender's lantern brightens x1.6 on the heal): a factor over 1 draws the lantern once at full and
     * once more additively with the rest ({@code F_LANTERN_EXTRA}).
     */
    public static void passFactors(Pose pose, boolean glowPass, double[] out) {
        double a = pose.alpha, g = glowPass ? pose.glow : 1.0;
        out[F_BODY] = a;
        out[F_HALO] = pose.haloAlpha;
        out[F_LANTERN] = a * Math.min(1.0, g);
        out[F_LANTERN_EXTRA] = a * Math.max(0.0, g - 1.0);
    }

    /** The pose fades some part (arriving, dying): its passes draw translucent. */
    public static boolean translucent(Pose pose) {
        return pose.alpha < 0.999 || pose.haloAlpha < 0.999;
    }

    // ------------------------------------------------------------------ onto the model ----------------------------

    /**
     * One model part as {@link #apply} writes it, in ModelPart's own terms: radians (MC signs), model px (y down) and
     * the part's scale. A client adapter (AngelParts) implements it over ModelPart.
     */
    public interface Part {
        void addRotation(float xRad, float yRad, float zRad);

        void addOffset(float x, float y, float z);

        void setScale(float x, float y, float z);

        void setVisible(boolean visible);
    }

    /** The resolved parts (null where the body has no such bone). */
    public static final class Bones {
        final Part[] parts = new Part[N];

        public Bones(Function<String, ? extends Part> lookup) {
            for (int b = 0; b < N; b++)
                parts[b] = lookup.apply(BONES[b]);
        }
    }

    private static final float D2R = (float) (Math.PI / 180.0);

    /**
     * Writes a pose onto the parts (after the model's restPose). The sign mapping is tools/dsl_to_game.py's: a DSL
     * rotation (x, y, z) degrees goes on as (-x, +y, -z) radians, a DSL move (x, y, z) px as (+x, -y, +z) (model y is
     * down); the scale is set as is; a hidden bone is not drawn.
     */
    public static void apply(Pose pose, Bones bones) {
        for (int b = 0; b < N; b++) {
            Part p = bones.parts[b];
            if (p == null)
                continue;
            double[] r = pose.rot[b], o = pose.move[b], s = pose.scale[b];
            p.addRotation((float) -r[0] * D2R, (float) r[1] * D2R, (float) -r[2] * D2R);
            p.addOffset((float) o[0], (float) -o[1], (float) o[2]);
            p.setScale((float) s[0], (float) s[1], (float) s[2]);
            p.setVisible(!pose.hidden[b]);
        }
    }
}
