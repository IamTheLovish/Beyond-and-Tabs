package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Gyro Scout: the parrot model at 1.4x (so it can be clicked from the RTS camera) as a clockwork rotor
 * drone - a brass body, copper wings and a glass eye (32x32, tools/gen_brass_skins.py), one skin whatever the parrot's
 * variant, and the Field Upgrade layer on top.
 */
@OnlyIn(Dist.CLIENT)
public class GyroScoutRenderer extends ParrotRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/gyro_scout_unit.png");
    static final float SCALE = 1.4f;

    public GyroScoutRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= SCALE;
        this.addLayer(new BrassUpgradeLayer<>(this, 0.6f));
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
