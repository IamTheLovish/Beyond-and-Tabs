package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.HumanActs;
import com.solegendary.reignofnether.unit.modelling.animations.HumanBase;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.ArchitectAnim;
import com.solegendary.reignofnether.unit.modelling.models.gen.RoyalArchitectModel;
import com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Royal Architect (royal_architect_unit, the T2 builder) in motion, over its generated body
 * (RoyalArchitectModel, from design/models/sunforged_architect_v2.json by tools/dsl_to_game.py; the generator is
 * tools/gen_architect.py) - until Oct 21 the villager body (lovish 21:20: every human unit moves onto the human body).
 *
 * - walk and idle: the SHARED human base clips (HumanBase, from design/anims/architect.json: a brisk walk, the rolled
 *   plan carried like a baton in the left hand, the free right arm swinging; the coat's four skirts parting for the
 *   legs). On top: the breath, the look;
 * - work and fight keep the villager model's timing (no base work clip exists yet - HumanActs): building or gathering
 *   the mallet comes from his belt to his right hand and swings on ArmSwingingUnit's keys; with a target it chops at
 *   the illager's attackTime curve.
 */
@OnlyIn(Dist.CLIENT)
public class RoyalArchitectUnitModel extends RoyalArchitectModel<RoyalArchitectUnit> {

    private final ModelPart body, head, rArm, rFore, mallet, beltMallet;
    private final HumanBase base;

    public RoyalArchitectUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        rArm = bone("right_arm");
        rFore = bone("right_fore");
        mallet = bone("mallet");
        beltMallet = bone("belt_mallet");
        base = new HumanBase(ArchitectAnim.BONES, bones,
            new HumanBase.Clip(ArchitectAnim.IDLE_P, ArchitectAnim.IDLE_LOOP, ArchitectAnim.IDLE_ROT, ArchitectAnim.IDLE_MOVE),
            new HumanBase.Clip(ArchitectAnim.WALK_P, ArchitectAnim.WALK_LOOP, ArchitectAnim.WALK_ROT, ArchitectAnim.WALK_MOVE),
            ArchitectAnim.IDLE_TICKS, ArchitectAnim.WALK_PHASE);
    }

    @Override
    public void setupAnim(RoyalArchitectUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        restPose();
        if (body == null)
            return;
        boolean tool = HumanActs.working(entity) || HumanActs.fighting(entity);
        if (mallet != null) mallet.visible = tool;
        if (beltMallet != null) beltMallet.visible = !tool;
        float breathe = Life.breath(ageInTicks, entity.getId(), 90f);
        base.pose(limbSwing, limbSwingAmount, ageInTicks, entity.getId(),
            HumanBase.drift(this.attackTime > 0f ? this.attackTime : -1f));
        body.y -= breathe * 0.07f;
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }
        if (!HumanActs.workSwing(entity, rArm))
            HumanActs.chop(this.attackTime, rArm, rFore, body);
    }
}
