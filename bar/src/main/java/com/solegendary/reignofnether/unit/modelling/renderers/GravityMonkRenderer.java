package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.GravityMonkUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Gravity Monk: the illager body in violet and gold with a shaved head and a gold circlet, empty-handed (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class GravityMonkRenderer extends AbstractVillagerUnitRenderer<GravityMonkUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/gravity_monk_unit.png");

    public GravityMonkRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
    }

    public ResourceLocation getTextureLocation(GravityMonkUnit unit) {
        return TEXTURE;
    }
}
