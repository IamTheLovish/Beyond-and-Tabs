package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.SeerUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Seer: the illager body in a midnight-blue robe with a gold star chart and a lantern staff (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class SeerRenderer extends AbstractVillagerUnitRenderer<SeerUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/seer_unit.png");

    public SeerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
    }

    public ResourceLocation getTextureLocation(SeerUnit unit) {
        return TEXTURE;
    }
}
