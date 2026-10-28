package com.solegendary.reignofnether.fogofwar;

import com.solegendary.reignofnether.ReignOfNether;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

// Pre-game chunk packets inside the world border; served to dark players by ChunkMapInitialSendMixin so
// they can't cheat by reconnecting or leaving/re-entering render range.
//
// Backed by disk, not memory: each captured chunk is encoded to one file under
// <world>/reignofnether/fogsnapshot/ (~50-500 KB per chunk). Only a small in-memory index of which chunks
// exist (plus a bounded LRU of recently-decoded packets) is held on the heap, so even a full 80x80-chunk
// RTS border costs kilobytes of RAM instead of gigabytes. Fog is gated to RTS-optimised maps
// (WorldBorderServerEvents.isRtsOptimisedMap), so the bounded border always fits.
//
// The capture is spread over server ticks. It used to run in one tick at the first RTS join and froze the server
// thread 14-23 s (a synchronous chunk load, packet build, encode and file write for each of ~2,000 chunks):
//  - server thread, at most TICK_BUDGET_NS per tick ({@link #tick}): build the packet of every border chunk that is
//    loaded (the packet constructor copies the chunk's sections, block entities and light, so the packet IS the
//    snapshot) once its light has settled (see below); a chunk that is not loaded gets a level-33 region ticket (FULL, not ticking - what
//    getChunk(x, z, true) asked for) and is built once the chunk system has loaded it on its own workers;
//  - one background thread: encode each built packet and write its file (bytes only, no world access); a chunk
//    joins the on-disk index once its file is written, and until then get() serves the in-memory packet;
//  - a chunk still waiting is captured on the spot, before it can differ from the pre-game state, when one of its
//    blocks is about to change (LevelChunkMixin -> {@link #beforeBlockChange}) or a dark player is about to be sent
//    it ({@link #get}), so the snapshot holds what the old one-tick capture held and get() never returns null for a
//    loaded border chunk while the capture runs.
// Light settles: light is computed on the light engine's own worker, behind the block changes, so a chunk's light can
// still be catching up with blocks changed or chunks lit a moment ago (on a busy host for several ticks). A chunk is
// built only once it is light-correct and a light barrier ({@link #lightBarrier}: a POST_UPDATE task on the light
// engine queue for it and each loaded neighbour, run after every light update queued before it) has completed; until
// then it waits, at no budget cost. The two on-the-spot captures cannot wait:
//  - before a block change: built at once and kept as is. Light computed later would already carry the change (the
//    change's own light update runs in the same light batch as any later barrier), which is what must not leak.
//  - before a dark player is sent it: built at once (the player needs it now) and rebuilt when its barrier completes,
//    unless one of its blocks changes first (then the served build stays, as above).
public class FogChunkSnapshot {

    private static final String DIR_NAME = "reignofnether/fogsnapshot";
    private static final int LRU_CAPACITY = 64; // hot chunks served to multiple players / on relog bursts

    /** Server-thread time the capture may use per tick. */
    public static final long TICK_BUDGET_NS = 20_000_000L;
    /** Chunks taken off the walk (load ticket out or light barrier pending) at once. */
    private static final int MAX_WAITING = 64;
    /** How long finishNow waits for light to settle before building what is left anyway (logged as an error). */
    private static final long FINISH_LIGHT_WAIT_NS = 30_000_000_000L;
    /** A loaded chunk waiting longer than this for its light is built anyway (a hung barrier must not stall the capture). */
    public static final long MAX_CHUNK_WAIT_NS = 10_000_000_000L;
    private static final long PROGRESS_LOG_NS = 10_000_000_000L;

    // ThreadedLevelLightEngine.addTask(int, int, TaskType, Runnable) and TaskType.POST_UPDATE: 1.20.1 has no public
    // "wait for pending light tasks" call. Resolved once; a missing member fails loud at class load.
    private static final Method ADD_LIGHT_TASK;
    private static final Object POST_UPDATE;
    static {
        try {
            Class<?> taskType = Class.forName("net.minecraft.server.level.ThreadedLevelLightEngine$TaskType");
            ADD_LIGHT_TASK = ObfuscationReflectionHelper.findMethod(ThreadedLevelLightEngine.class, "m_9312_",
                    int.class, int.class, taskType, Runnable.class);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object post = Enum.valueOf((Class) taskType, "POST_UPDATE");
            POST_UPDATE = post;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("[FogChunkSnapshot] light engine task hook not found", e);
        }
    }
    /** Built packets waiting for the writer before the capture pauses (bounds the heap: ~50-500 KB each). */
    private static final int MAX_UNWRITTEN = 128;

    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("reignofnether_fog_snapshot", Comparator.comparingLong(ChunkPos::toLong));

