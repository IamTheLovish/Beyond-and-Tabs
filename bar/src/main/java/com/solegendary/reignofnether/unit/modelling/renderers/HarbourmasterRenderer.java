package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.tide.ShipwrightUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Tidewrought Harbourmaster: the Shipwright's body in a long brass-buttoned harbour coat and a peaked cap (tools/gen_tide_skins.py). */
@OnlyIn(Dist.CLIENT)
public class HarbourmasterRenderer extends ShipwrightRenderer {
    private static final ResourceLocation HARBOURMASTER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/harbourmaster_unit.png");

    public HarbourmasterRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ShipwrightUnit unit) {
        return HARBOURMASTER_UNIT;
    }
}
