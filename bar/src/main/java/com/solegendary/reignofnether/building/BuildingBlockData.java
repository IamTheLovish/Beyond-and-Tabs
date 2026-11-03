package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.blocks.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

// a class for static functions related to reading building NBT data (as created by Structure Blocks)

public class BuildingBlockData {

    public static ArrayList<BuildingBlock> getBuildingBlocksFromNbt(String structureName, LevelAccessor level) {
        if (level.isClientSide())
            return readBuildingBlocks(structureName, level, Minecraft.getInstance().getResourceManager());
        // the server parses each structure once per resource manager (a /reload brings a new one) and hands out copies:
        // every placement check read and unzipped the .nbt again, and a bot's site search makes hundreds of them
        ResourceManager resourceManager = level.getServer().getResourceManager();
        ArrayList<BuildingBlock> parsed;
        synchronized (SERVER_BLOCKS) {
            if (serverBlocksFor != resourceManager) {
                SERVER_BLOCKS.clear();
                serverBlocksFor = resourceManager;
            }
            parsed = SERVER_BLOCKS.computeIfAbsent(structureName, n -> readBuildingBlocks(n, level, resourceManager));
        }
        ArrayList<BuildingBlock> copy = new ArrayList<>(parsed.size());
        for (BuildingBlock b : parsed)
            copy.add(new BuildingBlock(b.getBlockPos(), b.getBlockState(), b.getBlockNbt() == null ? null : b.getBlockNbt().copy()));
        return copy;
    }

    private static final java.util.Map<String, ArrayList<BuildingBlock>> SERVER_BLOCKS = new java.util.HashMap<>();
    private static ResourceManager serverBlocksFor = null;

    private static ArrayList<BuildingBlock> readBuildingBlocks(String structureName, LevelAccessor level, ResourceManager resourceManager) {
        CompoundTag nbt = getBuildingNbt(structureName, resourceManager);
        // a blueprint missing from this side's jar (assets/ on the client, data/ on the server) used to NPE here -
        // every frame on the client while the ghost was shown; warn once and hand back no blocks instead
        // (BuildingValidators refuses to place an empty building)
        if (nbt == null) {
            if (MISSING_WARNED.add(structureName))
                ReignOfNether.LOGGER.warn("Building blueprint structures/{}.nbt is missing ({} side)", structureName,
                    level.isClientSide() ? "client" : "server");
            return new ArrayList<>();
        }
        return getBuildingBlocksFromNbt(nbt);
    }

    private static final java.util.Set<String> MISSING_WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static ArrayList<BuildingBlock> getBuildingBlocksFromNbt(CompoundTag nbt) {
        ArrayList<BuildingBlock> blocks = new ArrayList<>();

        // load in blocks (list of blockPos and their palette index)
        ListTag blocksNbt = nbt.getList("blocks", 10);

        ArrayList<BlockState> palette = getBuildingPalette(nbt);

        for(int i = 0; i < blocksNbt.size(); i++) {
            CompoundTag blockNbt = blocksNbt.getCompound(i);
            ListTag blockPosNbt = blockNbt.getList("pos", 3);

            BlockPos bp = new BlockPos(
                    blockPosNbt.getInt(0),
                    blockPosNbt.getInt(1),
                    blockPosNbt.getInt(2)
            );
            BlockState bs = palette.get(blockNbt.getInt("state"));
            CompoundTag bNbt = null;
            if (blockNbt.contains("nbt")) {
                bNbt = blockNbt.getCompound("nbt");
            }
            if (BlockUtils.isFallingLogBlock(bs))
                bs = BlockUtils.getNonFallingLog(bs);

            if (bs.getBlock() != Blocks.WATER || bs.getFluidState().isSource())
                blocks.add(new BuildingBlock(bp, bs, bNbt));
        }
        return blocks;
    }

    public static CompoundTag getBuildingNbt(String structureName, ResourceManager resManager) {

        try {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("reignofnether", "structures/" + structureName + ".nbt");
            Optional<Resource> rs = resManager.getResource(rl);
            if (rs.isEmpty()) return null;
            return NbtIo.readCompressed(rs.get().open());
        } catch (IOException e) {
            ReignOfNether.LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    public static ArrayList<BlockState> getBuildingPalette(CompoundTag nbt) {
        ArrayList<BlockState> palette = new ArrayList<>();
        // load in palette (list of unique block states)
        ListTag paletteNbt = nbt.getList("palette", 10);
        for(int i = 0; i < paletteNbt.size(); i++) {
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteNbt.getCompound(i)));
        }
        // NbtUtils.readBlockState turns a block id nobody registered into AIR without a word: a structure naming a
        // block of a missing mod (or a generator typo) would stand with holes in it. Say so, once per id - the
        // GameTest structure_palettes_name_only_registered_blocks fails on it in CI.
        for (String id : unknownBlockIds(nbt))
            if (UNKNOWN_WARNED.add(id))
                ReignOfNether.LOGGER.error("A building structure names block '{}', which is not registered - it loads "
                    + "as air (is the mod that adds it installed?)", id);
        return palette;
    }

    private static final java.util.Set<String> UNKNOWN_WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The block ids a structure's palette(s) name that no installed mod registers (each would load as air). */
    public static java.util.List<String> unknownBlockIds(CompoundTag nbt) {
        java.util.List<String> unknown = new ArrayList<>();
        java.util.List<ListTag> palettes = new ArrayList<>();
        palettes.add(nbt.getList("palette", 10));
        ListTag multi = nbt.getList("palettes", 9);   // vanilla's multi-palette form (none of ours use it yet)
        for (int p = 0; p < multi.size(); p++)
            palettes.add(multi.getList(p));
        for (ListTag pal : palettes)
            for (int i = 0; i < pal.size(); i++) {
                String id = pal.getCompound(i).getString("Name");
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if ((rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) && !unknown.contains(id))
                    unknown.add(id);
            }
        return unknown;
    }

    public static BuildingBlock getBuildingBlockByPos(ArrayList<BuildingBlock> blocks, BlockPos bp) {
        for (BuildingBlock block : blocks) {
            if (block.getBlockPos().equals(bp)) return block;
        }
        return null;
    }
}
