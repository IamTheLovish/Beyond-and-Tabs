package com.solegendary.reignofnether.unit.units.villagers;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Client only: the Cathedral Warden is the Sun Colossus' gilded golem (its skin) at {@link CathedralWardenUnit#SCALE}x -
 * a PLACEHOLDER (Lane B draws the belfry and howdah; slice T3b-10).
 */
public class CathedralWardenRenderer extends SunColossusRenderer {

    public CathedralWardenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= CathedralWardenUnit.SCALE / SunColossusUnit.SCALE;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(CathedralWardenUnit.SCALE, CathedralWardenUnit.SCALE, CathedralWardenUnit.SCALE);
    }
}
