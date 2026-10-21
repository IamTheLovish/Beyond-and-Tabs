package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierAnim;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Halberdier (vindicator_unit) in motion, over its generated body (HalberdierModel, from
 * design/models/sunforged_halberdier_v2.json by tools/dsl_to_game.py; the generator is tools/gen_halberdier.py).
 *
 * - the trunk is two bones: the pelvis ("body", the legs hang from it) and the chest over it (the cuirass, pauldrons,
 *   head and arms ride it), so the guard turns the hips in over the planted feet and the shoulders further, and the
 *   thrust brings the shoulders through and folds the spine forward over the lead knee (Meyer; a fencing lunge);
 * - idle (round 10): the READY guard held - the POLEARM class guard: the rear (right) fist at the rear hip, the butt
 *   low, the front (left) fist a guide ring forward on the haft, the point up ~18 deg at head height, the left side
 *   and foot forward, the knees soft (Meyer 1570) - with a slow weight shift over the lead foot and back, the point
 *   drifting with it, a glance over the open shoulder each 4 s cycle; each unit at its own phase (Life.offset);
 * - walk (round 10): the halberd at the PORT carry (de Gheyn 1607: the butt low at the rear hip, the haft diagonal
 *   across the body, the point ahead over the lead shoulder), the haft held steady in the world while the trunk bobs
 *   and turns under it, the feet planted (the stance ankle travels straight back under the hip at the walking speed),
 *   crossfaded with the idle by the stride; the walk's phase runs WALK_PHASE cycles per vanilla limbSwing cycle;
 * - attack (attackTime): the lunge thrust - the hips sink and turn as the rear hand draws back (the haft slides back
 *   through the front fist), then the hips travel and the lead foot steps ahead, the rear arm drives along the haft
 *   through the front fist (both hands stay on it), the body behind the point, the rear leg straight; the weight
 *   comes back and the lead foot steps home. The tassel lags the haft;
 * - the life layer on top (Life.java, shared by every rig): the breath in the pelvis and chest, the look, the
 *   tassel's stir, the plume a link behind the head.
 * Every number of every clip is DATA: design/anims/halberdier.json, built from the figure by tools/grip_keys.py
 * (the rear arm hung by anatomy from its fist's cuff and the halberd moved into the fist, the front arm solved onto
 * the haft, the legs by IK with the rear foot planted) and generated into HalberdierAnim by tools/anim_lab.py, which
 * also plays and measures the clips offline. Between keys a clip is a monotone cubic Hermite (anim_lab.tangents and
 * {@link #tangents} are the same curve - change both together): speed carries through a key, so no limb stops dead
 * at every key as the old per-segment smoothstep made it; the idle and the walk are loops (the tangents wrap).
 * Until round 10 only the thrust was keyed: standing and walking used the generator's planted-halberd rest pose
 * with a one-handed upright carry (lovish's tour, Oct 21: the guard existed only inside the thrust). Until Oct 21 the right fist was a hammer grip (the point
 * 116 degrees off the forearm): with the haft forward the arm had to hang straight and the halberd dangled from it
 * (lovish: "like a purse dangling, his arm looks stiff"); the left hand never touched the haft. DSL degrees (a part
 * takes -x, +y, -z radians) and px (y up).
 */
@OnlyIn(Dist.CLIENT)
public class HalberdierUnitModel extends HalberdierModel<VindicatorUnit> {

    /** The walk clip's cycles per vanilla limbSwing cycle: the stride its feet were keyed for (grip_keys.WALK_PHASE). */
    private static final float WALK_PHASE = HalberdierAnim.WALK_PHASE;
    /** The idle's drift off the guard (its weight shift, the glance) fades out over this much of the thrust and back in
     *  over its last part - the thrust starts from wherever the idle is, not from a snap to the guard (anim_lab.IDLE_FADE). */
    private static final float IDLE_FADE = 0.15f;

    /** One clip: its key times and every bone's keys (rotation and, if any, move) with their tangents. */
    private static final class Clip {
        final float[] p;
        final boolean loop;
        final float[][][] rot, move, rotTan, moveTan;

        Clip(float[] p, boolean loop, float[][][] rot, float[][][] move) {
            this.p = p;
            this.loop = loop;
            this.rot = rot;
            this.move = move;
            this.rotTan = new float[rot.length][][];
            this.moveTan = new float[rot.length][][];
            for (int i = 0; i < rot.length; i++) {
                rotTan[i] = tangents(p, rot[i], loop);
                moveTan[i] = move[i] == null ? null : tangents(p, move[i], loop);
            }
        }

        /** The pose at progress q (0..1), written into rot/move out (per bone, 3 axes). */
        void sample(float q, float[][] rotOut, float[][] moveOut) {
            int k = 0;
            while (k < p.length - 2 && q > p[k + 1])
                k++;
            float h = p[k + 1] - p[k];
            float f = Mth.clamp((q - p[k]) / h, 0f, 1f);
            for (int i = 0; i < rot.length; i++) {
                for (int a = 0; a < 3; a++) {
                    rotOut[i][a] = hermite(rot[i], rotTan[i], k, a, f, h);
                    moveOut[i][a] = move[i] == null ? 0f : hermite(move[i], moveTan[i], k, a, f, h);
                }
            }
        }
    }

    private static final String[] BONES = {"body", "chest", "head", "plume", "halberd", "tassel", "tabard_front",
        "tabard_back", "right_arm", "right_fore", "left_arm", "left_fore", "left_thigh", "left_shin", "left_foot",
        "right_thigh", "right_shin", "right_foot"};

    private static float[][][] rots(float[][]... t) {
        return t;
    }

    private static final Clip IDLE = new Clip(HalberdierAnim.IDLE_P, HalberdierAnim.IDLE_LOOP,
        rots(HalberdierAnim.IDLE_BODY, HalberdierAnim.IDLE_CHEST, HalberdierAnim.IDLE_HEAD, HalberdierAnim.IDLE_PLUME,
             HalberdierAnim.IDLE_POLE, HalberdierAnim.IDLE_TASSEL, HalberdierAnim.IDLE_TABF, HalberdierAnim.IDLE_TABB,
             HalberdierAnim.IDLE_RARM, HalberdierAnim.IDLE_RFORE, HalberdierAnim.IDLE_LARM, HalberdierAnim.IDLE_LFORE,
             HalberdierAnim.IDLE_LTHIGH, HalberdierAnim.IDLE_LSHIN, HalberdierAnim.IDLE_LFOOT,
             HalberdierAnim.IDLE_RTHIGH, HalberdierAnim.IDLE_RSHIN, HalberdierAnim.IDLE_RFOOT),
        rots(HalberdierAnim.IDLE_BODY_MOVE, null, null, null, HalberdierAnim.IDLE_POLE_MOVE, null, null, null, null,
             null, null, null, null, null, null, null, null, null));
    private static final Clip WALK = new Clip(HalberdierAnim.WALK_P, HalberdierAnim.WALK_LOOP,
        rots(HalberdierAnim.WALK_BODY, HalberdierAnim.WALK_CHEST, HalberdierAnim.WALK_HEAD, HalberdierAnim.WALK_PLUME,
             HalberdierAnim.WALK_POLE, HalberdierAnim.WALK_TASSEL, HalberdierAnim.WALK_TABF, HalberdierAnim.WALK_TABB,
             HalberdierAnim.WALK_RARM, HalberdierAnim.WALK_RFORE, HalberdierAnim.WALK_LARM, HalberdierAnim.WALK_LFORE,
             HalberdierAnim.WALK_LTHIGH, HalberdierAnim.WALK_LSHIN, HalberdierAnim.WALK_LFOOT,
             HalberdierAnim.WALK_RTHIGH, HalberdierAnim.WALK_RSHIN, HalberdierAnim.WALK_RFOOT),
        rots(HalberdierAnim.WALK_BODY_MOVE, null, null, null, HalberdierAnim.WALK_POLE_MOVE, null, null, null, null,
             null, null, null, null, null, null, null, null, null));
    private static final Clip THRUST = new Clip(HalberdierAnim.THRUST_P, HalberdierAnim.THRUST_LOOP,
        rots(HalberdierAnim.THRUST_BODY, HalberdierAnim.THRUST_CHEST, HalberdierAnim.THRUST_HEAD,
             HalberdierAnim.THRUST_PLUME, HalberdierAnim.THRUST_POLE, HalberdierAnim.THRUST_TASSEL,
             HalberdierAnim.THRUST_TABF, HalberdierAnim.THRUST_TABB, HalberdierAnim.THRUST_RARM,
             HalberdierAnim.THRUST_RFORE, HalberdierAnim.THRUST_LARM, HalberdierAnim.THRUST_LFORE,
             HalberdierAnim.THRUST_LTHIGH, HalberdierAnim.THRUST_LSHIN, HalberdierAnim.THRUST_LFOOT,
             HalberdierAnim.THRUST_RTHIGH, HalberdierAnim.THRUST_RSHIN, HalberdierAnim.THRUST_RFOOT),
        rots(HalberdierAnim.THRUST_BODY_MOVE, null, null, null, HalberdierAnim.THRUST_POLE_MOVE, null, null, null,
             null, null, null, null, null, null, null, null, null, null));

    /**
     * Fritsch-Carlson monotone cubic tangents (per unit of p) for each axis of a key table: speed carries through a
     * key when the motion continues the same way, a turn or a hold gets a zero tangent (no overshoot), the ends ease -
     * or, for a loop, wrap (the last segment feeds the first key's tangent, so a cycle runs through its seam).
     * tools/anim_lab.py tangents() is the same formula - the lab measures what the game plays.
     */
    private static float[][] tangents(float[] p, float[][] keys, boolean loop) {
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

    private final ModelPart body, head, tassel, plume;
    private final ModelPart[] parts = new ModelPart[BONES.length];
    // scratch (one model instance renders every halberdier in turn; setupAnim is single-threaded)
    private final float[][] rotA = new float[BONES.length][3], movA = new float[BONES.length][3];
    private final float[][] rotB = new float[BONES.length][3], movB = new float[BONES.length][3];
    private static final float[][] READY_ROT = new float[BONES.length][3], READY_MOVE = new float[BONES.length][3];
    static {
        IDLE.sample(0f, READY_ROT, READY_MOVE);          // the idle's first key is the guard every clip starts in
    }

    public HalberdierUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        tassel = bone("tassel");
        plume = bone("plume");
        for (int i = 0; i < BONES.length; i++)
            parts[i] = bone(BONES[i]);
    }

    private static void dsl(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.xRot -= x * Mth.DEG_TO_RAD * k;
        p.yRot += y * Mth.DEG_TO_RAD * k;
        p.zRot -= z * Mth.DEG_TO_RAD * k;
    }

    /** A DSL move (px, y up) onto a part's offset (y down), times k. */
    private static void move(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.x += x * k;
        p.y -= y * k;
        p.z += z * k;
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    @Override
    public void setupAnim(VindicatorUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (body == null)
            return;
        float amt = Math.min(1f, limbSwingAmount);
        float t = ageInTicks + Life.offset(entity.getId());   // the shared life layer: no two units stir in step
        float breathe = Life.breath(ageInTicks, entity.getId(), 64f);   // a quick in-breath, a long out-breath

        // ---- the keyed clips (design/anims/halberdier.json): the thrust over the swing, else the walk crossfaded with
        // the idle guard by the stride (amt) - every clip starts and ends in READY, so the three blend without a pop
        // the idle cycle by age (each unit at its own phase)
        IDLE.sample((t / HalberdierAnim.IDLE_TICKS) % 1f, rotA, movA);
        if (this.attackTime > 0f) {
            float q = this.attackTime;
            THRUST.sample(q, rotB, movB);
            float k = 1f - smooth(Math.min(1f, Math.min(q / IDLE_FADE, (1f - q) / IDLE_FADE)));
            for (int i = 0; i < BONES.length; i++)
                for (int a = 0; a < 3; a++) {
                    rotA[i][a] = rotB[i][a] + (rotA[i][a] - READY_ROT[i][a]) * k;
                    movA[i][a] = movB[i][a] + (movA[i][a] - READY_MOVE[i][a]) * k;
                }
        } else {
            // the walk by the stride's phase: WALK_PHASE cycles per vanilla cycle so the planted foot does not slide
            // for the stride the keys were built on
            if (amt > 0f) {
                float q = (limbSwing * 0.6662f / Mth.TWO_PI * WALK_PHASE) % 1f;
                WALK.sample(q < 0f ? q + 1f : q, rotB, movB);
                float wk = smooth(amt);
                for (int i = 0; i < BONES.length; i++)
                    for (int a = 0; a < 3; a++) {
                        rotA[i][a] += (rotB[i][a] - rotA[i][a]) * wk;
                        movA[i][a] += (movB[i][a] - movA[i][a]) * wk;
                    }
            }
        }
        for (int i = 0; i < BONES.length; i++) {
            dsl(parts[i], rotA[i][0], rotA[i][1], rotA[i][2], 1f);
            move(parts[i], movA[i][0], movA[i][1], movA[i][2], 1f);
        }

        // ---- the life layer over the keys: breath in the chest and the haft (the hands ride with it), the look, the
        // tassel and the plume a link behind
        body.y -= breathe * 0.10f;
        ModelPart chest = parts[1];
        if (chest != null) chest.xRot -= breathe * 0.012f;
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }
        if (tassel != null) {
            tassel.xRot += 0.06f * Mth.sin(t * 0.12f) + 0.25f * amt;
            tassel.zRot += 0.05f * Mth.sin(t * 0.1f);
        }
        if (plume != null)      // the feathers a link behind the head's stir (Life.chain), streaming back on the walk
            plume.xRot += 0.05f * Life.chain(ageInTicks, entity.getId(), 48f, 1, 3f) + 0.2f * amt;
    }
}
