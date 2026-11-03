package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.verdant.ThornweaverUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Thornweaver (essence caster): a placeholder look until lovish picks one - the Court's illager body
 * in the Elder Druid's robe (as the Bloom Priestess started from it). A non-fighter, so its arms stay folded.
 */
@OnlyIn(Dist.CLIENT)
public class ThornweaverRenderer extends AbstractVillagerUnitRenderer<ThornweaverUnit> {
    private static final ResourceLocation THORNWEAVER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/elder_druid_unit.png");

    public ThornweaverRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ThornweaverUnit unit) {
        return THORNWEAVER_UNIT;
    }
}
