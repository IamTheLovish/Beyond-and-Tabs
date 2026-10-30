package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.DawnPaladinUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.DawnPaladinModel;
import com.solegendary.reignofnether.unit.units.heroes.DawnPaladinUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Dawn Paladin (dawn_paladin_unit, the Sunforged hero) on the human body - a bascinet over a mail aventail, the
 * surcoat in the owner's colour with the great gilt sun, plate on the arms and legs, an arming sword and a heater
 * shield (tools/gen_dawn_paladin.py -> tools/dsl_to_game.py). Until Oct 21 the villager body in a hero skin
 * (HeroRenderers.DawnPaladin; lovish 21:20: every human unit moves onto the human body). A tenth bigger than the line
 * (HeroRenderers.HERO_SCALE), as every hero is.
 */
@OnlyIn(Dist.CLIENT)
public class DawnPaladinRenderer extends MobRenderer<DawnPaladinUnit, DawnPaladinUnitModel> {
    private static final String NAME = "dawn_paladin_body_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    static final float SCALE = HeroRenderers.HERO_SCALE;

    public DawnPaladinRenderer(EntityRendererProvider.Context context) {
        super(context, new DawnPaladinUnitModel(context.bakeLayer(DawnPaladinModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(DawnPaladinUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(DawnPaladinUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(DawnPaladinUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
