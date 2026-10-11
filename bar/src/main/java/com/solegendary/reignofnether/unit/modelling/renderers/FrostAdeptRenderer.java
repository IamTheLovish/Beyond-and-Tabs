package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.FrostAdeptUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Frost Adept: the illager body in ice-blue and red lacquer with a silver diadem, a prismarine shard for its staff tip (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class FrostAdeptRenderer extends AbstractVillagerUnitRenderer<FrostAdeptUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/frost_adept_unit.png");

    public FrostAdeptRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    public ResourceLocation getTextureLocation(FrostAdeptUnit unit) {
        return TEXTURE;
    }
}
