package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Rocket Battery: the illager body in a red-lacquer crew coat with a gold powder belt and a rocket rack on its back (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class RocketBatteryRenderer extends AbstractVillagerUnitRenderer<RocketBatteryUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/rocket_battery_unit.png");

    public RocketBatteryRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    public ResourceLocation getTextureLocation(RocketBatteryUnit unit) {
        return TEXTURE;
    }
}
