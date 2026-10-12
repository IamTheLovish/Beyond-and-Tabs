package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.brass.RepairCrawlerUnit;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Repair Crawler: the cave spider model at 0.65x as a low brass crawler with copper welding arms and a
 * green repair lamp on its back (64x32, the spider layout, tools/gen_brass_skins.py), and the Field Upgrade layer.
 */
@OnlyIn(Dist.CLIENT)
public class RepairCrawlerRenderer extends SpiderRenderer<RepairCrawlerUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/repair_crawler_unit.png");
    static final float SCALE = 0.65f;

    public RepairCrawlerRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.CAVE_SPIDER);
        this.shadowRadius *= SCALE;
        this.addLayer(new BrassUpgradeLayer<>(this, 0.8f));
    }

    @Override
    public ResourceLocation getTextureLocation(RepairCrawlerUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(RepairCrawlerUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
