package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.HumanBase;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.FieldMedicModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.MedicAnim;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Field Medic (witch_unit) in motion, over its generated body (FieldMedicModel, from
 * design/models/sunforged_field_medic_v2.json by tools/dsl_to_game.py; the generator is tools/gen_medic.py).
 *
 * - walk and idle: the SHARED human base clips (HumanBase, from design/anims/medic.json - tools/human_base.py's gait
 *   and idle spec with the healer's parameters: light and short, the robe's front and back panels parting for the
 *   legs, the left arm swinging, the staff carried like a walking staff - its arm swings, the wrist keeps it upright;
 *   the staff's pivot is her wrist, at the sleeve's mouth: turning it bends the hand, the fist stays on the forearm;
 *   the idle a weight shift and a glance round the staff-ready stance). On top: the breath, the side panels swaying
 *   with the hips, the sun disc turning slowly in its ring and bobbing, the ribbons streaming and stirring, the hood
 *   following the look. Until Oct 21 the walk was hand-written here (a sine on the thighs, straight legs at both
 *   extremes, sliding feet: design/anatomy_audit.md section 6);
 * - the throw (WitchUnit.throwPotion swings the arm, so attackTime runs): the staff raised high before her with the
 *   disc spinning fast, the left hand tossing the potion underarm.
 * The angles were laid out with tools/pose_preview.py (DSL degrees; a part takes -x, +y, -z radians); their extremes
 * are design/poses/sunforged_field_medic_v2.json (walk_A-D, cast) - keep it in step with the numbers here.
 */
@OnlyIn(Dist.CLIENT)
public class FieldMedicUnitModel extends FieldMedicModel<WitchUnit> {

    private final ModelPart body, head, staffArm, staff, disc, ribbon, leftArm, robeFront, robeBack, robeLeft, robeRight;
    private final HumanBase base;

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
        base = new HumanBase(MedicAnim.BONES, bones,
            new HumanBase.Clip(MedicAnim.IDLE_P, MedicAnim.IDLE_LOOP, MedicAnim.IDLE_ROT, MedicAnim.IDLE_MOVE),
            new HumanBase.Clip(MedicAnim.WALK_P, MedicAnim.WALK_LOOP, MedicAnim.WALK_ROT, MedicAnim.WALK_MOVE),
            MedicAnim.IDLE_TICKS, MedicAnim.WALK_PHASE);
    }

    @Override
    public void setupAnim(WitchUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (body == null)
            return;
        float t = ageInTicks + (entity.getId() & 63);
        float breathe = Life.breath(ageInTicks, entity.getId(), 90f);   // the shared life layer: quick in-breath, long out-breath
        float cast = this.attackTime > 0f ? Mth.sin(this.attackTime * Mth.PI) : 0f;

        // ---- the shared base: the staff-ready idle crossfaded with the walk by the stride (the walk data parts the
        // front and back panels for the legs); the throw fades the idle's drift out and back in
        float amt = base.pose(limbSwing, limbSwingAmount, ageInTicks, entity.getId(),
            HumanBase.drift(this.attackTime > 0f ? this.attackTime : -1f));
        float w = base.walkProgress(limbSwing) * Mth.TWO_PI;   // the stride's phase, for the props that swing with it
        if (robeFront != null) robeFront.xRot += 0.02f * Mth.sin(t * 0.08f);
        if (robeBack != null) robeBack.xRot += 0.02f * Mth.sin(t * 0.08f + 1f);
        if (robeLeft != null) robeLeft.zRot += Mth.sin(w) * 0.05f * amt;
        if (robeRight != null) robeRight.zRot += Mth.sin(w) * 0.05f * amt;

        // ---- breath, the look
        body.y -= breathe * 0.08f;
        if (leftArm != null) leftArm.xRot += breathe * 0.02f;
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }

        // ---- the disc and the ribbons
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