    // which chunks have a file on disk; cheap membership test so get()/hasAny() never touch disk needlessly
    private static final Set<ChunkPos> index = ConcurrentHashMap.newKeySet();
    // built but not yet on disk (the writer thread moves each to the index)
    private static final Map<ChunkPos, ClientboundLevelChunkWithLightPacket> unwritten = new ConcurrentHashMap<>();
    // border chunks not built yet, the walk order, and those with a load ticket out (server thread only)
    private static final Set<ChunkPos> pending = new HashSet<>();
    private static final ArrayDeque<ChunkPos> order = new ArrayDeque<>();
    // border chunks taken off the walk and not built yet, with their light barrier once loaded (null before)
    private static final Map<ChunkPos, CompletableFuture<Void>> waiting = new LinkedHashMap<>();
    // of those, the ones with a load ticket out
    private static final Set<ChunkPos> ticketed = new HashSet<>();
    // chunks built on the spot for a dark player before their light settled; rebuilt when the barrier completes
    private static final Map<ChunkPos, CompletableFuture<Void>> relight = new LinkedHashMap<>();
    // when each waiting chunk was first seen loaded / each relight entry was added (server thread only)
    private static final Map<ChunkPos, Long> waitSince = new java.util.HashMap<>();
    private static final Map<ChunkPos, Long> relightSince = new java.util.HashMap<>();
    private static int forcedCount = 0;
    private static int totalCount = 0;
    private static long lastProgressNs = 0;
    /** Test hook: when set and it returns non-null for a chunk, that future is used as the chunk's light barrier. */
    public static volatile java.util.function.Function<ChunkPos, CompletableFuture<Void>> testBarrierHook = null;
    private static ServerLevel captureLevel = null;
    private static volatile boolean capturing = false;
    private static int capturedCount = 0;
    private static long captureStartNs = 0;
    private static long captureMaxStepNs = 0;
    // the longest single chunk build (pending block entities + packet) and how many pending block entities the
    // capture created: logged with the step, so a long step can be told apart from a slow build
    private static long captureMaxBuildNs = 0;
    private static int pendingCreatedCount = 0;
    // bumped by clear(): a write task of an older capture drops its result
    private static volatile int generation = 0;

