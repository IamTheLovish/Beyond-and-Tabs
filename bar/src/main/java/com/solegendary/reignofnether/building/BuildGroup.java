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
 * open tab the same twelve grid keys pick a cell, in reading order on a 4-wide grid: Q W E R / A S D F / Z X C V. So
 * the Nth building of every tab is on SLOT_KEYS[N]: keys repeat across tabs and the open tab decides the building.
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
     * The grid keys, by cell: Q W E R / A S D F / Z X C V. A tab's 13th building on is click only (drawn on a fourth
     * row without a key). Live only while a tab is open (BuildTabs.onKey), so they may share keys with the orders.
     */
    public static final List<Keybinding> SLOT_KEYS = List.of(Keybindings.buildGrid);

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
