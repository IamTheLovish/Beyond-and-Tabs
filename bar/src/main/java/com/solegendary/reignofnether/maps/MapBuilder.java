package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Stamps a pre-made map (MapLayout) into the world round the lobby centre (design/maps.md). Chunk by chunk from the
 * centre outwards, inside a per-tick time budget, so the server keeps ticking and the players watch the map rise
 * (the action bar shows the progress). Per column it writes only the blocks that differ from what is there: the
 * new surface, three blocks of subsoil, solid ground four blocks under the surface (no cave mouth under a base),
 * fluid up to the water/lava line and air above (trees, hills and villages of the old terrain go). Then trees in
 * the reclaim lanes, pillars, lava vents, and last the economy: metal patches with their values and the map's
 * capture sites.
 */
public final class MapBuilder {
    private MapBuilder() { }

    /** Wall-clock budget per server tick; there is no match running yet, so the build may use most of a tick. */
    static final long BUDGET_NANOS = 35_000_000L;
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Share of forest columns that grow a tree (sparse: reclaim lanes you can see through). */
    static final double TREE_DENSITY = 0.045;

    /** The blocks one map is made of. */
    public record Palette(BlockState top, BlockState grass, BlockState sand, BlockState sub, BlockState deep,
                          BlockState cliff, BlockState mesaTop, BlockState bridge, BlockState voidFloor,
                          BlockState bed, BlockState pillar, ResourceKey<ConfiguredFeature<?, ?>> tree,
                          int wallMaterial) { }

    static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    public static Palette paletteFor(MapPreset p) {
        return switch (p) {
            case ARCHIPELAGO -> new Palette(s(Blocks.GRASS_BLOCK), s(Blocks.GRASS_BLOCK), s(Blocks.SAND), s(Blocks.DIRT),
                s(Blocks.STONE), s(Blocks.STONE), s(Blocks.GRASS_BLOCK), s(Blocks.OAK_PLANKS), s(Blocks.STONE),
                s(Blocks.SAND), s(Blocks.STONE), TreeFeatures.OAK, 1);
            case HIGHLANDS -> new Palette(s(Blocks.GRASS_BLOCK), s(Blocks.GRASS_BLOCK), s(Blocks.COARSE_DIRT), s(Blocks.DIRT),
                s(Blocks.STONE), s(Blocks.STONE), s(Blocks.GRASS_BLOCK), s(Blocks.STONE_BRICKS), s(Blocks.STONE),
                s(Blocks.GRAVEL), s(Blocks.STONE), TreeFeatures.SPRUCE, 0);
            case NETHER_RIFT -> new Palette(s(Blocks.NETHERRACK), s(Blocks.CRIMSON_NYLIUM), s(Blocks.SOUL_SOIL), s(Blocks.NETHERRACK),
                s(Blocks.NETHERRACK), s(Blocks.BASALT), s(Blocks.NETHERRACK), s(Blocks.POLISHED_BLACKSTONE_BRICKS),
                s(Blocks.BLACKSTONE), s(Blocks.BLACKSTONE), s(Blocks.BASALT), null, 3);
            case END_SPIRES -> new Palette(s(Blocks.END_STONE), s(Blocks.END_STONE), s(Blocks.END_STONE), s(Blocks.END_STONE),
                s(Blocks.END_STONE), s(Blocks.END_STONE_BRICKS), s(Blocks.END_STONE), s(Blocks.PURPUR_BLOCK),
                s(Blocks.BLACK_CONCRETE), s(Blocks.END_STONE), s(Blocks.OBSIDIAN), null, 2);
            case VERDANT_VALLEY -> new Palette(s(Blocks.GRASS_BLOCK), s(Blocks.GRASS_BLOCK), s(Blocks.SAND), s(Blocks.DIRT),
                s(Blocks.STONE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.GRASS_BLOCK), s(Blocks.OAK_PLANKS), s(Blocks.STONE),
                s(Blocks.SAND), s(Blocks.STONE), TreeFeatures.OAK, 0);
            case DESERT_MESA -> new Palette(s(Blocks.SAND), s(Blocks.GRASS_BLOCK), s(Blocks.SAND), s(Blocks.SANDSTONE),
                s(Blocks.SANDSTONE), s(Blocks.TERRACOTTA), s(Blocks.RED_SAND), s(Blocks.SMOOTH_SANDSTONE), s(Blocks.SANDSTONE),
                s(Blocks.SAND), s(Blocks.TERRACOTTA), TreeFeatures.ACACIA, 1);
            default -> new Palette(s(Blocks.GRASS_BLOCK), s(Blocks.GRASS_BLOCK), s(Blocks.SAND), s(Blocks.DIRT),
                s(Blocks.STONE), s(Blocks.STONE), s(Blocks.GRASS_BLOCK), s(Blocks.OAK_PLANKS), s(Blocks.STONE),
                s(Blocks.SAND), s(Blocks.STONE), TreeFeatures.OAK, 0);
        };
    }

