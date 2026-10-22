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
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

    /**
     * Wall-clock budget per server tick. The build is the first stage of the preparation (match.MatchPrep): nobody
     * is playing and nothing else heavy runs, so it may use most of a tick - 45 ms, up from 35 (spec 3, 2.1).
     */
    static final long BUDGET_NANOS = 45_000_000L;
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
            // a generated map: temperate ground, stone cliff faces, coarse dirt on the high tiers (the MESA surface),
            // gravel under fords and shores, oak and birch groves
            case GENERATED -> new Palette(s(Blocks.GRASS_BLOCK), s(Blocks.GRASS_BLOCK), s(Blocks.SAND), s(Blocks.DIRT),
                s(Blocks.STONE), s(Blocks.STONE), s(Blocks.COARSE_DIRT), s(Blocks.OAK_PLANKS), s(Blocks.STONE),
                s(Blocks.GRAVEL), s(Blocks.STONE), TreeFeatures.OAK, 0);
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
        /** The plant cover's mix for this preset (null: none - see mixFor). */
        PlantMix mix;
        final List<long[]> chunks = new ArrayList<>();   // {chunkX, chunkZ}
        int nextChunk = 0;
        final List<BlockPos> trees = new ArrayList<>();
        final List<BlockPos> decor = new ArrayList<>();
        /** Plant cover: the column's air block above its grass, and what grows there (an index into PLANTS). */
        final List<BlockPos> plants = new ArrayList<>();
        final List<Byte> plantKinds = new ArrayList<>();
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
     * The running build's stage for the save (match.MatchState through MatchPrep, code_review CR-13): -1 idle,
     * 0 terrain, 1 groves, 2 decoration, 3 economy next, 4 economy stamped and only the hand-over left. A reload
     * re-runs a build saved at 0-2 from the terrain (it only writes blocks that differ; groves and decoration are
     * listed during the terrain pass, so they cannot start on their own) and one saved at 3 from the economy alone;
     * at 4 it must not stamp the patches and sites twice (MatchPrep saves that as its CLEAR stage).
     */
    public static int stage() {
        Job j = job;
        return j == null ? -1 : j.stage;
    }

    /** /rts-reset: drop the build (what was stamped stays; nothing is seated). */
    public static void cancel() {
        job = null;
    }

    /**
     * Progress 0..100 of the running build (100 when idle): the terrain's chunks fill 0-90, the groves 90-95, then
     * the decoration and the economy - so a build resumed at its economy never shows 0 %.
     */
    public static int progress() {
        Job j = job;
        if (j == null)
            return 100;
        return switch (j.stage) {
            case 0 -> j.chunks.isEmpty() ? 90 : 90 * j.nextChunk / j.chunks.size();
            case 1 -> 90 + (j.groves.isEmpty() ? 5 : 5 * j.nextGrove / j.groves.size());
            case 2 -> 96;
            case 3 -> 98;
            default -> 99;
        };
    }

    /**
     * Starts stamping {@code layout} centred on (cx, cz) with its base height at baseY; {@code onDone} runs on the
     * server thread once the economy is stamped. A build already running is dropped.
     */
    public static void start(ServerLevel level, int cx, int cz, int baseY, MapLayout layout, Runnable onDone) {
        start(level, cx, cz, baseY, layout, 0, onDone);
    }

    /**
     * As above from a saved stage ({@link #stage}): below 3 the whole build runs again (idempotent), at 3 the
     * terrain, groves and decoration are on disk already and only the economy is stamped - a world saved exactly
     * there used to reload with no patches and no sites, since the resume took "3" for "stamped".
     */
    public static void start(ServerLevel level, int cx, int cz, int baseY, MapLayout layout, int fromStage, Runnable onDone) {
        Job j = new Job();
        j.level = level;
        j.layout = layout;
        j.pal = paletteFor(layout.preset);
        j.mix = mixFor(layout.preset);
        j.cx = cx;
        j.cz = cz;
        j.baseY = baseY;
        j.onDone = onDone;
        int r = layout.arenaHalfExtent() + 3;
        int c0x = (cx - r) >> 4, c1x = (cx + r) >> 4, c0z = (cz - r) >> 4, c1z = (cz + r) >> 4;
        for (int x = c0x; x <= c1x; x++)
            for (int z = c0z; z <= c1z; z++) {
                // the chunk's nearest point to the centre must be inside the map (a circle or a square: its own test)
                double nx = Math.max(x * 16, Math.min(cx, x * 16 + 15)) - cx;
                double nz = Math.max(z * 16, Math.min(cz, z * 16 + 15)) - cz;
                if (layout.distanceToArenaEdge(nx, nz) >= -3)
                    j.chunks.add(new long[] { x, z });
            }
        // centre first: the middle of the map rises before the edges, where the players' cameras start
        j.chunks.sort((a, b) -> Double.compare(
            Math.hypot(a[0] * 16 + 8 - cx, a[1] * 16 + 8 - cz), Math.hypot(b[0] * 16 + 8 - cx, b[1] * 16 + 8 - cz)));
        if (fromStage >= 3) {
            j.stage = 3;
            j.nextChunk = j.chunks.size();
        }
        job = j;
        ReignOfNether.LOGGER.info("[MapBuilder] forging {} for {} slots at [{}, {}] y {}: radius {}, {} chunks{}",
            layout.preset.displayName, layout.n, cx, cz, baseY, layout.radius, j.chunks.size(),
            j.stage == 3 ? " (resumed: the economy only)" : "");
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
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < w; i++)
            for (int k = 0; k < d; k++) {
                int x = x0 + i, z = z0 + k;
                double dx = x - j.cx, dz = z - j.cz;
                if (layout.distanceToArenaEdge(dx, dz) < -3)
                    continue;
                int gi = i + b, gk = k + b;
                int hh = h[gi][gk];
                int slope = Math.max(Math.max(Math.abs(h[gi - 1][gk] - hh), Math.abs(h[gi + 1][gk] - hh)),
                    Math.max(Math.abs(h[gi][gk - 1] - hh), Math.abs(h[gi][gk + 1] - hh)));
                boolean railHere = rail != null && rail[gi][gk];
                column(j, p, x, z, hh, surf[gi][gk], fluid[i][k], slope, railHere ? railState(rail, gi, gk) : null);
                double u = uv[i][k * 2], v = uv[i][k * 2 + 1];
                if (forest[i][k] && slope < 2 && j.pal.tree() != null && layout.treeAt((int) dx, (int) dz, u, v, TREE_DENSITY))
                    j.trees.add(new BlockPos(x, j.baseY + hh + 1, z));
                else if (forest[i][k] && slope < 2 && !railHere && j.pal.tree() == null && layout.decorHash(u, v) < layout.groveDensity())
                    j.groves.add(new Grove(new BlockPos(x, j.baseY + hh, z), layout.preset.theme == MapPreset.Theme.END
                        ? CHORUS : layout.groveWarped(u, v) ? WARPED : CRIMSON, (int) (layout.decorHash(u + 57, v) * 1000)));
                else if (layout.preset == MapPreset.DESERT_MESA && surf[gi][gk] == MapLayout.Surface.LAND && slope < 2
                        && layout.decorHash(u + 101, v) < 0.0025)
                    j.decor.add(new BlockPos(x, j.baseY + hh + 1, z));
                else if (j.mix != null && slope < 2 && !railHere && !forest[i][k] && growsOn(j, surf[gi][gk], slope)) {
                    int kind = plantAt(j.mix, layout, u, v);
                    if (kind >= 0) {
                        j.plants.add(new BlockPos(x, j.baseY + hh + 1, z));
                        j.plantKinds.add((byte) kind);
                    }
                }
            }
    }

    // ------------------------------------------------------------------------------------------ plant cover

    /**
     * Grass, ferns and flowers on the pre-made maps (lovish, Oct 16: "effects like wind blowing in the grass to make
     * the grass come to life ... it almost felt like the whole world was alive"). The maps were built bare - a tree
     * now and then on a lawn - so the shader's waving foliage (Complementary waves grass, ferns and flowers) had
     * nothing to move. Meadows grow where a smooth noise in slot frame is high (so the cover is n-fold symmetric like
     * the rest of the map: nobody's base sits in taller grass), mostly short grass that never hides a unit, tall grass
     * and ferns only in the densest middle of a meadow, flowers in their own drifts. Plants are soft blocks: a footprint
     * ignores them and clears them on placement (BuildingValidators.isSoftBlock), the cursor ray ignores them
     * (COLLIDER), fire does not spread to them (FireBlockMixin's flammables). Gamerule plantCover (default on).
     */
    record PlantMix(double grass, double tall, double fern, double flower, BlockState[] flowers) { }

    static final BlockState[] PLANTS = {
        Blocks.GRASS.defaultBlockState(), Blocks.TALL_GRASS.defaultBlockState(), Blocks.FERN.defaultBlockState(),
        Blocks.LARGE_FERN.defaultBlockState(),
        Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
        Blocks.AZURE_BLUET.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(), Blocks.ALLIUM.defaultBlockState()
    };
    static final int GRASS = 0, TALL = 1, FERN = 2, LARGE_FERN = 3, FLOWER0 = 4;

    static PlantMix mixFor(MapPreset preset) {
        BlockState[] meadow = { PLANTS[4], PLANTS[5], PLANTS[6], PLANTS[7], PLANTS[8] };
        return switch (preset) {
            case VERDANT_VALLEY, GENERATED -> new PlantMix(0.58, 0.08, 0.04, 0.05, meadow);
            case HIGHLANDS -> new PlantMix(0.42, 0.05, 0.16, 0.02, new BlockState[] { PLANTS[8], PLANTS[7], PLANTS[9] });
            case ARCHIPELAGO -> new PlantMix(0.40, 0.05, 0.04, 0.03, new BlockState[] { PLANTS[5], PLANTS[4], PLANTS[6] });
            case DESERT_MESA -> new PlantMix(0.45, 0.08, 0.08, 0.01, new BlockState[] { PLANTS[5] });   // the oases
            default -> null;   // nether, end: their own groves, no meadow
        };
    }

    /** Whether the column's top will be a grass block (what grass and flowers stand on). */
    private static boolean growsOn(Job j, MapLayout.Surface surface, int slope) {
        boolean steep = slope >= 3;
        BlockState top = switch (surface) {
            case GRASS -> steep ? j.pal.cliff() : j.pal.grass();
            case LAND -> steep ? j.pal.cliff() : j.pal.top();
            default -> null;
        };
        return top != null && top.is(Blocks.GRASS_BLOCK);
    }

    /** A smooth value noise in [0, 1] over slot frame (lattice from decorHash, smoothstep-blended). */
    static double plantNoise(MapLayout l, double u, double v, double scale, double salt) {
        double x = u / scale, y = v / scale;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = l.decorHash(x0 + salt, y0), b = l.decorHash(x0 + 1 + salt, y0);
        double c = l.decorHash(x0 + salt, y0 + 1), d = l.decorHash(x0 + 1 + salt, y0 + 1);
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    /** What grows at (u, v) in slot frame: an index into PLANTS, or -1 for bare grass. */
    static int plantAt(PlantMix mix, MapLayout l, double u, double v) {
        double meadow = 0.65 * plantNoise(l, u, v, 11.0, 0) + 0.35 * plantNoise(l, u, v, 4.0, 200);   // two octaves
        double dense = Math.max(0, Math.min(1, (meadow - 0.3) / 0.45));
        double pick = l.decorHash(u + 311, v);
        if (pick < mix.grass() * dense * (2 - dense)) {
            double h = l.decorHash(u + 733, v);
            if (meadow > 0.68 && h < mix.tall())
                return h < mix.tall() * 0.25 && mix.fern() > 0.1 ? LARGE_FERN : TALL;
            return h < mix.tall() + mix.fern() ? FERN : GRASS;
        }
        if (mix.flower() > 0 && plantNoise(l, u, v, 13.0, 500) > 0.7 && pick > 1 - mix.flower()) {
            BlockState f = mix.flowers()[(int) (l.decorHash(u + 977, v) * mix.flowers().length) % mix.flowers().length];
            for (int i = FLOWER0; i < PLANTS.length; i++)
                if (PLANTS[i] == f)
                    return i;
        }
        return -1;
    }

    /**
     * Plants go in last of the decoration, where the column is still bare grass with air above (a tree, a grove or a
     * cactus may have taken it), and never on the economy's ground: patch pads and extractor squares, capture sites
     * and vents are stamped after, so their surroundings stay clean.
     */
    private static int plant(Job j) {
        if (j.plants.isEmpty() || !j.level.getGameRules().getBoolean(
                com.solegendary.reignofnether.registrars.GameRuleRegistrar.PLANT_COVER))
            return 0;
        ServerLevel level = j.level;
        List<int[]> keepOut = new ArrayList<>();   // {x, z, half-size}
        for (MapLayout.Mex m : j.layout.mexes)
            keepOut.add(new int[] { j.cx + m.dx(), j.cz + m.dz(), 4 });
        for (MapLayout.Site st : j.layout.sites)
            keepOut.add(new int[] { j.cx + st.dx(), j.cz + st.dz(), 6 });
        for (MapLayout.Spot sp : j.layout.vents)
            keepOut.add(new int[] { j.cx + sp.dx(), j.cz + sp.dz(), 3 });
        for (MapLayout.Spot sp : j.layout.pillars)
            keepOut.add(new int[] { j.cx + sp.dx(), j.cz + sp.dz(), 3 });
        BlockState upper = Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState fernUpper = Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        int placed = 0;
        plants:
        for (int i = 0; i < j.plants.size(); i++) {
            BlockPos at = j.plants.get(i);
            for (int[] k : keepOut)
                if (Math.abs(at.getX() - k[0]) <= k[2] && Math.abs(at.getZ() - k[1]) <= k[2])
                    continue plants;
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK))
                continue;
            int kind = j.plantKinds.get(i);
            boolean dbl = kind == TALL || kind == LARGE_FERN;
            if (dbl && !level.getBlockState(at.above()).isAir())
                kind = kind == TALL ? GRASS : FERN;
            level.setBlock(at, PLANTS[kind], FLAGS);
            if (kind == TALL)
                level.setBlock(at.above(), upper, FLAGS);
            else if (kind == LARGE_FERN)
                level.setBlock(at.above(), fernUpper, FLAGS);
            placed++;
        }
        j.plants.clear();
        j.plantKinds.clear();
        return placed;
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
            var alt = j.layout.preset == MapPreset.VERDANT_VALLEY || j.layout.preset == MapPreset.GENERATED
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
        int plants = plant(j);
        ReignOfNether.LOGGER.info("[MapBuilder] {} trees, {} groves, {} decor, {} plants, {} pillars, {} vents", grown,
            j.groves.size(), j.decor.size(), plants, j.layout.pillars.size(), j.layout.vents.size());
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
