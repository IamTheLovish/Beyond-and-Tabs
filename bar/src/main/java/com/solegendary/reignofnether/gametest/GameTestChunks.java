package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;

/**
 * Keeps far-away benchmark chunks loaded WITHOUT blocking the server thread for good.
 *
 * ServerLevel.setChunkForced(x, z, true) generates the chunk synchronously; twice on CI (runs 38297338888 and
 * 38305869783) the server thread parked in it for 20+ minutes waiting on generation at x-3000 and the 40-minute
 * watchdog killed the suite with no verdict. A FORCED region ticket (what setChunkForced adds underneath, same level 2)
 * loads the chunk asynchronously instead; callers wait until the chunks are entity-ticking and fail cleanly if they
 * never are. Not persisted in ForcedChunksSavedData, so always pair hold() with release().
 *
 * The wait is bounded ({@link #WAIT_MS}): run 38342219886 parked the server thread in vanilla's own
 * StructureUtils.forceLoadChunks -> setChunkForced -> getChunk for 20+ minutes with every worldgen worker idle - a
 * chunk future that never completes, not slow generation - so an unbounded wait is never safe. {@link #awaitLoaded}
 * pumps the chunk source's own task queue (what getChunk's managedBlock does) until every chunk is FULL or the deadline
 * passes, then logs a "[ChunkWait]" line and gives up. A ticket is never dropped on a chunk that has not finished
 * loading ({@link #releaseAll}): run 38336416146 hung the NEXT batch's structure load right after far tickets were
 * released mid-generation, so a stuck chunk keeps its ticket (the test world is thrown away anyway).
 */
public final class GameTestChunks {
    private GameTestChunks() { }

    /** How long a wait for chunks may block the server thread before it gives up and says so. */
    public static final long WAIT_MS = 60_000L;

    public static void hold(ServerLevel level, int cx, int cz) {
        ChunkPos pos = new ChunkPos(cx, cz);
        level.getChunkSource().addRegionTicket(TicketType.FORCED, pos, 2, pos);
    }

