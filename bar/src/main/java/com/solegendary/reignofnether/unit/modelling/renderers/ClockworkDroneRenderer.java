package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Iron Leviathan Engine's clockwork drones: the parrot model at {@link #SCALE}x - smaller than the Gyro Scout it
 * borrows its brass-and-copper rotor skin from (tools/gen_brass_skins.py), so a swarm of them reads as the engine's
 * escorts rather than scouts. No upgrade layer: drones are never field-upgraded.
 */
@OnlyIn(Dist.CLIENT)
public class ClockworkDroneRenderer extends ParrotRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/gyro_scout_unit.png");
    static final float SCALE = 0.9f;

    public ClockworkDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= SCALE;
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
