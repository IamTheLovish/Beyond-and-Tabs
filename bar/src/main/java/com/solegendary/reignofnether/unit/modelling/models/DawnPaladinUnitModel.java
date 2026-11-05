package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.HumanActs;
import com.solegendary.reignofnether.unit.modelling.animations.HumanBase;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.DawnPaladinAnim;
import com.solegendary.reignofnether.unit.modelling.models.gen.DawnPaladinModel;
import com.solegendary.reignofnether.unit.units.heroes.DawnPaladinUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Dawn Paladin (dawn_paladin_unit, the Sunforged hero) in motion, over its generated body (DawnPaladinModel,
 * from design/models/sunforged_dawn_paladin_v2.json by tools/dsl_to_game.py; the generator is
 * tools/gen_dawn_paladin.py) - until Oct 21 the villager body (lovish 21:20: every human unit moves onto the human
 * body).
 *
 * - walk and idle: the SHARED human base clips (HumanBase, from design/anims/dawn_paladin.json, the sword-and-shield
 *   class: upright and measured in plate, the sword arm swinging a little, the shield arm held; the surcoat's skirts
 *   swinging with the thighs). On top: the breath, the look;
 * - the blow keeps the illager's attackTime timing (no base sword clip exists yet - HumanActs.chop); the shield comes
 *   up a little through it.
 */
@OnlyIn(Dist.CLIENT)
public class DawnPaladinUnitModel extends DawnPaladinModel<DawnPaladinUnit> {

    private final ModelPart body, head, rArm, rFore, lArm, surFront, surBack;
    private final HumanBase base;

    public DawnPaladinUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        rArm = bone("right_arm");
        rFore = bone("right_fore");
        lArm = bone("left_arm");
        surFront = bone("surcoat_front");
        surBack = bone("surcoat_back");
        base = new HumanBase(DawnPaladinAnim.BONES, bones,
            new HumanBase.Clip(DawnPaladinAnim.IDLE_P, DawnPaladinAnim.IDLE_LOOP, DawnPaladinAnim.IDLE_ROT,
                DawnPaladinAnim.IDLE_MOVE),
            new HumanBase.Clip(DawnPaladinAnim.WALK_P, DawnPaladinAnim.WALK_LOOP, DawnPaladinAnim.WALK_ROT,
                DawnPaladinAnim.WALK_MOVE),
            DawnPaladinAnim.IDLE_TICKS, DawnPaladinAnim.WALK_PHASE);
    }

    @Override
    public void setupAnim(DawnPaladinUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        restPose();
        if (body == null)
            return;
        float t = ageInTicks + (entity.getId() & 63);
        float breathe = Life.breath(ageInTicks, entity.getId(), 95f);
        base.pose(limbSwing, limbSwingAmount, ageInTicks, entity.getId(),
            HumanBase.drift(this.attackTime > 0f ? this.attackTime : -1f));
        body.y -= breathe * 0.08f;
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }
        if (surFront != null) surFront.xRot += 0.012f * Mth.sin(t * 0.08f);
        if (surBack != null) surBack.xRot += 0.012f * Mth.sin(t * 0.08f + 1.1f);
        if (this.attackTime > 0f) {
            HumanActs.chop(this.attackTime, rArm, rFore, body);
            if (lArm != null) lArm.xRot -= 0.35f * Mth.sin(this.attackTime * Mth.PI);   // the shield up into the blow
        }
    }
}
