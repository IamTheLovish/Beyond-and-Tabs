package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Every faction's siege lobber (design/siege_t1.md): the illager body with the skin named after its entity type
 * ({@code textures/entities/<id>_unit.png}, placeholders from tools/gen_siege_lobber_skins.py until Itachi 1 draws them).
 */
@OnlyIn(Dist.CLIENT)
public class SiegeLobberRenderer extends AbstractVillagerUnitRenderer<SiegeLobberUnit> {
    private static final Map<EntityType<?>, ResourceLocation> SKINS = new HashMap<>();

    public SiegeLobberRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    public ResourceLocation getTextureLocation(SiegeLobberUnit unit) {
        return SKINS.computeIfAbsent(unit.getType(), t -> {
            ResourceLocation key = EntityType.getKey(t);
            return ResourceLocation.fromNamespaceAndPath(key.getNamespace(), "textures/entities/" + key.getPath() + ".png");
        });
    }
}
