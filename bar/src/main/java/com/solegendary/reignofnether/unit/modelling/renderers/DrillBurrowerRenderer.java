package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SilverfishRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Drill Burrower: the silverfish model at 2.5x as a segmented brass drill-worm - riveted rings, a soot
 * iron drill head, furnace-orange vents along the back (64x32, tools/gen_brass_skins.py) - and the Field Upgrade layer.
 * While it is under the ground it is invisible (vanilla skips the model; the owner sees the translucent ghost).
 */
@OnlyIn(Dist.CLIENT)
public class DrillBurrowerRenderer extends SilverfishRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/drill_burrower_unit.png");
    static final float SCALE = 2.5f;

    public DrillBurrowerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= SCALE;
        this.addLayer(new BrassUpgradeLayer<>(this, 0.6f));
    }

    @Override
    public ResourceLocation getTextureLocation(Silverfish entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Silverfish entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
