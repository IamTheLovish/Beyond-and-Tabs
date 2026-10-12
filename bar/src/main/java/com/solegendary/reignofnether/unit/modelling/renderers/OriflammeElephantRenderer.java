package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.model.ModelElephant;
import com.github.alexthe666.alexsmobs.client.render.RenderElephant;
import com.github.alexthe666.alexsmobs.entity.EntityElephant;
import com.mojang.blaze3d.vertex.PoseStack;

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
 * The Sunforged Oriflamme Elephant: Alex's Mobs' elephant with AM's own tusks, gold caparison and side chests (set on
 * the unit), plus the Oriflamme on its back - a white-and-gold howdah, a birch banner pole with a long gold-and-white
 * pennant and a lantern at its foot (block pieces, {@link BlockPieces}). From AM's model: the back top sits at model
 * y = -2.0 over z -1.44..1.5 (the head is at -z), the body about 0.9 wide each side.
 */
@OnlyIn(Dist.CLIENT)
public class OriflammeElephantRenderer extends RenderElephant {

    public OriflammeElephantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new OriflammeLayer(this, ctx.getBlockRenderDispatcher()));
    }

    static class OriflammeLayer extends RenderLayer<EntityElephant, ModelElephant> {
        private static final BlockState HOWDAH = Blocks.WHITE_WOOL.defaultBlockState();
        private static final BlockState TRIM = Blocks.GOLD_BLOCK.defaultBlockState();
        private static final BlockState POLE = Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
        private static final BlockState PENNANT = Blocks.YELLOW_WOOL.defaultBlockState();
        private static final BlockState PENNANT_TAIL = Blocks.WHITE_WOOL.defaultBlockState();
        private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState();
        static final float BACK = -2.0f;

        private final BlockRenderDispatcher blocks;

        OriflammeLayer(RenderLayerParent<EntityElephant, ModelElephant> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, EntityElephant entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            float z = 0.25f;
            BlockPieces.piece(blocks, ps, buf, light, TRIM, 0f, BACK, z, 1.25f, 0.08f, 1.35f);
            BlockPieces.piece(blocks, ps, buf, light, HOWDAH, 0f, BACK - 0.08f, z, 1.1f, 0.42f, 1.2f);
            BlockPieces.piece(blocks, ps, buf, light, TRIM, 0f, BACK - 0.5f, z, 1.2f, 0.06f, 1.3f);
            float poleY = BACK - 0.56f;
            BlockPieces.piece(blocks, ps, buf, light, POLE, 0f, poleY, z + 0.3f, 0.09f, 1.6f, 0.09f);
            BlockPieces.piece(blocks, ps, buf, light, PENNANT, 0f, poleY - 1.1f, z + 0.7f, 0.03f, 0.45f, 0.75f);
            BlockPieces.piece(blocks, ps, buf, light, PENNANT_TAIL, 0f, poleY - 1.25f, z + 1.2f, 0.03f, 0.25f, 0.35f);
            BlockPieces.piece(blocks, ps, buf, light, LAMP, 0f, poleY, z - 0.25f, 0.3f, 0.3f, 0.3f);
        }
    }
}
