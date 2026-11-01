package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.gen.DomeClericModel;
import com.solegendary.reignofnether.unit.units.villagers.AngelCallerUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Angel Caller (angel_caller_unit), a placeholder look until its own art: the Dome Cleric's generated
 * human body and skin (tools/gen_cleric.py), with the owner's colour and the body's glow, in its rest pose (the
 * cleric's casting animation reads the Evoker's spell state, which the caller does not have).
 */
@OnlyIn(Dist.CLIENT)
public class AngelCallerRenderer extends MobRenderer<AngelCallerUnit, DomeClericModel<AngelCallerUnit>> {
    private static final String NAME = "dome_cleric_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);

    public AngelCallerRenderer(EntityRendererProvider.Context context) {
        super(context, new DomeClericModel<>(context.bakeLayer(DomeClericModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(AngelCallerUnit entity) {
        return TEXTURE;
    }

    @Override
    public void render(AngelCallerUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
