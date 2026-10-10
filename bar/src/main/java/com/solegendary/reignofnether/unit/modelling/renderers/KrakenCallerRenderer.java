package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.tide.KrakenCallerUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Tidewrought Kraken Caller: the illager body in deep-sea robes with a tentacle mantle and a pearl-eyed hood (tools/gen_tide_skins.py). */
@OnlyIn(Dist.CLIENT)
public class KrakenCallerRenderer extends AbstractVillagerUnitRenderer<KrakenCallerUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/kraken_caller_unit.png");

    public KrakenCallerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    public ResourceLocation getTextureLocation(KrakenCallerUnit unit) {
        return TEXTURE;
    }
}
