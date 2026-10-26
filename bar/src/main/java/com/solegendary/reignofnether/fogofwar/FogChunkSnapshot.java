package com.solegendary.reignofnether.fogofwar;

import com.solegendary.reignofnether.ReignOfNether;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
//    snapshot); a chunk that is not loaded gets a level-33 region ticket (FULL, not ticking - what
//    getChunk(x, z, true) asked for) and is built once the chunk system has loaded it on its own workers;
//  - one background thread: encode each built packet and write its file (bytes only, no world access); a chunk
//    joins the on-disk index once its file is written, and until then get() serves the in-memory packet;
//  - a chunk still waiting is captured on the spot, before it can differ from the pre-game state, when one of its
//    blocks is about to change (LevelChunkMixin -> {@link #beforeBlockChange}) or a dark player is about to be sent
//    it ({@link #get}), so the snapshot holds what the old one-tick capture held and get() never returns null for a
//    loaded border chunk while the capture runs.
public class FogChunkSnapshot {

    private static final String DIR_NAME = "reignofnether/fogsnapshot";
    private static final int LRU_CAPACITY = 64; // hot chunks served to multiple players / on relog bursts

    /** Server-thread time the capture may use per tick. */
    public static final long TICK_BUDGET_NS = 20_000_000L;
    /** Chunks with a load ticket out at once. */
    private static final int MAX_LOADING = 64;
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
    private static final Set<ChunkPos> loading = new LinkedHashSet<>();
    private static ServerLevel captureLevel = null;
    private static volatile boolean capturing = false;
    private static int capturedCount = 0;
    private static long captureStartNs = 0;
    private static long captureMaxStepNs = 0;
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
        captureStartNs = System.nanoTime();
        captureMaxStepNs = 0;
        ReignOfNether.LOGGER.info("[FogChunkSnapshot] capturing {} chunks inside world border over the next ticks",
                pending.size());
    }

    /** One budgeted capture step; PlayerServerEvents calls it every server tick. */
    public static void tick() {
        step(TICK_BUDGET_NS);
    }

    /** Server thread: build packets of loaded waiting chunks and ask for the rest, for at most about {@code budgetNs}. */
    public static void step(long budgetNs) {
        if (!capturing) return;
        long start = System.nanoTime();
        ServerChunkCache cache = captureLevel.getChunkSource();
        // chunks the chunk system has finished loading for us
        var it = loading.iterator();
        while (it.hasNext() && System.nanoTime() - start < budgetNs && unwritten.size() < MAX_UNWRITTEN) {
            ChunkPos pos = it.next();
            if (pending.contains(pos)) {
                LevelChunk chunk = cache.getChunkNow(pos.x, pos.z);
                if (chunk == null) continue; // still loading
                captureNow(chunk);
            }
            it.remove();
            cache.removeRegionTicket(TICKET, pos, 0, pos);
        }
        // walk the border box: loaded chunks are built now, the rest get a load ticket
        while (!order.isEmpty() && System.nanoTime() - start < budgetNs && unwritten.size() < MAX_UNWRITTEN) {
            ChunkPos pos = order.peekFirst();
            if (!pending.contains(pos)) { // captured on the spot meanwhile
                order.pollFirst();
                continue;
            }
            LevelChunk chunk = cache.getChunkNow(pos.x, pos.z);
            if (chunk != null) {
                order.pollFirst();
                captureNow(chunk);
            } else if (loading.size() < MAX_LOADING) {
                order.pollFirst();
                loading.add(pos);
                cache.addRegionTicket(TICKET, pos, 0, pos); // level 33 = FULL, as getChunk(x, z, true) asks
            } else {
                break; // wait for the loads in flight
            }
        }
        long took = System.nanoTime() - start;
        if (took > captureMaxStepNs) captureMaxStepNs = took;
        if (pending.isEmpty())
            finishCapture();
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
        for (ChunkPos pos : loading)
            captureLevel.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        loading.clear();
        order.clear();
        ReignOfNether.LOGGER.info("[FogChunkSnapshot] captured {} chunks inside world border to {} over {} ms "
                        + "(longest server-thread step {} ms; files are written in the background)",
                capturedCount, dir, (System.nanoTime() - captureStartNs) / 1_000_000,
                String.format(Locale.ROOT, "%.1f", captureMaxStepNs / 1e6));
    }

    // build the packet now (that is the snapshot) and hand it to the writer
    private static ClientboundLevelChunkWithLightPacket captureNow(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        ClientboundLevelChunkWithLightPacket packet =
                new ClientboundLevelChunkWithLightPacket(chunk, captureLevel.getLightEngine(), null, null);
        pending.remove(pos);
        capturedCount++;
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

    /** LevelChunkMixin, before a block of a loaded chunk changes: a chunk still waiting is captured first. */
    public static void beforeBlockChange(LevelChunk chunk) {
        if (!capturing || chunk.getLevel() != captureLevel || !captureLevel.getServer().isSameThread()) return;
        if (pending.contains(chunk.getPos()))
            captureNow(chunk);
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
                if (chunk != null) return captureNow(chunk);
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
            for (ChunkPos pos : new ArrayList<>(pending)) {
                LevelChunk chunk = cache.getChunk(pos.x, pos.z, true);
                if (chunk != null) captureNow(chunk);
                else pending.remove(pos);
            }
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
            for (ChunkPos pos : loading)
                captureLevel.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        loading.clear();
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
