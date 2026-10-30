package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The skirmish lobby's "Start Battle": one packet carrying every choice. The server starts the sender as an RTS
 * player with a readied start (their colour, capitol foundations already laid, commander and workers beside it),
 * pins the arena size and metal richness for the battlefield that forms a few seconds later, and calls in each
 * configured bot at the chosen spawn distance.
 *
 * Faction codes: 0 Sunforged Kingdom (villagers), 1 Gravebound (monsters), 2 Ironhide Horde (piglins), 3 random,
 * 4 Verdant Court, 5 Tidewrought, 6 Stormcallers, 7 Brassworks (all live since their slice 1). 3 kept its meaning when the Court arrived (codes travel as bytes
 * and persist in the lobby screen); the table is CODE_PATHS.
 * Arena: 0 small, 1 medium, 2 large, 3 huge, 4 random.  Metal: 0 lean, 1 normal, 2 rich, 3 random.
 * Spawn distance: 0 close, 1 normal, 2 far.  Difficulty: 0 easy, 1 medium, 2 hard.
 * Economy pace: 0 relaxed, 1 standard, 2 fast (EconomyPace; the last byte, so older fields keep their place).
 * After the map and start-position fields: the pre-made map's seed (a long, MapLayout.build; 0 = unseeded) and the
 * time of day (a byte, match.TimeOfDay ids) - inbox spec-2 PR 1; then the day/night cycle (a byte, match.DayCycle ids).
 */
public class SkirmishServerboundPacket {

    /** @param colorMapId PlayerPalette map colour id, or 0 for "first free colour" */
    public record BotSpec(int faction, int difficulty, int colorMapId, int team) { }

    private final int faction;
    private final int colorMapId;
    private final int team;
    private final List<BotSpec> bots;
    private final int arena;
    private final int metal;
    private final int spawnDistance;
    private final int pace;
    private final int map;                    // MapPreset id (design/maps.md)
    private final boolean choosePositions;    // BAR's start-position phase
    private final long mapSeed;               // the pre-made map's seed, rolled by the lobby (its preview shows it)
    private final int timeOfDay;              // match.TimeOfDay id: the light at Start
    private final int dayCycle;               // match.DayCycle id: whether and how fast the sun then moves

    public SkirmishServerboundPacket(int faction, int colorMapId, int team, List<BotSpec> bots, int arena, int metal, int spawnDistance, int pace) {
        this(faction, colorMapId, team, bots, arena, metal, spawnDistance, pace, 0, false);
    }

    public SkirmishServerboundPacket(int faction, int colorMapId, int team, List<BotSpec> bots, int arena, int metal,
                                     int spawnDistance, int pace, int map, boolean choosePositions) {
        this(faction, colorMapId, team, bots, arena, metal, spawnDistance, pace, map, choosePositions, 0L, 0,
            com.solegendary.reignofnether.match.DayCycle.DEFAULT.ordinal());
    }

    public SkirmishServerboundPacket(int faction, int colorMapId, int team, List<BotSpec> bots, int arena, int metal,
                                     int spawnDistance, int pace, int map, boolean choosePositions, long mapSeed,
                                     int timeOfDay, int dayCycle) {
        this.mapSeed = mapSeed;
        this.timeOfDay = timeOfDay;
        this.dayCycle = dayCycle;
        this.map = map;
        this.choosePositions = choosePositions;
        this.faction = faction;
        this.colorMapId = colorMapId;
        this.team = team;
        this.bots = bots;
        this.arena = arena;
        this.metal = metal;
        this.spawnDistance = spawnDistance;
        this.pace = pace;
    }

    public static void send(int faction, int colorMapId, int team, List<BotSpec> bots, int arena, int metal, int spawnDistance, int pace) {
        PacketHandler.INSTANCE.sendToServer(new SkirmishServerboundPacket(faction, colorMapId, team, bots, arena, metal, spawnDistance, pace));
    }

    public static void send(int faction, int colorMapId, int team, List<BotSpec> bots, int arena, int metal,
                            int spawnDistance, int pace, int map, boolean choosePositions) {
        PacketHandler.INSTANCE.sendToServer(new SkirmishServerboundPacket(faction, colorMapId, team, bots, arena, metal,
            spawnDistance, pace, map, choosePositions));
    }

