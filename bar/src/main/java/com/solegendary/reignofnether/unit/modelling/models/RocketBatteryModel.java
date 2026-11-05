package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit;
import com.solegendary.reignofnether.unit.units.storm.RocketLaunchPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Rocket Battery's body: the shared illager model with its arms taken over while a volley goes - both hands up on
 * the rack, braced, jolted back by each rocket (RocketLaunchPose) - instead of the generic attack swing the mortar crews
 * use. Outside a volley it is exactly the shared model.
 */
@OnlyIn(Dist.CLIENT)
public class RocketBatteryModel extends VillagerUnitModel<RocketBatteryUnit> {

    public RocketBatteryModel(ModelPart part) {
        super(part);
    }

    @Override
    public void setupAnim(RocketBatteryUnit unit, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(unit, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        int left = unit.getLaunchTicks();
        if (left <= 0)
            return;
        float partial = ageInTicks - unit.tickCount;
        float age = RocketLaunchPose.age(left, partial);
        float brace = RocketLaunchPose.rackLift(age), recoil = RocketLaunchPose.recoil(age);
        this.crossedArms.visible = false;
        this.rightArm.visible = true;
        this.leftArm.visible = true;
        this.rightArm.xRot = Mth.lerp(brace, this.rightArm.xRot, -1.35F) + recoil * 0.5F;
        this.leftArm.xRot = Mth.lerp(brace, this.leftArm.xRot, -1.2F) + recoil * 0.5F;
        this.rightArm.yRot = Mth.lerp(brace, this.rightArm.yRot, -0.25F);
        this.leftArm.yRot = Mth.lerp(brace, this.leftArm.yRot, 0.25F);
        this.rightArm.zRot = Mth.lerp(brace, this.rightArm.zRot, 0.0F);
        this.leftArm.zRot = Mth.lerp(brace, this.leftArm.zRot, 0.0F);
        this.head.xRot -= recoil * 0.25F;   // the kick throws the head up
    }
}
