package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.layers.UpgradeGlowLayer;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// based on PillagerRenderer
@OnlyIn(Dist.CLIENT)
public class PillagerUnitRenderer extends AbstractVillagerUnitRenderer<PillagerUnit> {
    private static final ResourceLocation PILLAGER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/pillager_unit.png");

    public PillagerUnitRenderer(EntityRendererProvider.Context p_174354_) {
        super(p_174354_, new VillagerUnitModel<>(p_174354_.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, p_174354_.getItemInHandRenderer()));
        // Multishot Crossbows researched: the crossbow glows Sunforged gold (after the hand layer, so it lands on it)
        this.addLayer(UpgradeGlowLayer.heldItem(this));
    }

    public ResourceLocation getTextureLocation(PillagerUnit pEntity) {
        return PILLAGER_UNIT;
    }
}