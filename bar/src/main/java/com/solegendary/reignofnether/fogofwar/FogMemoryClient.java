package com.solegendary.reignofnether.fogofwar;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client's fog memory: remembered terrain (palette snapshots per section, null entry = all air) of chunks that
 * left the chunk cache. Used by ClientChunkCacheMixin. It must not outlive the world it was taken in: a rematch
 * ("Back to lobby") keeps the same ClientLevel, so it is also cleared when a match preparation starts
 * ({@link #onPrepState}). Client main thread only.
 */
public final class FogMemoryClient {
    private FogMemoryClient() { }

    private static Map<ChunkPos, PalettedContainer<BlockState>[]> memory;

    public static Map<ChunkPos, PalettedContainer<BlockState>[]> map() {
        if (memory == null) memory = new LinkedHashMap<>();
        return memory;
    }

    public static void clear(String why) {
        int n = memory == null ? 0 : memory.size();
        if (memory != null) memory.clear();
        ReignOfNether.LOGGER.info("[FogMemory] cleared {} chunks ({})", n, why);
    }

    /** Prep running state changed: any change to or from running clears; a start also forgets what was explored. */
    public static void onPrepState(boolean was, boolean now) {
        if (was == now) return;
        clear(now ? "match preparation started" : "match preparation ended");
        if (!was && now) FogExploration.clear();
    }
}
