package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.unit.modelling.animations.Life;
import net.minecraft.util.Mth;

import static com.solegendary.reignofnether.unit.modelling.motion.AnimState.*;

/**
 * One client tick of a unit's motion snapshot (design/living_army.md, section 3), from what the client sees of it:
 * how far it moved and turned this tick and its health. Pure (no world, no entity), no allocation: the world side is
 * {@link AnimStates}. Also steps the springs the lean, the start/stop rock, the arms' lag, the flinch and the stagger
 * ride on, so a frame only reads them ({@link Life#springAt}) - the view and a shader pack's shadow pass then pose a
 * unit identically.
 *
 * Hits come from the client's damage event ({@link #onHit}, the direction of the damage source), not from
 * LivingEntity.hurtDir: vanilla 1.20.1 only sets hurtDir for players (the camera tilt), so on a unit it is stale.
 * The size is judged from the health the server syncs a tick or two after the event: a drop of {@link #STAGGER_DROP}
 * of max health within {@link #SIZE_WINDOW} ticks turns the flinch into a stagger.
 */
public final class Kinematics {

    private Kinematics() {}

    public static final float STOP_SPEED = 0.01f, RUN_SPEED = 0.15f, TELEPORT_BLOCKS = 4f;
    public static final float LEAN_MAX_DEG = 12f, LEAN_PER_SPEED_TURN = 4f;
    public static final float PITCH_MAX_DEG = 8f, PITCH_PER_ACCEL = 200f;
    public static final float ARMS_MAX_DEG = 10f, ARMS_PER_ACCEL = -150f;
    public static final float STAGGER_DROP = 0.12f;
    public static final int SIZE_WINDOW = 3;
    /** Impulses (degrees per tick) a hit gives the flinch and stagger springs. */
    public static final float FLINCH_KICK = 9f, STAGGER_KICK = 10f;
    /** The impulse a landed blow gives its attacker's recoil spring (degrees per tick), and that spring's stiffness and damping. */
    public static final float RECOIL_KICK = 18f, RECOIL_OMEGA = 1.1f, RECOIL_ZETA = 0.3f;

    /**
     * @param dx,dy,dz   the unit's movement this tick (x - xo ...), blocks
     * @param dYawDeg    its body's turn this tick (yBodyRot - yBodyRotO), degrees
     * @param health     its health now (as synced to the client), maxHealth its max
     * @param attacking  swinging or drawing this tick (counts as exertion)
     * @param fresh      the slot's first tick: seed it, no motion
     */
    /** Ticks per breath at an exertion: 60 at rest (a man standing), 25 when blown. */
    public static float breathPeriod(float exertion) {
        return Mth.lerp(Mth.clamp(exertion, 0f, 1f), 60f, 25f);
    }

    public static void step(float[] s, int b, float dx, float dy, float dz, float dYawDeg, float health, float maxHealth,
                            boolean attacking, boolean fresh) {
        if (fresh) {
            for (int i = 0; i < STRIDE; i++)
                s[b + i] = 0f;
            s[b + LAST_HEALTH] = Float.isFinite(health) ? health : 0f;
            s[b + HIT_AGE] = 1000f;
            return;
        }
        if (!Float.isFinite(dx)) dx = 0f;
        if (!Float.isFinite(dy)) dy = 0f;
        if (!Float.isFinite(dz)) dz = 0f;
        if (!Float.isFinite(dYawDeg)) dYawDeg = 0f;

        float d = Mth.sqrt(dx * dx + dz * dz);
        boolean teleport = d > TELEPORT_BLOCKS || Math.abs(dy) > TELEPORT_BLOCKS;
        if (!teleport) {
            float before = s[b + SPEED];
            s[b + SPEED] = d;
            s[b + ACCEL] += (d - before - s[b + ACCEL]) * 0.5f;
            s[b + VSPEED] = dy;
            s[b + YAW_RATE] += (Mth.wrapDegrees(dYawDeg) - s[b + YAW_RATE]) * 0.5f;
        }
        float speed = s[b + SPEED];
        if (speed < STOP_SPEED) {
            s[b + SINCE_STOP] = Math.min(s[b + SINCE_STOP] + 1f, 1e6f);
            s[b + SINCE_START] = 0f;
        } else {
            s[b + SINCE_START] = Math.min(s[b + SINCE_START] + 1f, 1e6f);
            s[b + SINCE_STOP] = 0f;
        }
        float e = s[b + EXERTION];
        s[b + EXERTION] = speed > RUN_SPEED || attacking ? e + (1f - e) * 0.02f : e * 0.99f;
        // the breath runs on its own phase: exertion changes how fast it turns, never where it is (Motion.pose)
        float tempo = s[b + TEMPO] > 0f ? s[b + TEMPO] : 1f;
        s[b + BREATH_PREV] = s[b + BREATH_PHASE];
        s[b + BREATH_PHASE] = Life.advance(s[b + BREATH_PHASE], tempo / breathPeriod(s[b + EXERTION]));

        s[b + HIT_AGE] = Math.min(s[b + HIT_AGE] + 1f, 1000f);
        if (Float.isFinite(health) && Float.isFinite(maxHealth) && maxHealth > 0f) {
            if (s[b + HIT_SIZE] == FLINCH && s[b + HIT_AGE] <= SIZE_WINDOW
                && (s[b + HIT_HEALTH0] - health) / maxHealth >= STAGGER_DROP) {
                s[b + HIT_SIZE] = STAGGER;
                s[b + SP_STAGGER + 1] += STAGGER_KICK;
            }
            s[b + LAST_HEALTH] = health;
        }

        float lean = Mth.clamp(speed * s[b + YAW_RATE] * LEAN_PER_SPEED_TURN, -LEAN_MAX_DEG, LEAN_MAX_DEG);
        float pitch = Mth.clamp(s[b + ACCEL] * PITCH_PER_ACCEL, -PITCH_MAX_DEG, PITCH_MAX_DEG);
        float arms = Mth.clamp(s[b + ACCEL] * ARMS_PER_ACCEL, -ARMS_MAX_DEG, ARMS_MAX_DEG);
        Life.spring(s, b + SP_LEAN, lean, 0.5f, 0.8f, 1f);
        Life.spring(s, b + SP_PITCH, pitch, 0.6f, 0.45f, 1f);
        Life.spring(s, b + SP_ARMS, arms, 0.7f, 0.4f, 1f);
        Life.spring(s, b + SP_FLINCH_X, 0f, 0.9f, 0.5f, 1f);
        Life.spring(s, b + SP_FLINCH_Z, 0f, 0.9f, 0.5f, 1f);
        Life.spring(s, b + SP_STAGGER, 0f, 0.5f, 0.55f, 1f);
        Life.spring(s, b + SP_RECOIL, 0f, RECOIL_OMEGA, RECOIL_ZETA, 1f);

        for (int i = 0; i < STRIDE; i++)
            if (!Float.isFinite(s[b + i])) {
                float h = s[b + LAST_HEALTH];
                step(s, b, 0f, 0f, 0f, 0f, Float.isFinite(h) ? h : 0f, maxHealth, false, true);
                return;
            }
    }

