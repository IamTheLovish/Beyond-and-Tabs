package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.TitheBinderUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.TitheBinderModel;
import com.solegendary.reignofnether.unit.units.monsters.TitheBinderUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Gravebound Tithe Binder (tithe_binder_unit): the generated body (TitheBinderModel; tools/gen_tithe_binder.py ->
 * tools/dsl_to_game.py) at its rest pose, team colour on the hood and mantle, the lantern core and the weapons' edges
 * drawn again full-bright. The docked weapons follow the synced count (TitheBinderUnitModel.showWeapons). The Tithe of
 * Iron upgrade's cubes stay hidden (no research yet). The animation rig comes later (TitheBinderUnitModel's TODO).
 */
@OnlyIn(Dist.CLIENT)
public class TitheBinderRenderer extends MobRenderer<TitheBinderUnit, TitheBinderUnitModel> {
    private static final String NAME = "tithe_binder_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);

    public TitheBinderRenderer(EntityRendererProvider.Context context) {
        super(context, new TitheBinderUnitModel(context.bakeLayer(TitheBinderModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(TitheBinderUnit entity) {
        return TEXTURE;
    }

    @Override
    public void render(TitheBinderUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        this.model.setUpgrade("tithe_of_iron", false);
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