    public static void send(int faction, int colorMapId, int team, List<BotSpec> bots, int arena, int metal,
                            int spawnDistance, int pace, int map, boolean choosePositions, long mapSeed, int timeOfDay,
                            int dayCycle) {
        PacketHandler.INSTANCE.sendToServer(new SkirmishServerboundPacket(faction, colorMapId, team, bots, arena, metal,
            spawnDistance, pace, map, choosePositions, mapSeed, timeOfDay, dayCycle));
    }

    public SkirmishServerboundPacket(FriendlyByteBuf buf) {
        faction = buf.readByte();
        colorMapId = buf.readInt();
        team = buf.readByte();
        int n = buf.readByte();
        bots = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            bots.add(new BotSpec(buf.readByte(), buf.readByte(), buf.readInt(), buf.readByte()));
        arena = buf.readByte();
        metal = buf.readByte();
        spawnDistance = buf.readByte();
        pace = buf.readByte();
        map = buf.readByte();
        choosePositions = buf.readBoolean();
        mapSeed = buf.readLong();
        timeOfDay = buf.readByte();
        dayCycle = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(faction);
        buf.writeInt(colorMapId);
        buf.writeByte(team);
        buf.writeByte(bots.size());
        for (BotSpec b : bots) {
            buf.writeByte(b.faction());
            buf.writeByte(b.difficulty());
            buf.writeInt(b.colorMapId());
            buf.writeByte(b.team());
        }
        buf.writeByte(arena);
        buf.writeByte(metal);
        buf.writeByte(spawnDistance);
        buf.writeByte(pace);
        buf.writeByte(map);
        buf.writeBoolean(choosePositions);
        buf.writeLong(mapSeed);
        buf.writeByte(timeOfDay);
        buf.writeByte(dayCycle);
    }

    /** The code the "Random" entry sends; it kept 3 when the Court arrived. */
    public static final int RANDOM_CODE = 3;

    // faction code -> registry path, indexed by code. 3 is Random; 5 is the Tidewrought (design/tidewrought_plan.md),
    // 6 the Stormcallers (design/stormcallers_plan.md), 7 the Brassworks (design/brassworks_plan.md), live since slice 1. A new
    // faction appends its path here, so every code already in a remembered lobby keeps its meaning and the lobby's
    // picker lists it without further changes
    static final List<String> CODE_PATHS = List.of("villagers", "monsters", "piglins", "random", "verdant_court", "tidewrought",
        "stormcallers", "brassworks");

    /** Number of codes in use (0 .. codeCount()-1), Random included. */
    public static int codeCount() {
        return CODE_PATHS.size();
    }

