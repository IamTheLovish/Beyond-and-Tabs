package com.solegendary.reignofnether.unit.modelling.renderers;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Witch;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Ink Scribe: vanilla's witch body (and its held brush) in the dynasty's own skin - a paper-white robe
 * with ink-black trim and red seal marks, the pointed hat recut as a scholar's black cap with a gold bead (64x128, the
 * witch layout; drawn by tools/gen_storm_skins.py). The Moonwell Bearer's renderer, re-skinned.
 */
@OnlyIn(Dist.CLIENT)
public class InkScribeRenderer extends WitchRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/ink_scribe_unit.png");

    public InkScribeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Witch entity) {
        return TEXTURE;
    }
}
