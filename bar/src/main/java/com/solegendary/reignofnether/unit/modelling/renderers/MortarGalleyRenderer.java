package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderAlligatorSnappingTurtle;
import com.github.alexthe666.alexsmobs.entity.EntityAlligatorSnappingTurtle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tidewrought Mortar Galley: Alex's Mobs' own alligator snapping turtle model, texture and moss layer, drawn
 * {@link MortarGalleyUnit#SCALE}x to match the unit's hitbox (EntityRegistrar sizes the type the same way).
 */
@OnlyIn(Dist.CLIENT)
public class MortarGalleyRenderer extends RenderAlligatorSnappingTurtle {

    public MortarGalleyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.75F * MortarGalleyUnit.SCALE;
    }

    @Override
    protected void scale(EntityAlligatorSnappingTurtle entity, PoseStack poseStack, float partialTickTime) {
        super.scale(entity, poseStack, partialTickTime);   // AM's per-turtle scale (always 1 for a galley)
        poseStack.scale(MortarGalleyUnit.SCALE, MortarGalleyUnit.SCALE, MortarGalleyUnit.SCALE);
    }
}
