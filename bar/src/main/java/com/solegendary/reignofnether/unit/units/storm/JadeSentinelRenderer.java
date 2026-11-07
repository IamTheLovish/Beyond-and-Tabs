package com.solegendary.reignofnether.unit.units.storm;

import com.mojang.blaze3d.vertex.PoseStack;

import com.solegendary.reignofnether.unit.modelling.renderers.EarthshakerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Client only: the Jade Sentinel is the Earthshaker's jade golem (its own skin) at {@link JadeSentinelUnit#SCALE}x - a
 * PLACEHOLDER (Lane B draws the guardian-lion; slice T3b-10).
 */
public class JadeSentinelRenderer extends EarthshakerRenderer {

    public JadeSentinelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= JadeSentinelUnit.SCALE;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(JadeSentinelUnit.SCALE, JadeSentinelUnit.SCALE, JadeSentinelUnit.SCALE);
    }
}
