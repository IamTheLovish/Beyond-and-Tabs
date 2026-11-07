package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Our own tree templates (tools/gen_trees.py -> data/reignofnether/structures/trees/*.nbt) written into the world:
 * the one entry every battle-map tree goes through - the battlefield biome's trees (worldgen, TemplateTreeFeature) and
 * the pre-made maps' trees (MapBuilder.decorate places the same configured features). Read once from the jar, then
 * written block by block with Block.UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE, turned and mirrored round the trunk with the
 * block states' own rotate / mirror (StructureTemplate's order: mirror, then rotate). Not
 * StructureTemplate.placeInWorld: that sends block updates (and so leaf distance ticks) across a whole forest.
 *
 * Only air and replaceable plants are written over: a tree never eats a wall, a pad or another tree. A trunk log at the
 * template's ground layer standing over air (a tree on a step) gets up to {@link #MAX_FOOT_FILL} blocks of dirt under
 * it, and ground plants of the template's skirt only go where there is soil under them. The templates' leaves carry
 * their distance (persistent=false), so they neither decay nor stop being a natural tree to resources/TreeClearing.
 * Learned from draft #358 (Oct 29), which first placed these templates on the pre-made maps.
 */
public final class TreeTemplates {
    private TreeTemplates() { }

    /** A template: its x / z size, every block {x, y, z} with the trunk at (sizeX / 2, sizeZ / 2), and its state. */
    public record Template(int sizeX, int sizeZ, List<int[]> pos, List<BlockState> states) { }

    private static final Map<String, Template> CACHE = new ConcurrentHashMap<>();
    private static final Template MISSING = new Template(0, 0, List.of(), List.of());
    /** Under each ground-layer log over air, at most this many blocks of dirt. */
    static final int MAX_FOOT_FILL = 2;
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** The template trees/{name}, or null when it is missing or unreadable (logged once). */
    public static Template load(String name) {
        Template t = CACHE.computeIfAbsent(name, n -> {
            Template r = read(BuiltInRegistries.BLOCK.asLookup(), n);
            if (r == null)
                ReignOfNether.LOGGER.warn("[TreeTemplates] tree template {} could not be read", n);
            return r == null ? MISSING : r;
        });
        return t == MISSING ? null : t;
    }

    private static Template read(HolderGetter<Block> blocks, String name) {
        String path = "/data/" + ReignOfNether.MOD_ID + "/structures/trees/" + name + ".nbt";
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
                if (s.isAir() || s.is(Blocks.STRUCTURE_VOID))
                    continue;
                ListTag p = b.getList("pos", 3);
                pos.add(new int[] { p.getInt(0), p.getInt(1), p.getInt(2) });
                states.add(s);
            }
            // the trunk is the pivot: every position is shifted so it sits at (sizeX / 2, sizeZ / 2)
            int sx = size.getInt(0), sz = size.getInt(2);
            int[] pivot = pivot(pos, states, sx, sz);
            for (int[] p : pos) {
                p[0] += sx / 2 - pivot[0];
                p[2] += sz / 2 - pivot[1];
            }
            return new Template(sx, sz, pos, states);
        } catch (Exception e) {
            return null;
        }
    }

    /** {x, z} of the trunk: the ground-layer (y 0) log nearest the template's centre, else the centre itself. */
    static int[] pivot(List<int[]> pos, List<BlockState> states, int sx, int sz) {
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < pos.size(); i++) {
            int[] p = pos.get(i);
            if (p[1] == 0 && states.get(i).is(BlockTags.LOGS)) {
                double d = Math.hypot(p[0] - sx / 2.0, p[2] - sz / 2.0);
                if (d < bd) {
                    bd = d;
                    best = new int[] { p[0], p[2] };
                }
            }
        }
        return best != null ? best : new int[] { sx / 2, sz / 2 };
    }

    /** (dx, dz) from the trunk after the mirror (x flipped) and then the turn (quarter turns clockwise). */
    static int[] turn(int dx, int dz, int quarterTurns, boolean mirror) {
        if (mirror)
            dx = -dx;
        return switch (quarterTurns & 3) {
            case 1 -> new int[] { -dz, dx };
            case 2 -> new int[] { -dx, -dz };
            case 3 -> new int[] { dz, -dx };
            default -> new int[] { dx, dz };
        };
    }

    static Rotation rotation(int quarterTurns) {
        return switch (quarterTurns & 3) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * Writes the tree with its trunk at {@code trunk} (the first block above the ground) turned and mirrored. Returns
     * the blocks written, or -1 if the template could not be read.
     */
    public static int place(WorldGenLevel level, Template t, BlockPos trunk, int quarterTurns, boolean mirror) {
        Rotation rot = rotation(quarterTurns);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int written = 0;
        int cx = t.sizeX() / 2, cz = t.sizeZ() / 2;
        for (int i = 0; i < t.pos().size(); i++) {
            int[] at = t.pos().get(i);
            int[] o = turn(at[0] - cx, at[2] - cz, quarterTurns, mirror);
            BlockState s = t.states().get(i);
            if (mirror)
                s = s.mirror(Mirror.FRONT_BACK);
            s = s.rotate(rot);
            p.set(trunk.getX() + o[0], trunk.getY() + at[1], trunk.getZ() + o[1]);
            BlockState cur = level.getBlockState(p);
            if (!cur.isAir() && !cur.canBeReplaced())
                continue;
            boolean wood = s.is(BlockTags.LOGS), leaf = s.is(BlockTags.LEAVES);
            if (!wood && !leaf && !level.getBlockState(p.below()).is(BlockTags.DIRT))
                continue;   // a skirt plant over a drop: never a floating flower
            level.setBlock(p, s, FLAGS);
            written++;
            if (at[1] == 0 && wood)
                written += fillFoot(level, p.getX(), p.getY(), p.getZ());
        }
        return written;
    }

    /** Dirt under a ground-layer log standing over air or plants, at most MAX_FOOT_FILL blocks down. */
    private static int fillFoot(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int d = 1; d <= MAX_FOOT_FILL; d++) {
            BlockState below = level.getBlockState(p.set(x, y - d, z));
            if (!below.isAir() && !below.canBeReplaced())
                break;
            level.setBlock(p, Blocks.DIRT.defaultBlockState(), FLAGS);
            n++;
        }
        return n;
    }
}
