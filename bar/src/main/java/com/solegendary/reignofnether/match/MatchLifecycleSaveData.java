package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.maps.MapMatch;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.RTSPlayerSaveData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Saves the match lifecycle with the world, in {@code <world>/data/bt-match-lifecycle.dat} (design/persistence.md):
 * the {@link MatchState} ({@code state}), the alliances between the match's sides ({@code alliances}) and the sides
 * whose match is over ({@code finished}, RTSPlayerSaveData's own format). Before this, alliances were not saved at
 * all - a reloaded team game turned teammates into enemies - and a reload forgot who had finished, so a winner could
 * no longer spectate and the results lost the beaten sides' rows.
 * <p>
 * The same shell as persistence/MatchStateSaveData (which keeps the pace and the faction registries): {@link #save}
 * snapshots the live state whenever the world is written ({@link #isDirty} is always true: a few hundred bytes per
 * autosave, cheaper than marking every pick and tick dirty); the live statics clear themselves on ServerStoppingEvent,
 * which Forge fires <i>before</i> the final save, so MatchServerEvents takes a highest-priority snapshot first and the
 * final save writes that; {@link #load} only keeps the tag, and MatchServerEvents restores it on ServerStartedEvent,
 * once the sides (RTSPlayerSaveData) and every level are back - read at server start, never lazily on first use.
 */
public class MatchLifecycleSaveData extends SavedData {

    static final String NAME = "bt-match-lifecycle";
    static final String STATE = "state", ALLIANCES = "alliances", FINISHED = "finished";

    /** Tag read from disk and not restored yet (null once applied). */
    private CompoundTag pending = null;
    /** Snapshot taken at ServerStoppingEvent, written by the final save instead of the (already cleared) statics. */
    private CompoundTag stopSnapshot = null;

    private static MatchLifecycleSaveData create() {
        return new MatchLifecycleSaveData();
    }

    @Nonnull
    public static MatchLifecycleSaveData getInstance(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(MatchLifecycleSaveData::load, MatchLifecycleSaveData::create, NAME);
    }

    public static MatchLifecycleSaveData load(CompoundTag tag) {
        MatchLifecycleSaveData data = create();
        data.pending = tag.copy();
        return data;
    }

    /** The live lifecycle as one tag. Public for the game tests. */
    public static CompoundTag snapshot(MatchState state) {
        CompoundTag tag = new CompoundTag();
        tag.put(STATE, state.save());
        Set<String> names = new LinkedHashSet<>();
        for (RTSPlayer p : MatchRoster.LIVE)
            names.add(p.name);
        for (RTSPlayer p : MatchRoster.FINISHED)
            if (p != null)
                names.add(p.name);
        if (state.match() != null)
            names.addAll(state.match().participants);
        tag.put(ALLIANCES, alliancePairs(names));
        RTSPlayerSaveData finished = RTSPlayerSaveData.load(new CompoundTag());
        for (RTSPlayer p : MatchRoster.FINISHED)
            if (p != null)
                finished.rtsPlayers.add(p);
        tag.put(FINISHED, finished.save(new CompoundTag()));
        return tag;
    }

    /** Every alliance between two of {@code names}, each pair once: [[a, b], ...]. Public for the game tests. */
    public static ListTag alliancePairs(Set<String> names) {
        List<String> all = new ArrayList<>(names);
        ListTag pairs = new ListTag();
        for (int i = 0; i < all.size(); i++)
            for (int j = i + 1; j < all.size(); j++)
                if (AlliancesServerEvents.isAllied(all.get(i), all.get(j))) {
                    ListTag pair = new ListTag();
                    pair.add(StringTag.valueOf(all.get(i)));
                    pair.add(StringTag.valueOf(all.get(j)));
                    pairs.add(pair);
                }
        return pairs;
    }

    /** Re-forms saved alliances (idempotent: addAlliance on an existing pair changes nothing). Returns the count. */
    public static int restoreAlliances(ListTag pairs) {
        int n = 0;
        for (int i = 0; i < pairs.size(); i++) {
            ListTag pair = pairs.getList(i);
            if (pair.size() != 2 || pair.getString(0).isEmpty() || pair.getString(1).isEmpty())
                continue;
            AlliancesServerEvents.addAlliance(pair.getString(0), pair.getString(1));
            n++;
        }
        return n;
    }

    /**
     * Restores a {@link #snapshot} as the live lifecycle (server start). {@code tag} null: a world saved before the
     * lifecycle was (or a fresh one) - sides already in play then make it a classic match.
     */
    static void restore(MinecraftServer server, @Nullable CompoundTag tag) {
        MatchState state = MatchState.load(tag == null ? null : tag.getCompound(STATE), MapMatch.EFFECTS);
        MatchState.replaceLive(state);
        // statics outlive an integrated server: a world opened after another in the same game must not inherit its
        // alliances or finished sides
        AlliancesServerEvents.resetAllAlliances();
        MatchRoster.FINISHED.clear();
        if (tag == null) {
            state.adoptRunningSides();
            state.resume();
            return;
        }
        int alliances = restoreAlliances(tag.getList(ALLIANCES, Tag.TAG_LIST));
        RTSPlayerSaveData finished = RTSPlayerSaveData.load(tag.getCompound(FINISHED));
        MatchRoster.FINISHED.addAll(finished.rtsPlayers);
        ReignOfNether.LOGGER.info("[Match] restored a {} match: clock {}, {} alliance(s), {} finished side(s)",
            state.phase(), state.clock(), alliances, finished.rtsPlayers.size());
        state.resume();
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) {
        // not restored yet (saved before the server finished starting): keep what was on disk rather than nothing
        CompoundTag src = stopSnapshot != null ? stopSnapshot : pending != null ? pending : snapshot(MatchState.live());
        for (String key : src.getAllKeys()) {
            Tag t = src.get(key);
            if (t != null)
                tag.put(key, t.copy());
        }
        return tag;
    }

    // the state changes every tick (the clock, a pick) and nothing marks this dirty: always write it
    @Override
    public boolean isDirty() {
        return true;
    }

    /** Server start: the saved lifecycle becomes the live one (or a clean one for a world without). */
    static void onServerStarted(MinecraftServer server) {
        MatchLifecycleSaveData data = getInstance(server);
        data.stopSnapshot = null;
        CompoundTag tag = data.pending;
        data.pending = null;
        restore(server, tag);
    }

    /** Before the stopping handlers clear the statics: the snapshot the final save writes. */
    static void onServerStopping(MinecraftServer server) {
        if (server == null || server.overworld() == null)
            return;
        MatchLifecycleSaveData data = getInstance(server);
        if (data.pending == null)
            data.stopSnapshot = snapshot(MatchState.live());
    }
}
