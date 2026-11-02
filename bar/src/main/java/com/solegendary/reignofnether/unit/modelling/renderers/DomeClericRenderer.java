package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.DomeClericUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.DomeClericModel;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Dome Cleric (evoker_unit) on the human body - an older priest in an ivory chasuble with the gilt
 * orphrey over an under-robe in the owner's colour, a linen skullcap with a gilt cross, the book of the dome against
 * his chest (tools/gen_cleric.py -> tools/dsl_to_game.py). Until Oct 21 the villager body in an HD skin (PR #114; lovish
 * 21:20: every human unit moves onto the human body). Its 29.2 px stature at 1:1 is the Crossbow Levy's (the same
 * figure, tools/human_kit.py). The old renderer's held-item layer is gone: the book is part of the body.
 */
@OnlyIn(Dist.CLIENT)
public class DomeClericRenderer extends MobRenderer<EvokerUnit, DomeClericUnitModel> {
    private static final String NAME = "dome_cleric_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    static final float SCALE = 1.0f;

    public DomeClericRenderer(EntityRendererProvider.Context context) {
        super(context, new DomeClericUnitModel(context.bakeLayer(DomeClericModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(EvokerUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(EvokerUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(EvokerUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
