package com.solegendary.reignofnether.unit.units.villagers;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Client only: the Sun Colossus is the iron golem model at {@link SunColossusUnit#SCALE}, re-skinned as a gilded
 * paladin statue - white stone, gold plate, a sun on the chest and a crown (tools/gen_t2t3_skins.py).
 */
public class SunColossusRenderer extends IronGolemRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/sun_colossus_unit.png");

    public SunColossusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= SunColossusUnit.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SunColossusUnit.SCALE, SunColossusUnit.SCALE, SunColossusUnit.SCALE);
    }
}
