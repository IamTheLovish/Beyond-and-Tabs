package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.MossbackBisonUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.MossbackBisonModel;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Mossback Bison: OUR body on the shared quadruped rig (MossbackBisonUnitModel; the entity stays
 * Alex's Mobs' bison underneath, only what is drawn changed), x{@link MossbackBisonUnit#SCALE}, with the Hollow Grove
 * on its back - a moss bed on the rump where the riders sit, azalea bushes at its corners, two birch rails, and a
 * crown of flowering azalea over the hump (block pieces, {@link BlockPieces}). The grove rides the rig: the bed and
 * rails follow the hips bone, the canopy the hump bone, so they dip and roll with the body instead of floating.
 */
@OnlyIn(Dist.CLIENT)
public class MossbackBisonRenderer extends MobRenderer<MossbackBisonUnit, MossbackBisonUnitModel> {
    private static final String NAME = "mossback_bison_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);

    public MossbackBisonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MossbackBisonUnitModel(ctx.bakeLayer(MossbackBisonModel.LAYER_LOCATION)), 0.9f * MossbackBisonUnit.SCALE);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
        this.addLayer(new GroveLayer(this, ctx.getBlockRenderDispatcher()));
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

    /**
     * The grove, in the bones' own frames (model units: 1 = 16 px, y down). The hips bone's pivot is at DSL
     * (0, 24, 4) px, the rump's mossy top at 32.2 px: 0.51 above it; the hump bone's pivot at (0, 31.5, -7), the hump
     * top at 40 px: 0.53 above it. The seats (MossbackBisonUnit.SEAT_XZ, 2.1 blocks up) sit on the moss bed.
     */
    static class GroveLayer extends RenderLayer<MossbackBisonUnit, MossbackBisonUnitModel> {
        private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
        private static final BlockState BUSH = Blocks.AZALEA.defaultBlockState();
        private static final BlockState BLOOM = Blocks.FLOWERING_AZALEA.defaultBlockState();
        private static final BlockState LEAVES = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState();
        private static final BlockState RAIL = Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
        static final float RUMP = -0.51f, HUMP = -0.53f;

        private final BlockRenderDispatcher blocks;

        GroveLayer(RenderLayerParent<MossbackBisonUnit, MossbackBisonUnitModel> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, MossbackBisonUnit entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            MossbackBisonUnitModel model = this.getParentModel();
            ps.pushPose();
            for (ModelPart p : model.path("hips"))
                p.translateAndRotate(ps);
            BlockPieces.piece(blocks, ps, buf, light, MOSS, 0f, RUMP, 0.38f, 1.0f, 0.08f, 0.9f);
            BlockPieces.piece(blocks, ps, buf, light, RAIL, 0.5f, RUMP - 0.08f, 0.38f, 0.1f, 0.1f, 0.9f);
            BlockPieces.piece(blocks, ps, buf, light, RAIL, -0.5f, RUMP - 0.08f, 0.38f, 0.1f, 0.1f, 0.9f);
            BlockPieces.piece(blocks, ps, buf, light, BUSH, 0.4f, RUMP - 0.08f, 0.74f, 0.28f, 0.28f, 0.28f);
            BlockPieces.piece(blocks, ps, buf, light, BLOOM, -0.4f, RUMP - 0.08f, 0.74f, 0.28f, 0.28f, 0.28f);
            ps.popPose();
            ps.pushPose();
            for (ModelPart p : model.path("hump"))
                p.translateAndRotate(ps);
            BlockPieces.piece(blocks, ps, buf, light, LEAVES, 0f, HUMP, 0f, 0.85f, 0.28f, 0.7f);
            BlockPieces.piece(blocks, ps, buf, light, BLOOM, 0f, HUMP - 0.28f, 0f, 0.38f, 0.38f, 0.38f);
            ps.popPose();
        }
    }
}
