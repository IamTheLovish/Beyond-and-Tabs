package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit;

import net.minecraft.client.model.HoglinModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.ZoglinRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Gravebound Ossuary Behemoth: the vanilla zoglin drawn at the unit's growing scale
 * ({@link OssuaryBehemothUnit#getScale}) with a heap of bones on its back that grows a piece per Bone Stack - bone
 * blocks along the spine, a skull on top once it has fed. The zoglin's back sits at model y = 0 over z -0.8..0.8 (its
 * head is at -z, the mane rises along the front half of the spine), so the heap sits on the rear half.
 */
@OnlyIn(Dist.CLIENT)
public class OssuaryBehemothRenderer extends ZoglinRenderer {

    public OssuaryBehemothRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= OssuaryBehemothUnit.BASE_SCALE;
        this.addLayer(new BoneHeapLayer(this, ctx.getBlockRenderDispatcher()));
    }

    @Override
    protected void scale(Zoglin entity, PoseStack poseStack, float partialTick) {
        float s = entity instanceof OssuaryBehemothUnit b ? b.getScale() : OssuaryBehemothUnit.BASE_SCALE;
        poseStack.scale(s, s, s);
    }

    static class BoneHeapLayer extends RenderLayer<Zoglin, HoglinModel<Zoglin>> {
        private static final BlockState BONE_Z = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        private static final BlockState BONE_Y = Blocks.BONE_BLOCK.defaultBlockState();
        private static final BlockState SKULL = Blocks.SKELETON_SKULL.defaultBlockState();
        private static final BlockState SOUL = Blocks.SOUL_SOIL.defaultBlockState();
        static final float BACK = 0.0f;

        private final BlockRenderDispatcher blocks;

        BoneHeapLayer(RenderLayerParent<Zoglin, HoglinModel<Zoglin>> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Zoglin entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible() || !(entity instanceof OssuaryBehemothUnit b))
                return;
            int stacks = b.getStacks();
            // the bed of grave-dirt and the spine, always there
            BlockPieces.piece(blocks, ps, buf, light, SOUL, 0f, BACK, 0.35f, 0.7f, 0.06f, 0.8f);
            BlockPieces.piece(blocks, ps, buf, light, BONE_Z, 0f, BACK - 0.06f, 0.35f, 0.16f, 0.12f, 0.75f);
            // ribs: one pair per stack, walking back from the shoulders
            for (int i = 0; i < stacks; i++) {
                float z = 0.05f + i * 0.11f;
                BlockPieces.piece(blocks, ps, buf, light, BONE_Y, 0.22f, BACK - 0.06f, z, 0.07f, 0.22f + 0.02f * i, 0.07f);
                BlockPieces.piece(blocks, ps, buf, light, BONE_Y, -0.22f, BACK - 0.06f, z, 0.07f, 0.22f + 0.02f * i, 0.07f);
            }
            if (stacks > 0)
                BlockPieces.piece(blocks, ps, buf, light, SKULL, 0f, BACK - 0.18f, 0.55f, 0.32f, 0.32f, 0.32f);
        }
    }
}
