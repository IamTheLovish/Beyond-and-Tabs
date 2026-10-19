package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BiomePalette;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingBlockData;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.CapitolLife;
import com.solegendary.reignofnether.building.FactionAura;
import com.solegendary.reignofnether.building.FactionAuraServerEvents;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.unit.UnitUnstick;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Biome-aware faction bases (design/biome_bases.md): the per-faction biome palettes (BiomePalette), the faction
 * terraform auras (FactionAura / FactionAuraServerEvents) and the RoN capitols' detail pass
 * (tools/gen_capitol_details.py). Everything a test builds stands in its own far-away chunks, 21000+ blocks south of
 * its arena; the aura tests drive their own registry, never the match's.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BiomeBaseGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    // ---------------------------------------------------------------------------------------------- the tables

    /** Every structure a building can wear: its own, its levels' (labs) and its upgrade's (extractor T2, graveyard). */
    private static Set<String> structuresOf(Building b) {
        Set<String> out = new HashSet<>();
        if (b.structureName == null)
            return out;
        out.add(b.structureName);
        for (int lv = 2; lv <= b.maxLevel; lv++)
            out.add(b.getLevelStructureName(lv));
        String up = b.getUpgradedStructureName(1);
        if (up != null)
            out.add(up);
        return out;
    }

    /**
     * No unmapped surprises: every block every structure of a faction uses (its buildings, their levels and upgrades,
     * the shared structures it builds) has a decision in that faction's table - kept as identity, swapped, or a
     * shared identity material. A new material in a structure fails here until someone decides what the biomes do to it.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void biome_palette_covers_every_faction_block(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<FactionTraits, Set<String>> undecided = new IdentityHashMap<>();
        int checked = 0, structures = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            if (b instanceof AbstractBridge || b instanceof CustomBuilding)
                continue;
            Faction faction = b.getFaction();
            FactionTraits traits = FactionTraits.of(faction);
            if (!BiomePalette.factions().contains(traits))
                continue;
            for (String name : structuresOf(b)) {
                List<BuildingBlock> blocks = BuildingBlockData.getBuildingBlocksFromNbt(name, level);
                if (blocks.isEmpty())
                    continue;
                structures++;
                for (BuildingBlock bb : blocks) {
                    Block block = bb.getBlockState().getBlock();
                    checked++;
                    if (!BiomePalette.isDecided(faction, block))
                        undecided.computeIfAbsent(traits, k -> new TreeSet<>()).add(
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath() + " (" + name + ")");
                }
            }
        }
        if (structures < 100)
            helper.fail("only " + structures + " structures were read - the building registry or the structure files are missing");
        else if (!undecided.isEmpty()) {
            StringBuilder sb = new StringBuilder("blocks without a biome decision: ");
            undecided.forEach((t, set) -> sb.append(t.name).append(' ').append(set).append("; "));
            helper.fail(sb.toString());
        } else
            helper.succeed();
    }

    /**
     * What a placement swap must never do, checked on the whole table: change a block's shape (its collision shape
     * in every state, once the shared properties are copied - so the footprint, silhouette and walkable cells hold),
     * change what the pathfinder sees in it (leaves, fence-likes, gates, doors, trapdoors, slabs, stairs, pits, solid),
     * make a falling block (it would drop out of the building), touch a protected block (code reads it back), or
     * light a block that was dark (lights are identity). A faction's identity blocks are never also swapped.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void biome_palette_tables_keep_shape_and_identity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        int pairs = 0;
        for (FactionTraits t : BiomePalette.factions()) {
            for (Block keep : BiomePalette.keepsOf(t))
                if (BiomePalette.sourcesOf(t).contains(keep))
                    problems.add(t.name + " both keeps and swaps " + keep);
            for (BiomePalette.Family f : BiomePalette.Family.values()) {
                for (Map.Entry<Block, Block> e : BiomePalette.swapsOf(t, f).entrySet()) {
                    pairs++;
                    String why = swapProblem(level, e.getKey(), e.getValue());
                    if (why != null)
                        problems.add(t.name + "/" + f + ": " + e.getKey() + " -> " + e.getValue() + ": " + why);
                }
            }
        }
        for (BiomePalette.Family f : BiomePalette.Family.values())
            for (Block[] w : BiomePalette.weatheringOf(f)) {
                pairs++;
                String why = swapProblem(level, w[0], w[1]);
                if (why != null)
                    problems.add("weathering/" + f + ": " + w[0] + " -> " + w[1] + ": " + why);
            }
        if (BiomePalette.swapsOf(FactionTraits.SUNFORGED, BiomePalette.Family.TEMPERATE).size() != 0)
            problems.add("the home family must be the drawn structure (TEMPERATE swaps something)");
        if (pairs < 200)
            problems.add("only " + pairs + " swaps in the tables");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " bad swaps: " + String.join("; ", problems.subList(0, Math.min(12, problems.size()))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyShared(BlockState from, BlockState to) {
        for (Property p : from.getProperties())
            if (to.hasProperty(p))
                to = to.setValue(p, from.getValue(p));
        return to;
    }

    /** Why from -> to is not a safe placement swap, or null. */
    private static String swapProblem(ServerLevel level, Block from, Block to) {
        if (BiomePalette.isProtected(from) || BiomePalette.isProtected(to))
            return "a protected block";
        if (to instanceof FallingBlock)
            return "the target falls";
        BlockState fd = from.defaultBlockState(), td = to.defaultBlockState();
        if (td.getLightEmission() > fd.getLightEmission())
            return "the target glows";
        if (fd.is(BlockTags.LEAVES) != td.is(BlockTags.LEAVES) || fd.is(BlockTags.FENCES) != td.is(BlockTags.FENCES)
                || fd.is(BlockTags.WALLS) != td.is(BlockTags.WALLS)
                || (from instanceof FenceGateBlock) != (to instanceof FenceGateBlock)
                || (from instanceof DoorBlock) != (to instanceof DoorBlock)
                || (from instanceof TrapDoorBlock) != (to instanceof TrapDoorBlock)
                || (from instanceof SlabBlock) != (to instanceof SlabBlock)
                || (from instanceof StairBlock) != (to instanceof StairBlock)
                || (from instanceof AbstractCauldronBlock) != (to instanceof AbstractCauldronBlock))
            return "a different kind of block to the pathfinder";
        for (BlockState fs : from.getStateDefinition().getPossibleStates()) {
            BlockState ts = copyShared(fs, td);
            if (fs.isSolid() != ts.isSolid())
                return "solid in one state and not the other: " + fs;
            VoxelShape a = fs.getCollisionShape(level, BlockPos.ZERO), b = ts.getCollisionShape(level, BlockPos.ZERO);
            if (Shapes.joinIsNotEmpty(a, b, BooleanOp.NOT_SAME))
                return "a different shape in " + fs;
        }
        return null;
    }

    /** The families follow vanilla's village types (and the jungle, swamp and badlands): tags first, climate after. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void biome_families_follow_village_types(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var reg = level.registryAccess().registryOrThrow(Registries.BIOME);
        Map<ResourceKey<Biome>, BiomePalette.Family> want = new HashMap<>();
        want.put(Biomes.PLAINS, BiomePalette.Family.TEMPERATE);
        want.put(Biomes.FOREST, BiomePalette.Family.TEMPERATE);
        want.put(Biomes.MEADOW, BiomePalette.Family.TEMPERATE);
        want.put(Biomes.SNOWY_PLAINS, BiomePalette.Family.SNOWY);
        want.put(Biomes.SNOWY_TAIGA, BiomePalette.Family.SNOWY);
        want.put(Biomes.GROVE, BiomePalette.Family.SNOWY);
        want.put(Biomes.TAIGA, BiomePalette.Family.TAIGA);
        want.put(Biomes.OLD_GROWTH_PINE_TAIGA, BiomePalette.Family.TAIGA);
        want.put(Biomes.WINDSWEPT_HILLS, BiomePalette.Family.TAIGA);
        want.put(Biomes.DESERT, BiomePalette.Family.DESERT);
        want.put(Biomes.BADLANDS, BiomePalette.Family.BADLANDS);
        want.put(Biomes.WOODED_BADLANDS, BiomePalette.Family.BADLANDS);
        want.put(Biomes.SAVANNA, BiomePalette.Family.SAVANNA);
        want.put(Biomes.JUNGLE, BiomePalette.Family.JUNGLE);
        want.put(Biomes.BAMBOO_JUNGLE, BiomePalette.Family.JUNGLE);
        want.put(Biomes.SWAMP, BiomePalette.Family.SWAMP);
        want.put(Biomes.MANGROVE_SWAMP, BiomePalette.Family.SWAMP);
        want.put(Biomes.NETHER_WASTES, BiomePalette.Family.TEMPERATE);
        List<String> wrong = new ArrayList<>();
        want.forEach((key, fam) -> {
            Holder<Biome> h = reg.getHolderOrThrow(key);
            BiomePalette.Family got = BiomePalette.familyOf(h);
            if (got != fam)
                wrong.add(key.location().getPath() + " reads " + got + ", expected " + fam);
        });
        if (wrong.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", wrong));
    }

    // ---------------------------------------------------------------------------------------------- placement

    /** Fills a loaded chunk's biomes with one biome (as the snowy-caps test does); returns the chunk. */
    static LevelChunk fillBiome(ServerLevel level, int cx, int cz, ResourceKey<Biome> biome) {
        Holder<Biome> h = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
        // bounded (GameTestChunks): getChunk could park the server thread for good on CI
        LevelChunk chunk = GameTestChunks.loadNowOrFail(level, cx, cz);
        chunk.fillBiomesFromNoise((x, y, z, sampler) -> h, level.getChunkSource().randomState().sampler());
        chunk.setUnsaved(true);
        return chunk;
    }

    /** Stamps a placement's blocks on a stone pad (the pocket tests' way: flag 2, nothing pops off). */
    static void stamp(ServerLevel level, BuildingPlacement bp, int margin) {
        BlockPos min = bp.minCorner, max = bp.maxCorner;
        int gy = min.getY();
        for (int x = min.getX() - margin; x <= max.getX() + margin; x++)
            for (int z = min.getZ() - margin; z <= max.getZ() + margin; z++) {
                level.setBlock(new BlockPos(x, gy - 1, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, gy, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = gy + 1; y <= max.getY() + 3; y++)
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        for (BuildingBlock b : bp.getBlocks())
            if (b.getBlockPos().getY() > gy || !b.getBlockState().isAir())
                level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
    }

    private static boolean sameShape(ServerLevel level, BlockState a, BlockState b, BlockPos at) {
        return a.isAir() == b.isAir()
            && !Shapes.joinIsNotEmpty(a.getCollisionShape(level, at), b.getCollisionShape(level, at), BooleanOp.NOT_SAME);
    }

    /**
     * The same building placed in a plains chunk and in a desert chunk: in the plains it is exactly the drawn structure;
     * in the desert its neutral stone is sandstone (none of the faction's swapped materials is left), every identity
     * block (team colours, lights, glass, metals, quartz, nether, deepslate...) is untouched, the block count and every
     * block's shape are the same, and - stamped side by side - every cell is exactly as walkable as in the plains copy,
     * so the desert building has the plains building's pockets: none.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void desert_and_plains_bases_take_their_palettes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y0 = 200, margin = 2;
        int chunkX = base.getX() >> 4, chunkZ = (base.getZ() + 21000) >> 4;
        List<Building> sample = List.of(Buildings.BARRACKS, Buildings.LOTUS_ACADEMY, Buildings.MAUSOLEUM,
            Buildings.MACHINE_SHOP, Buildings.GROVE, Buildings.WRECK_HARBOUR);
        List<String> problems = new ArrayList<>();
        int desertSwaps = 0;
        for (int i = 0; i < sample.size(); i++) {
            Building building = sample.get(i);
            // two chunks a side for each copy: the capitols grew to 15 wide (Oct 19), and a 15-wide copy plus its margin
            // reached into the next chunk - the plains copy's edge stood in the desert chunk and took desert blocks
            int cx = chunkX + 2 * i;
            for (int c = 0; c < 2; c++) {
                fillBiome(level, cx + c, chunkZ, Biomes.PLAINS);
                fillBiome(level, cx + c, chunkZ + 1, Biomes.PLAINS);
                fillBiome(level, cx + c, chunkZ + 2, Biomes.DESERT);
                fillBiome(level, cx + c, chunkZ + 3, Biomes.DESERT);
            }
            BlockPos plainsAt = new BlockPos((cx << 4) + 2, y0, (chunkZ << 4) + 2);
            BlockPos desertAt = new BlockPos((cx << 4) + 2, y0, ((chunkZ + 2) << 4) + 2);
            if (BiomePalette.familyAt(level, desertAt.offset(4, 1, 4)) != BiomePalette.Family.DESERT
                    || BiomePalette.familyAt(level, plainsAt.offset(4, 1, 4)) != BiomePalette.Family.TEMPERATE) {
                problems.add(building.structureName + ": the filled chunks don't read as plains / desert");
                continue;
            }
            var raw = BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level), level, plainsAt, Rotation.NONE);
            BuildingPlacement plains = BuildingUtils.getNewBuildingPlacement(building, level, plainsAt, Rotation.NONE, "gametest_biome_plains", false);
            BuildingPlacement desert = BuildingUtils.getNewBuildingPlacement(building, level, desertAt, Rotation.NONE, "gametest_biome_desert", false);
            if (plains == null || desert == null || plains.getBlocks().size() != raw.size() || desert.getBlocks().size() != raw.size()) {
                problems.add(building.structureName + ": a placement lost or gained blocks");
                continue;
            }
            FactionTraits traits = FactionTraits.of(building.getFaction());
            Set<Block> swapped = BiomePalette.swapsOf(traits, BiomePalette.Family.DESERT).keySet();
            Set<Block> weathered = new HashSet<>();
            for (Block[] w : BiomePalette.weatheringOf(BiomePalette.Family.DESERT))
                weathered.add(w[0]);
            int dz = desertAt.getZ() - plainsAt.getZ();
            int rawMinY = Integer.MAX_VALUE;
            for (BuildingBlock bb : raw)
                rawMinY = Math.min(rawMinY, bb.getBlockPos().getY());
            for (int k = 0; k < raw.size(); k++) {
                BlockState r = raw.get(k).getBlockState(), p = plains.getBlocks().get(k).getBlockState();
                BlockState d = desert.getBlocks().get(k).getBlockState();
                BlockPos at = raw.get(k).getBlockPos();
                if (!p.equals(r)) {
                    problems.add(building.structureName + " (plains): " + r + " became " + p + " - the home family must be the drawn building");
                    break;
                }
                if (!sameShape(level, r, d, at)) {
                    problems.add(building.structureName + " (desert): " + r + " became " + d + " - a swap must keep the shape");
                    break;
                }
                Block rb = r.getBlock();
                // the one swap the palette refuses on purpose: a foundation block outside the instant queue whose
                // target is a starting type (it would join the queue)
                Block target = BiomePalette.swapOf(building.getFaction(), BiomePalette.Family.DESERT, rb);
                boolean held = at.getY() < rawMinY + building.foundationYLayers
                    && !building.startingBlockTypes.contains(rb) && building.startingBlockTypes.contains(target);
                if (!d.equals(r))
                    desertSwaps++;
                if (BiomePalette.isSharedKeep(rb) && !weathered.contains(rb) && !d.equals(r)) {
                    problems.add(building.structureName + " (desert): the identity block " + r + " became " + d);
                    break;
                }
                // every material the faction swaps in the desert is gone, starting types included (unless held, or
                // soil under a plant)
                if (!held && swapped.contains(rb) && d.getBlock() == rb && !r.is(BlockTags.DIRT)) {
                    problems.add(building.structureName + " (desert): " + rb + " stayed in the desert");
                    break;
                }
            }
            // walkability: the two copies stamped on identical pads must agree cell for cell
            stamp(level, plains, margin);
            stamp(level, desert, margin);
            BlockPos min = plains.minCorner, max = plains.maxCorner;
            outer:
            for (int x = min.getX() - margin; x <= max.getX() + margin; x++)
                for (int z = min.getZ() - margin; z <= max.getZ() + margin; z++)
                    for (int y = min.getY() + 1; y <= max.getY() + 2; y++) {
                        BlockPos a = new BlockPos(x, y, z), b = a.offset(0, 0, dz);
                        if (UnitUnstick.isWalkable(level, a) != UnitUnstick.isWalkable(level, b)
                                || UnitUnstick.bodyBlocked(level, a) != UnitUnstick.bodyBlocked(level, b)) {
                            problems.add(building.structureName + ": cell " + (x - min.getX()) + "/" + (y - min.getY()) + "/"
                                + (z - min.getZ()) + " walks differently in the desert");
                            break outer;
                        }
                    }
        }
        if (desertSwaps < 100)
            problems.add("only " + desertSwaps + " blocks changed across six buildings in the desert");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * A fresh site starts from its instant foundation queue: the non-air blocks of the foundation layers that
     * BiomePalette.joinsStartQueue accepts (BuildingServerEvents). An empty queue makes RoN count the site as destroyed
     * on its first tick, and a different queue changes how fast it rises - so in every family every building queues
     * exactly the positions its drawn structure queues, while its starting materials (usually its main ones: the
     * Castle's stone bricks, the Tidewrought apron's sandstone) take the biome's look, in the foundation too.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void biome_palette_keeps_every_foundation_queue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        int buildings = 0, swappedStarts = 0, swappedInFoundation = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            if (b instanceof AbstractBridge || b instanceof CustomBuilding || b.structureName == null
                    || b.startingBlockTypes.isEmpty() || !BiomePalette.factions().contains(FactionTraits.of(b.getFaction())))
                continue;
            List<BuildingBlock> raw = b.getRelativeBlockData(level);
            if (raw.isEmpty())
                continue;
            buildings++;
            int minY = Integer.MAX_VALUE;
            for (BuildingBlock bb : raw)
                minY = Math.min(minY, bb.getBlockPos().getY());
            int top = minY + b.foundationYLayers - 1;
            for (BiomePalette.Family f : BiomePalette.Family.values()) {
                List<BuildingBlock> copy = new ArrayList<>(raw.size());
                for (BuildingBlock bb : raw)
                    copy.add(new BuildingBlock(bb.getBlockPos(), bb.getBlockState()));
                LongOpenHashSet starts = new LongOpenHashSet();
                BiomePalette.apply(copy, b, f, starts);
                String moved = null;
                for (int k = 0; k < raw.size(); k++) {
                    BlockState r = raw.get(k).getBlockState(), c = copy.get(k).getBlockState();
                    boolean foundation = raw.get(k).getBlockPos().getY() <= top;
                    boolean rawQueued = foundation && !r.isAir() && b.startingBlockTypes.contains(r.getBlock());
                    boolean copyQueued = foundation && !c.isAir() && BiomePalette.joinsStartQueue(b, copy.get(k), starts);
                    if (rawQueued != copyQueued)
                        moved = r + " -> " + c;
                    if (b.startingBlockTypes.contains(r.getBlock()) && !c.equals(r)) {
                        swappedStarts++;
                        if (foundation)
                            swappedInFoundation++;
                    }
                }
                if (moved != null)
                    problems.add(b.structureName + "/" + f + ": the foundation queue changed (" + moved + ")");
            }
        }
        if (buildings < 30)
            problems.add("only " + buildings + " buildings with starting types were read");
        // the point of tracking the queue by position: the main materials of most buildings are starting types
        if (swappedStarts < 100 || swappedInFoundation < 10)
            problems.add("only " + swappedStarts + " starting-type blocks (" + swappedInFoundation
                + " in a foundation) took a biome look");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(12, problems.size()))));
    }

    // ---------------------------------------------------------------------------------------------- auras

    /** A flat grass pad (dirt under it, air over it) covering [x0, x1] x [z0, z1] at height y. */
    private static void grassPad(ServerLevel level, int x0, int z0, int x1, int z1, int y) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                GameTestChunks.loadNowOrFail(level, cx, cz);   // bounded: getChunk could park the server thread for good
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                level.setBlock(new BlockPos(x, y - 1, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 1; dy <= 3; dy++)
                    level.setBlock(new BlockPos(x, y + dy, z), Blocks.AIR.defaultBlockState(), 2);
            }
    }

    /**
     * One aura on a grass pad round a 7x7 footprint with a metal patch beside it: every grow call converts no more
     * cells than it is given (and never more than it may examine), nothing changes inside the footprint (grown by
     * one) or on the patch's reserved square, every converted cell is a full walkable block with at most a
     * walk-through plant on it, and receding gives back exactly the grass and air that were there.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void faction_aura_respects_its_budget_and_metal_patches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200, half = 3, radius = 11;
        int cx = ((base.getX() >> 4) << 4) + 16, cz = (((base.getZ() + 24000) >> 4) << 4) + 16;
        int x0 = cx - radius - 2, x1 = cx + radius + 2, z0 = cz - radius - 2, z1 = cz + radius + 2;
        grassPad(level, x0, z0, x1, z1, y);
        int patchX = cx + 8, patchZ = cz;
        FactionAura.Obstacles obstacles = new FactionAura.Obstacles();
        obstacles.addBox(cx - half - 1, cz - half - 1, cx + half + 1, cz + half + 1);
        obstacles.addPatch(patchX, patchZ);
        FactionAura aura = new FactionAura(BlockPos.asLong(cx, y, cz), "gametest_aura", FactionAura.BLOOM, cx, cz, y, half, half, radius);
        Long2ObjectOpenHashMap<FactionAura> owners = new Long2ObjectOpenHashMap<>();
        BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        List<String> problems = new ArrayList<>();
        if (aura.plannedCells() < 40)
            problems.add("the aura plans only " + aura.plannedCells() + " cells");
        // a huge budget is still bounded by the candidates one call may examine
        int big = aura.grow(level, 1000, obstacles, owners, scratch);
        if (big > 8)
            problems.add("one grow call converted " + big + " cells (it may examine 8)");
        for (int call = 0; call < 400 && problems.isEmpty(); call++) {
            int before = aura.cellCount();
            int got = aura.grow(level, 2, obstacles, owners, scratch);
            if (got > 2 || aura.cellCount() - before != got)
                problems.add("a grow call with a budget of 2 converted " + got + " (" + (aura.cellCount() - before) + " cells)");
        }
        int converted = 0;
        for (int x = x0; x <= x1 && problems.isEmpty(); x++)
            for (int z = z0; z <= z1; z++) {
                BlockState ground = level.getBlockState(new BlockPos(x, y, z));
                BlockState cover = level.getBlockState(new BlockPos(x, y + 1, z));
                boolean changed = !ground.is(Blocks.GRASS_BLOCK) || !cover.isAir();
                if (!changed)
                    continue;
                converted++;
                boolean inFootprint = Math.abs(x - cx) <= half + 1 && Math.abs(z - cz) <= half + 1;
                boolean onPatch = Math.abs(x - patchX) <= com.solegendary.reignofnether.resources.MetalPatches.RESERVED_HALF
                    && Math.abs(z - patchZ) <= com.solegendary.reignofnether.resources.MetalPatches.RESERVED_HALF;
                if (inFootprint || onPatch) {
                    problems.add("the aura changed " + (x - cx) + "/" + (z - cz) + (inFootprint ? " inside the footprint" : " on the patch"));
                    break;
                }
                if (aura != owners.get(BlockPos.asLong(x, y, z)))
                    problems.add("a changed cell the aura does not own at " + (x - cx) + "/" + (z - cz));
                if (!ground.isCollisionShapeFullBlock(level, new BlockPos(x, y, z))
                        || WalkabilityBuilder.classify(level, x, y + 1, z) != WalkabilityBuilder.KIND_LAND)
                    problems.add("the converted cell " + (x - cx) + "/" + (z - cz) + " (" + ground + " under " + cover + ") is not plain walkable ground");
                if (!cover.isAir() && !cover.getCollisionShape(level, new BlockPos(x, y + 1, z)).isEmpty())
                    problems.add("a decoration with a collision box: " + cover);
            }
        if (problems.isEmpty() && converted != aura.cellCount())
            problems.add(converted + " cells changed on the pad, the aura holds " + aura.cellCount());
        if (problems.isEmpty() && converted < 25)
            problems.add("only " + converted + " cells converted after the whole plan");
        // give it all back
        aura.setReceding(true);
        for (int call = 0; call < 2000 && aura.cellCount() > 0; call++) {
            int got = aura.recede(level, 3, owners, scratch);
            if (got > 3) {
                problems.add("a recede call with a budget of 3 restored " + got);
                break;
            }
        }
        if (aura.cellCount() != 0 || !owners.isEmpty())
            problems.add("the aura still holds " + aura.cellCount() + " cells (" + owners.size() + " owned) after receding");
        for (int x = x0; x <= x1 && problems.isEmpty(); x++)
            for (int z = z0; z <= z1; z++)
                if (!level.getBlockState(new BlockPos(x, y, z)).is(Blocks.GRASS_BLOCK) || !level.getBlockState(new BlockPos(x, y + 1, z)).isAir()) {
                    problems.add("receding left " + level.getBlockState(new BlockPos(x, y, z)) + " / "
                        + level.getBlockState(new BlockPos(x, y + 1, z)) + " at " + (x - cx) + "/" + (z - cz));
                    break;
                }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Twelve overlapping auras in one registry: a pass changes no more cells than its budget, one cell never has two
     * owners, and when they all recede the pad is grass again and the registry empty.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void faction_aura_registry_keeps_the_global_budget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200, budget = 5;
        int ox = ((base.getX() >> 4) << 4) + 8, oz = (((base.getZ() + 27000) >> 4) << 4) + 8;
        grassPad(level, ox - 2, oz - 2, ox + 29, oz + 29, y);
        FactionAuraServerEvents.Registry registry = new FactionAuraServerEvents.Registry();
        List<FactionAura> auras = new ArrayList<>();
        List<FactionAura.Style> styles = FactionAura.styles();
        for (int i = 0; i < 12; i++) {
            int cx = ox + 4 + (i % 4) * 7, cz = oz + 4 + (i / 4) * 9;
            FactionAura a = new FactionAura(BlockPos.asLong(cx, y, cz), "gametest_aura_reg", styles.get(i % styles.size()),
                cx, cz, y, 1, 1, 6);
            auras.add(a);
            registry.add(a);
        }
        List<String> problems = new ArrayList<>();
        int total = 0;
        for (int pass = 0; pass < 60 && problems.isEmpty(); pass++) {
            int got = registry.runPass(level, budget, 24, 1, null);
            total += got;
            if (got > budget)
                problems.add("a pass with a budget of " + budget + " changed " + got + " cells");
        }
        int held = 0;
        for (FactionAura a : auras)
            held += a.cellCount();
        if (held != total)
            problems.add("the auras hold " + held + " cells, the passes converted " + total);
        if (total < 40)
            problems.add("only " + total + " cells converted in 60 passes");
        // one cell, one owner: if two auras had both recorded a cell, they would hold more cells than are owned
        int owned = 0;
        for (int x = ox - 2; x <= ox + 29; x++)
            for (int z = oz - 2; z <= oz + 29; z++)
                if (registry.ownerOf(new BlockPos(x, y, z)) != null)
                    owned++;
        if (owned != held)
            problems.add(owned + " cells are owned on the pad, the auras hold " + held);
        for (FactionAura a : auras)
            a.setReceding(true);
        for (int pass = 0; pass < 4000 && registry.auraCount() > 0; pass++)
            if (registry.runPass(level, budget, 24, 1, null) > budget) {
                problems.add("a receding pass went over its budget");
                break;
            }
        if (registry.auraCount() != 0)
            problems.add(registry.auraCount() + " auras left after receding");
        for (int x = ox - 2; x <= ox + 29 && problems.isEmpty(); x++)
            for (int z = oz - 2; z <= oz + 29; z++)
                if (!level.getBlockState(new BlockPos(x, y, z)).is(Blocks.GRASS_BLOCK) || !level.getBlockState(new BlockPos(x, y + 1, z)).isAir()) {
                    problems.add("receding left " + level.getBlockState(new BlockPos(x, y, z)) + " at " + (x - ox) + "/" + (z - oz));
                    break;
                }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** Every aura style places full, solid, walkable ground that never falls unsupported, and walk-through plants. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void faction_aura_styles_only_make_walkable_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        for (FactionAura.Style s : FactionAura.styles()) {
            for (Block g : s.groundBlocks()) {
                BlockState st = g.defaultBlockState();
                if (!st.isCollisionShapeFullBlock(level, BlockPos.ZERO) || !st.isSolid() || st.is(BlockTags.LEAVES)
                        || WalkabilityBuilder.isFenceLike(st) || g == Blocks.MAGMA_BLOCK || g == Blocks.SOUL_SAND
                        || g == Blocks.SLIME_BLOCK || BiomePalette.isProtected(g))
                    problems.add(s.name + ": " + g + " is not plain walkable ground");
            }
            if (s.groundBlocks().get(s.groundBlocks().size() - 1) instanceof FallingBlock)
                problems.add(s.name + ": the fallback for unsupported cells falls too");
            for (BlockState d : s.decorationStates())
                if (!d.getCollisionShape(level, BlockPos.ZERO).isEmpty() || d.is(Blocks.WITHER_ROSE) || d.is(Blocks.SWEET_BERRY_BUSH)
                        || d.getFluidState().isSource() || d.getLightEmission() > 0)
                    problems.add(s.name + ": the decoration " + d + " is not a harmless walk-through plant");
        }
        if (FactionAura.styleOf(com.solegendary.reignofnether.faction.Factions.PIGLINS) != null)
            problems.add("the Horde has an aura of its own (its Nether Zones are its terraform)");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ---------------------------------------------------------------------------------------------- RoN capitols

    /**
     * The capitols' boxes - RoN's for the Central Portal; the Temple of the Sun's and the Necropolis Cathedral's
     * (Oct 19, the owner's "a lot bigger, a lot taller") for the Town Centre and the Mausoleum - and their build time (buildTimeBlocks = RoN's block count) and its foundation types, gained detail, and the
     * Central Portal's obsidian frame with the air inside it is intact. The Mausoleum's bell hangs in an empty cell
     * under its ceiling with nothing beside it to swing into.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void capitol_details_keep_box_build_time_and_anchors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        Object[][] capitols = {
            { Buildings.TOWN_CENTRE, 305, new int[] { 14, 19, 14 } },   // the Temple of the Sun (Oct 19): 15 x 20 x 15
            { Buildings.MAUSOLEUM, 364, new int[] { 14, 21, 14 } },     // the Necropolis Cathedral (Oct 19): 15 x 22 x 15
            { Buildings.CENTRAL_PORTAL, 314, new int[] { 10, 15, 8 } },
            { Buildings.FOUNDRY_HALL, 428, new int[] { 14, 21, 14 } } };   // the Grand Foundry (Oct 19)
        for (Object[] row : capitols) {
            Building b = (Building) row[0];
            int ronBlocks = (Integer) row[1];
            int[] box = (int[]) row[2];
            List<BuildingBlock> blocks = b.getRelativeBlockData(level);
            int maxX = 0, maxY = 0, maxZ = 0, nonAir = 0;
            Map<BlockPos, BlockState> at = new HashMap<>();
            Set<Block> foundation = new HashSet<>();
            for (BuildingBlock bb : blocks) {
                BlockPos p = bb.getBlockPos();
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
                at.put(p, bb.getBlockState());
                if (!bb.getBlockState().isAir()) {
                    nonAir++;
                    if (p.getY() < b.foundationYLayers)
                        foundation.add(bb.getBlockState().getBlock());
                }
            }
            if (maxX != box[0] || maxY != box[1] || maxZ != box[2])
                problems.add(b.structureName + ": box " + maxX + "/" + maxY + "/" + maxZ + ", RoN's is " + box[0] + "/" + box[1] + "/" + box[2]);
            if (b.getBuildTimeBlocks() != ronBlocks)
                problems.add(b.structureName + ": buildTimeBlocks " + b.getBuildTimeBlocks() + ", RoN's block count is " + ronBlocks);
            if (nonAir <= ronBlocks)
                problems.add(b.structureName + ": " + nonAir + " blocks - the detail pass is missing");
            for (Block start : b.startingBlockTypes)
                if (!foundation.contains(start))
                    problems.add(b.structureName + ": no " + start + " left in its foundation layers");
            if (b == Buildings.CENTRAL_PORTAL) {
                for (int y = 2; y <= 11; y++) {
                    for (int x : new int[] { 3, 7 })
                        if (!at.getOrDefault(new BlockPos(x, y, 4), Blocks.AIR.defaultBlockState()).is(Blocks.OBSIDIAN))
                            problems.add("central_portal: the frame lost its obsidian at " + x + "/" + y + "/4");
                    for (int x = 4; x <= 6; x++)
                        if (!at.getOrDefault(new BlockPos(x, y, 4), Blocks.AIR.defaultBlockState()).isAir())
                            problems.add("central_portal: the portal opening is blocked at " + x + "/" + y + "/4");
                }
            }
        }
        WindGenerator.Motion bell = CapitolLife.MOTIONS.get(Buildings.MAUSOLEUM.structureName);
        if (bell == null || bell.kind() != WindGenerator.MotionKind.BELL) {
            problems.add("the Mausoleum declares no bell");
        } else {
            Map<BlockPos, BlockState> solid = new HashMap<>();
            for (BuildingBlock bb : Buildings.MAUSOLEUM.getRelativeBlockData(level))
                if (!bb.getBlockState().isAir())
                    solid.put(bb.getBlockPos(), bb.getBlockState());
            BlockPos p = bell.pos();
            if (solid.containsKey(p))
                problems.add("the Mausoleum's bell cell holds " + solid.get(p));
            if (!solid.containsKey(p.above()))
                problems.add("nothing holds the Mausoleum's bell up");
            for (Direction d : Direction.Plane.HORIZONTAL)
                if (solid.containsKey(p.relative(d)))
                    problems.add("the Mausoleum's bell would swing into " + p.relative(d).toShortString());
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ---------------------------------------------------------------------------------------------- cost

    /**
     * What the palettes and auras cost, measured on this server and logged as a [Stress] line (CI turns those into
     * notices). The palette is applied to every structure of every faction in every family; the auras run 200 passes
     * of a registry of 48 auras. Bounds are generous - a regression by an order of magnitude fails, runner noise does not.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void biome_palette_and_aura_costs_stay_small(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Building> buildings = new ArrayList<>();
        List<List<BuildingBlock>> structures = new ArrayList<>();
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            if (b instanceof AbstractBridge || b instanceof CustomBuilding || b.structureName == null
                    || !BiomePalette.factions().contains(FactionTraits.of(b.getFaction())))
                continue;
            List<BuildingBlock> blocks = b.getRelativeBlockData(level);
            if (!blocks.isEmpty()) {
                buildings.add(b);
                structures.add(blocks);
            }
        }
        long blockSteps = 0, changed = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < buildings.size(); i++) {
            for (BiomePalette.Family f : BiomePalette.Family.values()) {
                List<BuildingBlock> copy = new ArrayList<>(structures.get(i).size());
                for (BuildingBlock bb : structures.get(i))
                    copy.add(new BuildingBlock(bb.getBlockPos(), bb.getBlockState()));
                changed += BiomePalette.apply(copy, buildings.get(i), f);
                blockSteps += copy.size();
            }
        }
        long paletteNs = System.nanoTime() - t0;
        // auras: 48 on a pad, 200 passes at the live budget
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200;
        int ox = ((base.getX() >> 4) << 4) + 8, oz = (((base.getZ() + 30000) >> 4) << 4) + 8;
        grassPad(level, ox - 2, oz - 2, ox + 45, oz + 45, y);
        FactionAuraServerEvents.Registry registry = new FactionAuraServerEvents.Registry();
        for (int i = 0; i < 48; i++) {
            int cx = ox + 3 + (i % 8) * 6, cz = oz + 3 + (i / 8) * 7;
            registry.add(new FactionAura(BlockPos.asLong(cx, y, cz), "gametest_aura_cost", FactionAura.styles().get(i % 6),
                cx, cz, y, 1, 1, 6));
        }
        // passes that convert something (growing) and passes that find every plan done (idle: the steady state of a
        // match - all of them run inside this one game tick, so no aura's resweep comes due) are timed apart
        int cells = 0, busyPasses = 0;
        long busyNs = 0, idleNs = 0, maxNs = 0;
        for (int pass = 0; pass < 200; pass++) {
            long t1 = System.nanoTime();
            int n = registry.runPass(level, 24, 24, 1, null);
            long dt = System.nanoTime() - t1;
            cells += n;
            maxNs = Math.max(maxNs, dt);
            if (n > 0) {
                busyPasses++;
                busyNs += dt;
            } else
                idleNs += dt;
        }
        double perBlock = blockSteps == 0 ? 0 : paletteNs / (double) blockSteps;
        double busyMs = busyPasses == 0 ? 0 : busyNs / (double) busyPasses / 1e6;
        double idleMs = busyPasses == 200 ? 0 : idleNs / (double) (200 - busyPasses) / 1e6;
        ReignOfNether.LOGGER.info("[Stress] biome-bases: palette {} structures x {} families = {} blocks in {} ms ({} ns/block, {} swapped); "
                + "auras 48 x 200 passes: {} cells, {} growing passes {} ms avg, {} idle passes {} ms avg, max pass {} ms",
            buildings.size(), BiomePalette.Family.values().length, blockSteps, String.format("%.1f", paletteNs / 1e6),
            String.format("%.0f", perBlock), changed, cells, busyPasses, String.format("%.3f", busyMs), 200 - busyPasses,
            String.format("%.3f", idleMs), String.format("%.3f", maxNs / 1e6));
        List<String> problems = new ArrayList<>();
        if (perBlock > 5000)
            problems.add("the palette costs " + (int) perBlock + " ns a block");
        if (busyMs > 5.0 || idleMs > 1.0)
            problems.add("an aura pass costs " + String.format("%.2f", busyMs) + " ms growing, "
                + String.format("%.2f", idleMs) + " ms idle");
        if (changed == 0 || cells == 0)
            problems.add("nothing was swapped (" + changed + ") or converted (" + cells + ")");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
