package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit;

import net.minecraft.client.model.TurtleModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.TurtleRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tidewrought Shellback Siege-Turtle: the vanilla sea turtle drawn x{@link ShellbackTurtleUnit#SCALE} with a timber
 * fort on its shell (block pieces, {@link BlockPieces}): a spruce deck, mangrove palisade posts at the corners, a
 * prismarine-brick keep with a copper-capped deck gun facing forward, a tide bell on a post at the stern, lanterns, and
 * anchor chains that drop over the flanks while it is moored. From the vanilla model: the shell's top sits at model
 * y = 0.94 over z -0.44..0.81 (the head is at -z) and x -0.59..0.59.
 */
@OnlyIn(Dist.CLIENT)
public class ShellbackTurtleRenderer extends TurtleRenderer {

    public ShellbackTurtleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= ShellbackTurtleUnit.SCALE;
        this.addLayer(new FortLayer(this, ctx.getBlockRenderDispatcher()));
    }

    @Override
    protected void scale(Turtle entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(ShellbackTurtleUnit.SCALE, ShellbackTurtleUnit.SCALE, ShellbackTurtleUnit.SCALE);
    }

    static class FortLayer extends RenderLayer<Turtle, TurtleModel<Turtle>> {
        private static final BlockState DECK = Blocks.SPRUCE_PLANKS.defaultBlockState();
        private static final BlockState POST = Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState();
        private static final BlockState KEEP = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        private static final BlockState ROOF = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        private static final BlockState GUN = Blocks.DISPENSER.defaultBlockState();
        // the bell's body is a block-entity render, not a block model: a gold block under a post stands in for it
        private static final BlockState BELL = Blocks.GOLD_BLOCK.defaultBlockState();
        private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
        private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
        static final float SHELL = 0.94f, MID_Z = 0.19f;

        private final BlockRenderDispatcher blocks;

        FortLayer(RenderLayerParent<Turtle, TurtleModel<Turtle>> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Turtle entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            // the deck, then the palisade posts at its corners
            BlockPieces.piece(blocks, ps, buf, light, DECK, 0f, SHELL, MID_Z, 1.0f, 0.06f, 1.05f);
            for (int i = 0; i < 4; i++)   // no per-frame arrays: the corner from the loop index
                BlockPieces.piece(blocks, ps, buf, light, POST, (i & 1) == 0 ? -0.45f : 0.45f, SHELL - 0.06f,
                    MID_Z + ((i & 2) == 0 ? -0.47f : 0.47f), 0.1f, 0.22f, 0.1f);
            // the keep with its copper cap, the deck gun looking forward over the head
            BlockPieces.piece(blocks, ps, buf, light, KEEP, 0f, SHELL - 0.06f, MID_Z + 0.08f, 0.42f, 0.3f, 0.42f);
            BlockPieces.piece(blocks, ps, buf, light, ROOF, 0f, SHELL - 0.36f, MID_Z + 0.08f, 0.5f, 0.06f, 0.5f);
            BlockPieces.piece(blocks, ps, buf, light, GUN, 0f, SHELL - 0.06f, MID_Z - 0.32f, 0.22f, 0.18f, 0.22f);
            // the tide bell at the stern, lanterns on the bow posts
            BlockPieces.piece(blocks, ps, buf, light, POST, 0f, SHELL - 0.06f, MID_Z + 0.42f, 0.06f, 0.34f, 0.06f);
            BlockPieces.piece(blocks, ps, buf, light, BELL, 0f, SHELL - 0.16f, MID_Z + 0.42f, 0.14f, 0.14f, 0.14f);
            BlockPieces.piece(blocks, ps, buf, light, LANTERN, -0.45f, SHELL - 0.28f, MID_Z - 0.47f, 0.1f, 0.1f, 0.1f);
            BlockPieces.piece(blocks, ps, buf, light, LANTERN, 0.45f, SHELL - 0.28f, MID_Z - 0.47f, 0.1f, 0.1f, 0.1f);
            // moored: anchor chains over both flanks to the ground
            if (entity instanceof ShellbackTurtleUnit t && t.isMooredClient()) {
                BlockPieces.piece(blocks, ps, buf, light, CHAIN, -0.6f, 1.5f, MID_Z, 0.1f, 1.5f - SHELL, 0.1f);
                BlockPieces.piece(blocks, ps, buf, light, CHAIN, 0.6f, 1.5f, MID_Z, 0.1f, 1.5f - SHELL, 0.1f);
            }
        }
    }
}
