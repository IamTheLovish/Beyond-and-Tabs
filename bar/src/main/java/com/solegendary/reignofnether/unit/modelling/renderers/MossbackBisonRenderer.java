package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Mossback Bison: OUR body on the shared quadruped rig (MossbackBisonUnitModel; the entity stays
 * Alex's Mobs' bison underneath, only what is drawn changed), x{@link MossbackBisonUnit#SCALE}. The Hollow Grove on
 * its back is two things that read as one: the MODEL's grove (tools/gen_quadruped.py: moss cushions, lobes, roots and
 * glow berries on the hump and hips bones, swaying with the coat - what reads as a mossy back from the RTS camera)
 * and, since round 16, a small REAL-BLOCK grove seated in it ({@link GroveLayer}: a moss bed in the crest's saddle, two
 * short stripped-birch rails along its edges, one azalea bush back-right - lovish Oct 21: "yea they can be added
 * back"). Round 14 had taken the old block grove off because it was a full flowering-azalea block and a plank lying
 * ON the back; this one is scaled to the back (the bed 9 x 1.8 px, the rails 1.3 px thick, the bush 4.8 px), sunk
 * into the modelled moss so the lobes' crowns break its surface, and drawn in the hump bone's frame so it rides the
 * hump's own bob and the body's pitch and roll - nothing floats or slides. The riders' seats
 * (MossbackBisonUnit.SEAT_HEIGHT, 2.1 blocks) sit in the modelled rump moss (its top ~30 px x SCALE = 2.4 blocks).
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
     * The real-block grove, in the hump bone's frame (model units: 1 = 16 px, y down; the hump's pivot is at DSL
     * (0, 27.5, -8.5) px, its crest at 36.5 px). The pieces are the ones tools/../scratchpad bison_r16/grove_proxy.py
     * draws offline as cubes on the same bone (the previews never show a renderer layer), so seating was judged there:
     * the bed's bottom at 34.2-34.5 px lies 1-2 px INSIDE the crest tiles (the ridge is 34.6-36.5 px under it) and the
     * modelled lobes stand 0.8 px proud of its top; the rails lie along the bed's edges at 36 px; the bush's foot is
     * in the bed. Secondary motion: each piece rocks about its own base a beat behind the hump - the bed 0.4 rad, the
     * rails 0.7, the bush 0.9 behind the stride (1-3 ticks at a walk), 1.5-3 degrees, the bush the most; at rest they
     * stir on the breath with the moss ({@link Life#chain}). Only the entity's packed light, no outline buffer; the
     * tallest piece (the bush, to 39.4 px) stays under the modelled hump's own reach, so the culling box is unchanged.
     */
    static class GroveLayer extends RenderLayer<MossbackBisonUnit, MossbackBisonUnitModel> {
        private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
        private static final BlockState RAIL = Blocks.STRIPPED_BIRCH_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        private static final BlockState BUSH = Blocks.FLOWERING_AZALEA.defaultBlockState();
        /** x, y (bottom), z, sx, sy, sz, yaw degrees - from grove_proxy.py's print-out. */
        private static final float[][] PIECES = {
            {-0.0187f, -0.4375f, -0.1438f, 0.5625f, 0.1125f, 0.3500f, 5.0f},    // the moss bed, front half
            {0.0375f, -0.4188f, 0.0438f, 0.4750f, 0.1000f, 0.2625f, -7.0f},     // the moss bed, rear half
            {0.2437f, -0.5312f, -0.0813f, 0.0813f, 0.0813f, 0.4000f, 3.0f},     // the left rail
            {-0.2500f, -0.5375f, -0.1188f, 0.0813f, 0.0813f, 0.3625f, -2.0f},   // the right rail
            {0.0875f, -0.4813f, 0.1063f, 0.3000f, 0.2625f, 0.3000f, 0.0f},      // the azalea, back-right
        };
        private static final BlockState[] STATES = {MOSS, MOSS, RAIL, RAIL, BUSH};
        /** Each piece's lag behind the stride (radians) and its rock (degrees), fore-aft and side-to-side. */
        private static final float[] LAG = {0.4f, 0.45f, 0.7f, 0.75f, 0.9f}, PITCH = {1.5f, 1.5f, 2f, 2f, 3f}, ROLL = {1f, 1f, 1.5f, 1.5f, 2.5f};

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
            float amt = Math.min(1f, limbSwingAmount);
            float ph = MossbackBisonUnitModel.stridePhase(limbSwing);
            float idle = 1f - amt;
            int id = entity.getId();
            ps.pushPose();
            for (ModelPart p : model.path("hump"))
                p.translateAndRotate(ps);
            for (int i = 0; i < PIECES.length; i++) {
                float[] q = PIECES[i];
                // the rock: two beats a stride fore-aft (the bob), one beat side-to-side (the roll), each a beat late
                float pitch = PITCH[i] * (Mth.sin(ph * 2f - LAG[i]) * amt + 0.4f * Life.chain(ageInTicks, id, 90f, 1 + i, 9f) * idle);
                float roll = ROLL[i] * Mth.sin(ph - LAG[i]) * amt;
                ps.pushPose();
                ps.translate(q[0], q[1], q[2]);                 // the piece's base: it rocks about where it sits
                ps.mulPose(Axis.YP.rotationDegrees(q[6]));
                ps.mulPose(Axis.XP.rotationDegrees(pitch));
                ps.mulPose(Axis.ZP.rotationDegrees(roll));
                BlockPieces.piece(blocks, ps, buf, light, STATES[i], 0f, 0f, 0f, q[3], q[4], q[5]);
                ps.popPose();
            }
            ps.popPose();
        }
    }
}
