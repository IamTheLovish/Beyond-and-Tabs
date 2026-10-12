package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;
import com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The road from Start to a seated match when the lobby picked a pre-made map and/or the start-position phase
 * (design/maps.md): raise the map (MapBuilder), let everyone place their start (StartPhase), then seat the lineup
 * exactly as the lobby always has (MultiplayerLobby.seatAll) with the battlefield set to form on this map. Used by the
 * multiplayer lobby and by the singleplayer skirmish screen (SkirmishServerboundPacket builds a lineup of seats).
 *
 * While it runs, {@link #isPreparing} keeps anyone from starting a base by hand (MultiplayerLobby.refusesLateStart)
 * and the lobby from starting a second match.
 */
public final class MapMatch {
    private MapMatch() { }

    private static boolean preparing = false;
    /** The map in play (for clients joining later: fog tint, map name); RANDOM when none. */
    static MapPreset activePreset = MapPreset.RANDOM;
    static int activeCx, activeCz, activeRadius;

    public static boolean isPreparing() {
        return preparing;
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
     * Prepares and then seats {@code lineup}. {@code onStarted} gets the seated match (on the server thread, later).
     * {@code choose}: run the start-position phase (skipped anyway when no human plays).
     */
    public static void begin(ServerLevel level, List<MultiplayerLobby.Seat> lineup, MapPreset preset, boolean choose,
                             int arena, int metal, int spawnDistance, BlockPos centre, Random rng,
                             Consumer<MultiplayerMatch> onStarted) {
        List<MultiplayerLobby.Seat> playing = MultiplayerLobby.playingSorted(lineup);
        Map<MultiplayerLobby.Seat, Integer> colours = MultiplayerLobby.assignColours(playing);
        int[] teams = new int[playing.size()];
        for (int i = 0; i < teams.length; i++)
            teams[i] = playing.get(i).team;
        int cx = centre.getX(), cz = centre.getZ();
        boolean anyHuman = false;
        for (MultiplayerLobby.Seat s : playing)
            if (!s.bot)
                anyHuman = true;
        final boolean phase = choose && anyHuman && playing.size() >= 2;
        preparing = true;

        if (preset.premade() && playing.size() >= 2) {
            MapLayout layout = MapLayout.build(preset, teams);
            // the map's base height: the centre's ground, kept in a band where water and cliffs look right
            int baseY = Math.max(62, Math.min(90, MetalPatches.solidTop(level, cx, cz)));
            activePreset = preset;
            activeCx = cx;
            activeCz = cz;
            activeRadius = layout.radius;
            MapStateClientboundPacket.broadcast();
            say(level, "Forging " + preset.displayName + " for " + playing.size() + " players: " + preset.blurb + ".");
            MapBuilder.start(level, cx, cz, baseY, layout, () ->
                afterBuild(level, layout, playing, colours, phase, cx, cz, rng, arena, metal, onStarted));
        } else {
            activePreset = MapPreset.RANDOM;
            // random terrain: the lobby's fair ring (rotated so slot 0 sits west, like the layouts' boxes)
            int ringR = MultiplayerLobby.ringRadius(playing.size(), spawnDistance);
            List<int[]> targets = MultiplayerLobby.ringTargets(centre, playing.size(), ringR, Math.PI);
            List<int[]> offsets = new ArrayList<>();
            for (int[] t : targets) {
                Vec3 g = MultiplayerLobby.groundSite(level, t[0], t[1]);
                offsets.add(new int[] { (int) Math.floor(g.x) - cx, (int) Math.floor(g.z) - cz });
            }
            int arenaR = SkirmishServerboundPacket.arenaRadius(arena);
            MapLayout layout = MapLayout.ring(teams, offsets, ringR, arenaR > 0 ? arenaR : ringR + 130);
            afterBuild(level, layout, playing, colours, phase, cx, cz, rng, arena, metal, onStarted);
        }
    }

    static void afterBuild(ServerLevel level, MapLayout layout, List<MultiplayerLobby.Seat> playing,
                           Map<MultiplayerLobby.Seat, Integer> colours, boolean phase, int cx, int cz, Random rng,
                           int arena, int metal, Consumer<MultiplayerMatch> onStarted) {
        List<StartPhase.Pick> picks = new ArrayList<>();
        int botNo = 1;
        for (int i = 0; i < playing.size(); i++) {
            MultiplayerLobby.Seat s = playing.get(i);
            PlayerPalette.Entry e = PlayerPalette.byMapColorId(colours.getOrDefault(s, 0));
            String name = s.bot ? "AI " + botNo++ : s.name;
            picks.add(new StartPhase.Pick(name, s.bot, s.team, i, e == null ? 0xFFFFFF : e.hex(), s));
        }
        Consumer<StartPhase> seat = ph -> seat(level, layout, ph, colours, cx, cz, rng, arena, metal, onStarted);
        StartPhase ph = new StartPhase(level, cx, cz, layout, picks, StartPhase.SECONDS, seat);
        if (phase) {
            StartPhase.begin(ph);
            say(level, "Start position mode: click inside your team's box to place your commander (" + StartPhase.SECONDS + " s).");
        } else {
            ph.finish();   // everyone at their slot
        }
    }

    static void seat(ServerLevel level, MapLayout layout, StartPhase ph, Map<MultiplayerLobby.Seat, Integer> colours,
                     int cx, int cz, Random rng, int arena, int metal, Consumer<MultiplayerMatch> onStarted) {
        preparing = false;
        List<MultiplayerLobby.Seat> playing = new ArrayList<>();
        List<Vec3> starts = new ArrayList<>();
        for (StartPhase.Pick p : ph.picks) {
            playing.add((MultiplayerLobby.Seat) p.seat);
            // the start is the GROUND block (as the lobby's ring and the skirmish start use it)
            starts.add(new Vec3(p.x + 0.5, MetalPatches.solidTop(level, p.x, p.z), p.z + 0.5));
        }
        BattlefieldSetup.expectFormation = true;
        if (layout.preset.premade()) {
            BattlefieldSetup.premadeLayout = layout;
            BattlefieldSetup.premadeCx = cx;
            BattlefieldSetup.premadeCz = cz;
        } else {
            BattlefieldSetup.arenaRadiusOverride = SkirmishServerboundPacket.arenaRadius(arena);
            BattlefieldSetup.richnessOverride = SkirmishServerboundPacket.metalRichness(metal);
        }
        try {
            MultiplayerMatch m = MultiplayerLobby.seatAll(level, playing, colours, starts, rng);
            if (onStarted != null)
                onStarted.accept(m);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[MapMatch] seating the match failed", e);
            say(level, "The match failed to start: " + e);
        }
        MapStateClientboundPacket.broadcast();
    }

    static void say(ServerLevel level, String msg) {
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers())
            sp.sendSystemMessage(Component.literal(msg));
    }

    /** /rts-reset or a new world: no map in play any more. */
    public static void reset() {
        preparing = false;
        activePreset = MapPreset.RANDOM;
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer sp)
            MapStateClientboundPacket.sendTo(sp);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        reset();
    }
}
