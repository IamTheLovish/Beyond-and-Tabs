package com.solegendary.reignofnether.unit.modelling.animations;

import java.util.Arrays;
import java.util.function.Function;

/**
 * STUB until the green commit (step() holds the rest pose). The Sunforged angels' rig (the Angel Caller's three summons: Mender Angel, Seraph Archer, Sword Seraph): a 1:1 port
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
    static final double FOLD_TIPS = 15.0;
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

    // ------------------------------------------------------------------ the tick (STUB) --------------------------
    /** STUB (tests first): holds the rest pose; the port of the driver's step() lands with the green commit. */
    public static void step(State st, Inputs in, Pose out) {
        st.age++;
        out.reset();
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
