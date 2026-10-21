package com.solegendary.reignofnether.worldmemory;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The server side of the ground memory (claude/world-that-remembers.md, slice 1): BarFx's blast, heavy-impact,
 * death and collapse calls stamp scorch and craters into the chunks' fields ({@link GroundMemoryCapability}), and a
 * pass every {@link #SYNC_TICKS} sends each player the cells they may see - a delta for a chunk they hold, a snapshot
 * for one they first see (a late joiner, a spectator, a chunk coming into vision), nothing for columns under their fog
 * (SyncRules). Decay is lazy (GroundMemory.decayTo) and never synced: every client decays its copy the same way.
 * <p>
 * Budgets (spec section 5): a stamp is O(cells in the disc) with one heightmap read per cell; the pass walks the
 * marked chunks once and asks vanilla's chunk map who tracks each (the fog mixin already filters that list), with a
 * {@link #MAX_BYTES_PER_PASS} cap per player so a player landing on a scarred battlefield gets it over a few passes
 * rather than one burst. The GameTests measure both against the spec.
 * <p>
 * Purely visual: nothing here changes a block, so pathing never sees it (the GameTest holds that as an invariant).
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class GroundMemoryServer {
    private GroundMemoryServer() { }

    /** Ticks between sync passes: half a second keeps a scar within a blast's own smoke. */
    public static final int SYNC_TICKS = 10;
    /** Per player per pass; the spec's 30 KB burst. */
    public static final int MAX_BYTES_PER_PASS = 30 * 1024;
    /** A column whose top is further above the blast than this is a roof or a cliff top: untouched. */
    public static final double MAX_RISE = 3.0;
    /** ...and one further below is a pit the blast did not reach. */
    public static final double MAX_DROP = 6.0;
    /** A blast higher than this above the ground under it (anti-air hits, a shell bursting in flight) leaves no scar. */
    public static final double AIRBURST = 2.5;

    /** The decay the server runs and sends (DecayParams.DEFAULT, or the common config's [World memory] block). */
    private static DecayParams params = DecayParams.DEFAULT;
    private static int generationCounter = 0;

    /** Whether scars are stamped at all (the common config's master switch; the GameTests force it on). */
    public static boolean enabled = true;

    /** The per-level state: what is marked, what changed since the last pass, what each viewer holds. */
    static final class LevelState {
        final LongOpenHashSet marked = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<long[]> dirty = new Long2ObjectOpenHashMap<>();
        final Map<UUID, Long2ObjectOpenHashMap<SyncRules.State>> viewers = new HashMap<>();
        /** Chunk keys whose load event arrived (possibly off-thread); the tick checks them for saved marks. */
        final ConcurrentLinkedQueue<Long> loaded = new ConcurrentLinkedQueue<>();
        int tick = 0;
        // running totals for the log line and the stress test
        long bytesSent = 0, updatesSent = 0, passes = 0, stamps = 0;
    }

    // a ConcurrentHashMap: chunk load events may arrive off the server thread and only queue a key
    private static final Map<ResourceKey<Level>, LevelState> STATES = new java.util.concurrent.ConcurrentHashMap<>();

    static LevelState state(ServerLevel level) {
        return STATES.computeIfAbsent(level.dimension(), k -> new LevelState());
    }

    public static DecayParams params() {
        return params;
    }

    /** The decay parameters to run with (tests, and the config at server start). */
    public static void setParams(DecayParams p) {
        params = p == null ? DecayParams.DEFAULT : p;
    }

    // ------------------------------------------------------------------ what BarFx reports

    /**
     * An explosion of {@code radius} blocks at (x, y, z): scorch across ~0.5 + 0.9 r, and from radius 2 a crater whose
     * depth grows with the blast. Nothing for an airburst or an unloaded spot. kind is BarFx's K_* (a magic blast
     * leaves a lighter scorch than fire and powder).
     */
    public static void blast(ServerLevel level, double x, double y, double z, float radius, byte kind) {
        if (!enabled || level == null)
            return;
        float r = Math.max(0.5f, Math.min(radius, 16f));
        int peak = kind == BarFx.K_MAGIC ? 150 : 230;
        double scorchR = 0.5 + r * 0.9;
        stampScorch(level, x, y, z, scorchR, peak);
        if (r >= 2f)
            stampCrater(level, x, y, z, r * 0.65, craterDepthFor(r));
    }

    /** Crater depth (1-15) for a blast radius: a creeper's 3 digs 8, a game ender's 6 or more the full 15. */
    public static int craterDepthFor(float radius) {
        return Math.max(1, Math.min(GroundMemory.CRATER_MAX, Math.round(radius * 2.5f)));
    }

    /** A heavy weapon landed (artillery shell, a T3 slam): BarFx.heavyImpact. */
    public static void heavyImpact(ServerLevel level, double x, double y, double z, float radius) {
        if (!enabled || level == null)
            return;
        float r = Math.max(0.75f, Math.min(radius, 8f));
        stampScorch(level, x, y, z, r * 0.95, 200);
        if (r >= 2f)
            stampCrater(level, x, y, z, r * 0.6, craterDepthFor(r * 0.8f));
    }

    /** A shell or fireball hit the ground without a blast of its own (BarFx.impact at AT_GROUND). */
    public static void groundImpact(ServerLevel level, double x, double y, double z, byte kind, float size) {
        if (!enabled || level == null)
            return;
        switch (kind) {
            case BarFx.K_FIREBALL -> stampScorch(level, x, y, z, 0.9 * Math.max(0.5f, size), 110);
            case BarFx.K_BIG_FIREBALL, BarFx.K_TNT, BarFx.K_ROCKET -> stampScorch(level, x, y, z, 1.4 * Math.max(0.5f, size), 150);
            default -> { }
        }
    }

    /**
     * A unit died: a T3 leaves a burn where it stood, a construct (golem, blaze) a smaller one; everything else leaves
     * nothing in slice 1 (slice 6 adds the faction stains).
     */
    public static void death(ServerLevel level, double x, double y, double z, byte kind, int tier, float width) {
        if (!enabled || level == null)
            return;
        float scale = Math.max(0.4f, Math.min(4f, width));
        if (tier >= 3)
            stampScorch(level, x, y, z, 1.0 + scale * 0.6, 170);
        else if (kind == BarFx.D_CONSTRUCT)
            stampScorch(level, x, y, z, 0.5 + scale * 0.5, 130);
    }

    /** A building fell: its footprint is scorched (slice 3 puts the rubble and stumps on it). */
    public static void collapse(ServerLevel level, double x, double y, double z, float halfSize) {
        if (!enabled || level == null)
            return;
        stampScorch(level, x, y, z, Math.min(16, halfSize * 1.25 + 1), 210);
    }

    // ------------------------------------------------------------------ the stamps

    /**
     * Stamps a scorch disc ({@code peak} at the centre) over the loaded chunks it touches; returns how many cells
     * changed. The blast must be near the ground ({@link #AIRBURST}); columns whose top is far above or below it, or
     * has no decal surface (water, a post), are skipped.
     */
    public static int stampScorch(ServerLevel level, double cx, double y, double cz, double radius, int peak) {
        return stamp(level, cx, y, cz, radius, peak, false);
    }

    /** Stamps a crater dish of {@code depth} (1-15) the same way. */
    public static int stampCrater(ServerLevel level, double cx, double y, double cz, double radius, int depth) {
        return stamp(level, cx, y, cz, radius, depth, true);
    }

    private static int stamp(ServerLevel level, double cx, double y, double cz, double radius, int peak, boolean crater) {
        if (level == null || level.isClientSide() || radius <= 0 || peak <= 0)
            return 0;
        if (!Double.isFinite(cx) || !Double.isFinite(y) || !Double.isFinite(cz))
            return 0;
        int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
        if (!level.isLoaded(new BlockPos(bx, 0, bz)))
            return 0;
        int groundAtCentre = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (y - groundAtCentre > AIRBURST || groundAtCentre - y > MAX_DROP)
            return 0;
        long now = level.getGameTime();
        LevelState st = state(level);
        int minCx = (int) Math.floor(cx - radius) >> 4, maxCx = (int) Math.floor(cx + radius) >> 4;
        int minCz = (int) Math.floor(cz - radius) >> 4, maxCz = (int) Math.floor(cz + radius) >> 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changedCells = 0;
        for (int chunkX = minCx; chunkX <= maxCx; chunkX++)
            for (int chunkZ = minCz; chunkZ <= maxCz; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                GroundMemory mem = GroundMemoryCapability.of(chunk);
                if (mem == null)
                    continue;
                long[] allowed = allowedColumns(level, chunk, cx, y, cz, radius, pos);
                if (!GroundMemory.anyBit(allowed))
                    continue;
                mem.decayTo(now, params);
                long[] changed = crater ? mem.addCraterDisc(chunkX, chunkZ, cx, cz, radius, peak, allowed)
                    : mem.addScorchDisc(chunkX, chunkZ, cx, cz, radius, peak, allowed);
                if (!GroundMemory.anyBit(changed))
                    continue;
                changedCells += GroundMemory.bitCount(changed);
                chunk.setUnsaved(true);
                long key = ChunkPos.asLong(chunkX, chunkZ);
                st.marked.add(key);
                long[] dirty = st.dirty.get(key);
                if (dirty == null)
                    st.dirty.put(key, changed);
                else
                    for (int i = 0; i < 4; i++)
                        dirty[i] |= changed[i];
            }
        if (changedCells > 0)
            st.stamps++;
        return changedCells;
    }

    /** The columns of a chunk inside the disc that a decal may sit on, at a height the blast reached. */
    static long[] allowedColumns(ServerLevel level, LevelChunk chunk, double cx, double y, double cz, double radius,
                                 BlockPos.MutableBlockPos pos) {
        long[] allowed = new long[4];
        int ox = chunk.getPos().getMinBlockX(), oz = chunk.getPos().getMinBlockZ();
        int x0 = Math.max(ox, (int) Math.floor(cx - radius)), x1 = Math.min(ox + 15, (int) Math.floor(cx + radius));
        int z0 = Math.max(oz, (int) Math.floor(cz - radius)), z1 = Math.min(oz + 15, (int) Math.floor(cz + radius));
        double r2 = radius * radius;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                if (dx * dx + dz * dz > r2)
                    continue;
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);   // first air above
                if (top - y > MAX_RISE || y - top > MAX_DROP)
                    continue;
                double surface = GroundSurface.surfaceY(level, pos.set(x, top - 1, z));
                if (Double.isNaN(surface))
                    continue;
                GroundMemory.setBit(allowed, GroundMemory.index(x - ox, z - oz));
            }
        return allowed;
    }

    // ------------------------------------------------------------------ the sync pass

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || !(evt.level instanceof ServerLevel level))
            return;
        LevelState st = STATES.get(level.dimension());
        if (st == null)
            return;
        try {
            drainLoaded(level, st);
            if (++st.tick % SYNC_TICKS == 0 && !st.marked.isEmpty())
                syncPass(level, st);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("World memory: sync pass failed", e);
        }
    }

    private static void drainLoaded(ServerLevel level, LevelState st) {
        Long key;
        int n = 0;
        while (n++ < 256 && (key = st.loaded.poll()) != null) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            GroundMemory mem = GroundMemoryCapability.of(chunk);
            if (mem != null && !mem.isEmpty())
                st.marked.add((long) key);
        }
    }

    /** Runs one sync pass now (the GameTests call it instead of waiting for the tick). */
    public static void syncPass(ServerLevel level, LevelState st) {
        long now = level.getGameTime();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        List<ServerPlayer> online = level.players();
        if (online.isEmpty()) {
            // nobody to send to: the dirty masks are still consumed (a joiner gets snapshots anyway)
            for (LongIterator it = st.dirty.keySet().iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                GroundMemory mem = GroundMemoryCapability.of(chunk);
                if (mem != null) {
                    mem.previousGeneration = mem.generation;
                    mem.generation = ++generationCounter;
                }
            }
            st.dirty.clear();
            st.passes++;
            return;
        }
        int players = online.size();
        List<List<GroundMemoryClientboundPacket.Update>> outgoing = new ArrayList<>(players);
        int[] budget = new int[players];
        for (int p = 0; p < players; p++) {
            outgoing.add(null);
            budget[p] = MAX_BYTES_PER_PASS;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (LongIterator it = st.marked.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            GroundMemory mem = GroundMemoryCapability.of(chunk);
            if (mem == null) {   // unloaded under us: onChunkLoad puts it back when it returns
                it.remove();
                st.dirty.remove(key);
                continue;
            }
            mem.decayTo(now, params);
            long[] dirty = st.dirty.remove(key);
            if (dirty != null || mem.generation == 0) {
                mem.previousGeneration = mem.generation;
                mem.generation = ++generationCounter;
            }
            ChunkPos cpos = new ChunkPos(cx, cz);
            List<ServerPlayer> watching = chunkMap.getPlayers(cpos, false);   // tracked, and not fog-dark (ChunkMapMixin)
            for (int p = 0; p < players; p++) {
                ServerPlayer sp = online.get(p);
                Long2ObjectOpenHashMap<SyncRules.State> states = st.viewers.get(sp.getUUID());
                if (!watching.contains(sp)) {
                    if (states != null)
                        states.remove(key);   // out of sight: a snapshot of what it sees when it comes back
                    continue;
                }
                if (states == null) {
                    states = new Long2ObjectOpenHashMap<>();
                    st.viewers.put(sp.getUUID(), states);
                }
                long[] visible = visibleColumns(sp, cpos);
                SyncRules.State have = states.get(key);
                long[] send = SyncRules.plan(have, mem.generation, mem.previousGeneration, dirty, visible);
                if (send != null) {
                    GroundMemoryClientboundPacket.Update u = update(mem, cx, cz, send);
                    if (budget[p] < u.wireBytes()) {
                        states.remove(key);   // over budget this pass: forget what it holds, a snapshot comes next pass
                        continue;
                    }
                    budget[p] -= u.wireBytes();
                    List<GroundMemoryClientboundPacket.Update> list = outgoing.get(p);
                    if (list == null) {
                        list = new ArrayList<>();
                        outgoing.set(p, list);
                    }
                    list.add(u);
                }
                SyncRules.State next = SyncRules.after(have, mem.generation, mem.previousGeneration, dirty, send);
                if (next == null)
                    states.remove(key);
                else if (next != have)
                    states.put(key, next);
            }
        }
        for (int p = 0; p < players; p++) {
            List<GroundMemoryClientboundPacket.Update> list = outgoing.get(p);
            if (list == null || list.isEmpty())
                continue;
            ServerPlayer sp = online.get(p);
            for (int from = 0; from < list.size(); from += GroundMemoryClientboundPacket.MAX_UPDATES) {
                List<GroundMemoryClientboundPacket.Update> part = list.subList(from,
                    Math.min(list.size(), from + GroundMemoryClientboundPacket.MAX_UPDATES));
                try {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                        new GroundMemoryClientboundPacket(params, new ArrayList<>(part)));
                } catch (Exception e) {
                    ReignOfNether.LOGGER.warn("World memory: could not send {} updates to {}", part.size(), sp.getGameProfile().getName(), e);
                }
                for (GroundMemoryClientboundPacket.Update u : part) {
                    st.bytesSent += u.wireBytes();
                    st.updatesSent++;
                }
            }
        }
        st.passes++;
    }

    /** One update for a viewer: the whole field when every cell is due, else the listed cells. */
    public static GroundMemoryClientboundPacket.Update update(GroundMemory mem, int cx, int cz, long[] send) {
        if (GroundMemory.isFull(send))
            return new GroundMemoryClientboundPacket.Update(cx, cz, mem.lastDecayTick(), mem.copyCells(), null, null);
        int n = GroundMemory.bitCount(send);
        int[] cells = new int[n], values = new int[n];
        int k = 0;
        for (int cell = 0; cell < GroundMemory.CELLS && k < n; cell++)
            if (GroundMemory.bit(send, cell)) {
                cells[k] = cell;
                values[k] = mem.packed(cell);
                k++;
            }
        return new GroundMemoryClientboundPacket.Update(cx, cz, mem.lastDecayTick(), null, cells, values);
    }

    /** The columns of a chunk this player may see: all of them without fog or in a live chunk, the fog's mask at an edge. */
    static long[] visibleColumns(ServerPlayer sp, ChunkPos cpos) {
        if (!FogOfWarServerEvents.isFogActiveFor(sp) || FogOfWarServerEvents.isChunkLiveFor(sp, cpos))
            return GroundMemory.fullMask();
        long[] mask = new long[4];
        int ox = cpos.getMinBlockX(), oz = cpos.getMinBlockZ();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++)
                if (FogOfWarServerEvents.isBlockVisibleFor(sp, ox + lx, oz + lz))
                    GroundMemory.setBit(mask, GroundMemory.index(lx, lz));
        return mask;
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load evt) {
        // may arrive off the main thread (ArenaStructureClearing's note): only queue the key
        if (evt.getLevel() instanceof ServerLevel level && evt.getChunk() instanceof LevelChunk chunk)
            state(level).loaded.add(chunk.getPos().toLong());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload evt) {
        if (evt.getLevel() instanceof ServerLevel level) {
            LevelState st = STATES.get(level.dimension());
            if (st == null)
                return;
            long key = evt.getChunk().getPos().toLong();
            st.marked.remove(key);
            st.dirty.remove(key);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload evt) {
        if (evt.getLevel() instanceof ServerLevel level)
            STATES.remove(level.dimension());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent evt) {
        if (evt.getEntity() == null)
            return;
        UUID id = evt.getEntity().getUUID();
        for (LevelState st : STATES.values())
            st.viewers.remove(id);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        STATES.clear();
        generationCounter = 0;
        setParams(WorldMemoryConfig.decayParams());
        enabled = WorldMemoryConfig.scarsEnabled();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        STATES.clear();
    }

    // ------------------------------------------------------------------ for the tests and the log

    /** Chunks of a level holding marks right now. */
    public static int markedChunks(ServerLevel level) {
        LevelState st = STATES.get(level.dimension());
        return st == null ? 0 : st.marked.size();
    }

    /** Dirty cells of a chunk waiting for the next pass (null = none). */
    public static long[] dirtyMask(ServerLevel level, int cx, int cz) {
        LevelState st = STATES.get(level.dimension());
        return st == null ? null : st.dirty.get(ChunkPos.asLong(cx, cz));
    }

    /** What a viewer holds of a chunk (null = nothing). */
    public static SyncRules.State viewerState(ServerLevel level, UUID player, int cx, int cz) {
        LevelState st = STATES.get(level.dimension());
        if (st == null)
            return null;
        Long2ObjectOpenHashMap<SyncRules.State> states = st.viewers.get(player);
        return states == null ? null : states.get(ChunkPos.asLong(cx, cz));
    }

    /** Runs the level's sync pass now. */
    public static void syncNow(ServerLevel level) {
        syncPass(level, state(level));
    }

    /** Bytes and updates sent so far on this level (the audience measurement). */
    public static long[] sentTotals(ServerLevel level) {
        LevelState st = STATES.get(level.dimension());
        return st == null ? new long[] { 0, 0, 0 } : new long[] { st.bytesSent, st.updatesSent, st.passes };
    }

    /** Forgets everything about a level (the GameTests start clean). */
    public static void reset(ServerLevel level) {
        STATES.remove(level.dimension());
    }
}
