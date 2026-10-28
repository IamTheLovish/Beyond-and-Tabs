package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The Wager Book's temporary blocks (earth walls, a mound, a campfire): placed only into air, never inside a building or
 * a blueprint's bounds, with nobody standing there - and always taken down again, on their own clock rather than the
 * hero's (a wall outlives a hero who dies, never the phenomenon's 90 s).
 * <p>
 * Mode A B2: an entry is only forgotten once its block is really gone. At its time with its chunk unloaded it waits and
 * goes on the first sweep after the chunk is loaded again; a server stop takes down what is loaded and keeps the rest;
 * the list lives in the overworld's SavedData ("bat_wager_blocks"), so a restart or a crash still cleans up. A block
 * someone changed meanwhile is left alone (the entry is just dropped).
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class WagerBlocks {
    private WagerBlocks() { }

    static final String DATA_NAME = "bat_wager_blocks";
    static final int SWEEP_EVERY = 20;

    private record Placed(ResourceKey<Level> dim, BlockPos pos, BlockState state, long until) { }

    private static final List<Placed> PLACED = new ArrayList<>();
    @Nullable private static Data data;

    /** Places {@code state} at {@code pos} until {@code until} if the spot is free; true if it did. */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, long until) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()
                || BuildingUtils.findBuildingOrBlueprint(false, pos) != null
                || !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty())
            return false;
        level.setBlockAndUpdate(pos, state);
        PLACED.add(new Placed(level.dimension(), pos.immutable(), state, until));
        dirty();
        return true;
    }

    /** Takes down this level's due blocks whose chunk is loaded; due ones in unloaded chunks wait. */
    public static void sweep(ServerLevel level, long now, Predicate<BlockPos> loaded) {
        boolean changed = false;
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed p = PLACED.get(i);
            if (p.dim == level.dimension() && now >= p.until && loaded.test(p.pos)) {
                takeDown(level, p);
                PLACED.remove(i);
                changed = true;
            }
        }
        if (changed)
            dirty();
    }

    /** A stop: the loaded ones come down now (early), the unloaded ones stay listed for the next start. */
    public static void stop(ServerLevel level, Predicate<BlockPos> loaded) {
        boolean changed = false;
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed p = PLACED.get(i);
            if (p.dim == level.dimension() && loaded.test(p.pos)) {
                takeDown(level, p);
                PLACED.remove(i);
                changed = true;
            }
        }
        if (changed)
            dirty();
    }

    public static int pendingAt(ServerLevel level, BlockPos pos) {
        int n = 0;
        for (Placed p : PLACED)
            if (p.dim == level.dimension() && p.pos.equals(pos))
                n++;
        return n;
    }

    /** The list as saved: {blocks: [{dim, x, y, z, until, state}]}. */
    public static CompoundTag saveTag() {
        ListTag list = new ListTag();
        for (Placed p : PLACED) {
            CompoundTag e = new CompoundTag();
            e.putString("dim", p.dim.location().toString());
            e.putInt("x", p.pos.getX());
            e.putInt("y", p.pos.getY());
            e.putInt("z", p.pos.getZ());
            e.putLong("until", p.until);
            e.put("state", NbtUtils.writeBlockState(p.state));
            list.add(e);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("blocks", list);
        return tag;
    }

    /** Adds the saved entries that are not listed yet (a start, or a test's restart). */
    public static void loadTag(@Nullable MinecraftServer server, CompoundTag tag) {
        for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("dim"));
            if (dim == null)
                continue;
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
            BlockPos pos = new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z"));
            boolean listed = false;
            for (Placed p : PLACED)
                if (p.dim == key && p.pos.equals(pos))
                    listed = true;
            if (!listed)
                PLACED.add(new Placed(key, pos, NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    e.getCompound("state")), e.getLong("until")));
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        PLACED.clear();   // a world loaded after another in the same game starts from its own list
        data = evt.getServer().overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || PLACED.isEmpty() || evt.getServer().getTickCount() % SWEEP_EVERY != 0)
            return;
        for (ServerLevel level : evt.getServer().getAllLevels())
            sweep(level, level.getGameTime(), level::isLoaded);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        // the levels and their SavedData are written after this event: what stays listed is saved
        for (ServerLevel level : evt.getServer().getAllLevels())
            stop(level, level::isLoaded);
        dirty();
    }

    private static void takeDown(ServerLevel level, Placed p) {
        if (level.getBlockState(p.pos).is(p.state.getBlock()))   // a campfire the rain put out still goes
            level.removeBlock(p.pos, false);
    }

    private static void dirty() {
        if (data != null)
            data.setDirty();
    }

    /** The overworld's saved copy of the list. */
    static final class Data extends SavedData {
        static Data load(CompoundTag tag) {
            loadTag(null, tag);
            return new Data();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.put("blocks", saveTag().getList("blocks", Tag.TAG_COMPOUND));
            return tag;
        }
    }
}
