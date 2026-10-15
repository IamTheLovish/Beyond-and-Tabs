package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.FieldMedicUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.FieldMedicModel;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Field Medic (witch_unit): a robed healer under a hood with a staff half again her height, crowned
 * with a floating sun disc - the second body through the concept -> game pipeline (tools/gen_medic.py ->
 * tools/dsl_to_game.py; claude/paladin-radiant.md has the method). Team colour on the mantle, scapular and the
 * staff's ribbons; the disc, the eyes in the hood and the vials on her belt drawn again full-bright. At 1:1 she
 * stands ~1.9 blocks to the top of the hood, the witch's own height; the staff's disc ~3 blocks up, the bright dot
 * that marks the healer from the RTS camera.
 */
@OnlyIn(Dist.CLIENT)
public class FieldMedicRenderer extends MobRenderer<WitchUnit, FieldMedicUnitModel> {
    private static final String NAME = "field_medic_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);

    public FieldMedicRenderer(EntityRendererProvider.Context context) {
        super(context, new FieldMedicUnitModel(context.bakeLayer(FieldMedicModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(WitchUnit entity) {
        return TEXTURE;
    }

    @Override
    public void render(WitchUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
