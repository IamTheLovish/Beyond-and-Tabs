package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.LeafFade;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The RTS leaf modes (orthoview/LeafFade) on vanilla's chunk mesher: a leaf inside a fade hole (or every leaf, in the
 * ALL mode) is meshed as air, and a snow layer resting on one goes with it. Until Oct 22 this swapped leaves for green
 * stained glass in windows round every unit and the cursor, re-meshing the chunks round each of them every 5 frames -
 * see LeafFade for what replaced it. Embeddium meshes through its own WorldSlice: EmbeddiumWorldSliceMixin.
 */
@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin {

    @Unique
    private boolean reignofnether$inLeafLookup = false;

    @Inject(
            method = "getBlockState",
            at = @At("RETURN"),
            cancellable = true
    )
    private void getBlockState(BlockPos pPos, CallbackInfoReturnable<BlockState> cir) {
        if (this.reignofnether$inLeafLookup || !LeafFade.meshActive())
            return;   // a nested below() lookup, or no leaf mode on: one volatile read per block
        BlockState state = cir.getReturnValue();
        if (state == null)
            return;
        if (LeafFade.isFadeable(state)) {
            if (LeafFade.hiddenInMesh(pPos.getX(), pPos.getY(), pPos.getZ()))
                cir.setReturnValue(Blocks.AIR.defaultBlockState());
        } else if (LeafFade.isSnowLayer(state) && LeafFade.hiddenInMesh(pPos.getX(), pPos.getY() - 1, pPos.getZ())
                && LeafFade.isFadeable(reignofnether$below(pPos))) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    // Read through the region (i.e. the main-thread palette copies), not the live level.
    @Unique
    private BlockState reignofnether$below(BlockPos pPos) {
        this.reignofnether$inLeafLookup = true;
        try {
            return ((RenderChunkRegion) (Object) this).getBlockState(pPos.below());
        } finally {
            this.reignofnether$inLeafLookup = false;
        }
    }
}
