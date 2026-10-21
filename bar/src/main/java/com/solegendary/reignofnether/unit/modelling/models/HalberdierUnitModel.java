package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierAnim;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Sunforged Halberdier (vindicator_unit) in motion, over its generated body (HalberdierModel, from
 * design/models/sunforged_halberdier_v2.json by tools/dsl_to_game.py; the generator is tools/gen_halberdier.py).
 *
 * - the trunk is two bones: the pelvis ("body", the legs hang from it) and the chest over it (the cuirass, pauldrons,
 *   head and arms ride it), so the guard turns the hips in over the planted feet and the shoulders further, and the
 *   thrust brings the shoulders through and folds the spine forward over the lead knee (Meyer; a fencing lunge);
 * - at rest the halberd stands planted beside him, gripped in front of the chest (a sentry's hold); with a target he
 *   drops it to the READY guard - the POLEARM class guard: the rear (right) hand at the hip with the elbow bent and
 *   out, the front (left) fist forward on the haft, the point ahead at chest height, the left side and foot forward,
 *   the knees soft (Meyer 1570) - blended in and out over ~6 ticks per unit;
 * - attack (attackTime): the lunge thrust - the hips sink and turn as the rear hand draws back (the haft slides back
 *   through the front fist), then the hips travel and the lead foot steps ahead, the rear arm drives along the haft
 *   through the front fist (both hands stay on it), the body behind the point, the rear leg straight; the weight
 *   comes back and the lead foot steps home. The tassel lags the haft;
 * - walk: a measured stride, the tabard pushed by the legs, the halberd carried (the wrist keeps it upright at rest),
 *   the tassel streaming; idle: breath, the tassel stirring, the head following the look.
 * Every guard and thrust number is DATA: design/anims/halberdier.json, built from the figure by tools/grip_keys.py
 * (the rear arm hung by anatomy from its fist's cuff and the halberd moved into the fist, the front arm solved onto
 * the haft, the legs by IK with the rear foot planted) and generated into HalberdierAnim by tools/anim_lab.py, which
 * also plays and measures the clip offline. Between keys the clip is a monotone cubic Hermite (anim_lab.tangents and
 * {@link #tangents} are the same curve - change both together): speed carries through a key, so no limb stops dead
 * at every key as the old per-segment smoothstep made it. Until Oct 21 the right fist was a hammer grip (the point
 * 116 degrees off the forearm): with the haft forward the arm had to hang straight and the halberd dangled from it
 * (lovish: "like a purse dangling, his arm looks stiff"); the left hand never touched the haft. DSL degrees (a part
 * takes -x, +y, -z radians) and px (y up).
 */
@OnlyIn(Dist.CLIENT)
public class HalberdierUnitModel extends HalberdierModel<VindicatorUnit> {

    private static final float READY_TICKS = 6f;
    private static final float[] KEY_P = HalberdierAnim.THRUST_P;

    /** One animated bone: its guard values and its thrust keys (rotation and, if any, move) with their tangents. */
    private static final class Track {
        final String bone;
        final boolean leg;
        final float[] readyRot, readyMove;
        final float[][] rot, move, rotTan, moveTan;

        Track(String bone, boolean leg, float[] readyRot, float[] readyMove, float[][] rot, float[][] move) {
            this.bone = bone;
            this.leg = leg;
            this.readyRot = readyRot;
            this.readyMove = readyMove;
            this.rot = rot;
            this.move = move;
            this.rotTan = tangents(rot);
            this.moveTan = move == null ? null : tangents(move);
        }
    }

    private static final Track[] TRACKS = {
        new Track("body", false, HalberdierAnim.READY_BODY, HalberdierAnim.READY_BODY_MOVE,
                  HalberdierAnim.THRUST_BODY, HalberdierAnim.THRUST_BODY_MOVE),
        new Track("chest", false, HalberdierAnim.READY_CHEST, null, HalberdierAnim.THRUST_CHEST, null),
        new Track("head", false, HalberdierAnim.READY_HEAD, null, HalberdierAnim.THRUST_HEAD, null),
        new Track("halberd", false, HalberdierAnim.READY_POLE, HalberdierAnim.READY_POLE_MOVE,
                  HalberdierAnim.THRUST_POLE, HalberdierAnim.THRUST_POLE_MOVE),
        new Track("tassel", false, HalberdierAnim.READY_TASSEL, null, HalberdierAnim.THRUST_TASSEL, null),
        new Track("tabard_front", false, HalberdierAnim.READY_TABF, null, HalberdierAnim.THRUST_TABF, null),
        new Track("tabard_back", false, HalberdierAnim.READY_TABB, null, HalberdierAnim.THRUST_TABB, null),
        new Track("right_arm", false, HalberdierAnim.READY_RARM, null, HalberdierAnim.THRUST_RARM, null),
        new Track("right_fore", false, HalberdierAnim.READY_RFORE, null, HalberdierAnim.THRUST_RFORE, null),
        new Track("left_arm", false, HalberdierAnim.READY_LARM, null, HalberdierAnim.THRUST_LARM, null),
        new Track("left_fore", false, HalberdierAnim.READY_LFORE, null, HalberdierAnim.THRUST_LFORE, null),
        new Track("left_thigh", true, HalberdierAnim.READY_LTHIGH, null, HalberdierAnim.THRUST_LTHIGH, null),
        new Track("left_shin", true, HalberdierAnim.READY_LSHIN, null, HalberdierAnim.THRUST_LSHIN, null),
        new Track("left_foot", true, HalberdierAnim.READY_LFOOT, null, HalberdierAnim.THRUST_LFOOT, null),
        new Track("right_thigh", true, HalberdierAnim.READY_RTHIGH, null, HalberdierAnim.THRUST_RTHIGH, null),
        new Track("right_shin", true, HalberdierAnim.READY_RSHIN, null, HalberdierAnim.THRUST_RSHIN, null),
        new Track("right_foot", true, HalberdierAnim.READY_RFOOT, null, HalberdierAnim.THRUST_RFOOT, null),
    };

    /**
     * Fritsch-Carlson monotone cubic tangents (per unit of p) for each axis of a key table: speed carries through a
     * key when the motion continues the same way, a turn or a hold gets a zero tangent (no overshoot), the ends ease.
     * tools/anim_lab.py tangents() is the same formula - the lab measures what the game plays.
     */
    private static float[][] tangents(float[][] keys) {
        int n = keys.length;
        float[][] m = new float[n][3];
        for (int a = 0; a < 3; a++) {
            float[] d = new float[n - 1];
            for (int i = 0; i < n - 1; i++)
                d[i] = (keys[i + 1][a] - keys[i][a]) / (KEY_P[i + 1] - KEY_P[i]);
            for (int i = 1; i < n - 1; i++) {
                if (d[i - 1] * d[i] <= 0f) {
                    m[i][a] = 0f;
                } else {
                    float t = (d[i - 1] + d[i]) / 2f;
                    float al = t / d[i - 1], be = t / d[i];
                    if (al * al + be * be > 9f)
                        t *= 3f / Mth.sqrt(al * al + be * be);
                    m[i][a] = t;
                }
            }
        }
        return m;
    }

    /** The cubic Hermite of axis a between keys k and k + 1 at the local time t (0..1); h = the segment's length. */
    private static float hermite(float[][] v, float[][] tan, int k, int a, float t, float h) {
        float t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * v[k][a] + (t3 - 2 * t2 + t) * h * tan[k][a]
               + (-2 * t3 + 3 * t2) * v[k + 1][a] + (t3 - t2) * h * tan[k + 1][a];
    }

    private final ModelPart body, head, arm, pole, tassel, leftArm, tabFront, tabBack;
    private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
    private final ModelPart[] parts = new ModelPart[TRACKS.length];
    private final Map<VindicatorUnit, float[]> readyState = new WeakHashMap<>();
    private float ready = 0f;

    public HalberdierUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        arm = bone("halberd_arm");
        pole = bone("halberd");
        tassel = bone("tassel");
        leftArm = bone("left_arm");
        tabFront = bone("tabard_front");
        tabBack = bone("tabard_back");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            thigh[i] = bone(sides[i] + "_thigh");
            shin[i] = bone(sides[i] + "_shin");
            foot[i] = bone(sides[i] + "_foot");
        }
        for (int i = 0; i < TRACKS.length; i++)
            parts[i] = bone(TRACKS[i].bone);
    }

    @Override
    public void prepareMobModel(VindicatorUnit entity, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        float age = entity.tickCount + partialTick;
        float[] st = readyState.get(entity);   // get/put, not computeIfAbsent: the lambda captures age and would allocate
        if (st == null)                   // every frame for every unit (and again in a shader pack's shadow pass)
            readyState.put(entity, st = new float[] {0f, age});
        float dt = Mth.clamp(age - st[1], 0f, 20f);
        float target = (entity.getTarget() != null || entity.isAggressive()) ? 1f : 0f;
        st[0] = target > st[0] ? Math.min(target, st[0] + dt / READY_TICKS) : Math.max(target, st[0] - dt / READY_TICKS);
        st[1] = age;
        ready = st[0];
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
        float w = limbSwing * 0.6662f;
        float t = ageInTicks + Life.offset(entity.getId());   // the shared life layer: no two units stir in step
        float breathe = Mth.sin(t * 0.07f);

        // the stride (the anatomy pass, Oct 16 - Perry & Burnfield's gait): the thigh swings 28 degrees forward and 12
        // back, not +-31.5 (a scissor); the knee leads the swing - about 30 flexed at toe-off, 54 in early swing, near
        // straight at heel strike - and gives ~9 in the stance after contact (it was straight at both extremes: a
        // compass walk, vanilla's humanoid). ph 0: the leg furthest back; the swing runs to ph = PI.
        float bob = (Math.abs(Mth.cos(w)) - 0.5f) * 0.5f * amt;           // lowest at contact
        for (int i = 0; i < 2; i++) {
            float ph = w + (i == 0 ? 0f : Mth.PI);
            if (thigh[i] != null)
                thigh[i].xRot += (Mth.cos(ph) * 0.35f - 0.14f) * amt;   // (the thighs hang from the pelvis: its bob is theirs)
            float s = Mth.sin(ph + 0.6f);
            float lift = Math.max(0f, s);
            if (shin[i] != null) shin[i].xRot += (s > 0f ? s * 0.95f : -s * 0.16f) * amt;
            if (foot[i] != null) foot[i].xRot -= lift * 0.3f * amt;     // toe up on the swing (it dragged 0.5 px)
        }
        float spread = Math.abs(Mth.cos(w)) * amt;
        if (tabFront != null) tabFront.xRot += -spread * 0.35f + 0.02f * Mth.sin(t * 0.08f);
        if (tabBack != null) tabBack.xRot += spread * 0.3f;
        body.y += breathe * 0.08f + bob;
        body.yRot += Mth.sin(w) * 0.05f * amt;
        // the free hand swings with the stride and stirs with the breath - only until it takes the haft
        if (leftArm != null) leftArm.xRot += (-Mth.cos(w) * 0.45f * amt + breathe * 0.02f) * (1f - ready);
        float carry = Mth.cos(w) * 0.12f * amt * (1f - ready);
        if (arm != null) arm.xRot += carry;
        if (pole != null) pole.xRot -= carry;                           // the wrist keeps it upright at rest
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }
        if (tassel != null) {
            tassel.xRot += 0.06f * Mth.sin(t * 0.12f) + 0.3f * amt;
            tassel.zRot += 0.05f * Mth.sin(t * 0.1f);
        }

        if (this.attackTime > 0f) {
            // the thrust, from the guard (an attack always starts from it): every track a monotone cubic through its
            // keys, the hips' travel carried by the body and the thigh roots alike
            float p = this.attackTime;
            int k = 0;
            while (k < KEY_P.length - 2 && p > KEY_P[k + 1])
                k++;
            float h = KEY_P[k + 1] - KEY_P[k];
            float f = Mth.clamp((p - KEY_P[k]) / h, 0f, 1f);
            for (int i = 0; i < TRACKS.length; i++) {
                Track tr = TRACKS[i];
                ModelPart part = parts[i];
                if (part == null)
                    continue;
                dsl(part, hermite(tr.rot, tr.rotTan, k, 0, f, h), hermite(tr.rot, tr.rotTan, k, 1, f, h),
                    hermite(tr.rot, tr.rotTan, k, 2, f, h), 1f);
                if (tr.move != null)
                    move(part, hermite(tr.move, tr.moveTan, k, 0, f, h), hermite(tr.move, tr.moveTan, k, 1, f, h),
                         hermite(tr.move, tr.moveTan, k, 2, f, h), 1f);
            }
        } else if (ready > 0f) {
            float b = smooth(ready);
            for (int i = 0; i < TRACKS.length; i++) {
                Track tr = TRACKS[i];
                float wt = tr.leg ? b * (1f - amt) : b;   // the stance's soft knees only standing: the walk owns the legs
                dsl(parts[i], tr.readyRot[0], tr.readyRot[1], tr.readyRot[2], wt);
                if (tr.readyMove != null)
                    move(parts[i], tr.readyMove[0], tr.readyMove[1], tr.readyMove[2], wt);
            }
        }
    }
}
