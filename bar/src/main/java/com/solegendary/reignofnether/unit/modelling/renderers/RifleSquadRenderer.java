package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.brass.RifleSquadUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;

/** The Brassworks Rifle Squad: the illager body as a bearded dwarf musketeer in a soot coat with brass buttons, a crossbow for the musket (tools/gen_brass_skins.py). */
@OnlyIn(Dist.CLIENT)
public class RifleSquadRenderer extends AbstractVillagerUnitRenderer<RifleSquadUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/rifle_squad_unit.png");

    public RifleSquadRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new BrassUpgradeLayer<>(this, 1.0f));   // people, never upgraded: the layer draws nothing (kept for the turret)
    }

    public ResourceLocation getTextureLocation(RifleSquadUnit unit) {
        return TEXTURE;
    }
}
