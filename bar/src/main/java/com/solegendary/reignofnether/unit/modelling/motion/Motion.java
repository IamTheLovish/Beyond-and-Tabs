package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.unit.modelling.animations.Life;
import net.minecraft.util.Mth;

import static com.solegendary.reignofnether.unit.modelling.motion.AnimState.*;

/**
 * The living army's pose (design/living_army.md, section 2): one unit's motion snapshot ({@link AnimState}) to offsets
 * for eight bones, added on top of whatever the body already does. Pure and renderer-agnostic - it writes numbers, not
 * ModelParts ({@link MotionPass} is the vanilla adapter; an instanced renderer would bring its own) - and it reads the
 * springs between their last two ticks, so the same frame posed twice (a shader pack's shadow pass, then the view)
 * gives the same bones.
 *
 * Semantic convention of {@code out} (per bone {@link #CH} floats): RX radians, + = pitched forward; RY radians, + =
 * turned to the unit's right; RZ radians, + = rolled to the unit's right; TX px + = to the right, TY px + = up, TZ px
 * + = forward. ROOT is the whole body about the feet. A hanging limb (an arm, a leg, the crossed arms) swings its free
 * end: RX + = the hand or foot forward, RZ + = the hand or foot to the unit's right.
 *
 * Every behaviour's size = its slider (0..2) x {@link ZoomGain} x the unit's own amplitude ({@link Desync#amp}); its
 * timing runs at the unit's own tempo ({@link Desync#tempo}); every channel is clamped to {@link #limit}.
 */
public final class Motion {

    private Motion() {}

    public static final int ROOT = 0, BODY = 1, HEAD = 2, ARM_R = 3, ARM_L = 4, LEG_R = 5, LEG_L = 6, ARMS = 7;
    public static final int BONES = 8, CH = 6, OUT = BONES * CH;
    public static final int RX = 0, RY = 1, RZ = 2, TX = 3, TY = 4, TZ = 5;
    /**
     * The look's frame input ({@link #pose}'s {@code look}, filled by MotionPass): how this body looks
     * ({@link #LOOK_NONE}, {@link #LOOK_FULL} - a vanilla body whose model put the whole head yaw and pitch on the head -
     * or {@link #LOOK_TWIST} - a rig that aims its own head, whose chest turns only with an aim), the body's yaw, the
     * head yaw relative to the body as the model already put it on, and the pitch, degrees.
     */
    public static final int LOOK_MODE = 0, LOOK_BODY_YAW = 1, LOOK_NET_YAW = 2, LOOK_PITCH = 3, LOOK_LEN = 4;
    public static final float LOOK_NONE = 0f, LOOK_FULL = 1f, LOOK_TWIST = 2f;
    /** The chest's share of a look at rest and when aiming (a ranged unit drawing on its target), and its limits. */
    static final float CHEST_YAW_SHARE = 0.15f, CHEST_YAW_SHARE_AIM = 0.45f, CHEST_PITCH_SHARE = 0.1f,
        CHEST_PITCH_SHARE_AIM = 0.3f, CHEST_YAW_LIMIT = 25f, CHEST_PITCH_LIMIT = 15f, HEAD_YAW_LOOK = 85f,
        HEAD_PITCH_LOOK = 40f;
    private static final float[] SHARES = new float[2], LIMITS = new float[2], CHAIN = new float[2];
    /**
     * The gait bridge's frame input ({@link #pose}'s {@code walk}, filled by MotionPass for a biped on vanilla's
     * one-bone legs - {@link BoneMap#strides}): vanilla's walk clock (WalkAnimationState's position and speed, as the
     * model's setupAnim got them) and the angle the model gave each leg, in this class's convention (+ = foot forward).
     */
    public static final int WALK_POS = 0, WALK_SPEED = 1, WALK_R = 2, WALK_L = 3, WALK_LEN = 4;
    /**
     * The bridge's stride: the planted foot sweeps back at a steady rate and the swinging one comes through eased
     * (Gait's construction for the quadrupeds), reaching STRIDE_RAD either side at vanilla's own cadence - so the arms,
     * which vanilla swings on the same clock, stay in step. 0.62 rad is where the planted foot keeps ~3/4 of the ground
     * speed at every speed (the whole stride in vanilla's cycle would need 0.9 rad, a 52 degree split).
     */
    static final float STRIDE_RAD = 0.62f, STEP_LIFT_PX = 1.6f, HIP_SWAY_PX = 0.35f, WALK_FULL = 0.2f,
        LEG_PX = 12f, CADENCE = 0.6662f;

