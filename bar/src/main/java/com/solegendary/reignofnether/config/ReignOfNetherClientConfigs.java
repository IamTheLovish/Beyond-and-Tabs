package com.solegendary.reignofnether.config;

import com.solegendary.reignofnether.player.PlayerColors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;

public class ReignOfNetherClientConfigs {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<Integer> PLAYER_COLOR_SELF;
    public static final ForgeConfigSpec.ConfigValue<Integer> PLAYER_COLOR_ALLY;
    public static final ForgeConfigSpec.ConfigValue<Integer> PLAYER_COLOR_NEUTRAL;
    public static final ForgeConfigSpec.ConfigValue<Integer> PLAYER_COLOR_ENEMY;

    public static final ForgeConfigSpec.ConfigValue<Boolean> USE_PLAYER_COLORS;
    public static final ForgeConfigSpec.ConfigValue<Integer> CAMERA_SENSITIVITY;
    public static final ForgeConfigSpec.ConfigValue<Boolean> SQUARE_MINIMAP;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ALERTS_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<Integer> ALERT_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ANNOUNCER_VOICE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> SHOW_DEV_TIME;
    public static final ForgeConfigSpec.ConfigValue<Boolean> FIRST_LAUNCH_DONE;
    // which key layout this install's saved key bindings were last moved to (keybinds/KeybindMigration)
    public static final ForgeConfigSpec.ConfigValue<Integer> KEYBIND_LAYOUT;
    public static final ForgeConfigSpec.ConfigValue<Integer> DOUBLE_CLICK_MS;
    public static final ForgeConfigSpec.ConfigValue<Integer> HOST_PORT;
    public static final ForgeConfigSpec.ConfigValue<Integer> AREA_MEX_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CAMERA_ZOOM_INVERT;
    public static final ForgeConfigSpec.ConfigValue<Integer> VOICE_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<Boolean> LOBBY_SOUNDS;
    // in-game sound effects per category (sounds.SfxClient, SoundSettingsScreen; design/audio.md). The Announcer
    // category shares VOICE_VOLUME; UI also scales the lobby sounds.
    public static final ForgeConfigSpec.ConfigValue<Integer> SFX_UNITS_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<Integer> SFX_BUILDINGS_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<Integer> SFX_ABILITIES_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<Integer> SFX_UI_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<Boolean> MUSIC_STINGERS;
    // title / loading screen (hud/SplashArt, design/splash.md)
    public static final ForgeConfigSpec.ConfigValue<Boolean> SPLASH_REDUCE_MOTION;

    // BAR Spring-camera tuning (OrthoviewClientEvents, CameraSettingsScreen; design/camera.md)
    public static final ForgeConfigSpec.ConfigValue<Integer> CAMERA_ZOOM_SPEED;
    public static final ForgeConfigSpec.ConfigValue<Integer> CAMERA_MMB_SPEED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CAMERA_MMB_INVERT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CAMERA_EDGE_SCROLL;
    public static final ForgeConfigSpec.ConfigValue<Integer> CAMERA_EDGE_SPEED;
    public static final ForgeConfigSpec.ConfigValue<Integer> CAMERA_SMOOTHING;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CAMERA_TILT_FOLLOWS_ZOOM;
    public static final ForgeConfigSpec.ConfigValue<Integer> CAMERA_PITCH_CLOSE;
    public static final ForgeConfigSpec.ConfigValue<Integer> CAMERA_PITCH_FAR;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CAMERA_ZOOM_OUT_FROM_CURSOR;
    public static final ForgeConfigSpec.ConfigValue<Integer> CAMERA_SHAKE;

