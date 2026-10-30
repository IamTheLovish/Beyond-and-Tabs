package com.solegendary.reignofnether.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Our own architectural moulding blocks, slice 1 (lovish Oct 29 2026: the Engine Shed "looks blocky and toy"; he
 * approved "our own architectural blocks (custom models)"): a cornice / string course (straight, outer and inner
 * corner as its `shape`), a window sill, a lintel (with or without a `keystone`) and the arch ring of a 3-wide round
 * arch (its `piece`s). Written before the blocks (red on main): the ids and properties below are the spec, typed
 * here by hand, not read from the implementation.
 * <p>
 * Every block must be registered with an item and ship a blockstate (every variant of it), block models, an item
 * model, a loot table and an en_us name; every state must have a partial collision shape (not empty, not a full
 * cube) that turns with its facing, and must never occlude its neighbours as a full cube (no culled faces behind a
 * partial shape, no x-ray); every texture its models name must be a vanilla minecraft:block/ texture that exists, so
 * the realistic resource pack's retexture of bricks, smooth stone, deepslate tiles and copper reaches the mouldings.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArchBlocksGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    /** id -> the properties it must have besides facing. */
    static Map<String, String[]> expected() {
        Map<String, String[]> ids = new java.util.LinkedHashMap<>();
        for (String m : new String[] { "stone", "brick", "slate", "copper" })
            ids.put("cornice_" + m, new String[] { "shape" });
        for (String m : new String[] { "stone", "brick", "slate" }) {
            ids.put("string_course_" + m, new String[] { "shape" });
            ids.put("sill_" + m, new String[0]);
            ids.put("lintel_" + m, new String[] { "keystone" });
        }
        for (String m : new String[] { "stone", "brick" })
            ids.put("arch_ring_" + m, new String[] { "piece" });
        return ids;
    }

    static JsonObject json(String path, List<String> problems) {
        try (InputStream in = ArchBlocksGameTests.class.getResourceAsStream(path)) {
            if (in == null) {
                problems.add(path + " is missing");
                return null;
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            problems.add(path + " unreadable: " + e);
            return null;
        }
    }

    static boolean resourceExists(String path) {
        try (InputStream in = ArchBlocksGameTests.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    static Block block(String id) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, id);
        return ForgeRegistries.BLOCKS.containsKey(rl) ? ForgeRegistries.BLOCKS.getValue(rl) : null;
    }

    /** The blockstate file's variant key for a state: "facing=north,shape=outer" (property names sorted). */
    static String variantKey(BlockState s) {
        List<String> parts = new ArrayList<>();
        for (Property<?> p : s.getProperties())
            parts.add(p.getName() + "=" + valueName(s, p));
        parts.sort(String::compareTo);
        return String.join(",", parts);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static String valueName(BlockState s, Property p) {
        return p.getName(s.getValue(p));
    }

    /** Model ids the blockstate file names (reignofnether:block/x -> x). */
    static Set<String> modelsOf(JsonObject bs) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, JsonElement> v : bs.getAsJsonObject("variants").entrySet()) {
            JsonElement e = v.getValue();
            JsonObject o = e.isJsonArray() ? e.getAsJsonArray().get(0).getAsJsonObject() : e.getAsJsonObject();
            out.add(o.get("model").getAsString());
        }
        return out;
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): "
                + String.join("; ", problems.subList(0, Math.min(12, problems.size()))));
    }

    /** Registered, with an item, the properties of the spec, and every resource file a block needs to load and drop. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void arch_blocks_are_registered_with_blockstate_model_loot_and_name(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        JsonObject lang = json("/assets/reignofnether/lang/en_us.json", problems);
        for (Map.Entry<String, String[]> e : expected().entrySet()) {
            String id = e.getKey();
            Block b = block(id);
            if (b == null) {
                problems.add(id + " is not a registered block");
                continue;
            }
            if (!ForgeRegistries.ITEMS.containsKey(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, id)))
                problems.add(id + " has no block item");
            if (!b.defaultBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING))
                problems.add(id + " has no horizontal facing");
            for (String prop : e.getValue())
                if (b.getStateDefinition().getProperty(prop) == null)
                    problems.add(id + " has no '" + prop + "' property");
            JsonObject bs = json("/assets/reignofnether/blockstates/" + id + ".json", problems);
            if (bs != null) {
                if (!bs.has("variants")) {
                    problems.add(id + " blockstate has no variants");
                } else {
                    for (BlockState s : b.getStateDefinition().getPossibleStates())
                        if (!bs.getAsJsonObject("variants").has(variantKey(s)))
                            problems.add(id + " blockstate lacks variant " + variantKey(s));
                    for (String model : modelsOf(bs))
                        if (!model.startsWith(ReignOfNether.MOD_ID + ":block/"))
                            problems.add(id + " blockstate names a model outside our block models: " + model);
                        else
                            json("/assets/reignofnether/models/block/"
                                + model.substring((ReignOfNether.MOD_ID + ":block/").length()) + ".json", problems);
                }
            }
            json("/assets/reignofnether/models/item/" + id + ".json", problems);
            JsonObject loot = json("/data/reignofnether/loot_tables/blocks/" + id + ".json", problems);
            if (loot != null && !loot.toString().contains("reignofnether:" + id))
                problems.add(id + " loot table does not drop itself");
            if (lang != null && !lang.has("block.reignofnether." + id))
                problems.add(id + " has no en_us name");
        }
        finish(helper, problems);
    }

    /**
     * Every texture of every model a blockstate names is minecraft:block/<name> and that texture is in the game (so a
     * resource pack's retexture of the vanilla block reaches the moulding): no reignofnether: texture, no other mod's.
     * Where the client's assets are on the classpath the .png itself must be there; the CI GameTest server has no
     * client assets (run 40992160121: even block/bricks.png is absent), so there the texture must be the texture of a
     * registered vanilla block of the same name (bricks, smooth_stone, polished_deepslate, weathered_cut_copper - each
     * a full block whose single texture carries its name). The eyes client run loads the models with no missing-texture
     * warning (PR #407).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void arch_block_models_use_only_vanilla_textures_that_exist(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        boolean clientAssets = resourceExists("/assets/minecraft/textures/block/stone.png");
        int checked = 0;
        for (String id : expected().keySet()) {
            JsonObject bs = json("/assets/reignofnether/blockstates/" + id + ".json", problems);
            if (bs == null || !bs.has("variants"))
                continue;
            for (String model : modelsOf(bs)) {
                String name = model.substring(model.indexOf("block/") + "block/".length());
                JsonObject m = json("/assets/reignofnether/models/block/" + name + ".json", problems);
                if (m == null)
                    continue;
                if (!m.has("textures") || m.getAsJsonObject("textures").size() == 0) {
                    problems.add(name + " model names no textures");
                    continue;
                }
                for (Map.Entry<String, JsonElement> t : m.getAsJsonObject("textures").entrySet()) {
                    String v = t.getValue().getAsString();
                    if (v.startsWith("#"))
                        continue;
                    if (!v.startsWith("minecraft:block/")) {
                        problems.add(name + " texture " + t.getKey() + " = " + v + " is not a vanilla minecraft:block/ texture");
                        continue;
                    }
                    checked++;
                    String tex = v.substring("minecraft:block/".length());
                    String png = "/assets/minecraft/textures/block/" + tex + ".png";
                    if (clientAssets && !resourceExists(png))
                        problems.add(name + " texture " + v + " does not exist (" + png + ")");
                    if (!clientAssets && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(
                            ResourceLocation.fromNamespaceAndPath("minecraft", tex)))
                        problems.add(name + " texture " + v + " is not the texture of a vanilla block");
                }
            }
        }
        if (checked == 0)
            problems.add("no texture was checked");
        finish(helper, problems);
    }

    /**
     * Every state placed in the world: a partial collision shape (not empty, not a full cube); the east-facing shape is
     * the north-facing one turned a quarter clockwise; and no full-cube occlusion.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void arch_blocks_have_partial_shapes_that_turn_and_never_occlude_as_a_cube(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BlockPos rel = new BlockPos(3, 3, 3);
        BlockPos abs = helper.absolutePos(rel);
        int states = 0;
        for (String id : expected().keySet()) {
            Block b = block(id);
            if (b == null || !b.defaultBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                problems.add(id + " is not registered with a facing");
                continue;
            }
            for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                helper.setBlock(rel, s);
                BlockState placed = helper.getLevel().getBlockState(abs);
                if (placed != s) {
                    problems.add(variantKey(s) + " of " + id + " did not place as itself");
                    continue;
                }
                states++;
                VoxelShape c = placed.getCollisionShape(helper.getLevel(), abs);
                if (c.isEmpty())
                    problems.add(id + "[" + variantKey(s) + "] has no collision");
                else if (!Shapes.joinIsNotEmpty(Shapes.block(), c, BooleanOp.NOT_SAME))
                    problems.add(id + "[" + variantKey(s) + "] collides as a full cube");
                if (placed.canOcclude())
                    problems.add(id + "[" + variantKey(s) + "] occludes its neighbours like a full cube");
                if (s.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH && !c.isEmpty()) {
                    BlockState east = s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
                    helper.setBlock(rel, east);
                    AABB n = c.bounds(), e = helper.getLevel().getBlockState(abs).getCollisionShape(helper.getLevel(), abs).bounds();
                    // a quarter turn clockwise seen from above: (x, z) -> (1 - z, x)
                    AABB want = new AABB(1 - n.maxZ, n.minY, n.minX, 1 - n.minZ, n.maxY, n.maxX);
                    if (Math.abs(want.minX - e.minX) > 1e-6 || Math.abs(want.maxX - e.maxX) > 1e-6
                            || Math.abs(want.minZ - e.minZ) > 1e-6 || Math.abs(want.maxZ - e.maxZ) > 1e-6
                            || Math.abs(want.minY - e.minY) > 1e-6 || Math.abs(want.maxY - e.maxY) > 1e-6)
                        problems.add(id + "[" + variantKey(s) + "] east shape " + e + " is not north " + n + " turned a quarter");
                }
            }
        }
        helper.setBlock(rel, Blocks.AIR);
        if (states == 0)
            problems.add("no state was checked");
        finish(helper, problems);
    }
}
