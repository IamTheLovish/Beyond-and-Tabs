package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.MossbackBisonUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.MossbackBisonModel;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Mossback Bison: OUR body on the shared quadruped rig (MossbackBisonUnitModel; the entity stays
 * Alex's Mobs' bison underneath, only what is drawn changed), x{@link MossbackBisonUnit#SCALE}. The Hollow Grove on
 * its back is PART OF THE MODEL now (tools/gen_quadruped.py: the moss cushions, lobes, roots and glow berries on the
 * hump and hips bones, swaying with the coat) - the block-piece grove this renderer drew until round 14 (a moss bed,
 * birch rails, azalea bushes, a flowering-azalea crown, {@link BlockPieces}) is gone: in the first in-game footage
 * (eyes run 39595906716) it read as a full flowering-azalea block and a pale plank lying on the bison's back, over a
 * grove the model already carries. The riders' seats (MossbackBisonUnit.SEAT_HEIGHT, 2.1 blocks) sit in the modelled
 * rump moss (its top ~30 px x SCALE = 2.4 blocks).
 */
@OnlyIn(Dist.CLIENT)
public class MossbackBisonRenderer extends MobRenderer<MossbackBisonUnit, MossbackBisonUnitModel> {
    private static final String NAME = "mossback_bison_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);

    public MossbackBisonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MossbackBisonUnitModel(ctx.bakeLayer(MossbackBisonModel.LAYER_LOCATION)), 0.9f * MossbackBisonUnit.SCALE);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(MossbackBisonUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(MossbackBisonUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MossbackBisonUnit.SCALE, MossbackBisonUnit.SCALE, MossbackBisonUnit.SCALE);
    }

    @Override
    public void render(MossbackBisonUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
