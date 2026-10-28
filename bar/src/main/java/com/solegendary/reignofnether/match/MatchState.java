package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.MapMatch;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.StartPhase;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The match lifecycle as one state machine (design/ron_rewrite_plan.md, rewrite 5): {@link MatchPhase} LOBBY ->
 * MAP_RISING -> START_POSITIONS -> PLAYING -> ENDED, with this class the only owner of a phase change. It replaces
 * what was spread over RoN's PlayerServerEvents (a match "existed" while rtsPlayers was non-empty), maps.MapMatch's
 * {@code preparing} flag with callbacks closed over the lineup, and MultiplayerLobby's {@code liveMatch} - none of
 * which survived a save, which is code_review CR-13: a world saved while a pre-made map rose or starts were being
 * placed reloaded with a half-raised map, no match, and late starts allowed again.
 * <p>
 * What each phase keeps ({@link #save}): MAP_RISING and START_POSITIONS the frozen {@link MatchSetup} (and how far
 * the battlefield's preparation got / every pick and the clock); PLAYING and ENDED the seated match's participants
 * and humans (who wins, who may rejoin, who watches) and ENDED its winners and the time it was decided at (its sides
 * get the results again on logging in, {@link #owesResults}); always the match clock and the map in play. The world
 * itself (blocks, units, buildings) and the sides (RTSPlayerSaveData) are saved by their owners as before.
 * <p>
 * MAP_RISING is wider than its name since spec 3 (inbox/spec-3, section 2.1): it covers the whole preparation
 * ({@link MatchPrep} - the pre-made map's forge, the start clearing, the arena wall, the arena's chunks, the lights)
 * for every lobby or skirmish start with two or more sides, random terrain included, so that nothing heavy runs
 * once the players are placing starts or playing. The name stays: phases are saved by name and the table is pinned.
 * <p>
 * Reload = <b>resume</b>, not cancel (code_review CR-13 asked to pick one): every preparation stage is idempotent,
 * so it is simply run again from the saved stage - finishing a half-raised map or a half-built wall, which a cancel
 * could never take back; a start-position phase comes back with every pick and the clock it had, held until a human
 * is online again (nobody is auto-placed while still loading in) and then given at least
 * StartPhase.RESUME_FLOOR_SECONDS. Only a setup that can't be resumed (a map this build doesn't know, a lineup that
 * doesn't fit) is cancelled - cleanly, to LOBBY, with a chat line - see {@link #resumePlan}.
 * <p>
 * Effects (the preparation, the start phase, seating, chat) go through {@link MatchEffects}, so the game tests
 * drive real instances through every transition without touching the server's live one ({@link #live}). Game
 * thread only.
 */
public final class MatchState {

    /** Who started the current match: decides who may still join it, and who is told it began. Saved by name. */
    public enum Origin {
        /**
         * Sides started one by one (RoN's start screen and start blocks, /bot add, Quick Battle's instant start):
         * victory is decided among every side on the server.
         */
        CLASSIC,
        /** The multiplayer lobby: its humans own their seats, anyone else who joins watches. */
        LOBBY,
        /** The singleplayer skirmish screen, through a pre-made map and/or the start-position phase. */
        SKIRMISH;

        public static Origin byName(String name) {
            for (Origin o : values())
                if (o.name().equals(name))
                    return o;
            return CLASSIC;
        }
    }

    /** What {@link #resume} will do with a state loaded from a save. */
    public enum ResumePlan {
        /** LOBBY, ENDED: nothing to restart. */
        NOTHING,
        /** MAP_RISING below READY: run the (idempotent) preparation again from the saved stage. */
        REBUILD_MAP,
        /** MAP_RISING saved READY (on the preparation's last tick): go on to the starts. */
        AFTER_MAP,
        /** START_POSITIONS: the phase comes back with its picks and clock, held until a human is back. */
        CONTINUE_START_PHASE,
        /** PLAYING: the seated match takes its participants back (victory scope, late joiners watch). */
        REJOIN_MATCH,
        /** A setup that can't be resumed as saved: back to LOBBY, told in chat. */
        CANCEL
    }

    static final int VERSION = 1;

    // ------------------------------------------------------------------------------------------ the live match

    private static MatchState live = new MatchState(MapMatch.EFFECTS);

    /** The server's one match. Replaced at server start by the saved one (MatchLifecycleSaveData). */
    public static MatchState live() {
        return live;
    }

    static void replaceLive(MatchState state) {
        live = state;
    }

    // ------------------------------------------------------------------------------------------ state

    private final MatchEffects effects;
    private MatchPhase phase = MatchPhase.LOBBY;
    private Origin origin = Origin.CLASSIC;
    @Nullable private MatchSetup setup = null;           // MAP_RISING, START_POSITIONS
    @Nullable private StartPhase startPhase = null;      // START_POSITIONS (the instance players click into)
    @Nullable private MultiplayerMatch match = null;     // PLAYING, ENDED; null for a CLASSIC match
    private final Set<String> winners = new LinkedHashSet<>();
    private long clock = 0;                              // match time in ticks (PlayerServerEvents.rtsGameTicks)
    private long endClock = 0;                           // ENDED: the match time it was decided at (the results)
    private MapPreset mapPreset = MapPreset.RANDOM;      // the map in play (fog tint, name), kept after the start
    private int mapCx, mapCz, mapRadius;
    private boolean seating = false;                     // inside a seat: the sides joining are this match's
    // loaded from a save and not resumed yet
    private int savedBuilderStage = -1;
    @Nullable private CompoundTag savedBuilderDetail = null;
    @Nullable private CompoundTag savedStartPhase = null;
    // the caller's own "seated" hook: transient, a reload never has it (origin-specific work is done here instead)
    @Nullable private Consumer<MultiplayerMatch> onSeated = null;
    // what a resume did, for everyone who logs in while it still applies (at server start nobody is online to be
    // told); any later phase change makes it stale
    @Nullable private String resumeNotice = null;

    public MatchState(MatchEffects effects) {
        this.effects = effects;
    }

    public MatchPhase phase() {
        return phase;
    }

    public Origin origin() {
        return origin;
    }

    @Nullable
    public MatchSetup setup() {
        return setup;
    }

    @Nullable
    public StartPhase startPhase() {
        return startPhase;
    }

    @Nullable
    public MultiplayerMatch match() {
        return match;
    }

    public Set<String> winners() {
        return Collections.unmodifiableSet(winners);
    }

    public long clock() {
        return clock;
    }

    /** The match time an ENDED match was decided at (the clock itself drops to 0 once nobody is in play). */
    public long endClock() {
        return endClock;
    }

    /**
     * Whether {@code name} took part in this decided match and so gets its results again on logging in (they are
     * client memory: after a reload the popup - and with it Spectate - was gone, and a winner's Match Menu offered
     * Resign). Sides of a classic match are known by having finished.
     */
    public boolean owesResults(String name) {
        if (phase != MatchPhase.ENDED || name == null)
            return false;
        if (winners.contains(name))
            return true;
        return match != null ? match.participants.contains(name) : MatchRoster.isFinished(name);
    }

    public MapPreset mapPreset() {
        return mapPreset;
    }

    public int mapCx() {
        return mapCx;
    }

    public int mapCz() {
        return mapCz;
    }

    public int mapRadius() {
        return mapRadius;
    }

    public boolean isPreparing() {
        return phase.preparing();
    }

    /** What the last resume did, while it still applies (told to players as they log in), else null. */
    @Nullable
    public String resumeNotice() {
        return resumeNotice;
    }

    /** The multiplayer lobby's match while it is being fought, else null. */
    @Nullable
    public MultiplayerMatch lobbyMatch() {
        MultiplayerMatch m = match;
        return phase == MatchPhase.PLAYING && origin == Origin.LOBBY && m != null && !m.isOver() ? m : null;
    }

    /**
     * Whether {@code name} may not start a base by hand: nobody while a match is being prepared (the setup seats
     * everyone), and nobody but its own humans while a lobby match is fought (they watch instead).
     */
    public boolean refusesLateStart(String name) {
        // the setup's own seating runs while the phase is still MAP_RISING / START_POSITIONS and seats humans through
        // PlayerServerEvents.startRTS - that call must not be refused as a "late start". lovish's playtest of b4f3610
        // (Oct 13, Chorus Isles vs one bot): "match started: 1 side(s), teams {1=[Bot1]}", he was told he was
        // spectating and his build orders were rejected; bots were unaffected because addBotAt never asks here.
        if (seating)
            return false;
        if (phase.preparing())
            return true;
        MultiplayerMatch m = lobbyMatch();
        return m != null && !m.humans.contains(name);
    }

    // ------------------------------------------------------------------------------------------ transitions

    private boolean enter(MatchPhase to, String why) {
        if (!MatchPhase.canMove(phase, to)) {
            ReignOfNether.LOGGER.warn("[Match] refused {} -> {} ({})", phase, to, why);
            return false;
        }
        ReignOfNether.LOGGER.info("[Match] {} -> {} ({})", phase, to, why);
        phase = to;
        resumeNotice = null;
        return true;
    }

    /**
     * A lobby or skirmish Start with a pre-made map and/or the start-position phase (maps.MapMatch.begin): from an
     * idle world to MAP_RISING (the battlefield is prepared first - the map, the clearing, the wall, the arena's
     * chunks), or, with fewer than two sides, straight on to the start phase or to PLAYING. {@code onSeated} is the
     * caller's own hook for the seated match (not saved). False, and nothing changed, while another match is being
     * prepared or fought.
     */
    public boolean prepare(MatchSetup newSetup, @Nullable Consumer<MultiplayerMatch> onSeated) {
        abandonIfNobodyPlays();
        if (newSetup == null || !phase.idle() || seating) {
            ReignOfNether.LOGGER.warn("[Match] refused to prepare a match while {}", phase);
            return false;
        }
        // fog needs a bounded play area: fit the world border round this match's arena before any side is seated
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null)
            com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.fitBorderForMatch(server.overworld(),
                newSetup.cx + 0.5, newSetup.cz + 0.5, newSetup.layout().radius, newSetup.layout().radius, false);
        setup = newSetup;
        origin = newSetup.origin;
        this.onSeated = onSeated;
        // a new battlefield is formed: what an earlier match changed is this one's starting ground (BattlefieldJournal)
        if (this == live)
            BattlefieldJournal.discard();
        match = null;
        winners.clear();
        endClock = 0;
        startPhase = null;
        if (newSetup.prepares()) {
            // random terrain shows as RANDOM (no fog tint, no name) but with its arena, which is now known at Start
            showMap(newSetup.raisesMap() ? newSetup.preset : MapPreset.RANDOM, newSetup.cx, newSetup.cz,
                newSetup.layout().outerRadius());
            enter(MatchPhase.MAP_RISING, (newSetup.raisesMap() ? newSetup.preset.displayName : "the battlefield")
                + " for " + newSetup.playing().size() + " sides");
            effects.prepare(this, -1, null);
        } else {
            showMap(MapPreset.RANDOM, newSetup.cx, newSetup.cz, 0);
            afterMap();
        }
        return true;
    }

    /** The battlefield is READY (MatchPrep's end, or a resume that found it saved READY). */
    public void mapRaised() {
        if (phase != MatchPhase.MAP_RISING || setup == null) {
            ReignOfNether.LOGGER.warn("[Match] a map finished rising while {} - ignored", phase);
            return;
        }
        afterMap();
    }

    /**
     * A preparation stage threw (MatchPrep): the match being set up is dropped cleanly, to LOBBY with a chat line,
     * exactly as a setup that cannot resume is - never left half-way with the phase stuck at MAP_RISING.
     */
    public void prepFailed(String why) {
        if (phase != MatchPhase.MAP_RISING) {
            ReignOfNether.LOGGER.warn("[Match] a preparation failed while {} - ignored ({})", phase, why);
            return;
        }
        cancel(why, "The battlefield could not be prepared (" + why + "). Start a new match from the lobby.");
    }

    private void afterMap() {
        // the battlefield is ready: from here every block the match changes is kept for Back to lobby
        if (this == live)
            BattlefieldJournal.arm();
        StartPhase ph = effects.newStartPhase(this);
        if (setup.runsStartPhase()) {
            if (!enter(MatchPhase.START_POSITIONS, ph.picks.size() + " sides placing starts"))
                return;
            startPhase = ph;
            effects.beginStartPhase(this, ph, false);
        } else {
            ph.finish();   // everyone at their slot: comes straight back into startsPlaced
        }
    }

    /** The start positions are final (clock, everyone placed, or no phase at all): seat everyone where they stand. */
    public void startsPlaced(StartPhase ph) {
        MatchSetup done = setup;
        if (done == null || !(phase.preparing() || phase.idle())) {
            ReignOfNether.LOGGER.warn("[Match] start positions finished with nothing to seat while {} - ignored", phase);
            return;
        }
        if (startPhase != null && ph != startPhase) {
            ReignOfNether.LOGGER.warn("[Match] a stale start phase finished - ignored");
            return;
        }
        MultiplayerMatch m;
        seating = true;
        try {
            m = effects.seat(this, ph);
        } finally {
            seating = false;
        }
        startPhase = null;
        setup = null;
        if (m == null) {
            // seating failed part-way (the effect logged and told everyone): whoever made it in plays on as a
            // classic match, else there is no match at all
            if (MatchRoster.any()) {
                origin = Origin.CLASSIC;
                enter(MatchPhase.PLAYING, "seated only partly");
                com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.enableForNewMatch();
            } else if (phase != MatchPhase.LOBBY) {
                enter(MatchPhase.LOBBY, "nobody could be seated");
            }
            onSeated = null;
            return;
        }
        adopt(m, done.origin);
    }

    /**
     * The lobby's instant start (random terrain, no start phase): {@code seat} seats the lineup and returns its
     * match, which becomes this one. Null when refused (a match is already being prepared or fought).
     */
    @Nullable
    public MultiplayerMatch seatNow(Supplier<MultiplayerMatch> seat, Origin how) {
        abandonIfNobodyPlays();
        if (!phase.idle() || seating) {
            ReignOfNether.LOGGER.warn("[Match] refused an instant start while {}", phase);
            return null;
        }
        MultiplayerMatch m;
        // an instant lobby start: the bases are about to land, so the journal records from before them
        if (this == live)
            BattlefieldJournal.arm();
        seating = true;
        try {
            m = seat.get();
        } finally {
            seating = false;
        }
        if (m != null) {
            setup = null;
            onSeated = null;
            adopt(m, how);
        }
        return m;
    }

    /**
     * A PLAYING match with nobody left in play (the last side beaten with no one to win, every winner spectating
     * before the end was recorded) is over in all but name: a new start may replace it, as it always could.
     */
    private void abandonIfNobodyPlays() {
        if (phase == MatchPhase.PLAYING && !seating && !MatchRoster.any()) {
            enter(MatchPhase.LOBBY, "abandoned: nobody left in play");
            match = null;
            winners.clear();
            endClock = 0;
        }
    }

    private void adopt(MultiplayerMatch m, Origin how) {
        if (!enter(MatchPhase.PLAYING, how + " match seated: " + m.participants))
            return;
        match = m;
        origin = how;
        winners.clear();
        endClock = 0;
        if (how == Origin.LOBBY)
            effects.say("Match started: " + String.join(", ", m.participants));
        // fog of war is on in a new match unless the host turned the reignofnetherFogByDefault gamerule off first
        // (tester, Oct 21: "fog of war on by default"); /rts-fog disable still works during the match
        com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.enableForNewMatch();
        Consumer<MultiplayerMatch> hook = onSeated;
        onSeated = null;
        if (hook != null)
            hook.accept(m);
    }

    /**
     * A side joined play outside any seating (MatchRoster.join): RoN's classic starts, /bot add, Quick Battle's
     * instant start. The first one makes an idle world a CLASSIC match.
     */
    public void sideJoined(String name) {
        if (seating || !phase.idle())
            return;
        origin = Origin.CLASSIC;
        match = null;
        setup = null;
        winners.clear();
        endClock = 0;
        enter(MatchPhase.PLAYING, name + " started a side");
        // Quick Battle / skirmish instant start and /bot add: the border was fitted before this first side (see
        // SkirmishServerboundPacket), so fog can go on now; it did not before because only adopt() asked
        com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.enableForNewMatch();
    }

    /**
     * MatchOutcome found the winners of {@code m} (null: a classic match, everyone on the server). Only this
     * state's own match ends it - several matches run side by side in the game tests.
     */
    public void onDecided(@Nullable MultiplayerMatch m, Set<String> won) {
        if (phase != MatchPhase.PLAYING || m != match || won == null || won.isEmpty())
            return;
        winners.clear();
        winners.addAll(won);
        endClock = clock;
        enter(MatchPhase.ENDED, "won by " + won);
    }

    /** /rts-reset: whatever was being prepared is dropped (nothing seated), and the world has no match any more. */
    public void reset() {
        if (phase.preparing() || startPhase != null)
            effects.cancelPreparation();
        if (phase != MatchPhase.LOBBY)
            enter(MatchPhase.LOBBY, "reset");
        clear();
        showMap(MapPreset.RANDOM, 0, 0, 0);
    }

    private void clear() {
        origin = Origin.CLASSIC;
        setup = null;
        startPhase = null;
        match = null;
        winners.clear();
        clock = 0;
        endClock = 0;
        seating = false;
        savedBuilderStage = -1;
        savedBuilderDetail = null;
        savedStartPhase = null;
        onSeated = null;
        resumeNotice = null;
    }

    /** The match being prepared is dropped: {@code why} for the log, {@code told} for the chat and late joiners. */
    private void cancel(String why, String told) {
        effects.cancelPreparation();
        ReignOfNether.LOGGER.warn("[Match] cancelled the match being prepared: {}", why);
        effects.say(told);
        enter(MatchPhase.LOBBY, "cancelled: " + why);
        clear();
        showMap(MapPreset.RANDOM, 0, 0, 0);
        resumeNotice = told;
    }

    private void cancelResume(String why) {
        cancel(why, "The match that was being set up when this world was saved could not be resumed (" + why
            + "). Start a new one from the lobby.");
    }

    private void showMap(MapPreset preset, int cx, int cz, int radius) {
        mapPreset = preset;
        mapCx = cx;
        mapCz = cz;
        mapRadius = radius;
        effects.showMap(preset, cx, cz, radius);
    }

    /**
     * One tick of match time (MatchServerEvents): it runs while anyone is in play and is 0 otherwise, exactly as
     * RoN's rtsGameTicks did - but saved, so a reloaded match keeps its clock and its graphs' stride.
     */
    public long advanceClock(boolean anyoneInPlay) {
        clock = anyoneInPlay ? clock + 1 : 0;
        return clock;
    }

    // ------------------------------------------------------------------------------------------ save / load

    /** This state as NBT. The live instance asks the effects how far a running preparation got. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putString("phase", phase.name());
        tag.putString("origin", origin.name());
        tag.putLong("clock", clock);
        CompoundTag map = new CompoundTag();
        map.putString("preset", mapPreset.name());
        map.putInt("cx", mapCx);
        map.putInt("cz", mapCz);
        map.putInt("radius", mapRadius);
        tag.put("map", map);
        if (setup != null)
            tag.put("setup", setup.save());
        if (phase == MatchPhase.MAP_RISING) {   // a state loaded and not resumed yet still has the saved stage
            tag.putInt("builderStage", savedBuilderStage >= 0 ? savedBuilderStage : effects.prepStage());
            CompoundTag detail = savedBuilderStage >= 0 ? savedBuilderDetail : effects.prepDetail();
            if (detail != null)
                tag.put("builderDetail", detail.copy());
        }
        if (startPhase != null)
            tag.put("startPhase", startPhase.save());
        else if (savedStartPhase != null)
            tag.put("startPhase", savedStartPhase.copy());
        if (match != null) {
            CompoundTag m = new CompoundTag();
            m.put("participants", names(match.participants));
            m.put("humans", names(match.humans));
            tag.put("match", m);
        }
        if (!winners.isEmpty())
            tag.put("winners", names(winners));
        if (phase == MatchPhase.ENDED)
            tag.putLong("endClock", endClock);
        return tag;
    }

    /**
     * A saved state, not resumed yet ({@link #resume} restarts what it describes). Tolerant: an unknown phase or a
     * missing part reads as LOBBY or as nothing, and {@link #resumePlan} cancels a setup that doesn't fit.
     */
    public static MatchState load(CompoundTag tag, MatchEffects effects) {
        MatchState s = new MatchState(effects);
        if (tag == null)
            return s;
        if (tag.getInt("version") > VERSION)
            ReignOfNether.LOGGER.warn("[Match] the saved match state is newer ({}) than this build ({}); reading what it can",
                tag.getInt("version"), VERSION);
        s.phase = MatchPhase.byName(tag.getString("phase"));
        s.origin = Origin.byName(tag.getString("origin"));
        s.clock = Math.max(0, tag.getLong("clock"));
        CompoundTag map = tag.getCompound("map");
        MapPreset preset = MapPreset.RANDOM;
        for (MapPreset p : MapPreset.values())
            if (p.name().equals(map.getString("preset")))
                preset = p;
        s.mapPreset = preset;
        s.mapCx = map.getInt("cx");
        s.mapCz = map.getInt("cz");
        s.mapRadius = map.getInt("radius");
        if (tag.contains("setup", Tag.TAG_COMPOUND))
            s.setup = MatchSetup.load(tag.getCompound("setup"));
        s.savedBuilderStage = tag.contains("builderStage", Tag.TAG_INT) ? tag.getInt("builderStage") : -1;
        if (tag.contains("builderDetail", Tag.TAG_COMPOUND))
            s.savedBuilderDetail = tag.getCompound("builderDetail").copy();
        if (tag.contains("startPhase", Tag.TAG_COMPOUND))
            s.savedStartPhase = tag.getCompound("startPhase").copy();
        if (tag.contains("match", Tag.TAG_COMPOUND)) {
            CompoundTag m = tag.getCompound("match");
            s.match = new MultiplayerMatch(readNames(m.getList("participants", Tag.TAG_STRING)),
                readNames(m.getList("humans", Tag.TAG_STRING)));
            if (s.phase != MatchPhase.PLAYING)
                s.match.end();   // a decided match is over: it scopes nothing any more
        }
        s.winners.addAll(readNames(tag.getList("winners", Tag.TAG_STRING)));
        s.endClock = Math.max(0, tag.getLong("endClock"));
        return s;
    }

    /** What {@link #resume} will do. Pure: the game tests check it for every phase. */
    public ResumePlan resumePlan() {
        return switch (phase) {
            case LOBBY, ENDED -> ResumePlan.NOTHING;
            case MAP_RISING -> setup == null || !setup.prepares() ? ResumePlan.CANCEL
                : savedBuilderStage >= MatchPrep.SAVED_READY ? ResumePlan.AFTER_MAP : ResumePlan.REBUILD_MAP;
            case START_POSITIONS -> setup == null || savedStartPhase == null ? ResumePlan.CANCEL
                : ResumePlan.CONTINUE_START_PHASE;
            case PLAYING -> ResumePlan.REJOIN_MATCH;
        };
    }

    /** Restarts what a loaded state describes (server start, after the sides and the world are back). */
    public void resume() {
        ResumePlan plan = resumePlan();
        ReignOfNether.LOGGER.info("[Match] resuming a saved {} match: {}", phase, plan);
        switch (plan) {
            case NOTHING -> effects.showMap(mapPreset, mapCx, mapCz, mapRadius);
            case CANCEL -> cancelResume(setup == null ? "its setup was unreadable" : "its start positions were not saved");
            case REBUILD_MAP -> {
                effects.showMap(mapPreset, mapCx, mapCz, mapRadius);
                effects.prepare(this, savedBuilderStage, savedBuilderDetail);
                resumeNotice = "This world was saved while " + (mapPreset.premade() ? mapPreset.displayName
                    : "the battlefield") + " was being prepared: it is being finished, then the match goes on.";
            }
            case AFTER_MAP -> {
                effects.showMap(mapPreset, mapCx, mapCz, mapRadius);
                afterMap();
            }
            case CONTINUE_START_PHASE -> {
                StartPhase ph = effects.newStartPhase(this);
                if (!ph.load(savedStartPhase)) {
                    cancelResume("its saved start positions don't fit its lineup");
                    return;
                }
                effects.showMap(mapPreset, mapCx, mapCz, mapRadius);
                startPhase = ph;
                effects.beginStartPhase(this, ph, true);
                resumeNotice = "This world was saved during start position mode: every start placed so far is kept."
                    + " The clock waits for the players to come back, then runs at least "
                    + StartPhase.RESUME_FLOOR_SECONDS + " s.";
            }
            case REJOIN_MATCH -> {
                effects.showMap(mapPreset, mapCx, mapCz, mapRadius);
                if (match != null)
                    match.register();
            }
        }
        savedBuilderStage = -1;
        savedBuilderDetail = null;
        savedStartPhase = null;
    }

    /**
     * A world from before the lifecycle was saved has sides in play but no saved phase: it is a classic match
     * (MatchLifecycleSaveData calls this when the roster came back non-empty and no state did).
     */
    void adoptRunningSides() {
        if (phase.idle() && MatchRoster.any()) {
            origin = Origin.CLASSIC;
            enter(MatchPhase.PLAYING, "sides in play with no saved phase");
        }
    }

    private static ListTag names(Set<String> set) {
        ListTag list = new ListTag();
        for (String n : set)
            list.add(StringTag.valueOf(n));
        return list;
    }

    private static Set<String> readNames(ListTag list) {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++)
            out.add(list.getString(i));
        return out;
    }
}
