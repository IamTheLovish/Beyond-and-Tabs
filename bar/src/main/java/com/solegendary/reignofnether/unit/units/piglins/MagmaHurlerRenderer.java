package com.solegendary.reignofnether.unit.units.piglins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.renderers.PiglinUnitRenderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * Client only: the Magma Hurler is the Horde's piglin body at {@link MagmaHurlerUnit#SCALE}x in the Headhunter's skin -
 * a PLACEHOLDER (Lane B draws the real body and the furnace-cauldron on its back; slice T3b-10).
 */
public class MagmaHurlerRenderer extends PiglinUnitRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/headhunter_unit.png");

    public MagmaHurlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= MagmaHurlerUnit.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(Mob entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Mob entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MagmaHurlerUnit.SCALE, MagmaHurlerUnit.SCALE, MagmaHurlerUnit.SCALE);
    }
}
