package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.brass.MortarCrawlerUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;

/** The Brassworks Mortar Crawler: the illager body as a dwarf crew in a leather apron with a brass mortar pack, a bow for the bow-cooldown goal (tools/gen_brass_skins.py), the Field Upgrade layer on top. */
@OnlyIn(Dist.CLIENT)
public class MortarCrawlerRenderer extends AbstractVillagerUnitRenderer<MortarCrawlerUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/mortar_crawler_unit.png");

    public MortarCrawlerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new BrassUpgradeLayer<>(this, 1.0f));
    }

    public ResourceLocation getTextureLocation(MortarCrawlerUnit unit) {
        return TEXTURE;
    }
}
