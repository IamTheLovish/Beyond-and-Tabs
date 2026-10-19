package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Generates every chunk inside the arena wall in the background, centre outwards, a few chunks in flight at a
 * time. lovish's Oct 12 Watch Battle clips panned the spectator camera across a Huge arena and half the screen was
 * dark, chunk-shaped holes for 10-15 s at a time: the terrain there had never been generated, and world generation
 * (not loading) is what the camera was waiting for. BAR's map is all there from the first second; after this,
 * terrain anywhere in the arena only has to be read back from disk.
 *
 * Asynchronous: each chunk gets a level-33 (FULL, not ticking) region ticket, the chunk system generates it on its
 * worker threads, and the ticket is dropped once the chunk is loaded (it then unloads and saves like any other).
 * Two ways in: a classic formation (BattlefieldWall) runs it during play with {@link #IN_FLIGHT} chunks and pauses
 * it while the wall is still rising (that work generates the ring itself); a prepared match (match.MatchPrep)
 * runs it as its own stage before the start phase with up to {@link #MAX_IN_FLIGHT}, nothing else running. Only
 * with a human on the server either way: headless GameTest servers never spend time on it.
 */
public final class ArenaPregen {
    private ArenaPregen() {}

    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("reignofnether_arena_pregen", Comparator.comparingLong(ChunkPos::toLong));

    /** Chunks in flight at once during play: gentle on a laptop running the integrated server beside the client. */
    static final int IN_FLIGHT = 2;
    /** The most a caller may ask for (the preparation asks for 8). */
    public static final int MAX_IN_FLIGHT = 8;
    /** A chunk that hasn't arrived in this long is let go (its ticket dropped) so one stuck chunk can't stall the rest. */
    static final int GIVE_UP_TICKS = 20 * 30;

    private static ServerLevel level = null;
    private static long[] order = new long[0];
    private static int next = 0;
    private static long[] inFlight = new long[IN_FLIGHT];
    private static long[] inFlightSince = new long[IN_FLIGHT];
    private static boolean[] inFlightUsed = new boolean[IN_FLIGHT];
    private static int generated = 0;
    private static int gaveUp = 0;
    private static boolean yieldToWall = true;

    /** Queue the arena disc (radius in blocks round cx, cz) for generation, nearest chunks first; the in-play way. */
    public static synchronized void start(ServerLevel lvl, double cx, double cz, double radius) {
        start(lvl, cx, cz, radius, IN_FLIGHT, true);
    }

    /**
     * As above with {@code inFlight} chunks generating at once (1..{@link #MAX_IN_FLIGHT}) and, when
     * {@code waitForWall} is false, without pausing for a wall still rising (the preparation raised it already).
     */
    public static synchronized void start(ServerLevel lvl, double cx, double cz, double radius, int inFlight,
                                          boolean waitForWall) {
        stop();
        if (lvl == null || radius <= 0)
            return;
        int slots = Math.max(1, Math.min(MAX_IN_FLIGHT, inFlight));
        ArenaPregen.inFlight = new long[slots];
        inFlightSince = new long[slots];
        inFlightUsed = new boolean[slots];
        yieldToWall = waitForWall;
        int ccx = (int) Math.floor(cx) >> 4, ccz = (int) Math.floor(cz) >> 4;
        int r = (int) Math.ceil(radius / 16.0) + 1;
        long[] buf = new long[(2 * r + 1) * (2 * r + 1)];
        int n = 0;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                if (dx * dx + dz * dz <= r * r)
                    buf[n++] = ChunkPos.asLong(ccx + dx, ccz + dz);
        order = Arrays.copyOf(buf, n);
        // centre outwards: the middle of the arena (where the fighting and the spectator camera start) first
        final int fx = ccx, fz = ccz;
        Long[] boxed = new Long[n];
        for (int i = 0; i < n; i++)
            boxed[i] = order[i];
        Arrays.sort(boxed, Comparator.comparingLong(c -> {
            long ddx = ChunkPos.getX(c) - fx, ddz = ChunkPos.getZ(c) - fz;
            return ddx * ddx + ddz * ddz;
        }));
        for (int i = 0; i < n; i++)
            order[i] = boxed[i];
        level = lvl;
        next = 0;
        generated = 0;
        gaveUp = 0;
        ReignOfNether.LOGGER.info("[ArenaPregen] queued {} arena chunks round [{}, {}] (radius {}, {} in flight)", n,
            (int) cx, (int) cz, (int) radius, slots);
    }

    /** Drop everything (tickets included). */
    public static synchronized void stop() {
        if (level != null) {
            ServerChunkCache cache = level.getChunkSource();
            for (int i = 0; i < inFlightUsed.length; i++)
                if (inFlightUsed[i]) {
                    ChunkPos cp = new ChunkPos(inFlight[i]);
                    cache.removeRegionTicket(TICKET, cp, 0, cp);
                }
        }
        Arrays.fill(inFlightUsed, false);
        level = null;
        order = new long[0];
        next = 0;
    }

    public static boolean isRunning() {
        return level != null;
    }

    /** Chunks of the current run (0 when idle). */
    public static synchronized int total() {
        return order.length;
    }

    /** Chunks of the current run that are loaded, skipped as loaded already, or given up on. */
    public static synchronized int done() {
        int pending = 0;
        for (boolean used : inFlightUsed)
            if (used)
                pending++;
        return Math.max(0, next - pending);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        stop();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || level == null || evt.getServer() == null)
            return;
        if (evt.getServer().getPlayerList().getPlayerCount() == 0 || (yieldToWall && BattlefieldWall.isBuilding()))
            return;
        synchronized (ArenaPregen.class) {
            if (level == null)
                return;
            ServerChunkCache cache = level.getChunkSource();
            long now = level.getGameTime();
            boolean busy = false;
            for (int i = 0; i < inFlightUsed.length; i++) {
                if (inFlightUsed[i]) {
                    long c = inFlight[i];
                    boolean arrived = level.hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c));
                    if (arrived || now - inFlightSince[i] > GIVE_UP_TICKS) {
                        ChunkPos cp = new ChunkPos(c);
                        cache.removeRegionTicket(TICKET, cp, 0, cp);
                        inFlightUsed[i] = false;
                        if (arrived) {
                            generated++;
                        } else {
                            // never a silent hole: the position is in the log for the next look at the clips
                            gaveUp++;
                            ReignOfNether.LOGGER.warn("[ArenaPregen] gave up on chunk [{}, {}] after {} s", cp.x, cp.z,
                                GIVE_UP_TICKS / 20);
                        }
                    }
                }
                // refill this slot with the next chunk nobody has loaded yet
                while (!inFlightUsed[i] && next < order.length) {
                    long c = order[next++];
                    if (level.hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c)))
                        continue;
                    ChunkPos cp = new ChunkPos(c);
                    cache.addRegionTicket(TICKET, cp, 0, cp);
                    inFlight[i] = c;
                    inFlightSince[i] = now;
                    inFlightUsed[i] = true;
                }
                busy |= inFlightUsed[i];
            }
            if (!busy && next >= order.length) {
                ReignOfNether.LOGGER.info("[ArenaPregen] arena ready ({} chunks generated or loaded, {} given up)",
                    generated, gaveUp);
                level = null;
                order = new long[0];
            }
        }
    }
}
