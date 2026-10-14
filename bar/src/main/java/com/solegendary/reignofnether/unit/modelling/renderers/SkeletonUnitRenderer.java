package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Gravebound Grave Archer: cracked bone, a black hooded cowl, soul-blue sockets, a team rag sash across the ribs.
 * Drawn by tools/gen_hd_skins.py (original art; the unit wore the vanilla texture before), with an owner-coloured
 * team region (TeamColourLayer, skeleton_unit_team.png). Model, layers and animations stay vanilla's.
 */
@OnlyIn(Dist.CLIENT)
public class SkeletonUnitRenderer extends SkeletonRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/skeleton_unit.png");

    public SkeletonUnitRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.addLayer(new TeamColourLayer<>(this));
        // researched looks (unit/UpgradeVisuals): trim on the torso, a weapon glow after vanilla's hand layer
        this.addLayer(com.solegendary.reignofnether.unit.modelling.layers.UpgradeTrimLayer.body(this));
        this.addLayer(com.solegendary.reignofnether.unit.modelling.layers.UpgradeGlowLayer.heldItem(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractSkeleton entity) {
        return TEXTURE;
    }
}
