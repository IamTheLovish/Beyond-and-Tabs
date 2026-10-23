package com.solegendary.reignofnether.maps;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

/** The world's arena slots, saved with it. NOT BUILT YET: the stub the tests are written against. */
public class ArenaOrigins extends SavedData {
    private ArenaSlots.Cursor cursor = new ArenaSlots.Cursor();

    public ArenaOrigins() { }

    public static ArenaOrigins get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ArenaOrigins::load, ArenaOrigins::new, "bt-arena-origins");
    }

    public static ArenaOrigins load(CompoundTag tag) {
        return new ArenaOrigins();
    }

    public ArenaSlots.Cursor cursor() {
        return cursor;
    }

    public int[] snapshot() {
        return cursor.toInts();
    }

    public void restore(int[] saved) {
        cursor = ArenaSlots.Cursor.fromInts(saved);
    }

    /** The centre the next match uses: the current slot's ground, or {@code fallback} when no shift has happened. */
    public static BlockPos centreFor(ServerLevel level, BlockPos fallback) {
        return fallback;
    }

    @Nonnull
    @Override
    public CompoundTag save(@Nonnull CompoundTag tag) {
        return tag;
    }
}