    // desert cliffs show the badlands' banded terracotta
    private static final BlockState[] BANDS = { s(Blocks.TERRACOTTA), s(Blocks.ORANGE_TERRACOTTA), s(Blocks.TERRACOTTA),
        s(Blocks.RED_TERRACOTTA), s(Blocks.YELLOW_TERRACOTTA), s(Blocks.WHITE_TERRACOTTA), s(Blocks.BROWN_TERRACOTTA) };

    // ------------------------------------------------------------------------------------------ the job

    static final class Job {
        ServerLevel level;
        MapLayout layout;
        Palette pal;
        int cx, cz, baseY;
        final List<long[]> chunks = new ArrayList<>();   // {chunkX, chunkZ}
        int nextChunk = 0;
        final List<BlockPos> trees = new ArrayList<>();
        final List<BlockPos> decor = new ArrayList<>();
        int stage = 0;   // 0 terrain, 1 decoration, 2 economy, 3 done
        int lastShownPct = -1;
        Runnable onDone;
    }

    private static Job job = null;

    public static boolean isBuilding() {
        return job != null;
    }

    /** /rts-reset: drop the build (what was stamped stays; nothing is seated). */
    public static void cancel() {
        job = null;
    }

    /** Progress 0..100 of the running build (100 when idle). */
    public static int progress() {
        Job j = job;
        if (j == null)
            return 100;
        return Math.min(99, j.chunks.isEmpty() ? 99 : 100 * j.nextChunk / j.chunks.size());
    }

