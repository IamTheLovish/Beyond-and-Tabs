package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged T2 constructor: the worker illager body in white-and-gold architect robes with a gold circlet, a
 * plumb-bob and a blueprint satchel (tools/gen_t2t3_skins.py). Its own renderer rather than VillagerUnitRenderer
 * because that one adds the biome/profession overlay layer, which would paint the villager's work clothes over
 * the robes.
 */
@OnlyIn(Dist.CLIENT)
public class RoyalArchitectRenderer extends AbstractVillagerUnitRenderer<RoyalArchitectUnit> {
    private static final ResourceLocation ROYAL_ARCHITECT_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/royal_architect_unit.png");

    public RoyalArchitectRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        // the T2 constructor's back banner in the owner's colour: found at a glance among the T1 workers
        this.addLayer(new com.solegendary.reignofnether.unit.modelling.layers.BackBannerLayer<>(this));
        this.addLayer(new ItemInHandLayer<RoyalArchitectUnit, VillagerUnitModel<RoyalArchitectUnit>>(this, context.getItemInHandRenderer()) {
            public void render(PoseStack pose, MultiBufferSource mbs, int light, RoyalArchitectUnit unit, float a, float b, float c, float d, float e, float f) {
                // tools only while working or fighting, like the T1 villager worker
                if ((unit.getBuildRepairGoal() != null && unit.getBuildRepairGoal().isBuilding()) ||
                    (unit.getGatherResourceGoal() != null && unit.getGatherResourceGoal().isGathering()) ||
                    (unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null)) {
                    super.render(pose, mbs, light, unit, a, b, c, d, e, f);
                }
            }
        });
    }

    public ResourceLocation getTextureLocation(RoyalArchitectUnit unit) {
        return ROYAL_ARCHITECT_UNIT;
    }
}
