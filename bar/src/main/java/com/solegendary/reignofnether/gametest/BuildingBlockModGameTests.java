package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingBlockData;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The building-block mods are required dependencies (lovish, Oct 20 2026: "increase block vocabulary, scale the
 * buildings bigger"): Macaw's Roofs, Macaw's Windows, Macaw's Fences and Walls and Chipped (with Athena and
 * Resourceful Lib), and since Oct 29 ("blocky and toy") FramedBlocks and Domum Ornamentum, whose blocks take their
 * look (camo / material skin) from block-entity data the .nbt carries. Structures name their blocks by id, and Minecraft turns an id nobody registered into AIR without
 * a word (NbtUtils.readBlockState) - so a missing mod, a renamed block or a generator typo would put holes in a
 * building and every structure test would quietly check a different building from the one players see. These tests
 * make that loud: the mods load and register the blocks we use, and every structure we ship names only registered
 * blocks.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildingBlockModGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * One known block per mod (mod id -> block ids) - those our structures already use (villager_house,
     * haunted_house) or a mod's staple - and the least a mod must register: a far smaller count means a broken or
     * different jar. bar/build.gradle pins the files (lovish's instance, pack/instance_mods.json).
     */
    static final Map<String, List<String>> KNOWN_BLOCKS = new LinkedHashMap<>();
    static final Map<String, Integer> MIN_BLOCKS = new LinkedHashMap<>();
    static {
        KNOWN_BLOCKS.put("mcwroofs", List.of("mcwroofs:thatch_steep_roof", "mcwroofs:deepslate_top_roof"));
        KNOWN_BLOCKS.put("mcwwindows", List.of("mcwwindows:spruce_window", "mcwwindows:blackstone_brick_gothic"));
        KNOWN_BLOCKS.put("mcwfences", List.of("mcwfences:oak_picket_fence"));
        KNOWN_BLOCKS.put("chipped", List.of());
        // slopes, prisms and half pillars: domes, hips and pilasters (camo = any block's texture, block entity "camo")
        KNOWN_BLOCKS.put("framedblocks",
            List.of("framedblocks:framed_slope", "framedblocks:framed_prism", "framedblocks:framed_half_pillar"));
        // shingle roofs, half-timber (fachwerk) panels and pillars for the Brassworks (skin: block entity "textureData")
        KNOWN_BLOCKS.put("domum_ornamentum",
            List.of("domum_ornamentum:shingle", "domum_ornamentum:plain", "domum_ornamentum:blockpillar"));
        MIN_BLOCKS.put("mcwroofs", 100);
        MIN_BLOCKS.put("mcwwindows", 100);
        MIN_BLOCKS.put("mcwfences", 40);
        MIN_BLOCKS.put("chipped", 500);
        MIN_BLOCKS.put("framedblocks", 150);
        MIN_BLOCKS.put("domum_ornamentum", 60);
    }
    static final List<String> REQUIRED_MODS = List.of("mcwroofs", "mcwwindows", "mcwfences", "chipped", "athena",
        "resourcefullib", "framedblocks", "domum_ornamentum");

    /**
     * Every block of these mods (namespace, or prefix "mcw" = all of Macaw's) goes to run-gametest/mod_block_ids.json:
     * id, block-state properties with their allowed values, and whether it carries a block entity - the vocabulary the
     * structure generators (tools/gen_structures.py) may name. .github/workflows/block-ids.yml publishes the file to
     * branch block-ids, since nobody outside a CI log could list them before.
     */
    static final List<String> DUMP_MODS =
        List.of("mcw", "framedblocks", "domum_ornamentum", "chipped", "decorative_blocks", "supplementaries");
    static final String DUMP_FILE = "mod_block_ids.json";

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void building_block_mods_are_loaded_with_their_blocks(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        try {
            writeBlockIdDump(Path.of(DUMP_FILE));
        } catch (Exception e) {
            problems.add("could not write " + DUMP_FILE + ": " + e);
        }
        for (String mod : REQUIRED_MODS)
            if (!ModList.get().isLoaded(mod))
                problems.add("mod '" + mod + "' is not loaded (bar/build.gradle runtimeOnly + mods.toml)");

        Map<String, TreeSet<String>> byMod = new LinkedHashMap<>();
        for (String mod : KNOWN_BLOCKS.keySet())
            byMod.put(mod, new TreeSet<>());
        for (ResourceLocation key : ForgeRegistries.BLOCKS.getKeys()) {
            TreeSet<String> ids = byMod.get(key.getNamespace());
            if (ids != null)
                ids.add(key.toString());
        }
        for (var e : byMod.entrySet()) {
            String mod = e.getKey();
            TreeSet<String> ids = e.getValue();
            // the vocabulary for whoever writes the next structure (tools/ci_gametests.sh makes it a CI notice)
            ReignOfNether.LOGGER.info("[ModBlocks] {}: {} blocks, e.g. {}", mod, ids.size(), sample(ids, 30));
            // the whole vocabulary, one line per mod, for builders reading the job log (not a notice: too long)
            ReignOfNether.LOGGER.info("[ModBlocksAll] {}: {}", mod, String.join(" ", ids));
            for (String id : KNOWN_BLOCKS.get(mod))
                if (!ids.contains(id))
                    problems.add(id + " is not registered (" + mod + " has " + ids.size() + " blocks, e.g. "
                        + sample(ids, 12) + ")");
            if (ids.size() < MIN_BLOCKS.get(mod))
                problems.add(mod + " registers only " + ids.size() + " blocks (expected at least "
                    + MIN_BLOCKS.get(mod) + ")");
        }
        if (!problems.isEmpty())
            helper.fail("building-block mods: " + problems);
        helper.succeed();
    }

    /**
     * Every structure in the mod file (data/ for the server, assets/ for the client's ghost preview) names only blocks
     * some installed mod registers - none silently becomes air. And the real load path keeps a mod block: the
     * Villager House's Macaw's roof and windows (BuildingBlockData) come out as those blocks, not air.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void structure_palettes_name_only_registered_blocks(GameTestHelper helper) {
        var modFile = ModList.get().getModFileById(ReignOfNether.MOD_ID);
        if (modFile == null) {
            helper.fail("could not find our own mod file");
            return;
        }
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (String side : List.of("data", "assets")) {
            Path dir = modFile.getFile().findResource(side, ReignOfNether.MOD_ID, "structures");
            if (!Files.isDirectory(dir)) {
                problems.add(side + "/" + ReignOfNether.MOD_ID + "/structures is missing");
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".nbt")).sorted().toList()) {
                    checked++;
                    CompoundTag nbt;
                    try (InputStream in = Files.newInputStream(p)) {
                        nbt = NbtIo.readCompressed(in);
                    }
                    List<String> unknown = BuildingBlockData.unknownBlockIds(nbt);
                    if (!unknown.isEmpty())
                        problems.add(side + "/" + p.getFileName() + " names unregistered " + unknown);
                }
            } catch (Exception e) {
                problems.add(side + ": could not read the structures (" + e + ")");
            }
        }
        if (checked < 300)
            problems.add("only " + checked + " structure files were read - the scan is broken");

        List<BuildingBlock> house = BuildingBlockData.getBuildingBlocksFromNbt("villager_house", helper.getLevel());
        long roofs = house.stream().filter(b -> isFrom(b, "mcwroofs")).count();
        long windows = house.stream().filter(b -> isFrom(b, "mcwwindows")).count();
        if (roofs == 0 || windows == 0)
            problems.add("villager_house loaded " + roofs + " Macaw's roof and " + windows + " Macaw's window blocks "
                + "(its palette names both) - they became air");

        if (!problems.isEmpty())
            helper.fail("structure palettes: " + problems);
        helper.succeed();
    }

    /**
     * A FramedBlocks slope placed the way a building places it - a structure palette entry plus the block's "nbt"
     * (FramedBlockEntity's camo: {type: framedblocks:block, state: {Name}}, FramedBlocks 9.4.3 BlockCamoContainer),
     * read by BuildingBlockData and put down by BuildingPlacement.restoreBlockEntity - keeps its camo through the
     * block entity and a save / load round trip. Without that the generators' framed blocks would stand as bare frames.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void building_block_mod_framed_slope_keeps_its_camo_from_structure_nbt(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 1, 1, 6, 6);
        CompoundTag camo = new CompoundTag();
        camo.putString("type", "framedblocks:block");
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:bricks");
        camo.put("state", state);
        CompoundTag be = new CompoundTag();
        be.putString("id", "framedblocks:framed_tile");
        be.put("camo", camo);
        String err = placeAndRoundTrip(helper, new BlockPos(2, 2, 2), "framedblocks:framed_slope", be,
            saved -> saved.getCompound("camo").getCompound("state").getString("Name"), "minecraft:bricks");
        if (err != null) helper.fail(err);
        else helper.succeed();
    }

    /**
     * Domum Ornamentum's shingle roof and half-timber panel take their materials from the block entity
     * (MateriallyTexturedBlockEntity "textureData": component id -> block id, Domum 1.20.1-1.0.304). Placed from
     * structure data they keep the skin we wrote (deepslate-tile shingles on spruce; spruce frame, white terracotta infill).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void building_block_mod_domum_shingle_and_timber_keep_their_materials(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 1, 1, 6, 6);
        CompoundTag shingle = domumSkin("minecraft:block/clay", "minecraft:deepslate_tiles",
            "minecraft:block/oak_planks", "minecraft:spruce_planks");
        String err = placeAndRoundTrip(helper, new BlockPos(2, 2, 2), "domum_ornamentum:shingle", shingle,
            saved -> saved.getCompound("textureData").getString("minecraft:block/clay") + " on "
                + saved.getCompound("textureData").getString("minecraft:block/oak_planks"),
            "minecraft:deepslate_tiles on minecraft:spruce_planks");
        if (err == null) {
            CompoundTag timber = domumSkin("minecraft:block/oak_planks", "minecraft:spruce_planks",
                "minecraft:block/dark_oak_planks", "minecraft:white_terracotta");
            err = placeAndRoundTrip(helper, new BlockPos(4, 2, 2), "domum_ornamentum:plain", timber,
                saved -> saved.getCompound("textureData").getString("minecraft:block/oak_planks") + " / "
                    + saved.getCompound("textureData").getString("minecraft:block/dark_oak_planks"),
                "minecraft:spruce_planks / minecraft:white_terracotta");
        }
        if (err != null) helper.fail(err);
        else helper.succeed();
    }

    private static CompoundTag domumSkin(String c1, String b1, String c2, String b2) {
        CompoundTag tex = new CompoundTag();
        tex.putString(c1, b1);
        tex.putString(c2, b2);
        CompoundTag be = new CompoundTag();
        be.putString("id", "domum_ornamentum:materially_retexturable");
        be.put("textureData", tex);
        return be;
    }

    /**
     * blockId with block-entity data beNbt as a one-block structure (palette + blocks[{state, pos, nbt}], as
     * tools/gen_structures.py writes it), through the production load and place path; then the placed block entity
     * is saved, byte round-tripped and loaded again (a chunk save / load). null when skin(saved) is want both times.
     */
    private static String placeAndRoundTrip(GameTestHelper helper, BlockPos rel, String blockId, CompoundTag beNbt,
                                            java.util.function.Function<CompoundTag, String> skin, String want) {
        ResourceLocation key = ResourceLocation.tryParse(blockId);
        if (key == null || !ForgeRegistries.BLOCKS.containsKey(key))
            return blockId + " is not registered (is its mod loaded?)";
        CompoundTag structure = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(ForgeRegistries.BLOCKS.getValue(key).defaultBlockState()));
        structure.put("palette", palette);
        CompoundTag entry = new CompoundTag();
        entry.putInt("state", 0);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0));
        entry.put("pos", pos);
        entry.put("nbt", beNbt);
        ListTag blocks = new ListTag();
        blocks.add(entry);
        structure.put("blocks", blocks);

        List<BuildingBlock> loaded = BuildingBlockData.getBuildingBlocksFromNbt(structure);
        if (loaded.size() != 1 || loaded.get(0).getBlockNbt() == null)
            return blockId + ": the structure loader gave " + loaded.size() + " blocks / no nbt";
        BlockState bs = loaded.get(0).getBlockState();
        if (!key.equals(ForgeRegistries.BLOCKS.getKey(bs.getBlock())))
            return blockId + " loaded as " + bs;
        BlockPos at = helper.absolutePos(rel);
        var level = helper.getLevel();
        level.setBlockAndUpdate(at, bs);
        BuildingPlacement.restoreBlockEntity(level, at, bs, loaded.get(0).getBlockNbt());
        BlockEntity placed = level.getBlockEntity(at);
        if (placed == null)
            return blockId + ": no block entity after placing";
        String got = skin.apply(placed.saveWithFullMetadata());
        if (!want.equals(got))
            return blockId + ": placed with skin '" + got + "', expected '" + want + "' (" + placed.saveWithFullMetadata() + ")";
        try {
            BlockEntity again = BlockEntity.loadStatic(at, bs, SkirmishGameTests.bytesRoundTrip(placed.saveWithFullMetadata()));
            String back = again == null ? null : skin.apply(again.saveWithFullMetadata());
            if (!want.equals(back))
                return blockId + ": after a save / load the skin is '" + back + "', expected '" + want + "'";
        } catch (java.io.IOException e) {
            return blockId + ": NBT round trip failed: " + e;
        }
        return null;
    }

    /** DUMP_MODS' blocks: {mod: {count, blocks: {id: {props: {name: [values]}, block_entity: bool}}}}, sorted. */
    static void writeBlockIdDump(Path out) throws java.io.IOException {
        Map<String, java.util.TreeMap<String, JsonObject>> byMod = new java.util.TreeMap<>();
        for (var e : ForgeRegistries.BLOCKS.getEntries()) {
            ResourceLocation id = e.getKey().location();
            String ns = id.getNamespace();
            if (DUMP_MODS.stream().noneMatch(m -> m.equals("mcw") ? ns.startsWith("mcw") : m.equals(ns)))
                continue;
            Block block = e.getValue();
            JsonObject props = new JsonObject();
            for (Property<?> p : block.getStateDefinition().getProperties())
                props.add(p.getName(), values(p));
            JsonObject b = new JsonObject();
            b.add("props", props);
            b.addProperty("block_entity", block instanceof EntityBlock);
            byMod.computeIfAbsent(ns, k -> new java.util.TreeMap<>()).put(id.toString(), b);
        }
        JsonObject root = new JsonObject();
        for (var m : byMod.entrySet()) {
            JsonObject mod = new JsonObject();
            mod.addProperty("version", ModList.get().getModContainerById(m.getKey())
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
            mod.addProperty("count", m.getValue().size());
            JsonObject blocks = new JsonObject();
            m.getValue().forEach(blocks::add);
            mod.add("blocks", blocks);
            root.add(m.getKey(), mod);
            ReignOfNether.LOGGER.info("[ModBlockIds] {}: {} blocks", m.getKey(), m.getValue().size());
        }
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
    }

    private static <T extends Comparable<T>> JsonArray values(Property<T> p) {
        JsonArray a = new JsonArray();
        for (T v : p.getPossibleValues())
            a.add(p.getName(v));
        return a;
    }

    private static boolean isFrom(BuildingBlock b, String mod) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(b.getBlockState().getBlock());
        return key != null && mod.equals(key.getNamespace());
    }

    private static String sample(TreeSet<String> ids, int n) {
        List<String> out = new ArrayList<>();
        int step = Math.max(1, ids.size() / n);
        int i = 0;
        for (String id : ids)
            if (i++ % step == 0 && out.size() < n)
                out.add(id);
        return out.toString();
    }
}
