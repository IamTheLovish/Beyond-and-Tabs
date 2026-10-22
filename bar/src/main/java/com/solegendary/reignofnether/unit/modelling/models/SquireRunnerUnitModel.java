package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.HumanBase;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.SquireAnim;
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
 * - idle and walk: the SHARED human base clips (HumanBase, from design/anims/squire.json - tools/human_base.py's gait
 *   and idle spec with the runner's parameters: a long quick stride, the trunk leaning 8 degrees into it, both arms
 *   pumping, the sword arm less; the idle a weight shift and a glance round the sword-ready stance). The pennant on
 *   his back lifts and flutters with his speed (at rest it sags and stirs). Until Oct 21 the run was hand-written
 *   here: a sine on the thighs, straight legs at both extremes (design/anatomy_audit.md section 6), the feet sliding;
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
    private static final float[][] AIM = {{10, -60, 98}, {16, 0, 0}, {100, 7, -72}, {81, 0, 0},
        {-17.9f, -36.6f, -91.9f}, {6, 0, 0}, {-6, -84, 0}, {0, 0, 9}, {0, 0, -9}};
    // the sword: wind-up, chop, follow-through (DSL degrees on right_arm / right_fore / sword / body). The chop and the
    // follow from tools/grip_keys.py (it prints these rows; change both together): the fist rides the forearm, closed
    // round the grip as the sword rests (-25 in the forearm, tools/gen_squire.py), the blade 65 degrees off the
    // forearm, and KEY_SWORD may only turn it a wrist's reach (+-20) in the fist. Until Oct 16 the chop turned it -87
    // (the follow -77): the grip came out of the knuckles and the blade ran straight on from the forearm (lovish: "when
    // he puts his hand around the weapon it doesnt look natural"). A fist like this cannot point the blade forward-down
    // from a forearm held out level, so the chop lowers the arm (the forearm ~50 degrees down, the elbow a little bent)
    // and snaps the wrist (-20: the grip 45 degrees to the forearm), the blade level at the waist and a little across
    // the body with its turn; the follow carries it on down
    private static final float[] KEY_P = {0f, 0.3f, 0.55f, 0.75f, 1f};
    private static final float[][] KEY_ARM = {{0, 0, 0}, {150, 0, -10}, {42.5f, -6.1f, -17}, {25.7f, -19, -19.6f},
        {0, 0, 0}};
    private static final float[] KEY_FORE = {0, 30, -14, -16, 0};
    private static final float[] KEY_SWORD = {0, 13, -20, -20, 0};
    // a right-handed cut: the wind-up turns the chest to his RIGHT (DSL y > 0 turns the front toward -x), the strike
    // drives the right shoulder through to his left (y < 0) - the signs were reversed until the Oct 15 review
    private static final float[][] KEY_BODY = {{0, 0, 0}, {6, 18, 0}, {-14, -10, 0}, {-12, -8, 0}, {0, 0, 0}};
    private static final float AIM_TICKS = 4f, DRAW_TICKS = 3f;

    private final ModelPart body, head, rArm, rFore, lArm, lFore, sword, swordRune, sheathed, bow, bowString, bowRune,
        arrow, backBow;
    private final ModelPart[] aimParts = new ModelPart[AIM_BONES.length];
    private final ModelPart[] pennant = new ModelPart[3];
    private final HumanBase base;
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
        base = new HumanBase(SquireAnim.BONES, bones,
            new HumanBase.Clip(SquireAnim.IDLE_P, SquireAnim.IDLE_LOOP, SquireAnim.IDLE_ROT, SquireAnim.IDLE_MOVE),
            new HumanBase.Clip(SquireAnim.WALK_P, SquireAnim.WALK_LOOP, SquireAnim.WALK_ROT, SquireAnim.WALK_MOVE),
            SquireAnim.IDLE_TICKS, SquireAnim.WALK_PHASE);
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
        float[] st = state.get(entity);   // get/put, not computeIfAbsent: the lambda captures age and would allocate
        if (st == null)                   // every frame for every unit (and again in a shader pack's shadow pass)
            state.put(entity, st = new float[] {0f, 0f, age});
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
        float t = ageInTicks + (entity.getId() & 63);
        float breathe = Life.breath(ageInTicks, entity.getId(), 90f);   // the shared life layer: quick in-breath, long out-breath
        float a = smooth(aim);

        // the shared base: the sword-ready idle (its drift held off while aiming - the AIM is keyed on READY - and
        // faded out through the swing) crossfaded with the walk by the stride; the aim stills the walk too (he
        // stands side-on to shoot)
        float swing = usingBow ? -1f : (this.attackTime > 0f ? this.attackTime : -1f);
        base.pose(limbSwing, limbSwingAmount * (1f - a), ageInTicks, entity.getId(), HumanBase.drift(swing) * (1f - a));
        body.y -= breathe * 0.07f;
        if (lArm != null) lArm.xRot += breathe * 0.02f;
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
