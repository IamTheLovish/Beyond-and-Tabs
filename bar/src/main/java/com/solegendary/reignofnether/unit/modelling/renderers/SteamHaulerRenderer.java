package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit;

import net.minecraft.client.model.SnifferModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.SnifferRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Brassworks Steam Hauler: the vanilla sniffer drawn x{@link SteamHaulerUnit#SCALE} as a traction engine (block
 * pieces, {@link BlockPieces}): a brass boiler (waxed cut copper) lying along the back, a soot-black smokestack at the
 * front, a lit blast-furnace firebox at the rear, a coal hopper, iron-chain crane arm with a hook, and grindstone
 * "gear wheels" on both flanks - Brassworks brass, soot and furnace orange (design/brassworks_plan.md). From the
 * vanilla model: the body's top sits at model y = -0.56 over z -1.25..1.25 (the head is at -z), x +-0.78.
 */
@OnlyIn(Dist.CLIENT)
public class SteamHaulerRenderer extends SnifferRenderer {

    public SteamHaulerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= SteamHaulerUnit.SCALE;
        this.addLayer(new BoilerLayer(this, ctx.getBlockRenderDispatcher()));
    }

    @Override
    protected void scale(Sniffer entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(SteamHaulerUnit.SCALE, SteamHaulerUnit.SCALE, SteamHaulerUnit.SCALE);
    }

    static class BoilerLayer extends RenderLayer<Sniffer, SnifferModel<Sniffer>> {
        private static final BlockState BRASS = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        private static final BlockState BAND = Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState();
        private static final BlockState SOOT = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        private static final BlockState FIREBOX = Blocks.BLAST_FURNACE.defaultBlockState()
            .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true);
        private static final BlockState COAL = Blocks.COAL_BLOCK.defaultBlockState();
        private static final BlockState GEAR = Blocks.GRINDSTONE.defaultBlockState();
        private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
        private static final BlockState ARM = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
        static final float BACK = -0.56f;

        private final BlockRenderDispatcher blocks;

        BoilerLayer(RenderLayerParent<Sniffer, SnifferModel<Sniffer>> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Sniffer entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            // the boiler along the back with three bands, the smokestack at the front with a soot cap
            BlockPieces.piece(blocks, ps, buf, light, BRASS, 0f, BACK, -0.1f, 0.9f, 0.55f, 1.6f);
            BlockPieces.piece(blocks, ps, buf, light, BAND, 0f, BACK + 0.01f, -0.7f, 0.96f, 0.6f, 0.08f);
            BlockPieces.piece(blocks, ps, buf, light, BAND, 0f, BACK + 0.01f, -0.1f, 0.96f, 0.6f, 0.08f);
            BlockPieces.piece(blocks, ps, buf, light, BAND, 0f, BACK + 0.01f, 0.5f, 0.96f, 0.6f, 0.08f);
            BlockPieces.piece(blocks, ps, buf, light, SOOT, 0f, BACK - 0.55f, -0.7f, 0.22f, 0.7f, 0.22f);
            BlockPieces.piece(blocks, ps, buf, light, SOOT, 0f, BACK - 1.25f, -0.7f, 0.32f, 0.08f, 0.32f);
            // the firebox and the coal hopper at the rear
            BlockPieces.piece(blocks, ps, buf, light, FIREBOX, 0f, BACK, 0.95f, 0.6f, 0.5f, 0.35f);
            BlockPieces.piece(blocks, ps, buf, light, COAL, 0f, BACK - 0.5f, 0.95f, 0.45f, 0.18f, 0.3f);
            // the gear wheels on both flanks
            BlockPieces.piece(blocks, ps, buf, light, GEAR, -0.82f, BACK + 0.9f, 0.2f, 0.12f, 0.55f, 0.55f);
            BlockPieces.piece(blocks, ps, buf, light, GEAR, 0.82f, BACK + 0.9f, 0.2f, 0.12f, 0.55f, 0.55f);
            // the crane: a post, a jib out over the right flank, a chain and a lantern for a hook
            BlockPieces.piece(blocks, ps, buf, light, ARM, 0.35f, BACK - 0.55f, 0.45f, 0.1f, 0.5f, 0.1f);
            BlockPieces.piece(blocks, ps, buf, light, ARM, 0.6f, BACK - 1.05f, 0.45f, 0.6f, 0.08f, 0.08f);
            BlockPieces.piece(blocks, ps, buf, light, CHAIN, 0.88f, BACK - 0.55f, 0.45f, 0.06f, 0.5f, 0.06f);
            BlockPieces.piece(blocks, ps, buf, light, LANTERN, 0.88f, BACK - 0.45f, 0.45f, 0.12f, 0.12f, 0.12f);
        }
    }
}
