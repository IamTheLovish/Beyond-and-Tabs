package com.solegendary.reignofnether.unit.modelling.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.modelling.models.RocketRackModel;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit;
import com.solegendary.reignofnether.unit.units.storm.RocketCharge;
import com.solegendary.reignofnether.unit.units.storm.RocketLaunchPose;
import com.solegendary.reignofnether.unit.units.storm.RocketRackGeometry;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Rocket Battery's lightning-rod rack as geometry on its back (RocketRackModel). It shows three things from numbers
 * the unit syncs:
 * <ul>
 * <li>the reload and the charge (RocketCharge): after a volley the rods stand empty, take their bolts one after another
 * as the cooldown runs out, and in the last moments before the next volley (with a target) the rack lifts and the orbs
 * flare and flicker - the wind-up, with no delay to the shot;</li>
 * <li>the volley (RocketLaunchPose): each rod's orb goes out as its bolt leaves, the rack is kicked back by each one and
 * settles;</li>
 * <li>at rest, a dim dormant glow.</li>
 * </ul>
 * It replaces the bow the crew used to be drawn holding - a bow is only what UnitBowAttackGoal needs to fire.
 */
@OnlyIn(Dist.CLIENT)
public class RocketRackLayer extends RenderLayer<RocketBatteryUnit, VillagerUnitModel<RocketBatteryUnit>> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/entities/rocket_rack.png");
    /** The glow of a loaded orb at rest, and how much brighter a full charge makes it. */
    private static final float DORMANT = 0.55F;

    private final RocketRackModel model;

    public RocketRackLayer(LivingEntityRenderer<RocketBatteryUnit, VillagerUnitModel<RocketBatteryUnit>> parent, EntityModelSet models) {
        super(parent);
        this.model = new RocketRackModel(models.bakeLayer(RocketRackModel.LAYER_LOCATION));
    }

    /** The rack's lift (0 rest, 1 firing position) for a frame: from the charge between volleys, from the launch during one. */
    public static float liftOf(RocketBatteryUnit unit, float partialTick) {
        int left = unit.getLaunchTicks();
        if (left > 0)
            return RocketLaunchPose.chargedLift(RocketLaunchPose.age(left, partialTick));
        return RocketCharge.charge(unit.getReloadTicks(), unit.isEngaged());
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, RocketBatteryUnit unit, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (unit.isInvisible())
            return;
        int left = unit.getLaunchTicks();
        float age = RocketLaunchPose.age(left, partialTick);
        float recoil = left > 0 ? RocketLaunchPose.recoil(age) : 0f;
        float charge = left > 0 ? 0f : RocketCharge.charge(unit.getReloadTicks(), unit.isEngaged());
        model.pose(liftOf(unit, partialTick), recoil);
        // each rod's orb: out as its bolt leaves during a volley, filling as the cooldown runs out between volleys; it
        // flickers faster and wider as the charge builds
        float flicker = 1f + (0.06f + 0.22f * charge) * Mth.sin(ageInTicks * (1.7f + 2.5f * charge) + unit.getId());
        for (int rod = 0; rod < RocketRackGeometry.RODS; rod++) {
            float fill = left > 0 ? RocketLaunchPose.loaded(rod, age) : RocketCharge.fill(rod, unit.getReloadTicks());
            float flare = charge > 0.6f ? 1f + 0.35f * Mth.sin(ageInTicks * 3.1f + rod * 1.9f) * (charge - 0.6f) : 1f;
            model.orb(rod, fill * flicker * flare * (0.8f + 0.2f * charge));
        }
        float glow = Mth.clamp(DORMANT + (1f - DORMANT) * charge, 0f, 1f);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        int overlay = LivingEntityRenderer.getOverlayCoords(unit, 0.0F);
        model.rack.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, overlay);
        // the orbs first as solid blue at full light, then the additive glow over them: eyes() alone only ADDS its blue
        // to whatever is behind, so over the copper and the grass the orbs came out salmon-pink (storm critic round 2)
        model.glow.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        model.glow.render(pose, buffer.getBuffer(RenderType.eyes(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F);
        pose.popPose();
    }
}
