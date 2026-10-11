package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Earthshaker: the iron golem model as a jade-veined stone golem with gold-capped shoulders (128x128,
 * the golem layout, tools/gen_storm_skins.py), a touch taller so it reads as the dynasty's wall from the RTS camera.
 * The vanilla crack overlay doubles as split stone.
 */
@OnlyIn(Dist.CLIENT)
public class EarthshakerRenderer extends IronGolemRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/earthshaker_unit.png");
    static final float SCALE = 1.1f;

    public EarthshakerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
