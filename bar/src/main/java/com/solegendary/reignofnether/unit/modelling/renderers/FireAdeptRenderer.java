package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Fire Adept: the illager body in flame-orange and red lacquer with a gold diadem, a blaze rod for its fan (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class FireAdeptRenderer extends AbstractVillagerUnitRenderer<FireAdeptUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/fire_adept_unit.png");

    public FireAdeptRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    public ResourceLocation getTextureLocation(FireAdeptUnit unit) {
        return TEXTURE;
    }
}
