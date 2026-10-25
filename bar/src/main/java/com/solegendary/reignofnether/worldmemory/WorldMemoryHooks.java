package com.solegendary.reignofnether.worldmemory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * Common-side hooks the client's scar mesh listens to, without common code naming a client class: a block changed
 * under a scar (the decal must follow the new top face - mixin/LevelChunkMixin), the fog of war's visible columns
 * changed (the fogged tint is baked into the mesh - FogOfWarClientEvents). The client registers {@link #listener}
 * at startup; on a dedicated server it stays null and every hook is a null check.
 */
public final class WorldMemoryHooks {
    private WorldMemoryHooks() { }

    public interface Listener {
        void blockChanged(Level level, BlockPos pos);

        void fogChanged(Set<ChunkPos> chunks);
    }

    public static volatile Listener listener = null;

    public static void onBlockChanged(Level level, BlockPos pos) {
        Listener l = listener;
        if (l != null && level != null && level.isClientSide())
            l.blockChanged(level, pos);
    }

    public static void onFogChanged(Set<ChunkPos> chunks) {
        Listener l = listener;
        if (l != null && chunks != null && !chunks.isEmpty())
            l.fogChanged(chunks);
    }
}
