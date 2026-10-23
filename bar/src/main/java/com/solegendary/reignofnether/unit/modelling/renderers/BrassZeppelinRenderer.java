package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderSpectre;
import com.github.alexthe666.alexsmobs.entity.EntitySpectre;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.brass.BrassZeppelinUnit;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Brass Zeppelin: a PLACEHOLDER - the Thunder Dragon's spectre model (RenderSpectre, AM's animations) in a
 * brass skin (the dragon's skin recoloured) with a furnace-orange glow, stretched long and fat into an airship (SCALE_X/Y/Z).
 * Lane B draws the real airship with its gas bag and gondola (slice T3b-10). AM's ghostly layers are dropped (they carry AM's textures).
 */
@OnlyIn(Dist.CLIENT)
public class BrassZeppelinRenderer extends RenderSpectre {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/brass_zeppelin_unit.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/brass_zeppelin_glow.png");

    public BrassZeppelinRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.clear();
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
        this.shadowRadius = 0.5f * BrassZeppelinUnit.SCALE_X;
    }

    @Override
    protected void scale(EntitySpectre entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(BrassZeppelinUnit.SCALE_X, BrassZeppelinUnit.SCALE_Y, BrassZeppelinUnit.SCALE_Z);
    }

    @Override
    public ResourceLocation getTextureLocation(EntitySpectre entity) {
        return TEXTURE;
    }

    @Override
    public float getAlphaForRender(EntitySpectre entity, float partialTicks) {
        return 1f;   // brass and canvas, not a ghost
    }
}
