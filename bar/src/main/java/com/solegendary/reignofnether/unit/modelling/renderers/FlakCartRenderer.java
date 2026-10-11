package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.brass.FlakCartUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Brassworks Flak Cart: the illager body as a dwarf gunner with a brass flak pack and goggles (tools/gen_brass_skins.py), the Field Upgrade layer on top. */
@OnlyIn(Dist.CLIENT)
public class FlakCartRenderer extends AbstractVillagerUnitRenderer<FlakCartUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/flak_cart_unit.png");

    public FlakCartRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new BrassUpgradeLayer<>(this, 1.0f));
    }

    public ResourceLocation getTextureLocation(FlakCartUnit unit) {
        return TEXTURE;
    }
}
