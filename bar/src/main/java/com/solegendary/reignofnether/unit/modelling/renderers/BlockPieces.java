package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Client only: draws a block model as a scaled "piece" inside an entity render layer - the Leviathan's fortress trick
 * (LeviathanRenderer.FortressLayer), shared by the big beasts' howdahs, banners and bone heaps (design/big_units.md).
 * Layers draw in the model's space: 1 unit = 1 block before the renderer's scale, y pointing down, the feet at y = 1.5.
 * A piece is sx x sy x sz model units, centred on (x, z), its bottom face at model height y (it rises toward -y).
 * No allocation per call.
 */
@OnlyIn(Dist.CLIENT)
public final class BlockPieces {
    private BlockPieces() { }

    public static void piece(BlockRenderDispatcher blocks, PoseStack ps, MultiBufferSource buf, int light, BlockState state,
                             float x, float y, float z, float sx, float sy, float sz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(-sx, -sy, sz);   // back to y-up (two flips keep the faces facing out), sized to the piece
        ps.translate(-0.5, 0.0, -0.5);
        blocks.renderSingleBlock(state, ps, buf, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }
}
