package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.SkyCorsairModel;
import com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tidewrought Sky Corsair: its own airship model ({@link SkyCorsairModel} - a canvas balloon carrying a timber
 * hull on ropes) in the skin tools/gen_tide_skins.py paints for it, drawn at model scale: the model is built two
 * blocks tall and wide, the size of the unit's hitbox, so no renderer scale is needed (the interim ghast body was a
 * white-and-teal cube from the RTS camera).
 */
@OnlyIn(Dist.CLIENT)
public class SkyCorsairRenderer extends MobRenderer<SkyCorsairUnit, SkyCorsairModel> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/sky_corsair_unit.png");

    public SkyCorsairRenderer(EntityRendererProvider.Context context) {
        super(context, new SkyCorsairModel(context.bakeLayer(SkyCorsairModel.LAYER_LOCATION)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(SkyCorsairUnit unit) {
        return TEXTURE;
    }
}