    // display (DisplaySettings, DisplaySettingsScreen; design/hud.md "Settings")
    public static final ForgeConfigSpec.ConfigValue<Integer> ICON_MODE;
    public static final ForgeConfigSpec.ConfigValue<Integer> ICON_ZOOM_THRESHOLD;
    public static final ForgeConfigSpec.ConfigValue<Integer> ICON_SCALE;
    public static final ForgeConfigSpec.ConfigValue<Integer> FOG_STYLE;
    public static final ForgeConfigSpec.ConfigValue<Integer> HEALTH_BAR_MODE;
    // unit render level of detail (unit/modelling/renderers/UnitLod): false = the old behaviour, for A/B comparison
    public static final ForgeConfigSpec.ConfigValue<Boolean> UNIT_RENDER_LOD;
    // the world look (postfx/WorldLookPass): edge smoothing, distance haze, a film grade and a vignette for players without shader packs
    public static final ForgeConfigSpec.ConfigValue<Integer> WORLD_LOOK;
    public static final ForgeConfigSpec.ConfigValue<Integer> WORLD_LOOK_HAZE;
    // HUD declutter (HudPanels, HudSettingsScreen; design/hud.md "Human-POV audit"): every non-essential panel
    // remembers whether it is collapsed, so a player who tidied the screen once keeps it tidy across restarts
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_STATS_OPEN;
    public static final ForgeConfigSpec.ConfigValue<Integer> HUD_STATS_TAB;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_ALERTS_COLLAPSED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_CHEATSHEET_COLLAPSED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_UNITCARD_COMPACT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_ECONOMY_COMPACT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_COMMAND_COMPACT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_MINIMAP_LARGE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_OBSERVER_BAR;
    public static final ForgeConfigSpec.ConfigValue<Integer> HUD_TOAST_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<Integer> HUD_PLATE_OPACITY;
    public static final ForgeConfigSpec.ConfigValue<Boolean> BUILDING_AMBIENCE;
    // the moving parts drawn on buildings (building/PartRenderClientEvents, design/moving_buildings.md): -1 = not
    // chosen yet (follows BUILDING_AMBIENCE), 0 Off, 1 Low, 2 Full
    public static final ForgeConfigSpec.ConfigValue<Integer> BUILDING_ANIMATION;
    public static final ForgeConfigSpec.ConfigValue<Boolean> GROUND_SCARS;
    // tree limbs swaying in the wind (environment/wind, design/branch_wind.md): 0 Off, 1 Near (within 60 blocks of the camera), 2 All
    public static final ForgeConfigSpec.ConfigValue<Integer> BRANCH_WIND;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HERO_DAMAGE_NUMBERS;
    // onboarding (onboarding/, design/onboarding.md): first-time tips, which were seen, the First Battle world
    public static final ForgeConfigSpec.ConfigValue<Boolean> ONBOARDING_TIPS;
    public static final ForgeConfigSpec.ConfigValue<String> ONBOARDING_SEEN_TIPS;
    public static final ForgeConfigSpec.ConfigValue<String> ONBOARDING_WORLD;

