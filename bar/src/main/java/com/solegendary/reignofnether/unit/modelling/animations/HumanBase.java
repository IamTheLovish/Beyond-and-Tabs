package com.solegendary.reignofnether.unit.modelling.animations;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.Map;

/**
 * Client only. The shared human base clips, played: the idle (the weapon class's READY stance held, with a slow weight
 * shift, a glance, the arms' drift) and the walk (a planted-feet gait cycle played by the stride), keyed as DATA for
 * every Sunforged human body by tools/human_base.py (design/anims/&lt;unit&gt;.json: one gait and idle spec, the unit's own
 * parameters - stride, lift, attitude, weapon class) and generated into unit/modelling/models/gen/&lt;Unit&gt;Anim by
 * tools/anim_lab.py, which also measures them (no snaps, joints in range, planted feet). One player for every rig,
 * so the army walks and stands alike (lovish, Oct 21: "extremely realistic and cohesive", "a living army") and a unit
 * model writes no walk or idle of its own any more - it builds one of these from its Anim tables and calls
 * {@link #pose} first thing after its rest pose, then adds its attack clip and its own props (cloth, mechanisms, the
 * breath from {@link Life}) on top.
 *
 * Every clip starts and ends in READY (the idle's first key), so the three blend without a pop: the walk crossfades
 * with the idle by the stride; an attack clip (keyed at zero = READY) fades the idle's drift out over its first
 * {@link #IDLE_FADE} and back in over its last, so a blow starts from wherever the idle is, not from a snap to the
 * guard. Between keys a clip is a monotone cubic Hermite (Fritsch-Carlson tangents: speed carries through a key, a
 * turn or a hold stops cleanly, no overshoot; a loop's tangents wrap round its seam) - the same curve as
 * tools/anim_lab.py tangents() and HalberdierUnitModel: change all together.
 *
 * DSL degrees (a part takes -x, +y, -z radians) and px (y up) added to the model's own rest pose.
 */
public final class HumanBase {

    /** The idle's drift off READY fades out over this much of an attack and back in over its last part (anim_lab.IDLE_FADE). */
    public static final float IDLE_FADE = 0.15f;
    /** Vanilla's limbSwing to the walk's cycle: the model's own cycle is 2 pi / 0.6662 of it. */
    private static final float LIMB = 0.6662f / Mth.TWO_PI;

    /** One clip: its key times and every bone's keys (rotation and, if any, move) with their tangents. */
    public static final class Clip {
        final float[] p;
        final float[][][] rot, move, rotTan, moveTan;

        public Clip(float[] p, boolean loop, float[][][] rot, float[][][] move) {
            this.p = p;
            this.rot = rot;
            this.move = move;
            this.rotTan = new float[rot.length][][];
            this.moveTan = new float[rot.length][][];
            for (int i = 0; i < rot.length; i++) {
                rotTan[i] = rot[i] == null ? null : tangents(p, rot[i], loop);
                moveTan[i] = move[i] == null ? null : tangents(p, move[i], loop);
            }
        }

        /** The pose at progress q (0..1) into rotOut / moveOut (per bone, 3 axes). */
        public void sample(float q, float[][] rotOut, float[][] moveOut) {
            int k = 0;
            while (k < p.length - 2 && q > p[k + 1])
                k++;
            float h = p[k + 1] - p[k];
            float f = Mth.clamp((q - p[k]) / h, 0f, 1f);
            for (int i = 0; i < rot.length; i++)
                for (int a = 0; a < 3; a++) {
                    rotOut[i][a] = rot[i] == null ? 0f : hermite(rot[i], rotTan[i], k, a, f, h);
                    moveOut[i][a] = move[i] == null ? 0f : hermite(move[i], moveTan[i], k, a, f, h);
                }
        }
    }

    /**
     * Fritsch-Carlson monotone cubic tangents (per unit of p) for each axis of a key table: speed carries through a
     * key when the motion continues the same way, a turn or a hold gets a zero tangent (no overshoot), the ends ease -
     * or, for a loop, wrap (the last segment feeds the first key's tangent, so a cycle runs through its seam).
     */
    static float[][] tangents(float[] p, float[][] keys, boolean loop) {
        int n = keys.length;
        float[][] m = new float[n][3];
        for (int a = 0; a < 3; a++) {
            float[] d = new float[n - 1];
            for (int i = 0; i < n - 1; i++)
                d[i] = (keys[i + 1][a] - keys[i][a]) / (p[i + 1] - p[i]);
            for (int i = 1; i < n - 1; i++)
                m[i][a] = fc(d[i - 1], d[i]);
            if (loop)
                m[0][a] = m[n - 1][a] = fc(d[n - 2], d[0]);
        }
        return m;
    }

    private static float fc(float a, float b) {
        if (a * b <= 0f)
            return 0f;
        float t = (a + b) / 2f;
        float al = t / a, be = t / b;
        if (al * al + be * be > 9f)
            t *= 3f / Mth.sqrt(al * al + be * be);
        return t;
    }

