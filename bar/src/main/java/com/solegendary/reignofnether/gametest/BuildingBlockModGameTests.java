package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingBlockData;
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
 * Resourceful Lib). Structures name their blocks by id, and Minecraft turns an id nobody registered into AIR without
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
        MIN_BLOCKS.put("mcwroofs", 100);
        MIN_BLOCKS.put("mcwwindows", 100);
        MIN_BLOCKS.put("mcwfences", 40);
        MIN_BLOCKS.put("chipped", 500);
    }
    static final List<String> REQUIRED_MODS =
        List.of("mcwroofs", "mcwwindows", "mcwfences", "chipped", "athena", "resourcefullib");

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void building_block_mods_are_loaded_with_their_blocks(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
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
