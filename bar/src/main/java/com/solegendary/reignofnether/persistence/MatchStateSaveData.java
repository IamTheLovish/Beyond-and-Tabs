package com.solegendary.reignofnether.persistence;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.mana.LeyNetwork;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.mana.ManaWellServerEvents;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nonnull;

/**
 * Saves the in-memory match registries that have no block or entity of their own to ride on (design/persistence.md):
 * Stormcaller Mana Wells ({@link ManaWellServerEvents}), ley links ({@link LeyNetwork}), Tidewrought tidepools with
 * their remaining lifetime ({@link TidepoolServerEvents}) and the faction terraform auras with the ground each holds
 * and what it was (building.FactionAuraServerEvents). Each owning class knows its own format
 * ({@code saveTag()} / {@code restore(server, list)}); this is only the SavedData shell around them, the same
 * {@code computeIfAbsent} pattern as BuildingSaveData / ResearchSaveData.
 * <p>
 * Unlike those, nothing copies state into this object on every change: {@link #save(CompoundTag)} snapshots the live
 * registries when the world is written, and {@link #isDirty()} is always true so every autosave writes it (a few
 * hundred bytes, every few minutes - cheaper than marking it dirty from every raise, link and expiry). One catch: the
 * registries clear themselves on ServerStoppingEvent, which Forge fires <i>before</i> the final save, so a
 * highest-priority stopping handler takes the snapshot first and the final save writes that.
 * <p>
 * Loading: {@link #load} only keeps the tag; {@link #onServerStarted} restores it once every level exists. All restores
 * are idempotent, so a stale snapshot can never double a well, link or pool.
 */
public class MatchStateSaveData extends SavedData {

    static final String NAME = "bt-match-state";
    static final String WELLS = "wells", LEY = "ley", TIDEPOOLS = "tidepools";
    // faction terraform auras with the ground they hold and what it was (building/FactionAuraServerEvents)
    static final String AURAS = "auras";
    // the match's economy pace code (EconomyPace) - match-wide, so a plain int, not an owner list
    static final String PACE = "pace";

    /** Tag read from disk and not yet restored (null once applied). */
    private CompoundTag pending = null;
    /** Snapshot taken at ServerStoppingEvent, written by the final save instead of the (already cleared) registries. */
    private CompoundTag stopSnapshot = null;

    private static MatchStateSaveData create() {
        return new MatchStateSaveData();
    }

    @Nonnull
    public static MatchStateSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return create();
        return server.overworld().getDataStorage()
            .computeIfAbsent(MatchStateSaveData::load, MatchStateSaveData::create, NAME);
    }

    public static MatchStateSaveData load(CompoundTag tag) {
        MatchStateSaveData data = create();
        data.pending = tag.copy();
        return data;
    }

    /** The live registries as one tag. Public for the game test. */
    public static CompoundTag snapshot() {
        CompoundTag tag = new CompoundTag();
        tag.put(WELLS, ManaWellServerEvents.saveTag());
        tag.put(LEY, LeyNetwork.saveTag());
        tag.put(TIDEPOOLS, TidepoolServerEvents.saveTag());
        tag.put(AURAS, com.solegendary.reignofnether.building.FactionAuraServerEvents.saveTag());
        tag.putInt(PACE, com.solegendary.reignofnether.resources.EconomyPace.getMatchCode());
        return tag;
    }

    /** Restores a {@link #snapshot()} into the live registries (idempotent). Public for the game test. */
    public static void restore(MinecraftServer server, CompoundTag tag) {
        int wells = ManaWellServerEvents.restore(server, tag.getList(WELLS, Tag.TAG_COMPOUND));
        int links = LeyNetwork.restore(server, tag.getList(LEY, Tag.TAG_COMPOUND));
        int pools = TidepoolServerEvents.restore(server, tag.getList(TIDEPOOLS, Tag.TAG_COMPOUND));
        int auras = com.solegendary.reignofnether.building.FactionAuraServerEvents.restore(server, tag.getList(AURAS, Tag.TAG_COMPOUND));
        // only a real int restores the pace: the game tests' per-owner snapshot filter turns every key into a list,
        // and an absent key (a world saved before the option existed) keeps Standard
        if (tag.contains(PACE, Tag.TAG_INT))
            com.solegendary.reignofnether.resources.EconomyPace.setMatchCode(tag.getInt(PACE));
        if (wells + links + pools + auras > 0)
            ReignOfNether.LOGGER.info("MatchStateSaveData: restored {} mana wells, {} ley links, {} tidepools, {} faction auras",
                wells, links, pools, auras);
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) {
        // not restored yet (saved before the server finished starting): keep what was on disk rather than an empty world
        CompoundTag src = stopSnapshot != null ? stopSnapshot : pending != null ? pending : snapshot();
        for (String key : src.getAllKeys()) {
            Tag t = src.get(key);
            if (t != null)
                tag.put(key, t.copy());
        }
        return tag;
    }

    // the registries change every few seconds and nothing marks this dirty: always write it (see the class comment)
    @Override
    public boolean isDirty() {
        return true;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        MinecraftServer server = evt.getServer();
        // statics outlive an integrated server: a world opened after another in the same game must not inherit its
        // wells or links (ManaServerEvents' well registry has no stop handler of its own)
        ManaServerEvents.clearAll();
        com.solegendary.reignofnether.resources.EconomyPace.reset();   // likewise the pace of the last world
        MatchStateSaveData data = getInstance(server.overworld());
        data.stopSnapshot = null;
        if (data.pending != null) {
            CompoundTag tag = data.pending;
            data.pending = null;
            restore(server, tag);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStopping(ServerStoppingEvent evt) {
        MinecraftServer server = evt.getServer();
        if (server == null || server.overworld() == null)
            return;
        MatchStateSaveData data = getInstance(server.overworld());
        if (data.pending == null)
            data.stopSnapshot = snapshot();
    }
}