    /** Slider indices (the config's motion_* percentages / 100). */
    public static final int BREATH = 0, WEIGHT = 1, FIDGET = 2, LEAN = 3, ACCEL = 4, FLINCH = 5, SECONDARY = 6;

    private static final float D = Mth.DEG_TO_RAD;
    /** Joint limits per bone: rotation (degrees) and translation (px). */
    private static final float[] ROT_LIMIT_DEG = {15f, 25f, 40f, 40f, 40f, 70f, 70f, 40f};   // legs: the gait's correction
    private static final float[] MOVE_LIMIT_PX = {6f, 3f, 2f, 2f, 2f, 2f, 2f, 2f};
    private static final float HEAD_YAW_LIMIT_DEG = 75f;

    /** The clamp of one channel: radians for RX..RZ, px for TX..TZ. */
    public static float limit(int bone, int ch) {
        if (ch >= TX)
            return MOVE_LIMIT_PX[bone];
        if (bone == HEAD && ch == RY)
            return HEAD_YAW_LIMIT_DEG * D;
        return ROT_LIMIT_DEG[bone] * D;
    }

    private static void add(float[] out, int bone, int ch, float v) {
        out[bone * CH + ch] += v;
    }

    /**
     * @param s, b       the slab and the unit's slot base
     * @param id         the entity id (tempo, amplitude, phase)
     * @param ageF       the entity's age in ticks with the partial tick (breath, fidget envelopes)
     * @param partial    the partial tick, for the springs
     * @param zoom       the RTS camera's zoom (orthoview); anything at or under 20 reads as up close
     * @param sliders    the seven behaviour sliders, 0..2
     * @param flinchOnly a keyframe-clip unit: breath and flinch / stagger only
     */
    public static void pose(float[] s, int b, int id, float ageF, float partial, float zoom, float[] sliders,
                            boolean flinchOnly, float[] out) {
        pose(s, b, id, ageF, partial, zoom, sliders, flinchOnly, null, out);
    }

    /**
     * {@link #pose} with the look: {@code look} is the frame's {@link #LOOK_LEN} floats (null or LOOK_NONE: no look).
     * The look solver ({@link Life#look}) splits where the head looks - its spring, {@link AnimState#SP_LOOK_YAW} -
     * between the chest and the head: the chest turns its share (more when aiming), the head the rest, so the head
     * still ends on the target and arrives there late. On a vanilla body the arms keep the aim the model gave them.
     * Never scaled by the zoom or the unit's amplitude: it points at a real thing.
     */
    public static void pose(float[] s, int b, int id, float ageF, float partial, float zoom, float[] sliders,
                            boolean flinchOnly, float[] look, float[] out) {
        pose(s, b, id, ageF, partial, zoom, sliders, flinchOnly, look, null, out);
    }