    public static void release(ServerLevel level, int cx, int cz) {
        ChunkPos pos = new ChunkPos(cx, cz);
        level.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, 2, pos);
    }

    /** {@link #hold} for every {cx, cz} pair. */
    public static void holdAll(ServerLevel level, Iterable<long[]> chunks) {
        for (long[] c : chunks)
            hold(level, (int) c[0], (int) c[1]);
    }

    /**
     * {@link #release} for every {cx, cz} pair that is loaded. A chunk still loading keeps its ticket (and a log
     * line says so): dropping a ticket mid-generation is the suspected trigger of the permanent chunk-load hang.
     */
    public static void releaseAll(ServerLevel level, Iterable<long[]> chunks) {
        List<String> kept = new ArrayList<>();
        for (long[] c : chunks) {
            int cx = (int) c[0], cz = (int) c[1];
            if (isLoaded(level, cx, cz))
                release(level, cx, cz);
            else
                kept.add(cx + "," + cz);
        }
        if (!kept.isEmpty())
            ReignOfNether.LOGGER.warn("[ChunkWait] kept the FORCED ticket of {} chunk(s) still loading at release: {}",
                kept.size(), String.join(" ", kept));
    }

    /** Whether the chunk is loaded to FULL right now (never blocks; false off the server thread). */
    public static boolean isLoaded(ServerLevel level, int cx, int cz) {
        return level.getChunkSource().getChunkNow(cx, cz) != null;
    }

    /**
     * Blocks the server thread until every chunk is loaded to FULL or {@code timeoutMs} passed, running the chunk
     * source's queued work meanwhile (as vanilla's getChunk does, but bounded). Returns the chunks still missing
     * (empty when all are loaded), after logging them.
     */
    public static List<long[]> awaitLoaded(ServerLevel level, Iterable<long[]> chunks, long timeoutMs) {
        ServerChunkCache cache = level.getChunkSource();
        long start = System.nanoTime(), deadline = start + timeoutMs * 1_000_000L;
        List<long[]> missing = new ArrayList<>();
        int polls = 0;
        while (true) {
            missing.clear();
            for (long[] c : chunks)
                if (cache.getChunkNow((int) c[0], (int) c[1]) == null)
                    missing.add(c);
            if (missing.isEmpty())
                return missing;
            if (System.nanoTime() >= deadline)
                break;
            // the chunk source's main-thread queue: distance-manager updates, chunk promotions, light; empty => idle
            if (!cache.pollTask()) {
                Thread.yield();
                LockSupport.parkNanos("waiting for chunks", 100_000L);
            }
            polls++;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(missing.size(), 12); i++)
            sb.append(i == 0 ? "" : " ").append(missing.get(i)[0]).append(',').append(missing.get(i)[1]);
        ReignOfNether.LOGGER.warn(String.format(Locale.ROOT,
            "[ChunkWait] %d of %d chunk(s) not loaded after %.1f s (%d polls): %s%s", missing.size(), count(chunks),
            (System.nanoTime() - start) / 1e9, polls, sb, missing.size() > 12 ? " ..." : ""));
        return missing;
    }

    /**
     * {@link #holdAll} then {@link #awaitLoaded} with {@link #WAIT_MS}; a chunk that never loads fails the test
     * (the tickets of the loaded ones are released, the stuck one keeps its own) instead of hanging the server
     * thread until CI's watchdog kills the suite.
     */
    public static void holdAndAwaitOrFail(ServerLevel level, List<long[]> chunks, String what) {
        holdAll(level, chunks);
        List<long[]> missing = awaitLoaded(level, chunks, WAIT_MS);
        if (!missing.isEmpty()) {
            releaseAll(level, chunks);
            throw new GameTestAssertException(missing.size() + " of " + chunks.size() + " " + what
                + " chunks never loaded (first " + missing.get(0)[0] + "," + missing.get(0)[1] + ")");
        }
    }

    /**
     * The chunk, loaded to FULL, as {@code level.getChunk(cx, cz)} gives it - but through the same one-tick UNKNOWN
     * ticket getChunk adds and a bounded wait: null (and a "[ChunkWait]" line) after {@link #WAIT_MS}. On a timeout
     * the chunk also gets a FORCED ticket that is never released, so the expiring ticket cannot drop it mid-generation.
     */
    public static LevelChunk loadNow(ServerLevel level, int cx, int cz) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk != null)
            return chunk;
        ChunkPos pos = new ChunkPos(cx, cz);
        level.getChunkSource().addRegionTicket(TicketType.UNKNOWN, pos, 0, pos);   // level 33 = FULL, as getChunk does
        List<long[]> one = List.of(new long[] { cx, cz });
        if (!awaitLoaded(level, one, WAIT_MS).isEmpty()) {
            hold(level, cx, cz);
            return null;
        }
        return level.getChunkSource().getChunkNow(cx, cz);
    }

    /** {@link #loadNow}, failing the running test (GameTestAssertException) instead of returning null. */
    public static LevelChunk loadNowOrFail(ServerLevel level, int cx, int cz) {
        LevelChunk chunk = loadNow(level, cx, cz);
        if (chunk == null)
            throw new GameTestAssertException("chunk " + cx + "," + cz + " never loaded (" + WAIT_MS / 1000 + " s)");
        return chunk;
    }

    /**
     * Whether vanilla's StructureUtils.forceLoadChunks should go through {@link #hold} + {@link #awaitLoaded}
     * (mixin/StructureUtilsMixin): only on the headless game-test server, unless forced on or off with
     * {@code -Dreignofnether.asyncStructureChunks=true|false}. Normal play never runs the game-test framework, and
     * stays untouched either way.
     */
    public static boolean asyncStructureLoad(ServerLevel level) {
        String prop = System.getProperty("reignofnether.asyncStructureChunks");
        if (prop != null)
            return Boolean.parseBoolean(prop);
        return level.getServer() instanceof GameTestServer || Boolean.getBoolean("forge.gameTestServer");
    }

    private static int count(Iterable<long[]> chunks) {
        int n = 0;
        for (long[] ignored : chunks)
            n++;
        return n;
    }
}
