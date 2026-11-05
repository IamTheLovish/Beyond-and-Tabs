package com.solegendary.reignofnether.unit.modelling.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.modelling.models.RocketRackModel;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit;
import com.solegendary.reignofnether.unit.units.storm.RocketLaunchPose;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Rocket Battery's rocket rack as geometry on its back: it lifts forward over the shoulder when a volley goes, is
 * kicked back by each rocket and settles (RocketLaunchPose, from the unit's synced launch ticks). It replaces the bow
 * the crew used to be drawn holding - a bow is only what UnitBowAttackGoal needs to fire, not what the crew carries.
 */
@OnlyIn(Dist.CLIENT)
public class RocketRackLayer extends RenderLayer<RocketBatteryUnit, VillagerUnitModel<RocketBatteryUnit>> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/entities/rocket_rack.png");

    private final RocketRackModel model;

    public RocketRackLayer(LivingEntityRenderer<RocketBatteryUnit, VillagerUnitModel<RocketBatteryUnit>> parent, EntityModelSet models) {
        super(parent);
        this.model = new RocketRackModel(models.bakeLayer(RocketRackModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, RocketBatteryUnit unit, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (unit.isInvisible())
            return;
        float age = RocketLaunchPose.age(unit.getLaunchTicks(), partialTick);
        model.pose(RocketLaunchPose.rackLift(age), RocketLaunchPose.recoil(age));
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        model.rack.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight,
            LivingEntityRenderer.getOverlayCoords(unit, 0.0F));
        pose.popPose();
    }
}
