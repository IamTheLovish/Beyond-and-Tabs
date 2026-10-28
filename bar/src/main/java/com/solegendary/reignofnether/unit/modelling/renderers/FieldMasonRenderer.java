package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.FieldMasonUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.FieldMasonModel;
import com.solegendary.reignofnether.unit.units.engineers.FieldMasonUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Field Mason (field_mason_unit, a combat engineer) on the human body - a team gambeson under a riveted
 * steel breastplate, a keeled steel cap, a leather tool apron, the sun-painted pavise on his back and a mason's war
 * hammer in his fist (tools/gen_mason.py -> tools/dsl_to_game.py). Until Oct 21 the villager body in an HD skin through
 * CombatEngineerRenderer (PR #114; lovish 21:20: every human unit moves onto the human body); the other five combat
 * engineers keep that renderer until their own bodies are made.
 */
@OnlyIn(Dist.CLIENT)
public class FieldMasonRenderer extends MobRenderer<FieldMasonUnit, FieldMasonUnitModel> {
    private static final String NAME = "field_mason_body_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    static final float SCALE = 1.0f;

    public FieldMasonRenderer(EntityRendererProvider.Context context) {
        super(context, new FieldMasonUnitModel(context.bakeLayer(FieldMasonModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(FieldMasonUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(FieldMasonUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(FieldMasonUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
