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
 * - at rest the halberd stands planted beside him; with a target he drops it to the READY guard - the point forward
 *   at waist height (blended in and out over ~6 ticks per unit);
 * - attack (the swing: attackTime): pull back, then the thrust - arm and body behind it - and back to the guard;
 * - walk: a measured stride, the tabard pushed by the legs, the halberd carried (the wrist keeps it upright at rest),
 *   the tassel streaming; idle: breath, the tassel stirring, the head following the look.
 * Keys laid out with tools/pose_preview.py (DSL degrees on "halberd_arm" / "halberd" / "body"; a part takes
 * -x, +y, -z radians).
 */
@OnlyIn(Dist.CLIENT)
public class HalberdierUnitModel extends HalberdierModel<VindicatorUnit> {

    // the guard and the thrust (DSL degrees)
    private static final float[] READY_ARM = {45, 0, 10}, READY_POLE = {-110, 0, 0}, READY_BODY = {-6, 0, 0};
    private static final float[] KEY_P = {0f, 0.25f, 0.55f, 1f};
    private static final float[][] KEY_ARM = {{45, 0, 10}, {25, 0, 6}, {70, 0, 5}, {45, 0, 10}};
    private static final float[][] KEY_POLE = {{-110, 0, 0}, {-100, 0, 0}, {-130, 0, 0}, {-110, 0, 0}};
    private static final float[][] KEY_BODY = {{-6, 0, 0}, {0, 0, 0}, {-12, 0, 0}, {-6, 0, 0}};
    private static final float READY_TICKS = 6f;

    private final ModelPart body, head, arm, pole, tassel, leftArm, tabFront, tabBack;
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
        float[] st = readyState.computeIfAbsent(entity, e -> new float[] {0f, age});
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

        for (int i = 0; i < 2; i++) {
            float ph = w + (i == 0 ? 0f : Mth.PI);
            if (thigh[i] != null) thigh[i].xRot += Mth.cos(ph) * 0.55f * amt;
            float lift = Math.max(0f, Mth.sin(ph));
            if (shin[i] != null) shin[i].xRot += lift * 0.85f * amt;
            if (foot[i] != null) foot[i].xRot -= lift * 0.3f * amt;     // toe up on the swing (it dragged 0.5 px)
        }
        float spread = Math.abs(Mth.cos(w)) * amt;
        if (tabFront != null) tabFront.xRot += -spread * 0.35f + 0.02f * Mth.sin(t * 0.08f);
        if (tabBack != null) tabBack.xRot += spread * 0.3f;
        body.y += breathe * 0.08f + (Math.abs(Mth.cos(w)) - 0.5f) * 0.5f * amt;
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
            dsl(arm, Mth.lerp(f, KEY_ARM[k][0], KEY_ARM[k + 1][0]), Mth.lerp(f, KEY_ARM[k][1], KEY_ARM[k + 1][1]),
                Mth.lerp(f, KEY_ARM[k][2], KEY_ARM[k + 1][2]), 1f);
            dsl(pole, Mth.lerp(f, KEY_POLE[k][0], KEY_POLE[k + 1][0]), 0, 0, 1f);
            dsl(body, Mth.lerp(f, KEY_BODY[k][0], KEY_BODY[k + 1][0]), 0, 0, 1f);
        } else if (ready > 0f) {
            float b = smooth(ready);
            dsl(arm, READY_ARM[0], READY_ARM[1], READY_ARM[2], b);
            dsl(pole, READY_POLE[0], READY_POLE[1], READY_POLE[2], b);
            dsl(body, READY_BODY[0], READY_BODY[1], READY_BODY[2], b);
        }
    }
}
