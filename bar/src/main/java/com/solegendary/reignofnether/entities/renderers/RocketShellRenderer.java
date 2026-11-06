package com.solegendary.reignofnether.entities.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.entities.models.RocketShellModel;
import com.solegendary.reignofnether.unit.units.storm.RocketShell;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws a {@link RocketShell}: the finned rocket pointing along its flight (yaw and pitch as vanilla's arrow), lit by
 * the world, and its exhaust flame at full brightness (the eyes render type, like the Bone Dragon's glow) flickering in
 * length. The smoke trail behind it is BarFx's.
 */
@OnlyIn(Dist.CLIENT)
public class RocketShellRenderer extends EntityRenderer<RocketShell> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/entities/rocket_shell.png");
    /** The model is 11 px long; drawn at 1.4x it is about a block with its flame. */
    private static final float SCALE = 1.4F;

    private final RocketShellModel model;

    public RocketShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new RocketShellModel(context.bakeLayer(RocketShellModel.LAYER_LOCATION));
    }

    @Override
    public void render(RocketShell shell, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, shell.yRotO, shell.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, shell.xRotO, shell.getXRot())));
        pose.scale(SCALE, SCALE, SCALE);
        model.body.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        float age = shell.tickCount + partialTick;
        model.flame.xScale = 0.8F + 0.45F * Math.abs(Mth.sin(age * 2.3F));
        model.flame.render(pose, buffer.getBuffer(RenderType.eyes(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(shell, entityYaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RocketShell shell) {
        return TEXTURE;
    }
}
