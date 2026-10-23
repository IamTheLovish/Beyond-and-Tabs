package com.solegendary.reignofnether.unit.units.tide;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Client only: the Abyssal Hydra is the iron golem model in a deep-sea skin (the Sun Colossus' skin tinted teal) at
 * {@link AbyssalHydraUnit#SCALE}x - a PLACEHOLDER (Lane B draws the three heads and the barnacled mound; slice T3b-10).
 */
public class AbyssalHydraRenderer extends IronGolemRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/abyssal_hydra_unit.png");

    public AbyssalHydraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= AbyssalHydraUnit.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(AbyssalHydraUnit.SCALE, AbyssalHydraUnit.SCALE, AbyssalHydraUnit.SCALE);
    }
}
