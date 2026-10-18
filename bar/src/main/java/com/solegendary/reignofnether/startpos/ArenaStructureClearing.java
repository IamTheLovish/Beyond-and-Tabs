package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Beyond and Tabs: no vanilla villages in the arena. lovish's clip 15-56 had a village (with a Supplementaries
 * palisade) cutting straight across the battlefield. Skirmish worlds are created with structure generation OFF
 * ({@link #skirmishWorldOptions}); this is the safety net for worlds that have structures anyway (the long way
 * round, multiplayer, old saves): when the battlefield forms, every surface structure (villages, outposts, ruined
 * portals...) found through the chunks' structure starts is reverted to open ground inside the arena, and stray
 * villagers / golems / pillagers that are not units are removed. Chunks inside the wall that load later are
 * handled as they load (a few per tick). Player and bot buildings, metal patches and the wall are never touched.
 * Worlds without structure generation pay nothing.
 */
public final class ArenaStructureClearing {
    private ArenaStructureClearing() { }

    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Jigsaw villages reach ~80 blocks from their start chunk; scan this far past the core for starts. */
    static final int START_REACH = 96;
    /** Chunks handled per tick after formation (loaded late, inside the wall). */
    static final int CHUNKS_PER_TICK = 4;

    /** The world settings a fresh skirmish world is created with: no structures, no bonus chest. */
    public static WorldOptions skirmishWorldOptions(long seed) {
        return new WorldOptions(seed, false, false);
    }

    // the active arena (null level = none): only set when the world can actually have structures
    static ServerLevel arenaLevel = null;
    static float arenaCx, arenaCz, arenaR;
    static final LongSet doneChunks = new LongOpenHashSet();
    static final ConcurrentLinkedQueue<Long> loadedQueue = new ConcurrentLinkedQueue<>();

    static boolean structuresPossible(ServerLevel level) {
        try {
            return level.getServer().getWorldData().worldGenOptions().generateStructures();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Called as the battlefield forms, before patches are stamped: clears surface structures round the bases and
     * lanes (the core, which every patch and capture point lies in) and arms the late-chunk pass for the arena.
     */
    public static void clearForBattlefield(ServerLevel level, List<BlockPos> positions, float cx, float cz) {
        if (level == null || positions.isEmpty() || !structuresPossible(level))
            return;
        float core = 0;
        for (BlockPos p : positions)
            core = Math.max(core, (float) Math.hypot(p.getX() + 0.5f - cx, p.getZ() + 0.5f - cz));
        core += StartAreaClearing.BASE_RADIUS + 16;
        arenaLevel = level;
        arenaCx = cx;
        arenaCz = cz;
        arenaR = core;   // the wall widens this once it rolls its radius
        doneChunks.clear();
        StartAreaClearing.refreshProtected();
        // only structure STARTS are needed to find them: a cheap status for chunks nobody has generated yet
        Map<StructureStart, Boolean> starts = new IdentityHashMap<>();
        int scan = (int) Math.ceil(core) + START_REACH;
        int c0x = ((int) Math.floor(cx - scan)) >> 4, c1x = ((int) Math.ceil(cx + scan)) >> 4;
        int c0z = ((int) Math.floor(cz - scan)) >> 4, c1z = ((int) Math.ceil(cz + scan)) >> 4;
        for (int x = c0x; x <= c1x; x++)
            for (int z = c0z; z <= c1z; z++) {
                float dx = (x << 4) + 8 - cx, dz = (z << 4) + 8 - cz;
                if (dx * dx + dz * dz > (scan + 12f) * (scan + 12f))
                    continue;
                ChunkAccess chunk = level.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS);
                for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet())
                    if (relevant(e.getKey(), e.getValue()))
                        starts.put(e.getValue(), Boolean.TRUE);
            }
        int cleared = 0, pieces = 0;
        for (StructureStart s : starts.keySet())
            for (StructurePiece piece : s.getPieces()) {
                BoundingBox b = piece.getBoundingBox();
                if (!boxTouchesDisc(b, cx, cz, core))
                    continue;
                pieces++;
                cleared += clearBox(level, b, cx, cz, core);
                sweepStrays(level, new AABB(b.minX(), b.minY() - 2, b.minZ(), b.maxX() + 1, b.maxY() + 4, b.maxZ() + 1), cx, cz, core);
            }
        if (!starts.isEmpty())
            ReignOfNether.LOGGER.info("[ArenaStructureClearing] reverted {} structures ({} pieces, {} blocks) inside the arena core",
                    starts.size(), pieces, cleared);
    }

    /** The wall's rolled radius: chunks loading inside it later get the same treatment. */
    public static void setArenaRadius(ServerLevel level, float cx, float cz, float radius) {
        if (level == null || level != arenaLevel)
            return;
        arenaCx = cx;
        arenaCz = cz;
        arenaR = Math.max(0, radius - 8);   // never reach the wall itself
    }

    static boolean relevant(Structure structure, StructureStart start) {
        return start != null && start.isValid() && structure.step() == GenerationStep.Decoration.SURFACE_STRUCTURES;
    }

    static boolean boxTouchesDisc(BoundingBox b, float cx, float cz, float r) {
        float nx = Math.max(b.minX(), Math.min(cx, b.maxX() + 1)), nz = Math.max(b.minZ(), Math.min(cz, b.maxZ() + 1));
        return (nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) <= r * r;
    }

    static boolean insideArena(Entity e) {
        float dx = (float) e.getX() - arenaCx, dz = (float) e.getZ() - arenaCz;
        return dx * dx + dz * dz <= arenaR * arenaR;
    }

    /** A vanilla village / outpost mob that is not one of ours. */
    public static boolean strayVillageMob(Entity e) {
        return !(e instanceof Unit) && (e instanceof AbstractVillager || e instanceof IronGolem || e instanceof Pillager);
    }

    /** Removes stray village mobs inside aabb and the disc. Returns how many. */
    public static int sweepStrays(ServerLevel level, AABB aabb, float cx, float cz, float r) {
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, aabb, ArenaStructureClearing::strayVillageMob)) {
            float dx = (float) m.getX() - cx, dz = (float) m.getZ() - cz;
            if (dx * dx + dz * dz > r * r)
                continue;
            m.discard();
            n++;
        }
        return n;
    }

    /** Ground the arena keeps: soil, stone, sand. Anything else in a structure's box was built by it. */
    static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK);
    }

    static boolean guarded(List<BoundingBox> guards, int x, int z) {
        for (int i = 0; i < guards.size(); i++) {
            BoundingBox g = guards.get(i);
            if (x >= g.minX() && x <= g.maxX() && z >= g.minZ() && z <= g.maxZ())
                return true;
        }
        return false;
    }

    static boolean nearPatch(List<BlockPos> patches, int x, int z) {
        int r = MetalPatches.RESERVED_HALF + 1;
        for (int i = 0; i < patches.size(); i++) {
            BlockPos p = patches.get(i);
            if (Math.abs(p.getX() - x) <= r && Math.abs(p.getZ() - z) <= r)
                return true;
        }
        return false;
    }

    /**
     * Reverts the part of box inside the disc to open ground: each column from the top of the box down to the
     * first natural block is emptied (paths and farmland become grass), then shallow pits where foundations sat
     * are filled back up to the box's typical ground level. Returns the number of blocks changed.
     */
    public static int clearBox(ServerLevel level, BoundingBox box, float cx, float cz, float r) {
        List<BlockPos> patches;
        List<BlockPos> live = MetalPatches.getPatches(level);
        synchronized (live) {
            patches = live.isEmpty() ? Collections.emptyList() : new ArrayList<>(live);
        }
        // the standing buildings that overlap this box, found once rather than per block
        List<BoundingBox> guards = new ArrayList<>();
        for (BoundingBox g : StartAreaClearing.protectedBoxes())
            if (g.intersects(box))
                guards.add(g);
        int w = box.getXSpan(), d = box.getZSpan();
        int[] ground = new int[w * d];        // y of the natural top after clearing, MIN_VALUE = untouched / unknown
        int[] lowestSolid = new int[w * d];   // lowest solid structure block removed, MAX_VALUE = none
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int i = 0; i < w * d; i++) {
            ground[i] = Integer.MIN_VALUE;
            lowestSolid[i] = Integer.MAX_VALUE;
            int x = box.minX() + i % w, z = box.minZ() + i / w;
            float dx = x + .5f - cx, dz = z + .5f - cz;
            if (dx * dx + dz * dz > r * r || nearPatch(patches, x, z))
                continue;
            int top = Math.min(box.maxY(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
            if (guarded(guards, x, z))
                continue;   // a player or bot building stands here: leave the whole column
            for (int y = top; y >= box.minY(); y--) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                if (s.isAir())
                    continue;
                if (s.getBlock() instanceof LiquidBlock)
                    break;   // ponds and farm water stay
                if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND)) {
                    level.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(), FLAGS);
                    changed++;
                    ground[i] = y;
                    break;
                }
                if (natural(s)) {
                    ground[i] = y;
                    if (s.is(Blocks.DIRT) && level.getBlockState(p.set(x, y + 1, z)).isAir()) {
                        level.setBlock(p.set(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState(), FLAGS);
                        changed++;
                    }
                    break;
                }
                if (s.isSolidRender(level, p))
                    lowestSolid[i] = y;
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof Clearable c)
                    c.clearContent();   // chests, barrels, lecterns: no loot spilling across the field
                level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                changed++;
            }
        }
        // typical ground of the cleared columns; pits (house floors sunk into the soil) sit a block or two below
        int[] tops = new int[w * d];
        int n = 0;
        for (int g : ground)
            if (g != Integer.MIN_VALUE)
                tops[n++] = g;
        if (n == 0)
            return changed;
        java.util.Arrays.sort(tops, 0, n);
        int median = tops[n / 2];
        for (int i = 0; i < w * d; i++) {
            int g = ground[i];
            if (g == Integer.MIN_VALUE || g >= median || median - g > 2 || lowestSolid[i] > median || lowestSolid[i] <= g)
                continue;
            int x = box.minX() + i % w, z = box.minZ() + i / w;
            int fillTo = Math.min(median, lowestSolid[i]);
            level.setBlock(p.set(x, g, z), Blocks.DIRT.defaultBlockState(), FLAGS);
            for (int y = g + 1; y <= fillTo; y++) {
                level.setBlock(p.set(x, y, z), (y == fillTo ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), FLAGS);
                changed++;
            }
        }
        return changed;
    }

    /** One chunk inside the arena that loaded after formation: its share of every surface structure goes. */
    static void processChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        Set<StructureStart> starts = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet())
            if (relevant(e.getKey(), e.getValue()))
                starts.add(e.getValue());
        for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
            if (e.getKey().step() != GenerationStep.Decoration.SURFACE_STRUCTURES)
                continue;
            for (long ref : e.getValue()) {
                ChunkAccess origin = level.getChunk(ChunkPos.getX(ref), ChunkPos.getZ(ref), ChunkStatus.STRUCTURE_STARTS);
                StructureStart s = origin.getStartForStructure(e.getKey());
                if (relevant(e.getKey(), s))
                    starts.add(s);
            }
        }
        if (starts.isEmpty())
            return;
        BoundingBox chunkBox = new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
                cp.getMaxBlockX(), level.getMaxBuildHeight(), cp.getMaxBlockZ());
        for (StructureStart s : starts)
            for (StructurePiece piece : s.getPieces()) {
                BoundingBox b = piece.getBoundingBox();
                if (!b.intersects(chunkBox))
                    continue;
                clearBox(level, new BoundingBox(Math.max(b.minX(), chunkBox.minX()), b.minY(), Math.max(b.minZ(), chunkBox.minZ()),
                        Math.min(b.maxX(), chunkBox.maxX()), b.maxY(), Math.min(b.maxZ(), chunkBox.maxZ())), arenaCx, arenaCz, arenaR);
            }
        sweepStrays(level, new AABB(chunkBox.minX(), level.getMinBuildHeight(), chunkBox.minZ(),
                chunkBox.maxX() + 1, level.getMaxBuildHeight(), chunkBox.maxZ() + 1), arenaCx, arenaCz, arenaR);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load evt) {
        // may arrive off the main thread: only note the chunk, the server tick does the work
        if (arenaLevel != null && evt.getLevel() == arenaLevel && evt.getChunk() instanceof LevelChunk)
            loadedQueue.add(evt.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        ServerLevel level = arenaLevel;
        if (evt.phase != TickEvent.Phase.END || level == null || loadedQueue.isEmpty())
            return;
        boolean refreshed = false;
        for (int done = 0; done < CHUNKS_PER_TICK; ) {
            Long key = loadedQueue.poll();
            if (key == null)
                break;
            int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
            float dx = (x << 4) + 8 - arenaCx, dz = (z << 4) + 8 - arenaCz;
            if (dx * dx + dz * dz > (arenaR + 12f) * (arenaR + 12f) || !doneChunks.add((long) key))
                continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) {
                doneChunks.remove((long) key);   // unloaded again before we got to it: next load will requeue
                continue;
            }
            if (!refreshed) {
                StartAreaClearing.refreshProtected();
                refreshed = true;
            }
            try {
                processChunk(level, chunk);
            } catch (Exception e) {
                ReignOfNether.LOGGER.warn("[ArenaStructureClearing] failed on chunk [{}, {}]", x, z, e);
            }
            done++;
        }
    }

    /** Villagers and golems saved in a village chunk join as it loads: they never make it onto the field. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (arenaLevel != null && evt.getLevel() == arenaLevel && strayVillageMob(evt.getEntity()) && insideArena(evt.getEntity()))
            evt.setCanceled(true);
    }

    /** Day time a fresh skirmish world starts at: late morning, the sun high enough that it lights rather than tints. */
    static final long BATTLE_DAY_TIME = 5000L;

    /**
     * Battlefield light (rover playtest, Oct 17 2026, on lovish's PC: "the whole map reads orange"). A fresh skirmish
     * world starts at day time 0 - sunrise - and under Complementary the low golden sun washed every match amber, with
     * shadows ten blocks long; at day time 5000 the same ground reads green and its texture depth shows. So a Beyond
     * and Tabs skirmish world that has never run (QuickBattle names it "Beyond and Tabs - Skirmish N"; its clock is
     * still 0) starts at late morning with the daylight cycle off: BAR has no night, and a match that started at dawn
     * used to turn dark around minute 11. A lobby "time of day" choice is the planned follow-up (inbox spec-2,
     * section 2.1). A reloaded world keeps whatever time and rule it has.
     */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent evt) {
        net.minecraft.server.MinecraftServer server = evt.getServer();
        ServerLevel overworld = server == null ? null : server.overworld();
        if (overworld == null || overworld.getDayTime() >= 20)
            return;
        String name = server.getWorldData().getLevelName();
        if (name == null || !name.startsWith("Beyond and Tabs - Skirmish "))
            return;
        overworld.setDayTime(BATTLE_DAY_TIME);
        overworld.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        ReignOfNether.LOGGER.info("[Battlefield] fresh skirmish world: day time {} with the daylight cycle off", BATTLE_DAY_TIME);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        arenaLevel = null;
        arenaR = 0;
        doneChunks.clear();
        loadedQueue.clear();
    }
}
