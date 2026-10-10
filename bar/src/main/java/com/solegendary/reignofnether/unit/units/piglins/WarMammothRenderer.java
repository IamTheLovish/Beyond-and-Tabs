package com.solegendary.reignofnether.unit.units.piglins;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ravager;

/**
 * Client only: the War Mammoth is the ravager model at {@link WarMammothUnit#SCALE}, re-skinned with a woolly hide,
 * ivory tusks and the Horde's rust-and-timber archer platform on its back (tools/gen_t2t3_skins.py).
 */
public class WarMammothRenderer extends RavagerRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/war_mammoth_unit.png");

    public WarMammothRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= WarMammothUnit.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(Ravager entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Ravager entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(WarMammothUnit.SCALE, WarMammothUnit.SCALE, WarMammothUnit.SCALE);
    }
}
