package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.brass.ClockworkSpiderUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Clockwork Spider: the spider model as a brass turret walker - a riveted brass abdomen carrying the
 * twin-barrelled turret, jointed soot-and-brass legs (64x32, tools/gen_brass_skins.py); the vanilla eye layer reads as
 * its furnace lamps. The Field Upgrade layer on top.
 */
@OnlyIn(Dist.CLIENT)
public class ClockworkSpiderRenderer extends SpiderRenderer<ClockworkSpiderUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/clockwork_spider_unit.png");

    public ClockworkSpiderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.addLayer(new BrassUpgradeLayer<>(this, 1.0f));
    }

    @Override
    public ResourceLocation getTextureLocation(ClockworkSpiderUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(ClockworkSpiderUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.0f, 1.0f, 1.0f);
    }
}
