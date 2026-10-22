package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.LeafFade;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The RTS leaf modes (orthoview/LeafFade) under Embeddium: Embeddium 0.3.31 (the testers' pack and the eyes shader lane)
 * meshes chunk sections from its own WorldSlice, never vanilla's RenderChunkRegion, so RenderChunkRegionMixin alone left
 * every leaf in place there. ChunkBuilderMeshingTask reads each block through getBlockState(int, int, int) and its face
 * culling through getBlockState(BlockPos), which calls the former - one hook covers both.
 * <p>
 * Optional: {@code @Pseudo} and {@code require = 0}, so the plain client (no Embeddium) loads without it, and a later
 * Embeddium that renames the method only loses the leaf modes there, never the game.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
public abstract class EmbeddiumWorldSliceMixin {

    @Unique
    private boolean reignofnether$inLeafLookup = false;

    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"),
            cancellable = true, remap = false, require = 0)
    private void reignofnether$hideLeaves(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (this.reignofnether$inLeafLookup || !LeafFade.meshActive())
            return;
        BlockState state = cir.getReturnValue();
        if (state == null)
            return;
        if (LeafFade.isFadeable(state)) {
            if (LeafFade.hiddenInMesh(x, y, z))
                cir.setReturnValue(Blocks.AIR.defaultBlockState());
        } else if (LeafFade.isSnowLayer(state) && LeafFade.hiddenInMesh(x, y - 1, z)) {
            this.reignofnether$inLeafLookup = true;
            try {
                // through the BlockGetter face (WorldSlice is one), not a @Shadow: a renamed int overload in a later
                // Embeddium must cost the leaf modes, never the game
                if (LeafFade.isFadeable(((BlockGetter) (Object) this).getBlockState(new BlockPos(x, y - 1, z))))
                    cir.setReturnValue(Blocks.AIR.defaultBlockState());
            } finally {
                this.reignofnether$inLeafLookup = false;
            }
        }
    }
}
