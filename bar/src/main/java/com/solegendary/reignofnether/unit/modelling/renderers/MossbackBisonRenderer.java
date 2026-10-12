package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderBison;
import com.github.alexthe666.alexsmobs.entity.EntityBison;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Mossback Bison: Alex's Mobs' bison drawn x{@link MossbackBisonUnit#SCALE} with the Hollow Grove
 * on its back - a moss bed on the rump where the riders sit, azalea bushes at its corners, a crown of flowering azalea
 * leaves over the hump and two birch rails (block pieces, {@link BlockPieces}). From AM's model: the hump top sits at
 * model y = -1.0 over z -1.44..0.19 (the head is at -z), the rump top at y = -0.63 over z 0.19..1.38, about 0.6 wide
 * each side.
 */
@OnlyIn(Dist.CLIENT)
public class MossbackBisonRenderer extends RenderBison {

    public MossbackBisonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= MossbackBisonUnit.SCALE;
        this.addLayer(new GroveLayer(this, ctx.getBlockRenderDispatcher()));
    }

    @Override
    protected void scale(EntityBison entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(MossbackBisonUnit.SCALE, MossbackBisonUnit.SCALE, MossbackBisonUnit.SCALE);
    }

    static class GroveLayer extends RenderLayer<EntityBison, AdvancedEntityModel<EntityBison>> {
        private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
        private static final BlockState BUSH = Blocks.AZALEA.defaultBlockState();
        private static final BlockState BLOOM = Blocks.FLOWERING_AZALEA.defaultBlockState();
        private static final BlockState LEAVES = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState();
        private static final BlockState RAIL = Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
        static final float HUMP = -1.0f, RUMP = -0.63f;

        private final BlockRenderDispatcher blocks;

        GroveLayer(RenderLayerParent<EntityBison, AdvancedEntityModel<EntityBison>> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, EntityBison entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            BlockPieces.piece(blocks, ps, buf, light, MOSS, 0f, RUMP, 0.78f, 1.1f, 0.08f, 1.15f);
            BlockPieces.piece(blocks, ps, buf, light, RAIL, 0.55f, RUMP - 0.08f, 0.78f, 0.1f, 0.1f, 1.1f);
            BlockPieces.piece(blocks, ps, buf, light, RAIL, -0.55f, RUMP - 0.08f, 0.78f, 0.1f, 0.1f, 1.1f);
            BlockPieces.piece(blocks, ps, buf, light, BUSH, 0.45f, RUMP - 0.08f, 1.25f, 0.28f, 0.28f, 0.28f);
            BlockPieces.piece(blocks, ps, buf, light, BLOOM, -0.45f, RUMP - 0.08f, 1.25f, 0.28f, 0.28f, 0.28f);
            BlockPieces.piece(blocks, ps, buf, light, LEAVES, 0f, HUMP, -0.55f, 0.9f, 0.3f, 0.95f);
            BlockPieces.piece(blocks, ps, buf, light, BLOOM, 0f, HUMP - 0.3f, -0.55f, 0.4f, 0.4f, 0.4f);
        }
    }
}
