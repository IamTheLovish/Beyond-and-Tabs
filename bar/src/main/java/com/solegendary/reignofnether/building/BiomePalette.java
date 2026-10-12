package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.building.buildings.placements.BridgePlacement;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.placements.SculkCatalystPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.faction.FactionTraits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villages that change with the biome (lovish, Oct 12; design/lively_buildings.md, slice 1). When a building is placed,
 * a small set of its palette blocks is swapped by the biome under it, per faction:
 * <ul>
 *   <li>SNOWY (cold biomes): snow caps - the top block of a column, where nothing of the building sits above it, turns
 *       to snow if it is ground, stone, timber, tiles or leaves - and oak / birch / jungle / acacia / mangrove / cherry
 *       trim turns to spruce</li>
 *   <li>ARID (desert, badlands, savanna): cobble and stone bricks turn to sandstone, timber trim to acacia, a bare
 *       ground cap to smooth sandstone</li>
 *   <li>LUSH (jungle, swamps, lush caves): cobble and stone bricks go mossy, timber trim turns to jungle, a bare dirt cap
 *       to moss</li>
 * </ul>
 * Every swap is shape-for-shape - a full block for a full block, stairs for stairs, a slab for a slab, a fence for a
 * fence, with the old block's properties copied - so the footprint, the block count (HP, build time) and the walkable
 * cells (pockets) never change; the building's startingBlockTypes are never swapped (they are its foundation queue).
 * The Verdant Court's living wood and the Tidewrought ship timber keep their own trim. Bridges, custom buildings,
 * portals, beacons, farms and sculk are left alone (their placements read or rewrite their own blocks).
 * <p>
 * The swap happens on the placement's own block list (BuildingPlacement.setBlocks), on the server and on every client
 * alike, from the noise biome at the footprint's origin: the server has it, and a client has it as soon as the chunk
 * is loaded - until then the client defers the swap (BuildingPlacement.biomePalettePending, retried in its tick), so
 * both sides always agree on what a placed block should be.
 */
public final class BiomePalette {
    private BiomePalette() { }

    public enum Kind { NONE, SNOWY, ARID, LUSH }

