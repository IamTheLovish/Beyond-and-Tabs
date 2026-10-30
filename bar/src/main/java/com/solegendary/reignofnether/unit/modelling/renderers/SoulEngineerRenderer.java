package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.brass.SoulEngineerUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Soul Engineer (essence caster): a placeholder look until lovish picks one - the Tinker's illager body
 * in the Engineer-Captain's brass-trimmed coat. A non-fighter, so its arms stay folded and it carries no tool.
 */
@OnlyIn(Dist.CLIENT)
public class SoulEngineerRenderer extends AbstractVillagerUnitRenderer<SoulEngineerUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/engineer_captain_unit.png");

    public SoulEngineerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulEngineerUnit unit) {
        return TEXTURE;
    }
}
