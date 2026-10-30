package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.ability.TradeAction;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.alliance.AllyCommand;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.neutral.Beacon;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogChunkSnapshot;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.gamemode.GameMode;
import com.solegendary.reignofnether.gamemode.GameModeClientboundPacket;
import com.solegendary.reignofnether.gamerules.GameruleClientboundPacket;
import com.solegendary.reignofnether.guiscreen.TopdownGuiContainer;
import com.solegendary.reignofnether.hero.HeroClientboundPacket;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.items.ItemServerEvents;
import com.solegendary.reignofnether.items.RandomItemDropRule;
import com.solegendary.reignofnether.match.MatchOutcome;
import com.solegendary.reignofnether.match.MatchRoster;
import com.solegendary.reignofnether.match.MatchState;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.match.Spectators;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.research.ResearchClientboundPacket;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.scenario.ScenarioRole;
import com.solegendary.reignofnether.scenario.ScenarioServerEvents;
import com.solegendary.reignofnether.scenario.ScenarioUtils;
import com.solegendary.reignofnether.startpos.StartPosServerEvents;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.time.TimeServerEvents;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.tutorial.TutorialServerEvents;
import com.solegendary.reignofnether.unit.UnitChunkLoader;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.worldborder.WorldBorderServerEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import java.util.*;

import static com.solegendary.reignofnether.building.BuildingServerEvents.random;
import static com.solegendary.reignofnether.building.BuildingServerEvents.saveBuildings;
import static com.solegendary.reignofnether.items.RandomItemDropRule.ENABLED_NON_STRICT;
import static com.solegendary.reignofnether.items.RandomItemDropRule.ENABLED_STRICT;
import static com.solegendary.reignofnether.time.TimeUtils.getWaveSurvivalTimeModifier;
import static net.minecraft.world.level.GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK;

// this class tracks all available players so that any serverside functions that need to affect the player can be
// performed here by sending a client->server packet containing MC.player.getId()
//
// Rewrite 5 (design/ron_rewrite_plan.md): the match lifecycle - who is in play, defeat, victory, spectating, the
// match clock and its phases - lives in the match package now (MatchState, MatchRoster, MatchOutcome, Spectators).
// The public methods and fields below that belonged to it stay as thin delegates, so callers elsewhere are unchanged.

public class PlayerServerEvents {

    // list of what gamemode these players should be in when outside of RTS cam
    private static final Map<String, GameType> playerDefaultGameModes = new HashMap<>();
    private static final Map<String, Boolean> playerGuiOpenStatus = new HashMap<>();

    public static final ArrayList<ServerPlayer> players = new ArrayList<>();
    public static final ArrayList<ServerPlayer> orthoviewPlayers = new ArrayList<>();
    // players that are currently playing a match: match.MatchRoster's copy-on-write list (game thread only; a walk is
    // a snapshot, so a side may be beaten in the middle of one). The synchronized blocks some callers still put
    // around it are harmless and no longer needed.
    public static final List<RTSPlayer> rtsPlayers = MatchRoster.LIVE;
    // list of players after a match ends (beaten, or winners) - MatchRoster.FINISHED
    public static final List<RTSPlayer> postGameRtsPlayers = MatchRoster.FINISHED;
    public static boolean rtsLocked = false; // can players join as RTS players or not?
    public static boolean rtsSyncingEnabled = true; // will logging in players sync units and buildings?

    private static final int MONSTER_START_TIME_OF_DAY = 500; // 500 = dawn, 6500 = noon, 12500 = dusk

    public static final int TICKS_TO_REVEAL = 60 * ResourceCost.TICKS_PER_SECOND;

    /**
     * The name every skirmish world is created with (matchstart.QuickBattle.WORLD_NAME_PREFIX, kept in step by hand:
     * that class is client-only). In such a world the login chat lesson is skipped - the launch's loading screen and
     * hint bar carry the controls and the /rts-help line (inbox/spec-3, section 2.2; rover run 1, finding 8).
     */
    static final String SKIRMISH_WORLD_NAME_PREFIX = "Beyond and Tabs - Skirmish ";

