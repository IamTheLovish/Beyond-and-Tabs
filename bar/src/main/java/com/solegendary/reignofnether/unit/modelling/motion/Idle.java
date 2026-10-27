package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.unit.modelling.animations.Life;
import net.minecraft.util.Mth;

import static com.solegendary.reignofnether.unit.modelling.motion.AnimState.*;

/**
 * The living army's idle scheduler (design/living_army.md, section 2): a standing unit adjusts its grip, glances,
 * rolls a shoulder or looks around - one at a time, every {@link #GAP_MIN}-{@link #GAP_MAX} ticks, never the same
 * twice in a row, quiet while it moves or fights and for {@link #QUIET_AFTER_STOP} ticks after - and shifts its weight
 * from leg to leg on a slower track. Every draw is seeded by the entity id ({@link Desync#hash01}), so a block of
 * units never fidgets as one and every client sees the same. Pure; one step per client tick.
 */
public final class Idle {

    private Idle() {}

    public static final int NONE = 0, GRIP = 1, GLANCE = 2, SHOULDER = 3, LOOK_AROUND = 4;
    private static final int KINDS = 4;

    public static final int GAP_MIN = 80, GAP_MAX = 240, LEN_MIN = 20, LEN_MAX = 50, QUIET_AFTER_STOP = 30;
    public static final int WEIGHT_GAP_MIN = 120, WEIGHT_GAP_MAX = 280, WEIGHT_EASE = 40;

    private static float range(int id, int salt, int lo, int hi) {
        return (float) Math.floor(lo + Desync.hash01(id, salt) * (hi - lo + 1));
    }

    /** One tick; returns the fidget that STARTED this tick ({@link #NONE} otherwise) - a GLANCE wants a target. */
    public static int step(float[] s, int b, int id, int tick, boolean moving, boolean fighting) {
        if (s[b + IDLE_INIT] == 0f) {
            s[b + IDLE_INIT] = 1f;
            s[b + IDLE_NEXT] = tick + range(id, 1000, GAP_MIN, GAP_MAX);
            s[b + WEIGHT_NEXT] = tick + range(id, 2000, WEIGHT_GAP_MIN, WEIGHT_GAP_MAX);
            s[b + WEIGHT_SIDE] = Desync.hash01(id, 3000) < 0.5f ? -1f : 1f;
            s[b + WEIGHT_START] = -1000f;
        }
        int started = NONE;
        if (moving || fighting) {
            s[b + IDLE_KIND] = NONE;
            s[b + QUIET_UNTIL] = tick + 1 + QUIET_AFTER_STOP;
            s[b + IDLE_NEXT] = Math.max(s[b + IDLE_NEXT], s[b + QUIET_UNTIL]);
            s[b + WEIGHT_NEXT] = s[b + QUIET_UNTIL];          // one settling shift once it stands
        } else {
            if (s[b + IDLE_KIND] != NONE && tick >= s[b + IDLE_START] + s[b + IDLE_LEN])
                s[b + IDLE_KIND] = NONE;
            if (s[b + IDLE_KIND] == NONE && tick >= s[b + IDLE_NEXT] && tick >= s[b + QUIET_UNTIL]) {
                int n = (int) s[b + IDLE_COUNT] + 1;
                s[b + IDLE_COUNT] = n;
                int last = (int) s[b + IDLE_LAST];
                int pick = Math.min(KINDS - 2, (int) (Desync.hash01(id, n * 3) * (KINDS - 1)));
                int kind = 1;
                for (int k = 1, i = 0; k <= KINDS; k++) {     // the pick-th kind that is not the last one
                    if (k == last)
                        continue;
                    if (i++ == pick) {
                        kind = k;
                        break;
                    }
                }
                float len = range(id, n * 3 + 1, LEN_MIN, LEN_MAX);
                s[b + IDLE_KIND] = kind;
                s[b + IDLE_LAST] = kind;
                s[b + IDLE_START] = tick;
                s[b + IDLE_LEN] = len;
                s[b + IDLE_NEXT] = tick + len + range(id, n * 3 + 2, GAP_MIN, GAP_MAX);
                started = kind;
            }
            if (tick >= s[b + WEIGHT_NEXT]) {
                s[b + WEIGHT_SIDE] = -s[b + WEIGHT_SIDE];
                s[b + WEIGHT_START] = tick;
                s[b + WEIGHT_NEXT] = tick + range(id, 2000 + tick, WEIGHT_GAP_MIN, WEIGHT_GAP_MAX);
            }
        }
        int kind = (int) s[b + IDLE_KIND];
        float env = fidget(s, b, tick);
        float headYaw = 0f, headPitch = 0f;
        if (kind == GLANCE) {
            headYaw = s[b + GLANCE_YAW] * Math.min(1f, env * 2f);
            headPitch = s[b + GLANCE_PITCH] * Math.min(1f, env * 2f);
        } else if (kind == LOOK_AROUND) {
            float p = (tick - s[b + IDLE_START]) / Math.max(1f, s[b + IDLE_LEN]);
            headYaw = 50f * Mth.sin(p * Mth.TWO_PI) * env;
        }
        Life.spring(s, b + SP_HEAD_YAW, headYaw, 0.35f, 0.7f, 1f);
        Life.spring(s, b + SP_HEAD_PITCH, headPitch, 0.35f, 0.7f, 1f);
        Life.spring(s, b + SP_WEIGHT, weight(s, b, tick), 0.25f, 0.9f, 1f);
        return started;
    }

    /** The hip side the weight is on, -1 (left leg) .. 1 (right leg), easing over {@link #WEIGHT_EASE} ticks. */
    public static float weight(float[] s, int b, float tickF) {
        float side = s[b + WEIGHT_SIDE];
        float k = Life.smooth((tickF - s[b + WEIGHT_START]) / WEIGHT_EASE);
        return -side + 2f * side * k;
    }

    /** The current fidget's envelope 0..1..0 (0 when none plays). */
    public static float fidget(float[] s, int b, float tickF) {
        if (s[b + IDLE_KIND] == NONE)
            return 0f;
        float p = (tickF - s[b + IDLE_START]) / Math.max(1f, s[b + IDLE_LEN]);
        return p < 0f || p > 1f ? 0f : Mth.sin(p * Mth.PI);
    }
}
