package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everything a match being prepared needs to get from Start to seated, frozen when Start is pressed (maps.MapMatch
 * used to keep it in the closures it handed to MapBuilder and StartPhase, so a reload lost it: code_review CR-13).
 * <p>
 * Whatever the world or chance decided at Start is stored, not recomputed: the colours made unique, the pre-made
 * map's base height (the centre's ground is the half-stamped map after a reload), the random-terrain ring's ground
 * sites, and one seed for the faction rolls - so a resumed setup seats exactly the match the players set up.
 * Immutable apart from the cached layout; game thread only.
 */
public final class MatchSetup {

    public final MatchState.Origin origin;
    /** Every seat handed over at Start (players, heroes, spectators), copies, colours already made unique. */
    public final List<MultiplayerLobby.Seat> lineup;
    public final MapPreset preset;
    public final boolean choosePositions;
    public final int arena, metal, spawnDistance;
    /** The lobby centre (the host's ground). */
    public final int cx, cy, cz;
    /** Seeds the seat-time faction rolls (Random factions). */
    public final long seed;
    /** Pre-made map: the stamped map's base height. */
    public final int baseY;
    /** Random terrain: the start ring's radius, the wall's radius and each slot's ground site from the centre. */
    public final int ringRadius, arenaRadius;
    public final List<int[]> ringOffsets;

    private MapLayout layout = null;   // rebuilt from the fields above on first use (deterministic)

    private MatchSetup(MatchState.Origin origin, List<MultiplayerLobby.Seat> lineup, MapPreset preset, boolean choose,
                       int arena, int metal, int spawnDistance, int cx, int cy, int cz, long seed, int baseY,
                       int ringRadius, int arenaRadius, List<int[]> ringOffsets) {
        this.origin = origin;
        this.lineup = Collections.unmodifiableList(lineup);
        this.preset = preset;
        this.choosePositions = choose;
        this.arena = arena;
        this.metal = metal;
        this.spawnDistance = spawnDistance;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.seed = seed;
        this.baseY = baseY;
        this.ringRadius = ringRadius;
        this.arenaRadius = arenaRadius;
        this.ringOffsets = Collections.unmodifiableList(ringOffsets);
    }

    /**
     * Freezes a lobby or skirmish lineup at Start: unique colours written into copies of the seats, the map's base
     * height or the ring's ground sites read from the world now, one seed drawn from {@code rng}.
     */
    public static MatchSetup create(ServerLevel level, List<MultiplayerLobby.Seat> seats, MapPreset preset,
                                    boolean choose, int arena, int metal, int spawnDistance, BlockPos centre,
                                    Random rng, MatchState.Origin origin) {
        List<MultiplayerLobby.Seat> copies = new ArrayList<>(seats.size());
        for (MultiplayerLobby.Seat s : seats)
            copies.add(copy(s));
        List<MultiplayerLobby.Seat> playing = MultiplayerLobby.playingSorted(copies);
        for (Map.Entry<MultiplayerLobby.Seat, Integer> e : MultiplayerLobby.assignColours(playing).entrySet())
            e.getKey().colorMapId = e.getValue();
        int cx = centre.getX(), cz = centre.getZ();
        int baseY = 0, ringR = 0, arenaR = 0;
        List<int[]> offsets = new ArrayList<>();
        if (preset.premade() && playing.size() >= 2) {
            // the centre's ground, kept in a band where water and cliffs look right
            baseY = Math.max(62, Math.min(90, MetalPatches.solidTop(level, cx, cz)));
        } else {
            // random terrain: the lobby's fair ring (rotated so slot 0 sits west, like the layouts' boxes)
            ringR = MultiplayerLobby.ringRadius(playing.size(), spawnDistance);
            for (int[] t : MultiplayerLobby.ringTargets(centre, playing.size(), ringR, Math.PI)) {
                Vec3 g = MultiplayerLobby.groundSite(level, t[0], t[1]);
                offsets.add(new int[] { (int) Math.floor(g.x) - cx, (int) Math.floor(g.z) - cz });
            }
            int wall = SkirmishServerboundPacket.arenaRadius(arena);
            arenaR = wall > 0 ? wall : ringR + 130;
        }
        return new MatchSetup(origin, copies, preset, choose, arena, metal, spawnDistance, cx, centre.getY(), cz,
            rng.nextLong(), baseY, ringR, arenaR, offsets);
    }

    static MultiplayerLobby.Seat copy(MultiplayerLobby.Seat s) {
        MultiplayerLobby.Seat c = new MultiplayerLobby.Seat(s.id, s.bot);
        c.name = s.name;
        c.uuid = s.uuid;
        c.faction = s.faction;
        c.colorMapId = s.colorMapId;
        c.team = s.team;
        c.ready = s.ready;
        c.difficulty = s.difficulty;
        c.heroRole = s.heroRole;
        return c;
    }

    // ------------------------------------------------------------------------------------------ derived

    /** The seats that play, teammates side by side (the slot order). */
    public List<MultiplayerLobby.Seat> playing() {
        return MultiplayerLobby.playingSorted(lineup);
    }

    /** The Hero-role seats (design/hero_mode.md): attached to their team at seat time, no base of their own. */
    public List<MultiplayerLobby.Seat> heroes() {
        return MultiplayerLobby.heroSeats(lineup);
    }

    /** Each playing seat's colour, keyed by the seat object as MultiplayerLobby.seatAll expects. */
    public Map<MultiplayerLobby.Seat, Integer> colours() {
        Map<MultiplayerLobby.Seat, Integer> out = new IdentityHashMap<>();
        for (MultiplayerLobby.Seat s : playing())
            out.put(s, s.colorMapId);
        return out;
    }