    private static final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ReignOfNether-FogSnapshotWriter");
        t.setDaemon(true);
        return t;
    });

    public static boolean shouldRecapture = false;

    // bounded decode cache; accessOrder LRU, synchronised for the (rare) concurrent access
    private static final Map<ChunkPos, ClientboundLevelChunkWithLightPacket> lru =
            Collections.synchronizedMap(new LinkedHashMap<>(LRU_CAPACITY + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkPos, ClientboundLevelChunkWithLightPacket> eldest) {
                    return size() > LRU_CAPACITY;
                }
            });

    private static volatile Path dir = null;

    /** Start capturing every chunk inside the world border; {@link #tick} does the work over the next ticks. */
    public static void captureFogChunks(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        int minChunkX = (int) Math.floor(border.getMinX()) >> 4;
        int maxChunkX = (int) Math.floor(border.getMaxX() - 1) >> 4;
        int minChunkZ = (int) Math.floor(border.getMinZ()) >> 4;
        int maxChunkZ = (int) Math.floor(border.getMaxZ() - 1) >> 4;
        beginCapture(level, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    /** Start capturing chunks [minCx..maxCx] x [minCz..maxCz] (captureFogChunks's border box; tests pass their own). */
    public static void beginCapture(ServerLevel level, int minCx, int maxCx, int minCz, int maxCz) {
        shouldRecapture = false;
        clear();
        Path d = resolveDir(level);
        dir = d;
        int gen = generation;
        // wipe stale files left on disk by a prior server session before recapturing; on the writer, so it runs
        // before this capture's writes and off the server thread
        writer.execute(() -> {
            if (gen != generation) return;
            deleteDir(d);
            try {
                Files.createDirectories(d);
            } catch (IOException e) {
                ReignOfNether.LOGGER.error("[FogChunkSnapshot] failed to create snapshot dir {}", d, e);
            }
        });
        captureLevel = level;
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                pending.add(pos);
                order.add(pos);
            }
        capturing = !pending.isEmpty();
        capturedCount = 0;
        forcedCount = 0;
        totalCount = pending.size();
        captureStartNs = System.nanoTime();
        lastProgressNs = captureStartNs;
        captureMaxStepNs = 0;
        captureMaxBuildNs = 0;
        pendingCreatedCount = 0;
        ReignOfNether.LOGGER.info("[FogChunkSnapshot] capturing {} chunks inside world border over the next ticks",
                pending.size());
    }

    /** One budgeted capture step; PlayerServerEvents calls it every server tick. */
    public static void tick() {
        step(TICK_BUDGET_NS);
    }

    /** Server thread: build loaded, light-settled waiting chunks and ask for the rest, for at most about {@code budgetNs}. */
    public static void step(long budgetNs) {
        if (!capturing) return;
        long start = System.nanoTime();
        ServerChunkCache cache = captureLevel.getChunkSource();
        // chunks served early whose light has settled since: rebuild them with the settled light
        var rl = relight.entrySet().iterator();
        while (rl.hasNext() && System.nanoTime() - start < budgetNs && unwritten.size() < MAX_UNWRITTEN) {
            var e = rl.next();
            if (!e.getValue().isDone()) {
                Long since = relightSince.get(e.getKey());
                if (since != null && start - since > MAX_CHUNK_WAIT_NS) { // hung: keep the build it was served
                    rl.remove();
                    relightSince.remove(e.getKey());
                    forcedCount++;
                }
                continue;
            }
            LevelChunk chunk = cache.getChunkNow(e.getKey().x, e.getKey().z);
            rl.remove();
            relightSince.remove(e.getKey());
            if (chunk != null) captureNow(chunk);
        }
        // chunks taken off the walk: loaded -> light barrier -> built once the barrier has completed
        var it = waiting.entrySet().iterator();
        while (it.hasNext() && System.nanoTime() - start < budgetNs && unwritten.size() < MAX_UNWRITTEN) {
            var e = it.next();
            ChunkPos pos = e.getKey();
            if (pending.contains(pos)) {
                LevelChunk chunk = cache.getChunkNow(pos.x, pos.z);
                if (chunk == null) continue; // still loading
                Long since = waitSince.computeIfAbsent(pos, k -> start);
                if (start - since > MAX_CHUNK_WAIT_NS) { // light never settled: build with what it has
                    ReignOfNether.LOGGER.warn("[FogChunkSnapshot] chunk {} waited over {} s for its light; building it anyway",
                            pos, MAX_CHUNK_WAIT_NS / 1_000_000_000L);
                    forcedCount++;
                    captureNow(chunk);
                    it.remove();
                    waitSince.remove(pos);
                    if (ticketed.remove(pos))
                        cache.removeRegionTicket(TICKET, pos, 0, pos);
                    continue;
                }
                if (e.getValue() == null) { // just loaded: wait for its light
                    if (chunk.isLightCorrect()) e.setValue(lightBarrier(captureLevel, pos));
                    continue;
                }
                if (!e.getValue().isDone()) continue; // light still settling
                captureNow(chunk);
            }
            it.remove();
            waitSince.remove(pos);
            if (ticketed.remove(pos))
                cache.removeRegionTicket(TICKET, pos, 0, pos);
        }
        // walk the border box: loaded chunks wait for their light, the rest get a load ticket first
        while (!order.isEmpty() && System.nanoTime() - start < budgetNs && waiting.size() < MAX_WAITING) {
            ChunkPos pos = order.pollFirst();
            if (!pending.contains(pos)) continue; // captured on the spot meanwhile
            LevelChunk chunk = cache.getChunkNow(pos.x, pos.z);
            if (chunk != null) {
                waiting.put(pos, chunk.isLightCorrect() ? lightBarrier(captureLevel, pos) : null);
            } else {
                waiting.put(pos, null);
                ticketed.add(pos);
                cache.addRegionTicket(TICKET, pos, 0, pos); // level 33 = FULL, as getChunk(x, z, true) asks
            }
        }
        long took = System.nanoTime() - start;
        if (took > captureMaxStepNs) captureMaxStepNs = took;
        if (pending.isEmpty() && relight.isEmpty())
            finishCapture();
        else
            logProgress(cache);
    }

    private static void logProgress(ServerChunkCache cache) {
        long now = System.nanoTime();
        if (now - lastProgressNs < PROGRESS_LOG_NS) return;
        lastProgressNs = now;
        int unloaded = 0, lightIncorrect = 0, barrier = 0;
        for (var e : waiting.entrySet()) {
            if (cache.getChunkNow(e.getKey().x, e.getKey().z) == null) unloaded++;
            else if (e.getValue() == null) lightIncorrect++;
            else if (!e.getValue().isDone()) barrier++;
        }
        String msg = "[FogChunkSnapshot] progress " + capturedCount + "/" + totalCount + ", waiting " + waiting.size()
                + " (unloaded " + unloaded + ", light-incorrect " + lightIncorrect + ", barrier " + barrier
                + "), relight " + relight.size() + ", forced " + forcedCount;
        if (forcedCount > 0) ReignOfNether.LOGGER.warn(msg);
        else ReignOfNether.LOGGER.info(msg);
    }

    /** Chunks built after waiting past {@link #MAX_CHUNK_WAIT_NS} (or relight entries dropped) in this / the last capture. */
    public static int forcedCount() {
        return forcedCount;
    }

    /**
     * Completes once the light engine has run every light update queued, before this call, for chunk {@code pos} and
     * its loaded neighbours (light crosses at most one chunk border). Server thread.
     */
    public static CompletableFuture<Void> lightBarrier(ServerLevel level, ChunkPos pos) {
        var hook = testBarrierHook;
        if (hook != null) {
            CompletableFuture<Void> forced = hook.apply(pos);
            if (forced != null) return forced;
        }
        ServerChunkCache cache = level.getChunkSource();
        ThreadedLevelLightEngine engine = cache.getLightEngine();
        List<CompletableFuture<Void>> parts = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int x = pos.x + dx, z = pos.z + dz;
                if ((dx != 0 || dz != 0) && cache.getChunkNow(x, z) == null) continue; // nothing queued for it
                CompletableFuture<Void> f = new CompletableFuture<>();
                try {
                    ADD_LIGHT_TASK.invoke(engine, x, z, POST_UPDATE, (Runnable) () -> f.complete(null));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("[FogChunkSnapshot] queueing a light barrier failed", e);
                }
                parts.add(f);
            }
        return CompletableFuture.allOf(parts.toArray(new CompletableFuture[0]));
    }

    /** True while border chunks are still waiting to be captured. */
    public static boolean isCapturing() {
        return capturing;
    }

    /** Longest server-thread step of the current / last capture, in ns. */
    public static long lastCaptureMaxStepNs() {
        return captureMaxStepNs;
    }

    private static void finishCapture() {
        capturing = false;
        for (ChunkPos pos : ticketed)
            captureLevel.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        ticketed.clear();
        waiting.clear();
        waitSince.clear();
        relight.clear();
        relightSince.clear();
        order.clear();
        ReignOfNether.LOGGER.info("[FogChunkSnapshot] captured {} chunks inside world border to {} over {} ms "
                        + "(longest server-thread step {} ms, longest chunk build {} ms, {} pending block entities "
                        + "created; files are written in the background)",
                capturedCount, dir, (System.nanoTime() - captureStartNs) / 1_000_000,
                String.format(Locale.ROOT, "%.1f", captureMaxStepNs / 1e6),
                String.format(Locale.ROOT, "%.1f", captureMaxBuildNs / 1e6), pendingCreatedCount);
    }

    // build the packet now (that is the snapshot) and hand it to the writer
    private static ClientboundLevelChunkWithLightPacket captureNow(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        long buildStart = System.nanoTime();
        pendingCreatedCount += createPendingBlockEntities(chunk);
        ClientboundLevelChunkWithLightPacket packet =
                new ClientboundLevelChunkWithLightPacket(chunk, captureLevel.getLightEngine(), null, null);
        captureMaxBuildNs = Math.max(captureMaxBuildNs, System.nanoTime() - buildStart);
        if (pending.remove(pos)) capturedCount++;
        lru.remove(pos); // a rebuild (relight) replaces an earlier build
        unwritten.put(pos, packet);
        int gen = generation;
        Path d = dir;
        writer.execute(() -> {
            if (gen != generation) return;
            writeChunk(d, pos, packet, gen);
            unwritten.remove(pos, packet);
        });
        return packet;
    }

    /**
     * Creates the block entities {@code chunk} still holds only as pending tags, so its packet lists them. A chunk
     * loaded at level 33 (FULL, not ticking: the capture's own ticket) keeps those its features placed (a DUMMY tag
     * from WorldGenRegion) pending until the first getBlockEntity of their position: vanilla's background save
     * (ChunkMap.processUnloads -> saveChunkIfNeeded -> getBlockEntityNbtForSaving) or postProcessGeneration when the
     * chunk turns ticking, which runs before vanilla ever sends a chunk to a player. The packet lists only created
     * block entities, so without this the snapshot's depended on whether the save had reached the chunk yet (main run
     * 40456368179: "reference only" block entities). Creating them is what that save does anyway. Returns how many
     * positions were pending.
     */
    static int createPendingBlockEntities(LevelChunk chunk) {
        var created = chunk.getBlockEntities();
        int n = 0;
        for (var p : chunk.getBlockEntitiesPos())   // a fresh set: pending and created positions
            if (!created.containsKey(p)) {
                chunk.getBlockEntity(p);
                n++;
            }
        return n;
    }

    /** LevelChunkMixin, before a block of a loaded chunk changes: a chunk still waiting is captured first. */
    public static void beforeBlockChange(LevelChunk chunk) {
        if (!capturing || chunk.getLevel() != captureLevel || !captureLevel.getServer().isSameThread()) return;
        ChunkPos pos = chunk.getPos();
        // a chunk served early keeps its served build: a rebuild after this change would carry the change's light
        relight.remove(pos);
        relightSince.remove(pos);
        if (pending.contains(pos))
            captureNow(chunk); // final, see the class comment
    }

    // encode the packet and flush it to its chunk file; register in the index on success
    private static boolean writeChunk(Path d, ChunkPos pos, ClientboundLevelChunkWithLightPacket packet, int gen) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            Files.write(fileIn(d, pos), bytes);
            if (gen == generation)
                index.add(pos);
            return true;
        } catch (IOException e) {
            ReignOfNether.LOGGER.error("[FogChunkSnapshot] failed to write chunk {}", pos, e);
            return false;
        } finally {
            buf.release();
        }
    }

    public static ClientboundLevelChunkWithLightPacket get(ChunkPos pos) {
        Path d = dir;
        if (d == null) return null;
        ClientboundLevelChunkWithLightPacket built = unwritten.get(pos);
        if (built != null) return built;
        if (!index.contains(pos)) {
            // still waiting: capture it now from the loaded chunk (it is being sent, so it is loaded)
            if (capturing && captureLevel.getServer().isSameThread() && pending.contains(pos)) {
                LevelChunk chunk = captureLevel.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk != null) {
                    CompletableFuture<Void> lit = waiting.get(pos);
                    ClientboundLevelChunkWithLightPacket packet = captureNow(chunk);
                    // its light may still be settling: rebuild it once settled, unless a block of it changes first
                    if (lit == null || !lit.isDone()) {
                        relight.put(pos, lightBarrier(captureLevel, pos));
                        relightSince.put(pos, System.nanoTime());
                    }
                    return packet;
                }
            }
            return null; // no snapshot for this chunk; caller decides fallback
        }
        ClientboundLevelChunkWithLightPacket cached = lru.get(pos);
        if (cached != null) return cached;
        try {
            byte[] bytes = Files.readAllBytes(fileIn(d, pos));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(buf);
            lru.put(pos, packet);
            return packet;
        } catch (IOException e) {
            ReignOfNether.LOGGER.error("[FogChunkSnapshot] failed to read chunk {}", pos, e);
            index.remove(pos);
            return null;
        }
    }

    /**
     * Finish the capture now on the calling (server) thread, loading what is left synchronously, then wait for every
     * file to be written. Server stop (so a restart adopts a whole snapshot) and tests.
     */
    public static void finishNow() {
        if (capturing) {
            ServerChunkCache cache = captureLevel.getChunkSource();
            List<LevelChunk> chunks = new ArrayList<>();
            List<CompletableFuture<Void>> barriers = new ArrayList<>();
            for (ChunkPos pos : new ArrayList<>(pending)) {
                LevelChunk chunk = cache.getChunk(pos.x, pos.z, true);
                if (chunk != null) chunks.add(chunk);
                else pending.remove(pos);
            }
            for (LevelChunk chunk : chunks)
                barriers.add(lightBarrier(captureLevel, chunk.getPos()));
            barriers.addAll(relight.values());
            // let the light settle; pollTask runs main-thread work and schedules the light engine's batches
            CompletableFuture<Void> all = CompletableFuture.allOf(barriers.toArray(new CompletableFuture[0]));
            long waitStart = System.nanoTime();
            while (!all.isDone() && System.nanoTime() - waitStart < FINISH_LIGHT_WAIT_NS)
                if (!cache.pollTask()) LockSupport.parkNanos(200_000L);
            if (!all.isDone())
                ReignOfNether.LOGGER.error("[FogChunkSnapshot] light did not settle within {} s; building {} chunks "
                        + "with the light they have now", FINISH_LIGHT_WAIT_NS / 1_000_000_000L, chunks.size());
            for (LevelChunk chunk : chunks)
                if (pending.contains(chunk.getPos())) captureNow(chunk);
            for (ChunkPos pos : new ArrayList<>(relight.keySet())) {
                LevelChunk chunk = cache.getChunkNow(pos.x, pos.z);
                if (chunk != null) captureNow(chunk);
            }
            relight.clear();
            finishCapture();
        }
        awaitWrites();
    }

    /** Block until every queued write has run. */
    public static void awaitWrites() {
        try {
            writer.submit(() -> { }).get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[FogChunkSnapshot] waiting for snapshot writes failed", e);
        }
    }

    // Repopulate the in-memory index from files left on disk by a prior server session. Called on
    // ServerStartedEvent so a mid-match server restart keeps serving the existing snapshot (hasAny() becomes
    // true, so the first RTS join won't recapture) instead of showing the live world.
    public static void rebuildIndex(ServerLevel level) {
        Path d = resolveDir(level);
        if (!Files.isDirectory(d)) return;
        Set<ChunkPos> found = new HashSet<>();
        try (var stream = Files.list(d)) {
            stream.forEach(p -> {
                ChunkPos cp = parseChunkFile(p.getFileName().toString());
                if (cp != null) found.add(cp);
            });
        } catch (IOException e) {
            ReignOfNether.LOGGER.error("[FogChunkSnapshot] failed to rebuild index from {}", d, e);
            return;
        }
        if (found.isEmpty()) return;
        index.clear();
        lru.clear();
        index.addAll(found);
        dir = d;
        ReignOfNether.LOGGER.info("[FogChunkSnapshot] rebuilt index of {} chunks from {}", found.size(), d);
    }

    // "c.<x>.<z>.dat" -> ChunkPos, or null if the name isn't a snapshot file (x/z may be negative)
    private static ChunkPos parseChunkFile(String name) {
        String[] parts = name.split("\\.");
        if (parts.length != 4 || !parts[0].equals("c") || !parts[3].equals("dat")) return null;
        try {
            return new ChunkPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // a capture in progress counts: its chunks are served (captured on the spot) and a first join must not restart it
    public static boolean hasAny() {
        return capturing || !index.isEmpty() || !unwritten.isEmpty();
    }

    public static void clear() {
        if (!index.isEmpty() || capturing)
            ReignOfNether.LOGGER.info("[FogChunkSnapshot] clearing {} chunks", index.size() + unwritten.size());
        generation++;
        if (captureLevel != null)
            for (ChunkPos pos : ticketed)
                captureLevel.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        ticketed.clear();
        waiting.clear();
        waitSince.clear();
        relight.clear();
        relightSince.clear();
        pending.clear();
        order.clear();
        capturing = false;
        captureLevel = null;
        index.clear();
        unwritten.clear();
        lru.clear();
        Path d = dir;
        dir = null;
        if (d != null) writer.execute(() -> deleteDir(d)); // off the server thread, ordered before later writes
    }

    private static Path resolveDir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().resolve(DIR_NAME);
    }

    private static Path fileIn(Path d, ChunkPos pos) {
        return d.resolve("c." + pos.x + "." + pos.z + ".dat");
    }

    /** The file a chunk's snapshot is written to (null when there is no snapshot); tests read it back. */
    public static Path fileFor(ChunkPos pos) {
        Path d = dir;
        return d == null ? null : fileIn(d, pos);
    }

    private static void deleteDir(Path d) {
        if (d == null || !Files.isDirectory(d)) return;
        try (var stream = Files.list(d)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    ReignOfNether.LOGGER.warn("[FogChunkSnapshot] failed to delete {}", p, e);
                }
            });
            Files.deleteIfExists(d);
        } catch (IOException e) {
            ReignOfNether.LOGGER.warn("[FogChunkSnapshot] failed to clear snapshot dir {}", d, e);
        }
    }
}
