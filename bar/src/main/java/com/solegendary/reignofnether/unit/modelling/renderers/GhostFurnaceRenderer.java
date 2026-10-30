package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.brass.GhostFurnaceUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The Soul Engineer's Ghost Furnace: a lit blast furnace with a soul campfire's flame on top, drawn where the unit
 * stands (the blocks' own models, as the Renewal Sapling draws its azalea), centred on the hitbox.
 */
@OnlyIn(Dist.CLIENT)
public class GhostFurnaceRenderer extends EntityRenderer<GhostFurnaceUnit> {
    private static final BlockState FURNACE = Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true);
    private static final BlockState FLAME = Blocks.SOUL_CAMPFIRE.defaultBlockState();

    public GhostFurnaceRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(GhostFurnaceUnit furnace, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(FURNACE, poseStack, buffer, packedLight,
            OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        poseStack.translate(0, 1, 0);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(FLAME, poseStack, buffer, packedLight,
            OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        poseStack.popPose();
        super.render(furnace, yaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostFurnaceUnit furnace) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
