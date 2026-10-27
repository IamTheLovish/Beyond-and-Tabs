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

    /**
     * @param dx,dy,dz   the unit's movement this tick (x - xo ...), blocks
     * @param dYawDeg    its body's turn this tick (yBodyRot - yBodyRotO), degrees
     * @param health     its health now (as synced to the client), maxHealth its max
     * @param attacking  swinging or drawing this tick (counts as exertion)
     * @param fresh      the slot's first tick: seed it, no motion
     */
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