    /**
     * {@link #pose} with the look and the gait bridge: {@code walk} is the frame's {@link #WALK_LEN} floats (null: the
     * legs keep the model's walk). Like the look, the gait is never scaled by a slider, the zoom or the unit's
     * amplitude: it puts real feet on real ground.
     */
    public static void pose(float[] s, int b, int id, float ageF, float partial, float zoom, float[] sliders,
                            boolean flinchOnly, float[] look, float[] walk, float[] out) {
        for (int i = 0; i < OUT; i++)
            out[i] = 0f;
        float amp = Desync.amp(id);   // the tempo is in the slot: the breath phase turns at it (Kinematics.step)

        // breath: the chest swells (a small pitch back), the shoulders rise, the neck follows a beat later
        float gBreath = sliders[BREATH] * ZoomGain.gain(zoom, ZoomGain.K_BREATH) * amp;
        if (gBreath != 0f) {
            float ex = Mth.clamp(s[b + EXERTION], 0f, 1f);
            float size = Mth.lerp(ex, 1.2f, 2.6f) * gBreath;
            // the phase is state (Kinematics.step): a change of exertion changes the breath's speed, never jumps it
            float phase = Life.blendPhase(s[b + BREATH_PREV], s[b + BREATH_PHASE], partial);
            float br = Life.breathAt(phase), neck = Life.breathAt(phase - 0.08f);
            add(out, BODY, RX, -br * size * D);
            add(out, ARM_R, TY, br * 0.25f * size);
            add(out, ARM_L, TY, br * 0.25f * size);
            add(out, HEAD, RX, -neck * 0.6f * size * D);
        }

        // flinch and stagger: the torso bends away from the blow, the head snaps further, the arms jerk; a heavy blow
        // rocks the whole body back away from it, as if half a step
        float gFlinch = sliders[FLINCH] * ZoomGain.gain(zoom, ZoomGain.K_FLINCH) * amp;
        if (gFlinch != 0f) {
            float fx = Life.springAt(s, b + SP_FLINCH_X, partial) * gFlinch;
            float fz = Life.springAt(s, b + SP_FLINCH_Z, partial) * gFlinch;
            add(out, BODY, RX, fx * D);
            add(out, BODY, RZ, fz * D);
            add(out, HEAD, RX, fx * 0.5f * D);
            add(out, HEAD, RZ, fz * 0.4f * D);
            add(out, ARM_R, RX, -fx * 0.8f * D);
            add(out, ARM_L, RX, -fx * 0.8f * D);
            add(out, ARMS, RX, -fx * 0.5f * D);
            float st = Life.springAt(s, b + SP_STAGGER, partial) * gFlinch;
            float dir = s[b + HIT_DIR];
            float c = Mth.cos(dir), sn = Mth.sin(dir);
            add(out, ROOT, RX, -st * c * 0.8f * D);
            add(out, ROOT, RZ, -st * sn * 0.8f * D);
            add(out, ROOT, TZ, -st * c * 0.3f);
            add(out, ROOT, TX, -st * sn * 0.3f);

            // contact recoil: the unit's own blow landed - the weapon stops on the target, the arms and torso jolt
            // back and shudder still (Kinematics.onContact, the same frame as the target's flinch)
            float rc = Life.springAt(s, b + SP_RECOIL, partial) * gFlinch;
            add(out, ARM_R, RX, -rc * 1.2f * D);
            add(out, ARM_L, RX, -rc * 0.8f * D);
            add(out, ARMS, RX, -rc * 0.8f * D);
            add(out, BODY, RX, -rc * 0.45f * D);
            add(out, HEAD, RX, -rc * 0.3f * D);
        }

        if (look != null && look[LOOK_MODE] != LOOK_NONE)
            look(s, b, partial, look, out);

        if (!flinchOnly && walk != null)
            gait(walk, out);

        if (!flinchOnly) {
            float standing = Life.smooth(1f - s[b + SPEED] / 0.05f);

            float gWeight = sliders[WEIGHT] * ZoomGain.gain(zoom, ZoomGain.K_WEIGHT) * amp * standing;
            if (gWeight != 0f) {
                float w = Life.springAt(s, b + SP_WEIGHT, partial) * gWeight;
                add(out, ROOT, TX, w * 0.6f);
                add(out, BODY, RZ, -w * 2f * D);
                add(out, HEAD, RZ, w * 1.5f * D);
                add(out, LEG_R, RZ, -w * 1.5f * D);   // the hips go over, the feet stay where they stand
                add(out, LEG_L, RZ, -w * 1.5f * D);
            }

            // idle fidgets (Idle): a grip adjust, a shoulder roll; the head follows the glance / look-around spring.
            // A glance aims at a real thing, so it is never scaled up by the zoom or the unit's amplitude.
            float gFidget = sliders[FIDGET] * ZoomGain.gain(zoom, ZoomGain.K_FIDGET) * amp * standing;
            if (gFidget != 0f) {
                int kind = (int) s[b + IDLE_KIND];
                float env = Idle.fidget(s, b, ageF) * gFidget;
                float p = (ageF - s[b + IDLE_START]) / Math.max(1f, s[b + IDLE_LEN]);
                if (kind == Idle.GRIP) {
                    add(out, ARM_R, RX, env * 7f * D);
                    add(out, ARM_R, RZ, env * 4f * Mth.sin(p * Mth.TWO_PI * 2f) * D);
                    add(out, ARMS, RX, env * 4f * D);
                } else if (kind == Idle.SHOULDER) {
                    add(out, ARM_R, TY, env * 0.8f);
                    add(out, ARM_L, TY, env * 0.5f);
                    add(out, ARM_L, RZ, -env * 5f * D);
                    add(out, BODY, RZ, env * 2f * D);
                }
                float aim = Math.min(1f, sliders[FIDGET]) * standing;
                add(out, HEAD, RY, Life.springAt(s, b + SP_HEAD_YAW, partial) * aim * D);
                add(out, HEAD, RX, Life.springAt(s, b + SP_HEAD_PITCH, partial) * aim * D);
            }

            float gLean = sliders[LEAN] * ZoomGain.gain(zoom, ZoomGain.K_LEAN) * amp;
            float lean = Life.springAt(s, b + SP_LEAN, partial);
            add(out, ROOT, RZ, lean * gLean * D);

            float gAccel = sliders[ACCEL] * ZoomGain.gain(zoom, ZoomGain.K_ACCEL) * amp;
            float pitch = Life.springAt(s, b + SP_PITCH, partial);
            add(out, ROOT, RX, pitch * gAccel * D);

            // secondary: the arms lag a start and swing on a stop, splay a little in a turn; the head holds level
            float gSec = sliders[SECONDARY] * ZoomGain.gain(zoom, ZoomGain.K_SECONDARY) * amp;
            if (gSec != 0f) {
                float arms = Life.springAt(s, b + SP_ARMS, partial) * gSec;
                add(out, ARM_R, RX, arms * D);
                add(out, ARM_L, RX, arms * D);
                add(out, ARMS, RX, arms * 0.6f * D);
                add(out, ARM_R, RZ, -lean * 0.3f * gSec * D);   // out of the turn
                add(out, ARM_L, RZ, -lean * 0.3f * gSec * D);
                add(out, HEAD, RX, -pitch * 0.5f * gSec * D);
                add(out, HEAD, RZ, -lean * 0.4f * gSec * D);
            }
        }

        clamp(out);
    }

