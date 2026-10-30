package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.HumanActs;
import com.solegendary.reignofnether.unit.modelling.animations.HumanBase;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.FieldMasonModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.MasonAnim;
import com.solegendary.reignofnether.unit.units.engineers.FieldMasonUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Field Mason (field_mason_unit, a combat engineer) in motion, over its generated body
 * (FieldMasonModel, from design/models/sunforged_mason_v2.json by tools/dsl_to_game.py; the generator is
 * tools/gen_mason.py) - until Oct 21 the villager body (lovish 21:20: every human unit moves onto the human body).
 *
 * - walk and idle: the SHARED human base clips (HumanBase, from design/anims/mason.json, the sword class: the hammer
 *   arm swinging a little, the free arm more, a heavy-shouldered stride; the tool apron swinging with the thighs). On
 *   top: the breath, the look;
 * - work and fight keep the villager model's timing (no base work clip exists yet - HumanActs): the hammer swings on
 *   ArmSwingingUnit's keys while he builds, and chops at the illager's attackTime curve against a target.
 */
@OnlyIn(Dist.CLIENT)
public class FieldMasonUnitModel extends FieldMasonModel<FieldMasonUnit> {

    private final ModelPart body, head, rArm, rFore, lArm;
    private final HumanBase base;

    public FieldMasonUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        rArm = bone("right_arm");
        rFore = bone("right_fore");
        lArm = bone("left_arm");
        base = new HumanBase(MasonAnim.BONES, bones,
            new HumanBase.Clip(MasonAnim.IDLE_P, MasonAnim.IDLE_LOOP, MasonAnim.IDLE_ROT, MasonAnim.IDLE_MOVE),
            new HumanBase.Clip(MasonAnim.WALK_P, MasonAnim.WALK_LOOP, MasonAnim.WALK_ROT, MasonAnim.WALK_MOVE),
            MasonAnim.IDLE_TICKS, MasonAnim.WALK_PHASE);
    }

    @Override
    public void setupAnim(FieldMasonUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        restPose();
        if (body == null)
            return;
        float breathe = Life.breath(ageInTicks, entity.getId(), 85f);
        base.pose(limbSwing, limbSwingAmount, ageInTicks, entity.getId(),
            HumanBase.drift(this.attackTime > 0f ? this.attackTime : -1f));
        body.y -= breathe * 0.08f;
        if (lArm != null) lArm.xRot += breathe * 0.02f;
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }
        if (!HumanActs.workSwing(entity, rArm))
            HumanActs.chop(this.attackTime, rArm, rFore, body);
    }
}
