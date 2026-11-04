package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.RoyalArchitectUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.RoyalArchitectModel;
import com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Royal Architect (royal_architect_unit, the T2 constructor) on the human body - an oat-linen coat, a
 * short shoulder cape and a soft cap in the owner's colour, a satchel of plans, a rolled plan in hand and a mallet that
 * comes from his belt to his hand when he builds (tools/gen_architect.py -> tools/dsl_to_game.py). Until Oct 21 the
 * villager body in a skin (lovish 21:20: every human unit moves onto the human body). The back banner and the held
 * tool layers went with the villager model: the cape and cap carry the owner's colour on him, the mallet is his own.
 */
@OnlyIn(Dist.CLIENT)
public class RoyalArchitectRenderer extends MobRenderer<RoyalArchitectUnit, RoyalArchitectUnitModel> {
    private static final String NAME = "royal_architect_body_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    static final float SCALE = 1.0f;

    public RoyalArchitectRenderer(EntityRendererProvider.Context context) {
        super(context, new RoyalArchitectUnitModel(context.bakeLayer(RoyalArchitectModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(RoyalArchitectUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(RoyalArchitectUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(RoyalArchitectUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