    /** A world the skirmish screen made (Start Battle / Host Online), by its level name. */
    public static boolean isSkirmishWorld(@Nullable MinecraftServer server) {
        try {
            return server != null && server.getWorldData().getLevelName().startsWith(SKIRMISH_WORLD_NAME_PREFIX);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The match clock in ticks: runs while at least one side is in play. A mirror for older callers, written every
     * tick by match.MatchServerEvents - the clock itself is MatchState.live().clock(), saved with the world.
     */
    public static long rtsGameTicks = 0;
    // the beacon count last sent per side (server thread only), so an unchanged count isn't re-sent every second
    private static final Map<String, Long> lastBeaconTicksSent = new HashMap<>();

    public static ServerLevel serverLevel = null;

    // warpten - faster building/unit production
    // operationcwal - faster resource gathering
    // modifythephasevariance - ignore building requirements
    // medievalman - get all research (cannot reverse)
    // greedisgood X - gain X of each resource
    // foodforthought - ignore soft population caps
    // thereisnospoon - allow changing survival wave by clicking the wave indicator and using debug commands
    // slipslopslap - monster units are unaffected by sunlight
    // wouldyoukindly - allow control of non-unit mobs in RTS mode
    // thebeastofcaerbannog - spawns the Killer Rabbit
    // elitetaurenchieftain - levels all owned heroes to 10
    public static final List<String> singleWordCheats = List.of(
        "warpten",
        "operationcwal",
        "modifythephasevariance",
        "medievalman",
        "foodforthought",
        "thereisnospoon",
        "slipslopslap",
        "wouldyoukindly"
    );

    public static void saveRTSPlayers() {
        if (serverLevel == null) {
            return;
        }
        RTSPlayerSaveData data = RTSPlayerSaveData.getInstance(serverLevel);
        data.rtsPlayers.clear();
        data.rtsPlayers.addAll(rtsPlayers);
        data.save();   // dirty only: the world save writes it (persistence/RtsSaves)
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);

        if (level != null) {
            RTSPlayerSaveData data = RTSPlayerSaveData.getInstance(level);

            rtsPlayers.clear();
            rtsPlayers.addAll(data.rtsPlayers);
            FogOfWarServerEvents.invalidateRtsCache();

            for (RTSPlayer rtsPlayer : rtsPlayers) {
                if (rtsPlayer.faction == Factions.NONE) {
                    GameModeClientboundPacket.setAndLockAllClientGameModes(GameMode.SANDBOX);
                    enableAllCheats(rtsPlayer.name);
                    break;
                }
            }
            UnitServerEvents.maxPopulation = level.getGameRules().getInt(GameRuleRegistrar.MAX_POPULATION);

            level.getGameRules().getRule(RULE_DISABLE_ELYTRA_MOVEMENT_CHECK).set(true, evt.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        saveRTSPlayers();
    }

    public static boolean isRTSPlayer(String playerName) {
        return MatchRoster.isInPlay(playerName);
    }

    @Nullable
    public static RTSPlayer getRTSPlayer(String playerName) {
        return MatchRoster.get(playerName);
    }

    public static boolean isRTSPlayer(int id) {
        return MatchRoster.isInPlay(id);
    }

    public static boolean isBot(String playerName) {
        return MatchRoster.isBot(playerName);
    }

    public static boolean isBot(int id) {
        return MatchRoster.isBot(id);
    }

    public static boolean isGameActive() {
        return MatchRoster.any();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);

        if (FogChunkSnapshot.shouldRecapture) {
            FogChunkSnapshot.captureFogChunks(serverLevel);
        }
        // the fog snapshot is captured over several ticks with a per-tick time budget (FogChunkSnapshot)
        if (evt.phase == TickEvent.Phase.END)
            FogChunkSnapshot.tick();

        // the match clock, its graph samples and its sync run in match.MatchServerEvents; the roster walks below are
        // snapshots (MatchRoster), so the beacon victory may beat sides from inside them
        if (evt.phase == TickEvent.Phase.END) {
            for (RTSPlayer rtsPlayer : rtsPlayers)
                rtsPlayer.serverTick();

            for (RTSPlayer rtsPlayer : rtsPlayers) {
                if (rtsPlayer.beaconOwnerTicks == Beacon.getTicksToWin(serverLevel)) {
                    PlayerServerEvents.beaconVictory(rtsPlayer.name);
                    break;
                }
            }
            long clock = MatchState.live().clock();
            if (!rtsPlayers.isEmpty() && clock > 0 && clock % 20 == 0) {
                // only a count that moved (and everyone every 10 s for late joiners): with no beacon on the
                // map this was one packet per side per second to every client, all of them zero
                boolean refresh = clock % 200 == 0;
                for (RTSPlayer rtsPlayer : rtsPlayers) {
                    Long last = lastBeaconTicksSent.get(rtsPlayer.name);
                    if (refresh || last == null || last != rtsPlayer.beaconOwnerTicks) {
                        lastBeaconTicksSent.put(rtsPlayer.name, (long) rtsPlayer.beaconOwnerTicks);
                        PlayerClientboundPacket.syncBeaconOwnerTicks(rtsPlayer.name, rtsPlayer.beaconOwnerTicks);
                    }
                }
            }
        }
        // the RTS players reach the disk with Minecraft's world save (persistence/RtsSaves, plan 014), not every 60 s
    }

    // what a player who just logged in needs about every unit; the per-unit part goes to that player only
    // (it was broadcast, so each login re-sent the whole army to everyone already playing)
    private static void syncUnits(ServerPlayer joiner) {
        if (joiner.hasDisconnected())
            return;
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (entity instanceof Unit unit) {
                UnitSyncClientboundPacket.sendJoinSync(unit, joiner);
            }
            if (entity instanceof HeroUnit hero) {
                HeroClientboundPacket.setExperience(entity.getId(), hero.getExperience());
                HeroClientboundPacket.setSkillPoints(entity.getId(), hero.getSkillPoints());
                HeroClientboundPacket.setCharges(entity.getId(), hero.getChargesForSaveData());
                List<HeroAbility> abls = hero.getHeroAbilities();
                if (abls.size() > 0)
                    HeroClientboundPacket.setAbilityRank(entity.getId(), abls.get(0).getRank(hero), 0);
                if (abls.size() > 1)
                    HeroClientboundPacket.setAbilityRank(entity.getId(), abls.get(1).getRank(hero), 1);
                if (abls.size() > 2)
                    HeroClientboundPacket.setAbilityRank(entity.getId(), abls.get(2).getRank(hero), 2);
                if (abls.size() > 3)
                    HeroClientboundPacket.setAbilityRank(entity.getId(), abls.get(3).getRank(hero), 3);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        ServerPlayer serverPlayer = (ServerPlayer) evt.getEntity();

        players.add((ServerPlayer) evt.getEntity());
        String playerName = serverPlayer.getName().getString();
        ReignOfNether.LOGGER.info("Player logged in: " + playerName + ", id: " + serverPlayer.getId());

        // if a player is looking directly at a frozenchunk on login, they may load in the real blocks before
        // they are frozen so move them away then BuildingClientEvents.placeBuilding moves them to their base later
        // don't do this if they don't own any buildings
        /*
        if (isRTSPlayer(playerName) && rtsSyncingEnabled) {
            for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
                if (building.ownerName.equals(playerName)) {
                    movePlayer(serverPlayer.getId(), 0, ORTHOVIEW_PLAYER_BASE_Y, 0);
                    break;
                }
            }
        }
         */
        if (rtsSyncingEnabled) {
            MinecraftServer server = evt.getEntity().level().getServer();
            if (server == null || !server.isDedicatedServer()) {
                // a second later, on the server thread (syncUnits walks the unit list the server thread mutates)
                TaskSchedulerServerEvents.schedule(20, () -> syncUnits(serverPlayer));
            } else {
                syncUnits(serverPlayer);
            }
            ResearchServerEvents.syncResearch(playerName);
            ResearchServerEvents.syncCheats(playerName);
        }

        boolean inOrthoviewList = false;
        for (ServerPlayer orthoviewPlayer : orthoviewPlayers) {
            if (orthoviewPlayer.getId() == evt.getEntity().getId())  {
                inOrthoviewList = true;
                break;
            }
        }
        if (!inOrthoviewList)
            orthoviewPlayers.add((ServerPlayer) evt.getEntity());

        // a skirmish world: no chat lesson at login (welcome, "/rts-help", the op's "/rts-fog" lines) - it landed on
        // top of every match start (rover run 1, finding 8); the loading screen's tips and the hint bar after it say
        // what matters, once (MatchLoadingScreen, MapClient.onVeilCut)
        boolean skirmish = isSkirmishWorld(evt.getEntity().getServer());
        if (skirmish)
            ReignOfNether.LOGGER.info("[Player] {} joined a skirmish world: the login chat lesson is skipped",
                playerName);
        if (!TutorialServerEvents.isEnabled() && !skirmish) {
            if (!isRTSPlayer(serverPlayer.getId())) {
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.welcome")
                    .withStyle(Style.EMPTY.withBold(true)));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.join"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.help"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.controls"));
                if (rtsLocked) {
                    serverPlayer.sendSystemMessage(Component.literal(""));
                    serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.locked"));
                }
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.welcome_back")
                    .withStyle(Style.EMPTY.withBold(true)));
            }
            if (serverPlayer.hasPermissions(4)) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.op_commands"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.fog"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.lock"));;
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.reset"));
                serverPlayer.sendSystemMessage(Component.literal(""));
            }
            if (!rtsSyncingEnabled) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.sync_disabled1"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.sync_disabled2"));
                serverPlayer.sendSystemMessage(Component.literal(""));
            }
        }
        if (getRTSPlayer(playerName) == null) {
            PlayerClientboundPacket.removeRTSPlayer(playerName);
        }
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            PlayerClientboundPacket.addRTSPlayer(rtsPlayer.name, rtsPlayer.faction, (long) rtsPlayer.id, rtsPlayer.startPosColorId, rtsPlayer.isDogPerson);
        }

        if (rtsLocked) {
            PlayerClientboundPacket.lockRTS(playerName);
        } else {
            PlayerClientboundPacket.unlockRTS(playerName);
        }

        if (rtsSyncingEnabled) {
            PlayerClientboundPacket.enableStartRTS(playerName);
        } else {
            PlayerClientboundPacket.disableStartRTS(playerName);
        }

        updateMarketRates(playerName);
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent evt) {
        int id = evt.getEntity().getId();
        ReignOfNether.LOGGER.info("Player logged out: " + evt.getEntity().getName().getString() + ", id: " + id);
        players.removeIf(player -> player.getId() == id);
        com.solegendary.reignofnether.network.PacketAuth.forget(evt.getEntity().getUUID());
    }

    public static void startRTS(int playerId, Vec3 pos, Faction faction) {
        startRTS(playerId, pos, faction, 0);
    }

    // readied start is a simultaneous start from players using RTS start pos blocks, difference being:
    // - places the capitol foundations automatically
    // - spawns workers outside the foundations
    // - no start messages are sent other than the one from the countdown
    /** Server tick at which each side started; a side is never "defeated" for having no units in its first seconds. */
    public static final java.util.Map<String, Long> startedAtTick = new java.util.concurrent.ConcurrentHashMap<>();
    public static final int START_GRACE_TICKS = 20 * 15;

    public static boolean inStartGrace(String playerName) {
        Long t = startedAtTick.get(playerName);
        if (t == null || serverLevel == null)
            return false;
        return serverLevel.getGameTime() - t < START_GRACE_TICKS;
    }

    /**
     * RTS units are mostly hostile mobs (workers extend Vindicator): in Peaceful, vanilla discards them on their first
     * tick. Skirmish worlds are created Peaceful and the only switch was client-side (OrthoviewClientEvents, on
     * entering the RTS camera), which a lobby match seated during map rising never reached: every side's starting
     * units were DISCARDED 70 ms after "match started" (lovish's multiplayer playtest, Oct 18). Fixed on the server,
     * before any side's units spawn.
     */
    public static void leavePeaceful(ServerLevel level) {
        if (level == null || level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL)
            return;
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.EASY, true);
        ReignOfNether.LOGGER.warn("[Player] world was Peaceful at an RTS start: set to Easy so units are not discarded");
    }

    /**
     * The game mode a seated commander needs: the RTS camera works only in creative (OrthoviewClientEvents). Skirmish
     * worlds are created creative, but a dedicated server keeps each player's saved mode - lovish joined his own server
     * in survival, sat down in a lobby match and got no RTS view (Oct 21). Survival / adventure become creative;
     * creative and spectator are left alone.
     */
    public static net.minecraft.world.level.GameType commanderGameMode(net.minecraft.world.level.GameType current) {
        return current == net.minecraft.world.level.GameType.SURVIVAL || current == net.minecraft.world.level.GameType.ADVENTURE
                ? net.minecraft.world.level.GameType.CREATIVE : current;
    }

    /** Puts a seated commander in {@link #commanderGameMode}; returns whether it changed anything. */
    public static boolean ensureCommanderGameMode(net.minecraft.server.level.ServerPlayer sp) {
        net.minecraft.world.level.GameType now = sp.gameMode.getGameModeForPlayer();
        net.minecraft.world.level.GameType want = commanderGameMode(now);
        if (want == now)
            return false;
        sp.setGameMode(want);
        ReignOfNether.LOGGER.info("[Player] {} was in {} at a lobby match start: set to {} for the RTS view",
                sp.getName().getString(), now, want);
        return true;
    }

    public static void startRTS(int playerId, Vec3 pos, Faction faction, int startPosColorId) {
        if (faction == Factions.RANDOM)   // any live faction, so each new one joins the pool when it goes live
            faction = Factions.randomLive(random);
        // a preview faction has no capitol, worker or units yet: starting one would spawn a player with nothing
        if (faction.preview) {
            ReignOfNether.LOGGER.warn("[Player] startRTS refused: {} is a preview faction", faction.key);
            return;
        }
        ReignOfNether.LOGGER.info("[Player] startRTS: playerId={}, pos=[{},{},{}], faction={}, startPosColorId={}", playerId, pos.x, pos.y, pos.z, faction, startPosColorId);
        boolean readiedStart = startPosColorId != 0;

        ServerPlayer serverPlayer = null;
        for (ServerPlayer player : players)
            if (player.getId() == playerId)
                serverPlayer = player;

        if (serverPlayer == null) {
            return;
        }
        // NONE is the sandbox "faction" (a sandbox player may act for every owner) and NEUTRAL has no army: only the
        // host or an op starts as either, whatever route the start came by (design/multiplayer.md)
        if (!PacketAuth.mayPlayAs(serverPlayer, faction)) {
            ReignOfNether.LOGGER.warn("[Player] startRTS refused: {} may not start as {}", serverPlayer.getName().getString(), faction.key);
            return;
        }
        leavePeaceful(serverPlayer.serverLevel());
        if (rtsLocked) {
            serverPlayer.sendSystemMessage(Component.literal(""));
            serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.locked"));
            serverPlayer.sendSystemMessage(Component.literal(""));
            return;
        }
        // a lobby match is under way and this player wasn't seated in it: they watch (design/multiplayer.md)
        if (MultiplayerLobby.refusesLateStart(serverPlayer.getName().getString())) {
            serverPlayer.sendSystemMessage(Component.literal("A multiplayer match is in progress - you are spectating it."));
            return;
        }
        if (isRTSPlayer(serverPlayer.getId())) {
            serverPlayer.sendSystemMessage(Component.literal(""));
            serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.already_started"));
            serverPlayer.sendSystemMessage(Component.literal(""));
            return;
        }
        if (serverPlayer.level().getWorldBorder().getDistanceToBorder(pos.x, pos.z) < 1 && faction != Factions.NONE) {
            serverPlayer.sendSystemMessage(Component.literal(""));
            serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.outside_border"));
            serverPlayer.sendSystemMessage(Component.literal(""));
            return;
        }
        boolean isDogPerson = random.nextBoolean();

        EntityType<?> workerEntityType = ForgeRegistries.ENTITY_TYPES.getValue(faction.workerEntityType);
	        EntityType<?> scoutEntityType = ForgeRegistries.ENTITY_TYPES.getValue(faction.scoutEntityType);
			if (faction.equals(Factions.VILLAGERS) && !isDogPerson) scoutEntityType = EntityRegistrar.SCOUT_CAT_UNIT.get();
        // first RTS join into a fresh game: snapshot the playable area for late joiners
        if (rtsPlayers.isEmpty() && !FogChunkSnapshot.hasAny() && WorldBorderServerEvents.isRtsOptimisedMap(serverLevel)) {
            FogChunkSnapshot.captureFogChunks((ServerLevel) serverPlayer.level());
        }
        if (rtsPlayers.isEmpty()) {
            FogOfWarServerEvents.captureNeutralFogUnits();
        }
        RandomItemDropRule randomItemDropRule = RandomItemDropRule.fromValue(
                serverPlayer.level().getGameRules().getRule(GameRuleRegistrar.RANDOM_ITEM_DROPS).get()
        );
        Long itemDropSeed = switch (randomItemDropRule) {
            case DISABLED -> -1L;
            case ENABLED_NON_STRICT -> random.nextLong();
            case ENABLED_STRICT -> ItemServerEvents.RANDOM_UNIT_ITEM_DROPS_SEED;
        };
        // into play (MatchRoster.join also drops the fog's who-plays cache and starts a classic match in an idle world)
        MatchRoster.join(RTSPlayer.getNewPlayer(
                serverPlayer.getName().getString(),
                faction,
                serverPlayer.getId(),
                startPosColorId,
                isDogPerson,
                itemDropSeed
        ));
        // every start forms the battlefield here, except the readied countdown which already formed its own
        if (!com.solegendary.reignofnether.startpos.StartPosServerEvents.isStartingGame()
                && serverPlayer.level() instanceof ServerLevel sl)
            com.solegendary.reignofnether.startpos.BattlefieldSetup.onStart(sl,
                BlockPos.containing(pos.x, pos.y, pos.z));
        String playerName = serverPlayer.getName().getString();
        ResourcesServerEvents.assignResources(playerName);
        if (serverLevel != null)
            startedAtTick.put(playerName, serverLevel.getGameTime());
        PlayerClientboundPacket.addRTSPlayer(playerName, faction, (long) serverPlayer.getId(), startPosColorId, isDogPerson);

        ServerLevel level = (ServerLevel) serverPlayer.level();
        ArrayList<Entity> startingWorkers = new ArrayList<>();
        Entity startingScout = null;

        List<BlockPos> nonReadiedWorkerBps = List.of(
                new BlockPos((int) pos.x,0,(int) pos.z),
                new BlockPos((int) pos.x+1,0,(int) pos.z),
                new BlockPos((int) pos.x,0,(int) pos.z+1),
                new BlockPos((int) pos.x-1,0,(int) pos.z),
                new BlockPos((int) pos.x,0,(int) pos.z-1)
        );
        for (BlockPos bp0 : nonReadiedWorkerBps) {
            Entity entity = workerEntityType != null ? workerEntityType.create(level) : null;
            if (entity instanceof Unit unit) {
                BlockPos bp = MiscUtil.getHighestNonAirBlock(level, bp0)
                        .above()
                        .above();
                unit.setOwnerName(playerName);
                entity.moveTo(bp, 0, 0);
                if (startingWorkers.isEmpty())
                    CommanderServerEvents.makeCommander(entity);   // BAR: you start with your commander
                if (!readiedStart)
                    level.addFreshEntity(entity);
                startingWorkers.add(entity);
            }
        }
        boolean isBat = scoutEntityType == EntityRegistrar.BAT_UNIT.get();
        if (FogOfWarServerEvents.isEnabled()) {
            BlockPos nonReadiedScoutBp = new BlockPos((int) pos.x - 1, 0,(int) pos.z - 1);
            Entity scoutEntity = scoutEntityType != null ? scoutEntityType.create(level) : null;
            if (scoutEntity instanceof Unit) {
                BlockPos bp = MiscUtil.getHighestNonAirBlock(level, nonReadiedScoutBp)
                        .above()
                        .above();
                ((Unit) scoutEntity).setOwnerName(playerName);
                scoutEntity.moveTo(bp, isBat ? 2 : 0, 0);
                if (!readiedStart)
                    level.addFreshEntity(scoutEntity);
                startingScout = scoutEntity;
            }
        }

        if (faction != Factions.NONE) {
            if (SurvivalServerEvents.isEnabled()) {
                level.setDayTime(TimeUtils.DAWN + getWaveSurvivalTimeModifier(SurvivalServerEvents.getDifficulty()));
                for (RTSPlayer rtsPlayer : rtsPlayers)
                    if (!rtsPlayer.name.equals(playerName))
                        AlliancesServerEvents.addAlliance(rtsPlayer.name, playerName);
            } else {
                level.setDayTime(MONSTER_START_TIME_OF_DAY);
            }
            ResearchServerEvents.removeAllCheatsFor(playerName);
        } else {
            enableAllCheats(playerName);
        }
        ResourcesServerEvents.resetResources(playerName, readiedStart);

        if (readiedStart) {
            Building building = ReignOfNetherRegistries.BUILDING.get(faction.capitolBuilding);;
            ArrayList<BuildingBlock> blocks;
            if (building != null) {
                blocks = building.getRelativeBlockData(level);
                BlockPos bp = getBuildingOriginPos(new BlockPos((int) pos.x, (int) pos.y, (int) pos.z), blocks);
                for (int i = 0; i < startingWorkers.size(); i++) {
                    startingWorkers.get(i).moveTo(bp.offset(-1, 1, i), 0, 0);
                    level.addFreshEntity(startingWorkers.get(i));
                }
                if (startingScout != null) {
                    startingScout.moveTo(bp.offset(0,  isBat ? 3 : 1, 2), 0, 0);
                    level.addFreshEntity(startingScout);
                }
                var workerIds = new int[startingWorkers.size()];
                for (int i = 0; i < startingWorkers.size(); i++) {
                    workerIds[i] = startingWorkers.get(i).getId();
                }
                BuildingServerEvents.placeBuilding(building, bp, Rotation.NONE, playerName, workerIds, false, false, true, true);
                PlayerClientboundPacket.teleport(playerName, BlockPos.containing(pos));
            }
            for (RTSPlayer rtsPlayer : rtsPlayers) {
                String playerName1 = rtsPlayer.name;
                String playerName2 = serverPlayer.getName().getString();
                if (!playerName1.equals(playerName2) && rtsPlayer.startPosColorId == startPosColorId) {
                    AlliancesServerEvents.addAlliance(playerName1, playerName2);
                }
            }
        }

        boolean coopMode = serverLevel.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get();

        if (!TutorialServerEvents.isEnabled() && !readiedStart) {
            serverPlayer.sendSystemMessage(Component.literal(""));
            if (faction.equals(Factions.NONE))
                sendMessageToAllPlayers("server.reignofnether.started_sandbox", true, playerName);
            else if (coopMode)
                sendMessageToAllPlayers("server.reignofnether.started_ally", true, playerName);
            else
                sendMessageToAllPlayers("server.reignofnether.started", true, playerName);
            // the head count goes to the log: in chat it was drawn large across the RTS view at every start
            // (rover run 2, finding 14); the player panel shows who is in play
            ReignOfNether.LOGGER.info("[Player] {} RTS player(s) now in play", rtsPlayers.size());
        }
        if (coopMode)
            AlliancesServerEvents.applyCoopAlliances();

        PlayerClientboundPacket.syncRtsGameTime(MatchState.live().clock());
        saveRTSPlayers();
    }

    public static BlockPos getBuildingOriginPos(BlockPos bp, ArrayList<BuildingBlock> blocks) {
        Vec3i buildingDimensions = BuildingUtils.getBuildingSize(blocks);
        int xRadius = buildingDimensions.getX() / 2;
        int zRadius = buildingDimensions.getZ() / 2;
        return bp.offset(-xRadius, 0 , -zRadius);
    }

    public static void startRTSBot(String name, Vec3 pos, Faction faction) {
        startRTSBot(name, pos, faction, 0);
    }

    /** Map colour ids already taken by RTS players (humans and bots), so a new side can pick a free one. */
    public static java.util.Set<Integer> takenColorIds() {
        return MatchRoster.takenColorIds();
    }

    /**
     * @param colorMapId the bot's team colour (a PlayerPalette map colour id); 0 picks the first colour nobody
     *                   else is using. Sides sharing a colour are allies, so a clash with a human is refused.
     */
    public static void startRTSBot(String name, Vec3 pos, Faction faction, int colorMapId) {
        if (players.isEmpty())
            return;
        startRTSBot((ServerLevel) players.get(0).level(), name, pos, faction, colorMapId);
    }

    /** As above in a given level, with no human online required (headless bot-vs-bot game tests). */
    public static void startRTSBot(ServerLevel level, String name, Vec3 pos, Faction faction, int colorMapId) {
        // same rule as startRTS: a preview faction has no worker type (getValue(null) below) and no bot kit
        if (faction == null || faction.preview) {
            ReignOfNether.LOGGER.warn("[Player] startRTSBot refused: {} is not a startable faction", faction == null ? null : faction.key);
            return;
        }
        leavePeaceful(level);
        // the bot starts far from every player: load and hold its chunks first, or its workers spawn into
        // chunks that unload moments later and the bot is 'defeated' before it has built anything
        UnitChunkLoader.holdArea(level, BlockPos.containing(pos.x, pos.y, pos.z));
        RTSPlayer bot = RTSPlayer.getNewBot(name, faction);
        java.util.Set<Integer> taken = takenColorIds();
        if (colorMapId == 0 || taken.contains(colorMapId)
                || !com.solegendary.reignofnether.player.PlayerPalette.isPaletteColor(colorMapId))
            colorMapId = com.solegendary.reignofnether.player.PlayerPalette.firstFree(taken);
        bot.startPosColorId = colorMapId;
        startedAtTick.put(bot.name, level.getGameTime());
        MatchRoster.join(bot);
        PlayerClientboundPacket.addRTSPlayer(bot.name, bot.faction, (long) bot.id, bot.startPosColorId, bot.isDogPerson);
        ResourcesServerEvents.assignResources(bot.name);
        com.solegendary.reignofnether.startpos.BattlefieldSetup.onStart(level,
            BlockPos.containing(pos.x, pos.y, pos.z));

        spawnBotStartUnits(level, bot.name, faction, pos, true);
        if (faction.equals(Factions.MONSTERS)) {
            level.setDayTime(MONSTER_START_TIME_OF_DAY);
        }
        ResourcesServerEvents.resetResources(bot.name, false);

        // "Bot1 (bot) has been added to the game!" and the head count were two bold chat lines per bot, drawn large
        // across the RTS view at every start (rover run 2, finding 14); the lineup is on the loading screen and in
        // the player panel, so the log keeps the record
        ReignOfNether.LOGGER.info("[Player] bot {} ({}) added; {} RTS player(s) now in play", bot.name,
            faction.getName(), rtsPlayers.size());
        saveRTSPlayers();
    }

    /**
     * A bot's three starting workers on the ground at pos, the first made its commander when withCommander. Used by
     * startRTSBot and by BotPlayer when a bot finds itself with no workers and no capitol early on (the start was
     * lost: lovish's Oct 12 Watch Battle had three of four bots sit at +0 income with untouched pools). Returns how
     * many joined the level.
     */
    /**
     * The top block a start unit is dropped on: its own column (x, z) when that is solid, dry ground, else the nearest
     * such column within 4 blocks (rings outward), else its own column as before. getHighestNonAirBlock stops at a
     * fluid, so a lava pool beside a seat took a start worker before the bot's first think (build 40632885850: "the
     * Verdant bot should start with 3 Seedshapers, got 2", "court seedshaper_unit died t10 lava"). A column whose eight
     * neighbours are dry too is preferred (the three units shove each other apart as they land), then any dry one.
     */
    static BlockPos dryStartColumn(ServerLevel level, int x, int z) {
        BlockPos own = MiscUtil.getHighestNonAirBlock(level, new BlockPos(x, 0, z));
        for (int pass = 0; pass < 2; pass++)
            for (int r = 0; r <= 4; r++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                            continue;
                        BlockPos top = r == 0 ? own : MiscUtil.getHighestNonAirBlock(level, new BlockPos(x + dx, 0, z + dz));
                        if (dryTop(level, top) && (pass == 1 || dryAround(level, top)))
                            return top;
                    }
        return own;
    }

    private static boolean dryAround(ServerLevel level, BlockPos top) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0)
                        && !dryTop(level, MiscUtil.getHighestNonAirBlock(level, new BlockPos(top.getX() + dx, 0, top.getZ() + dz))))
                    return false;
        return true;
    }

    /** A top block a unit can stand on: no fluid in it or the two blocks above, and not a fire or magma block. */
    private static boolean dryTop(ServerLevel level, BlockPos top) {
        var state = level.getBlockState(top);
        if (!state.getFluidState().isEmpty() || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || state.is(net.minecraft.tags.BlockTags.FIRE))
            return false;
        for (int up = 1; up <= 2; up++) {
            var above = level.getBlockState(top.above(up));
            if (!above.getFluidState().isEmpty() || above.is(net.minecraft.tags.BlockTags.FIRE))
                return false;
        }
        return true;
    }

    public static int spawnBotStartUnits(ServerLevel level, String owner, Faction faction, Vec3 pos, boolean withCommander) {
        EntityType<?> entityType = faction == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(faction.workerEntityType);
        UnitChunkLoader.holdArea(level, BlockPos.containing(pos.x, pos.y, pos.z));
        int added = 0;
        boolean first = withCommander;
        for (int i = -1; i <= 1; i++) {
            Entity entity = entityType != null ? entityType.create(level) : null;
            if (entity instanceof Unit unit) {
                BlockPos bp = dryStartColumn(level, (int) (pos.x + i), (int) pos.z).above().above();
                unit.setOwnerName(owner);
                entity.moveTo(bp, 0, 0);
                if (first) {
                    CommanderServerEvents.makeCommander(entity);
                    first = false;
                }
                if (level.addFreshEntity(entity))
                    added++;
            }
        }
        return added;
    }

    public static void startRTSScenario(int playerId, int roleIndex) {
        ServerPlayer serverPlayer = null;
        for (ServerPlayer player : players)
            if (player.getId() == playerId)
                serverPlayer = player;

        ScenarioRole role = ScenarioUtils.getScenarioRole(false, roleIndex);

        if (serverPlayer == null || role == null) {
            return;
        }
        if (role.isNpc) {
            serverPlayer.sendSystemMessage(Component.literal(""));
            serverPlayer.sendSystemMessage(Component.translatable("sandbox.reignofnether.scenario.npc_role_error"));
            serverPlayer.sendSystemMessage(Component.literal(""));
        }
        if (rtsLocked) {
            serverPlayer.sendSystemMessage(Component.literal(""));
            serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.locked"));
            serverPlayer.sendSystemMessage(Component.literal(""));
            return;
        }
        // a lobby match is under way and this player wasn't seated in it: they watch (design/multiplayer.md)
        if (MultiplayerLobby.refusesLateStart(serverPlayer.getName().getString())) {
            serverPlayer.sendSystemMessage(Component.literal("A multiplayer match is in progress - you are spectating it."));
            return;
        }
        if (isRTSPlayer(serverPlayer.getId())) {
            serverPlayer.sendSystemMessage(Component.literal(""));
            serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.already_started_scenario"));
            serverPlayer.sendSystemMessage(Component.literal(""));
            return;
        }
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            if (rtsPlayer.scenarioRoleIndex == roleIndex) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.scenario_role_taken"));
                serverPlayer.sendSystemMessage(Component.literal(""));
                return;
            }
        }
        RTSPlayer rtsPlayer = RTSPlayer.getNewScenarioPlayer(
                serverPlayer.getName().getString(),
                role.faction,
                serverPlayer.getId(),
                roleIndex
        );
        MatchRoster.join(rtsPlayer);
        String playerName = serverPlayer.getName().getString();
        ResourcesServerEvents.assignScenarioResources(rtsPlayer);
        PlayerClientboundPacket.addRTSPlayer(playerName, role.faction, (long) serverPlayer.getId(), 0, true);

        for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
            if (building.scenarioRoleIndex == roleIndex) {
                building.ownerName = playerName;
                BuildingClientboundPacket.syncBuilding(building.originPos, building.getBlocksPlaced(),
                        building.partialBlocksDestroyed, playerName, building.scenarioRoleIndex);
            }
        }
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le instanceof Unit unit && unit.getScenarioRoleIndex() == roleIndex) {
                unit.setOwnerName(playerName);
                UnitSyncClientboundPacket.sendSyncOwnerNamePacket(unit);
            }
        }

        sendMessageToAllPlayers("server.reignofnether.started_scenario", true, playerName, role.name, role.faction.getName());
        PlayerClientboundPacket.syncRtsGameTime(MatchState.live().clock());

        // add NPC rtsPlayers
        int id = -1;
        for (ScenarioRole scenarioRole : ScenarioServerEvents.scenarioRoles) {
            int numUnits = ScenarioServerEvents.getNumScenarioUnits(scenarioRole);
            int numBuilds = ScenarioServerEvents.getNumScenarioBuildings(scenarioRole);
            if (!isRTSPlayer(scenarioRole.name) && (numUnits > 0 || numBuilds > 0) && scenarioRole.isNpc) {
                RTSPlayer npcRtsPlayer = RTSPlayer.getNewScenarioPlayer(
                        scenarioRole.name,
                        scenarioRole.faction,
                        id,
                        scenarioRole.index
                );
                MatchRoster.join(npcRtsPlayer);
                ResourcesServerEvents.assignScenarioResources(npcRtsPlayer);
                PlayerClientboundPacket.addScenarioNPCRTSPlayer(scenarioRole.name, scenarioRole.faction, (long) id, scenarioRole.index);
                id -= 1;
            }
        }
        if (serverLevel.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).get())
            AlliancesServerEvents.applyScenarioAlliances();
        if (serverLevel.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get())
            AlliancesServerEvents.applyCoopAlliances();
        saveRTSPlayers();
    }

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent evt) {
        if (evt.getPlayer().hasPermissions(4)) {
            String msg = evt.getMessage().getString();
            String[] words = msg.split(" ");
            String playerName = evt.getPlayer().getName().getString();

            if (words.length == 1 && words[0].equalsIgnoreCase("thebeastofcaerbannog")) {
                UnitServerEvents.spawnMob(EntityRegistrar.getEntityType("Killer Rabbit"), serverLevel, evt.getPlayer().getOnPos(), playerName);
                sendMessageToAllPlayers("server.reignofnether.used_cheat",false, playerName, words[0]);
            }

            if (words.length == 1 && words[0].equalsIgnoreCase("elitetaurenchieftain")) {
                for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
                    if (entity instanceof HeroUnit heroUnit && ((Unit) heroUnit).getOwnerName().equals(playerName)) {
                        heroUnit.addExperience(10000);
                        heroUnit.setSkillPoints(10);
                        heroUnit.setMana(heroUnit.getMaxMana());
                        HeroClientboundPacket.setExperience(entity.getId(), heroUnit.getExperience());
                        HeroClientboundPacket.setSkillPoints(entity.getId(), 10);
                    }
                }
                sendMessageToAllPlayers("server.reignofnether.used_cheat",false, playerName, words[0]);
            }

            if (words.length == 2) {
                try {
                    if (words[0].equalsIgnoreCase("greedisgood")) {
                        int amount = Integer.parseInt(words[1]);
                        if (amount > 0) {
                            ResourcesServerEvents.addSubtractResources(new Resources(playerName,
                                    amount,
                                    amount,
                                    amount,
                                    amount
                            ));
                            evt.setCanceled(true);
                            sendMessageToAllPlayers("server.reignofnether.used_cheat_amount",
                                    false,
                                    playerName,
                                    words[0],
                                    Integer.toString(amount)
                            );
                        }
                    }
                } catch (NumberFormatException err) {
                    ReignOfNether.LOGGER.error(err);
                }
            }

            if (words.length == 3) {
                try {
                    if (words[0].equalsIgnoreCase("greedisgood")) {
                        int amount = Integer.parseInt(words[2]);
                        if (amount > 0 && List.of("food", "wood", "ore", "emerald").contains(words[1].toLowerCase())) {
                            switch (words[1].toLowerCase()) {
                                case "food" -> ResourcesServerEvents.addSubtractResources(new Resources(playerName, amount, 0, 0,0));
                                case "wood" -> ResourcesServerEvents.addSubtractResources(new Resources(playerName, 0, amount, 0,0));
                                case "ore" -> ResourcesServerEvents.addSubtractResources(new Resources(playerName, 0, 0, amount,0));
                                case "emerald" -> ResourcesServerEvents.addSubtractResources(new Resources(playerName, 0, 0,0, amount));
                            }
                            evt.setCanceled(true);
                            sendMessageToAllPlayers("server.reignofnether.used_cheat_amount",
                                    false,
                                    playerName,
                                    words[0] + " " + words[1],
                                    Integer.toString(amount)
                            );
                        }
                    }
                } catch (NumberFormatException err) {
                    ReignOfNether.LOGGER.error(err);
                }
            }

            for (String cheatName : singleWordCheats) {
                if (words.length == 1 && words[0].equalsIgnoreCase(cheatName)) {
                    if (ResearchServerEvents.playerHasCheat(playerName, cheatName)) {
                        ResearchServerEvents.removeCheat(playerName, cheatName);
                        ResearchClientboundPacket.removeCheat(playerName, cheatName);
                        evt.setCanceled(true);
                        sendMessageToAllPlayers("server.reignofnether.disabled_cheat", false, playerName, cheatName);
                    } else {
                        ResearchServerEvents.addCheat(playerName, cheatName);
                        ResearchClientboundPacket.addCheat(playerName, cheatName);
                        evt.setCanceled(true);
                        sendMessageToAllPlayers("server.reignofnether.enabled_cheat", false, playerName, cheatName);
                    }
                }
            }

            // apply all cheats - NOTE can cause concurrentModificationException clientside
            if (words.length == 1 && words[0].equalsIgnoreCase("allcheats") && (
                playerName.equalsIgnoreCase("solegendary") ||
                playerName.equalsIgnoreCase("altsolegendary"))
            ) {
                ResourcesServerEvents.addSubtractResources(new Resources(playerName, 99999, 99999, 99999));
                UnitServerEvents.maxPopulation = 99999;
                enableAllCheats(playerName);
                evt.setCanceled(true);
                sendMessageToAllPlayers("server.reignofnether.all_cheats", false, playerName);
            }
        }
    }

    public static void enableAllCheats(String playerName) {
        for (String cheatName : singleWordCheats) {
            ResearchServerEvents.addCheat(playerName, cheatName);
            ResearchClientboundPacket.addCheat(playerName, cheatName);
        }
    }

    public static void enableOrthoview(int id) {
        ServerPlayer player = getPlayerById(id);
        if (player != null)
            player.removeAllEffects();

        orthoviewPlayers.removeIf(p -> p.getId() == id);
        orthoviewPlayers.add(player);
    }

    public static void disableOrthoview(int id) {
        ServerPlayer leaving = getPlayerById(id);
        orthoviewPlayers.removeIf(p -> p.getId() == id);
        if (leaving != null) {
            // drop fog bookkeeping so normal view-distance behavior resumes
            com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.onPlayerExitOrthoview(leaving);
        }
    }

    private static ServerPlayer getPlayerById(int playerId) {
        for (ServerPlayer player : players) {
            if (playerId == player.getId()) {
                return player;
            }
        }
        return null;
    }

    public static void openTopdownGui(int playerId) {
        ServerPlayer serverPlayer = getPlayerById(playerId);

        if (serverPlayer != null) {
            // Open GUI server-side
            MenuConstructor provider = TopdownGuiContainer.getServerContainerProvider();
            MenuProvider namedProvider = new SimpleMenuProvider(provider, TopdownGuiContainer.TITLE);
            NetworkHooks.openScreen(serverPlayer, namedProvider);

            // Save original game mode only if it's not already saved for this session
            String playerName = serverPlayer.getName().getString();
            playerDefaultGameModes.putIfAbsent(playerName, serverPlayer.gameMode.getGameModeForPlayer());

            // Mark that this player has the GUI open
            playerGuiOpenStatus.put(playerName, true);

            // Set game mode to SPECTATOR for GUI interaction
            serverPlayer.setGameMode(GameType.SPECTATOR);
        } else {
            ReignOfNether.LOGGER.warn("serverPlayer is null, cannot open topdown GUI");
        }
    }

    public static void closeTopdownGui(int playerId) {
        ServerPlayer serverPlayer = getPlayerById(playerId);

        if (serverPlayer != null) {
            String playerName = serverPlayer.getName().getString();

            // Ensure player had GUI open before attempting to close
            if (Boolean.TRUE.equals(playerGuiOpenStatus.get(playerName))) {
                // Restore the player’s original game mode if saved
                GameType originalGameType = playerDefaultGameModes.remove(playerName);

                if (originalGameType != null) {
                    // a watcher stays a spectator, hidden from the players (Spectators.modeAfterLeavingRtsView)
                    serverPlayer.setGameMode(com.solegendary.reignofnether.match.Spectators.modeAfterLeavingRtsView(playerName, originalGameType));
                } else {
                    ReignOfNether.LOGGER.warn("No original game mode found for player {}", playerName);
                }

                if (SandboxServer.isSandboxPlayer(playerName)) {
                    serverPlayer.setGameMode(GameType.CREATIVE);
                }

                // Mark that the GUI is now closed
                playerGuiOpenStatus.remove(playerName);
            } else {
                ReignOfNether.LOGGER.warn("Attempted to close GUI for player {} who didn't have it open", playerName);
            }
        } else {
            ReignOfNether.LOGGER.warn("serverPlayer is null, cannot close topdown GUI");
        }
    }

    public static void movePlayer(int playerId, double x, double y, double z) {
        ServerPlayer serverPlayer = getPlayerById(playerId);
        if (serverPlayer != null && (serverPlayer.isCreative() || serverPlayer.isSpectator())) {
            // the position is the client's word: NaN or infinity would break the player's position for good
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                ReignOfNether.LOGGER.warn("[Player] {} sent a non-finite teleport [{}, {}, {}]: ignored", serverPlayer.getName().getString(), x, y, z);
                return;
            }
            // every sender, not only the ortho-view players, stays inside the world border and the build height range
            net.minecraft.world.level.border.WorldBorder border = serverPlayer.level().getWorldBorder();
            double margin = 1.0D;
            double minX = border.getMinX() + margin;
            double maxX = border.getMaxX() - margin;
            double minZ = border.getMinZ() + margin;
            double maxZ = border.getMaxZ() - margin;
            if (maxX > minX && maxZ > minZ) {
                x = net.minecraft.util.Mth.clamp(x, minX, maxX);
                z = net.minecraft.util.Mth.clamp(z, minZ, maxZ);
            }
            y = net.minecraft.util.Mth.clamp(y, serverPlayer.level().getMinBuildHeight(), serverPlayer.level().getMaxBuildHeight() + MAX_CAMERA_ABOVE_BUILD_HEIGHT);
            serverPlayer.teleportTo(x, y, z);
        }
    }

    /** How far above the build limit a camera body may be moved (the RTS camera hovers well above the terrain). */
    private static final double MAX_CAMERA_ABOVE_BUILD_HEIGHT = 64.0D;

    public static void sendMessageToAllPlayers(String msg) {
        sendMessageToAllPlayers(msg, false);
    }

    public static void sendMessageToAllPlayers(String msg, int color, boolean bold, Object... formatArgs) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.literal(""));
            if (bold) {
                player.sendSystemMessage(Component.translatable(msg, formatArgs)
                        .withStyle(Style.EMPTY.withBold(true).withColor(color)));
            } else {
                player.sendSystemMessage(Component.translatable(msg, formatArgs)
                        .withStyle(Style.EMPTY.withBold(false).withColor(color)));
            }
            player.sendSystemMessage(Component.literal(""));
        }
    }

    public static void sendMessageToAllPlayers(String msg, boolean bold,  Object... formatArgs) {
        sendMessageToAllPlayers(msg, 0xFFFFFF, bold, formatArgs);
    }

    public static void sendMessageToAllPlayersNoNewlines(String msg) {
        sendMessageToAllPlayersNoNewlines(msg, false);
    }

    public static void sendMessageToAllPlayersNoNewlines(String msg, boolean bold, Object... formatArgs) {
        for (ServerPlayer player : players) {
            if (bold) {
                player.sendSystemMessage(Component.translatable(msg, formatArgs).withStyle(Style.EMPTY.withBold(true)));
            } else {
                player.sendSystemMessage(Component.translatable(msg, formatArgs));
            }
        }
    }

    public static void sendMessageToPlayerNoNewLines(String playerName, String msg) {
        sendMessageToPlayerNoNewLines(playerName, msg, false);
    }

    public static void sendMessageToPlayer(String playerName, String msg) {
        sendMessageToPlayer(playerName, msg, false);
    }

    public static void sendMessageToPlayerNoNewLines(String playerName, String msg, boolean bold, Object... formatArgs) {
        for (ServerPlayer player : players) {
            if (player.getName().getString().equals(playerName)) {
                if (bold) {
                    player.sendSystemMessage(Component.translatable(msg, formatArgs).withStyle(Style.EMPTY.withBold(true)));
                } else {
                    player.sendSystemMessage(Component.translatable(msg, formatArgs));
                }
                return;
            }
        }
    }

    public static void sendMessageToPlayer(String playerName, String msg, boolean bold, Object... formatArgs) {
        for (ServerPlayer player : players) {
            if (player.getName().getString().equals(playerName)) {
                player.sendSystemMessage(Component.literal(""));
                if (bold) {
                    player.sendSystemMessage(Component.translatable(msg, formatArgs).withStyle(Style.EMPTY.withBold(true)));
                } else {
                    player.sendSystemMessage(Component.translatable(msg, formatArgs));
                }
                player.sendSystemMessage(Component.literal(""));
                return;
            }
        }
    }

    // defeat a player, giving them a defeat screen, removing all their unit/building control and removing them from
    // rtsPlayers - match.MatchOutcome since rewrite 5
    public static void defeat(int playerId, String reason) {
        for (ServerPlayer player : players) {
            if (player.getId() == playerId) {
                defeat(player.getName().getString(), reason);
                return;
            }
        }
    }

    /** Beats a side (MatchOutcome.defeat); a bare translation key as the reason is translated on each client. */
    public static void defeat(String playerName, String reason) {
        MatchOutcome.defeat(playerName, MatchOutcome.reasonText(reason));
    }

    /**
     * The winners when only {@code remaining} are left: everyone of them if they are all one connected alliance
     * (a lone survivor included), nobody while two sides still stand or when nobody is left (MatchOutcome).
     */
    public static Set<String> winnersAmong(Set<String> remaining) {
        return MatchOutcome.winnersAmong(remaining);
    }

    /**
     * A finished player (a winner whose match is over) steps out of the match to watch what is left of it, keeping
     * everything they own; refused for a side still in the fight (match.Spectators).
     */
    public static void spectate(int playerId) {
        Spectators.spectate(playerId);
    }

    public static void beaconVictory(String playerName) {
        MatchOutcome.beaconVictory(playerName);
    }

    // End-of-match "most damage dealt" award: credit damage landed by a unit (or its projectile - getEntity() is the
    // shooter) to the unit's owner. LOWEST priority so armour/friendly-fire/zeal adjustments from the other
    // LivingDamageEvent handlers are already applied, and cancelled hits are skipped. Overkill is clamped to the
    // victim's remaining health; hits on your own units don't count. Building (block) damage is not included.
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void trackDamageDealt(net.minecraftforge.event.entity.living.LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide() || evt.getAmount() <= 0)
            return;
        if (!(evt.getSource().getEntity() instanceof Unit attacker))
            return;
        String owner = attacker.getOwnerName();
        if (owner == null || owner.isBlank())
            return;
        creditDamage(owner, evt.getEntity() instanceof Unit victim ? victim.getOwnerName() : null,
                Math.min(evt.getAmount(), Math.max(0, evt.getEntity().getHealth())));
    }

    /** Adds {@code amount} to {@code attackerOwner}'s match damage, unless the victim is their own. */
    public static void creditDamage(String attackerOwner, @Nullable String victimOwner, float amount) {
        if (attackerOwner == null || attackerOwner.isBlank() || amount <= 0 || attackerOwner.equals(victimOwner))
            return;
        RTSPlayer rtsPlayer = getRTSPlayer(attackerOwner);
        if (rtsPlayer != null)
            rtsPlayer.damageDealt += amount;
    }

    // Sends the final per-player scoreboard to all clients so they can show the
    // end-of-match stats popup (MatchEndScreen). Winners are those in winnerNames;
    // everyone else (already moved to postGameRtsPlayers on defeat) is a loser. (MatchOutcome)
    public static void broadcastMatchStats(Set<String> winnerNames) {
        MatchOutcome.broadcastMatchStats(winnerNames);
    }

    public static String getBeaconWinTime(String playerName) {
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            if (rtsPlayer.name.equals(playerName)) {
                return TimeUtils.getTimeStrFromTicks(Beacon.getTicksToWin(serverLevel) - rtsPlayer.beaconOwnerTicks);
            }
        }
        return TimeUtils.getTimeStrFromTicks(Beacon.getTicksToWin(serverLevel));
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent evt) {
        AllyCommand.register(evt.getDispatcher());
        RTSPlayerScoresCommand.register(evt.getDispatcher());

        evt.getDispatcher().register(Commands.literal("rts-lock").then(Commands.literal("enable").executes((command) -> {
            if ((command.getSource() != null &&
                command.getSource().getPlayer() != null &&
                command.getSource().getPlayer().hasPermissions(4)) ||
                (command.getSource() != null &&
                !command.getSource().isPlayer())) {
                setRTSLock(true);
                return 1;
            }
            return 0;
        })));

        evt.getDispatcher().register(Commands.literal("rts-lock").then(Commands.literal("disable").executes((command) -> {
            if ((command.getSource() != null &&
                command.getSource().getPlayer() != null &&
                command.getSource().getPlayer().hasPermissions(4)) ||
                (command.getSource() != null &&
                !command.getSource().isPlayer())) {
                setRTSLock(false);
                return 1;
            }
            return 0;
        })));
    }

    public static int resetRTS(boolean hardReset) {
        return resetRTS(hardReset, false);
    }

    /**
     * {@code retireOverTicks}: a new match on fresh ground (maps.NewMatch) is leaving this arena behind, so its units
     * and buildings are unregistered at once but taken down over the next ticks (maps.ArenaRetirement) instead of
     * killed and blown up in this one - a big end-of-match would otherwise stall the server tick.
     */
    public static int resetRTS(boolean hardReset, boolean retireOverTicks) {
        ReignOfNether.LOGGER.info("[Player] resetRTS: hardReset={} retireOverTicks={}", hardReset, retireOverTicks);
        ResignVoteServerEvents.clear();   // a vote from the match being reset has nothing left to decide
        MultiplayerLobby.onReset();          // the lobby match is over too: nobody is refused a start any more
        lastBeaconTicksSent.clear();
        StartPosServerEvents.cancelStartGameCountdown(true);
        FogChunkSnapshot.clear();

        boolean isSandboxOrScenario = SandboxServer.isAnyoneASandboxPlayer() || serverLevel.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).get();

        MatchRoster.clearLive();
        // the battlefield resets with the match: patches dug out so a rematch stamps a fresh layout
        if (!isSandboxOrScenario)
            com.solegendary.reignofnether.resources.MetalPatches.clearAll(serverLevel);
        com.solegendary.reignofnether.tide.TidepoolServerEvents.clearAll();   // tidepools never outlive a match
        com.solegendary.reignofnether.tide.FogBankServerEvents.clearAll();
        com.solegendary.reignofnether.mana.ManaServerEvents.clearAll();   // nor does Overcharge
        com.solegendary.reignofnether.building.production.UnitProductionPause.clearAll();   // nor a paused unit queue
        com.solegendary.reignofnether.mana.WardServerEvents.clearAll();
        com.solegendary.reignofnether.mana.DecoyServerEvents.clearAll();
        com.solegendary.reignofnether.mana.LanternDomeServerEvents.clearAll();
        com.solegendary.reignofnether.mana.TempestServerEvents.clearAll();
        com.solegendary.reignofnether.mana.FrostServerEvents.clearAll();

        // the arena is being left behind: unregister it all now, take it down over the next ticks (the loops below
        // then find nothing to do)
        if (retireOverTicks && !isSandboxOrScenario)
            com.solegendary.reignofnether.maps.ArenaRetirement.retireAll(serverLevel);

        for (LivingEntity entity : UnitServerEvents.getAllUnits())
            if (hardReset || (entity instanceof Unit unit && !Unit.hasAnchor(unit) && !isSandboxOrScenario))
                entity.kill();

        if (!isSandboxOrScenario)
            UnitServerEvents.getAllUnits().removeIf(u -> (hardReset || (u instanceof Unit unit && !Unit.hasAnchor(unit))));

        if (!isSandboxOrScenario)
            for (LivingEntity entity : UnitServerEvents.getAllUnits())
                if (entity instanceof Unit unit)
                    unit.setOwnerName("");

        for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
            if (building instanceof ProductionPlacement productionBuilding)
                productionBuilding.productionQueue.clear();
            if ((building.getBuilding().shouldDestroyOnReset || hardReset) && !isSandboxOrScenario)
                building.destroy((ServerLevel) building.getLevel());
        }
        if (!isSandboxOrScenario)
            BuildingServerEvents.getBuildings().removeIf(b -> b.getBuilding().shouldDestroyOnReset || hardReset);
        if (!isSandboxOrScenario)
            com.solegendary.reignofnether.building.Blueprints.clear();   // their workers are gone: no order outlives the match

        if (!isSandboxOrScenario)
            for (BuildingPlacement building : BuildingServerEvents.getBuildings())
                building.ownerName = "";

        ResearchServerEvents.removeAllResearch();
        ResearchServerEvents.removeAllCheats();
        PlayerClientboundPacket.resetRTS(hardReset);
        if (!TutorialServerEvents.isEnabled()) {
            if (hardReset)
                sendMessageToAllPlayers("server.reignofnether.match_reset_hard", true);
            else
                sendMessageToAllPlayers("server.reignofnether.match_reset", true);
        }
        ResourcesServerEvents.resourcesList.clear();
        saveAll();

        if (rtsLocked)
            setRTSLock(false);
        AlliancesServerEvents.resetAllAlliances();
        SurvivalServerEvents.reset();
        HeroServerEvents.fallenHeroes.clear();
        UnitServerEvents.saveFallenHeroUnits(serverLevel);

        for (ServerPlayer player : serverLevel.players())
            player.setGameMode(GameType.SPECTATOR);

        playerDefaultGameModes.replaceAll((key, oldValue) -> GameType.SPECTATOR);
        AlliancesServerEvents.playersWithAlliedControl.clear();
        TimeServerEvents.resetBloodMoon();

        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings())
            if (bpl instanceof CustomBuildingPlacement cbpl)
                cbpl.resetAllCommands();

        return 1;
    }

    public static void publishScenarioMap() {
        if (ScenarioServerEvents.getNumScenarioUnits() == 0 && ScenarioServerEvents.getNumScenarioBuildings() == 0) {
            sendMessageToAllPlayers("server.reignofnether.scenario_published_error1");
            return;
        }

        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le instanceof Unit unit) {
                ScenarioRole role = ScenarioUtils.getScenarioRole(false, unit.getScenarioRoleIndex());
                unit.setOwnerName(role != null ? role.name : "");
                UnitSyncClientboundPacket.sendSyncScenarioRoleIndexPacket(unit);
            }
        }
        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings()) {
            ScenarioRole role = ScenarioUtils.getScenarioRole(false, bpl.scenarioRoleIndex);
            bpl.ownerName = role != null ? role.name : "";
            BuildingClientEvents.syncBuilding(bpl, bpl.getBlocksPlaced(), bpl.partialBlocksDestroyed, bpl.ownerName, bpl.scenarioRoleIndex);
        }
        serverLevel.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).set(true, serverLevel.getServer());

        MatchRoster.clearLive();
        MatchState.live().reset();   // a published scenario is a fresh world to start sides in, not a match in play

        for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
            if (building instanceof ProductionPlacement productionBuilding)
                productionBuilding.productionQueue.clear();
        }
        ResearchServerEvents.removeAllResearch();
        ResearchServerEvents.removeAllCheats();
        PlayerClientboundPacket.publishScenarioMap();
        sendMessageToAllPlayers("server.reignofnether.scenario_published", true);
        sendMessageToAllPlayers("server.reignofnether.scenario_published_tooltip1");
        sendMessageToAllPlayers("server.reignofnether.scenario_published_tooltip2");
        ResourcesServerEvents.resourcesList.clear();
        saveAll();

        if (rtsLocked)
            setRTSLock(false);
        if (serverLevel.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).get())
            AlliancesServerEvents.applyScenarioAlliances();
        if (serverLevel.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get())
            AlliancesServerEvents.applyCoopAlliances();
        SurvivalServerEvents.reset();
        HeroServerEvents.fallenHeroes.clear();
        UnitServerEvents.saveFallenHeroUnits(serverLevel);

        for (ServerPlayer player : serverLevel.players())
            player.setGameMode(GameType.SPECTATOR);

        playerDefaultGameModes.replaceAll((key, oldValue) -> GameType.SPECTATOR);
        AlliancesServerEvents.playersWithAlliedControl.clear();
        TimeServerEvents.resetBloodMoon();

        GameruleClientboundPacket.setScenarioMode(true);
        GameModeClientboundPacket.setAndLockAllClientGameModes(GameMode.SCENARIO);
    }

    private static void saveAll() {
        saveRTSPlayers();
        postGameRtsPlayers.clear();
        saveBuildings(serverLevel);
        BuildingServerEvents.saveNetherZones(serverLevel);
        UnitServerEvents.saveGatherTargets(serverLevel);
        ResourcesServerEvents.saveResources(serverLevel);
        ResearchServerEvents.saveResearch();
    }

    public static void setRTSLock(boolean lock) {
        setRTSLock(lock, false);
    }

    public static void setRTSLock(boolean lock, boolean noMsg) {
        rtsLocked = lock;
        // every online player, not only the overworld's: each packet goes to the player it names (network/Audience)
        serverLevel.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (rtsLocked) {
                PlayerClientboundPacket.lockRTS(p.getName().getString());
            } else {
                PlayerClientboundPacket.unlockRTS(p.getName().getString());
            }
        });
        if (!noMsg) {
            if (rtsLocked) {
                sendMessageToAllPlayers("server.reignofnether.match_locked");
            } else {
                sendMessageToAllPlayers("server.reignofnether.match_unlocked");
            }
        }
    }

    public static void setRTSSyncingEnabled(boolean enable) {
        rtsSyncingEnabled = enable;
        if (rtsSyncingEnabled) {
            sendMessageToAllPlayers("server.reignofnether.sync_enabled");
        } else {
            sendMessageToAllPlayers("server.reignofnether.sync_disabled");
        }
    }

    public static void updateMarketRates(String playerName) {
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            if (rtsPlayer.name.equals(playerName)) {
                for (TradeAction tradeAction : rtsPlayer.tradeRates.keySet()) {
                    PlayerClientboundPacket.setMarketRate(tradeAction, rtsPlayer.name, rtsPlayer.tradeRates.get(tradeAction));
                }
                return;
            }
        }
    }

    public static void updateMarketRates(int playerId) {
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            if (rtsPlayer.id == playerId) {
                for (TradeAction tradeAction : rtsPlayer.tradeRates.keySet()) {
                    PlayerClientboundPacket.setMarketRate(tradeAction, rtsPlayer.name, rtsPlayer.tradeRates.get(tradeAction));
                }
                return;
            }
        }
    }
}
