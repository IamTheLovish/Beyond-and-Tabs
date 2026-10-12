package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.model.ModelRhinoceros;
import com.github.alexthe666.alexsmobs.client.render.RenderRhinoceros;
import com.github.alexthe666.alexsmobs.entity.EntityRhinoceros;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit;

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
 * The Ironhide Horde Gorehorn Rhino: Alex's Mobs' rhinoceros drawn x{@link GorehornRhinoUnit#SCALE} under rust-red
 * plate - exposed-copper plates over its shoulders and rump, a rack of dripstone spikes down the spine and a chain
 * girth (block pieces, {@link BlockPieces}). From AM's model: the shoulder top sits at model y = -0.56 over z -1.25..0
 * (the head is at -z), the rump top at y = -0.31 over z 0..1.2, the body about 0.6 wide each side.
 */
@OnlyIn(Dist.CLIENT)
public class GorehornRhinoRenderer extends RenderRhinoceros {

    public GorehornRhinoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= GorehornRhinoUnit.SCALE;
        this.addLayer(new PlateLayer(this, ctx.getBlockRenderDispatcher()));
    }

    @Override
    protected void scale(EntityRhinoceros entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(GorehornRhinoUnit.SCALE, GorehornRhinoUnit.SCALE, GorehornRhinoUnit.SCALE);
    }

    static class PlateLayer extends RenderLayer<EntityRhinoceros, ModelRhinoceros> {
        private static final BlockState PLATE = Blocks.EXPOSED_CUT_COPPER.defaultBlockState();
        private static final BlockState RIVET = Blocks.IRON_BLOCK.defaultBlockState();
        private static final BlockState SPIKE = Blocks.POINTED_DRIPSTONE.defaultBlockState();
        private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
        static final float SHOULDER = -0.56f, RUMP = -0.31f;

        private final BlockRenderDispatcher blocks;

        PlateLayer(RenderLayerParent<EntityRhinoceros, ModelRhinoceros> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, EntityRhinoceros entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            BlockPieces.piece(blocks, ps, buf, light, PLATE, 0f, SHOULDER, -0.62f, 1.3f, 0.07f, 1.1f);
            BlockPieces.piece(blocks, ps, buf, light, PLATE, 0f, RUMP, 0.62f, 1.15f, 0.07f, 1.05f);
            BlockPieces.piece(blocks, ps, buf, light, RIVET, 0f, SHOULDER - 0.07f, -0.62f, 0.2f, 0.05f, 0.9f);
            for (int i = 0; i < 4; i++)
                BlockPieces.piece(blocks, ps, buf, light, SPIKE, 0f, SHOULDER - 0.07f, -1.0f + i * 0.28f, 0.22f, 0.32f - i * 0.03f, 0.22f);
            for (int i = 0; i < 3; i++)
                BlockPieces.piece(blocks, ps, buf, light, SPIKE, 0f, RUMP - 0.07f, 0.25f + i * 0.3f, 0.2f, 0.24f - i * 0.04f, 0.2f);
            BlockPieces.piece(blocks, ps, buf, light, CHAIN, 0.6f, SHOULDER + 0.05f, -0.1f, 0.12f, 0.12f, 0.12f);
            BlockPieces.piece(blocks, ps, buf, light, CHAIN, -0.6f, SHOULDER + 0.05f, -0.1f, 0.12f, 0.12f, 0.12f);
        }
    }
}
