package com.solegendary.reignofnether.unit.units.monsters;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZoglinRenderer;
import net.minecraft.world.entity.monster.Zoglin;

/**
 * Client only: the Plague Abomination is the zoglin model at {@link PlagueAbominationUnit#SCALE}x in the zoglin's own
 * skin - a PLACEHOLDER (Lane B draws the stitched body; slice T3b-10).
 */
public class PlagueAbominationRenderer extends ZoglinRenderer {

    public PlagueAbominationRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= PlagueAbominationUnit.SCALE;
    }

    @Override
    protected void scale(Zoglin entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(PlagueAbominationUnit.SCALE, PlagueAbominationUnit.SCALE, PlagueAbominationUnit.SCALE);
    }
}
