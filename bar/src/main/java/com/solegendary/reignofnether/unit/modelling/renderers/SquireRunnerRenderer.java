package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.unit.UpgradeVisuals;
import com.solegendary.reignofnether.unit.modelling.layers.CaptainBannerLayer;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.SquireRunnerUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.SquireRunnerModel;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Squire Runner (militia_unit - the trained raider; the temporary militia called to arms keep the
 * villager body and its professions): the smallest of the line, lean and leaning into the run, a team-colour brigandine
 * and a long team pennant on a pole on his back - a body from the concept -> game pipeline (tools/gen_squire.py ->
 * tools/dsl_to_game.py). Sword or bow in hand as he swaps; the Militia Bows research shows the slung bow and quiver
 * ("bows" parts, UpgradeVisuals SHAPE); an enchanted weapon shows its light. While he aims, the whole body turns a
 * quarter so his left side faces the target (SquireRunnerUnitModel.aimTurn).
 */
@OnlyIn(Dist.CLIENT)
public class SquireRunnerRenderer extends MobRenderer<MilitiaUnit, SquireRunnerUnitModel> {
    private static final String NAME = "squire_runner_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    /** ~28.5 px to the helm's knob at 16 px a block: 0.95 keeps him the smallest of the Sunforged line. */
    static final float SCALE = 0.95f;

    public SquireRunnerRenderer(EntityRendererProvider.Context context) {
        super(context, new SquireRunnerUnitModel(context.bakeLayer(SquireRunnerModel.LAYER_LOCATION)), 0.45f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
        // a captain (the Castle's Promote Illager) wears the ominous banner on the helm
        this.addLayer(new CaptainBannerLayer<>(this, context.getItemInHandRenderer(), model -> model.path("head"), 0.75f));
    }

    @Override
    public ResourceLocation getTextureLocation(MilitiaUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(MilitiaUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected void setupRotations(MilitiaUnit entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        // the side-on aim: after the model's flip, its left (+x) is -x; a -90 degree turn about Y brings it to where
        // the front (-z) was - toward the target
        float turn = this.model.aimTurn();
        if (turn > 0f)
            poseStack.mulPose(Axis.YP.rotationDegrees(-90f * turn));
    }

    @Override
    public void render(MilitiaUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.update(entity, partialTick);
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        this.model.setUpgrade("bows", entity.isUsingBow()
            || UpgradeVisuals.has(entity.getUpgradeMask(), UpgradeVisuals.Kind.SHAPE));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