    /** A damage event reached the client: stamp a flinch from `dirRad` ({@link #hitDir}); the size follows in step. */
    public static void onHit(float[] s, int b, float dirRad) {
        if (!Float.isFinite(dirRad))
            dirRad = 0f;
        s[b + HIT_AGE] = 0f;
        s[b + HIT_DIR] = dirRad;
        s[b + HIT_SIZE] = FLINCH;
        s[b + HIT_HEALTH0] = s[b + LAST_HEALTH];
        s[b + SP_FLINCH_X + 1] -= Mth.cos(dirRad) * FLINCH_KICK;   // from the front: bent back
        s[b + SP_FLINCH_Z + 1] -= Mth.sin(dirRad) * FLINCH_KICK;   // from the right: bent to the left
    }

    /** The look's spring (arrives late, settles without a wobble) and how fast a ranged unit's aim eases in / out. */
    public static final float LOOK_OMEGA = 0.45f, LOOK_ZETA = 0.8f, AIM_RATE = 0.2f;

    /**
     * One tick of the look (the shared look solver's input, {@link Life#look}): the head's world yaw and its pitch, as
     * the server's LookControl turned them, into springs - the head arrives late and settles - and the aim blend.
     * The yaw spring follows the angle the short way round and is kept within a turn of zero.
     *
     * @param headYawDeg the head's world yaw (yHeadRot), pitchDeg its pitch (xRot, + = down)
     * @param aiming     a ranged unit drawing on a target (aggressive)
     * @param fresh      the slot's first tick: start on the head, no swing in from zero
     */
    public static void look(float[] s, int b, float headYawDeg, float pitchDeg, boolean aiming, boolean fresh) {
        if (!Float.isFinite(headYawDeg))
            headYawDeg = s[b + SP_LOOK_YAW];
        if (!Float.isFinite(pitchDeg))
            pitchDeg = 0f;
        int y = b + SP_LOOK_YAW, p = b + SP_LOOK_PITCH;
        if (fresh) {
            s[y] = s[y + 2] = Mth.wrapDegrees(headYawDeg);
            s[p] = s[p + 2] = pitchDeg;
            s[y + 1] = s[p + 1] = 0f;
            s[b + AIM] = aiming ? 1f : 0f;
            return;
        }
        float cur = s[y];
        Life.spring(s, y, cur + Mth.wrapDegrees(headYawDeg - cur), LOOK_OMEGA, LOOK_ZETA, 1f);
        if (Math.abs(s[y]) > 360f) {      // back within a turn: the value and the previous step together
            float turn = 360f * Math.signum(s[y]);
            s[y] -= turn;
            s[y + 2] -= turn;
        }
        Life.spring(s, p, Mth.clamp(pitchDeg, -90f, 90f), LOOK_OMEGA, LOOK_ZETA, 1f);
        float a = s[b + AIM];
        s[b + AIM] = a + ((aiming ? 1f : 0f) - a) * AIM_RATE;
    }

    /**
     * The contact event, attacker side: this unit's blow landed (the client's damage event names it as the direct
     * source - for a keyed swing that is HeldBlow's hit key, the frame the blade arrives). The weapon stops on the
     * target: the arms and torso jolt back and shudder still. Stamped in the same call as the target's {@link #onHit},
     * so the two show on the same frame.
     */
    public static void onContact(float[] s, int b) {
        s[b + SP_RECOIL + 1] += RECOIL_KICK;
    }

    /**
     * The direction a hit came from relative to the body, radians: 0 from the front, +PI/2 from the unit's right,
     * +-PI from behind. (srcDx, srcDz) = the source's position minus the unit's; bodyYawDeg = yBodyRot (0 faces +z).
     */
    public static float hitDir(double srcDx, double srcDz, float bodyYawDeg) {
        float r = bodyYawDeg * Mth.DEG_TO_RAD;
        float sin = Mth.sin(r), cos = Mth.cos(r);
        double fwd = -srcDx * sin + srcDz * cos;
        double right = -srcDx * cos - srcDz * sin;
        return (float) Math.atan2(right, fwd);
    }
}