    /** The seat with this id, or null. */
    @Nullable
    public MultiplayerLobby.Seat seat(int id) {
        for (MultiplayerLobby.Seat s : lineup)
            if (s.id == id)
                return s;
        return null;
    }

    /** A pre-made map is stamped before anyone is seated (with fewer than two sides the map is skipped). */
    public boolean raisesMap() {
        return preset.premade() && playing().size() >= 2;
    }

    /** The start-position phase runs: asked for, at least two sides, and a human to place (bots never wait). */
    public boolean runsStartPhase() {
        List<MultiplayerLobby.Seat> playing = playing();
        if (!choosePositions || playing.size() < 2)
            return false;
        for (MultiplayerLobby.Seat s : playing)
            if (!s.bot)
                return true;
        return false;
    }

    public MapLayout layout() {
        if (layout == null) {
            List<MultiplayerLobby.Seat> playing = playing();
            int[] teams = new int[playing.size()];
            for (int i = 0; i < teams.length; i++)
                teams[i] = playing.get(i).team;
            layout = raisesMap() ? MapLayout.build(preset, teams)
                : MapLayout.ring(teams, new ArrayList<>(ringOffsets), ringRadius, arenaRadius);
        }
        return layout;
    }

    // ------------------------------------------------------------------------------------------ save / load

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("origin", origin.name());
        tag.putString("preset", preset.name());
        tag.putBoolean("choose", choosePositions);
        tag.putInt("arena", arena);
        tag.putInt("metal", metal);
        tag.putInt("spawns", spawnDistance);
        tag.putInt("cx", cx);
        tag.putInt("cy", cy);
        tag.putInt("cz", cz);
        tag.putLong("seed", seed);
        tag.putInt("baseY", baseY);
        tag.putInt("ringRadius", ringRadius);
        tag.putInt("arenaRadius", arenaRadius);
        int[] flat = new int[ringOffsets.size() * 2];
        for (int i = 0; i < ringOffsets.size(); i++) {
            flat[i * 2] = ringOffsets.get(i)[0];
            flat[i * 2 + 1] = ringOffsets.get(i)[1];
        }
        tag.putIntArray("ring", flat);
        ListTag seats = new ListTag();
        for (MultiplayerLobby.Seat s : lineup) {
            CompoundTag st = new CompoundTag();
            st.putInt("id", s.id);
            st.putBoolean("bot", s.bot);
            st.putString("name", s.name);
            if (s.uuid != null)
                st.putUUID("uuid", s.uuid);
            st.putInt("faction", s.faction);
            st.putInt("colour", s.colorMapId);
            st.putInt("team", s.team);
            st.putBoolean("ready", s.ready);
            st.putInt("difficulty", s.difficulty);
            st.putBoolean("hero", s.heroRole);
            seats.add(st);
        }
        tag.put("lineup", seats);
        return tag;
    }

    /**
     * A saved setup, or null when it can't be resumed as saved (no lineup, a pre-made map this build doesn't know,
     * a random-terrain ring that doesn't match its seats) - the caller cancels the setup cleanly instead.
     */
    @Nullable
    public static MatchSetup load(CompoundTag tag) {
        if (tag == null || !tag.contains("lineup", Tag.TAG_LIST)) {
            ReignOfNether.LOGGER.warn("[Match] saved setup has no lineup");
            return null;
        }
        MapPreset preset = null;
        for (MapPreset p : MapPreset.values())
            if (p.name().equals(tag.getString("preset")))
                preset = p;
        if (preset == null) {
            ReignOfNether.LOGGER.warn("[Match] saved setup names an unknown map '{}'", tag.getString("preset"));
            return null;
        }
        List<MultiplayerLobby.Seat> lineup = new ArrayList<>();
        ListTag seats = tag.getList("lineup", Tag.TAG_COMPOUND);
        for (int i = 0; i < seats.size(); i++) {
            CompoundTag st = seats.getCompound(i);
            MultiplayerLobby.Seat s = new MultiplayerLobby.Seat(st.getInt("id"), st.getBoolean("bot"));
            s.name = st.getString("name");
            s.uuid = st.hasUUID("uuid") ? st.getUUID("uuid") : null;
            s.faction = st.getInt("faction");
            s.colorMapId = st.getInt("colour");
            s.team = st.getInt("team");
            s.ready = st.getBoolean("ready");
            s.difficulty = st.getInt("difficulty");
            s.heroRole = st.getBoolean("hero");
            lineup.add(s);
        }
        int[] flat = tag.getIntArray("ring");
        List<int[]> offsets = new ArrayList<>(flat.length / 2);
        for (int i = 0; i + 1 < flat.length; i += 2)
            offsets.add(new int[] { flat[i], flat[i + 1] });
        MatchSetup setup = new MatchSetup(MatchState.Origin.byName(tag.getString("origin")), lineup, preset,
            tag.getBoolean("choose"), tag.getInt("arena"), tag.getInt("metal"), tag.getInt("spawns"),
            tag.getInt("cx"), tag.getInt("cy"), tag.getInt("cz"), tag.getLong("seed"), tag.getInt("baseY"),
            tag.getInt("ringRadius"), tag.getInt("arenaRadius"), offsets);
        if (setup.playing().isEmpty()) {
            ReignOfNether.LOGGER.warn("[Match] saved setup has nobody to seat");
            return null;
        }
        if (!setup.raisesMap() && offsets.size() != setup.playing().size()) {
            ReignOfNether.LOGGER.warn("[Match] saved setup's ring has {} sites for {} sides", offsets.size(), setup.playing().size());
            return null;
        }
        return setup;
    }
}
