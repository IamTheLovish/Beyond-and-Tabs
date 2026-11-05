package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.unit.modelling.layers.RocketRackLayer;
import com.solegendary.reignofnether.unit.modelling.models.RocketBatteryModel;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit;
import com.solegendary.reignofnether.unit.units.storm.RocketLaunchPose;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Rocket Battery: the illager body in a red-lacquer crew coat with a gold powder belt (tools/gen_storm_skins.py),
 * a rocket rack on its back as geometry (RocketRackLayer), and a launch animation - the crew leans back with each rocket,
 * the arms brace on the rack (RocketBatteryModel). The old renderer's layers: the held bow (ItemInHandLayer) is dropped on
 * purpose - the crew carries the rack, the bow is only what UnitBowAttackGoal needs; the team colour, head, armour and
 * trim layers are the shared ones.
 */
@OnlyIn(Dist.CLIENT)
public class RocketBatteryRenderer extends AbstractVillagerUnitRenderer<RocketBatteryUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/rocket_battery_unit.png");
    /**
     * How far a full recoil leans the whole crew back, and how far the wind-up leans it forward into the shot, degrees. The
     * first version leaned 5 degrees the wrong way for a kick, which a 1600x900 frame of the old tour could not show
     * (runs 42056293582 / 42056297739: "the crew is a statue"). A positive turn about X leans the crew FORWARD, as with
     * every vanilla pitching mob (dolphin, phantom).
     */
    private static final float LEAN_BACK_DEG = 18.0F, LEAN_FORWARD_DEG = 12.0F;

    public RocketBatteryRenderer(EntityRendererProvider.Context context) {
        super(context, new RocketBatteryModel(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new RocketRackLayer(this, context.getModelSet()));
    }

    @Override
    protected void setupRotations(RocketBatteryUnit unit, PoseStack pose, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(unit, pose, ageInTicks, rotationYaw, partialTicks);
        int left = unit.getLaunchTicks();
        if (left > 0) {
            float age = RocketLaunchPose.age(left, partialTicks);
            pose.mulPose(Axis.XP.rotationDegrees(LEAN_FORWARD_DEG * RocketLaunchPose.windup(age) - LEAN_BACK_DEG * RocketLaunchPose.recoil(age)));
        }
    }

    public ResourceLocation getTextureLocation(RocketBatteryUnit unit) {
        return TEXTURE;
    }
}
