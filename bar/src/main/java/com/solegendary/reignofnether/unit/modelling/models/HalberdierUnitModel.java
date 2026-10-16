package com.solegendary.reignofnether.unit.modelling.models;

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
 * - at rest the halberd stands planted beside him; with a target he drops it to the READY guard - held at the trail,
 *   the arm hanging and the fist by the hip, the point forward and a little up (blended in and out over ~6 ticks per
 *   unit);
 * - attack (the swing: attackTime): draw back, then the thrust - the arm swinging through, the body behind it - and
 *   back to the guard;
 * - walk: a measured stride, the tabard pushed by the legs, the halberd carried (the wrist keeps it upright at rest),
 *   the tassel streaming; idle: breath, the tassel stirring, the head following the look.
 * The guard and the thrust are built by tools/grip_keys.py (it prints these tables; change both together): the
 * gauntlet rides the halberd, closed round the haft as it stands at rest, and holds its point 116 degrees from the
 * forearm's line - so with the haft forward the forearm stands near vertical. Until Oct 16 the keys tilted the haft
 * 100-130 degrees about the grip while the forearm kept its rest pose: the cuff pointed up, away from the forearm, a
 * broken wrist (lovish: "when he puts his hand around the weapon it doesnt look natural"; "the elbows don't match
 * human anatomy"). Now each key hangs the arm by anatomy (the elbow's flex, the wrist within 15 degrees of the cuff)
 * and moves the halberd into the fist (KEY_GRIP: the halberd's offset); the haft keeps the old keys' angles. Pose
 * sweep: design/poses/sunforged_halberdier_v2.json. DSL degrees (a part takes -x, +y, -z radians) and px (y up).
 */
@OnlyIn(Dist.CLIENT)
public class HalberdierUnitModel extends HalberdierModel<VindicatorUnit> {

    // the guard and the thrust, from tools/grip_keys.py: the haft's angle (POLE, in the body's frame - the rig,
    // "halberd_arm", turns only for the walk), the halberd's grip moved into the fist (GRIP, px), the arm onto it
    // (RARM on right_arm, RFORE the elbow's flex on right_fore), the body behind the thrust
    // (re-derived Oct 16 for the anatomy pass: the shoulders 1.7 px higher and 0.6 in, upper arm 5.65 / forearm 4.55)
    private static final float[] READY_POLE = {-65, 0, 10}, READY_GRIP = {0.47f, -4.34f, 2.07f};
    private static final float[] READY_RARM = {15.2f, -16, 3.8f}, READY_BODY = {-6, 0, 0};
    private static final float READY_RFORE = -64;
    private static final float[] KEY_P = {0f, 0.25f, 0.55f, 1f};
    private static final float[][] KEY_POLE = {READY_POLE, {-75, 0, 6}, {-60, 0, 5}, READY_POLE};
    private static final float[][] KEY_GRIP = {READY_GRIP, {-0.01f, -3.27f, 6.14f}, {-0.44f, -3.85f, -0.29f},
        READY_GRIP};
    private static final float[][] KEY_RARM = {READY_RARM, {-14.9f, -16, -0.4f}, {30.8f, -16, -1.3f}, READY_RARM};
    private static final float[] KEY_RFORE = {READY_RFORE, -48, -70, READY_RFORE};
    private static final float[][] KEY_BODY = {READY_BODY, {0, 0, 0}, {-12, 0, 0}, READY_BODY};
    private static final float READY_TICKS = 6f;

    private final ModelPart body, head, arm, pole, tassel, leftArm, rArm, rFore, tabFront, tabBack;
    private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
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
        rArm = bone("right_arm");
        rFore = bone("right_fore");
        tabFront = bone("tabard_front");
        tabBack = bone("tabard_back");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            thigh[i] = bone(sides[i] + "_thigh");
            shin[i] = bone(sides[i] + "_shin");
            foot[i] = bone(sides[i] + "_foot");
        }
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
        float t = ageInTicks + (entity.getId() & 63);
        float breathe = Mth.sin(t * 0.07f);

        // the stride (the anatomy pass, Oct 16 - Perry & Burnfield's gait): the thigh swings 28 degrees forward and 12
        // back, not +-31.5 (a scissor); the knee leads the swing - about 30 flexed at toe-off, 54 in early swing, near
        // straight at heel strike - and gives ~9 in the stance after contact (it was straight at both extremes: a
        // compass walk, vanilla's humanoid). ph 0: the leg furthest back; the swing runs to ph = PI.
        float bob = (Math.abs(Mth.cos(w)) - 0.5f) * 0.5f * amt;           // lowest at contact
        for (int i = 0; i < 2; i++) {
            float ph = w + (i == 0 ? 0f : Mth.PI);
            if (thigh[i] != null) {
                thigh[i].xRot += (Mth.cos(ph) * 0.35f - 0.14f) * amt;
                thigh[i].y += bob;                       // the hips ride the body's bob (they slid 0.25 px in it)
            }
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
        if (leftArm != null) leftArm.xRot += -Mth.cos(w) * 0.45f * amt + breathe * 0.02f;
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
            // the thrust, from the guard (an attack always starts from it)
            float p = this.attackTime;
            int k = 0;
            while (k < KEY_P.length - 2 && p > KEY_P[k + 1])
                k++;
            float f = smooth(Mth.clamp((p - KEY_P[k]) / (KEY_P[k + 1] - KEY_P[k]), 0f, 1f));
            float[] p0 = KEY_POLE[k], p1 = KEY_POLE[k + 1], g0 = KEY_GRIP[k], g1 = KEY_GRIP[k + 1];
            float[] a0 = KEY_RARM[k], a1 = KEY_RARM[k + 1];
            dsl(pole, Mth.lerp(f, p0[0], p1[0]), Mth.lerp(f, p0[1], p1[1]), Mth.lerp(f, p0[2], p1[2]), 1f);
            move(pole, Mth.lerp(f, g0[0], g1[0]), Mth.lerp(f, g0[1], g1[1]), Mth.lerp(f, g0[2], g1[2]), 1f);
            dsl(rArm, Mth.lerp(f, a0[0], a1[0]), Mth.lerp(f, a0[1], a1[1]), Mth.lerp(f, a0[2], a1[2]), 1f);
            dsl(rFore, Mth.lerp(f, KEY_RFORE[k], KEY_RFORE[k + 1]), 0, 0, 1f);
            dsl(body, Mth.lerp(f, KEY_BODY[k][0], KEY_BODY[k + 1][0]), 0, 0, 1f);
        } else if (ready > 0f) {
            float b = smooth(ready);
            dsl(pole, READY_POLE[0], READY_POLE[1], READY_POLE[2], b);
            move(pole, READY_GRIP[0], READY_GRIP[1], READY_GRIP[2], b);
            dsl(rArm, READY_RARM[0], READY_RARM[1], READY_RARM[2], b);
            dsl(rFore, READY_RFORE, 0, 0, b);
            dsl(body, READY_BODY[0], READY_BODY[1], READY_BODY[2], b);
        }
    }
}
