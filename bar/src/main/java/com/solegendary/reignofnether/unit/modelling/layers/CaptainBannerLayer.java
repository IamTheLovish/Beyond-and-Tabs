package com.solegendary.reignofnether.unit.modelling.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Function;

/**
 * The captain's banner on a generated body's head (the Castle's Promote Illager puts the ominous banner in the HEAD
 * slot of a Halberdier, Crossbow Levy or Squire Runner; HudClientEvents names it "Captain"). The illager body drew it
 * through CustomUnitHeadLayer on its 8 px head; a generated body's head is a nested part of its own size, so this
 * walks the head's path (the generated models' path("head"), root side first), scales the vanilla head frame to the
 * body's head height and draws the banner exactly as CustomUnitHeadLayer does. Only banners: no other head item
 * belongs on these bodies.
 */
@OnlyIn(Dist.CLIENT)
public class CaptainBannerLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private final ItemInHandRenderer items;
    private final Function<M, ModelPart[]> headPath;
    private final float headScale;

    /**
     * @param headPath  the head part and its ancestors, root side first
     * @param headScale the body's head height over the vanilla head's 8 px (where the banner's pole sits)
     */
    public CaptainBannerLayer(RenderLayerParent<T, M> parent, ItemInHandRenderer items, Function<M, ModelPart[]> headPath,
                              float headScale) {
        super(parent);
        this.items = items;
        this.headPath = headPath;
        this.headScale = headScale;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!(stack.getItem() instanceof BannerItem) || entity.isInvisible())
            return;
        ModelPart[] chain = headPath.apply(this.getParentModel());
        if (chain == null || chain.length == 0)
            return;
        pose.pushPose();
        for (ModelPart p : chain)
            p.translateAndRotate(pose);
        pose.scale(headScale, headScale, headScale);
        CustomUnitHeadLayer.translateToHead(pose, false, stack.getItem());
        items.renderItem(entity, stack, ItemDisplayContext.HEAD, false, pose, buffers, light);
        pose.popPose();
    }
}