    static {
        BUILDER.push("Configuration File");
        BUILDER.pop();
        BUILDER.comment("Player colors");
        for (PlayerColors.PlayerColor color : PlayerColors.colors) {
            BUILDER.comment("- " + color.name + ": " + color.id);
        }
        PLAYER_COLOR_SELF = BUILDER.define("player_color_self", PlayerColors.COLOR_OWNED.id);
        PLAYER_COLOR_ALLY = BUILDER.define("player_color_ally", PlayerColors.COLOR_FRIENDLY.id);
        PLAYER_COLOR_NEUTRAL = BUILDER.define("player_color_neutral", PlayerColors.COLOR_NEUTRAL.id);
        PLAYER_COLOR_ENEMY = BUILDER.define("player_color_enemy", PlayerColors.COLOR_HOSTILE.id);
        BUILDER.comment("true: units, outlines, rings, icons and the minimap use each player's lobby colour; false: green (you), blue (allies), yellow (neutral), red (enemies). Default true since Oct 20 (Lane C tester: each player's colour should be the one they picked in the lobby)");
        USE_PLAYER_COLORS = BUILDER.define("use_player_colors", true);
        BUILDER.comment("Camera pan speed (keys and screen edge), 1-30 (10 = default)");
        CAMERA_SENSITIVITY = BUILDER.define("camera_sensitivity", 10);
        BUILDER.comment("Mouse-wheel zoom step, 1-30 (10 = 15% per notch)");
        CAMERA_ZOOM_SPEED = BUILDER.defineInRange("camera_zoom_speed", 10, 1, 30);
        BUILDER.comment("Area mex circle radius in blocks (Shift with a metal extractor armed; Shift+wheel resizes it), 4-32");
        AREA_MEX_RADIUS = BUILDER.defineInRange("area_mex_radius", 12, 4, 32);
        BUILDER.comment("Invert the mouse wheel: wheel forward zooms out (also flips Ctrl+wheel tilt)");
        CAMERA_ZOOM_INVERT = BUILDER.define("camera_zoom_invert", false);
        BUILDER.comment("Middle-mouse drag speed, 1-30 (10 = the ground stays under the cursor)");
        CAMERA_MMB_SPEED = BUILDER.defineInRange("camera_mmb_speed", 10, 1, 30);
        BUILDER.comment("Invert middle-mouse drag (drag moves the camera instead of the map)");
        CAMERA_MMB_INVERT = BUILDER.define("camera_mmb_invert", false);
        BUILDER.comment("Pan when the cursor touches a screen edge");
        CAMERA_EDGE_SCROLL = BUILDER.define("camera_edge_scroll", true);
        BUILDER.comment("Screen-edge pan speed, 1-30 (10 = same as the pan keys)");
        CAMERA_EDGE_SPEED = BUILDER.defineInRange("camera_edge_speed", 10, 1, 30);
        BUILDER.comment("Camera smoothing, 0-30: 0 = instant, 10 = about 0.2 s glide (BAR-like), 30 = slow and floaty");
        CAMERA_SMOOTHING = BUILDER.defineInRange("camera_smoothing", 10, 0, 30);
        BUILDER.comment("Tilt follows zoom: top-down when zoomed out, more angled close up (BAR)");
        CAMERA_TILT_FOLLOWS_ZOOM = BUILDER.define("camera_tilt_follows_zoom", true);
        // _v2 keys (Oct 12): new, gentler defaults must reach players whose config already holds the old 40/80 pair
        BUILDER.comment("Camera pitch in degrees below the horizon when fully zoomed in (12-90)");
        CAMERA_PITCH_CLOSE = BUILDER.defineInRange("camera_pitch_close_v2", 55, 12, 90);
        BUILDER.comment("Camera pitch in degrees below the horizon when zoomed out (12-90)");
        CAMERA_PITCH_FAR = BUILDER.defineInRange("camera_pitch_far_v2", 75, 12, 90);
        BUILDER.comment("Zoom out away from the cursor as well (Spring default: zoom in to the cursor, out from the centre)");
        CAMERA_ZOOM_OUT_FROM_CURSOR = BUILDER.define("camera_zoom_out_from_cursor", false);
        BUILDER.comment("Camera shake on big blasts and building collapses, 0-100% (0 = none; never grows with army size)");
        CAMERA_SHAKE = BUILDER.defineInRange("camera_shake", 100, 0, 100);
        SQUARE_MINIMAP = BUILDER.define("square_minimap", false);
        BUILDER.comment("BAR-style notifications (commander under attack, unit lost, construction complete...)");
        ALERTS_ENABLED = BUILDER.define("alerts_enabled", true);
        BUILDER.comment("Notification sound volume, 0-100 (0 = text only)");
        ALERT_VOLUME = BUILDER.defineInRange("alert_volume", 100, 0, 100);
        BUILDER.comment("Spoken announcer for alerts (replaces the alert chord while on; needs alerts_enabled)");
        ANNOUNCER_VOICE = BUILDER.define("announcer_voice", true);
        BUILDER.comment("Show how long the game has taken to build (\"Built in ~69 h\") at the bottom right of the title screen");
        SHOW_DEV_TIME = BUILDER.define("show_dev_time", true);
        BUILDER.comment("Set after the first launch applied its defaults (full screen, no accessibility screen): onboarding/FirstLaunchClientEvents. Set false to apply them again.");
        FIRST_LAUNCH_DONE = BUILDER.define("first_launch_done", false);
        BUILDER.comment("Key layout version the saved key bindings were last moved to (keybinds/KeybindMigration). Minecraft keeps every binding in options.txt, defaults included, so a default we change never reaches an install that already saved the old one: once per version, a binding still on an OLD default moves to the new default (a key you chose yourself is never touched). Set 0 to run the moves again.");
        KEYBIND_LAYOUT = BUILDER.defineInRange("keybind_layout", 0, 0, 1000);
        BUILDER.comment("Double-click / double-tap window in milliseconds: units and buildings in the world and unit-type buttons (select all of that type), control-group keys (jump to the group)");
        DOUBLE_CLICK_MS = BUILDER.defineInRange("double_click_ms", 400, 150, 800);
        BUILDER.comment("Port Host Online opens the world on, so a fixed tunnel (playit.gg and the like) keeps one address across sessions (design/multiplayer.md). 0 = any free port. If it is taken, a free port is used and chat says so.");
        HOST_PORT = BUILDER.defineInRange("host_port", 25565, 0, 65535);
        BUILDER.comment("Announcer voice volume, 0-100");
        VOICE_VOLUME = BUILDER.defineInRange("voice_volume", 100, 0, 100);
        BUILDER.comment("Clicks, chirps and faction stingers in the match lobby and skirmish setup screen");
        LOBBY_SOUNDS = BUILDER.define("lobby_sounds", true);
        BUILDER.comment("Sound effects: units (weapons, hits, deaths), 0-100");
        SFX_UNITS_VOLUME = BUILDER.defineInRange("sfx_units_volume", 80, 0, 100);
        BUILDER.comment("Sound effects: buildings (construction, completion, destruction, gates, traps, reclaim), 0-100");
        SFX_BUILDINGS_VOLUME = BUILDER.defineInRange("sfx_buildings_volume", 100, 0, 100);
        BUILDER.comment("Sound effects: abilities, heroes, possession and the T2 beasts, 0-100");
        SFX_ABILITIES_VOLUME = BUILDER.defineInRange("sfx_abilities_volume", 100, 0, 100);
        BUILDER.comment("Sound effects: interface (lobby, start-position phase), 0-100");
        SFX_UI_VOLUME = BUILDER.defineInRange("sfx_ui_volume", 100, 0, 100);
        BUILDER.comment("A short faction fanfare when the match starts (follows the Music slider)");
        MUSIC_STINGERS = BUILDER.define("music_stingers", true);
        BUILDER.comment("Reduce motion on the title and loading screens: no turning light, candle flicker or mouse parallax");
        SPLASH_REDUCE_MOTION = BUILDER.define("splash_reduce_motion", false);
        BUILDER.comment("Strategic icons: 0 = by zoom (icon_zoom_threshold), 1 = always icons, 2 = never icons");
        ICON_MODE = BUILDER.defineInRange("icon_mode", 0, 0, 2);
        BUILDER.comment("Camera zoom at which units turn into strategic icons, 20-70 (70 = default). Not higher:"
            + " past ~70 vanilla culling starts hiding far mobs, and the icons must take over before that");
        ICON_ZOOM_THRESHOLD = BUILDER.defineInRange("icon_zoom_threshold", 70, 20, 70);
        BUILDER.comment("Strategic icon size in percent, 50-200");
        ICON_SCALE = BUILDER.defineInRange("icon_scale", 100, 50, 200);
        BUILDER.comment("Fog of war look: 0 = subtle, 1 = clear (default), 2 = strong (darker, hatched unexplored minimap)");
        FOG_STYLE = BUILDER.defineInRange("fog_style", 1, 0, 2);
        BUILDER.comment("Unit health bars: 0 = always, 1 = damaged (default: hurt, selected or hovered units), 2 = selected or hovered only");
        HEALTH_BAR_MODE = BUILDER.defineInRange("health_bar_mode", 1, 0, 2);
        BUILDER.comment("World look: 0 = off, 1 = subtle (default), 2 = full. Smoothed geometry edges, distance haze, a gentle film grade and a soft"
            + " vignette over the world, never the HUD; switches itself off while an Oculus / Iris shader pack is on");
        WORLD_LOOK = BUILDER.defineInRange("world_look", 1, 0, 2);
        BUILDER.comment("World look: distance haze strength in percent, 0-200 (100 = default, 0 = grade only)");
        WORLD_LOOK_HAZE = BUILDER.defineInRange("world_look_haze", 100, 0, 200);
        BUILDER.comment("HUD: the stats table (players / economy / army / production) is open (I toggles it)");
        HUD_STATS_OPEN = BUILDER.define("hud_stats_open", false);
        BUILDER.comment("HUD: the stats table's tab, 0 players, 1 economy, 2 army, 3 production");
        HUD_STATS_TAB = BUILDER.defineInRange("hud_stats_tab", 0, 0, 3);
        BUILDER.comment("HUD: alert toasts collapsed to icons with count badges");
        HUD_ALERTS_COLLAPSED = BUILDER.define("hud_alerts_collapsed", false);
        BUILDER.comment("HUD: the controls cheat-sheet collapsed to a ? button");
        HUD_CHEATSHEET_COLLAPSED = BUILDER.define("hud_cheatsheet_collapsed", true);
        BUILDER.comment("HUD: the selected unit's card in one line");
        HUD_UNITCARD_COMPACT = BUILDER.define("hud_unitcard_compact", false);
        BUILDER.comment("HUD: the economy bar in one row (no income / expense line)");
        HUD_ECONOMY_COMPACT = BUILDER.define("hud_economy_compact", false);
        BUILDER.comment("HUD: the command card without its backing and group totals line");
        HUD_COMMAND_COMPACT = BUILDER.define("hud_command_compact", false);
        BUILDER.comment("HUD: the large minimap (M) was open when the game closed");
        HUD_MINIMAP_LARGE = BUILDER.define("hud_minimap_large", false);
        BUILDER.comment("HUD: the spectator observer bar (income, army, units, tier, powers per player) while spectating");
        HUD_OBSERVER_BAR = BUILDER.define("hud_observer_bar", true);
        BUILDER.comment("HUD: seconds an alert toast stays up before it fades, 1-10");
        HUD_TOAST_SECONDS = BUILDER.defineInRange("hud_toast_seconds", 3, 1, 10);
        BUILDER.comment("Strategic view: opacity of the team-coloured building plates, 10-100%");
        HUD_PLATE_OPACITY = BUILDER.defineInRange("hud_plate_opacity", 100, 10, 100);
        BUILDER.comment("Building ambience: chimney smoke, petals, soul flames, steam and sparks round capitols, energy buildings and labs near the camera");
        BUILDING_AMBIENCE = BUILDER.define("building_ambience", true);
        BUILDER.comment("Building animation: moving parts drawn on buildings (metal extractor rigs: sheave wheel, ore skip, owner pennant). 0 Off, 1 Low (nearest 24, simplified sooner), 2 Full; -1 = not chosen yet: follows building_ambience (false = Off, true = Full)");
        BUILDING_ANIMATION = BUILDER.defineInRange("building_animation", -1, -1, 2);
        BUILDER.comment("Branch wind: tree limbs drawn swaying in the wind. 0 Off (limbs are plain blocks, no cost), 1 Near (default: limbs within 60 blocks of the camera sway, the rest are plain blocks), 2 All (every limb in view sways)");
        BRANCH_WIND = BUILDER.defineInRange("branch_wind", 1, 0, 2);
        BUILDER.comment("Hero mode: floating damage numbers over what your possessed unit hits (the hit marker always shows)");
        HERO_DAMAGE_NUMBERS = BUILDER.define("hero_damage_numbers", true);
        BUILDER.comment("One-line first-time tips in matches (each shown once; OnboardingTips)");
        ONBOARDING_TIPS = BUILDER.define("onboarding_tips", true);
        BUILDER.comment("Tips already shown (comma-separated ids); clear it to see them again");
        ONBOARDING_SEEN_TIPS = BUILDER.define("onboarding_seen_tips", "");
        BUILDER.comment("The saves folder of the last First Battle tutorial, for Resume (empty = none or finished)");
        ONBOARDING_WORLD = BUILDER.define("onboarding_world", "");
        BUILDER.comment("Ground scars: the scorch and craters blasts leave stay on the ground for the match (a world that remembers); false = the old 12-second marks");
        GROUND_SCARS = BUILDER.define("ground_scars", true);
        BUILDER.comment("Unit render level of detail: with a shader pack, units skip their team-colour / glow overlay passes in the pack's shadow map (the body already casts that shadow); outside the RTS view their small detail parts follow on-screen size instead of a fixed 40 blocks. Nothing changes up close. false = the old behaviour, for A/B comparison (the [FrameParts] line's unitBodies and unitShadow in latest.log); the file is re-read while the game runs. -Dreignofnether.unitLod=false overrides it.");
        UNIT_RENDER_LOD = BUILDER.define("unit_render_lod", true);
        SPEC = BUILDER.build();
    }

    public static ConfigScreenHandler.ConfigScreenFactory createConfigScreen() {
        return new ConfigScreenHandler.ConfigScreenFactory(ReignOfNetherClientConfigs::buildConfigScreen);
    }

    private static Screen buildConfigScreen(Screen screen) {
        return new ReignOfNetherConfigScreen(screen);
    }
}
