package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.models.gen.SquireRunnerModel;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Sunforged Squire Runner (militia_unit) in motion, over its generated body (SquireRunnerModel, from
 * design/models/sunforged_squire_runner_v2.json by tools/dsl_to_game.py; the generator is tools/gen_squire.py).
 *
 * - weapons: the sword in hand, or (after swapping) the bow in hand and the sword's hilt in its scabbard; the Militia
 *   Bows research shows the slung bow and the quiver; an enchanted weapon shows its light (sword_rune / bow_rune);
 * - run: he leans into it, knees high, arms pumping, and the pennant on his back lifts and flutters with his speed
 *   (at rest it sags and stirs);
 * - sword attack (the swing: attackTime): wind-up over the shoulder, a chop forward with the body behind it, follow;
 * - bow: with a target he turns side-on (the renderer turns the whole body a quarter turn, {@link #aimTurn}) and takes
 *   the AIM pose solved in tools/gen_squire.py - bow arm out, string drawn to the jaw; while he draws
 *   (isUsingItem) the nocked arrow and the drawn string show instead of the straight string.
 * Keys laid out with tools/game_preview.py --poses (DSL degrees; a part takes -x, +y, -z radians).
 */
@OnlyIn(Dist.CLIENT)
public class SquireRunnerUnitModel extends SquireRunnerModel<MilitiaUnit> {

    // the aim, from tools/gen_squire.py (it prints these; change both together)
    private static final String[] AIM_BONES = {"left_arm", "left_fore", "right_arm", "right_fore", "bow", "body", "head",
        "left_thigh", "right_thigh"};
    private static final float[][] AIM = {{8, -58, 99}, {16, 0, 0}, {95, 9, -72}, {82, 0, 0}, {-53.6f, -35.4f, -92.6f},
        {6, 0, 0}, {-6, -84, 0}, {0, 0, 9}, {0, 0, -9}};
    // the sword: wind-up, chop, follow-through (DSL degrees on right_arm / right_fore / sword / body)
    private static final float[] KEY_P = {0f, 0.3f, 0.55f, 0.75f, 1f};
    private static final float[][] KEY_ARM = {{0, 0, 0}, {150, 0, -10}, {62, 0, 10}, {30, 0, 15}, {0, 0, 0}};
    private static final float[] KEY_FORE = {0, 30, 0, 8, 0};
    private static final float[] KEY_SWORD = {0, 60, -40, -30, 0};
    private static final float[][] KEY_BODY = {{0, 0, 0}, {6, -18, 0}, {-14, 10, 0}, {-12, 8, 0}, {0, 0, 0}};
    private static final float AIM_TICKS = 4f, DRAW_TICKS = 3f;

    private final ModelPart body, head, rArm, rFore, lArm, lFore, sword, swordRune, sheathed, bow, bowString, bowRune,
        arrow, backBow;
    private final ModelPart[] aimParts = new ModelPart[AIM_BONES.length];
    private final ModelPart[] pennant = new ModelPart[3];
    private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2];
    private final Map<MilitiaUnit, float[]> state = new WeakHashMap<>();   // {aim, draw, last age}
    private float aim = 0f, draw = 0f;
    private boolean usingBow = false, enchanted = false;

    public SquireRunnerUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        rArm = bone("right_arm");
        rFore = bone("right_fore");
        lArm = bone("left_arm");
        lFore = bone("left_fore");
        sword = bone("sword");
        swordRune = bone("sword_rune");
        sheathed = bone("sheathed");
        bow = bone("bow");
        bowString = bone("bow_string");
        bowRune = bone("bow_rune");
        arrow = bone("arrow");
        backBow = bone("back_bow");
        for (int i = 0; i < AIM_BONES.length; i++)
            aimParts[i] = bone(AIM_BONES[i]);
        for (int i = 0; i < 3; i++)
            pennant[i] = bone("pennant_" + (i + 1));
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            thigh[i] = bone(sides[i] + "_thigh");
            shin[i] = bone(sides[i] + "_shin");
        }
    }

    private static float approach(float v, float target, float step) {
        return target > v ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    /**
     * Called by the renderer BEFORE it sets up the entity's rotations (the aim turns the whole body, so the blend must
     * be current when setupRotations runs, which is before prepareMobModel).
     */
    public void update(MilitiaUnit entity, float partialTick) {
        float age = entity.tickCount + partialTick;
        float[] st = state.computeIfAbsent(entity, e -> new float[] {0f, 0f, age});
        float dt = Mth.clamp(age - st[2], 0f, 20f);
        usingBow = entity.isUsingBow();
        enchanted = entity.getMainHandItem().isEnchanted();
        boolean aiming = usingBow && (entity.isAggressive() || entity.isUsingItem());
        st[0] = approach(st[0], aiming ? 1f : 0f, dt / AIM_TICKS);
        st[1] = approach(st[1], aiming && entity.isUsingItem() ? 1f : 0f, dt / DRAW_TICKS);
        st[2] = age;
        aim = st[0];
        draw = st[1];
    }

    /** The quarter turn of the whole body while aiming (0..1, smoothed): his left side to the target. */
    public float aimTurn() {
        return smooth(aim);
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

    private static void show(ModelPart p, boolean on) {
        if (p != null)
            p.visible = on;
    }

    @Override
    public void setupAnim(MilitiaUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (body == null)
            return;
        boolean drawn = usingBow && draw > 0.9f && aim > 0.9f;
        show(sword, !usingBow);
        show(swordRune, !usingBow && enchanted);
        show(sheathed, usingBow);
        show(bow, usingBow);
        show(bowRune, usingBow && enchanted);
        show(bowString, !drawn);
        show(arrow, drawn);
        show(backBow, !usingBow);

        float amt = Math.min(1f, limbSwingAmount);
        float w = limbSwing * 0.6662f;
        float t = ageInTicks + (entity.getId() & 63);
        float breathe = Mth.sin(t * 0.07f);
        float a = smooth(aim);
        float run = amt * (1f - a);

        // legs: high knees, a long stride
        for (int i = 0; i < 2; i++) {
            float ph = w + (i == 0 ? 0f : Mth.PI);
            if (thigh[i] != null) thigh[i].xRot += Mth.cos(ph) * 0.75f * run;
            if (shin[i] != null) shin[i].xRot += Math.max(0f, Mth.sin(ph)) * 1.1f * run;
        }
        // the body leans into the run and bobs; arms pump against the legs (the sword arm less)
        dsl(body, -9f, 0, 0, run);
        body.y += breathe * 0.07f + (Math.abs(Mth.cos(w)) - 0.5f) * 0.6f * run;
        body.yRot += Mth.sin(w) * 0.06f * run;
        if (lArm != null) lArm.xRot += -Mth.cos(w) * 0.7f * run + breathe * 0.02f;
        dsl(lFore, 45f, 0, 0, run);
        if (rArm != null) rArm.xRot += Mth.cos(w) * 0.35f * run;
        if (head != null && a < 1f) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f * (1f - a);
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f * (1f - a);
        }
        // the pennant: at rest it sags and stirs; running it lifts and flutters, faster with speed
        float freq = 0.12f + 0.45f * amt;
        for (int i = 0; i < 3; i++) {
            ModelPart p = pennant[i];
            if (p == null)
                continue;
            dsl(p, 14f - 22f * amt, 0, 0, 1f);
            p.yRot += Mth.sin(t * freq - i * 1.1f) * (0.06f + 0.22f * amt) * (i == 0 ? 0.6f : 1f);
            p.xRot += Mth.sin(t * freq * 1.3f - i * 0.9f) * 0.04f * (0.5f + amt);
        }

        if (usingBow) {
            // holding the bow out of the aim: the forearm a little forward so the bow stands before the thigh
            dsl(lFore, 22f, 0, 0, 1f - a);
            if (a > 0f) {
                for (int i = 0; i < AIM.length; i++)
                    dsl(aimParts[i], AIM[i][0], AIM[i][1], AIM[i][2], a);
                // not drawing: the right hand eases forward off the jaw
                float ease = (1f - smooth(draw)) * a;
                dsl(rArm, -18f, 0, 0, ease);
                dsl(rFore, -30f, 0, 0, ease);
            }
        } else if (this.attackTime > 0f) {
            float p = this.attackTime;
            int k = 0;
            while (k < KEY_P.length - 2 && p > KEY_P[k + 1])
                k++;
            float f = smooth(Mth.clamp((p - KEY_P[k]) / (KEY_P[k + 1] - KEY_P[k]), 0f, 1f));
            dsl(rArm, Mth.lerp(f, KEY_ARM[k][0], KEY_ARM[k + 1][0]), Mth.lerp(f, KEY_ARM[k][1], KEY_ARM[k + 1][1]),
                Mth.lerp(f, KEY_ARM[k][2], KEY_ARM[k + 1][2]), 1f);
            dsl(rFore, Mth.lerp(f, KEY_FORE[k], KEY_FORE[k + 1]), 0, 0, 1f);
            dsl(sword, Mth.lerp(f, KEY_SWORD[k], KEY_SWORD[k + 1]), 0, 0, 1f);
            dsl(body, Mth.lerp(f, KEY_BODY[k][0], KEY_BODY[k + 1][0]), Mth.lerp(f, KEY_BODY[k][1], KEY_BODY[k + 1][1]),
                0, 1f);
        }
    }
}
