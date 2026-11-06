package com.solegendary.reignofnether.worldmemory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The block side of {@link TrampleRules#surfaceOf}: a block's trample surface by its registry id, cached per block. The
 * server's stamp filter and the client's mesh both ask (the integrated server and the client are two threads, so the
 * cache is concurrent), so what is worn is what is drawn.
 */
public final class TrampleSurfaces {
    private TrampleSurfaces() { }

    private static final ConcurrentHashMap<Block, TrampleRules.Surface> CACHE = new ConcurrentHashMap<>();

    public static TrampleRules.Surface of(BlockState state) {
        if (state == null)
            return TrampleRules.Surface.NONE;
        Block block = state.getBlock();
        TrampleRules.Surface known = CACHE.get(block);   // the hot path: a plain read, no lambda, no lock
        if (known != null)
            return known;
        return CACHE.computeIfAbsent(block, b -> {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            return TrampleRules.surfaceOf(id == null ? null : id.toString());
        });
    }
}
