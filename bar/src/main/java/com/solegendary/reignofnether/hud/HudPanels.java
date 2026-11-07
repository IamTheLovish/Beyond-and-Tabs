package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.gamerules.GameruleClient;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.playerdisplay.PlayerPanelClientEvents;
import com.solegendary.reignofnether.match.PlayerListRule;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The HUD declutter pass (design/hud.md, "Human-POV audit"; lovish: "a lot of things pop on the screen that take up a
 * lot of space, like the AI and player teams - being able to minimise things to see the game in a more effective
 * way"). One place for:
 *
 * - every collapsible panel's state (stats table open + tab, kill feed ticker, alert icons, cheat-sheet "?", compact
 *   unit card / economy bar / command card), read from the client config once and written back on each toggle, so
 *   a tidy screen stays tidy across restarts;
 * - the chevron each panel draws in its corner and the click on it (a fixed array of zones rebuilt every frame - no
 *   allocation), which counts as HUD for the cursor so a click on a chevron never box-selects;
 * - the stats-table key (I, F4 alias) and the clean-view key (=), both rebindable, both quiet while the selected
 *   card has a button on the key (HudClientEvents.isCardKey);
 * - {@link #hidden()}: vanilla's hideGui (F1) hides every RTS overlay, as RoN's own F1 toggle always did, and the two
 *   are kept in step.
 */
public final class HudPanels {
    private HudPanels() { }

    private static final Minecraft MC = Minecraft.getInstance();

    // chevron / click-zone ids (1 was the kill feed, removed; left unused so the other ids keep their numbers)
    public static final int STATS = 0, ALERTS = 2, CHEAT = 3, UNITCARD = 4, ECONOMY = 5, COMMAND = 6;
    public static final int TAB_FIRST = 8;   // stats tabs 8..11
    public static final int TABS = 4;
    public static final int CHEVRON = 9;     // px

    public static boolean statsOpen = false;
    public static int statsTab = 0;
    public static boolean alertsCollapsed = false;
    public static boolean cheatSheetCollapsed = true;
    public static boolean unitCardCompact = false;
    public static boolean economyCompact = false;
    public static boolean commandCompact = false;
    /** Clean view: only the economy bar, the minimap and the command card. Not remembered: it is a glance key. */
    public static boolean cleanView = false;

    private static boolean loaded = false;
    private static boolean lastHideGui = false;

    private static final int MAX_ZONES = 24;
    private static final int[] zx = new int[MAX_ZONES], zy = new int[MAX_ZONES], zw = new int[MAX_ZONES],
        zh = new int[MAX_ZONES], zid = new int[MAX_ZONES];
    private static int zones = 0;

    /** Reads the remembered state once; the config may not be loaded during very early frames, so retry later. */
    public static void ensureLoaded() {
        if (loaded)
            return;
        try {
            statsOpen = ReignOfNetherClientConfigs.HUD_STATS_OPEN.get();
            statsTab = Math.max(0, Math.min(TABS - 1, ReignOfNetherClientConfigs.HUD_STATS_TAB.get()));
            alertsCollapsed = ReignOfNetherClientConfigs.HUD_ALERTS_COLLAPSED.get();
            cheatSheetCollapsed = ReignOfNetherClientConfigs.HUD_CHEATSHEET_COLLAPSED.get();
            unitCardCompact = ReignOfNetherClientConfigs.HUD_UNITCARD_COMPACT.get();
            economyCompact = ReignOfNetherClientConfigs.HUD_ECONOMY_COMPACT.get();
            commandCompact = ReignOfNetherClientConfigs.HUD_COMMAND_COMPACT.get();
            loaded = true;
        } catch (IllegalStateException e) {
            // config not loaded yet: keep the defaults this frame
        }
    }

    /** Re-reads the remembered state (after Mod Settings > HUD changed it). */
    public static void reload() {
        loaded = false;
        ensureLoaded();
    }

    /** Every RTS overlay checks this: RoN's F1 toggle off, or vanilla's hideGui on. */
    public static boolean hidden() {
        return !HudClientEvents.enabled || MC.options.hideGui;
    }

    /** Panels that clean view hides (everything but the economy bar, the minimap and the command card). */
    public static boolean showExtras() {
        return !hidden() && !cleanView;
    }

    private static void save(ForgeConfigSpec.ConfigValue<Boolean> cfg, boolean v) {
        try {
            cfg.set(v);
        } catch (IllegalStateException ignored) { }
    }

    public static void setStatsOpen(boolean open) {
        statsOpen = open;
        save(ReignOfNetherClientConfigs.HUD_STATS_OPEN, open);
    }

    /**
     * The list key / chevron. With the match option "Player list" on this folds and opens the table as before. With it
     * off (the default) the list is hidden until the key brings it up as the open table, and the next press hides it
     * again (match.PlayerListRule).
     */
    private static void toggleStats() {
        boolean matchShows = GameruleClient.showPlayerList;
        if (matchShows) {
            setStatsOpen(!statsOpen);
            return;
        }
        boolean next = PlayerListRule.afterKey(false, PlayerPanelClientEvents.playerOpened);
        PlayerPanelClientEvents.playerOpened = next;
        setStatsOpen(next);
    }

    public static void setStatsTab(int tab) {
        statsTab = Math.max(0, Math.min(TABS - 1, tab));
        try {
            ReignOfNetherClientConfigs.HUD_STATS_TAB.set(statsTab);
        } catch (IllegalStateException ignored) { }
    }

    public static void toggle(int id) {
        ensureLoaded();
        switch (id) {
            case STATS -> toggleStats();
            case ALERTS -> save(ReignOfNetherClientConfigs.HUD_ALERTS_COLLAPSED, alertsCollapsed = !alertsCollapsed);
            case CHEAT -> save(ReignOfNetherClientConfigs.HUD_CHEATSHEET_COLLAPSED, cheatSheetCollapsed = !cheatSheetCollapsed);
            case UNITCARD -> save(ReignOfNetherClientConfigs.HUD_UNITCARD_COMPACT, unitCardCompact = !unitCardCompact);
            case ECONOMY -> save(ReignOfNetherClientConfigs.HUD_ECONOMY_COMPACT, economyCompact = !economyCompact);
            case COMMAND -> save(ReignOfNetherClientConfigs.HUD_COMMAND_COMPACT, commandCompact = !commandCompact);
            default -> {
                if (id >= TAB_FIRST && id < TAB_FIRST + TABS) {
                    setStatsTab(id - TAB_FIRST);
                    if (!statsOpen)
                        setStatsOpen(true);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- click zones

    /** Registers a clickable zone for this frame (dropped silently past MAX_ZONES). */
    public static void addZone(int id, int x, int y, int w, int h) {
        if (zones >= MAX_ZONES)
            return;
        zx[zones] = x;
        zy[zones] = y;
        zw[zones] = w;
        zh[zones] = h;
        zid[zones] = id;
        zones++;
    }

    static int zoneAt(int mx, int my) {
        for (int i = zones - 1; i >= 0; i--)
            if (mx >= zx[i] && mx < zx[i] + zw[i] && my >= zy[i] && my < zy[i] + zh[i])
                return zid[i];
        return -1;
    }

    public static boolean isMouseOver(int mx, int my) {
        return zoneAt(mx, my) >= 0;
    }

    /**
     * A 9 px chevron button: pointing down while the panel is open (click to collapse), right while it is collapsed
     * (click to expand). Drawn with fills only, so it reads at GUI scale 2 and 3 without a texture.
     */
    public static void chevron(GuiGraphics gg, int id, int x, int y, boolean collapsed) {
        boolean hover = HudClientEvents.mouseX >= x && HudClientEvents.mouseX < x + CHEVRON
            && HudClientEvents.mouseY >= y && HudClientEvents.mouseY < y + CHEVRON;
        gg.fill(x, y, x + CHEVRON, y + CHEVRON, hover ? 0xE0384048 : 0xC0101216);
        gg.fill(x, y + CHEVRON - 1, x + CHEVRON, y + CHEVRON, 0xFF3A3F46);
        int col = hover ? 0xFFFFFFFF : 0xFFB8C0C8;
        if (collapsed) {   // ">"
            for (int i = 0; i < 3; i++) {
                gg.fill(x + 3 + i, y + 2 + i, x + 4 + i, y + 3 + i, col);
                gg.fill(x + 3 + i, y + 6 - i, x + 4 + i, y + 7 - i, col);
            }
        } else {           // "v"
            for (int i = 0; i < 3; i++) {
                gg.fill(x + 2 + i, y + 3 + i, x + 3 + i, y + 4 + i, col);
                gg.fill(x + 6 - i, y + 3 + i, x + 7 - i, y + 4 + i, col);
            }
        }
        addZone(id, x, y, CHEVRON, CHEVRON);
    }

    /** The collapsed cheat-sheet: a small "?" button (click or hover for nothing else - it only expands). */
    public static void helpButton(GuiGraphics gg, int x, int y) {
        int s = HudLayout.HELP_BUTTON;
        boolean hover = HudClientEvents.mouseX >= x && HudClientEvents.mouseX < x + s
            && HudClientEvents.mouseY >= y && HudClientEvents.mouseY < y + s;
        gg.fill(x, y, x + s, y + s, hover ? 0xE0384048 : 0xC0101216);
        gg.fill(x, y + s - 1, x + s, y + s, 0xFF3A3F46);
        gg.drawString(MC.font, "?", x + (s - MC.font.width("?")) / 2 + 1, y + 2, hover ? 0xFFFFFFFF : 0xFFFFC860);
        addZone(CHEAT, x, y, s, s);
    }

    // ---------------------------------------------------------------------------------------------- events

    // first in the frame: the zones are rebuilt by whatever draws this frame
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPre(ScreenEvent.Render.Pre evt) {
        zones = 0;
        ensureLoaded();
    }

    @SubscribeEvent
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!(evt.getScreen() instanceof TopdownGui) || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1 || hidden())
            return;
        int id = zoneAt((int) evt.getMouseX(), (int) evt.getMouseY());
        if (id < 0)
            return;
        toggle(id);
        evt.setCanceled(true);
    }

    /** From HudClientEvents.onKeyRelease (RTS screen, no text box focused), after the card-key rule is known. */
    public static void onKey(int key, boolean cardKey) {
        if (!OrthoviewClientEvents.isEnabled())
            return;
        // F4 (the alias) always works; I gives way to a card button on I (ability slot 8)
        if (Keybindings.statsTable.matches(key) && (!cardKey || key == Keybindings.statsTable.getAliasKey()))
            toggle(STATS);
        else if (Keybindings.cleanView.matches(key) && !cardKey)
            cleanView = !cleanView;
    }

    /**
     * RoN's F1 (HudClientEvents.enabled) and vanilla's hideGui are one switch in the RTS view: InputEvent.Key fires
     * after vanilla handled the key, so if vanilla already flipped hideGui we follow it, otherwise we flip both.
     */
    public static void onF1() {
        if (MC.options.hideGui != lastHideGui) {
            HudClientEvents.enabled = !MC.options.hideGui;
        } else {
            HudClientEvents.enabled = !HudClientEvents.enabled;
            MC.options.hideGui = !HudClientEvents.enabled;
        }
        lastHideGui = MC.options.hideGui;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        // hideGui flipped elsewhere (F1 in the Minecraft POV): the RTS HUD follows it
        if (MC.options.hideGui != lastHideGui) {
            lastHideGui = MC.options.hideGui;
            HudClientEvents.enabled = !lastHideGui;
        }
    }
}
