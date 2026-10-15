package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.CaptainBannerLayer;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.layers.UpgradeGlowLayer;
import com.solegendary.reignofnether.unit.modelling.models.CrossbowLevyUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.CrossbowLevyModel;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Crossbow Levy (pillager_unit): a levy in a quilted gambeson under a wide-brimmed kettle hat, an
 * oversized crossbow across the body, a team-colour pavise with the sun on his back - a body from the concept ->
 * game pipeline (tools/gen_levy.py -> tools/dsl_to_game.py). The Multishot research makes the crossbow itself shine
 * (UpgradeGlowLayer on its "crossbow" part, the faction glow, pulsing) as the held item did before.
 */
@OnlyIn(Dist.CLIENT)
public class CrossbowLevyRenderer extends MobRenderer<PillagerUnit, CrossbowLevyUnitModel> {
    private static final String NAME = "crossbow_levy_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);

    public CrossbowLevyRenderer(EntityRendererProvider.Context context) {
        super(context, new CrossbowLevyUnitModel(context.bakeLayer(CrossbowLevyModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
        // a captain (the Castle's Promote Illager) wears the ominous banner on the helm
        this.addLayer(new CaptainBannerLayer<>(this, context.getItemInHandRenderer(), model -> model.path("head"), 0.85f));
        this.addLayer(UpgradeGlowLayer.partPath(this, model -> model.path("crossbow")));
    }

    @Override
    public ResourceLocation getTextureLocation(PillagerUnit entity) {
        return TEXTURE;
    }

    @Override
    public void render(PillagerUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
