package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.FieldMedicModel;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Field Medic (witch_unit) in motion, over its generated body (FieldMedicModel, from
 * design/models/sunforged_field_medic_v2.json by tools/dsl_to_game.py; the generator is tools/gen_medic.py).
 *
 * - walk: a light stride under the robe (the four panels part for the legs: the front one pushed forward, the back
 *   one trailing, the sides swaying with the hips), the left arm swinging, the staff carried like a walking staff
 *   (its arm swings, the wrist keeps it upright - the staff's pivot is her wrist, at the sleeve's mouth: turning it
 *   bends the hand, the fist stays on the forearm), the ribbons streaming back;
 * - idle: breath, the sun disc turning slowly in its ring and bobbing, the ribbons stirring, the hood following
 *   the look;
 * - the throw (WitchUnit.throwPotion swings the arm, so attackTime runs): the staff raised high before her with the
 *   disc spinning fast, the left hand tossing the potion underarm.
 * The angles were laid out with tools/pose_preview.py (DSL degrees; a part takes -x, +y, -z radians); their extremes
 * are design/poses/sunforged_field_medic_v2.json (walk_A-D, cast) - keep it in step with the numbers here.
 */
@OnlyIn(Dist.CLIENT)
public class FieldMedicUnitModel extends FieldMedicModel<WitchUnit> {

    private final ModelPart body, head, staffArm, staff, disc, ribbon, leftArm, robeFront, robeBack, robeLeft, robeRight;
    private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];

    public FieldMedicUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        staffArm = bone("staff_arm");
        staff = bone("staff");
        disc = bone("sun_disc");
        ribbon = bone("staff_ribbon");
        leftArm = bone("left_arm");
        robeFront = bone("robe_front");
        robeBack = bone("robe_back");
        robeLeft = bone("robe_left");
        robeRight = bone("robe_right");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            thigh[i] = bone(sides[i] + "_thigh");
            shin[i] = bone(sides[i] + "_shin");
            foot[i] = bone(sides[i] + "_foot");
        }
    }

    @Override
    public void setupAnim(WitchUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (body == null)
            return;
        float amt = Math.min(1f, limbSwingAmount);
        float w = limbSwing * 0.6662f;
        float t = ageInTicks + (entity.getId() & 63);
        float breathe = Life.breath(ageInTicks, entity.getId(), 90f);   // the shared life layer: quick in-breath, long out-breath

        // ---- legs under the robe (the swing 0.45 rad: the anatomy pass raised her hips 1.6 px, and at 0.5 the longer
        // legs took a 12% longer step than the walk was tuned to - the trailing boot and thigh 1.3 px into the robe)
        float knee = 0f;                                    // the deeper knee bend this instant (for the back panel)
        for (int i = 0; i < 2; i++) {
            float ph = w + (i == 0 ? 0f : Mth.PI);
            float lift = Math.max(0f, Mth.sin(ph));
            knee = Math.max(knee, lift);
            if (thigh[i] != null) thigh[i].xRot += Mth.cos(ph) * 0.45f * amt;
            if (shin[i] != null) shin[i].xRot += lift * 0.8f * amt;
            if (foot[i] != null) foot[i].xRot -= lift * 0.3f * amt;     // toe up on the swing (it dragged 0.5 px)
        }
        // ---- the robe parts for them
        float spread = Math.abs(Mth.cos(w)) * amt;
        if (robeFront != null) robeFront.xRot += -spread * 0.50f + 0.02f * Mth.sin(t * 0.08f);
        // the back panel also rides out over the bent knee's boot (following only the stride's spread, it was flat
        // at the peak bend and the boot punched 2.2 px through it - Oct 15 review); 0.33 of the spread since the
        // robe hangs from the raised hips (its hem swings on a longer lever, the trailing leg reaches further back)
        if (robeBack != null) robeBack.xRot += spread * 0.33f + knee * 0.45f * amt + 0.02f * Mth.sin(t * 0.08f + 1f);
        if (robeLeft != null) robeLeft.zRot += Mth.sin(w) * 0.05f * amt;
        if (robeRight != null) robeRight.zRot += Mth.sin(w) * 0.05f * amt;

        // ---- torso, arms, the staff
        body.y += breathe * 0.08f + (Math.abs(Mth.cos(w)) - 0.5f) * 0.5f * amt;
        body.yRot += Mth.sin(w) * 0.05f * amt;
        if (leftArm != null) leftArm.xRot += -Mth.cos(w) * 0.4f * amt + breathe * 0.02f;
        float staffSwing = Mth.cos(w) * 0.25f * amt;
        if (staffArm != null) staffArm.xRot += staffSwing;
        if (staff != null) staff.xRot -= staffSwing * 0.8f;           // the wrist keeps the staff upright
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }

        // ---- the disc and the ribbons
        float cast = this.attackTime > 0f ? Mth.sin(this.attackTime * Mth.PI) : 0f;
        if (disc != null) {
            disc.zRot += t * 0.03f + cast * 2.5f;
            disc.y += Mth.sin(t * 0.06f) * 0.35f;
        }
        if (ribbon != null) {
            ribbon.xRot += 0.08f * Mth.sin(t * 0.13f) + 0.35f * amt + cast * 0.4f;
            ribbon.zRot += 0.05f * Mth.sin(t * 0.1f);
        }

        // ---- the throw: the staff raised high before her, the potion tossed underarm
        if (cast > 0f) {
            float lift = 65f * Mth.DEG_TO_RAD * cast;
            if (staffArm != null) staffArm.xRot -= lift;
            if (staff != null) staff.xRot += lift * 0.9f;
            if (leftArm != null) leftArm.xRot -= 70f * Mth.DEG_TO_RAD * cast;
            body.xRot += 0.08f * cast;
        }
    }
}
