package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.GeomancerUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Geomancer (and the Thunder Sage): the worker illager body in a red robe with a jade sash (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class GeomancerRenderer extends AbstractVillagerUnitRenderer<GeomancerUnit> {
    private static final ResourceLocation GEOMANCER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/geomancer_unit.png");

    public GeomancerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<GeomancerUnit, VillagerUnitModel<GeomancerUnit>>(this, context.getItemInHandRenderer()) {
            public void render(PoseStack pose, MultiBufferSource mbs, int light, GeomancerUnit unit, float a, float b, float c, float d, float e, float f) {
                // tools only while working or fighting, like the other factions' workers
                if ((unit.getBuildRepairGoal() != null && unit.getBuildRepairGoal().isBuilding()) ||
                    (unit.getGatherResourceGoal() != null && unit.getGatherResourceGoal().isGathering()) ||
                    (unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null)) {
                    super.render(pose, mbs, light, unit, a, b, c, d, e, f);
                }
            }
        });
    }

    public ResourceLocation getTextureLocation(GeomancerUnit unit) {
        return GEOMANCER_UNIT;
    }
}
