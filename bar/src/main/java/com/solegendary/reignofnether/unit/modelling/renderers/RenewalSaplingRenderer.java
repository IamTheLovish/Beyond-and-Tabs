package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.verdant.RenewalSaplingUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The Seed of Renewal's sapling: a flowering azalea drawn where the unit stands (the block's own model, as the Vine
 * Snare draws its bush), centred on the hitbox.
 */
@OnlyIn(Dist.CLIENT)
public class RenewalSaplingRenderer extends EntityRenderer<RenewalSaplingUnit> {
    private static final BlockState SAPLING = Blocks.FLOWERING_AZALEA.defaultBlockState();

    public RenewalSaplingRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(RenewalSaplingUnit sapling, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(SAPLING, poseStack, buffer, packedLight,
            OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        poseStack.popPose();
        super.render(sapling, yaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RenewalSaplingUnit sapling) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
