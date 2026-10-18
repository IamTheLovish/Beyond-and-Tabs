package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Skirmish from the title screen (Beyond and Tabs). One button opens the skirmish lobby ({@link SkirmishSetupScreen}:
 * your faction and colour, the opponents, arena size, metal richness, spawn distance). Start Battle makes a FRESH
 * world every time - so an old, finished match can never bleed into a new one - enters the RTS camera, and sends the
 * lobby choices to the server, which lays your capitol, seats your commander and calls in the bots. The long way
 * round (make a world, press the camera key (Backspace, or F12), pick a faction, place your base, /bot add ...) still
 * works and is what multiplayer uses. A lobby with the player on no team (Spectate) starts a bots-only match that the
 * player watches as an observer.
 * <p>
 * From Start Battle to the start boxes the player sees one Beyond and Tabs loading screen ({@link MatchLoadingScreen}),
 * not the vanilla world loader and then a half-built arena: {@link #loading} is set here and the screen swaps itself in
 * for the vanilla loaders while it is set.
 */
public class QuickBattle {

    static final String WORLD_DIR_PREFIX = "beyondandtabs_skirmish_";
    /** Also in PlayerServerEvents (SKIRMISH_WORLD_NAME_PREFIX): the server cannot load this client-only class. */
    static final String WORLD_NAME_PREFIX = "Beyond and Tabs - Skirmish ";

    static SkirmishSetupScreen.Settings pending = null;
    static int phase = 0;   // 0 idle, 1 waiting for world, 2 send the lobby choices, 3 waiting for the rts start
    static int waitTicks = 0;
    /**
     * A launch is loading: from the Start Battle click until the loading veil's cut (or until the lobby, a disconnect
     * or the title screen ends the launch). MatchLoadingScreen reads it to take the vanilla loaders' place.
     */
    public static boolean loading = false;

    @SubscribeEvent
    public static void onTitleScreen(ScreenEvent.Init.Post evt) {
        if (!(evt.getScreen() instanceof TitleScreen title))
            return;
        int w = 200, x = title.width / 2 - w / 2, y = title.height / 4 + 48 - 26;
        evt.addListener(Button.builder(Component.translatable("quickbattle.reignofnether.button"),
                b -> Minecraft.getInstance().setScreen(new SkirmishSetupScreen(title))).bounds(x, y, w, 20).build());
    }

    /** Called by the lobby's Start Battle: make a fresh world and queue the start. */
    static void launch(Screen from, SkirmishSetupScreen.Settings settings) {
        pending = settings;
        phase = 1;
        waitTicks = 0;
        loading = true;   // before createWorld: its first LevelLoadingScreen is already ours
        ReignOfNether.LOGGER.info("[QuickBattle] launch");
        createWorld();
    }

    /**
     * The lobby's "Host Online": the same fresh battlefield, but instead of starting, open it to LAN (the e4mc mod
     * turns that into a public address it prints in chat) and open the multiplayer lobby with this lobby's map
     * settings, for friends to join (MultiplayerLobby, design/multiplayer.md).
     */
    static void launchHost(Screen from, SkirmishSetupScreen.Settings settings) {
        pending = settings;
        phase = HOST_PHASE;
        waitTicks = 0;
        loading = true;   // the veil covers the world's making here too; the lobby screen opening ends it
        createWorld();
    }

    static final int HOST_PHASE = 10;

    static void createWorld() {
        Minecraft mc = Minecraft.getInstance();
        // a fresh world per skirmish; number them so the Singleplayer list stays readable
        int n = 1;
        while (mc.getLevelSource().levelExists(WORLD_DIR_PREFIX + n))
            n++;
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(true, null);
        LevelSettings levelSettings = new LevelSettings(WORLD_NAME_PREFIX + n, GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        // structures OFF: a vanilla village with a palisade once generated across the battlefield (clip 15-56)
        mc.createWorldOpenFlows().createFreshLevel(WORLD_DIR_PREFIX + n, levelSettings,
                com.solegendary.reignofnether.startpos.ArenaStructureClearing.skirmishWorldOptions(
                        pending != null && pending.seed != null ? pending.seed : WorldOptions.randomSeed()),
                QuickBattle::battlefieldDimensions);
    }

    /** The Battlefield world preset (gentle rolling grassland built for matches); vanilla terrain as the fallback. */
    static net.minecraft.world.level.levelgen.WorldDimensions battlefieldDimensions(net.minecraft.core.RegistryAccess registryAccess) {
        try {
            var presets = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET);
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.WORLD_PRESET,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "battlefield"));
            var preset = presets.get(key);
            if (preset != null)
                return preset.createWorldDimensions();
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[QuickBattle] battlefield preset unavailable, using vanilla terrain", e);
        }
        return WorldPresets.createNormalWorldDimensions(registryAccess);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || phase == 0)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        // the RTS view itself is a screen (TopdownGui), and so is our own loading veil before the RTS view takes
        // over - only a foreign screen (pause menu, chat...) pauses us
        if (mc.screen != null && !(mc.screen instanceof com.solegendary.reignofnether.guiscreen.TopdownGui)
                && !(mc.screen instanceof MatchLoadingScreen))
            return;
        waitTicks++;
        switch (phase) {
            case 1 -> {   // in the world: enter the RTS camera almost at once (the loading screen has closed by now);
                // the old 2 s wait showed the vanilla first-person view and hotbar first (lovish's Oct 12 clips)
                if (waitTicks > 5) {
                    if (!OrthoviewClientEvents.isEnabled())
                        OrthoviewClientEvents.toggleEnable();
                    phase = 2;
                    waitTicks = 0;
                }
            }
            case 2 -> {   // hand the lobby choices to the server: it starts us, lays the capitol, calls in the bots
                if (waitTicks > 20 && pending != null) {
                    List<SkirmishServerboundPacket.BotSpec> bots = new ArrayList<>();
                    for (SkirmishSetupScreen.Bot b : pending.bots)
                        bots.add(new SkirmishServerboundPacket.BotSpec(b.faction, b.difficulty,
                                pending.botColorMapId(b), b.team));
                    SkirmishServerboundPacket.send(pending.faction, pending.colorMapId(), pending.team, bots,
                            pending.arena, pending.metal, pending.spawnDistance, pending.pace,
                            pending.map, pending.choosePositions);
                    phase = 3;
                    waitTicks = 0;
                }
            }
            case 3 -> {   // once the server has us as an RTS player we're done (a spectator never is)
                if (PlayerClientEvents.isRTSPlayer(mc.player.getName().getString()) || waitTicks > 300
                        || (pending != null && pending.spectating())) {
                    if (pending != null && pending.spectating())
                        com.solegendary.reignofnether.hud.playerdisplay.PlayerDisplayClientEvents.showObserverDisplays();
                    phase = 0;
                }
            }
            case HOST_PHASE -> {   // in the fresh world: share it, then open the lobby with the chosen map
                if (waitTicks > 40) {
                    var server = mc.getSingleplayerServer();
                    if (server != null && !server.isPublished()) {
                        // cheats OFF for joiners: with them on every friend is an op (resets, cheat words)
                        int port = net.minecraft.util.HttpUtil.getAvailablePort();
                        boolean ok = server.publishServer(server.getDefaultGameType(), false, port);
                        mc.gui.getChat().addMessage(Component.literal(ok
                            ? "Open to friends on port " + port + ". With the e4mc mod the public join address appears in chat - send it to your friends (Multiplayer -> Direct Connection)."
                            : "Could not open the world to LAN - use Esc -> Open to LAN."));
                    }
                    LobbyClientEvents.openLobby();
                    if (pending != null) {
                        LobbyServerboundPacket.send(MultiplayerLobby.Action.SET_ARENA, 0, pending.arena);
                        LobbyServerboundPacket.send(MultiplayerLobby.Action.SET_METAL, 0, pending.metal);
                        LobbyServerboundPacket.send(MultiplayerLobby.Action.SET_SPAWNS, 0, pending.spawnDistance);
                        LobbyServerboundPacket.send(MultiplayerLobby.Action.SET_PACE, 0, pending.pace);
                        LobbyServerboundPacket.send(MultiplayerLobby.Action.SET_MAP, 0, pending.map);
                        LobbyServerboundPacket.send(MultiplayerLobby.Action.SET_STARTPOS, 0, pending.choosePositions ? 1 : 0);

                    }
                    pending = null;
                    phase = 0;
                }
            }
            default -> phase = 0;
        }
    }
}
