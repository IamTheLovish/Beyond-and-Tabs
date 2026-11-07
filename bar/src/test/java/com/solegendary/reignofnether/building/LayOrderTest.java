package com.solegendary.reignofnether.building;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LayOrder: layer by layer, bottom up, full cubes before the rest of each layer, each group in the file's order. The
 * loader (BuildingBlockData) applies it with each block state's own collision shape; the GameTests in
 * LayOrderGameTests check that on every structure and in the world. Here, without the game's registries, a block is
 * full when its name says so.
 */
class LayOrderTest {

    private static final File DIR = new File("src/main/resources/data/reignofnether/structures");

    /** A cell of a structure: its height, its block name, and its place in the file. */
    private record Cell(int y, String name, int index) {
    }

    // the blocks without a full collision cube that the shipped structures use most (a test's stand-in for the shape)
    private static boolean full(Cell c) {
        String n = c.name;
        return !(n.endsWith("_stairs") || n.endsWith("_slab") || n.endsWith("_wall") || n.endsWith("_fence")
            || n.endsWith("_pane") || n.endsWith("_carpet") || n.endsWith("_trapdoor") || n.endsWith("_door")
            || n.endsWith("torch") || n.endsWith("lantern") || n.endsWith("_button") || n.endsWith("_bars")
            || Set.of("minecraft:vine", "minecraft:glow_lichen", "minecraft:ladder", "minecraft:air", "minecraft:chain",
                "minecraft:water").contains(n));
    }

    private static List<Cell> cells(Object... yAndName) {
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < yAndName.length; i += 2)
            out.add(new Cell((Integer) yAndName[i], (String) yAndName[i + 1], i / 2));
        return out;
    }

    private static List<String> names(List<Cell> cells) {
        List<String> out = new ArrayList<>();
        for (Cell c : cells)
            out.add(c.y + " " + c.name);
        return out;
    }

    @Test
    void aVineListedBeforeTheStoneItHangsOnIsLaidAfterIt() {
        // the round-13 Heartwood Hall's eave corner as its generator wrote it: the vine, the stair beside it, the pier
        List<Cell> layer = cells(6, "minecraft:vine", 6, "minecraft:stone_brick_stairs", 6, "minecraft:stripped_dark_oak_wood");
        LayOrder.sort(layer, Cell::y, LayOrderTest::full);
        assertEquals(List.of("6 minecraft:stripped_dark_oak_wood", "6 minecraft:vine", "6 minecraft:stone_brick_stairs"),
            names(layer));
    }

    @Test
    void layersStayBottomUpAndEachGroupKeepsTheFileOrder() {
        // a structure block's file: every full cube first (layers mixed in), then the rest
        List<Cell> file = cells(1, "minecraft:stone", 0, "minecraft:dirt", 1, "minecraft:oak_planks", 0, "minecraft:moss_carpet",
            1, "minecraft:vine", 0, "minecraft:torch", 1, "minecraft:lantern");
        LayOrder.sort(file, Cell::y, LayOrderTest::full);
        assertEquals(List.of("0 minecraft:dirt", "0 minecraft:moss_carpet", "0 minecraft:torch", "1 minecraft:stone",
            "1 minecraft:oak_planks", "1 minecraft:vine", "1 minecraft:lantern"), names(file));
    }

    @Test
    void everyShippedStructureComesOutInLayOrder() throws IOException {
        File[] files = DIR.listFiles((d, n) -> n.endsWith(".nbt"));
        assertTrue(files != null && files.length >= 100, "structures found: " + (files == null ? 0 : files.length));
        int reordered = 0;
        for (File f : files) {
            List<Cell> cells = read(f);
            List<Cell> laid = new ArrayList<>(cells);
            LayOrder.sort(laid, Cell::y, LayOrderTest::full);
            assertEquals(cells.size(), laid.size(), f.getName());
            int y = Integer.MIN_VALUE;
            boolean rest = false;
            for (Cell c : laid) {
                if (c.y != y) {
                    assertTrue(c.y > y, f.getName() + ": layer " + c.y + " after " + y);
                    y = c.y;
                    rest = false;
                }
                if (full(c))
                    assertTrue(!rest, f.getName() + ": a full " + c.name + " in layer " + y + " after a non-full block");
                else
                    rest = true;
            }
            if (!laid.equals(cells))
                reordered++;
        }
        // the shipped files' own order is not lay order (z then x in a layer from the generators, or full cubes first
        // across all layers from a structure block): most of them move
        assertTrue(reordered >= files.length / 2, reordered + " of " + files.length + " structures reordered");
    }

    private static List<Cell> read(File f) throws IOException {
        CompoundTag root = NbtIo.readCompressed(f);
        ListTag palette = root.getList("palette", 10);
        ListTag blocks = root.getList("blocks", 10);
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            String name = palette.getCompound(b.getInt("state")).getString("Name");
            out.add(new Cell(b.getList("pos", 3).getInt(1), name, i));
        }
        return out;
    }
}