    /**
     * The gait bridge on vanilla's one-bone legs: each leg is set to the bridge's stride (the model's angle taken off,
     * the bridge's put on); the hips drop by what the planted leg's tilt lifts them (so its sole stays on the ground
     * instead of both soles rising at every stride's end), the swinging leg is drawn up off the ground, and the hips
     * ride over the planted foot. Phase 0 = the right foot back: vanilla's own (HumanoidModel's right leg is
     * cos(position x 0.6662) in part space, the foot forward at the half).
     */
    static void gait(float[] walk, float[] out) {
        float w = Life.smooth(Mth.clamp(walk[WALK_SPEED] / WALK_FULL, 0f, 1f));
        if (!(w > 0f))
            return;
        float p = walk[WALK_POS] * CADENCE / Mth.TWO_PI;
        p -= Mth.floor(p);
        float pl = p < 0.5f ? p + 0.5f : p - 0.5f;
        float r = stride(p) * w, l = stride(pl) * w;
        add(out, LEG_R, RX, r - walk[WALK_R]);
        add(out, LEG_L, RX, l - walk[WALK_L]);
        add(out, LEG_R, TY, lift(p) * STEP_LIFT_PX * w);
        add(out, LEG_L, TY, lift(pl) * STEP_LIFT_PX * w);
        add(out, ROOT, TY, -LEG_PX * (1f - Mth.cos(p >= 0.5f ? r : l)));
        add(out, ROOT, TX, -Mth.sin(p * Mth.TWO_PI) * HIP_SWAY_PX * w);   // + = right: over the right foot in its stance
    }

