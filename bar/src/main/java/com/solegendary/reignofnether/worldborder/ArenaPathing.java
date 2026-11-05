package com.solegendary.reignofnether.worldborder;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.gamerules.GameruleClientboundPacket;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityGrid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The RTS pathfinder (async grid A* + walkability cache) in EVERY battlefield match, not only on maps with a world
 * border of 1280 or less (lovish, Oct 22 01:50: "it should also be on big maps").
 *
 * Why: matches never set a world border, so {@link WorldBorderServerEvents#isRtsOptimisedMap} was false in real play
 * and every unit path was vanilla A* on the server thread - FOLLOW_RANGE 64 = 1024 nodes and ~100 chunk lookups a
 * path. In lovish's 2-player match (Oct 22) that, with ~490 entities, put the server 25 s behind.
 *
 * What: when the arena wall is planned ({@link com.solegendary.reignofnether.startpos.BattlefieldWall}), the
 * rtsPathfinding rule goes on for the server and every client, and the arena's chunks are classified into the
 * walkability cache nearest-the-centre first, spread over ticks (a time budget per tick, so the match never
 * freezes on a big map). Until a chunk is warm the pathfinder warms it itself, as it always has.
 * Big arenas: radius up to ~720 = more chunks than the cache holds (PathfinderConfig.MAX_CACHED_CHUNKS); the
 * prewarm stops at the cap with margin and the rest is warmed on demand (LRU), so memory stays bounded.
 */
public final class ArenaPathing {

    private ArenaPathing() { }

    /** Milliseconds of prewarm per server tick (a 50 ms tick; the rest is the match's). */
    static final long BUDGET_MS = 6;
    /** Chunks beyond the wall still classified: units stand and path at the wall's foot. */
    static final int EDGE_CHUNKS = 1;

    private static ServerLevel level = null;
    private static final List<long[]> queue = new ArrayList<>();
    private static int next = 0, built = 0;
    private static long startedMs = 0;

    /** The arena's wall is being planned round (cx, cz) at this radius: switch RTS pathfinding on and warm it. */
    public static void onArena(ServerLevel lvl, double cx, double cz, double radius) {
        if (lvl == null || lvl.getServer() == null)
            return;
        // the headless GameTest server builds walls in many tests on ONE server: flipping a global rule there would
        // make every later test in the shard run on the other pathfinder. Tests that want it switch it on themselves
        // (OrdersObeyGameTests); real servers and singleplayer always get it.
        if (lvl.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)
            return;
        if (!UnitServerEvents.rtsPathfinding) {
            lvl.getServer().getGameRules().getRule(GameRuleRegistrar.RTS_PATHFINDING).set(true, lvl.getServer());
            UnitServerEvents.rtsPathfinding = true;
            GameruleClientboundPacket.setRtsPathfinding(true);
            ReignOfNether.LOGGER.info("[ArenaPathing] RTS pathfinding on for this match (arena radius {})", (int) radius);
        }
        // every chunk whose square meets the circle, nearest the centre first, up to the cache's size with margin
        int ccx = (int) Math.floor(cx / 16.0), ccz = (int) Math.floor(cz / 16.0);
        int r = (int) Math.ceil(radius / 16.0) + EDGE_CHUNKS;
        double rr = (radius / 16.0 + EDGE_CHUNKS + 0.75) * (radius / 16.0 + EDGE_CHUNKS + 0.75);
        List<long[]> cells = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                if (dx * dx + dz * dz <= rr)
                    cells.add(new long[]{ccx + dx, ccz + dz, (long) dx * dx + (long) dz * dz});
        cells.sort((a, b) -> Long.compare(a[2], b[2]));
        int cap = PathfinderConfig.MAX_CACHED_CHUNKS - 256;
        synchronized (ArenaPathing.class) {
            level = lvl;
            queue.clear();
            queue.addAll(cells.size() > cap ? cells.subList(0, cap) : cells);
            next = 0;
            built = 0;
            startedMs = System.currentTimeMillis();
        }
        ReignOfNether.LOGGER.info("[ArenaPathing] warming the navmesh: {} arena chunks{} over the next ticks",
            Math.min(cells.size(), cap), cells.size() > cap ? " (of " + cells.size() + "; the rest warm on demand)" : "");
    }

    /** The arena being warmed is left behind (maps.NewMatch moved the world on): stop warming it. */
    public static void abandon() {
        synchronized (ArenaPathing.class) {
            queue.clear();
            next = 0;
            built = 0;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        ServerLevel lvl;
        synchronized (ArenaPathing.class) {
            if (level == null || next >= queue.size())
                return;
            lvl = level;
        }
        WalkabilityGrid grid = WalkabilityGrid.get(lvl);
        long until = System.nanoTime() + BUDGET_MS * 1_000_000L;
        while (System.nanoTime() < until) {
            long[] c;
            synchronized (ArenaPathing.class) {
                if (level != lvl || next >= queue.size())
                    return;
                c = queue.get(next++);
            }
            try {
                // only chunks that already exist: the wall pre-generates the ring, and nothing here should
                // generate terrain on the server thread mid-match (a missing chunk is warmed on demand later)
                ChunkAccess chunk = lvl.getChunk((int) c[0], (int) c[1], ChunkStatus.FULL, false);
                if (chunk == null)
                    continue;
                int[] band = WorldBorderServerEvents.surfaceBand(chunk);
                grid.getOrBuild(lvl, (int) c[0], (int) c[1], band[0], band[1]);
                built++;
            } catch (Throwable t) {
                ReignOfNether.LOGGER.warn("[ArenaPathing] prewarm failed for chunk ({}, {}): {}", c[0], c[1], t.toString());
            }
        }
        synchronized (ArenaPathing.class) {
            if (next >= queue.size())
                ReignOfNether.LOGGER.info("[ArenaPathing] navmesh warm: {} of {} arena chunks in {} ms (the rest were not loaded)",
                    built, queue.size(), System.currentTimeMillis() - startedMs);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        synchronized (ArenaPathing.class) {
            level = null;
            queue.clear();
            next = built = 0;
        }
    }
}