    /** The palette kind of the biome at pos (the exact noise biome, no fuzzing: the server and clients agree). */
    public static Kind kindAt(LevelReader level, BlockPos pos) {
        Holder<Biome> b = level.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
            QuartPos.fromBlock(pos.getZ()));
        if (b.is(BiomeTags.IS_NETHER) || b.is(BiomeTags.IS_END))
            return Kind.NONE;
        if (b.is(BiomeTags.IS_JUNGLE) || b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP) || b.is(Biomes.LUSH_CAVES))
            return Kind.LUSH;
        float t = b.value().getBaseTemperature();
        if (t < 0.15f)                  // snowy plains / taiga / slopes, groves, frozen peaks, ice spikes
            return Kind.SNOWY;
        if (t >= 1.5f && !b.value().hasPrecipitation())   // desert, badlands, savanna
            return Kind.ARID;
        return Kind.NONE;
    }

    /** One faction's swaps for one biome kind: block-for-block trim swaps, and the blocks that take a cap. */
    private record Rules(Map<Block, Block> trim, Set<Block> capped, Block cap) { }
    private static final Map<String, Rules> RULES = new ConcurrentHashMap<>();
    private static final Rules NO_RULES = new Rules(Map.of(), Set.of(), Blocks.AIR);

    /** True if this placement takes biome swaps at all. */
    static boolean applies(BuildingPlacement p) {
        Building b = p.getBuilding();
        return !(b instanceof AbstractBridge) && !(b instanceof CustomBuilding) && !(p instanceof BridgePlacement)
            && !(p instanceof PortalPlacement) && !(p instanceof BeaconPlacement) && !(p instanceof FarmPlacement)
            && !(p instanceof SculkCatalystPlacement)
            && FactionTraits.hasExplicit(b.getFaction());
    }

    /**
     * Swaps the placement's blocks for its biome, or (a client whose chunk isn't loaded yet) marks it pending.
     * Called from BuildingPlacement.setBlocks and, while pending, from its client tick.
     */
    static void applyOrDefer(BuildingPlacement p) {
        p.biomePalettePending = false;
        Level level = p.getLevel();
        if (level == null || p.originPos == null || p.blocks == null || !applies(p))
            return;
        if (level.isClientSide() && !level.hasChunkAt(p.originPos)) {
            p.biomePalettePending = true;
            return;
        }
        apply(p.blocks, p.getBuilding(), kindAt(level, p.originPos.above()));
    }

    /**
     * Swaps blocks in place for this building and biome kind; returns how many changed. Public for the GameTest.
     * Positions never change and every swap keeps the block's shape, so the list's footprint and counts stay put.
     */
    public static int apply(List<BuildingBlock> blocks, Building building, Kind kind) {
        if (kind == Kind.NONE || blocks.isEmpty())
            return 0;
        Rules rules = rulesFor(FactionTraits.of(building.getFaction()).name, kind);
        if (rules == NO_RULES)
            return 0;
        Set<BlockPos> covered = new HashSet<>();
        for (BuildingBlock bb : blocks)
            if (!bb.getBlockState().isAir())
                covered.add(bb.getBlockPos().below());
        int changed = 0;
        List<Block> start = building.startingBlockTypes;
        for (BuildingBlock bb : blocks) {
            BlockState bs = bb.getBlockState();
            Block block = bs.getBlock();
            if (bs.isAir() || start.contains(block))
                continue;
            Block to = null;
            if (!covered.contains(bb.getBlockPos()) && (rules.capped().contains(block)
                    || (rules.cap() == Blocks.SNOW_BLOCK && bs.is(BlockTags.LEAVES))))
                to = rules.cap();
            else
                to = rules.trim().get(block);
            if (to == null || to == block)
                continue;
            bb.setBlockState(copyProperties(bs, to.defaultBlockState()));
            changed++;
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

    private static Rules rulesFor(String faction, Kind kind) {
        return RULES.computeIfAbsent(faction + "/" + kind, k -> build(faction, kind));
    }

    // ------------------------------------------------------------------ the tables

    /** planks, stairs, slab, fence, fence gate of each wood (the trim a building's timber is made of). */
    private static final Block[][] WOODS = {
        { Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE },
        { Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE_GATE },
        { Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE },
        { Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_FENCE_GATE },
        { Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE, Blocks.ACACIA_FENCE_GATE },
        { Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_FENCE_GATE },
        { Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_FENCE, Blocks.CHERRY_FENCE_GATE },
    };
    private static final int SPRUCE = 2, JUNGLE = 3, ACACIA = 4;

    /** Light woods re-trimmed in each biome (dark oak, the Gravebound's, stays dark everywhere). */
    private static void woodTo(Map<Block, Block> trim, int target) {
        for (int w = 0; w < WOODS.length; w++)
            if (w != target)
                for (int i = 0; i < WOODS[w].length; i++)
                    trim.put(WOODS[w][i], WOODS[target][i]);
    }

    private static void put(Map<Block, Block> trim, Block[] from, Block[] to) {
        for (int i = 0; i < from.length; i++)
            trim.put(from[i], to[i]);
    }

    private static final Block[] COBBLE = { Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL };
    private static final Block[] MOSSY_COBBLE = { Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_WALL };
    private static final Block[] BRICKS = { Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL };
    private static final Block[] MOSSY_BRICKS = { Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_WALL };
    private static final Block[] SANDSTONE = { Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL };
    private static final Block[] SMOOTH_SANDSTONE = { Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL };

    /** Ground blocks: capped in every biome kind that caps (snow, sand, moss). */
    private static final Block[] GROUND = { Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT,
        Blocks.PODZOL, Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.PACKED_MUD };
    /** Roofs, tops and ledges that snow settles on (white quartz, gold and the faction accents stay bare). */
    private static final Block[] SNOW_SETTLES = { Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
        Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE,
        Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS,
        Blocks.POLISHED_DEEPSLATE, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.POLISHED_BLACKSTONE,
        Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.BRICKS,
        Blocks.MUD_BRICKS, Blocks.TERRACOTTA, Blocks.HAY_BLOCK, Blocks.DRIED_KELP_BLOCK, Blocks.SOUL_SOIL };

    private static Rules build(String faction, Kind kind) {
        if ("neutral".equals(faction))
            return NO_RULES;
        // the Court's trees are alive and the Tidewrought build from wrecks: their timber is who they are
        boolean keepsWood = "verdant".equals(faction) || "tidewrought".equals(faction);
        Map<Block, Block> trim = new HashMap<>();
        Set<Block> capped = new HashSet<>(List.of(GROUND));
        Block cap;
        switch (kind) {
            case SNOWY -> {
                if (!keepsWood)
                    woodTo(trim, SPRUCE);
                capped.addAll(List.of(SNOW_SETTLES));
                cap = Blocks.SNOW_BLOCK;
            }
            case ARID -> {
                if (!keepsWood)
                    woodTo(trim, ACACIA);
                put(trim, COBBLE, SANDSTONE);
                put(trim, MOSSY_COBBLE, SANDSTONE);
                put(trim, BRICKS, SMOOTH_SANDSTONE);
                put(trim, MOSSY_BRICKS, SMOOTH_SANDSTONE);
                cap = Blocks.SMOOTH_SANDSTONE;
            }
            case LUSH -> {
                if (!keepsWood)
                    woodTo(trim, JUNGLE);
                put(trim, COBBLE, MOSSY_COBBLE);
                put(trim, BRICKS, MOSSY_BRICKS);
                capped.remove(Blocks.MOSS_BLOCK);
                cap = Blocks.MOSS_BLOCK;
            }
            default -> {
                return NO_RULES;
            }
        }
        return new Rules(Map.copyOf(trim), Set.copyOf(capped), cap);
    }

    /** For tests and tools: the blocks a kind's cap turns into (snow / smooth sandstone / moss), or null. */
    public static Block capOf(Kind kind) {
        return switch (kind) {
            case SNOWY -> Blocks.SNOW_BLOCK;
            case ARID -> Blocks.SMOOTH_SANDSTONE;
            case LUSH -> Blocks.MOSS_BLOCK;
            default -> null;
        };
    }
}
