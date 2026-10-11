package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.MirrorMageUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Mirror Mage: the illager body in a silver-grey robe with a bronze mirror on the chest and a red hood (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class MirrorMageRenderer extends AbstractVillagerUnitRenderer<MirrorMageUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/mirror_mage_unit.png");

    public MirrorMageRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
    }

    public ResourceLocation getTextureLocation(MirrorMageUnit unit) {
        return TEXTURE;
    }
}
