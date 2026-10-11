package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Brassworks Sapper: the Halberdier's renderer (and attack animation) as a dwarf in a soot apron with a charge satchel (tools/gen_brass_skins.py). */
@OnlyIn(Dist.CLIENT)
public class SapperRenderer extends VindicatorUnitRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/sapper_unit.png");

    public SapperRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VindicatorUnit unit) {
        return TEXTURE;
    }
}
