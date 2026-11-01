package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;

import java.util.List;

/**
 * The four tabs of a worker's build menu (design/mockups/mockup_a_tabs.png, groups in design/mockups/groups.md).
 * Every building registered with Factions.registerBuildings names its tab.
 * <p>
 * Keys follow BAR's gridmenu (github.com/beyond-all-reason/Beyond-All-Reason, luaui/Widgets/gui_gridmenu.lua and the
 * "Grid" profile of common/configs/keybind_defaults.json): Z / X / C / V open a tab (BAR's categories), and inside an
 * open tab the same twelve grid keys pick a cell on a 4-wide grid (Q W E R / A S D F / Z X C V), filled from the bottom
 * row up as BAR's gridmenu row 1 is the bottom row (gui_gridmenu.lua:869-872). So the Nth building of every tab is on
 * SLOT_KEYS[N]: keys repeat across tabs and the open tab decides the building.
 */
public enum BuildGroup {
    ECONOMY("economy"),
    MILITARY("military"),
    DEFENCE("defence"),
    TECH("tech");

    /** Columns of the build grid (BAR: 4), so each key row is a grid row. */
    public static final int COLS = 4;

    /** Tab keys, by ordinal: Z Economy, X Military, C Defence, V Tech (BAR's gridmenu_category 1-4). */
    public static final List<Keybinding> TAB_KEYS = List.of(
        Keybindings.buildTabEconomy, Keybindings.buildTabMilitary, Keybindings.buildTabDefence, Keybindings.buildTabTech);

    /**
     * The grid keys, by fill order (bottom row first): Z X C V / A S D F / Q W E R. A tab's 13th building on is click only (drawn on a fourth
     * row without a key). Live only while a tab is open (BuildTabs.onKey), so they may share keys with the orders.
     */
    public static final List<Keybinding> SLOT_KEYS = List.of(
        Keybindings.buildGrid[8], Keybindings.buildGrid[9], Keybindings.buildGrid[10], Keybindings.buildGrid[11],
        Keybindings.buildGrid[4], Keybindings.buildGrid[5], Keybindings.buildGrid[6], Keybindings.buildGrid[7],
        Keybindings.buildGrid[0], Keybindings.buildGrid[1], Keybindings.buildGrid[2], Keybindings.buildGrid[3]);

    /** Row (0 = top) the cell is drawn on in a grid of the given rows: anchored at the bottom, so slot 0 is on the last row. */
    public static int gridRow(int cell, int rows) {
        return rows - 1 - cell / COLS;
    }

    public final String id;

    BuildGroup(String id) {
        this.id = id;
    }

    /** Lang key of the tab's title. */
    public String langKey() {
        return "hud.reignofnether.build_tab." + id;
    }

    /** The key of the nth cell of a tab, or null past the last (click only). */
    public static Keybinding slotKey(int index) {
        return index >= 0 && index < SLOT_KEYS.size() ? SLOT_KEYS.get(index) : null;
    }
}
