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
    /** Slider indices (the config's motion_* percentages / 100). */
    public static final int BREATH = 0, WEIGHT = 1, FIDGET = 2, LEAN = 3, ACCEL = 4, FLINCH = 5, SECONDARY = 6;

    private static final float D = Mth.DEG_TO_RAD;
    /** Joint limits per bone: rotation (degrees) and translation (px). */
    private static final float[] ROT_LIMIT_DEG = {15f, 25f, 40f, 40f, 40f, 15f, 15f, 40f};
    private static final float[] MOVE_LIMIT_PX = {6f, 3f, 2f, 2f, 2f, 1f, 1f, 2f};
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
        for (int i = 0; i < OUT; i++)
            out[i] = 0f;
        float amp = Desync.amp(id), tempo = Desync.tempo(id);

        // breath: the chest swells (a small pitch back), the shoulders rise, the neck follows a beat later
        float gBreath = sliders[BREATH] * ZoomGain.gain(zoom, ZoomGain.K_BREATH) * amp;
        if (gBreath != 0f) {
            float ex = Mth.clamp(s[b + EXERTION], 0f, 1f);
            float period = Mth.lerp(ex, 60f, 25f) / tempo;
            float size = Mth.lerp(ex, 1.2f, 2.6f) * gBreath;
            float br = Life.breath(ageF, id, period), neck = Life.breath(ageF - period * 0.08f, id, period);
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
        }

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

        for (int bone = 0; bone < BONES; bone++)
            for (int ch = 0; ch < CH; ch++) {
                int i = bone * CH + ch;
                float lim = limit(bone, ch);
                float x = out[i];
                out[i] = Float.isFinite(x) ? Mth.clamp(x, -lim, lim) : 0f;
            }
    }
}
