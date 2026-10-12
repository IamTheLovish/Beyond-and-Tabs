package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Gravebound Bone Legionary: dried flesh under a bone-plate cuirass and greaves, a black-iron helm with a team crest.
 * Drawn by tools/gen_hd_skins.py (original art; the unit wore the vanilla texture before), with an owner-coloured
 * team region (TeamColourLayer, husk_unit_team.png). Model, layers and animations stay vanilla's.
 */
@OnlyIn(Dist.CLIENT)
public class HuskUnitRenderer extends HuskRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/husk_unit.png");

    public HuskUnitRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.addLayer(new TeamColourLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return TEXTURE;
    }
}
