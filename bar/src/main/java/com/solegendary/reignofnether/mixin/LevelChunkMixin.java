package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.resources.ResourceIndex;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityGrid;
import com.solegendary.reignofnether.worldmemory.WorldMemoryHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mark the walkability chunk dirty on any loaded-chunk block change, on both client and server (the
// server-side grid is the one the pathfinder reads; client has no grid so it no-ops). LevelChunk.setBlockState
// is the single chokepoint every such change funnels through (worldgen uses ProtoChunk, so this never fires
// during generation). Marking (not evicting) keeps the chunk readable until the deferred drain rebuilds it.
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Shadow public abstract Level getLevel();

    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void reignofnether$invalidateWalkability(BlockPos pos, BlockState state, boolean isMoving,
                                                     CallbackInfoReturnable<BlockState> cir) {
        // Only feed the walkability cache when the rtsPathfinding gamerule is on; with it off the
        // grid is unused so there's nothing to invalidate. The resource index is independent and always runs.
        if (UnitServerEvents.rtsPathfinding)
            WalkabilityGrid.markChunkDirtyIfPresent(getLevel(), pos);
        ResourceIndex.onBlockChange(getLevel(), pos, state);
        // the chase goals' per-tick cell memo (Terrain.classify) must not answer from before this change
        com.solegendary.reignofnether.unit.Terrain.onBlockChanged();
        // client: a ground scar's decal follows the column's new top face (worldmemory/GroundMemoryClient)
        WorldMemoryHooks.onBlockChanged(getLevel(), pos);
        // server: the battlefield journal keeps each block's state from before the match, for Back to lobby
        // (match/BattlefieldJournal); the return value is the replaced state, null when nothing changed
        com.solegendary.reignofnether.match.BattlefieldJournal.onBlockChanged(getLevel(), pos, cir.getReturnValue());
    }
}
