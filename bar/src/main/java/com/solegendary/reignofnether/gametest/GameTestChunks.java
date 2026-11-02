package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.mixin.ChunkMapAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
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
 *
 * What counts as progress is GENERATION progress, not FULL chunks (Oct 17: runs 39064052007, 39066383397 and
 * others failed both duel batches with "380 of 380 duel pad chunks never loaded" after exactly {@link #WAIT_MS}, while
 * the same commit had passed hours earlier and the next wait of the same JVM loaded its 384 chunks fine in ~3 min).
 * Vanilla's chunk scheduler orders work by ticket level and, within a level, first come first served per chunk; the
 * duel pads put ~2,800 chunks at levels 31-32 at once, so the region moves through the statuses in lockstep -
 * every chunk's noise, surface and carvers before any chunk's features - and the FIRST chunk reaches FULL only once
 * most of the region's heavy work is done: about 40 s of a 78 s load on a quick runner, past 60 s on the slower
 * runners of Oct 17 (server start 43-75 s instead of 34 s). "No FULL chunk in 60 s" cannot tell that from a hang.
 * The wait now scores every chunk the wanted ones depend on (their holders' last completed status, see
 * {@link #watchRadius}) and stalls only when NOTHING in that cone advances for {@link #WAIT_MS}; a deadlocked future
 * still trips it (idle workers advance nothing), honest generation never does. It also logs where generation stands
 * every {@link #REPORT_MS}, and the failure message says where every chunk round the missing ones stood.
 */
public final class GameTestChunks {
    private GameTestChunks() { }

    /**
     * How long a wait may go WITHOUT PROGRESS (no chunk in the dependency cone completing a further status, no
     * further wanted chunk reaching FULL) before it gives up and says so. A deadlocked chunk future makes no progress
     * at all, so 60 s catches it; honest generation keeps making progress and is allowed to take as long as it needs
     * up to {@link #HARD_CAP_MS}. Run 38470339031 showed why a flat cap is wrong: the duel pads hold 380 chunks at
     * once and a CI runner generates them at a few per second, so "208 of 384 chunks never loaded" after a flat 60 s
     * was slow generation, not a hang - and Oct 17 showed why FULL chunks alone are the wrong measure (class comment).
     */
    public static final long WAIT_MS = 60_000L;

    /** Absolute ceiling on one wait, well under the 40-minute CI watchdog so a verdict still gets written. */
    public static final long HARD_CAP_MS = 15 * 60_000L;

    /**
     * How often a wait re-checks its chunks and scores the cone round them. The score walks every holder in the cone
     * (~6,000 for the duel pads, a few microseconds each), so it is not done on every poll; between checks the loop
     * only pumps the chunk source's queue.
     */
    static final long CHECK_MS = 250L;

    /** How often a wait logs where generation stands ("[ChunkWait] ... after 15.0 s: ..."), so a slow runner's log shows the rate instead of a minute of silence. */
    static final long REPORT_MS = 15_000L;

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

    /** What a wait ended with: the chunks still missing (empty when all are loaded) and one line on where generation stood. */
    public record Wait(List<long[]> missing, String summary) {
        public boolean complete() {
            return missing.isEmpty();
        }
    }

    /**
     * Blocks the server thread until every chunk is loaded to FULL, running the chunk source's queued work meanwhile
     * (as vanilla's getChunk does). Gives up when {@code stallMs} pass with no progress anywhere in the chunks'
     * dependency cone (a stuck future), or after {@link #HARD_CAP_MS} in all. Returns the chunks still missing
     * (empty when all are loaded), after logging them.
     */
    public static List<long[]> awaitLoaded(ServerLevel level, Iterable<long[]> chunks, long stallMs) {
        return await(level, chunks, stallMs, "chunks").missing();
    }

    /**
     * {@link #awaitLoaded} with the result's summary line kept, for a failure message that says where generation
     * stood (so the next "never loaded" can be read as slow or stuck from the job log alone). {@code what} names the
     * chunks in the log lines.
     */
    public static Wait await(ServerLevel level, Iterable<long[]> chunks, long stallMs, String what) {
        ServerChunkCache cache = level.getChunkSource();
        List<long[]> wanted = new ArrayList<>();
        for (long[] c : chunks)
            wanted.add(c);
        // the wanted chunks and every chunk their generation depends on: a step completed anywhere in it is progress
        List<long[]> watched = new ArrayList<>(wanted);
        watched.addAll(ring(wanted, watchRadius()));
        int[] histogram = new int[ChunkStatus.FULL.getIndex() + 2];   // [0] = no holder / nothing done, [i+1] = status i
        ChunkStatus[] statuses = new ChunkStatus[histogram.length];
        long start = System.nanoTime();
        long lastProgress = start, lastReport = start, nextCheck = start;
        long hardDeadline = start + HARD_CAP_MS * 1_000_000L;
        long bestScore = -1;
        int bestLoaded = -1;
        List<long[]> missing = new ArrayList<>();
        String summary = "";
        int polls = 0;
        while (true) {
            long now = System.nanoTime();
            if (now >= nextCheck) {
                nextCheck = now + CHECK_MS * 1_000_000L;
                missing.clear();
                for (long[] c : wanted)
                    if (cache.getChunkNow((int) c[0], (int) c[1]) == null)
                        missing.add(c);
                if (missing.isEmpty()) {
                    if (now - start >= REPORT_MS * 1_000_000L)
                        ReignOfNether.LOGGER.info(String.format(Locale.ROOT, "[ChunkWait] %s: all %d loaded after %.1f s",
                            what, wanted.size(), (now - start) / 1e9));
                    return new Wait(missing, "all " + wanted.size() + " loaded");
                }
                long score = progressScore(cache, watched, histogram, statuses);
                int loaded = wanted.size() - missing.size();
                if (score > bestScore || loaded > bestLoaded) {
                    bestScore = Math.max(bestScore, score);
                    bestLoaded = Math.max(bestLoaded, loaded);
                    lastProgress = now;
                }
                summary = describe(histogram, statuses, watched.size());
                if (now - lastReport >= REPORT_MS * 1_000_000L) {
                    lastReport = now;
                    ReignOfNether.LOGGER.info(String.format(Locale.ROOT,
                        "[ChunkWait] %s: %d of %d FULL after %.1f s (last progress %.1f s ago); %s",
                        what, loaded, wanted.size(), (now - start) / 1e9, (now - lastProgress) / 1e9, summary));
                }
                if (now - lastProgress >= stallMs * 1_000_000L || now >= hardDeadline)
                    break;
            }
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
        long end = System.nanoTime();
        ReignOfNether.LOGGER.warn(String.format(Locale.ROOT,
            "[ChunkWait] %s: %d of %d chunk(s) not loaded after %.1f s (%d polls, no progress for %.1f s): %s%s; %s",
            what, missing.size(), wanted.size(), (end - start) / 1e9, polls, (end - lastProgress) / 1e9, sb,
            missing.size() > 12 ? " ..." : "", summary));
        return new Wait(missing, String.format(Locale.ROOT, "no progress for %.1f s of %.1f s; %s",
            (end - lastProgress) / 1e9, (end - start) / 1e9, summary));
    }

    /**
     * How far round a wanted chunk its generation reaches: a chunk at FULL needs STRUCTURE_STARTS ten chunks out
     * (ChunkStatus.maxDistance() entries round a full chunk), and the entity-ticking FORCED tickets of {@link #hold}
     * sit two levels under FULL, so their holders reach two chunks further still. Everything in that square has a
     * holder and does work the wanted chunk waits on; nothing outside it does.
     */
    static int watchRadius() {
        return ChunkStatus.maxDistance() + 2;
    }

    /**
     * A monotone measure of generation progress over {@code watched}: for each chunk, one plus the index of the last
     * status its holder has completed (0 = no holder yet, or nothing completed). Every status any chunk of the cone
     * completes raises it by one; it never falls while the tickets stay. {@code histogram} and {@code statuses} are
     * filled on the side with how many chunks stand at each status, for the log line.
     */
    private static long progressScore(ServerChunkCache cache, List<long[]> watched, int[] histogram, ChunkStatus[] statuses) {
        java.util.Arrays.fill(histogram, 0);
        ChunkMapAccessor map = (ChunkMapAccessor) cache.chunkMap;
        long score = 0;
        for (long[] c : watched) {
            ChunkHolder holder = map.reignofnether$getVisibleChunkIfPresent(ChunkPos.asLong((int) c[0], (int) c[1]));
            ChunkStatus status = holder == null ? null : holder.getLastAvailableStatus();
            int bucket = status == null ? 0 : status.getIndex() + 1;
            statuses[bucket] = status;
            histogram[bucket]++;
            score += bucket;
        }
        return score;
    }

    /** "cone of 6102 chunks: 1200 none, 2300 structure_starts, ..., 380 full" from the last {@link #progressScore}. */
    private static String describe(int[] histogram, ChunkStatus[] statuses, int watched) {
        StringBuilder sb = new StringBuilder("cone of ").append(watched).append(" chunks:");
        boolean any = false;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] == 0)
                continue;
            sb.append(any ? ", " : " ").append(histogram[i]).append(' ')
              .append(i == 0 ? "none" : String.valueOf(statuses[i]));
            any = true;
        }
        return sb.toString();
    }

    /**
     * {@link #holdAll} then {@link #await} with {@link #WAIT_MS}; a chunk that never loads fails the test
     * (the tickets of the loaded ones are released, the stuck one keeps its own) instead of hanging the server
     * thread until CI's watchdog kills the suite. The message says where generation stood, so "never loaded" can be
     * read as slow (statuses still spread through the cone) or stuck (nothing moved) from the job log.
     */
    public static void holdAndAwaitOrFail(ServerLevel level, List<long[]> chunks, String what) {
        holdAll(level, chunks);
        Wait wait = await(level, chunks, WAIT_MS, what);
        if (!wait.complete()) {
            releaseAll(level, chunks);
            List<long[]> missing = wait.missing();
            throw new GameTestAssertException(missing.size() + " of " + chunks.size() + " " + what
                + " chunks never loaded (first " + missing.get(0)[0] + "," + missing.get(0)[1] + "; " + wait.summary() + ")");
        }
    }

    /** The chunks within 2 of the listed ones that are not listed themselves (the ring entity ticking needs FULL). */
    public static List<long[]> ring(List<long[]> chunks) {
        return ring(chunks, 2);
    }

    /** The chunks within {@code radius} (Chebyshev) of the listed ones that are not listed themselves, each once. */
    public static List<long[]> ring(List<long[]> chunks, int radius) {
        java.util.Set<Long> listed = new java.util.HashSet<>(), seen = new java.util.HashSet<>();
        for (long[] c : chunks)
            listed.add(ChunkPos.asLong((int) c[0], (int) c[1]));
        List<long[]> out = new ArrayList<>();
        for (long[] c : chunks)
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = (int) c[0] + dx, z = (int) c[1] + dz;
                    long k = ChunkPos.asLong(x, z);
                    if (!listed.contains(k) && seen.add(k))
                        out.add(new long[] { x, z });
                }
        return out;
    }

    /**
     * Blocks (bounded, like {@link #holdAndAwaitOrFail}) until the RING round the held chunks is FULL too: vanilla
     * makes a chunk entity-ticking only once every chunk within 2 of it is FULL, and the FORCED tickets already ask
     * for that ring (their level reaches FULL two chunks out) - but while the server is TICKING, a loaded runner gets
     * through the ring's main-thread work only in each tick's spare time. Build 38873245585 failed
     * audience_cuts_8v8_deliveries with the ring at 38 of 64 FULL and no further chunk in 200 ticks; the wait pumps
     * the chunk source's queue at full speed and gives up only after {@link #WAIT_MS} without progress. A ring that
     * never loads fails the test (the held chunks' tickets released) with what was missing.
     */
    public static void awaitRingOrFail(ServerLevel level, List<long[]> chunks, String what) {
        List<long[]> ring = ring(chunks);
        Wait wait = await(level, ring, WAIT_MS, "ring round the " + what + " chunks");
        if (!wait.complete()) {
            releaseAll(level, chunks);
            List<long[]> missing = wait.missing();
            throw new GameTestAssertException(missing.size() + " of " + ring.size() + " chunks in the ring round the "
                + what + " chunks never loaded (first " + missing.get(0)[0] + "," + missing.get(0)[1] + "; "
                + wait.summary() + ")");
        }
    }

    /**
     * Runs {@code body} once (from the test's tick, at least {@code minTicks} in) when every chunk's ENTITY section is
     * loaded and entity-ticking - a chunk at FULL is not enough: entities load on their own, later, and an entity
     * added to a section that is still loading stays out of level.getEntity (the audience_cuts_8v8_deliveries
     * flake: "spawned units are not visible to the level").
     * <p>
     * The wait is progress-based, like {@link #awaitLoaded}: it gives up only when no further chunk became ready for
     * {@code stallTicks} ticks, or at {@code capTicks} in all (keep that under the test's timeoutTicks, so the verdict
     * is this message, not a timeout). A flat cap of 300 ticks failed build 60b337d on a loaded runner with "4 of 32
     * 8v8 audience chunks never had their entities loaded" - slow IO, still progressing.
     * <p>
     * Progress also counts the HALO: a chunk turns entity-ticking only once every chunk within 2 of it is FULL (vanilla
     * ChunkMap.prepareEntityTickingChunk), and our FORCED tickets load that ring by themselves - so the edge chunks wait
     * on ring generation that the listed chunks never show. Build 38849050175 failed "14 of 32 ... 18 ready after 277
     * ticks, none more in the last 200" while (most likely) the ring was still generating; a ring chunk reaching FULL
     * now counts as progress, and the message says how far the ring got.
     */
    public static void whenEntitiesReady(GameTestHelper helper, ServerLevel level, List<long[]> chunks, int minTicks,
                                         int stallTicks, int capTicks, String what, Runnable body) {
        final int[] t = { 0 }, best = { -1 }, bestAt = { 0 }, bestHalo = { -1 };
        final long[] bestAtNanos = { System.nanoTime() };
        final boolean[] ran = { false };
        java.util.Set<Long> listed = new java.util.HashSet<>(), halo = new java.util.HashSet<>();
        for (long[] c : chunks)
            listed.add(ChunkPos.asLong((int) c[0], (int) c[1]));
        for (long[] c : chunks)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    long k = ChunkPos.asLong((int) c[0] + dx, (int) c[1] + dz);
                    if (!listed.contains(k))
                        halo.add(k);
                }
        helper.onEachTick(() -> {
            if (ran[0])
                return;
            t[0]++;
            if (t[0] < minTicks)
                return;
            int notReady = 0;
            long[] first = null;
            for (long[] c : chunks) {
                int cx = (int) c[0], cz = (int) c[1];
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))
                        || !level.isPositionEntityTicking(new BlockPos((cx << 4) + 8, 64, (cz << 4) + 8))) {
                    notReady++;
                    if (first == null)
                        first = c;
                }
            }
            int ready = chunks.size() - notReady, haloFull = 0;
            if (notReady > 0)
                for (long k : halo)
                    if (level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k)) != null)
                        haloFull++;
            if (ready > best[0] || haloFull > bestHalo[0]) {
                best[0] = Math.max(best[0], ready);
                bestHalo[0] = Math.max(bestHalo[0], haloFull);
                bestAt[0] = t[0];
                bestAtNanos[0] = System.nanoTime();
            }
            // the loads are wall-clock work (IO and worldgen threads) but the GameTest server runs its ticks back to back,
            // several hundred a second when little else runs (run 40447858037): a stall needs stallTicks ticks AND as
            // long in real time as those ticks take at 20 per second
            boolean stalled = t[0] - bestAt[0] >= stallTicks
                && System.nanoTime() - bestAtNanos[0] >= stallTicks * 50_000_000L;
            if (notReady > 0 && !stalled && t[0] < capTicks)
                return;
            ran[0] = true;
            if (notReady > 0)
                throw new GameTestAssertException(notReady + " of " + chunks.size() + " " + what + " chunks never had"
                    + " their entities loaded (first " + first[0] + "," + first[1] + "): " + ready + " ready after "
                    + t[0] + " ticks, " + (stalled ? "none more in the last " + stallTicks : "the cap of " + capTicks)
                    + "; the ring round them (2 chunks, needed FULL for entity ticking) " + haloFull + " of " + halo.size()
                    + " FULL");
            if (t[0] > minTicks)
                ReignOfNether.LOGGER.info("[ChunkWait] {}: entity sections ready after {} ticks", what, t[0]);
            body.run();
        });
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
        if (!await(level, one, WAIT_MS, "chunk " + cx + "," + cz).complete()) {
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
}
