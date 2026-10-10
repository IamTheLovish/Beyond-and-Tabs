package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Tidewrought Flak Swivel: PillagerUnitRenderer's illager body as a gun crew in teal, brass goggles and a cartridge belt (tools/gen_tide_skins.py). */
@OnlyIn(Dist.CLIENT)
public class FlakSwivelRenderer extends AbstractVillagerUnitRenderer<FlakSwivelUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/flak_swivel_unit.png");

    public FlakSwivelRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    public ResourceLocation getTextureLocation(FlakSwivelUnit unit) {
        return TEXTURE;
    }
}
