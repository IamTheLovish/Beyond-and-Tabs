package com.solegendary.reignofnether.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Read access to a chunk's holder from the server thread, for the headless test's far-chunk waits
 * (gametest/GameTestChunks): the holder says which generation status the chunk has reached while it is still on
 * its way to FULL, which is the only way to tell "slow" from "stuck" for a chunk that ServerChunkCache.getChunkNow
 * reports as absent either way. Vanilla keeps the method protected (ChunkMap.getVisibleChunkIfPresent, the map the
 * main thread reads; the updating map is the distance manager's own). Read-only: nothing here schedules or blocks.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    /** The chunk's holder as the main thread sees it, or null when no ticket reaches the chunk. */
    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder reignofnether$getVisibleChunkIfPresent(long chunkPos);

    /** Entity id -> its TrackedEntity (package-private; cast to fogofwar.TrackedEntityInvoker). Fog re-tracking only. */
    @Accessor("entityMap")
    Int2ObjectMap<?> reignofnether$getEntityMap();

    /** The view distance the chunk map sends chunks and tracks entities by (1.20.1 keeps it as is, clamped 2..32). */
    @Accessor("viewDistance")
    int reignofnether$getViewDistance();
}
