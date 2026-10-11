package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.StriderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Strider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Steam Runner: the strider model at 0.8x as a riveted brass boiler on soot-black stilt legs with a
 * furnace-orange firebox (64x128, tools/gen_brass_skins.py) - one skin whatever the strider's "cold" state - and the
 * Field Upgrade layer on top. The saddle layer never draws (nothing saddles it).
 */
@OnlyIn(Dist.CLIENT)
public class SteamRunnerRenderer extends StriderRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/steam_runner_unit.png");
    static final float SCALE = 0.8f;

    public SteamRunnerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= SCALE;
        this.addLayer(new BrassUpgradeLayer<>(this, 1.2f));
    }

    @Override
    public ResourceLocation getTextureLocation(Strider entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Strider entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
