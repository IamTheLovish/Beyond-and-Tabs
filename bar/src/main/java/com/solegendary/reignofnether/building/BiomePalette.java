package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.building.buildings.placements.BridgePlacement;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.placements.SculkCatalystPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.FactionTraits;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Villages that change with the biome (lovish's vision list, Oct 11 #5; design/biome_bases.md). When a building is
 * placed, its blocks are run through a per-faction substitution table for the biome family at its site, the way a
 * vanilla village picks its plains / desert / savanna / taiga / snowy pieces - but with ONE structure per building and
 * the table applied at placement, so no structure is ever copied per biome.
 * <p>
 * Three layers, all shape-for-shape (a full block for a full block, stairs for stairs, a slab for a slab, a wall for a
 * wall, a log for a log with its axis, leaves for leaves; every property the two blocks share is copied), so the
 * footprint, the silhouette, the block count (HP, build time) and every walkable cell (pockets) stay exactly as drawn:
 * <ol>
 *   <li><b>Material swaps</b> per faction and family ({@link #swapOf}): a faction's <i>neutral</i> materials - generic
 *       stone, plinths, light timber trim, the ground it stands on - take the local look (sandstone in the desert, red
 *       sandstone in the badlands, granite on the savanna, mossy stone in the jungle, mud brick in the swamp, spruce in
 *       the north...). Its <i>identity</i> materials never move: team and faction colours (wool, banners, glass,
 *       terracotta), lights, metals (gold, brass, jade, quartz), the Gravebound's deepslate and bone, the Horde's
 *       nether, the Verdant's azalea canopy, the Tidewrought's prismarine and ship timber, the Stormcallers' lacquer.</li>
 *   <li><b>Weathering</b> ({@link #weatheringOf}): a few blocks age in place by climate - moss in the damp, cracks in
 *       the heat and frost, sandblasted or verdigris brass - on a deterministic share of positions, so two copies of a
 *       building in the same biome are not identical. It changes how old a material looks, never which material it is.</li>
 *   <li><b>Snow caps</b> (SNOWY only): a bare top (no block of the building directly on it) of ground, stone, timber,
 *       bricks or leaves turns to snow. Brass, gold and quartz stay bare: the boilers and the gilding shed snow.</li>
 * </ol>
 * The home family ({@link Family#TEMPERATE}) is the identity: a building in the plains is exactly the drawn structure.
 * <p>
 * The instant foundation queue stays the drawn one: BuildingServerEvents queues the foundation layers' blocks of a
 * starting type, and an empty queue makes a fresh site count as destroyed. A starting-type block the swap changes is
 * remembered by position ({@link BuildingPlacement#biomePaletteStarts}, read through {@link #joinsStartQueue}), and a
 * foundation block outside the queue never swaps INTO a starting type, so the queue holds exactly the drawn positions
 * in every family while the materials adapt everywhere.
 * <p>
 * Never swapped: the {@link #isProtected protected} blocks other code reads back by identity (the extractor and
 * graveyard tier markers, the Laboratory's rod on copper, the Castle's and markets' wool, chests, trap trigger plates,
 * metal-patch blocks...), as a source or as a target; and a ground block a plant stands on keeps a block the plant
 * survives on. Bridges, custom buildings, portals, beacons, farms and sculk are left alone (their placements read or
 * rewrite their own blocks).
 * <p>
 * Cost: the tables are built once, at class load, into identity maps per faction and family (no string work, no
 * allocation per block); a placement does one map lookup per block plus one position map for the caps and plants and
 * one small set for the queue (two allocations per placement, nothing per tick). The swap runs on the placement's own
 * block list ({@link BuildingPlacement#setBlocks}) on the server and on every client, from the exact noise biome at
 * the footprint's centre and a hash of each block's world position - so every side computes the same building. A
 * client that does not have the chunk yet defers it ({@link BuildingPlacement#biomePalettePending}, retried in its
 * tick).
 */
public final class BiomePalette {
    private BiomePalette() { }

    /**
     * The biome families a building adapts to: vanilla's five village types (plains = TEMPERATE, desert, savanna,
     * taiga, snowy) plus the jungle and swamp it has villager skins for, and the badlands. Order matters: it is the
     * column order of the tables below.
     */
    public enum Family { TEMPERATE, SNOWY, TAIGA, DESERT, BADLANDS, SAVANNA, JUNGLE, SWAMP }
    private static final int FAMILIES = Family.values().length;

    // Forge's common biome tags, made by name rather than through Tags.Biomes so a pack without them simply never
    // matches (modded biomes - Terralith, Biomes O' Plenty - tag themselves with these and still get the right family)
    private static final TagKey<Biome> FORGE_SNOWY = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", "is_snowy"));
    private static final TagKey<Biome> FORGE_DESERT = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", "is_desert"));
    private static final TagKey<Biome> FORGE_SWAMP = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", "is_swamp"));

    /** The family of the biome at pos (the exact noise biome, no fuzzing: the server and every client agree). */
    public static Family familyAt(LevelReader level, BlockPos pos) {
        return familyOf(level.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
            QuartPos.fromBlock(pos.getZ())));
    }

    /**
     * Tags first (vanilla's, then Forge's for modded biomes), climate as the fallback: snow falls below 0.15 (snowy
     * plains / taiga / slopes, groves, peaks, ice spikes), the cold forests and windswept hills sit below 0.3, and
     * hot biomes without rain are deserts. The Nether and the End keep the home palette.
     */
    public static Family familyOf(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_NETHER) || b.is(BiomeTags.IS_END))
            return Family.TEMPERATE;
        if (b.is(BiomeTags.IS_BADLANDS))
            return Family.BADLANDS;
        float t = b.value().getBaseTemperature();
        if (b.is(FORGE_SNOWY) || t < 0.15f)
            return Family.SNOWY;
        if (b.is(BiomeTags.IS_JUNGLE) || b.is(Biomes.LUSH_CAVES))
            return Family.JUNGLE;
        if (b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP) || b.is(FORGE_SWAMP))
            return Family.SWAMP;
        if (b.is(BiomeTags.IS_SAVANNA))
            return Family.SAVANNA;
        if (b.is(BiomeTags.IS_TAIGA) || t < 0.3f)
            return Family.TAIGA;
        if (b.is(Biomes.DESERT) || b.is(FORGE_DESERT) || (t >= 1.5f && !b.value().hasPrecipitation()))
            return Family.DESERT;
        return Family.TEMPERATE;
    }

    /** True if this placement takes biome swaps at all. */
    static boolean applies(BuildingPlacement p) {
        Building b = p.getBuilding();
        return !(b instanceof AbstractBridge) && !(b instanceof CustomBuilding) && !(p instanceof BridgePlacement)
            && !(p instanceof PortalPlacement) && !(p instanceof BeaconPlacement) && !(p instanceof FarmPlacement)
            && !(p instanceof SculkCatalystPlacement)
            && TABLES.containsKey(FactionTraits.of(b.getFaction()));
    }

    /**
     * Swaps the placement's blocks for its biome, or (a client whose chunk isn't loaded yet) marks it pending.
     * Called from BuildingPlacement.setBlocks and, while pending, from its client tick.
     */
    static void applyOrDefer(BuildingPlacement p) {
        p.biomePalettePending = false;
        p.biomePaletteStarts = null;
        Level level = p.getLevel();
        if (level == null || p.originPos == null || p.blocks == null || p.blocks.isEmpty() || !applies(p))
            return;
        BlockPos at = sitePos(p.blocks, p.originPos);
        if (level.isClientSide() && !level.hasChunkAt(at)) {
            p.biomePalettePending = true;
            return;
        }
        LongOpenHashSet starts = new LongOpenHashSet();
        apply(p.blocks, p.getBuilding(), familyAt(level, at), starts);
        p.biomePaletteStarts = starts.isEmpty() ? null : starts;
    }

    /**
     * True if this block of a fresh site joins its instant foundation queue (BuildingServerEvents asks for every
     * non-air block of the foundation layers): a block of a starting type, or one the biome swap changed from a starting
     * type (swappedStarts: {@link BuildingPlacement#biomePaletteStarts}, null when nothing was). Since a foundation block
     * never swaps into a starting type, this is exactly the drawn structure's queue.
     */
    public static boolean joinsStartQueue(Building building, BuildingBlock bb, LongSet swappedStarts) {
        return building.startingBlockTypes.contains(bb.getBlockState().getBlock())
            || (swappedStarts != null && swappedStarts.contains(bb.getBlockPos().asLong()));
    }

    /**
     * Where a building's biome is read: the middle of its footprint, one block above its foundation. The origin is a
     * corner, and a footprint straddling a biome border should follow the ground most of it stands on.
     */
    static BlockPos sitePos(List<BuildingBlock> blocks, BlockPos origin) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BuildingBlock bb : blocks) {
            BlockPos bp = bb.getBlockPos();
            minX = Math.min(minX, bp.getX());
            maxX = Math.max(maxX, bp.getX());
            minZ = Math.min(minZ, bp.getZ());
            maxZ = Math.max(maxZ, bp.getZ());
        }
        return new BlockPos((minX + maxX) >> 1, origin.getY() + 1, (minZ + maxZ) >> 1);
    }

    /** {@link #apply(List, Building, Family, LongSet)} without recording the swapped starting blocks. */
    public static int apply(List<BuildingBlock> blocks, Building building, Family family) {
        return apply(blocks, building, family, null);
    }

    /**
     * Swaps blocks in place for this building and family; returns how many changed. Public for the GameTests.
     * Positions never change and every swap keeps the block's shape, so the list's footprint and counts stay put.
     * swappedStarts (if not null) receives the position of every foundation block of a starting type that changed
     * (see {@link #joinsStartQueue}); what is swapped never depends on it, so every side computes the same building.
     */
    public static int apply(List<BuildingBlock> blocks, Building building, Family family, LongSet swappedStarts) {
        if (family == Family.TEMPERATE || blocks.isEmpty())
            return 0;
        Table table = TABLES.get(FactionTraits.of(building.getFaction()));
        if (table == null)
            return 0;
        Map<Block, Block> swap = table.swap.get(family);
        Map<Block, Weathering[]> weather = WEATHER.get(family);
        boolean caps = family == Family.SNOWY;
        // the building's own non-air blocks by position: a cap needs nothing of the building on top, and a block a
        // plant stands on must stay one it survives on (one map per placement - nothing per tick)
        Long2ObjectOpenHashMap<BlockState> solid = new Long2ObjectOpenHashMap<>(blocks.size());
        int minY = Integer.MAX_VALUE;
        for (BuildingBlock bb : blocks) {
            minY = Math.min(minY, bb.getBlockPos().getY());
            if (!bb.getBlockState().isAir())
                solid.put(bb.getBlockPos().asLong(), bb.getBlockState());
        }
        // the layers BuildingServerEvents takes its instant foundation queue from (same minY: every block, air too)
        int foundationTop = minY + building.foundationYLayers - 1;
        List<Block> start = building.startingBlockTypes;
        int changed = 0;
        for (BuildingBlock bb : blocks) {
            BlockState bs = bb.getBlockState();
            if (bs.isAir())
                continue;
            Block block = bs.getBlock();
            boolean foundation = bb.getBlockPos().getY() <= foundationTop;
            boolean queued = foundation && start.contains(block);
            if (PROTECTED.contains(block))
                continue;
            Block to = swap.getOrDefault(block, block);
            long key = bb.getBlockPos().asLong();
            Weathering[] ws = weather.get(to);
            if (ws != null)
                for (Weathering w : ws)
                    if (hash01(key, w.salt()) < w.rate()) {
                        to = w.to();
                        break;
                    }
            BlockState above = solid.get(BlockPos.offset(key, 0, 1, 0));
            if (caps && above == null && (CAPPED.contains(to) || CAPPED.contains(block) || bs.is(BlockTags.LEAVES)))
                to = Blocks.SNOW_BLOCK;
            // the tables never name a protected block as a target (biome_palette_tables_keep_shape_and_identity), and
            // a foundation block outside the queue swapped into a starting type would quietly join it: both stay as drawn
            if (to == block || (foundation && !queued && start.contains(to)) || PROTECTED.contains(to))
                continue;
            BlockState swapped = copyProperties(bs, to.defaultBlockState());
            // a flower, grass or sapling on this block pops off unless the new block is still soil for it
            if (above != null && above.getBlock() instanceof BushBlock && !swapped.is(BlockTags.DIRT))
                continue;
            bb.setBlockState(swapped);
            changed++;
            if (queued && swappedStarts != null)
                swappedStarts.add(key);
        }
        return changed;
    }

    /** to with every property of from that it also has (stair facing/half/shape, slab type, axis, waterlogged...). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProperties(BlockState from, BlockState to) {
        for (Property prop : from.getProperties())
            if (to.hasProperty(prop))
                to = to.setValue(prop, from.getValue(prop));
        return to;
    }

    /** A deterministic number in [0, 1) for a block position and a salt: the same on the server and every client. */
    static float hash01(long pos, int salt) {
        long h = pos * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) * 0x1.0p-24f;
    }

    // ------------------------------------------------------------------ queries (GameTests, tools, the design doc)

    /** The block this faction's buildings use instead of {@code block} in this family (the block itself if none). */
    public static Block swapOf(Faction faction, Family family, Block block) {
        Table t = TABLES.get(FactionTraits.of(faction));
        return t == null ? block : t.swap.get(family).getOrDefault(block, block);
    }

    /** Every material swap of one faction in one family, read-only (empty for factions without a table). */
    public static Map<Block, Block> swapsOf(FactionTraits traits, Family family) {
        Table t = TABLES.get(traits);
        return t == null ? Map.of() : Collections.unmodifiableMap(t.swap.get(family));
    }

    /** The factions that have a table (the seven playable ones). */
    public static Set<FactionTraits> factions() {
        return Collections.unmodifiableSet(TABLES.keySet());
    }

    /** A faction's own identity blocks (never swapped), read-only. */
    public static Set<Block> keepsOf(FactionTraits traits) {
        Table t = TABLES.get(traits);
        return t == null ? Set.of() : Collections.unmodifiableSet(t.keep);
    }

    /** Every block a faction swaps in at least one family, read-only. */
    public static Set<Block> sourcesOf(FactionTraits traits) {
        Table t = TABLES.get(traits);
        return t == null ? Set.of() : Collections.unmodifiableSet(t.sources);
    }

    /** One way a block ages in a family: into {@code to} on a {@code rate} share of positions. */
    public record Weathering(Block to, float rate, int salt) { }

    /** Every weathering of a family as {from, to} pairs (shared by all factions), for the shape test. */
    public static List<Block[]> weatheringOf(Family family) {
        List<Block[]> out = new ArrayList<>();
        WEATHER.get(family).forEach((from, ws) -> {
            for (Weathering w : ws)
                out.add(new Block[] { from, w.to() });
        });
        return out;
    }

    /** The block a bare top turns into in this family, or null (only the snowy family caps). */
    public static Block capOf(Family family) {
        return family == Family.SNOWY ? Blocks.SNOW_BLOCK : null;
    }

    /** True for a block that takes a snow cap in the snowy family when nothing of the building sits on it. */
    public static boolean takesSnowCap(BlockState state) {
        return CAPPED.contains(state.getBlock()) || state.is(BlockTags.LEAVES);
    }

    /** A block other code reads back by identity: never swapped, weathered or capped, and never swapped into. */
    public static boolean isProtected(Block block) {
        return PROTECTED.contains(block);
    }

    /**
     * Whether the table has decided what this faction's {@code block} becomes in every family: a shared identity
     * block (lights, colours, glass, metals, devices, plants, the nether and the deep, sea stone...), one of the
     * faction's own identity blocks, a block it swaps, or a block from another mod (never swapped: unknown to us).
     * The coverage GameTest fails on any block a faction's structures use that is none of these - a new material in a
     * structure needs a decision, not a silent default.
     */
    public static boolean isDecided(Faction faction, Block block) {
        Table t = TABLES.get(FactionTraits.of(faction));
        if (t == null)
            return true;
        if (block == Blocks.AIR || isSharedKeep(block) || t.keep.contains(block) || t.sources.contains(block))
            return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && !"minecraft".equals(id.getNamespace());
    }

    /** One of the materials every faction keeps everywhere (see {@link #isDecided}). */
    public static boolean isSharedKeep(Block b) {
        return SHARED_KEEP.contains(b) || PROTECTED.contains(b) || b instanceof EntityBlock || b instanceof BushBlock
            || b instanceof ButtonBlock || b instanceof BasePressurePlateBlock || b instanceof DoorBlock
            || b instanceof LadderBlock || b instanceof BaseRailBlock || b instanceof IronBarsBlock || b instanceof ChainBlock
            || b instanceof AbstractCauldronBlock || b instanceof FlowerPotBlock || b instanceof BaseCoralPlantTypeBlock
            || b instanceof CandleBlock || b instanceof CarpetBlock || b instanceof InfestedBlock || b instanceof AnvilBlock
            || b instanceof CraftingTableBlock || b instanceof BaseFireBlock || b instanceof StainedGlassBlock
            || b instanceof GlazedTerracottaBlock;
    }

    // ------------------------------------------------------------------ the tables

    /** One faction's decisions: per family the material swaps; its identity blocks; every block it swaps somewhere. */
    private static final class Table {
        final EnumMap<Family, Map<Block, Block>> swap = new EnumMap<>(Family.class);
        final Set<Block> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Block> sources = Collections.newSetFromMap(new IdentityHashMap<>());

        Table() {
            for (Family f : Family.values())
                swap.put(f, new IdentityHashMap<>());
        }

        /**
         * Material {@code from} becomes {@code to[family]} slot by slot (null = this family keeps it). Rows are aligned
         * by shape (see the row constants); a slot either row lacks is left as it is.
         */
        Table rule(Block[] from, Block[]... to) {
            if (to.length != FAMILIES)
                throw new IllegalArgumentException("a rule needs one target per family, got " + to.length);
            for (int f = 0; f < FAMILIES; f++) {
                if (to[f] == null)
                    continue;
                for (int i = 0; i < from.length && i < to[f].length; i++) {
                    if (from[i] == null || to[f][i] == null || from[i] == to[f][i])
                        continue;
                    swap.get(Family.values()[f]).put(from[i], to[f][i]);
                }
            }
            for (Block b : from)
                if (b != null)
                    sources.add(b);
            return this;
        }

        Table rules(Block[][] froms, Block[]... to) {
            for (Block[] from : froms)
                rule(from, to);
            return this;
        }

        Table keep(Block[]... rows) {
            for (Block[] row : rows)
                for (Block b : row)
                    if (b != null)
                        keep.add(b);
            return this;
        }
    }

    // Rows. Stone rows are {full, stairs, slab, wall}; wood rows {planks, stairs, slab, fence, fence gate, trapdoor,
    // log, wood, stripped log, stripped wood, leaves}; ground rows a single block. A null slot: the material has none.
    private static Block[] row(Block... blocks) {
        return blocks;
    }

    static final Block[] COBBLE = row(Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL);
    static final Block[] MOSSY_COBBLE = row(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_WALL);
    static final Block[] STONE_BRICKS = row(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL);
    static final Block[] MOSSY_STONE_BRICKS = row(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_WALL);
    static final Block[] STONE = row(Blocks.STONE, Blocks.STONE_STAIRS, Blocks.STONE_SLAB, null);
    static final Block[] SMOOTH_STONE = row(Blocks.SMOOTH_STONE, null, Blocks.SMOOTH_STONE_SLAB, null);
    static final Block[] ANDESITE = row(Blocks.ANDESITE, Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_SLAB, Blocks.ANDESITE_WALL);
    static final Block[] POLISHED_ANDESITE = row(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_SLAB, null);
    static final Block[] DIORITE = row(Blocks.DIORITE, Blocks.DIORITE_STAIRS, Blocks.DIORITE_SLAB, Blocks.DIORITE_WALL);
    static final Block[] POLISHED_DIORITE = row(Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_WALL);
    static final Block[] GRANITE = row(Blocks.GRANITE, Blocks.GRANITE_STAIRS, Blocks.GRANITE_SLAB, Blocks.GRANITE_WALL);
    static final Block[] POLISHED_GRANITE = row(Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE_STAIRS, Blocks.POLISHED_GRANITE_SLAB, Blocks.GRANITE_WALL);
    static final Block[] SANDSTONE = row(Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL);
    static final Block[] SMOOTH_SANDSTONE = row(Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL);
    static final Block[] CUT_SANDSTONE = row(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL);
    static final Block[] RED_SANDSTONE = row(Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_WALL);
    static final Block[] SMOOTH_RED_SANDSTONE = row(Blocks.SMOOTH_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_WALL);
    static final Block[] CUT_RED_SANDSTONE = row(Blocks.CUT_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.CUT_RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_WALL);
    static final Block[] MUD_BRICKS = row(Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL);
    static final Block[] PACKED_MUD = row(Blocks.PACKED_MUD, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL);
    // a northern harbour's quay: grey stone where the south has sand
    static final Block[] QUAY = row(Blocks.SMOOTH_STONE, Blocks.STONE_STAIRS, Blocks.SMOOTH_STONE_SLAB, Blocks.COBBLESTONE_WALL);
    static final Block[] QUAY_ROUGH = row(Blocks.STONE, Blocks.STONE_STAIRS, Blocks.STONE_SLAB, Blocks.COBBLESTONE_WALL);
    static final Block[] CHISELED_STONE_BRICKS = row(Blocks.CHISELED_STONE_BRICKS);
    static final Block[] CRACKED_STONE_BRICKS = row(Blocks.CRACKED_STONE_BRICKS);

    private static Block[] wood(Block planks, Block stairs, Block slab, Block fence, Block gate, Block trapdoor,
                                Block log, Block wood, Block strippedLog, Block strippedWood, Block leaves) {
        return row(planks, stairs, slab, fence, gate, trapdoor, log, wood, strippedLog, strippedWood, leaves);
    }

    static final Block[] OAK = wood(Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE,
        Blocks.OAK_TRAPDOOR, Blocks.OAK_LOG, Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_OAK_WOOD, Blocks.OAK_LEAVES);
    static final Block[] SPRUCE = wood(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE,
        Blocks.SPRUCE_TRAPDOOR, Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.SPRUCE_LEAVES);
    static final Block[] BIRCH = wood(Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE_GATE,
        Blocks.BIRCH_TRAPDOOR, Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD, Blocks.BIRCH_LEAVES);
    static final Block[] JUNGLE = wood(Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_FENCE_GATE,
        Blocks.JUNGLE_TRAPDOOR, Blocks.JUNGLE_LOG, Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_WOOD, Blocks.JUNGLE_LEAVES);
    static final Block[] ACACIA = wood(Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE, Blocks.ACACIA_FENCE_GATE,
        Blocks.ACACIA_TRAPDOOR, Blocks.ACACIA_LOG, Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_ACACIA_WOOD, Blocks.ACACIA_LEAVES);
    static final Block[] DARK_OAK = wood(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE_GATE,
        Blocks.DARK_OAK_TRAPDOOR, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_WOOD, Blocks.DARK_OAK_LEAVES);
    static final Block[] MANGROVE = wood(Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_FENCE_GATE,
        Blocks.MANGROVE_TRAPDOOR, Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_WOOD, Blocks.MANGROVE_LEAVES);
    static final Block[] CHERRY = wood(Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_FENCE, Blocks.CHERRY_FENCE_GATE,
        Blocks.CHERRY_TRAPDOOR, Blocks.CHERRY_LOG, Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_CHERRY_WOOD, Blocks.CHERRY_LEAVES);
    // the desert's timber: sun-bleached birch, its bark stripped (white birch bark reads as a different material)
    static final Block[] SUN_BLEACHED = wood(Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE_GATE,
        Blocks.BIRCH_TRAPDOOR, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD, Blocks.BIRCH_LEAVES);
    static final Block[] AZALEA_LEAVES = row(Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES);
    static final Block[] BAMBOO_TRIM = row(Blocks.BAMBOO_MOSAIC, Blocks.BAMBOO_MOSAIC_STAIRS, Blocks.BAMBOO_MOSAIC_SLAB, Blocks.BAMBOO_PLANKS,
        Blocks.BAMBOO_STAIRS, Blocks.BAMBOO_SLAB, Blocks.BAMBOO_FENCE, Blocks.BAMBOO_FENCE_GATE, Blocks.BAMBOO_TRAPDOOR, Blocks.BAMBOO_BLOCK,
        Blocks.STRIPPED_BAMBOO_BLOCK);

    static final Block[] GRASS = row(Blocks.GRASS_BLOCK);
    static final Block[] DIRT = row(Blocks.DIRT);
    static final Block[] COARSE_DIRT = row(Blocks.COARSE_DIRT);
    static final Block[] PODZOL = row(Blocks.PODZOL);
    static final Block[] ROOTED_DIRT = row(Blocks.ROOTED_DIRT);
    static final Block[] MOSS = row(Blocks.MOSS_BLOCK);
    static final Block[] SAND_FLOOR = row(Blocks.SMOOTH_SANDSTONE);   // packed sand that never falls
    static final Block[] MESA_FLOOR = row(Blocks.TERRACOTTA);
    static final Block[] MUD_FLOOR = row(Blocks.PACKED_MUD);

    // Shared targets, one per family in Family order: TEMPERATE, SNOWY, TAIGA, DESERT, BADLANDS, SAVANNA, JUNGLE, SWAMP.
    // Light timber: spruce in the north (vanilla's snowy and taiga villages), sun-bleached birch in the desert,
    // acacia on the savanna and in the badlands, jungle wood in the jungle, mangrove in the swamp.
    private static final Block[][] TIMBER = { null, SPRUCE, SPRUCE, SUN_BLEACHED, ACACIA, ACACIA, JUNGLE, MANGROVE };
    // The Verdant Court grows the land's own trees: palm-like jungle trunks in the desert, oak in the (wooded) badlands.
    private static final Block[][] LIVING_WOOD = { null, SPRUCE, SPRUCE, JUNGLE, null, ACACIA, JUNGLE, MANGROVE };
    // Generic masonry: sandstone in the desert, red sandstone in the badlands, granite on the savanna, moss in the
    // jungle, mud brick in the swamp; the north keeps grey stone (snow and moss do the work there).
    private static final Block[][] BRICKWORK = { null, null, null, CUT_SANDSTONE, CUT_RED_SANDSTONE, POLISHED_GRANITE, MOSSY_STONE_BRICKS, MUD_BRICKS };
    private static final Block[][] MOSSY_BRICKWORK = { null, null, null, CUT_SANDSTONE, CUT_RED_SANDSTONE, POLISHED_GRANITE, null, MUD_BRICKS };
    private static final Block[][] RUBBLE = { null, null, null, SANDSTONE, RED_SANDSTONE, GRANITE, MOSSY_COBBLE, MOSSY_COBBLE };
    private static final Block[][] MOSSY_RUBBLE = { null, null, null, SANDSTONE, RED_SANDSTONE, GRANITE, null, null };
    private static final Block[][] RAW_STONE = { null, null, null, SANDSTONE, RED_SANDSTONE, GRANITE, null, null };
    // plinths and paving: white marble (diorite) under snow
    private static final Block[][] SMOOTH_PAVING = { null, POLISHED_DIORITE, null, SMOOTH_SANDSTONE, SMOOTH_RED_SANDSTONE, POLISHED_GRANITE, null, null };
    private static final Block[][] ROUGH_PAVING = { null, DIORITE, null, SANDSTONE, RED_SANDSTONE, GRANITE, null, null };
    private static final Block[][] CHISELED = { null, null, null, row(Blocks.CHISELED_SANDSTONE), row(Blocks.CHISELED_RED_SANDSTONE), null, null, null };
    private static final Block[][] CRACKED = { null, null, null, row(Blocks.CUT_SANDSTONE), row(Blocks.CUT_RED_SANDSTONE),
        row(Blocks.POLISHED_GRANITE), row(Blocks.MOSSY_STONE_BRICKS), row(Blocks.MUD_BRICKS) };
    // The ground a building stands on (its yards and planters): forest floor in the taiga, packed sand, mesa clay,
    // savanna dirt, moss in the jungle and swamp. Under a plant only soil targets apply (see apply()).
    private static final Block[][] GRASS_GROUND = { null, null, PODZOL, SAND_FLOOR, MESA_FLOOR, COARSE_DIRT, MOSS, MOSS };
    private static final Block[][] DIRT_GROUND = { null, null, COARSE_DIRT, SAND_FLOOR, MESA_FLOOR, COARSE_DIRT, ROOTED_DIRT, MUD_FLOOR };
    private static final Block[][] COARSE_GROUND = { null, null, null, SAND_FLOOR, MESA_FLOOR, null, ROOTED_DIRT, MUD_FLOOR };
    private static final Block[][] DARK_GROUND = { null, null, null, SAND_FLOOR, MESA_FLOOR, COARSE_DIRT, null, null };
    private static final Block[][] ROOTED_GROUND = { null, null, null, SAND_FLOOR, MESA_FLOOR, COARSE_DIRT, null, MUD_FLOOR };
    // a harbour's apron and quay: the local shore - grey stone in the north, red sand, mud flats
    private static final Block[][] SHORE = { null, QUAY, QUAY, null, SMOOTH_RED_SANDSTONE, null, null, PACKED_MUD };
    private static final Block[][] ROUGH_SHORE = { null, QUAY_ROUGH, QUAY_ROUGH, null, RED_SANDSTONE, null, null, MUD_BRICKS };
    private static final Block[][] SANDSTONE_PAVING = { null, POLISHED_DIORITE, null, null, SMOOTH_RED_SANDSTONE, POLISHED_GRANITE, null, null };

    /** The neutral materials most factions build their plinths, walls and trim from. */
    private static Table masonry(Table t) {
        return t.rule(STONE_BRICKS, BRICKWORK).rule(MOSSY_STONE_BRICKS, MOSSY_BRICKWORK)
            .rule(COBBLE, RUBBLE).rule(MOSSY_COBBLE, MOSSY_RUBBLE).rule(STONE, RAW_STONE)
            .rule(SMOOTH_STONE, SMOOTH_PAVING).rule(ANDESITE, ROUGH_PAVING).rule(POLISHED_ANDESITE, SMOOTH_PAVING)
            .rule(CHISELED_STONE_BRICKS, CHISELED).rule(CRACKED_STONE_BRICKS, CRACKED);
    }

    private static Table ground(Table t) {
        return t.rule(GRASS, GRASS_GROUND).rule(DIRT, DIRT_GROUND).rule(COARSE_DIRT, COARSE_GROUND);
    }

    private static final Map<FactionTraits, Table> TABLES = new IdentityHashMap<>();

    static {
        // Sunforged Kingdom: white quartz, gold and blue are the kingdom; its stone courts, timber and yards are local.
        // Polished granite (the Town Centre's cross) and polished diorite are accents, dark oak the trim that stays dark.
        TABLES.put(FactionTraits.SUNFORGED, ground(masonry(new Table()))
            .rules(new Block[][] { OAK, SPRUCE, BIRCH, JUNGLE }, TIMBER)
            .rule(PODZOL, DARK_GROUND).rule(ROOTED_DIRT, ROOTED_GROUND).rule(MOSS, DARK_GROUND)
            .keep(DARK_OAK, POLISHED_GRANITE, POLISHED_DIORITE, BAMBOO_TRIM, row(Blocks.MANGROVE_SLAB, Blocks.GRANITE, Blocks.DIORITE)));
        // Gravebound: deepslate, blackstone, bone, soul fire and dark oak are the crypt; the RoN crypts' grey stone,
        // the sheds' spruce and the yards adapt. Grave soil (podzol) and the slime pit's moss stay.
        TABLES.put(FactionTraits.GRAVEBOUND, ground(masonry(new Table()))
            .rules(new Block[][] { SPRUCE, OAK, BIRCH }, TIMBER)
            .rule(ROOTED_DIRT, ROOTED_GROUND)
            .keep(DARK_OAK, PODZOL, MOSS));
        // Ironhide Horde: the Horde brings the Nether with it - every block is nether stone, gilded blackstone, basalt
        // or red wood, which no biome changes. It still takes the snow and the sun-cracks, and its Nether Zones (RoN)
        // terraform the ground around its portals.
        TABLES.put(FactionTraits.HORDE, new Table()
            .keep(MANGROVE, BAMBOO_TRIM));
        // Verdant Court: the azalea canopy, moss, mossy stone, calcite moonwells, silver birch and the spirit tree's
        // cherry are the Court; its living trunks grow the local species.
        TABLES.put(FactionTraits.VERDANT, new Table()
            .rule(OAK, LIVING_WOOD)
            .keep(AZALEA_LEAVES, MOSS, ROOTED_DIRT, MOSSY_STONE_BRICKS, MOSSY_COBBLE, BIRCH, CHERRY, DARK_OAK, GRASS, PODZOL));
        // Tidewrought: prismarine, waxed copper and the wreck timber (spruce, mangrove, dark oak) are the fleet; the
        // sandstone apron is the local shore.
        TABLES.put(FactionTraits.TIDEWROUGHT, new Table()
            .rule(SMOOTH_SANDSTONE, SHORE).rule(SANDSTONE, ROUGH_SHORE).rule(CUT_SANDSTONE, SHORE)
            .keep(SPRUCE, MANGROVE, DARK_OAK));
        // Stormcallers: red lacquer, jade roofs, gold and green glaze are the dynasty; the grey plinths and courts are
        // local stone (white marble under snow), the sheds' spruce local timber. Cherry and dark oak stay.
        TABLES.put(FactionTraits.STORMCALLERS, masonry(new Table())
            .rule(SPRUCE, TIMBER).rule(OAK, TIMBER)
            .rule(SMOOTH_SANDSTONE, SANDSTONE_PAVING)
            .keep(CHERRY, DARK_OAK, BAMBOO_TRIM));
        // Brassworks: brass, soot (blackstone, deepslate bricks), brick stacks and furnace orange are the guild and
        // stay everywhere; the climate weathers the brass (sandblasted, verdigris) and cracks the soot.
        TABLES.put(FactionTraits.BRASSWORKS, new Table()
            .keep(DARK_OAK));
    }

    /** Ages blocks in place, shared by every faction (see the class comment). Salts keep the shares independent. */
    private static final EnumMap<Family, Map<Block, Weathering[]>> WEATHER = new EnumMap<>(Family.class);

    private static void weather(Family family, Block[] from, Block[] to, float rate, int salt) {
        if (rate <= 0)
            return;
        Map<Block, Weathering[]> m = WEATHER.get(family);
        for (int i = 0; i < from.length && i < to.length; i++) {
            if (from[i] == null || to[i] == null)
                continue;
            Weathering[] old = m.get(from[i]);
            Weathering w = new Weathering(to[i], rate, salt);
            if (old == null) {
                m.put(from[i], new Weathering[] { w });
            } else {
                Weathering[] grown = java.util.Arrays.copyOf(old, old.length + 1);
                grown[old.length] = w;
                m.put(from[i], grown);
            }
        }
    }

    static final Block[] CUT_COPPER = row(Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER_SLAB);
    static final Block[] EXPOSED_COPPER = row(Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB);
    static final Block[] WEATHERED_COPPER = row(Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB);
    static final Block[] CRACKABLE = row(Blocks.STONE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.NETHER_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
    static final Block[] CRACKED_VARIANTS = row(Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.CRACKED_NETHER_BRICKS,
        Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);

    static {
        for (Family f : Family.values())
            WEATHER.put(f, new IdentityHashMap<>());
        // shares per family:            TEMPERATE SNOWY TAIGA DESERT BADLANDS SAVANNA JUNGLE SWAMP
        float[] moss =   { 0f, 0f,    0.25f, 0f,    0f,    0f,    0.30f, 0.30f };   // damp forests
        float[] crack =  { 0f, 0.08f, 0.04f, 0.12f, 0.14f, 0.08f, 0f,    0f };      // frost and sun
        float[] blast =  { 0f, 0f,    0f,    0.20f, 0.20f, 0f,    0f,    0f };      // sandblasted brass
        float[] patina = { 0f, 0f,    0f,    0f,    0f,    0f,    0.18f, 0.22f };   // verdigris in the wet
        for (Family f : Family.values()) {
            int i = f.ordinal();
            weather(f, STONE_BRICKS, MOSSY_STONE_BRICKS, moss[i], 1);
            weather(f, COBBLE, MOSSY_COBBLE, moss[i], 1);
            weather(f, CRACKABLE, CRACKED_VARIANTS, crack[i], 2);
            weather(f, CUT_COPPER, EXPOSED_COPPER, blast[i], 3);
            weather(f, CUT_COPPER, WEATHERED_COPPER, patina[i], 3);
        }
    }

    /** What snow settles on (bare tops only): ground, stone, timber, bricks. Brass, gold and quartz stay bare. */
    private static final Set<Block> CAPPED = Collections.newSetFromMap(new IdentityHashMap<>());
    static {
        Collections.addAll(CAPPED, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT,
            Blocks.MOSS_BLOCK, Blocks.PACKED_MUD, Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
            Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.ANDESITE,
            Blocks.POLISHED_ANDESITE, Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.SMOOTH_STONE,
            Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS,
            Blocks.DARK_OAK_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS,
            Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
            Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.BLACKSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.BRICKS, Blocks.MUD_BRICKS,
            Blocks.TERRACOTTA, Blocks.DRIED_KELP_BLOCK, Blocks.SOUL_SOIL);
    }

    /**
     * Blocks other code identifies a building by (never swapped from or to): MetalExtractor's tier marker (copper
     * block / cut copper / waxed copper block), Graveyard's (deepslate tiles / chiseled deepslate), the Laboratory's
     * rod on waxed copper, the Castle's and Villager Market's wool, the Blacksmith's blast furnace, the Library's
     * glowstone, the markets' brewing stand and crimson stairs, chests (stockpiles, portals), the beacon and its base
     * metals, sculk, and the metal-patch blocks (raw iron, raw gold, gold). Trap trigger plates are pressure plates,
     * also kept (see isSharedKeep).
     */
    private static final Set<Block> PROTECTED = Collections.newSetFromMap(new IdentityHashMap<>());
    static {
        Collections.addAll(PROTECTED, Blocks.COPPER_BLOCK, Blocks.CUT_COPPER, Blocks.WAXED_COPPER_BLOCK,
            Blocks.DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE, Blocks.LIGHTNING_ROD, Blocks.WHITE_WOOL, Blocks.RED_WOOL,
            Blocks.LIGHT_GRAY_WOOL, Blocks.YELLOW_WOOL, Blocks.BLAST_FURNACE, Blocks.GLOWSTONE, Blocks.BREWING_STAND,
            Blocks.CRIMSON_STAIRS, Blocks.CHEST, Blocks.BEACON, Blocks.NETHERITE_BLOCK, Blocks.DIAMOND_BLOCK,
            Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.SCULK_CATALYST, Blocks.RAW_IRON_BLOCK,
            Blocks.RAW_GOLD_BLOCK);
    }

    /**
     * Identity materials every faction keeps in every biome (on top of the block classes in isSharedKeep): colours,
     * lights, glass, metals and precious blocks, the nether and the end, the deep (deepslate, tuff, calcite), sea stone,
     * bricks and mud, quartz, bone and hay, snow and ice, natural decorations, and the non-full blocks a swap could not
     * keep the shape of (paths, farmland, mud).
     */
    private static final Set<Block> SHARED_KEEP = Collections.newSetFromMap(new IdentityHashMap<>());
    static {
        for (DyeColor c : DyeColor.values())
            for (String suffix : new String[] { "_wool", "_terracotta", "_concrete", "_concrete_powder", "_stained_glass_pane" }) {
                Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", c.getName() + suffix));
                if (b != Blocks.AIR)
                    SHARED_KEEP.add(b);
            }
        Collections.addAll(SHARED_KEEP,
            // lights
            Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
            Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.END_ROD, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT,
            Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT, Blocks.REDSTONE_LAMP,
            Blocks.MAGMA_BLOCK, Blocks.JACK_O_LANTERN, Blocks.LAVA, Blocks.WATER,
            // colour and glass
            Blocks.TERRACOTTA, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS,
            // metals and precious blocks
            Blocks.COAL_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.LAPIS_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST,
            Blocks.RAW_COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
            Blocks.EXPOSED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER,
            Blocks.CUT_COPPER_STAIRS, Blocks.EXPOSED_CUT_COPPER_STAIRS, Blocks.WEATHERED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER_STAIRS,
            Blocks.CUT_COPPER_SLAB, Blocks.EXPOSED_CUT_COPPER_SLAB, Blocks.WEATHERED_CUT_COPPER_SLAB, Blocks.OXIDIZED_CUT_COPPER_SLAB,
            Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER, Blocks.WAXED_OXIDIZED_COPPER,
            Blocks.WAXED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER,
            Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS,
            Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER_SLAB, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB,
            Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB,
            Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_PILLAR, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ,
            Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_SLAB,
            Blocks.IRON_TRAPDOOR,
            // devices without a block entity (the cartography table is not a CraftingTableBlock, unlike the fletching
            // and smithing tables isSharedKeep catches by class)
            Blocks.BOOKSHELF, Blocks.LOOM, Blocks.GRINDSTONE, Blocks.STONECUTTER, Blocks.COMPOSTER, Blocks.TARGET,
            Blocks.CARTOGRAPHY_TABLE,
            Blocks.NOTE_BLOCK, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.TNT, Blocks.LODESTONE, Blocks.RESPAWN_ANCHOR,
            Blocks.NETHER_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.SCAFFOLDING, Blocks.REDSTONE_WIRE, Blocks.LEVER,
            Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK, Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK,
            // the nether and the end
            Blocks.NETHERRACK, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_STAIRS, Blocks.NETHER_BRICK_SLAB, Blocks.NETHER_BRICK_WALL,
            Blocks.NETHER_BRICK_FENCE, Blocks.CRACKED_NETHER_BRICKS, Blocks.CHISELED_NETHER_BRICKS, Blocks.RED_NETHER_BRICKS,
            Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB, Blocks.RED_NETHER_BRICK_WALL,
            Blocks.BLACKSTONE, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL, Blocks.GILDED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_WALL,
            Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
            Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Blocks.BASALT, Blocks.POLISHED_BASALT, Blocks.SMOOTH_BASALT, Blocks.SOUL_SAND, Blocks.SOUL_SOIL,
            Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.NETHER_WART_BLOCK, Blocks.WARPED_WART_BLOCK,
            Blocks.CRIMSON_STEM, Blocks.WARPED_STEM, Blocks.CRIMSON_HYPHAE, Blocks.WARPED_HYPHAE, Blocks.STRIPPED_CRIMSON_STEM,
            Blocks.STRIPPED_WARPED_STEM, Blocks.STRIPPED_CRIMSON_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE,
            Blocks.CRIMSON_PLANKS, Blocks.WARPED_PLANKS, Blocks.CRIMSON_SLAB, Blocks.WARPED_SLAB,
            Blocks.WARPED_STAIRS, Blocks.CRIMSON_FENCE, Blocks.WARPED_FENCE, Blocks.CRIMSON_FENCE_GATE, Blocks.WARPED_FENCE_GATE,
            Blocks.CRIMSON_TRAPDOOR, Blocks.WARPED_TRAPDOOR, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ANCIENT_DEBRIS,
            Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK,
            Blocks.PURPUR_PILLAR, Blocks.REINFORCED_DEEPSLATE,
            // the deep
            Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB,
            Blocks.COBBLED_DEEPSLATE_WALL, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB,
            Blocks.POLISHED_DEEPSLATE_WALL, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB,
            Blocks.DEEPSLATE_BRICK_WALL, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
            Blocks.DEEPSLATE_TILE_WALL, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.TUFF, Blocks.CALCITE,
            Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE, Blocks.SCULK, Blocks.SCULK_VEIN,
            // the sea
            Blocks.PRISMARINE, Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB, Blocks.PRISMARINE_WALL, Blocks.PRISMARINE_BRICKS,
            Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE_STAIRS,
            Blocks.DARK_PRISMARINE_SLAB, Blocks.DRIED_KELP_BLOCK, Blocks.SPONGE, Blocks.WET_SPONGE,
            Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.DEAD_BRAIN_CORAL_BLOCK, Blocks.DEAD_BUBBLE_CORAL_BLOCK, Blocks.DEAD_FIRE_CORAL_BLOCK,
            Blocks.DEAD_HORN_CORAL_BLOCK,
            // bricks and mud
            Blocks.BRICKS, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICK_WALL, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS,
            Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL, Blocks.PACKED_MUD, Blocks.MUD, Blocks.CLAY,
            // bone, hay, crops, snow and ice
            Blocks.BONE_BLOCK, Blocks.HAY_BLOCK, Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.MELON, Blocks.SNOW_BLOCK,
            Blocks.SNOW, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.POWDER_SNOW,
            // natural decorations a building carries (the bush/coral/carpet classes cover the rest)
            Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.COBWEB, Blocks.MANGROVE_ROOTS, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.SUGAR_CANE,
            Blocks.CACTUS, Blocks.BAMBOO, Blocks.TURTLE_EGG, Blocks.SPORE_BLOSSOM, Blocks.HANGING_ROOTS, Blocks.KELP,
            Blocks.KELP_PLANT,
            // non-full blocks a full-block swap could not keep the shape of
            Blocks.DIRT_PATH, Blocks.FARMLAND);
    }
}
