package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.models.gen.CrossbowLevyModel;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Sunforged Crossbow Levy (pillager_unit) in motion, over its generated body (CrossbowLevyModel, from
 * design/models/sunforged_crossbow_levy_v2.json by tools/dsl_to_game.py; the generator is tools/gen_levy.py).
 *
 * - at rest the crossbow is carried at the ready in front of the chest, levelled (the art director's review, Oct 15:
 *   the crossbow, not the hat, must be the signature); with a target it comes up to the shoulder, the head down to the
 *   sights (the AIM, blended per unit over ~5 ticks);
 * - spanning it (Pillager.isChargingCrossbow) the whole rig - arms and crossbow together, so the hands stay on it -
 *   swings the nose down toward the ground and he leans into the crank;
 * - riding (a ravager: isPassenger) the legs sit astride and only the arms aim;
 * - walk: a light stride, the tabard pushed by the legs, the crossbow bobbing; idle: breath, the head following the
 *   look. Keys laid out in tools/pose_preview.py (DSL degrees on "xbow_arms" / "crossbow"; a part takes -x, +y, -z).
 */
@OnlyIn(Dist.CLIENT)
public class CrossbowLevyUnitModel extends CrossbowLevyModel<PillagerUnit> {

    private static final float AIM_TICKS = 5f;
    private final ModelPart body, head, arms, xbow, tabFront, tabBack;
    private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
    private final Map<PillagerUnit, float[]> state = new WeakHashMap<>();   // {aim, charge, last age}
    private float aim = 0f, charge = 0f;

    public CrossbowLevyUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        arms = bone("xbow_arms");
        xbow = bone("crossbow");
        tabFront = bone("tabard_front");
        tabBack = bone("tabard_back");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            thigh[i] = bone(sides[i] + "_thigh");
            shin[i] = bone(sides[i] + "_shin");
            foot[i] = bone(sides[i] + "_foot");
        }
    }

    private static float approach(float v, float target, float step) {
        return target > v ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    @Override
    public void prepareMobModel(PillagerUnit entity, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        float age = entity.tickCount + partialTick;
        float[] st = state.computeIfAbsent(entity, e -> new float[] {0f, 0f, age});
        float step = Mth.clamp(age - st[2], 0f, 20f) / AIM_TICKS;
        boolean charging = entity.isChargingCrossbow();
        boolean aiming = !charging && (entity.getTarget() != null || entity.isAggressive());
        st[0] = approach(st[0], aiming ? 1f : 0f, step);
        st[1] = approach(st[1], charging ? 1f : 0f, step);
        st[2] = age;
        aim = st[0];
        charge = st[1];
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
    public void setupAnim(PillagerUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (body == null)
            return;
        boolean riding = entity.isPassenger();
        float amt = riding ? 0f : Math.min(1f, limbSwingAmount);
        float w = limbSwing * 0.6662f;
        float t = ageInTicks + (entity.getId() & 63);
        float breathe = Mth.sin(t * 0.07f);

        if (riding) {
            // astride: thighs forward and splayed, shins down
            for (int i = 0; i < 2; i++) {
                float side = i == 0 ? 1f : -1f;
                // splayed OUT round the mount: yaw the knees apart (z alone rolled the shins inward and crossed the
                // feet - Oct 15 review)
                dsl(thigh[i], 75, -18 * side, 8 * side, 1f);
                dsl(shin[i], -75, 0, 0, 1f);
            }
        } else {
            for (int i = 0; i < 2; i++) {
                float ph = w + (i == 0 ? 0f : Mth.PI);
                if (thigh[i] != null) thigh[i].xRot += Mth.cos(ph) * 0.6f * amt;
                float lift = Math.max(0f, Mth.sin(ph));
                if (shin[i] != null) shin[i].xRot += lift * 0.85f * amt;
                // the foot follows through, toe up, so it clears the ground on the swing (it dragged 0.5 px)
                if (foot[i] != null) foot[i].xRot -= lift * 0.3f * amt;
            }
        }
        float spread = Math.abs(Mth.cos(w)) * amt;
        if (tabFront != null) tabFront.xRot += -spread * 0.35f + 0.02f * Mth.sin(t * 0.08f);
        if (tabBack != null) tabBack.xRot += spread * 0.3f;
        body.y += breathe * 0.08f + (Math.abs(Mth.cos(w)) - 0.5f) * 0.5f * amt;
        body.yRot += Mth.sin(w) * 0.05f * amt;
        if (arms != null) arms.xRot += Mth.sin(w * 2f) * 0.04f * amt + breathe * 0.015f;
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }

        // the aim: the crossbow raised and levelled, its nose brought round to the front. At rest it is carried
        // angled 20 degrees to his left (tools/gen_levy.py), so the aim turns it 20 to his right (DSL y > 0): 12 by the
        // torso (shoulders, rig and head go with it) and 8 by the rig - a whole 20 on the rig alone would pull the
        // shoulders 1.3 px off the torso. The head turns back the 12 to keep its eyes on the target. (Keyed with
        // tools/game_preview.py --poses; until Oct 15 these keys turned the nose a further 10 left - 29 off target.)
        if (aim > 0f) {
            float a = smooth(aim);
            dsl(body, 0, 12, 0, a);
            dsl(arms, 14, 8, 0, a);
            dsl(xbow, -12, 0, 0, a);
            dsl(head, -6, -12, 0, a);
        }
        // spanning: the nose pitched down mostly by the crossbow's own bone, a little by the rig (the rig alone at -38
        // swung the stock and both gloves 1.7-3 px into his belly - Oct 15 review), leaning into the crank; a small
        // crank rhythm in the arms
        if (charge > 0f) {
            float c = smooth(charge);
            dsl(arms, -10, 0, 0, c);
            dsl(xbow, -28, 0, 0, c);
            dsl(body, -10, 0, 0, c);
            if (arms != null) arms.xRot += Mth.sin(t * 0.9f) * 0.06f * c;
        }
    }
}
