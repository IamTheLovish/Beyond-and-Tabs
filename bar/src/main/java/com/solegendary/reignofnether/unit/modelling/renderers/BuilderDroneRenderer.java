package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.BuilderDroneUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.BuilderDroneModel;
import com.solegendary.reignofnether.unit.units.tide.ShipwrightUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Light Grid's Builder Drone (shipwright_unit, converted in place: design/ai_faction.md, PR 379): a glossy black
 * kite-shaped hover hull outlined by one light line in its owner's colour (GeneratedBodies.teamGlow - from the RTS
 * camera that outline is the unit), the owner's chevron on the deck, a white visor slit and welding tips
 * (GeneratedBodies.glow). It hovers 0.6 block up, so the shadow under it reads "hover" at every zoom. The Admiral
 * (the first Shipwright, the commander) draws as a drone too until the Prime Program body (slice 1b's commander).
 */
@OnlyIn(Dist.CLIENT)
public class BuilderDroneRenderer extends MobRenderer<ShipwrightUnit, BuilderDroneUnitModel> {
    private static final String NAME = "builder_drone_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);

    public BuilderDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new BuilderDroneUnitModel(context.bakeLayer(BuilderDroneModel.LAYER_LOCATION)), 0.45f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
        this.addLayer(GeneratedBodies.teamGlow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(ShipwrightUnit entity) {
        return TEXTURE;
    }

    @Override
    public void render(ShipwrightUnit entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
