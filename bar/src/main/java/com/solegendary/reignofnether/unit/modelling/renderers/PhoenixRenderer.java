package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderSunbird;
import com.github.alexthe666.alexsmobs.entity.EntitySunbird;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.storm.PhoenixUnit;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Phoenix: Alex's Mobs' sunbird model and animations (RenderSunbird) in our own red-and-gold fire-bird
 * skin (tools/gen_storm_skins.py paints it on the sunbird's UV outline), drawn at {@link #SCALE} of the wild bird (a
 * 2.75-block sunbird is a roc at RTS scale). AM's scorch layer - which carries AM's glow texture - is dropped; an
 * ember-glow layer of ours (the wing edges and crest, always lit) takes its place. An ember is drawn dark: the glow
 * layer is skipped and the body dimmed through the renderer's light.
 */
@OnlyIn(Dist.CLIENT)
public class PhoenixRenderer extends RenderSunbird {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/phoenix_unit.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/phoenix_glow.png");
    public static final float SCALE = 0.55f;

    public PhoenixRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.clear();   // AM's scorch layer draws AM's sunbird glow
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }

            @Override
            public void render(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight,
                               EntitySunbird entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                if (entity instanceof PhoenixUnit p && p.isEmber())
                    return;   // an ember's fire is out
                super.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
        this.shadowRadius = 0.5f * SCALE;
    }

    @Override
    protected void scale(EntitySunbird entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected int getBlockLightLevel(EntitySunbird entity, net.minecraft.core.BlockPos pos) {
        // AM lights the sunbird at full bright; an ember smoulders, a flying Phoenix burns
        return entity instanceof PhoenixUnit p && p.isEmber() ? 4 : 15;
    }

    @Override
    public ResourceLocation getTextureLocation(EntitySunbird entity) {
        return TEXTURE;
    }
}
