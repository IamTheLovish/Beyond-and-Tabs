package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Jade Guard: the Halberdier's renderer (and attack animation) in red-lacquered lamellar under a jade-crested helmet (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class JadeGuardRenderer extends VindicatorUnitRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/jade_guard_unit.png");

    public JadeGuardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VindicatorUnit unit) {
        return TEXTURE;
    }
}
