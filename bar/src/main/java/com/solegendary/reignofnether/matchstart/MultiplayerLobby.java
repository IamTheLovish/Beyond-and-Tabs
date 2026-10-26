package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.match.MatchState;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;
import com.solegendary.reignofnether.unit.UnitChunkLoader;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The multiplayer skirmish lobby (design/multiplayer.md): several humans on one server - an integrated server opened
 * to LAN (with the e4mc tunnel for internet play) or a dedicated one - plus optional bots, in one match.
 *
 * Server-authoritative. The host (PacketAuth.isHost: the singleplayer owner, or an op on a dedicated server) opens
 * it; every connected player gets a seat; the host adds bots to the free seats and sets the map; each human changes
 * only their own faction, colour, team and Ready; the host presses Start once every other human is ready. Every
 * change is validated here and the whole lobby is re-sent to everyone (LobbyClientboundPacket, a few hundred bytes,
 * only when something changed). Start tells every client to enter the RTS view, waits two seconds, then seats
 * everyone on one fair ring ({@link #startMatch}): capitol, commander, resources, fog, team alliances. Anyone who
 * joins after that is a spectator.
 */
public class MultiplayerLobby {

    public static final int MAX_HUMANS = 8;
    public static final int MAX_SEATS = 16;
    public static final int MAX_PER_TEAM = 8;
    public static final int TEAMS = 4;
    /** A seat's team when that human watches instead of playing. */
    public static final int SPECTATE = -1;
    /** Ticks between Start and the bases appearing: every client turns on the RTS view in between. */
    static final int START_DELAY_TICKS = 40;

    /** One row of the lobby. Shared by server and client (the snapshot carries these). */
    public static class Seat {
        public final int id;
        public final boolean bot;
        public String name = "";
        public UUID uuid = null;   // humans only (never sent to clients)
        public int faction = SkirmishServerboundPacket.RANDOM_CODE;
        public int colorMapId = 0; // PlayerPalette map colour id, 0 = any free colour
        public int team = 0;
        public boolean ready = false;
        public int difficulty = 1; // bots: 0 easy, 1 medium, 2 hard
        // hero mode (design/hero_mode.md): a human on a team with no base of their own, who plays the team's hero
        // (possession) instead of commanding - HeroRoles attaches them to their team at the start
        public boolean heroRole = false;

        public Seat(int id, boolean bot) {
            this.id = id;
            this.bot = bot;
        }

        public boolean spectating() {
            return team < 0;
        }
    }

    // ---- lobby state (server thread only) ----
    static boolean open = false;
    static final List<Seat> seats = new ArrayList<>();
    static int nextSeatId = 1;
    static int arena = 4, metal = 3, spawnDistance = 1;
    static int pace = 1;   // economy pace code (EconomyPace): 0 relaxed, 1 standard, 2 fast
    static int map = 0;    // MapPreset id: 0 random terrain, else a pre-made map (design/maps.md)
    static boolean choosePositions = true;   // BAR's start-position phase before the match
    static int timeOfDay = 0;                // match.TimeOfDay id: the light at Start (morning)
    static int dayCycle = com.solegendary.reignofnether.match.DayCycle.DEFAULT.ordinal();   // match.DayCycle id
    static UUID hostUuid = null;
    static String hostName = "";
    static boolean dirty = false;
    static int startCountdown = -1;
    // Back to lobby (the tester's Oct 26 ask: rematches on one server): the lineup of the last lobby start, reseated
    // when the host goes back, and the ring centre it used, so the rematch is fought on the restored ground
    static final List<Seat> lastLineup = new ArrayList<>();
    static BlockPos lastCentre = null;
    static BlockPos rematchCentre = null;
    // the lobby's match being fought is match.MatchState's (rewrite 5): saved with the world, so a reload keeps who
    // may rejoin and who watches (it was a static here that a reload forgot)

    public static boolean isOpen() {
        return open;
    }

    /** A lobby match is being played (started from this lobby and not decided yet). */
    public static boolean isMatchLive() {
        return MatchState.live().lobbyMatch() != null;
    }

    /**
     * True for a player who may not start a base of their own: anyone while a match is being prepared (the setup
     * seats everyone), and anyone not seated in the live lobby match.
     */
    public static boolean refusesLateStart(String name) {
        return MatchState.live().refusesLateStart(name);
    }

    /**
     * A match was decided. MatchOutcome tells MatchState itself now; kept for older callers: the sides of a decided
     * match still in play are its winners.
     */
    @Deprecated
    public static void onMatchDecided(MultiplayerMatch m) {
        if (m != null)
            MatchState.live().onDecided(m, m.remaining());
    }

    /** /rts-reset: every match is gone. */
    public static void onReset() {
        startCountdown = -1;
        MultiplayerMatch.clearAll();
        MatchState.live().reset();
        com.solegendary.reignofnether.hero.PossessionServer.clearAll();
        com.solegendary.reignofnether.hero.HeroRoles.clearAll();
    }

    // ---- actions (LobbyServerboundPacket) ----

    public enum Action {
        OPEN, CLOSE, REQUEST, SET_FACTION, SET_COLOUR, SET_TEAM, SET_READY, SET_DIFFICULTY, ADD_BOT, REMOVE_BOT,
        SET_ARENA, SET_METAL, SET_SPAWNS, START,
        SET_PACE,  // appended: actions travel by ordinal
        SET_MAP, SET_STARTPOS,
        SET_ROLE,   // hero mode: value 1 = Hero role (no base), 0 = Commander
        SET_TIME, SET_CYCLE,  // the match sun: match.TimeOfDay id, match.DayCycle id
        BACK_TO_LOBBY         // host, match decided: reset the battlefield and reopen the lobby with the last lineup
    }

    static Seat seat(int id) {
        for (Seat s : seats)
            if (s.id == id)
                return s;
        return null;
    }

    static Seat seatOf(ServerPlayer sp) {
        for (Seat s : seats)
            if (!s.bot && sp.getUUID().equals(s.uuid))
                return s;
        return null;
    }

    static boolean isLobbyHost(ServerPlayer sp) {
        return sp != null && (sp.getUUID().equals(hostUuid) || PacketAuth.isHost(sp));
    }

    /** The seat {@code sp} may edit: their own, or (host) a bot's. Null when they may not. */
    static Seat editable(ServerPlayer sp, int seatId) {
        Seat s = seat(seatId);
        if (s == null)
            return null;
        return mayEdit(s, sp.getUUID(), isLobbyHost(sp)) ? s : null;
    }

    /** Whether {@code editor} may edit seat {@code s}: their own seat, or a bot's when they host. Pure. */
    public static boolean mayEdit(Seat s, UUID editor, boolean editorIsHost) {
        if (s == null || editor == null)
            return false;
        return s.bot ? editorIsHost : editor.equals(s.uuid);
    }

    /**
     * Whether colour {@code mapColorId} is held by a playing seat of {@code lineup} other than {@code except}
     * (spectators and Hero-role seats take no base, so they hold no colour; 0 = automatic is never held). The server
     * refuses a taken colour with it and the lobby screen crosses taken colours out with it. Pure.
     */
    public static boolean colourTakenIn(List<Seat> lineup, int mapColorId, Seat except) {
        if (mapColorId == 0)
            return false;
        for (Seat s : lineup)
            if (s != except && !s.spectating() && !s.heroRole && s.colorMapId == mapColorId)
                return true;
        return false;
    }

    /**
     * The lobby's colour rule (BAR's colour box, tester ask Oct 26): {@code editor} may set seat {@code s}'s colour
     * to {@code value} when the seat is theirs (or a bot's and they host) and {@code value} is 0 (automatic) or a
     * palette colour no other playing seat holds - so two sides never share a colour. Pure: the lobby_colors tests.
     */
    public static boolean colourChangeAllowed(List<Seat> lineup, Seat s, UUID editor, boolean editorIsHost, int value) {
        if (!mayEdit(s, editor, editorIsHost))
            return false;
        if (value == 0)
            return true;
        return PlayerPalette.isPaletteColor(value) && !colourTakenIn(lineup, value, s);
    }

    static int teamSize(int team, Seat except) {
        int n = 0;
        for (Seat s : seats)
            if (s != except && s.team == team)
                n++;
        return n;
    }

    static boolean colourTaken(int mapColorId, Seat except) {
        return colourTakenIn(seats, mapColorId, except);
    }

    /** Applies one lobby action from {@code sp}. Anything not allowed is ignored (the next snapshot corrects them). */
    public static void handle(ServerPlayer sp, Action action, int seatId, int value) {
        if (sp == null || action == null)
            return;
        switch (action) {
            case OPEN -> open(sp);
            case REQUEST -> LobbyClientboundPacket.sendSnapshot(sp);
            case BACK_TO_LOBBY -> {
                String why = backToLobby(sp);
                if (why != null)
                    sp.sendSystemMessage(Component.literal(why));
            }
            case CLOSE -> {
                if (isLobbyHost(sp))
                    close("The host closed the lobby.");
            }
            default -> {
                // nothing changes once Start was pressed or when there is no lobby
                if (!open || startCountdown >= 0)
                    break;
                if (edit(sp, action, seatId, value))
                    dirty = true;
                else
                    LobbyClientboundPacket.sendSnapshot(sp);   // refused: put their screen back as it was
            }
        }
    }

    static boolean edit(ServerPlayer sp, Action action, int seatId, int value) {
        boolean host = isLobbyHost(sp);
        switch (action) {
            case SET_FACTION -> {
                Seat s = editable(sp, seatId);
                if (s == null || value < 0 || value >= SkirmishServerboundPacket.codeCount())
                    return false;
                s.faction = value;
            }
            case SET_COLOUR -> {
                Seat s = seat(seatId);
                if (s == null || !colourChangeAllowed(seats, s, sp.getUUID(), isLobbyHost(sp), value))
                    return false;
                s.colorMapId = value;
            }
            case SET_TEAM -> {
                Seat s = editable(sp, seatId);
                if (s == null || value < SPECTATE || value >= TEAMS || (s.bot && value == SPECTATE))
                    return false;
                if (value != SPECTATE && teamSize(value, s) >= MAX_PER_TEAM)
                    return false;
                // back from spectating with a colour someone took meanwhile: any free colour instead
                if (s.spectating() && value != SPECTATE && colourTaken(s.colorMapId, s))
                    s.colorMapId = 0;
                s.team = value;
                if (value == SPECTATE) {
                    s.ready = true;   // a spectator never holds the start up
                    s.heroRole = false;
                }
            }
            case SET_READY -> {
                Seat s = seat(seatId);
                if (s == null || s.bot || !sp.getUUID().equals(s.uuid))
                    return false;
                s.ready = value != 0;
            }
            case SET_DIFFICULTY -> {
                Seat s = seat(seatId);
                if (!host || s == null || !s.bot || value < 0 || value > 2)
                    return false;
                s.difficulty = value;
            }
            case ADD_BOT -> {
                if (!host || seats.size() >= MAX_SEATS || value < 0 || value >= TEAMS || teamSize(value, null) >= MAX_PER_TEAM)
                    return false;
                Seat b = new Seat(nextSeatId++, true);
                b.team = value;
                b.ready = true;
                b.name = "AI";
                seats.add(b);
            }
            case REMOVE_BOT -> {
                Seat s = seat(seatId);
                if (!host || s == null || !s.bot)
                    return false;
                seats.remove(s);
            }
            case SET_ARENA -> {
                if (!host || value < 0 || value > 4)
                    return false;
                arena = value;
            }
            case SET_METAL -> {
                if (!host || value < 0 || value > 3)
                    return false;
                metal = value;
            }
            case SET_SPAWNS -> {
                if (!host || value < 0 || value > 2)
                    return false;
                spawnDistance = value;
            }
            case SET_PACE -> {
                if (!host || value < 0 || value > 2)
                    return false;
                pace = value;
            }
            case SET_MAP -> {
                if (!host || value < 0 || value >= com.solegendary.reignofnether.maps.MapPreset.count())
                    return false;
                map = value;
            }
            case SET_STARTPOS -> {
                if (!host)
                    return false;
                choosePositions = value != 0;
            }
            case SET_TIME -> {
                if (!host || value < 0 || value >= com.solegendary.reignofnether.match.TimeOfDay.count())
                    return false;
                timeOfDay = value;
            }
            case SET_CYCLE -> {
                if (!host || value < 0 || value >= com.solegendary.reignofnether.match.DayCycle.count())
                    return false;
                dayCycle = value;
            }
            case SET_ROLE -> {
                // only a human may play the hero, and only from a team seat (a spectator has no team to serve)
                Seat s = editable(sp, seatId);
                if (s == null || s.bot || s.spectating() || value < 0 || value > 1)
                    return false;
                // back from the Hero role with a colour someone took meanwhile: any free colour instead
                if (s.heroRole && value == 0 && colourTakenIn(seats, s.colorMapId, s))
                    s.colorMapId = 0;
                s.heroRole = value == 1;
            }
            case START -> {
                if (!host)
                    return false;
                String why = whyNotStartable(sp);
                if (why != null) {
                    sp.sendSystemMessage(Component.literal(why));
                    return false;
                }
                startCountdown = START_DELAY_TICKS;
                LobbyClientboundPacket.sendStart(seats);
                say("The battle begins in a moment...");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Null when the host may press Start, else what is missing (shown to the host). */
    static String whyNotStartable(ServerPlayer host) {
        if (PlayerServerEvents.isGameActive())
            return "A match is already running in this world. Use /rts-reset, or host from the title screen (Skirmish -> Host Online) for a fresh battlefield.";
        if (com.solegendary.reignofnether.maps.MapMatch.isPreparing())
            return "A battlefield is being prepared - wait for it to start.";
        Set<Integer> teams = new HashSet<>();
        int playing = 0;
        for (Seat s : seats) {
            if (s.spectating())
                continue;
            if (!s.bot && !s.ready && !host.getUUID().equals(s.uuid))
                return s.name + " is not ready yet.";
            if (s.heroRole)
                continue;   // a hero is not a side: no base, no place on the ring
            playing++;
            teams.add(s.team);
        }
        for (Seat s : seats)
            if (s.heroRole && !s.spectating() && !teams.contains(s.team))
                return s.name + " is a Hero on a team with no commander: someone on that team must command.";
        if (playing < 2)
            return "A match needs at least two sides: add an AI or wait for a friend.";
        if (teams.size() < 2)
            return "Everyone is on one team: put someone on another team first.";
        return null;
    }

    static void open(ServerPlayer sp) {
        if (!isLobbyHost(sp)) {
            // a joined player asking to see it: show it if there is one
            if (open)
                LobbyClientboundPacket.sendSnapshot(sp);
            else
                sp.sendSystemMessage(Component.literal("There is no lobby yet - the host opens it (Esc -> Multiplayer Lobby, or /rts-lobby)."));
            return;
        }
        if (isMatchLive()) {
            sp.sendSystemMessage(Component.literal("The lobby match is still being played."));
            return;
        }
        if (!open) {
            open = true;
            seats.clear();
            startCountdown = -1;
            hostUuid = sp.getUUID();
            hostName = sp.getName().getString();
            MinecraftServer server = sp.getServer();
            // the host first, then everyone else online, alternating teams so a 1v1 needs no clicks
            addHuman(sp);
            if (server != null)
                for (ServerPlayer other : server.getPlayerList().getPlayers())
                    if (other != sp)
                        addHuman(other);
            ReignOfNether.LOGGER.info("[Lobby] {} opened the multiplayer lobby ({} seat(s))", hostName, seats.size());
        }
        dirty = true;
    }

    static void addHuman(ServerPlayer sp) {
        if (seatOf(sp) != null)
            return;
        Seat s = new Seat(nextSeatId++, false);
        s.name = sp.getName().getString();
        s.uuid = sp.getUUID();
        long humans = seats.stream().filter(x -> !x.bot && !x.spectating()).count();
        if (humans >= MAX_HUMANS) {
            s.team = SPECTATE;
            s.ready = true;
        } else {
            s.team = (int) (humans % 2);
            if (teamSize(s.team, null) >= MAX_PER_TEAM)
                s.team = SPECTATE;
        }
        seats.add(s);
    }

    static void close(String why) {
        if (!open)
            return;
        open = false;
        seats.clear();
        startCountdown = -1;
        LobbyClientboundPacket.broadcastSnapshot();
        if (why != null)
            say(why);
    }

    /**
     * Back to lobby (the tester, Oct 26: "keep playing on one server rather than making new ones"): once the match is
     * decided, the host resets it - units, buildings, wrecks, resources (PlayerServerEvents.resetRTS) - puts every
     * block the match changed back as it was (match.BattlefieldJournal) and reopens the lobby with the last lineup
     * (bots, and every human still online with their faction, colour, team and role) and the same settings; the
     * rematch is fought on the restored ground. Null when done, else why not (told to the host).
     */
    static String backToLobby(ServerPlayer sp) {
        if (!isLobbyHost(sp))
            return "Only the host can take everyone back to the lobby.";
        com.solegendary.reignofnether.match.MatchState state = MatchState.live();
        if (state.isPreparing() || startCountdown >= 0)
            return "A match is being set up - wait for it to start or finish.";
        if (state.phase() == com.solegendary.reignofnether.match.MatchPhase.PLAYING
                && com.solegendary.reignofnether.match.MatchRoster.any())
            return "The match is still being played: it has to be decided first (resign from the match menu).";
        MinecraftServer server = sp.getServer();
        if (server == null)
            return "No server.";
        ReignOfNether.LOGGER.info("[Lobby] {} went back to the lobby: resetting the battlefield", sp.getName().getString());
        // the match first (its buildings come down), then the ground: the restore must be the last write
        PlayerServerEvents.resetRTS(false);
        for (net.minecraft.world.entity.Entity w : new ArrayList<>(com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks()))
            w.discard();
        int restored = com.solegendary.reignofnether.match.BattlefieldJournal.restore(server);
        rematchCentre = lastCentre;
        reopen(sp, server);
        say("Back to the lobby: the battlefield is reset (" + restored + " block(s) restored). Same lineup and settings - ready up for the rematch.");
        return null;
    }

    /** Opens the lobby for {@code host} with the last lineup kept ({@link #keptSeats}), then everyone else online. */
    static void reopen(ServerPlayer host, MinecraftServer server) {
        open = true;
        seats.clear();
        startCountdown = -1;
        hostUuid = host.getUUID();
        hostName = host.getName().getString();
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            online.add(p.getUUID());
        List<Seat> kept = keptSeats(lastLineup, online, nextSeatId);
        nextSeatId += kept.size();
        seats.addAll(kept);
        addHuman(host);
        for (ServerPlayer other : server.getPlayerList().getPlayers())
            if (other != host)
                addHuman(other);
        dirty = true;
    }

    /**
     * The seats a rematch starts from: every bot of {@code last} as it was, and every human of {@code last} still
     * online (by UUID) with their faction, colour, team and role - not ready (each confirms again). Humans who left
     * are dropped; new seat ids from {@code firstId}. Pure: the game tests check it.
     */
    public static List<Seat> keptSeats(List<Seat> last, Set<UUID> online, int firstId) {
        List<Seat> out = new ArrayList<>();
        int id = firstId;
        for (Seat s : last) {
            if (!s.bot && (s.uuid == null || !online.contains(s.uuid)))
                continue;
            Seat k = new Seat(id++, s.bot);
            k.name = s.name;
            k.uuid = s.uuid;
            k.faction = s.faction;
            k.colorMapId = s.colorMapId;
            k.team = s.team;
            k.difficulty = s.difficulty;
            k.heroRole = s.heroRole;
            k.ready = s.bot || s.spectating();   // a spectator never holds the start up
            out.add(k);
        }
        return out;
    }

    /** One plain chat line to everyone online (lobby news is English text, not a translation key). */
    static void say(String msg) {
        for (ServerPlayer p : PlayerServerEvents.players)
            p.sendSystemMessage(Component.literal(msg));
    }

    // ---- events ----

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (startCountdown > 0 && --startCountdown == 0) {
            startCountdown = -1;
            try {
                startFromLobby(evt.getServer());
            } catch (Exception e) {
                ReignOfNether.LOGGER.error("[Lobby] the match failed to start", e);
                say("The match failed to start: " + e);
            }
        }
        // at most one snapshot every other tick, and only after a change
        if (dirty && evt.getServer().getTickCount() % 2 == 0) {
            dirty = false;
            LobbyClientboundPacket.broadcastSnapshot();
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp))
            return;
        if (open && startCountdown < 0) {
            addHuman(sp);
            dirty = true;
        } else if (open) {
            LobbyClientboundPacket.sendSnapshot(sp);
        }
        MultiplayerMatch m = MatchState.live().lobbyMatch();
        if (m != null && !m.humans.contains(sp.getName().getString())) {
            // joined mid-match: watch it (fog off, observer HUD), no base of their own (refusesLateStart)
            sp.sendSystemMessage(Component.literal("A match is in progress - you are spectating it."));
            LobbyClientboundPacket.sendSpectate(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp) || !open)
            return;
        if (sp.getUUID().equals(hostUuid)) {
            close("The host left - the lobby is closed.");
            return;
        }
        Seat s = seatOf(sp);
        if (s != null) {
            seats.remove(s);
            dirty = true;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        open = false;
        seats.clear();
        startCountdown = -1;
        hostUuid = null;
        hostName = "";
        dirty = false;
        lastLineup.clear();
        lastCentre = null;
        rematchCentre = null;
        // the match itself is saved and dropped by match.MatchServerEvents (HIGHEST snapshot, LOWEST clear)
        MultiplayerMatch.clearAll();
        com.solegendary.reignofnether.hero.HeroRoles.clearAll();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        // /rts-lobby: the host opens (or reopens the screen of) the lobby; anyone else sees the open one.
        // /rts-lobby close: the host closes it
        evt.getDispatcher().register(Commands.literal("rts-lobby")
            .executes(c -> {
                open(c.getSource().getPlayerOrException());
                return 1;
            })
            .then(Commands.literal("close").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                if (!isLobbyHost(sp))
                    return 0;
                close("The host closed the lobby.");
                return 1;
            })));
    }

    // ---- the start ----

    static void startFromLobby(MinecraftServer server) {
        if (!open)
            return;
        ServerLevel level = server.overworld();
        ServerPlayer host = hostUuid == null ? null : server.getPlayerList().getPlayer(hostUuid);
        // the ring is centred where the host stands (a fresh skirmish world: its spawn) - on the ground
        // - or, for a rematch after Back to lobby, where the last match was (its ground is the one just restored)
        BlockPos c = rematchCentre != null ? rematchCentre : host != null ? host.blockPosition() : level.getSharedSpawnPos();
        rematchCentre = null;
        BlockPos centre = new BlockPos(c.getX(), MetalPatches.solidTop(level, c.getX(), c.getZ()), c.getZ());
        List<Seat> lineup = new ArrayList<>(seats);
        lastLineup.clear();
        lastLineup.addAll(lineup);
        lastCentre = centre;
        open = false;
        seats.clear();
        LobbyClientboundPacket.broadcastSnapshot();   // closes the lobby screens
        // the host's economy pace for the whole match (income + build/production speed; EconomyPace)
        com.solegendary.reignofnether.resources.EconomyPace.setMatchCode(pace);
        // the host's light and day/night cycle (match.DayClock moves the sun once the match is played)
        com.solegendary.reignofnether.match.DayClock.apply(level, com.solegendary.reignofnether.match.TimeOfDay.byId(timeOfDay),
            com.solegendary.reignofnether.match.DayCycle.byId(dayCycle));
        com.solegendary.reignofnether.maps.MapPreset preset = com.solegendary.reignofnether.maps.MapPreset.byId(map);
        if (preset.premade() || choosePositions) {
            // a pre-made map rises first and/or everyone places their start (design/maps.md); the match is seated
            // when that is done, and only then is it live. MatchState keeps the setup with the world (CR-13) and
            // says "Match started" when it seats - after a reload too
            if (!com.solegendary.reignofnether.maps.MapMatch.begin(level, lineup, preset, choosePositions, arena, metal,
                    spawnDistance, centre, new Random(), MatchState.Origin.LOBBY, null))
                say("The match could not start: another one is being set up or fought in this world.");
            spectateLateArrivals(server, lineup);
            return;
        }
        if (MatchState.live().seatNow(() -> startMatch(level, lineup, arena, metal, spawnDistance, centre, new Random()),
                MatchState.Origin.LOBBY) == null)
            say("The match could not start: another one is being set up or fought in this world.");
        spectateLateArrivals(server, lineup);
    }

    /** Someone who arrived during the countdown got no seat and no START: they watch, like any later joiner. */
    static void spectateLateArrivals(MinecraftServer server, List<Seat> lineup) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            boolean inLineup = false;
            for (Seat s : lineup)
                if (!s.bot && sp.getUUID().equals(s.uuid))
                    inLineup = true;
            if (!inLineup)
                LobbyClientboundPacket.sendSpectate(sp);
        }
    }

    /**
     * Seats a lineup in {@code level}: colours made unique, every playing seat on one fair ring around
     * {@code centre} (teammates side by side), humans started with a readied base (capitol foundations, commander
     * and workers, starting resources - PlayerServerEvents.startRTS), bots likewise (BotServerEvents.addBotAt),
     * one alliance per team (and no alliance left over across teams from an earlier match). Humans are looked up
     * among PlayerServerEvents.players by UUID (the game tests add fake players there). Returns the registered
     * match; seats that could not start are left out of it.
     */
    public static MultiplayerMatch startMatch(ServerLevel level, List<Seat> lineup, int arena, int metal,
                                              int spawnDistance, BlockPos centre, Random rng) {
        List<Seat> playing = playingSorted(lineup);
        Map<Seat, Integer> colours = assignColours(playing);

        List<Vec3> starts = startPositions(level, centre, playing.size(), spawnDistance, rng.nextDouble() * Math.PI * 2);

        // fog needs a bounded play area: a border round the arena and the farthest start, before the first base
        int reach = 0;
        for (Vec3 s : starts)
            reach = Math.max(reach, (int) Math.ceil(Math.hypot(s.x - centre.getX(), s.z - centre.getZ())));
        com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.fitBorderForMatch(level, centre.getX() + 0.5,
            centre.getZ() + 0.5, SkirmishServerboundPacket.arenaRadius(arena), reach, false);

        // the battlefield forms a few seconds after the last base registers (BattlefieldSetup)
        BattlefieldSetup.arenaRadiusOverride = SkirmishServerboundPacket.arenaRadius(arena);
        BattlefieldSetup.richnessOverride = SkirmishServerboundPacket.metalRichness(metal);
        return seatAll(level, playing, heroSeats(lineup), colours, starts, rng);
    }

    /** The seats that play (not spectating), teammates next to each other around the ring like BAR's team sides. */
    public static List<Seat> playingSorted(List<Seat> lineup) {
        List<Seat> playing = new ArrayList<>();
        List<Seat> heroes = new ArrayList<>();
        for (Seat s : lineup)
            if (!s.spectating() && s.heroRole && !s.bot)
                heroes.add(s);   // no base and no place on the ring: attached to their team below
            else if (!s.spectating())
                playing.add(s);
        playing.sort(Comparator.comparingInt(s -> s.team));
        return playing;
    }

    /** The lineup's Hero-role seats (design/hero_mode.md): humans on a team who take no base of their own. */
    public static List<Seat> heroSeats(List<Seat> lineup) {
        List<Seat> heroes = new ArrayList<>();
        for (Seat s : lineup)
            if (!s.spectating() && s.heroRole && !s.bot)
                heroes.add(s);
        return heroes;
    }

    /** Colours: unique requests first, then the first free palette colour for the rest. */
    public static Map<Seat, Integer> assignColours(List<Seat> playing) {
        Set<Integer> taken = new HashSet<>(PlayerServerEvents.takenColorIds());
        Map<Seat, Integer> colours = new LinkedHashMap<>();
        for (Seat s : playing)
            if (s.colorMapId != 0 && PlayerPalette.isPaletteColor(s.colorMapId) && taken.add(s.colorMapId))
                colours.put(s, s.colorMapId);
        for (Seat s : playing)
            if (!colours.containsKey(s)) {
                int col = PlayerPalette.firstFree(taken);
                taken.add(col);
                colours.put(s, col);
            }
        return colours;
    }

    /**
     * Starts every playing seat at its start ({@code starts}, same order as {@code playing}): humans with a readied
     * base, bots through BotServerEvents.addBotAt, one alliance per team. Returns the registered match.
     */
    public static MultiplayerMatch seatAll(ServerLevel level, List<Seat> playing, Map<Seat, Integer> colours,
                                           List<Vec3> starts, Random rng) {
        return seatAll(level, playing, List.of(), colours, starts, rng);
    }

    /** {@link #seatAll} plus the lineup's Hero-role seats (heroSeats), attached to their team's commanders. */
    public static MultiplayerMatch seatAll(ServerLevel level, List<Seat> playing, List<Seat> heroes,
                                           Map<Seat, Integer> colours, List<Vec3> starts, Random rng) {
        Set<String> participants = new LinkedHashSet<>();

        Set<String> humans = new LinkedHashSet<>();
        Map<Integer, List<String>> teams = new LinkedHashMap<>();
        for (int i = 0; i < playing.size(); i++) {
            Seat s = playing.get(i);
            Vec3 pos = starts.get(i);
            Faction faction = SkirmishServerboundPacket.factionOf(s.faction, rng);
            String name = null;
            if (s.bot) {
                BotPlayer.Difficulty diff = switch (s.difficulty) {
                    case 0 -> BotPlayer.Difficulty.EASY;
                    case 2 -> BotPlayer.Difficulty.HARD;
                    default -> BotPlayer.Difficulty.MEDIUM;
                };
                name = BotServerEvents.addBotAt(level, faction, diff, pos, colours.get(s));
            } else {
                ServerPlayer sp = findPlayer(s);
                if (sp == null)
                    continue;   // left during the countdown
                // keep the base's chunks loaded until the player's camera gets there
                UnitChunkLoader.holdArea(level, BlockPos.containing(pos));
                // a dedicated server keeps each player's saved game mode: the RTS camera needs creative
                PlayerServerEvents.ensureCommanderGameMode(sp);
                PlayerServerEvents.startRTS(sp.getId(), pos, faction, colours.get(s));
                if (PlayerServerEvents.isRTSPlayer(sp.getName().getString())) {
                    name = sp.getName().getString();
                    humans.add(name);
                }
            }
            if (name == null)
                continue;
            participants.add(name);
            teams.computeIfAbsent(s.team, k -> new ArrayList<>()).add(name);
        }

        // teams are alliances; an alliance from an earlier match between two now on different teams is dissolved
        List<String> all = new ArrayList<>(participants);
        for (int i = 0; i < all.size(); i++)
            for (int j = i + 1; j < all.size(); j++) {
                String a = all.get(i), b = all.get(j);
                if (sameTeam(teams, a, b))
                    AlliancesServerEvents.addAlliance(a, b);
                else if (AlliancesServerEvents.isAllied(a, b))
                    AlliancesServerEvents.removeAlliance(a, b);
            }
        // Hero-role players (design/hero_mode.md): no base; they serve their team's commanders and may possess the
        // team's heroes (PossessionServer.mayPossess). They watch until they do.
        for (Seat s : heroes) {
            ServerPlayer sp = findPlayer(s);
            if (sp == null)
                continue;
            List<String> commanders = teams.getOrDefault(s.team, List.of());
            com.solegendary.reignofnether.hero.HeroRoles.assign(sp.getName().getString(), commanders);
            // the client prints "You are your team's Hero: press <key>" itself on this packet: only it knows what the
            // camera toggle is bound to (design/code_review.md CR-07)
            com.solegendary.reignofnether.hero.PossessionClientboundPacket.role(sp, true);
        }
        ReignOfNether.LOGGER.info("[Lobby] match started: {} side(s), teams {}", participants.size(), teams);
        return new MultiplayerMatch(participants, humans).register();
    }

    static boolean sameTeam(Map<Integer, List<String>> teams, String a, String b) {
        for (List<String> members : teams.values())
            if (members.contains(a))
                return members.contains(b);
        return false;
    }

    static ServerPlayer findPlayer(Seat s) {
        for (ServerPlayer p : PlayerServerEvents.players)
            if (s.uuid != null ? s.uuid.equals(p.getUUID()) : s.name.equals(p.getName().getString()))
                return p;
        return null;
    }

    /** The ring's radius for {@code n} sides: the lobby's spawn distance, widened so neighbours stay 150 apart. */
    public static int ringRadius(int n, int spawnDistance) {
        int base = (SkirmishServerboundPacket.spawnMinR(spawnDistance) + SkirmishServerboundPacket.spawnMaxR(spawnDistance)) / 2;
        if (n < 2)
            return base;
        double neighbour = 75 / Math.sin(Math.PI / n);   // half of 150 over sin(half the angle between sides)
        return (int) Math.max(base, Math.ceil(neighbour));
    }

    /**
     * Where each of {@code n} sides should start: evenly around a ring about {@code centre} (rotated by
     * {@code rotation} radians), before the ground is checked. Every side is the same distance from the centre and
     * from its neighbours, which is what makes a start fair.
     */
    public static List<int[]> ringTargets(BlockPos centre, int n, int radius, double rotation) {
        List<int[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double a = rotation + Math.PI * 2 * i / Math.max(1, n);
            out.add(new int[] { centre.getX() + (int) Math.round(Math.cos(a) * radius),
                                centre.getZ() + (int) Math.round(Math.sin(a) * radius) });
        }
        return out;
    }

    /** The ring targets moved onto dry, fairly flat ground nearby, at ground height (the start block). */
    public static List<Vec3> startPositions(ServerLevel level, BlockPos centre, int n, int spawnDistance, double rotation) {
        List<Vec3> out = new ArrayList<>(n);
        for (int[] t : ringTargets(centre, n, ringRadius(n, spawnDistance), rotation))
            out.add(groundSite(level, t[0], t[1]));
        return out;
    }

    // offsets tried around a ring target, nearest first; the first dry, flat one wins (else the target itself)
    private static final int[][] SITE_OFFSETS = {
        { 0, 0 }, { 16, 0 }, { -16, 0 }, { 0, 16 }, { 0, -16 }, { 24, 24 }, { -24, 24 }, { 24, -24 }, { -24, -24 },
        { 40, 0 }, { -40, 0 }, { 0, 40 }, { 0, -40 }
    };

    public static Vec3 groundSite(ServerLevel level, int x, int z) {
        for (int[] o : SITE_OFFSETS) {
            int sx = x + o[0], sz = z + o[1];
            if (siteOk(level, sx, sz))
                return new Vec3(sx + 0.5, MetalPatches.solidTop(level, sx, sz), sz + 0.5);
        }
        return new Vec3(x + 0.5, MetalPatches.solidTop(level, x, z), z + 0.5);
    }

    // the same test the bot spawn search uses: no water at the centre or 12 blocks around, at most 10 blocks of slope
    public static boolean siteOk(ServerLevel level, int x, int z) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int dx = -12; dx <= 12; dx += 12)
            for (int dz = -12; dz <= 12; dz += 12) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, z + dz);
                if (!level.getBlockState(new BlockPos(x + dx, y - 1, z + dz)).getFluidState().isEmpty())
                    return false;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        return maxY - minY <= 10;
    }
}
