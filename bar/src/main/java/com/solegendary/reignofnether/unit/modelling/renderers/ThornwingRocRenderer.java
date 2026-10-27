package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderSpectre;
import com.github.alexthe666.alexsmobs.entity.EntitySpectre;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.verdant.ThornwingRocUnit;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Thornwing Roc: a PLACEHOLDER - the Thunder Dragon's spectre model (RenderSpectre, AM's animations) in a
 * bark-brown skin (the dragon's skin recoloured) with an amber eye glow, drawn at {@link ThornwingRocUnit#SCALE}x. Lane B
 * draws the real raptor with leaf feathers (slice T3b-10). AM's ghostly layers are dropped (they carry AM's textures).
 */
@OnlyIn(Dist.CLIENT)
public class ThornwingRocRenderer extends RenderSpectre {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/thornwing_roc_unit.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/thornwing_roc_glow.png");

    public ThornwingRocRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.clear();
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
        this.shadowRadius = 0.5f * ThornwingRocUnit.SCALE;
    }

    @Override
    protected void scale(EntitySpectre entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(ThornwingRocUnit.SCALE, ThornwingRocUnit.SCALE, ThornwingRocUnit.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(EntitySpectre entity) {
        return TEXTURE;
    }

    @Override
    public float getAlphaForRender(EntitySpectre entity, float partialTicks) {
        return 1f;   // bark and feather, not a ghost
    }
}