    /** A leg's angle at phase p (0 back, the swing to 0.5 forward eased, the stance back at a steady sweep). */
    static float stride(float p) {
        float x = p < 0.5f ? -1f + 2f * Life.smooth(p * 2f) : 1f - 4f * (p - 0.5f);
        return (float) Math.asin(x * Mth.sin(STRIDE_RAD));   // the foot moves linearly: the angle is its arcsine
    }

    /** 0..1 of the step's lift: an arch through the swing, nothing in the stance. */
    static float lift(float p) {
        return p < 0.5f ? Mth.sin(p * 2f * Mth.PI) : 0f;
    }

    private static void look(float[] s, int b, float partial, float[] look, float[] out) {
        boolean full = look[LOOK_MODE] == LOOK_FULL;
        float aim = Mth.clamp(s[b + AIM], 0f, 1f);
        float ly = Mth.clamp(Mth.wrapDegrees(Life.springAt(s, b + SP_LOOK_YAW, partial) - look[LOOK_BODY_YAW]),
            -HEAD_YAW_LOOK, HEAD_YAW_LOOK);
        float lp = Mth.clamp(Life.springAt(s, b + SP_LOOK_PITCH, partial), -HEAD_PITCH_LOOK, HEAD_PITCH_LOOK);
        SHARES[0] = full ? Mth.lerp(aim, CHEST_YAW_SHARE, CHEST_YAW_SHARE_AIM) : aim * CHEST_YAW_SHARE_AIM;
        LIMITS[0] = CHEST_YAW_LIMIT;
        LIMITS[1] = HEAD_YAW_LOOK;
        Life.look(ly, SHARES, LIMITS, 2, CHAIN);
        float cy = CHAIN[0], hy = CHAIN[1];
        SHARES[0] = full ? Mth.lerp(aim, CHEST_PITCH_SHARE, CHEST_PITCH_SHARE_AIM) : aim * CHEST_PITCH_SHARE_AIM;
        LIMITS[0] = CHEST_PITCH_LIMIT;
        LIMITS[1] = HEAD_PITCH_LOOK;
        Life.look(lp, SHARES, LIMITS, 2, CHAIN);
        float cp = CHAIN[0], hp = CHAIN[1];
        add(out, BODY, RY, cy * D);
        add(out, BODY, RX, cp * D);
        if (!full)
            return;
        // the model put the whole look on the head and the head rides the chest's turn: leave it its own share
        add(out, HEAD, RY, (hy - look[LOOK_NET_YAW]) * D);
        add(out, HEAD, RX, (hp - look[LOOK_PITCH]) * D);
        // arms the model aimed from the head (a bow, a crossbow) keep that aim: take the chest's turn back off them
        add(out, ARM_R, RY, -cy * aim * D);
        add(out, ARM_L, RY, -cy * aim * D);
        add(out, ARM_R, RX, cp * aim * D);
        add(out, ARM_L, RX, cp * aim * D);
    }

    private static void clamp(float[] out) {
        for (int bone = 0; bone < BONES; bone++)
            for (int ch = 0; ch < CH; ch++) {
                int i = bone * CH + ch;
                float lim = limit(bone, ch);
                float x = out[i];
                out[i] = Float.isFinite(x) ? Mth.clamp(x, -lim, lim) : 0f;
            }
    }
}
