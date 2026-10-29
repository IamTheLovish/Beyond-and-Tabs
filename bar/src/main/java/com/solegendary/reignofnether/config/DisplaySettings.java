package com.solegendary.reignofnether.config;

import com.solegendary.reignofnether.fogofwar.FogExploration;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client display options (Mod Settings -> Display..., design/hud.md "Settings"): when units turn into strategic
 * icons, how big the icons are, and how strongly the fog of war reads.
 *
 * The fog style is applied through the existing tint path (FogTintingBlockColor multiplies dark-chunk vertex
 * colours by FogOfWarClientEvents.FOG_TINT_RGB): the tint is baked into chunk meshes, so a change costs one chunk
 * rebuild and nothing per frame. A client tick compares the config with what was applied (one int compare), so an
 * edit from the screen, the config file or a fresh game start all land the same way.
 */
public class DisplaySettings {

    public static final int ICONS_BY_ZOOM = 0, ICONS_ALWAYS = 1, ICONS_NEVER = 2;
    public static final int FOG_SUBTLE = 0, FOG_CLEAR = 1, FOG_STRONG = 2;
    public static final int BARS_ALWAYS = 0, BARS_DAMAGED = 1, BARS_SELECTED = 2;
    public static final int LOOK_OFF = 0, LOOK_SUBTLE = 1, LOOK_FULL = 2, LOOK_ENCHANTED = 3;
    public static final int ANIM_OFF = 0, ANIM_LOW = 1, ANIM_FULL = 2;

    static final String[] ICON_MODE_NAMES = { "By zoom", "Always icons", "Never icons" };
    static final String[] FOG_STYLE_NAMES = { "Subtle", "Clear", "Strong" };
    static final String[] HEALTH_BAR_MODE_NAMES = { "Always", "Damaged", "Selected only" };
    public static final String[] WORLD_LOOK_NAMES = { "Off", "Subtle", "Full", "Enchanted" };
    public static final String[] BUILDING_ANIMATION_NAMES = { "Off", "Low", "Full" };

    // multipliers on the world's colours in fogged chunks: Clear is the original look; Subtle keeps most of the
    // colour (for players who find the fog gloomy), Strong is clearly darker so seen-vs-unseen reads at a glance
    private static final int[] FOG_TINTS = { 0xA8B0C4, FogOfWarClientEvents.FOG_TINT_CLEAR, 0x4C5672 };
    // the same styles for ground never in sight ("dark but visible", about 15% brightness; Strong darker still)
    private static final int[] FOG_TINTS_UNEXPLORED = { 0x2E323C, FogExploration.TINT_UNEXPLORED, 0x161820 };

    private static final Minecraft MC = Minecraft.getInstance();
    private static int appliedFogStyle = FOG_CLEAR;

    private static int get(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<Integer> v, int fallback) {
        try {
            return v.get();
        } catch (IllegalStateException e) {   // config not loaded yet (very early in startup)
            return fallback;
        }
    }

    public static int iconMode() {
        return get(ReignOfNetherClientConfigs.ICON_MODE, ICONS_BY_ZOOM);
    }

    public static float iconZoomThreshold() {
        return get(ReignOfNetherClientConfigs.ICON_ZOOM_THRESHOLD, 70);
    }

    public static float iconScale() {
        return get(ReignOfNetherClientConfigs.ICON_SCALE, 100) / 100f;
    }

    public static int fogStyle() {
        int s = get(ReignOfNetherClientConfigs.FOG_STYLE, FOG_CLEAR);
        return s < FOG_SUBTLE || s > FOG_STRONG ? FOG_CLEAR : s;
    }

    /**
     * Unit health bars (HealthBarClientEvents; clip study Oct 13 c1/e1: forty bars over one river tile). Always: every
     * unit. Damaged (default): selected, hovered, or hurt - own hurt units always, others only while they are still
     * being hit. Selected only: selected or hovered units, nothing else.
     */
    public static int healthBarMode() {
        int m = get(ReignOfNetherClientConfigs.HEALTH_BAR_MODE, BARS_DAMAGED);
        return m < BARS_ALWAYS || m > BARS_SELECTED ? BARS_DAMAGED : m;
    }