    /**
     * Starts stamping {@code layout} centred on (cx, cz) with its base height at baseY; {@code onDone} runs on the
     * server thread once the economy is stamped. A build already running is dropped.
     */
    public static void start(ServerLevel level, int cx, int cz, int baseY, MapLayout layout, Runnable onDone) {
        Job j = new Job();
        j.level = level;
        j.layout = layout;
        j.pal = paletteFor(layout.preset);
        j.cx = cx;
        j.cz = cz;
        j.baseY = baseY;
        j.onDone = onDone;
        int r = layout.radius + 3;
        int c0x = (cx - r) >> 4, c1x = (cx + r) >> 4, c0z = (cz - r) >> 4, c1z = (cz + r) >> 4;
        for (int x = c0x; x <= c1x; x++)
            for (int z = c0z; z <= c1z; z++) {
                // the chunk's nearest point to the centre must be inside the map
                double nx = Math.max(x * 16, Math.min(cx, x * 16 + 15)) - cx;
                double nz = Math.max(z * 16, Math.min(cz, z * 16 + 15)) - cz;
                if (nx * nx + nz * nz <= (double) r * r)
                    j.chunks.add(new long[] { x, z });
            }
        // centre first: the middle of the map rises before the edges, where the players' cameras start
        j.chunks.sort((a, b) -> Double.compare(
            Math.hypot(a[0] * 16 + 8 - cx, a[1] * 16 + 8 - cz), Math.hypot(b[0] * 16 + 8 - cx, b[1] * 16 + 8 - cz)));
        job = j;
        ReignOfNether.LOGGER.info("[MapBuilder] forging {} for {} slots at [{}, {}] y {}: radius {}, {} chunks",
            layout.preset.displayName, layout.n, cx, cz, baseY, layout.radius, j.chunks.size());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        job = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || job == null)
            return;
        Job j = job;
        try {
            long deadline = System.nanoTime() + BUDGET_NANOS;
            switch (j.stage) {
                case 0 -> {
                    MapLayout.Sample sample = new MapLayout.Sample();
                    while (j.nextChunk < j.chunks.size() && System.nanoTime() < deadline) {
                        long[] c = j.chunks.get(j.nextChunk++);
                        buildChunk(j, (int) c[0], (int) c[1], sample);
                    }
                    if (j.nextChunk >= j.chunks.size())
                        j.stage = 1;
                    showProgress(j);
                }
                case 1 -> {
                    decorate(j);
                    j.stage = 2;
                }
                case 2 -> {
                    stampEconomy(j.level, j.layout, j.cx, j.cz, j.pal);
                    j.stage = 3;
                }
                default -> {
                    job = null;
                    ReignOfNether.LOGGER.info("[MapBuilder] {} is ready", j.layout.preset.displayName);
                    if (j.onDone != null)
                        j.onDone.run();
                }
            }
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[MapBuilder] the map failed to build; starting on what is there", e);
            job = null;
            if (j.onDone != null)
                j.onDone.run();
        }
    }

    static void showProgress(Job j) {
        int pct = progress();
        if (pct / 5 == j.lastShownPct / 5)
            return;
        j.lastShownPct = pct;
        Component msg = Component.translatable("maps.reignofnether.forging", j.layout.preset.displayName, pct);
        for (ServerPlayer sp : j.level.getServer().getPlayerList().getPlayers())
            sp.displayClientMessage(msg, true);
    }

    // ------------------------------------------------------------------------------------------ terrain

    private static void buildChunk(Job j, int chunkX, int chunkZ, MapLayout.Sample sample) {
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        // heights of the chunk plus a one-block border, so every column knows its slope without resampling
        int[][] h = new int[18][18];
        MapLayout.Surface[][] surf = new MapLayout.Surface[16][16];
        int[][] fluid = new int[16][16];
        boolean[][] forest = new boolean[16][16];
        double[][] uv = new double[16][16 * 2];
        for (int i = 0; i < 18; i++)
            for (int k = 0; k < 18; k++) {
                j.layout.sample(x0 + i - 1 - j.cx, z0 + k - 1 - j.cz, sample);
                h[i][k] = sample.height;
                if (i >= 1 && i <= 16 && k >= 1 && k <= 16) {
                    surf[i - 1][k - 1] = sample.surface;
                    fluid[i - 1][k - 1] = sample.fluidTop;
                    forest[i - 1][k - 1] = sample.forest;
                    uv[i - 1][(k - 1) * 2] = sample.u;
                    uv[i - 1][(k - 1) * 2 + 1] = sample.v;
                }
            }
        double r2 = (double) (j.layout.radius + 3) * (j.layout.radius + 3);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 16; i++)
            for (int k = 0; k < 16; k++) {
                int x = x0 + i, z = z0 + k;
                double dx = x - j.cx, dz = z - j.cz;
                if (dx * dx + dz * dz > r2)
                    continue;
                int hh = h[i + 1][k + 1];
                int slope = Math.max(Math.max(Math.abs(h[i][k + 1] - hh), Math.abs(h[i + 2][k + 1] - hh)),
                    Math.max(Math.abs(h[i + 1][k] - hh), Math.abs(h[i + 1][k + 2] - hh)));
                column(j, p, x, z, hh, surf[i][k], fluid[i][k], slope);
                if (forest[i][k] && slope < 2 && j.pal.tree() != null
                        && j.layout.decorHash(uv[i][k * 2], uv[i][k * 2 + 1]) < TREE_DENSITY)
                    j.trees.add(new BlockPos(x, j.baseY + hh + 1, z));
                else if (j.layout.preset == MapPreset.DESERT_MESA && surf[i][k] == MapLayout.Surface.LAND && slope < 2
                        && j.layout.decorHash(uv[i][k * 2] + 101, uv[i][k * 2 + 1]) < 0.0025)
                    j.decor.add(new BlockPos(x, j.baseY + hh + 1, z));
            }
    }

    private static void column(Job j, BlockPos.MutableBlockPos p, int x, int z, int height,
                               MapLayout.Surface surface, int fluidTop, int slope) {
        ServerLevel level = j.level;
        Palette pal = j.pal;
        int target = j.baseY + height;
        BlockState top, sub;
        BlockState fluidState = null;
        boolean steep = slope >= 3;
        switch (surface) {
            case GRASS -> { top = steep ? pal.cliff() : pal.grass(); sub = steep ? pal.cliff() : pal.sub(); }
            case SAND -> { top = steep ? pal.cliff() : pal.sand(); sub = steep ? pal.cliff() : pal.sand(); }
            case CLIFF -> { top = pal.cliff(); sub = pal.cliff(); }
            case MESA -> { top = pal.mesaTop(); sub = pal.cliff(); }
            case BRIDGE -> { top = pal.bridge(); sub = pal.bridge(); }
            case WATER -> { top = pal.bed(); sub = pal.bed(); fluidState = Blocks.WATER.defaultBlockState(); }
            case LAVA -> { top = pal.bed(); sub = pal.bed(); fluidState = Blocks.LAVA.defaultBlockState(); }
            case VOID -> { top = pal.voidFloor(); sub = pal.deep(); }
            default -> { top = steep ? pal.cliff() : pal.top(); sub = steep ? pal.cliff() : pal.sub(); }
        }
        boolean bands = j.layout.preset == MapPreset.DESERT_MESA && (surface == MapLayout.Surface.MESA || steep);
        int fluidY = fluidState != null ? j.baseY + fluidTop : Integer.MIN_VALUE;
        int existingTop = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int floor = Math.max(level.getMinBuildHeight() + 1, Math.min(target, existingTop) - 4);
        int ceil = Math.min(level.getMaxBuildHeight() - 1, Math.max(existingTop, Math.max(target, fluidY)));
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = floor; y <= ceil; y++) {
            BlockState want;
            if (y > target)
                want = y <= fluidY ? fluidState : air;
            else if (y == target)
                want = bands && surface != MapLayout.Surface.MESA ? BANDS[Math.floorMod(y / 2, BANDS.length)] : top;
            else if (y >= target - 3)
                want = bands ? BANDS[Math.floorMod(y / 2, BANDS.length)] : sub;
            else
                want = bands ? BANDS[Math.floorMod(y / 2, BANDS.length)] : pal.deep();
            p.set(x, y, z);
            if (level.getBlockState(p) != want)
                level.setBlock(p, want, FLAGS);
        }
    }

    // ------------------------------------------------------------------------------------------ decoration

    private static void decorate(Job j) {
        ServerLevel level = j.level;
        int grown = 0;
        if (j.pal.tree() != null) {
            var holder = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(j.pal.tree());
            var alt = j.layout.preset == MapPreset.VERDANT_VALLEY
                ? level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(TreeFeatures.BIRCH)
                : holder;
            for (int i = 0; i < j.trees.size(); i++) {
                BlockPos t = j.trees.get(i);
                var h = (i % 3 == 0) ? alt : holder;
                if (h.isPresent() && h.get().value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), t))
                    grown++;
            }
        }
        // desert scatter: cacti and dead bushes (StartAreaClearing clears them off the bases)
        for (int i = 0; i < j.decor.size(); i++) {
            BlockPos d = j.decor.get(i);
            if (i % 2 == 0) {
                level.setBlock(d, Blocks.DEAD_BUSH.defaultBlockState(), FLAGS);
            } else {
                for (int y = 0; y < 2 + (i % 3 == 0 ? 1 : 0); y++)
                    level.setBlock(d.above(y), Blocks.CACTUS.defaultBlockState(), FLAGS);
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // landmark pillars (basalt / obsidian): 3x3 columns, a glowing cap so they read at night
        for (MapLayout.Spot s : j.layout.pillars) {
            int x = j.cx + s.dx(), z = j.cz + s.dz();
            int g = MetalPatches.solidTop(level, x, z);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int y = 1; y <= s.size() - (dx != 0 && dz != 0 ? 3 : 0); y++)
                        level.setBlock(p.set(x + dx, g + y, z + dz), j.pal.pillar(), FLAGS);
            BlockState cap = j.layout.preset == MapPreset.END_SPIRES ? Blocks.END_ROD.defaultBlockState()
                : Blocks.MAGMA_BLOCK.defaultBlockState();
            level.setBlock(p.set(x, g + s.size() + 1, z), cap, FLAGS);
        }
        // lava vents: a 3x3 pool flush with the ground in a magma rim (geothermal sites, never on dry ground)
        for (MapLayout.Spot s : j.layout.vents)
            stampVent(level, j.cx + s.dx(), j.cz + s.dz());
        ReignOfNether.LOGGER.info("[MapBuilder] {} trees, {} decor, {} pillars, {} vents", grown, j.decor.size(),
            j.layout.pillars.size(), j.layout.vents.size());
    }

    public static void stampVent(ServerLevel level, int x, int z) {
        int g = MetalPatches.solidTop(level, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                boolean pool = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                level.setBlock(p.set(x + dx, g - 1, z + dz), Blocks.BLACKSTONE.defaultBlockState(), FLAGS);
                level.setBlock(p.set(x + dx, g, z + dz),
                    (pool ? Blocks.LAVA : Blocks.MAGMA_BLOCK).defaultBlockState(), FLAGS);
                for (int y = 1; y <= 3; y++)
                    if (!level.getBlockState(p.set(x + dx, g + y, z + dz)).isAir())
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
            }
    }

    // ------------------------------------------------------------------------------------------ economy

    /** The map's metal patches (with their values) and capture sites. Public for the game test. */
    public static void stampEconomy(ServerLevel level, MapLayout layout, int cx, int cz, Palette pal) {
        // patch pads take the map's ground instead of grass (sand in the desert, end stone over the void...)
        MetalPatches.capOverride = pal != null && layout.preset.premade() ? pal.top() : null;
        try {
            for (MapLayout.Mex m : layout.mexes)
                MetalPatches.stamp(level, cx + m.dx(), cz + m.dz(), m.value());
        } finally {
            MetalPatches.capOverride = null;
        }
        for (MapLayout.Site s : layout.sites)
            CapturePointServerEvents.place(level, cx + s.dx(), cz + s.dz(), s.kind());
        MetalPatches.syncToClients(level);
        ReignOfNether.LOGGER.info("[MapBuilder] stamped {} patches and {} capture sites", layout.mexes.size(), layout.sites.size());
    }
}
