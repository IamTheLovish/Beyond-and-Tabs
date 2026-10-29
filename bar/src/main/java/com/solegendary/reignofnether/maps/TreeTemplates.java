package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps spec M6: our tree templates (tools/gen_trees.py, data/reignofnether/structures/trees/*.nbt) written into the
 * world for MapBuilder - read once from the jar, then placed block by block with MapBuilder.FLAGS, turned and mirrored
 * by TreeScatter.offset and the block states' own rotate / mirror. Not StructureTemplate.placeInWorld: that sends
 * block updates (and so leaf distance ticks) across the whole forest while the map builds.
 *
 * Only air and plants are replaced (grass, flowers, the old ground cover): a tree never eats a wall, a pad or another
 * tree. The trunk's foot is fitted to the ground: a log at the template's ground layer over air gets up to two blocks
 * of the map's subsoil under it, so a tree on a step never floats.
 */
public final class TreeTemplates {
    private TreeTemplates() { }

    /** A template read from the jar: its x / z size and every block, {x, y, z} with the state. */
    record Template(int sizeX, int sizeZ, List<int[]> pos, List<BlockState> states) { }

    private static final Map<String, Template> CACHE = new HashMap<>();
    /** Under each ground-layer log over air, at most this many blocks of subsoil. */
    static final int MAX_FOOT_FILL = 2;

    /** The template, or null when the file is missing or unreadable (MapBuilder falls back to vanilla's tree). */
    static Template load(ServerLevel level, String name) {
        if (CACHE.containsKey(name))
            return CACHE.get(name);
        Template t = read(level.holderLookup(Registries.BLOCK), name);
        if (t == null)
            ReignOfNether.LOGGER.warn("[MapBuilder] tree template {} could not be read - vanilla tree instead", name);
        CACHE.put(name, t);
        return t;
    }

    private static Template read(HolderGetter<Block> blocks, String name) {
        String path = "/data/" + ReignOfNether.MOD_ID + "/structures/" + name + ".nbt";
        try (InputStream in = TreeTemplates.class.getResourceAsStream(path)) {
            if (in == null)
                return null;
            CompoundTag nbt = NbtIo.readCompressed(in);
            ListTag size = nbt.getList("size", 3);
            ListTag palette = nbt.getList("palette", 10);
            List<BlockState> pal = new ArrayList<>();
            for (int i = 0; i < palette.size(); i++)
                pal.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
            ListTag list = nbt.getList("blocks", 10);
            List<int[]> pos = new ArrayList<>();
            List<BlockState> states = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag b = list.getCompound(i);
                BlockState s = pal.get(b.getInt("state"));
                if (s.isAir())
                    continue;
                ListTag p = b.getList("pos", 3);
                pos.add(new int[] { p.getInt(0), p.getInt(1), p.getInt(2) });
                states.add(s);
            }
            return new Template(size.getInt(0), size.getInt(2), pos, states);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes the tree with its trunk column at (x, z), its ground layer (template y 0) at groundY + 1. Returns the
     * blocks written, or -1 if the template could not be read.
     */
    static int place(ServerLevel level, TreeScatter.Tree tree, int x, int z, int groundY, BlockState subsoil) {
        Template t = load(level, tree.template());
        if (t == null)
            return -1;
        Rotation rot = switch (tree.rotation()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int written = 0;
        for (int i = 0; i < t.pos().size(); i++) {
            int[] o = TreeScatter.offset(t.pos().get(i), t.sizeX(), t.sizeZ(), tree.rotation(), tree.mirror());
            BlockState s = t.states().get(i);
            if (tree.mirror())
                s = s.mirror(Mirror.FRONT_BACK);
            s = s.rotate(rot);
            p.set(x + o[0], groundY + 1 + o[1], z + o[2]);
            BlockState cur = level.getBlockState(p);
            if (!cur.isAir() && !cur.canBeReplaced())
                continue;
            level.setBlock(p, s, MapBuilder.FLAGS);
            written++;
            if (o[1] == 0 && s.is(BlockTags.LOGS))
                written += fillFoot(level, p.getX(), p.getY(), p.getZ(), subsoil);
        }
        return written;
    }

    /** Subsoil under a ground-layer log standing over air or plants, at most MAX_FOOT_FILL blocks down. */
    private static int fillFoot(ServerLevel level, int x, int y, int z, BlockState subsoil) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int d = 1; d <= MAX_FOOT_FILL; d++) {
            BlockState below = level.getBlockState(p.set(x, y - d, z));
            if (!below.isAir() && !below.canBeReplaced())
                break;
            level.setBlock(p, subsoil, MapBuilder.FLAGS);
            n++;
        }
        return n;
    }
}