    /**
     * The world look pass (postfx/WorldLookPass): off, subtle (default) or full. Read every frame - a cached config
     * value, so an edit from the screen or the file lands on the next frame.
     */
    public static int worldLook() {
        int v = get(ReignOfNetherClientConfigs.WORLD_LOOK, LOOK_ENCHANTED);
        return v < LOOK_OFF || v > LOOK_ENCHANTED ? LOOK_ENCHANTED : v;
    }

    /** The world look's distance haze strength, 0-2 (1 = default). */
    public static float hazeStrength() {
        return Math.max(0, Math.min(get(ReignOfNetherClientConfigs.WORLD_LOOK_HAZE, 100), 200)) / 100f;
    }

    /** Ambient life (ambient/AmbientLifeClientEvents): butterflies by day, fireflies at night, round the camera's view. */
    public static boolean ambientLife() {
        try {
            return ReignOfNetherClientConfigs.AMBIENT_LIFE.get();
        } catch (IllegalStateException e) {   // config not loaded yet
            return true;
        }
    }

    /** Valley mist (ambient/mist/MistClientEvents): soft ground mist in hollows, by water and under cliffs at dawn and dusk. */
    public static boolean valleyMist() {
        try {
            return ReignOfNetherClientConfigs.VALLEY_MIST.get();
        } catch (IllegalStateException e) {   // config not loaded yet
            return true;
        }
    }

    /** Building ambience (design/lively_buildings.md): the idle particles round capitols, energy buildings and labs. */
    public static boolean buildingAmbience() {
        try {
            return ReignOfNetherClientConfigs.BUILDING_AMBIENCE.get();
        } catch (IllegalStateException e) {   // config not loaded yet
            return true;
        }
    }

    /**
     * Building animation (design/moving_buildings.md; building/PartRenderClientEvents): the moving parts drawn on
     * buildings - Off, Low (the nearest 24, simplified sooner, no secondary parts) or Full. Until the player picks one
     * (-1 in the file) it follows the older Building ambience switch: ambience off reads as Off, else Full (the
     * design's recommended default; the renderer may drop Full to Low for a slow session). Read every frame - a cached
     * config value.
     */
    public static int buildingAnimation() {
        int v = get(ReignOfNetherClientConfigs.BUILDING_ANIMATION, -1);
        if (v < ANIM_OFF || v > ANIM_FULL)
            return buildingAmbience() ? ANIM_FULL : ANIM_OFF;
        return v;
    }

    /**
     * Branch wind (environment/wind/BranchWindClient, design/branch_wind.md): tree limbs swaying - Off, Near (within
     * 60 blocks of the camera; the default) or All. Read every policy tick and per drawn limb - a cached config value.
     */
    public static int branchWind() {
        int v = get(ReignOfNetherClientConfigs.BRANCH_WIND, 1);
        return v < 0 || v > 2 ? 1 : v;
    }

    /** Whether the player has picked a building animation setting (else it follows Building ambience). */
    public static boolean buildingAnimationChosen() {
        int v = get(ReignOfNetherClientConfigs.BUILDING_ANIMATION, -1);
        return v >= ANIM_OFF && v <= ANIM_FULL;
    }

    /** The minimap hatches never-seen ground in every style (lovish, Oct 22: dark but visible). Read by the terrain pass, not per frame. */
    public static boolean hatchUnexplored() {
        return true;
    }

    /** Strong shades never-seen ground on the minimap darker still (1 = the base shade). */
    public static float unexploredMinimapScale() {
        return appliedFogStyle == FOG_STRONG ? 0.55f : 1f;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        int style = fogStyle();
        if (style == appliedFogStyle)
            return;
        appliedFogStyle = style;
        FogOfWarClientEvents.FOG_TINT_RGB = FOG_TINTS[style];
        FogOfWarClientEvents.FOG_TINT_UNEXPLORED = FOG_TINTS_UNEXPLORED[style];
        MinimapClientEvents.forceFullRedraw();
        // the tint lives in the chunk meshes: rebuild them once (only matters while fog is on)
        if (MC.level != null && MC.levelRenderer != null)
            MC.levelRenderer.allChanged();
    }
}
