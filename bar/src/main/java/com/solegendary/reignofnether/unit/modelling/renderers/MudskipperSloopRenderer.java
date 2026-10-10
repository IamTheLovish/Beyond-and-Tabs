package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderMudskipper;
import com.github.alexthe666.alexsmobs.entity.EntityMudskipper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.tide.MudskipperSloopUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tidewrought Mudskipper Sloop: Alex's Mobs' own mudskipper model and texture (with its open-mouth bite frame),
 * drawn {@link MudskipperSloopUnit#SCALE}x to match the unit's hitbox.
 */
@OnlyIn(Dist.CLIENT)
public class MudskipperSloopRenderer extends RenderMudskipper {

    public MudskipperSloopRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F * MudskipperSloopUnit.SCALE;
    }

    @Override
    protected void scale(EntityMudskipper entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(MudskipperSloopUnit.SCALE, MudskipperSloopUnit.SCALE, MudskipperSloopUnit.SCALE);
    }
}
