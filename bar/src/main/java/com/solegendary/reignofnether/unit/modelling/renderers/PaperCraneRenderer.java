package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderShoebill;
import com.github.alexthe666.alexsmobs.entity.EntityShoebill;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Paper Crane: Alex's Mobs' shoebill model and renderer (its animations, its scale) in our own
 * paper-white skin with red seal marks (tools/gen_storm_skins.py paints it on the shoebill's UV layout).
 */
@OnlyIn(Dist.CLIENT)
public class PaperCraneRenderer extends RenderShoebill {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/paper_crane_unit.png");

    public PaperCraneRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityShoebill entity) {
        return TEXTURE;
    }
}
