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
    public static final ForgeConfigSpec.ConfigValue<Integer> VOICE_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<Boolean> LOBBY_SOUNDS;

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

    // display (DisplaySettings, DisplaySettingsScreen; design/hud.md "Settings")
    public static final ForgeConfigSpec.ConfigValue<Integer> ICON_MODE;
    public static final ForgeConfigSpec.ConfigValue<Integer> ICON_ZOOM_THRESHOLD;
    public static final ForgeConfigSpec.ConfigValue<Integer> ICON_SCALE;
    public static final ForgeConfigSpec.ConfigValue<Integer> FOG_STYLE;
    // HUD declutter (HudPanels, HudSettingsScreen; design/hud.md "Human-POV audit"): every non-essential panel
    // remembers whether it is collapsed, so a player who tidied the screen once keeps it tidy across restarts
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_STATS_OPEN;
    public static final ForgeConfigSpec.ConfigValue<Integer> HUD_STATS_TAB;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_KILLFEED_COLLAPSED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_ALERTS_COLLAPSED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_CHEATSHEET_COLLAPSED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_UNITCARD_COMPACT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_ECONOMY_COMPACT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_COMMAND_COMPACT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_MINIMAP_LARGE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUD_OBSERVER_BAR;
    public static final ForgeConfigSpec.ConfigValue<Integer> HUD_TOAST_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<Integer> HUD_PLATE_OPACITY;

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
        USE_PLAYER_COLORS = BUILDER.define("use_player_colors", false);
        BUILDER.comment("Camera pan speed (keys and screen edge), 1-30 (10 = default)");
        CAMERA_SENSITIVITY = BUILDER.define("camera_sensitivity", 10);
        BUILDER.comment("Mouse-wheel zoom step, 1-30 (10 = 15% per notch)");
        CAMERA_ZOOM_SPEED = BUILDER.defineInRange("camera_zoom_speed", 10, 1, 30);
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
        BUILDER.comment("Camera pitch in degrees below the horizon when fully zoomed in (12-90)");
        CAMERA_PITCH_CLOSE = BUILDER.defineInRange("camera_pitch_close", 40, 12, 90);
        BUILDER.comment("Camera pitch in degrees below the horizon when zoomed out (12-90)");
        CAMERA_PITCH_FAR = BUILDER.defineInRange("camera_pitch_far", 80, 12, 90);
        BUILDER.comment("Zoom out away from the cursor as well (Spring default: zoom in to the cursor, out from the centre)");
        CAMERA_ZOOM_OUT_FROM_CURSOR = BUILDER.define("camera_zoom_out_from_cursor", false);
        SQUARE_MINIMAP = BUILDER.define("square_minimap", false);
        BUILDER.comment("BAR-style notifications (commander under attack, unit lost, construction complete...)");
        ALERTS_ENABLED = BUILDER.define("alerts_enabled", true);
        BUILDER.comment("Notification sound volume, 0-100 (0 = text only)");
        ALERT_VOLUME = BUILDER.defineInRange("alert_volume", 100, 0, 100);
        BUILDER.comment("Spoken announcer for alerts (replaces the alert chord while on; needs alerts_enabled)");
        ANNOUNCER_VOICE = BUILDER.define("announcer_voice", true);
        BUILDER.comment("Announcer voice volume, 0-100");
        VOICE_VOLUME = BUILDER.defineInRange("voice_volume", 100, 0, 100);
        BUILDER.comment("Clicks, chirps and faction stingers in the match lobby and skirmish setup screen");
        LOBBY_SOUNDS = BUILDER.define("lobby_sounds", true);
        BUILDER.comment("Strategic icons: 0 = by zoom (icon_zoom_threshold), 1 = always icons, 2 = never icons");
        ICON_MODE = BUILDER.defineInRange("icon_mode", 0, 0, 2);
        BUILDER.comment("Camera zoom at which units turn into strategic icons, 20-70 (70 = default). Not higher:"
            + " past ~70 vanilla culling starts hiding far mobs, and the icons must take over before that");
        ICON_ZOOM_THRESHOLD = BUILDER.defineInRange("icon_zoom_threshold", 70, 20, 70);
        BUILDER.comment("Strategic icon size in percent, 50-200");
        ICON_SCALE = BUILDER.defineInRange("icon_scale", 100, 50, 200);
        BUILDER.comment("Fog of war look: 0 = subtle, 1 = clear (default), 2 = strong (darker, hatched unexplored minimap)");
        FOG_STYLE = BUILDER.defineInRange("fog_style", 1, 0, 2);
        BUILDER.comment("HUD: the stats table (players / economy / army / production) is open (I toggles it)");
        HUD_STATS_OPEN = BUILDER.define("hud_stats_open", false);
        BUILDER.comment("HUD: the stats table's tab, 0 players, 1 economy, 2 army, 3 production");
        HUD_STATS_TAB = BUILDER.defineInRange("hud_stats_tab", 0, 0, 3);
        BUILDER.comment("HUD: kill feed collapsed to a one-line ticker");
        HUD_KILLFEED_COLLAPSED = BUILDER.define("hud_killfeed_collapsed", true);
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
        SPEC = BUILDER.build();
    }

    public static ConfigScreenHandler.ConfigScreenFactory createConfigScreen() {
        return new ConfigScreenHandler.ConfigScreenFactory(ReignOfNetherClientConfigs::buildConfigScreen);
    }

    private static Screen buildConfigScreen(Screen screen) {
        return new ReignOfNetherConfigScreen(screen);
    }
}
