package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderSpectre;
import com.github.alexthe666.alexsmobs.entity.EntitySpectre;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Thunder Dragon: Alex's Mobs' spectre model and animations (RenderSpectre) in our own opaque
 * jade-and-gold sky-serpent skin (tools/gen_storm_skins.py paints it on the spectre's UV outline), drawn a little larger
 * than a wild spectre. AM's ghostly layers - the translucent membrane and its eyes - are dropped (they would draw AM's
 * own spectre texture over ours); a gold eye-glow layer of ours takes their place.
 */
@OnlyIn(Dist.CLIENT)
public class ThunderDragonRenderer extends RenderSpectre {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/thunder_dragon_unit.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/thunder_dragon_glow.png");
    static final float SCALE = 1.6f;

    public ThunderDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.clear();   // AM's membrane and eyes layers carry AM's textures
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
        this.shadowRadius = 0.5f * SCALE;
    }

    @Override
    protected void scale(EntitySpectre entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(EntitySpectre entity) {
        return TEXTURE;
    }

    @Override
    public float getAlphaForRender(EntitySpectre entity, float partialTicks) {
        return 1f;   // a dragon of flesh and scale, not a ghost
    }
}
