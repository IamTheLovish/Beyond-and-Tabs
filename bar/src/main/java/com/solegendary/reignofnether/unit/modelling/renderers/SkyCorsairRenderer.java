package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit;
import net.minecraft.client.model.GhastModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tidewrought Sky Corsair (interim body): the vanilla ghast model under a teal-and-brass canvas balloon skin with a
 * timber gondola band and hanging rigging for tentacles (tools/gen_tide_skins.py), drawn at
 * {@link SkyCorsairUnit#SCALE} of a wild ghast - EntityRegistrar sizes the hitbox the same way.
 */
@OnlyIn(Dist.CLIENT)
public class SkyCorsairRenderer extends MobRenderer<SkyCorsairUnit, GhastModel<SkyCorsairUnit>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/sky_corsair_unit.png");

    public SkyCorsairRenderer(EntityRendererProvider.Context context) {
        super(context, new GhastModel<>(context.bakeLayer(ModelLayers.GHAST)), 1.5F * SkyCorsairUnit.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(SkyCorsairUnit unit) {
        return TEXTURE;
    }

    @Override
    protected void scale(SkyCorsairUnit unit, PoseStack poseStack, float partialTickTime) {
        float s = 4.5f * SkyCorsairUnit.SCALE;   // GhastRenderer draws a wild ghast at 4.5
        poseStack.scale(s, s, s);
    }
}
