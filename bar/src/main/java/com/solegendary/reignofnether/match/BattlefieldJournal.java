package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the battlefield looked like before the match touched it, so "Back to lobby" can put it back (the tester's ask,
 * Oct 26: "keep playing on one server rather than making new ones, which means we need a way to reset the map").
 * <p>
 * While armed, every block a loaded chunk changes in the match's dimension (LevelChunkMixin: LevelChunk.setBlockState
 * is the one chokepoint for buildings, explosions, fire, fluids, digging and our own stamps; worldgen writes ProtoChunks
 * and never lands here) is recorded ONCE, with the state it had before its first change. {@link #restore} writes every
 * recorded block back and disarms. Recording starts when the battlefield opens to the sides (MatchState: after the
 * preparation - so a pre-made map's forge and the prepared wall are the "map before the match" and are not undone -
 * or just before an instant lobby start seats everyone), for lobby and skirmish matches; a new preparation starts a
 * fresh journal. Classic matches (sides started one by one, which is also how the game tests start sides) do not
 * record.
 * <p>
 * Saved with the world ({@code <world>/data/bt-battlefield-journal.dat}), so a server restart mid-match still resets
 * cleanly. Capped at {@link #MAX_ENTRIES}: past that it logs an error once and stops taking new positions - fail loud,
 * the restore then puts back what it has. Block entity contents (chest items) are not kept: a restored chest comes
 * back empty. Game thread only.
 */
public final class BattlefieldJournal extends SavedData {

    static final String NAME = "bt-battlefield-journal";
    /** Positions kept at most: a long 4v4 changes tens of thousands; this is two orders of magnitude of headroom. */
    public static final int MAX_ENTRIES = 2_000_000;
    /** Restores tell clients but wake no neighbours, drop nothing and keep known shapes: the block is simply put back. */
    public static final int RESTORE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** The running server's journal while it records, else null (read by the mixin on every block change). */
    @Nullable private static volatile BattlefieldJournal recording = null;
    /**
     * Bounded journals the game tests run beside the live one (the test server is shared: a test must never record,
     * or restore, another test's blocks). Empty in play.
     */
    private static final List<BattlefieldJournal> probes = new CopyOnWriteArrayList<>();

    private final Long2ObjectOpenHashMap<BlockState> originals = new Long2ObjectOpenHashMap<>();
    private ResourceKey<Level> dimension = Level.OVERWORLD;
    private boolean armed = false;
    private boolean full = false;
    @Nullable private BoundingBox limit = null;   // probes only

    // ------------------------------------------------------------------------------------------ the hook

    /** LevelChunkMixin: {@code old} is the state the chunk replaced (null when nothing changed). */
    public static void onBlockChanged(Level level, BlockPos pos, @Nullable BlockState old) {
        BattlefieldJournal j = recording;
        if ((j == null && probes.isEmpty()) || old == null || level.isClientSide())
            return;
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread())
            return;   // only the game thread writes the battlefield; anything else is not the match's doing
        if (j != null && level.dimension() == j.dimension)
            j.record(pos, old);
        for (BattlefieldJournal p : probes)
            if (level.dimension() == p.dimension && p.limit != null && p.limit.isInside(pos))
                p.record(pos, old);
    }

    void record(BlockPos pos, BlockState old) {
        long key = pos.asLong();
        if (originals.containsKey(key))
            return;
        if (originals.size() >= MAX_ENTRIES) {
            if (!full) {
                full = true;
                ReignOfNether.LOGGER.error("[Journal] the battlefield journal is full ({} blocks): blocks changed from now on"
                    + " will NOT be restored by Back to lobby", MAX_ENTRIES);
            }
            return;
        }
        originals.put(key, old);
        setDirty();
    }

    // ------------------------------------------------------------------------------------------ probes (game tests)

    /** A test journal that records only inside {@code box} of {@code level} until {@link #close} or a restore. */
    public static BattlefieldJournal probe(ServerLevel level, BoundingBox box) {
        BattlefieldJournal p = new BattlefieldJournal();
        p.dimension = level.dimension();
        p.limit = box;
        p.armed = true;
        probes.add(p);
        return p;
    }

    /** Stop a {@link #probe} recording. */
    public void close() {
        probes.remove(this);
    }

    /** Blocks this journal keeps. */
    public int kept() {
        return originals.size();
    }

    /** The state kept for {@code pos}, null when it was never changed while recording. */
    @Nullable
    public BlockState original(BlockPos pos) {
        return originals.get(pos.asLong());
    }

    // ------------------------------------------------------------------------------------------ the live journal

    @Nullable
    private static BattlefieldJournal of(@Nullable MinecraftServer server) {
        if (server == null)
            return null;
        return server.overworld().getDataStorage().computeIfAbsent(BattlefieldJournal::load, BattlefieldJournal::new, NAME);
    }

    /**
     * Start recording on the overworld (the match's level) if not already: a journal that already records keeps its
     * originals, so a match fought over an ENDED one still restores to before the first.
     */
    public static void arm() {
        BattlefieldJournal j = of(ServerLifecycleHooks.getCurrentServer());
        if (j == null)
            return;
        if (!j.armed) {
            j.armed = true;
            j.full = false;
            j.dimension = Level.OVERWORLD;
            j.setDirty();
            ReignOfNether.LOGGER.info("[Journal] recording the battlefield ({} block(s) already kept)", j.originals.size());
        }
        recording = j;
    }

    /** Forget everything recorded and stop recording (a new preparation re-forms the battlefield). */
    public static void discard() {
        recording = null;
        BattlefieldJournal j = of(ServerLifecycleHooks.getCurrentServer());
        if (j == null)
            return;
        if (j.armed || !j.originals.isEmpty())
            ReignOfNether.LOGGER.info("[Journal] discarded ({} block(s) were kept)", j.originals.size());
        j.originals.clear();
        j.armed = false;
        j.full = false;
        j.setDirty();
    }

    /**
     * Stop recording and write every kept block back as it was before the match, then forget them. Returns how many
     * blocks were written. The chunks are loaded as needed (a reset is one deliberate pause, not a per-tick cost).
     */
    public static int restore(MinecraftServer server) {
        recording = null;   // first: the restore's own writes are not the match's
        BattlefieldJournal j = of(server);
        if (j == null)
            return 0;
        ServerLevel level = server.getLevel(j.dimension);
        if (level == null) {
            ReignOfNether.LOGGER.error("[Journal] the journal's level {} is gone: {} block(s) NOT restored",
                j.dimension.location(), j.originals.size());
            j.originals.clear();
            j.armed = false;
            j.full = false;
            j.setDirty();
            return 0;
        }
        return j.restoreInto(level);
    }

    /**
     * Writes every kept block back into {@code level}, forgets them and stops recording (a probe is closed too).
     * The writes themselves are not recorded: this journal is disarmed first. Returns the blocks written.
     */
    public int restoreInto(ServerLevel level) {
        boolean wasProbe = probes.remove(this);
        if (recording == this)
            recording = null;
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<BlockState> e : originals.long2ObjectEntrySet()) {
            p.set(e.getLongKey());
            if (level.getBlockState(p) != e.getValue())
                level.setBlock(p, e.getValue(), RESTORE_FLAGS);
            n++;
        }
        if (!wasProbe)
            ReignOfNether.LOGGER.info("[Journal] restored {} block(s){}", n, full ? " (the journal had filled up:"
                + " later changes were not kept)" : "");
        originals.clear();
        armed = false;
        full = false;
        setDirty();
        return n;
    }

    public static boolean isRecording() {
        return recording != null;
    }

    /** Server start (MatchServerEvents): a journal saved while recording records again. */
    public static void onServerStarted(MinecraftServer server) {
        BattlefieldJournal j = of(server);
        recording = j != null && j.armed ? j : null;
        if (j != null && (j.armed || !j.originals.isEmpty()))
            ReignOfNether.LOGGER.info("[Journal] loaded: {} block(s), {}", j.originals.size(), j.armed ? "recording" : "idle");
    }

    /** Server stop: the instance stays with the level's data storage for the final save; nothing records any more. */
    public static void onServerStopping() {
        recording = null;
    }

    // ------------------------------------------------------------------------------------------ save / load

    public static BattlefieldJournal load(CompoundTag tag) {
        BattlefieldJournal j = new BattlefieldJournal();
        j.armed = tag.getBoolean("armed");
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dimension"));
        j.dimension = dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim);
        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++)
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i)));
        long[] pos = tag.getLongArray("pos");
        int[] state = tag.getIntArray("state");
        if (pos.length != state.length)
            ReignOfNether.LOGGER.error("[Journal] the saved journal is damaged ({} positions, {} states): reading the shorter",
                pos.length, state.length);
        int n = Math.min(pos.length, state.length);
        for (int i = 0; i < n; i++)
            if (state[i] >= 0 && state[i] < palette.size())
                j.originals.put(pos[i], palette.get(state[i]));
        return j;
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) {
        tag.putBoolean("armed", armed);
        tag.putString("dimension", dimension.location().toString());
        Map<BlockState, Integer> index = new HashMap<>();
        ListTag palette = new ListTag();
        long[] pos = new long[originals.size()];
        int[] state = new int[originals.size()];
        int i = 0;
        for (Long2ObjectMap.Entry<BlockState> e : originals.long2ObjectEntrySet()) {
            Integer id = index.get(e.getValue());
            if (id == null) {
                id = palette.size();
                index.put(e.getValue(), id);
                palette.add(NbtUtils.writeBlockState(e.getValue()));
            }
            pos[i] = e.getLongKey();
            state[i] = id;
            i++;
        }
        tag.put("palette", palette);
        tag.putLongArray("pos", pos);
        tag.putIntArray("state", state);
        return tag;
    }
}
