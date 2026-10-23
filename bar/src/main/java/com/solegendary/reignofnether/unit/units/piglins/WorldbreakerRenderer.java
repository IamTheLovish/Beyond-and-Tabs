package com.solegendary.reignofnether.unit.units.piglins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.renderers.PiglinUnitRenderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * Client only: the Worldbreaker is the Horde's Brute body at {@link WorldbreakerUnit#SCALE}x in the Brute's skin - a
 * PLACEHOLDER (no war paint yet; Lane B draws the real body).
 */
public class WorldbreakerRenderer extends PiglinUnitRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/brute_unit.png");

    public WorldbreakerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= WorldbreakerUnit.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(Mob entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Mob entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(WorldbreakerUnit.SCALE, WorldbreakerUnit.SCALE, WorldbreakerUnit.SCALE);
    }
}
