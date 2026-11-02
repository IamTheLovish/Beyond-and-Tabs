package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

/**
 * The world's arena slots, saved with it (issue #35): which slot of the {@link ArenaSlots} grid the next match is
 * built on. Saved in the overworld's data storage, so a server that restarts between matches carries on with the slot
 * after the last and never reuses ground. Game thread only.
 */
public class ArenaOrigins extends SavedData {
    static final String NAME = "bt-arena-origins";
    static final String CURSOR = "cursor";

    private ArenaSlots.Cursor cursor = new ArenaSlots.Cursor();

    public ArenaOrigins() { }

    @Nonnull
    public static ArenaOrigins get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ArenaOrigins::load, ArenaOrigins::new, NAME);
    }

    public static ArenaOrigins load(CompoundTag tag) {
        ArenaOrigins data = new ArenaOrigins();
        data.cursor = ArenaSlots.Cursor.fromInts(tag.getIntArray(CURSOR));
        return data;
    }

    @Nonnull
    @Override
    public CompoundTag save(@Nonnull CompoundTag tag) {
        tag.putIntArray(CURSOR, cursor.toInts());
        return tag;
    }

    /** The live cursor; callers that move it must call {@link #setDirty()} (NewMatch does, and flushes). */
    public ArenaSlots.Cursor cursor() {
        return cursor;
    }

    /** The cursor as saved ints, to put back with {@link #restore} (game tests leave the world as they found it). */
    public int[] snapshot() {
        return cursor.toInts();
    }

    /** Puts a {@link #snapshot} back; null or a damaged one means "no shift has ever happened". */
    public void restore(int[] saved) {
        cursor = ArenaSlots.Cursor.fromInts(saved);
        setDirty();
    }

    /**
     * Where the next match's arena is centred: the ground at the slot a new match moved to, or {@code fallback} (where
     * the host stands: the lobby's and the skirmish's way since they began) when this world has never moved its arena.
     * Once the arena has moved, the host no longer decides it - the world border, the world spawn and the lobby are all
     * at the slot, and a host who has flown off must not start a match outside them.
     */
    public static BlockPos centreFor(ServerLevel level, BlockPos fallback) {
        int[] at = get(level.getServer()).cursor.current();
        if (at == null)
            return fallback;
        return groundAt(level, at[0], at[1]);
    }

    /** The ground block at x, z (loads that one chunk), kept inside the level's build height. */
    static BlockPos groundAt(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);   // the heightmaps need the chunk; one chunk, not the arena
        int y = MetalPatches.solidTop(level, x, z);
        y = Math.max(level.getMinBuildHeight() + 1, Math.min(y, level.getMaxBuildHeight() - 4));
        return new BlockPos(x, y, z);
    }
}
