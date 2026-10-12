package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.brass.TeslaWalkerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Tesla Walker: the illager body as a dwarf under a brass coil pack (tools/gen_brass_skins.py), with the
 * coil itself - a lightning rod standing up off the pack - drawn as one block render ({@link CoilLayer}), and the Field
 * Upgrade layer on top. One extra block render per walker per frame.
 */
@OnlyIn(Dist.CLIENT)
public class TeslaWalkerRenderer extends AbstractVillagerUnitRenderer<TeslaWalkerUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/tesla_walker_unit.png");

    public TeslaWalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new CoilLayer(this));
        this.addLayer(new BrassUpgradeLayer<>(this, 1.0f));
    }

    public ResourceLocation getTextureLocation(TeslaWalkerUnit unit) {
        return TEXTURE;
    }

    /** The tesla coil on the pack: a lightning rod rising from between the shoulders (model space is y-down: flipped upright). */
    static class CoilLayer extends RenderLayer<TeslaWalkerUnit, VillagerUnitModel<TeslaWalkerUnit>> {
        CoilLayer(TeslaWalkerRenderer parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TeslaWalkerUnit entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(180));   // y-down model space -> upright block
            pose.translate(-0.35, 0.1, -0.55);            // behind the shoulders, rising above the head
            pose.scale(0.7f, 1.1f, 0.7f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.LIGHTNING_ROD.defaultBlockState(), pose,
                buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
}
