package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.SparkSageUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Spark Sage (essence caster): a placeholder look until lovish picks one - the illager body in the Wind
 * Monk's robe. A non-fighter, so its arms stay folded.
 */
@OnlyIn(Dist.CLIENT)
public class SparkSageRenderer extends AbstractVillagerUnitRenderer<SparkSageUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/wind_monk_unit.png");

    public SparkSageRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
    }

    public ResourceLocation getTextureLocation(SparkSageUnit unit) {
        return TEXTURE;
    }
}