    /** The cubic Hermite of axis a between keys k and k + 1 at the local time t (0..1); h = the segment's length. */
    private static float hermite(float[][] v, float[][] tan, int k, int a, float t, float h) {
        float t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * v[k][a] + (t3 - 2 * t2 + t) * h * tan[k][a]
               + (-2 * t3 + 3 * t2) * v[k + 1][a] + (t3 - t2) * h * tan[k + 1][a];
    }

    public static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Adds DSL degrees (x, y, z) to a part: (-x, +y, -z) radians, times k. */
    public static void dsl(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.xRot -= x * Mth.DEG_TO_RAD * k;
        p.yRot += y * Mth.DEG_TO_RAD * k;
        p.zRot -= z * Mth.DEG_TO_RAD * k;
    }

    /** A DSL move (px, y up) onto a part's offset (y down), times k. */
    public static void move(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.x += x * k;
        p.y -= y * k;
        p.z += z * k;
    }

    private final ModelPart[] parts;
    private final Clip idle, walk;
    private final int idleTicks;
    private final float walkPhase;
    private final float[][] ready, readyMove;
    // scratch (one model instance renders every unit in turn; setupAnim is single-threaded)
    private final float[][] rotA, movA, rotB, movB;

    /**
     * @param bones     the Anim's BONES (the order of its tables)
     * @param bonesByName the model's bones by name (the generated model's `bones`; a bone the model lacks is skipped)
     * @param idleTicks the idle's cycle (IDLE_TICKS)
     * @param walkPhase the walk's cycles per vanilla limbSwing cycle (WALK_PHASE): the stride its feet were keyed for
     */
    public HumanBase(String[] bones, Map<String, ModelPart> bonesByName, Clip idle, Clip walk, int idleTicks,
                     float walkPhase) {
        int n = bones.length;
        parts = new ModelPart[n];
        for (int i = 0; i < n; i++)
            parts[i] = bonesByName.get(bones[i]);
        this.idle = idle;
        this.walk = walk;
        this.idleTicks = idleTicks;
        this.walkPhase = walkPhase;
        rotA = new float[n][3];
        movA = new float[n][3];
        rotB = new float[n][3];
        movB = new float[n][3];
        ready = new float[n][3];
        readyMove = new float[n][3];
        idle.sample(0f, ready, readyMove);     // the idle's first key is the READY stance every clip starts in
    }

    /** How much of the idle's drift off READY to keep through an attack at progress attackP (0..1; -1 = no attack):
     *  all of it outside one, faded out over the attack's first IDLE_FADE and back in over its last. */
    public static float drift(float attackP) {
        if (attackP < 0f)
            return 1f;
        return 1f - smooth(Math.min(1f, Math.min(attackP / IDLE_FADE, (1f - attackP) / IDLE_FADE)));
    }

    /**
     * The base pose onto the (rest-posed) parts: the idle by age at this unit's own phase (Life.offset), crossfaded with
     * the walk by the stride, the idle's drift off READY kept by `drift` (0..1: {@link #drift} through an attack, and
     * 0 for a pose that must sit exactly on READY, such as an aim).
     *
     * @param limbSwing       vanilla's stride phase (the walk is played by it: planted feet at the keyed stride)
     * @param limbSwingAmount vanilla's stride amount (0 standing .. 1 walking)
     * @param ageInTicks      the entity's age with the partial tick
     * @param id              the entity's id (its phase offset)
     * @return the stride blend applied (0..1, smoothed), for the caller's own stride-driven props
     */
    public float pose(float limbSwing, float limbSwingAmount, float ageInTicks, int id, float drift) {
        float t = ageInTicks + Life.offset(id);
        float q = (t / idleTicks) % 1f;
        idle.sample(q < 0f ? q + 1f : q, rotA, movA);
        int n = parts.length;
        if (drift < 1f) {
            float k = Mth.clamp(drift, 0f, 1f);
            for (int i = 0; i < n; i++)
                for (int a = 0; a < 3; a++) {
                    rotA[i][a] = ready[i][a] + (rotA[i][a] - ready[i][a]) * k;
                    movA[i][a] = readyMove[i][a] + (movA[i][a] - readyMove[i][a]) * k;
                }
        }
        float wk = 0f;
        float amt = Math.min(1f, limbSwingAmount);
        if (amt > 0f) {
            walk.sample(walkProgress(limbSwing), rotB, movB);
            wk = smooth(amt);
            for (int i = 0; i < n; i++)
                for (int a = 0; a < 3; a++) {
                    rotA[i][a] += (rotB[i][a] - rotA[i][a]) * wk;
                    movA[i][a] += (movB[i][a] - movA[i][a]) * wk;
                }
        }
        for (int i = 0; i < n; i++) {
            dsl(parts[i], rotA[i][0], rotA[i][1], rotA[i][2], 1f);
            move(parts[i], movA[i][0], movA[i][1], movA[i][2], 1f);
        }
        return wk;
    }

    /** The walk's cycle progress (0..1) for a stride phase: for the caller's own stride-driven props (cloth, a pennant). */
    public float walkProgress(float limbSwing) {
        float w = (limbSwing * LIMB * walkPhase) % 1f;
        return w < 0f ? w + 1f : w;
    }
}
