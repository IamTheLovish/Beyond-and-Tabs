package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.brass.Machine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tinker's Field Upgrade made visible (design/brassworks_plan.md 3.1): a {@link Machine} whose synced upgrade byte
 * is {@link Heat#UPGRADE_PLATING} wears a block of brass plates on its torso; {@link Heat#UPGRADE_BARREL} a brass bar
 * along its facing. Drawn in model space (y down, the origin 1.5 blocks above the feet), so the same layer fits every
 * body at hand: the torso of an illager, a golem or a strider all sit near model (0, 0.6, 0); {@code scale} tunes it per
 * renderer. One block render per upgraded machine per frame, nothing when there is no upgrade.
 */
@OnlyIn(Dist.CLIENT)
public class BrassUpgradeLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final float scale;

    public BrassUpgradeLayer(RenderLayerParent<T, M> parent, float scale) {
        super(parent);
        this.scale = scale;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof Machine m) || entity.isInvisible())
            return;
        byte up = m.upgrade();
        if (up == Heat.UPGRADE_NONE)
            return;
        var blocks = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        if (up == Heat.UPGRADE_PLATING) {
            // a slab of riveted brass sunk into the chest, its edges showing front and back
            float s = 0.42f * scale;
            pose.translate(-s / 2, 0.45 * scale, -s / 2 - 0.08 * scale);
            pose.scale(s, s * 0.9f, s);
            blocks.renderSingleBlock(Blocks.WAXED_CUT_COPPER.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } else {
            // a long brass barrel reaching forward (-z in model space) at shoulder height
            float w = 0.14f * scale, len = 0.9f * scale;
            pose.translate(-w / 2 + 0.05 * scale, 0.55 * scale, -len - 0.1 * scale);
            pose.scale(w, w, len);
            blocks.renderSingleBlock(Blocks.WAXED_CUT_COPPER.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }
}
