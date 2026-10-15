package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.UpgradeVisuals;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.HalberdierUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Halberdier (vindicator_unit; the Dawn Paladin hero keeps the illager body): tall and narrow in white
 * harness, a sallet with a team-colour plume, a halberd ~1.6x his height - the tallest T1 shape - planted at rest
 * and lowered to the guard against a target. A body from the concept -> game pipeline (tools/gen_halberdier.py ->
 * tools/dsl_to_game.py). The Diamond Axes research shows its "tempered" parts: the blade gilded, the sun struck on it
 * (UpgradeVisuals SHAPE).
 */
@OnlyIn(Dist.CLIENT)
public class HalberdierRenderer extends MobRenderer<VindicatorUnit, HalberdierUnitModel> {
    private static final String NAME = "halberdier_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    /** 33 px to the top of the plume at 16 px a block: 0.95 keeps the helm at the illager's height. */
    static final float SCALE = 0.95f;

    public HalberdierRenderer(EntityRendererProvider.Context context) {
        super(context, new HalberdierUnitModel(context.bakeLayer(HalberdierModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(VindicatorUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(VindicatorUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(VindicatorUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        this.model.setUpgrade("tempered", UpgradeVisuals.has(entity.getUpgradeMask(), UpgradeVisuals.Kind.SHAPE));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
