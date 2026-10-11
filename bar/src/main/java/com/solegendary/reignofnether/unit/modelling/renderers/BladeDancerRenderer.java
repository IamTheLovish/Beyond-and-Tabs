package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Blade Dancer: the Halberdier's renderer (and attack animation) in red silk with a gold sash and a topknot (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class BladeDancerRenderer extends VindicatorUnitRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/blade_dancer_unit.png");

    public BladeDancerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VindicatorUnit unit) {
        return TEXTURE;
    }
}
