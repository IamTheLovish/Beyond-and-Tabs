package com.solegendary.reignofnether.worldmemory;

import net.minecraft.world.level.LevelHeightAccessor;

/**
 * Whether a scorch or crater stamp centred on block column (bx, bz) may land: GroundMemoryServer.stamp asks this before
 * it touches the world. Kept apart from the server class (no Forge, no level) so JUnit can ask it about any world height.
 */
public final class StampGate {

    /** Is chunk (chunkX, chunkZ) loaded on the server (ServerLevel: {@code getChunkSource()::hasChunk}). */
    @FunctionalInterface
    public interface ChunkPresence {
        boolean hasChunk(int chunkX, int chunkZ);
    }

    private StampGate() {}

    /** True when the column's chunk is loaded, whatever the world's height range. */
    public static boolean columnLoaded(LevelHeightAccessor world, ChunkPresence chunks, int bx, int bz) {
        // the chunk, never a block at a fixed y: Level.isLoaded(new BlockPos(bx, 0, bz)) is false wherever y 0 is outside
        // the build height, and the battlefield world starts at y 48, so that check refused every scorch and crater in a
        // real match (eyes scars run 42217669387 on main's jar: "0 chunks marked on the server")
        return chunks.hasChunk(bx >> 4, bz >> 4);
    }
}