    /** The faction a code names, Factions.RANDOM for the Random code, NONE for an unknown code. Never rolls. */
    public static Faction factionForCode(int code) {
        if (code < 0 || code >= CODE_PATHS.size())
            return Factions.NONE;
        return Factions.getFaction(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, CODE_PATHS.get(code)));
    }

    /** The code for a faction (Random gives RANDOM_CODE), or -1 when the faction has none. */
    public static int codeOf(Faction f) {
        if (f == null || f.key == null || !ReignOfNether.MOD_ID.equals(f.key.getNamespace()))
            return -1;
        return CODE_PATHS.indexOf(f.key.getPath());
    }

    /**
     * The faction a player or bot actually starts as. Only a live faction is honoured; Random, an unknown code and a
     * preview faction (none right now; the Tidewrought on 5 and the Stormcallers on 6 were until their slice 1) all roll a random live one, so a
     * modified client can't start a preview from the skirmish screen either.
     */
    public static Faction factionOf(int code, Random rng) {
        Faction f = factionForCode(code);
        return Factions.isLive(f) ? f : Factions.randomLive(rng);
    }

    // lobby codes -> battlefield numbers, shared by this lobby and the multiplayer one (MultiplayerLobby)
    /** Arena code (0 small .. 3 huge, else random) to the boundary wall's radius; -1 rolls it. */
    public static int arenaRadius(int arena) {
        return switch (arena) {
            case 0 -> 230;
            case 1 -> 320;
            case 2 -> 420;
            case 3 -> 560;
            default -> -1;
        };
    }

    /** Metal code (0 lean, 1 normal, 2 rich, else random) to the patch richness; -1 rolls it. */
    public static float metalRichness(int metal) {
        return switch (metal) {
            case 0 -> 0.65f;
            case 1 -> 1.0f;
            case 2 -> 1.5f;
            default -> -1f;
        };
    }

    /**
     * The lobby's metal setting as the open-ground metal of a pre-made map (maps spec M1): lean, normal and rich scale
     * how many patches the fill places in every slot. Random keeps the normal map - a pre-made map is the same map the
     * lobby preview drew for its seed, so the roll that random terrain makes at formation has no place here.
     */
    public static com.solegendary.reignofnether.maps.MetalOptions premadeMetal(int metal) {
        float richness = metalRichness(metal);
        return richness < 0 ? com.solegendary.reignofnether.maps.MetalOptions.DEFAULT
            : com.solegendary.reignofnether.maps.MetalOptions.ofRichness(richness);
    }

    /** Spawn-distance code (0 close, 1 normal, 2 far) to the inner and outer radius of the bot spawn ring. */
    public static int spawnMinR(int spawnDistance) {
        return switch (spawnDistance) { case 0 -> 140; case 2 -> 300; default -> 180; };
    }

    public static int spawnMaxR(int spawnDistance) {
        return switch (spawnDistance) { case 0 -> 200; case 2 -> 420; default -> 260; };
    }

    // the registry path ("villagers", "monsters", "piglins", "verdant_court") is what /bot add parses; an unknown faction is then
    // refused there instead of silently becoming a villager bot
    static String factionName(Faction f) {
        return f.getName();
    }

    /** Seats this lobby through maps.MapMatch; false when there is nobody to fight (the instant path then runs). */
    private boolean startPrepared(ServerPlayer player, Random rng) {
        List<MultiplayerLobby.Seat> lineup = new ArrayList<>();
        int id = 1;
        if (team >= 0) {
            MultiplayerLobby.Seat me = new MultiplayerLobby.Seat(id++, false);
            me.name = player.getName().getString();
            me.uuid = player.getUUID();
            me.faction = faction;
            me.colorMapId = colorMapId != 0 ? colorMapId : 1;
            me.team = team;
            me.ready = true;
            lineup.add(me);
        }
        for (BotSpec b : bots) {
            MultiplayerLobby.Seat s = new MultiplayerLobby.Seat(id++, true);
            s.name = "AI";
            s.faction = b.faction();
            s.difficulty = b.difficulty();
            s.colorMapId = b.colorMapId();
            s.team = Math.max(0, b.team());
            s.ready = true;
            lineup.add(s);
        }
        if (lineup.size() < 2)
            return false;
        com.solegendary.reignofnether.resources.EconomyPace.setMatchCode(pace);
        int gx = player.getBlockX(), gz = player.getBlockZ();
        int gy = com.solegendary.reignofnether.resources.MetalPatches.solidTop(player.serverLevel(), gx, gz);
        // match.MatchState keeps the setup with the world, so a save-and-quit while the map rises or starts are
        // placed resumes on reload (code_review CR-13)
        boolean started = com.solegendary.reignofnether.maps.MapMatch.begin(player.serverLevel(), lineup,
            com.solegendary.reignofnether.maps.MapPreset.byId(map), choosePositions, arena, metal, spawnDistance,
            com.solegendary.reignofnether.maps.ArenaOrigins.centreFor(player.serverLevel(),   // the slot once a new match moved the arena
                new net.minecraft.core.BlockPos(gx, gy, gz)), rng,
            com.solegendary.reignofnether.match.MatchState.Origin.SKIRMISH, mapSeed,
            com.solegendary.reignofnether.match.TimeOfDay.byId(timeOfDay), m -> ReignOfNether.LOGGER.info(
                "[Skirmish] {} started on {} with {} side(s)", player.getName().getString(),
                com.solegendary.reignofnether.maps.MapPreset.byId(map).displayName, m.participants.size()));
        if (!started)
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "A match is already being set up or fought in this world - start the battle from a fresh world."));
        return true;
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {

        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;
            // the singleplayer lobby's start spawns bots and sizes the arena for the whole world: only the host may
            // send it (a joined player starts through MultiplayerLobby), and never into a running lobby match
            if (!com.solegendary.reignofnether.network.PacketAuth.isHost(player) || MultiplayerLobby.isMatchLive()
                    || com.solegendary.reignofnether.maps.MapMatch.isPreparing()) {
                ReignOfNether.LOGGER.warn("[Skirmish] refused a skirmish start from {}", player.getName().getString());
                return;
            }
            Random rng = new Random();
            // the battlefield light and day/night cycle the lobby chose (inbox spec-2 PR 1; lovish's Oct 18 cycle ask).
            // Start always runs in a fresh world, which PR #2 already started at late morning with the cycle off; this
            // sets the host's pick on both roads below, and match.DayClock moves the sun once the match is played
            com.solegendary.reignofnether.match.TimeOfDay light = com.solegendary.reignofnether.match.TimeOfDay.byId(timeOfDay);
            com.solegendary.reignofnether.match.DayClock.apply(player.serverLevel(), light,
                com.solegendary.reignofnether.match.DayCycle.byId(dayCycle));
            ReignOfNether.LOGGER.info("[Skirmish] time of day {}, map seed {}", light.name(), mapSeed);
            // a pre-made map and/or the start-position phase: the same road as the multiplayer lobby, with this
            // lobby's line-up as seats (design/maps.md). Random terrain with Auto starts keeps the instant path below
            if (com.solegendary.reignofnether.maps.MapPreset.byId(map).premade() || choosePositions) {
                if (startPrepared(player, rng))
                    return;
            }
            Faction mine = factionOf(faction, rng);

            // arena + metal for the battlefield that forms once everyone is in
            BattlefieldSetup.arenaRadiusOverride = arenaRadius(arena);
            BattlefieldSetup.richnessOverride = metalRichness(metal);
            // the economy pace for the whole match (income + build/production speed; EconomyPace)
            com.solegendary.reignofnether.resources.EconomyPace.setMatchCode(pace);
            int minR = spawnMinR(spawnDistance);
            int maxR = spawnMaxR(spawnDistance);

            // the player: readied start (colorMapId != 0) lays the capitol foundations and seats the commander.
            // In the RTS view the player entity floats at camera height, so snap the start to the ground.
            int gx = player.getBlockX(), gz = player.getBlockZ();
            // the start position is the GROUND block (building origins put blocks at origin.y+1, and the start
            // block of the classic flow sits in the ground too); the surface heightmap is one above that
            int gy = com.solegendary.reignofnether.resources.MetalPatches.solidTop(player.serverLevel(), gx, gz);
            Vec3 pos = new Vec3(gx + 0.5, gy, gz + 0.5);
            // a spectator (team < 0, SkirmishSetupScreen.Settings.SPECTATE) gets no base and never joins the match:
            // not an RTS player, so the fog stays off and the HUD is RoN's observer view; the bots ring their spot
            boolean spectator = team < 0;
            // fog needs a bounded play area: a border round the arena and the bot ring, before the first base
            com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.fitBorderForMatch(player.serverLevel(),
                gx + 0.5, gz + 0.5, arenaRadius(arena), maxR + Math.max(0, bots.size() - 1) * 35, false);
            if (!spectator)
                PlayerServerEvents.startRTS(player.getId(), pos, mine, colorMapId != 0 ? colorMapId : 1);

            // bigger lobbies need a wider spawn ring so bases do not pile up
            maxR += Math.max(0, bots.size() - 1) * 35;

            int added = 0;
            java.util.Map<Integer, List<String>> teams = new java.util.HashMap<>();
            if (!spectator)
                teams.computeIfAbsent(team, k -> new ArrayList<>()).add(player.getName().getString());
            for (BotSpec b : bots) {
                String diff = switch (b.difficulty()) { case 0 -> "easy"; case 2 -> "hard"; case 3 -> "passive"; default -> "medium"; };
                BotServerEvents.lastBotName = null;
                added += BotServerEvents.addBot(player, factionName(factionOf(b.faction(), rng)), diff, minR, maxR, b.colorMapId());
                if (BotServerEvents.lastBotName != null)
                    teams.computeIfAbsent(b.team(), k -> new ArrayList<>()).add(BotServerEvents.lastBotName);
            }
            // teams are alliances: everyone sharing a team fights together
            for (List<String> members : teams.values())
                for (int i = 0; i < members.size(); i++)
                    for (int j = i + 1; j < members.size(); j++)
                        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(members.get(i), members.get(j));
            ReignOfNether.LOGGER.info("[Skirmish] {} starts as {} with {} bot(s); arena {}, metal {}, spawn {}, pace {}",
                player.getName().getString(), factionName(mine), added, arena, metal, spawnDistance,
                com.solegendary.reignofnether.resources.EconomyPace.name(pace));
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
