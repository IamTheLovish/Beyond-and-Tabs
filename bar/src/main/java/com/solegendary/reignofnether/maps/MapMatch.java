package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.match.MatchEffects;
import com.solegendary.reignofnether.match.MatchPrep;
import com.solegendary.reignofnether.match.MatchSetup;
import com.solegendary.reignofnether.match.MatchState;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;
import com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.ArenaPregen;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;
import com.solegendary.reignofnether.startpos.BattlefieldWall;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The road from Start to a seated match when the lobby picked a pre-made map and/or the start-position phase
 * (design/maps.md): prepare the battlefield (match.MatchPrep: the map, the start clearing, the wall, the arena's
 * chunks), let everyone place their start (StartPhase), then seat the lineup exactly as the lobby always has
 * (MultiplayerLobby.seatAll) with the battlefield's economy forming on it. Used by the multiplayer lobby and by the
 * singleplayer skirmish screen (SkirmishServerboundPacket builds a lineup of seats).
 * <p>
 * Rewrite 5 (design/ron_rewrite_plan.md): which step comes next is now decided by match.MatchState, which also saves
 * the setup with the world (code_review CR-13); this class is the steps themselves ({@link #EFFECTS}). Before, the
 * lineup lived only in the closures handed from step to step, so a world saved mid-way reloaded with nothing to finish.
 * While a match is being prepared, {@link #isPreparing} keeps anyone from starting a base by hand
 * (MultiplayerLobby.refusesLateStart) and the lobby from starting a second match.
 */
public final class MapMatch {
    private MapMatch() { }

    /**
     * The map in play, for clients (MapStateClientboundPacket: fog tint, map name, box centre); RANDOM when none.
     * A mirror of the live MatchState's map, which is the saved one.
     */
    static MapPreset activePreset = MapPreset.RANDOM;
    static int activeCx, activeCz, activeRadius;

    public static boolean isPreparing() {
        return MatchState.live().isPreparing();
    }

    public static MapPreset activePreset() {
        return activePreset;
    }

    /** Team box colours, BAR-like: green, red, blue, yellow. */
    public static int teamColour(int team) {
        return switch (team) {
            case 0 -> 0x3CDC3C;
            case 1 -> 0xE63C3C;
            case 2 -> 0x3C78FF;
            default -> 0xF0D23C;
        };
    }

    /**
     * Prepares and then seats {@code lineup} as a singleplayer skirmish. {@code onStarted} gets the seated match (on
     * the server thread, later); it is not saved, so a match resumed after a reload seats without it.
     * {@code choose}: run the start-position phase (skipped anyway when no human plays).
     */
    public static void begin(ServerLevel level, List<MultiplayerLobby.Seat> lineup, MapPreset preset, boolean choose,
                             int arena, int metal, int spawnDistance, BlockPos centre, Random rng,
                             Consumer<MultiplayerMatch> onStarted) {
        begin(level, lineup, preset, choose, arena, metal, spawnDistance, centre, rng, MatchState.Origin.SKIRMISH, onStarted);
    }

    /** As above for a given origin (the multiplayer lobby or the skirmish screen). False when another match runs. */
    public static boolean begin(ServerLevel level, List<MultiplayerLobby.Seat> lineup, MapPreset preset, boolean choose,
                                int arena, int metal, int spawnDistance, BlockPos centre, Random rng,
                                MatchState.Origin origin, @Nullable Consumer<MultiplayerMatch> onStarted) {
        return begin(level, lineup, preset, choose, arena, metal, spawnDistance, centre, rng, origin, 0L,
            com.solegendary.reignofnether.match.TimeOfDay.MORNING, onStarted);
    }

    /**
     * As above with the lobby's map seed (0 = the unseeded map) and time of day - the skirmish screen's road; the
     * multiplayer lobby still plays the unseeded map at the default light (its preview has no seed yet).
     */
    public static boolean begin(ServerLevel level, List<MultiplayerLobby.Seat> lineup, MapPreset preset, boolean choose,
                                int arena, int metal, int spawnDistance, BlockPos centre, Random rng,
                                MatchState.Origin origin, long mapSeed,
                                com.solegendary.reignofnether.match.TimeOfDay timeOfDay,
                                @Nullable Consumer<MultiplayerMatch> onStarted) {
        MatchSetup setup = MatchSetup.create(level, lineup, preset, choose, arena, metal, spawnDistance, centre, rng,
            origin, mapSeed, timeOfDay);
        return MatchState.live().prepare(setup, onStarted);
    }

    /** The picks of a start phase for {@code setup}, every side at its slot, ending into {@code onDone}. */
    public static StartPhase newStartPhase(ServerLevel level, MatchSetup setup, Consumer<StartPhase> onDone) {
        List<MultiplayerLobby.Seat> playing = setup.playing();
        List<StartPhase.Pick> picks = new ArrayList<>();
        int botNo = 1;
        for (int i = 0; i < playing.size(); i++) {
            MultiplayerLobby.Seat s = playing.get(i);
            PlayerPalette.Entry e = PlayerPalette.byMapColorId(s.colorMapId);
            String name = s.bot ? "AI " + botNo++ : s.name;
            picks.add(new StartPhase.Pick(name, s.bot, s.team, i, e == null ? 0xFFFFFF : e.hex(), s));
        }
        return new StartPhase(level, setup.cx, setup.cz, setup.layout(), picks, StartPhase.SECONDS, onDone);
    }

    /** Seats every side where {@code ph} put it, on this map's battlefield. Null (logged and told) when it failed. */
    @Nullable
    static MultiplayerMatch seat(ServerLevel level, MatchSetup setup, StartPhase ph) {
        List<MultiplayerLobby.Seat> playing = new ArrayList<>();
        List<Vec3> starts = new ArrayList<>();
        for (StartPhase.Pick p : ph.picks) {
            playing.add((MultiplayerLobby.Seat) p.seat);
            // the start is the GROUND block (as the lobby's ring and the skirmish start use it)
            starts.add(new Vec3(p.x + 0.5, MetalPatches.solidTop(level, p.x, p.z), p.z + 0.5));
        }
        MapLayout layout = setup.layout();
        BattlefieldSetup.expectFormation = true;
        // a prepared setup (MatchPrep) already cleared the slots, raised the wall and scouted the arena: the
        // battlefield that forms after seating only clears the picks' own ground (cheap, mostly clear already) and,
        // on random terrain, stamps the economy where the humans actually placed - never a second wall
        BattlefieldSetup.wallStands = setup.prepares();
        if (layout.preset.premade()) {
            BattlefieldSetup.premadeLayout = layout;
            BattlefieldSetup.premadeCx = setup.cx;
            BattlefieldSetup.premadeCz = setup.cz;
        } else {
            BattlefieldSetup.arenaRadiusOverride = setup.prepares() ? layout.radius
                : SkirmishServerboundPacket.arenaRadius(setup.arena);
            BattlefieldSetup.richnessOverride = SkirmishServerboundPacket.metalRichness(setup.metal);
        }
        try {
            return MultiplayerLobby.seatAll(level, playing, setup.heroes(), setup.colours(), starts, new Random(setup.seed));
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[MapMatch] seating the match failed", e);
            say(level.getServer(), "The match failed to start: " + e);
            return null;
        } finally {
            MapStateClientboundPacket.broadcast();
        }
    }

    static void say(@Nullable MinecraftServer server, String msg) {
        if (server == null)
            return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers())
            sp.sendSystemMessage(Component.literal(msg));
    }

    /** The overworld the whole match stack plays in (design/maps.md: every map is stamped there). */
    static ServerLevel overworld() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.overworld();
    }

    /** What MatchState's transitions do to the live world. */
    public static final MatchEffects EFFECTS = new MatchEffects() {
        @Override
        public void prepare(MatchState state, int fromStage, @Nullable CompoundTag fromDetail) {
            ServerLevel level = overworld();
            MatchSetup setup = state.setup();
            if (level == null || setup == null)
                return;
            boolean resumed = fromStage >= 0;
            // qualified: inside this class an unqualified say(...) only sees MatchEffects.say(String)
            if (setup.raisesMap())
                MapMatch.say(level.getServer(), (resumed ? "Resuming: forging " : "Forging ") + setup.preset.displayName
                    + " for " + setup.playing().size() + " players: " + setup.preset.blurb + ".");
            else
                MapMatch.say(level.getServer(), (resumed ? "Resuming: preparing" : "Preparing")
                    + " the battlefield for " + setup.playing().size() + " players.");
            MatchPrep.begin(level, state, fromStage, fromDetail);
        }

        @Override
        public int prepStage() {
            return MatchPrep.liveStage();
        }

        @Nullable
        @Override
        public CompoundTag prepDetail() {
            return MatchPrep.liveDetail();
        }

        @Override
        public StartPhase newStartPhase(MatchState state) {
            return MapMatch.newStartPhase(overworld(), state.setup(), state::startsPlaced);
        }

        @Override
        public void beginStartPhase(MatchState state, StartPhase phase, boolean resumed) {
            if (resumed) {
                StartPhase.resume(phase);
                return;
            }
            StartPhase.begin(phase);
            MapMatch.say(phase.level.getServer(), "Start position mode: click inside your team's box to place your commander ("
                + StartPhase.SECONDS + " s).");
        }

        @Nullable
        @Override
        public MultiplayerMatch seat(MatchState state, StartPhase finished) {
            ServerLevel level = overworld();
            if (level == null || state.setup() == null)
                return null;
            return MapMatch.seat(level, state.setup(), finished);
        }

        @Override
        public void cancelPreparation() {
            MatchPrep.cancelLive();
            MapBuilder.cancel();
            BattlefieldWall.cancel();
            ArenaPregen.stop();
            StartPhase.cancel();
        }

        @Override
        public void showMap(MapPreset preset, int cx, int cz, int radius) {
            activePreset = preset;
            activeCx = cx;
            activeCz = cz;
            activeRadius = radius;
            MapStateClientboundPacket.broadcast();
        }

        @Override
        public void say(String message) {
            MapMatch.say(ServerLifecycleHooks.getCurrentServer(), message);
        }
    };

    /** /rts-reset (MultiplayerLobby.onReset): no match, nothing being prepared, no map in play. */
    public static void reset() {
        MatchState.live().reset();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer sp)
            MapStateClientboundPacket.sendTo(sp);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        // only the mirror: the match itself is saved and replaced by MatchServerEvents around this
        activePreset = MapPreset.RANDOM;
        activeCx = activeCz = activeRadius = 0;
    }
}
