package com.solegendary.reignofnether.unit.modelling.renderers;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Horde's combat engineer, the Wrecker (design/combat_engineers.md): the Grunt's piglin renderer (armour layer, the
 * owner-coloured TeamColourLayer) with its own HD skin - a riveted rust apron, a scrap pack and a team sash
 * (tools/gen_hd_skins.py). A subclass rather than a row in PiglinUnitRenderer's texture map, so the shared renderer
 * stays untouched.
 */
@OnlyIn(Dist.CLIENT)
public class WreckerRenderer extends PiglinUnitRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/wrecker_unit.png");

    public WreckerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Mob entity) {
        return TEXTURE;
    }
}
