package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.gametest.GameTestChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The game-test framework force-loads the 5x5 chunks round every test structure with ServerLevel.setChunkForced,
 * which generates each chunk synchronously on the server thread with no way out: CI run 38342219886 parked there
 * (StructureUtils.forceLoadChunks -> setChunkForced -> ServerChunkCache.getChunk) for 20+ minutes while placing the
 * next batch's structures, every worldgen worker idle, until the watchdog killed the suite with no verdict. This
 * adds the same FORCED tickets (level 31, what setChunkForced adds underneath) asynchronously and waits for the
 * chunks with a bounded wait that logs and carries on instead of hanging. Only active on the headless game-test
 * server ({@link GameTestChunks#asyncStructureLoad}); normal play never calls StructureUtils at all.
 */
@Mixin(StructureUtils.class)
public abstract class StructureUtilsMixin {

    @Inject(
            method = "forceLoadChunks(Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void reignofnether$forceLoadChunksAsync(BlockPos pos, ServerLevel level, CallbackInfo ci) {
        if (!GameTestChunks.asyncStructureLoad(level))
            return;
        // vanilla's range: chunk -1 to +3 round the structure's own on both axes (a template is at most 48 blocks)
        ChunkPos center = new ChunkPos(pos);
        List<long[]> chunks = new ArrayList<>();
        for (int i = -1; i < 4; ++i)
            for (int j = -1; j < 4; ++j)
                chunks.add(new long[] { center.x + i, center.z + j });
        GameTestChunks.holdAll(level, chunks);
        // the structure is placed right after this (clearSpaceForStructure, the structure block): the chunks must be
        // loaded by then. A chunk that is not after the deadline is logged and left to the next setBlock, which
        // loads it the old way - no worse than before, but now the log says which chunk, and the tickets stay
        GameTestChunks.awaitLoaded(level, chunks, GameTestChunks.WAIT_MS);
        ci.cancel();
    }
}
