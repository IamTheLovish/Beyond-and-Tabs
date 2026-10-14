package com.solegendary.reignofnether.config;

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

    static final String[] ICON_MODE_NAMES = { "By zoom", "Always icons", "Never icons" };
    static final String[] FOG_STYLE_NAMES = { "Subtle", "Clear", "Strong" };
    static final String[] HEALTH_BAR_MODE_NAMES = { "Always", "Damaged", "Selected only" };

    // multipliers on the world's colours in fogged chunks: Clear is the original look; Subtle keeps most of the
    // colour (for players who find the fog gloomy), Strong is clearly darker so seen-vs-unseen reads at a glance
    private static final int[] FOG_TINTS = { 0xA8B0C4, FogOfWarClientEvents.FOG_TINT_CLEAR, 0x4C5672 };

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

    /** Building ambience (design/lively_buildings.md): the idle particles round capitols, energy buildings and labs. */
    public static boolean buildingAmbience() {
        try {
            return ReignOfNetherClientConfigs.BUILDING_AMBIENCE.get();
        } catch (IllegalStateException e) {   // config not loaded yet
            return true;
        }
    }

    /** The minimap hatches never-seen ground (Strong only). Read by the minimap's terrain pass, not per frame. */
    public static boolean hatchUnexplored() {
        return appliedFogStyle == FOG_STRONG;
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
        MinimapClientEvents.forceFullRedraw();
        // the tint lives in the chunk meshes: rebuild them once (only matters while fog is on)
        if (MC.level != null && MC.levelRenderer != null)
            MC.levelRenderer.allChanged();
    }
}
