package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Steam Juggernaut: the ravager model at 0.8x as a boiler-plated war engine - riveted brass plates, a
 * soot face-plate with a furnace grille for a mouth, iron horns, a boiler drum on the back (128x128, the ravager layout,
 * tools/gen_brass_skins.py) - and the Field Upgrade layer on top.
 */
@OnlyIn(Dist.CLIENT)
public class SteamJuggernautRenderer extends RavagerRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/steam_juggernaut_unit.png");
    static final float SCALE = 0.8f;

    public SteamJuggernautRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= SCALE;
        this.addLayer(new BrassUpgradeLayer<>(this, 1.6f));
    }

    @Override
    public ResourceLocation getTextureLocation(Ravager entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Ravager entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
