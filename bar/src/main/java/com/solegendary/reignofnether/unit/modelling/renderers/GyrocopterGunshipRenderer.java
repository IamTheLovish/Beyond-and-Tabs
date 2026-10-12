package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Gyrocopter Gunship: the parrot model at 1.6x (the Gyro Scout's at a gunship's size) as a soot-black
 * hull with brass rotor-wings, a gun-barrel beak and a furnace-orange exhaust (32x32, tools/gen_brass_skins.py), one
 * skin whatever the parrot's variant, and the Field Upgrade layer on top.
 */
@OnlyIn(Dist.CLIENT)
public class GyrocopterGunshipRenderer extends ParrotRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/gyrocopter_gunship_unit.png");
    static final float SCALE = 1.6f;

    public GyrocopterGunshipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= SCALE;
        this.addLayer(new BrassUpgradeLayer<>(this, 0.7f));
    }

    @Override
    public ResourceLocation getTextureLocation(Parrot entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Parrot entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
