package com.solegendary.reignofnether.gametest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps far-away benchmark chunks loaded WITHOUT blocking the server thread.
 *
 * ServerLevel.setChunkForced(x, z, true) generates the chunk synchronously; twice on CI (runs 38297338888 and
 * 38305869783) the server thread parked in it for 20+ minutes waiting on generation at x-3000 and the 40-minute
 * watchdog killed the suite with no verdict. A FORCED region ticket (what setChunkForced adds underneath, same level 2)
 * loads the chunk asynchronously instead; callers wait until the chunks are entity-ticking and fail cleanly if they
 * never are. Not persisted in ForcedChunksSavedData, so always pair hold() with release().
 */
public final class GameTestChunks {
    private GameTestChunks() { }

    public static void hold(ServerLevel level, int cx, int cz) {
        ChunkPos pos = new ChunkPos(cx, cz);
        level.getChunkSource().addRegionTicket(TicketType.FORCED, pos, 2, pos);
    }

    public static void release(ServerLevel level, int cx, int cz) {
        ChunkPos pos = new ChunkPos(cx, cz);
        level.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, 2, pos);
    }
}
