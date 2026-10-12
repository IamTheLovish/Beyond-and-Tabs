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
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
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
 * fluid up to the water/lava line and air above (trees, hills and villages of the old terrain go), and on a railed
 * map (Chorus Isles) a guard rail on every column that touches the void. Then the realm maps' groves (short fungi,
 * chorus) in the same per-tick budget, trees in the reclaim lanes, pillars, lava vents, and last the economy: metal
 * patches with their values and the map's capture sites in the map's style.
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
            // sand is soul sand here (the slow valleys); the alternate top is warped nylium; no feature trees - the
            // builder grows its own short fungi (growFungus)
            case SOULFIRE_DELTAS -> new Palette(s(Blocks.NETHERRACK), s(Blocks.CRIMSON_NYLIUM), s(Blocks.SOUL_SAND),
                s(Blocks.NETHERRACK), s(Blocks.BLACKSTONE), s(Blocks.BASALT), s(Blocks.WARPED_NYLIUM),
                s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.BLACKSTONE), s(Blocks.BLACKSTONE), s(Blocks.BASALT), null, 3);
            case CHORUS_ISLES -> new Palette(s(Blocks.END_STONE), s(Blocks.END_STONE), s(Blocks.END_STONE), s(Blocks.END_STONE),
                s(Blocks.END_STONE), s(Blocks.END_STONE_BRICKS), s(Blocks.END_STONE), s(Blocks.PURPUR_BLOCK),
                s(Blocks.BLACK_CONCRETE), s(Blocks.END_STONE), s(Blocks.OBSIDIAN), null, 2);
            default -> new Palette(s(Blocks.GRASS_BLOCK), s(Blocks.GRASS_BLOCK), s(Blocks.SAND), s(Blocks.DIRT),
                s(Blocks.STONE), s(Blocks.STONE), s(Blocks.GRASS_BLOCK), s(Blocks.OAK_PLANKS), s(Blocks.STONE),
                s(Blocks.SAND), s(Blocks.STONE), TreeFeatures.OAK, 0);
        };
    }

    // desert cliffs show the badlands' banded terracotta
    private static final BlockState[] BANDS = { s(Blocks.TERRACOTTA), s(Blocks.ORANGE_TERRACOTTA), s(Blocks.TERRACOTTA),
        s(Blocks.RED_TERRACOTTA), s(Blocks.YELLOW_TERRACOTTA), s(Blocks.WHITE_TERRACOTTA), s(Blocks.BROWN_TERRACOTTA) };

    // ------------------------------------------------------------------------------------------ the job

    /**
     * A realm-map grove plant: the ground block it grows from, what it is, and a shape seed taken from the folded
     * slot-frame position - so the mirrored plant in every other wedge grows the same shape (and the same wood).
     */
    record Grove(BlockPos ground, int kind, int shape) { }
    static final int CRIMSON = 0, WARPED = 1, CHORUS = 2;

    static final class Job {
        ServerLevel level;
        MapLayout layout;
        Palette pal;
        int cx, cz, baseY;
        final List<long[]> chunks = new ArrayList<>();   // {chunkX, chunkZ}
        int nextChunk = 0;
        final List<BlockPos> trees = new ArrayList<>();
        final List<BlockPos> decor = new ArrayList<>();
        final List<Grove> groves = new ArrayList<>();
        int nextGrove = 0;
        int stage = 0;   // 0 terrain, 1 groves, 2 decoration, 3 economy, 4 done
        int lastShownPct = -1;
        Runnable onDone;
    }

    private static Job job = null;

    public static boolean isBuilding() {
        return job != null;
    }

    /**
     * The running build's stage for the save (match.MatchState, code_review CR-13): -1 idle, 0 terrain,
     * 1 decoration, 2 economy next, 3 economy stamped and only the hand-over left. A reload re-runs a build below 3
     * (it only writes blocks that differ); at 3 it must not stamp the patches and sites twice.
     */
    public static int stage() {
        Job j = job;
        return j == null ? -1 : j.stage;
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
                    // the realm maps' groves, inside the same budget as the terrain: a 16-player Soulfire Deltas
                    // grows a few thousand fungi, which would be one long tick done all at once
                    while (j.nextGrove < j.groves.size() && System.nanoTime() < deadline)
                        grow(j.level, j.groves.get(j.nextGrove++), j.layout);
                    if (j.nextGrove >= j.groves.size())
                        j.stage = 2;
                }
                case 2 -> {
                    decorate(j);
                    j.stage = 3;
                }
                case 3 -> {
                    stampEconomy(j.level, j.layout, j.cx, j.cz, j.pal);
                    j.stage = 4;
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
        buildArea(j, chunkX * 16, chunkZ * 16, 16, 16, sample);
    }

    /**
     * The terrain of the columns [x0, x0 + w) x [z0, z0 + d): a chunk of the running build, or a game test's window.
     * Samples the area plus a border - one block so every column knows its slope without resampling, two on a
     * railed map, where a rail's wall joins its neighbours only if they are rails too (and that needs theirs).
     */
    static void buildArea(Job j, int x0, int z0, int w, int d, MapLayout.Sample sample) {
        MapLayout layout = j.layout;
        boolean railed = layout.preset.railedVoid();
        int b = railed ? 2 : 1;
        int sw = w + 2 * b, sd = d + 2 * b;
        int[][] h = new int[sw][sd];
        MapLayout.Surface[][] surf = new MapLayout.Surface[sw][sd];
        int[][] fluid = new int[w][d];
        boolean[][] forest = new boolean[w][d];
        double[][] uv = new double[w][d * 2];
        for (int i = 0; i < sw; i++)
            for (int k = 0; k < sd; k++) {
                layout.sample(x0 + i - b - j.cx, z0 + k - b - j.cz, sample);
                h[i][k] = sample.height;
                surf[i][k] = sample.surface;
                int ii = i - b, kk = k - b;
                if (ii >= 0 && ii < w && kk >= 0 && kk < d) {
                    fluid[ii][kk] = sample.fluidTop;
                    forest[ii][kk] = sample.forest;
                    uv[ii][kk * 2] = sample.u;
                    uv[ii][kk * 2 + 1] = sample.v;
                }
            }
        // guard rails: every column of the grid but its outer ring, from its 8 neighbours (MapLayout.railAt)
        boolean[][] rail = null;
        if (railed) {
            rail = new boolean[sw][sd];
            for (int i = 1; i < sw - 1; i++)
                for (int k = 1; k < sd - 1; k++) {
                    boolean voidNb = false;
                    for (int a = -1; a <= 1 && !voidNb; a++)
                        for (int c = -1; c <= 1; c++)
                            if ((a != 0 || c != 0) && surf[i + a][k + c] == MapLayout.Surface.VOID) {
                                voidNb = true;
                                break;
                            }
                    rail[i][k] = layout.railAt(surf[i][k], voidNb);
                }
        }
        double r2 = (double) (layout.radius + 3) * (layout.radius + 3);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < w; i++)
            for (int k = 0; k < d; k++) {
                int x = x0 + i, z = z0 + k;
                double dx = x - j.cx, dz = z - j.cz;
                if (dx * dx + dz * dz > r2)
                    continue;
                int gi = i + b, gk = k + b;
                int hh = h[gi][gk];
                int slope = Math.max(Math.max(Math.abs(h[gi - 1][gk] - hh), Math.abs(h[gi + 1][gk] - hh)),
                    Math.max(Math.abs(h[gi][gk - 1] - hh), Math.abs(h[gi][gk + 1] - hh)));
                boolean railHere = rail != null && rail[gi][gk];
                column(j, p, x, z, hh, surf[gi][gk], fluid[i][k], slope, railHere ? railState(rail, gi, gk) : null);
                double u = uv[i][k * 2], v = uv[i][k * 2 + 1];
                if (forest[i][k] && slope < 2 && j.pal.tree() != null && layout.decorHash(u, v) < TREE_DENSITY)
                    j.trees.add(new BlockPos(x, j.baseY + hh + 1, z));
                else if (forest[i][k] && slope < 2 && !railHere && j.pal.tree() == null && layout.decorHash(u, v) < layout.groveDensity())
                    j.groves.add(new Grove(new BlockPos(x, j.baseY + hh, z), layout.preset.theme == MapPreset.Theme.END
                        ? CHORUS : layout.groveWarped(u, v) ? WARPED : CRIMSON, (int) (layout.decorHash(u + 57, v) * 1000)));
                else if (layout.preset == MapPreset.DESERT_MESA && surf[gi][gk] == MapLayout.Surface.LAND && slope < 2
                        && layout.decorHash(u + 101, v) < 0.0025)
                    j.decor.add(new BlockPos(x, j.baseY + hh + 1, z));
            }
    }

    private static final BlockState RAIL = Blocks.END_STONE_BRICK_WALL.defaultBlockState();

    /**
     * The guard rail's wall block at grid cell (i, k): joined (a low side) towards every 4-neighbour that is a rail
     * too, so the rail line is a continuous wall with no gap to squeeze through; a post where it is not a straight
     * run, as vanilla raises one. Worked out from the sampled grid, not the world: no neighbour updates needed.
     */
    static BlockState railState(boolean[][] rail, int i, int k) {
        boolean n = rail[i][k - 1], s = rail[i][k + 1], w = rail[i - 1][k], e = rail[i + 1][k];
        boolean straight = (n && s && !w && !e) || (w && e && !n && !s);
        return RAIL.setValue(WallBlock.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
            .setValue(WallBlock.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
            .setValue(WallBlock.WEST_WALL, w ? WallSide.LOW : WallSide.NONE)
            .setValue(WallBlock.EAST_WALL, e ? WallSide.LOW : WallSide.NONE)
            .setValue(WallBlock.UP, !straight);
    }

    private static void column(Job j, BlockPos.MutableBlockPos p, int x, int z, int height,
                               MapLayout.Surface surface, int fluidTop, int slope, BlockState rail) {
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
        if (rail != null)
            ceil = Math.max(ceil, Math.min(level.getMaxBuildHeight() - 1, target + 1));
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = floor; y <= ceil; y++) {
            BlockState want;
            if (y == target + 1 && rail != null)
                want = rail;   // the guard rail stands on the column's own surface
            else if (y > target)
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
        // landmark pillars (basalt / obsidian): 3x3 columns, a glowing cap so they read at night
        for (MapLayout.Spot s : j.layout.pillars)
            stampPillar(level, j.cx + s.dx(), j.cz + s.dz(), s.size(), j.pal.pillar(), j.layout.preset);
        // lava vents: a 3x3 pool flush with the ground in a magma rim (geothermal sites, never on dry ground)
        for (MapLayout.Spot s : j.layout.vents)
            stampVent(level, j.cx + s.dx(), j.cz + s.dz());
        ReignOfNether.LOGGER.info("[MapBuilder] {} trees, {} groves, {} decor, {} pillars, {} vents", grown,
            j.groves.size(), j.decor.size(), j.layout.pillars.size(), j.layout.vents.size());
    }

    static void stampPillar(ServerLevel level, int x, int z, int height, BlockState pillar, MapPreset preset) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int g = MetalPatches.solidTop(level, x, z);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int y = 1; y <= height - (dx != 0 && dz != 0 ? 3 : 0); y++)
                    level.setBlock(p.set(x + dx, g + y, z + dz), pillar, FLAGS);
        BlockState cap = preset == MapPreset.END_SPIRES ? Blocks.END_ROD.defaultBlockState()
            : Blocks.MAGMA_BLOCK.defaultBlockState();
        level.setBlock(p.set(x, g + height + 1, z), cap, FLAGS);
    }

    // ------------------------------------------------------------------------------------------ realm groves

    static void grow(ServerLevel level, Grove g, MapLayout layout) {
        if (g.kind() == CHORUS)
            growChorus(level, g.ground(), g.shape());
        else
            growFungus(level, g.ground(), g.kind() == WARPED, g.shape());
    }

    /**
     * A short huge fungus: a stem of 3 to 5 under a 3x3 wart cap with a shroomlight heart, a crown block and a
     * skirt hanging one lower on one side. Low on purpose - lovish's rule that trees are reclaim and never hide the
     * view - and on nylium, so TreeClearing fells it as a natural tree (its wart and shroomlight are canopy).
     */
    static void growFungus(ServerLevel level, BlockPos ground, boolean warped, int shape) {
        BlockState stem = (warped ? Blocks.WARPED_STEM : Blocks.CRIMSON_STEM).defaultBlockState();
        BlockState wart = (warped ? Blocks.WARPED_WART_BLOCK : Blocks.NETHER_WART_BLOCK).defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x = ground.getX(), z = ground.getZ(), gy = ground.getY();
        level.setBlock(p.set(x, gy, z), (warped ? Blocks.WARPED_NYLIUM : Blocks.CRIMSON_NYLIUM).defaultBlockState(), FLAGS);
        int height = 3 + shape % 3;
        for (int y = 1; y <= height; y++)
            level.setBlock(p.set(x, gy + y, z), stem, FLAGS);
        int top = gy + height + 1;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                level.setBlock(p.set(x + dx, top, z + dz),
                    dx == 0 && dz == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : wart, FLAGS);
        level.setBlock(p.set(x, top + 1, z), wart, FLAGS);
        int[] side = SIDES[(shape / 3) % 4];
        for (int t = -1; t <= 1; t++)
            if (side[0] != 0)
                level.setBlock(p.set(x + side[0], top - 1, z + t), wart, FLAGS);
            else
                level.setBlock(p.set(x + t, top - 1, z + side[1]), wart, FLAGS);
    }

    private static final int[][] SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /**
     * A short chorus plant: a stalk of 2 to 4 and, on most, one side branch, each ending in a flower. The flowers are
     * set dead (age 5), so the groves never grow on their own and stay the size the map drew them.
     */
    static void growChorus(ServerLevel level, BlockPos ground, int shape) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x = ground.getX(), z = ground.getZ(), gy = ground.getY();
        int height = 2 + shape % 3;
        boolean branch = (shape / 3) % 3 != 0;
        int[] side = SIDES[(shape / 9) % 4];
        BlockState plant = Blocks.CHORUS_PLANT.defaultBlockState().setValue(PipeBlock.DOWN, true).setValue(PipeBlock.UP, true);
        BlockState flower = Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5);
        for (int y = 1; y <= height; y++) {
            BlockState s = plant;
            if (branch && y == height)
                s = s.setValue(pipeSide(side[0], side[1]), true);
            level.setBlock(p.set(x, gy + y, z), s, FLAGS);
        }
        level.setBlock(p.set(x, gy + height + 1, z), flower, FLAGS);
        if (branch) {
            BlockState arm = Blocks.CHORUS_PLANT.defaultBlockState().setValue(pipeSide(-side[0], -side[1]), true)
                .setValue(PipeBlock.UP, true);
            level.setBlock(p.set(x + side[0], gy + height, z + side[1]), arm, FLAGS);
            level.setBlock(p.set(x + side[0], gy + height + 1, z + side[1]), flower, FLAGS);
        }
    }

    static net.minecraft.world.level.block.state.properties.BooleanProperty pipeSide(int dx, int dz) {
        return dx > 0 ? PipeBlock.EAST : dx < 0 ? PipeBlock.WEST : dz > 0 ? PipeBlock.SOUTH : PipeBlock.NORTH;
    }

    // ------------------------------------------------------------------------------------------ game tests

    /**
     * Game tests: raises the columns [x0, x0 + w) x [z0, z0 + d) of {@code layout} centred on (cx, cz) with its base
     * at baseY, synchronously, with the groves rooted there and the pillars and vents whose whole footprint lies
     * inside (nothing is written outside the window). The economy is left out: patches and sites register with the
     * live match. Returns the ground blocks of the grove plants it grew.
     */
    public static List<BlockPos> buildWindow(ServerLevel level, MapLayout layout, int cx, int cz, int baseY,
                                             int x0, int z0, int w, int d) {
        Job j = new Job();
        j.level = level;
        j.layout = layout;
        j.pal = paletteFor(layout.preset);
        j.cx = cx;
        j.cz = cz;
        j.baseY = baseY;
        buildArea(j, x0, z0, w, d, new MapLayout.Sample());
        List<BlockPos> grown = new ArrayList<>();
        for (Grove g : j.groves)
            if (g.ground().getX() >= x0 + 1 && g.ground().getX() < x0 + w - 1
                    && g.ground().getZ() >= z0 + 1 && g.ground().getZ() < z0 + d - 1) {   // a cap reaches one block
                grow(level, g, layout);
                grown.add(g.ground());
            }
        for (MapLayout.Spot s : layout.pillars)
            if (inside(cx + s.dx(), cz + s.dz(), 1, x0, z0, w, d))
                stampPillar(level, cx + s.dx(), cz + s.dz(), s.size(), j.pal.pillar(), layout.preset);
        for (MapLayout.Spot s : layout.vents)
            if (inside(cx + s.dx(), cz + s.dz(), 2, x0, z0, w, d))
                stampVent(level, cx + s.dx(), cz + s.dz());
        return grown;
    }

    private static boolean inside(int x, int z, int r, int x0, int z0, int w, int d) {
        return x - r >= x0 && x + r < x0 + w && z - r >= z0 && z + r < z0 + d;
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
        CapturePointServerEvents.Style style = siteStyle(layout.preset);
        for (MapLayout.Site s : layout.sites)
            CapturePointServerEvents.place(level, cx + s.dx(), cz + s.dz(), s.kind(), style);
        MetalPatches.syncToClients(level);
        ReignOfNether.LOGGER.info("[MapBuilder] stamped {} patches and {} capture sites", layout.mexes.size(), layout.sites.size());
    }

    /** The realm maps build their sites as nether-brick fortress ruins and End obsidian pillars; the rest as ruins. */
    public static CapturePointServerEvents.Style siteStyle(MapPreset preset) {
        return switch (preset) {
            case SOULFIRE_DELTAS -> CapturePointServerEvents.Style.NETHER_BRICK;
            case CHORUS_ISLES -> CapturePointServerEvents.Style.OBSIDIAN;
            default -> CapturePointServerEvents.Style.RUIN;
        };
    }
}
