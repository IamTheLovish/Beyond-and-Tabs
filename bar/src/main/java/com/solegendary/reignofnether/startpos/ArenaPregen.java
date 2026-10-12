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
 * Generates every chunk inside the arena wall in the background once a battlefield forms, centre outwards, a couple
 * of chunks in flight at a time. lovish's Oct 12 Watch Battle clips panned the spectator camera across a Huge arena
 * and half the screen was dark, chunk-shaped holes for 10-15 s at a time: the terrain there had never been
 * generated, and world generation (not loading) is what the camera was waiting for. BAR's map is all there from the
 * first second; after this, terrain anywhere in the arena only has to be read back from disk.
 *
 * Asynchronous: each chunk gets a level-33 (FULL, not ticking) region ticket, the chunk system generates it on its
 * worker threads, and the ticket is dropped once the chunk is loaded (it then unloads and saves like any other).
 * Paused while the wall is still being raised (that work generates the ring itself), and only with a human on the
 * server: headless GameTest servers never spend time on it.
 */
public final class ArenaPregen {
    private ArenaPregen() {}

    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("reignofnether_arena_pregen", Comparator.comparingLong(ChunkPos::toLong));

    /** Chunks in flight at once: gentle on a laptop running the integrated server beside the client. */
    static final int IN_FLIGHT = 2;
    /** A chunk that hasn't arrived in this long is let go (its ticket dropped) so one stuck chunk can't stall the rest. */
    static final int GIVE_UP_TICKS = 20 * 30;

    private static ServerLevel level = null;
    private static long[] order = new long[0];
    private static int next = 0;
    private static final long[] inFlight = new long[IN_FLIGHT];
    private static final long[] inFlightSince = new long[IN_FLIGHT];
    private static final boolean[] inFlightUsed = new boolean[IN_FLIGHT];
    private static int generated = 0;

    /** Queue the arena disc (radius in blocks round cx, cz) for generation, nearest chunks first. */
    public static synchronized void start(ServerLevel lvl, double cx, double cz, double radius) {
        stop();
        if (lvl == null || radius <= 0)
            return;
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
        ReignOfNether.LOGGER.info("[ArenaPregen] queued {} arena chunks round [{}, {}] (radius {})", n, (int) cx, (int) cz, (int) radius);
    }

    /** Drop everything (tickets included). */
    public static synchronized void stop() {
        if (level != null) {
            ServerChunkCache cache = level.getChunkSource();
            for (int i = 0; i < IN_FLIGHT; i++)
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

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        stop();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || level == null || evt.getServer() == null)
            return;
        if (evt.getServer().getPlayerList().getPlayerCount() == 0 || BattlefieldWall.isBuilding())
            return;
        synchronized (ArenaPregen.class) {
            if (level == null)
                return;
            ServerChunkCache cache = level.getChunkSource();
            long now = level.getGameTime();
            boolean busy = false;
            for (int i = 0; i < IN_FLIGHT; i++) {
                if (inFlightUsed[i]) {
                    long c = inFlight[i];
                    boolean arrived = level.hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c));
                    if (arrived || now - inFlightSince[i] > GIVE_UP_TICKS) {
                        ChunkPos cp = new ChunkPos(c);
                        cache.removeRegionTicket(TICKET, cp, 0, cp);
                        inFlightUsed[i] = false;
                        if (arrived)
                            generated++;
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
                ReignOfNether.LOGGER.info("[ArenaPregen] arena ready ({} chunks generated or loaded)", generated);
                level = null;
                order = new long[0];
            }
        }
    }
}
