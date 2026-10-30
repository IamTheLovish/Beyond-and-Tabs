package com.solegendary.reignofnether.keybinds;

/**
 * The build menu's key rules, after BAR's gridmenu (github.com/beyond-all-reason/Beyond-All-Reason,
 * luaui/Widgets/gui_gridmenu.lua: gridmenuCategoryHandler / gridmenuKeyHandler; keys in the "Grid" profile of
 * common/configs/keybind_defaults.json). No Minecraft types: BuildCategoryKeysGameTests checks the rules headless;
 * BuildTabs.onKey feeds them the key and the open tab's cells.
 * <ul>
 *   <li>The menu is either at home (no tab open: the tab strip shows the last tab, its buildings click-only, no key
 *       badges) or has a tab open.</li>
 *   <li>At home a tab key (Z / X / C / V) opens its tab (Economy / Military / Defence / Tech). The grid keys are not
 *       live, so S stays Stop, F Fight, X ... the tab key.</li>
 *   <li>With a tab open the grid keys (Q W E R / A S D F / Z X C V) pick the building in their cell. A grid key on an
 *       empty cell does nothing here, so the key keeps its other meaning (BAR: the handler returns false); a tab key
 *       on an empty cell switches tab (BAR would do nothing: it needs the home page first).</li>
 *   <li>Ctrl or Alt held, or no build menu on the card: nothing (BAR: alt / ctrl / meta return early).</li>
 * </ul>
 * BuildTabs closes the tab (home) when the building it picked is placed or dropped, as BAR's CommandNotify does.
 */
public final class BuildCategoryKeys {

    private BuildCategoryKeys() { }

    public enum Action { NONE, OPEN_TAB, PICK_CELL }

    /** What a key press does: an action and its tab ordinal (OPEN_TAB) or cell (PICK_CELL); -1 for NONE. */
    public record Press(Action action, int index) {
        public static final Press NONE = new Press(Action.NONE, -1);
    }

    /**
     * @param menuShown    the card shows the build menu (a builder, the commander, a mixed builder card)
     * @param ctrlOrAlt    Ctrl or Alt held
     * @param open         a tab is open
     * @param tabKey       the tab ordinal whose key this is, or -1
     * @param gridKey      the grid cell whose key this is, or -1
     * @param cellUsable   per cell of the open tab: a visible, enabled building is there
     */
    public static Press press(boolean menuShown, boolean ctrlOrAlt, boolean open, int tabKey, int gridKey,
                              boolean[] cellUsable) {
        if (!menuShown || ctrlOrAlt)
            return Press.NONE;
        if (open && gridKey >= 0 && gridKey < cellUsable.length && cellUsable[gridKey])
            return new Press(Action.PICK_CELL, gridKey);
        if (tabKey >= 0)
            return new Press(Action.OPEN_TAB, tabKey);
        return Press.NONE;
    }

    /** Index of the first entry of {@code keys} equal to {@code key} (GLFW codes; -1 never matches), or -1. */
    public static int indexOf(int key, int[] keys) {
        if (key < 0)
            return -1;
        for (int i = 0; i < keys.length; i++)
            if (keys[i] == key)
                return i;
        return -1;
    }

    /**
     * The button a grid key picks in one tab: the index (into the tab's buttons) of the usable button whose cell is
     * {@code cell}, or -1. {@code cells} is BuilderCardUnion.tabCells of the tab, so it is the selected tab that decides
     * which building a key means.
     */
    public static int buttonAt(int cell, int[] cells, boolean[] usable) {
        for (int i = 0; i < cells.length; i++)
            if (cells[i] == cell && usable[i])
                return i;
        return -1;
    }

    /** Per cell 0..cellCount-1: whether a usable button sits there. */
    public static boolean[] usableCells(int cellCount, int[] cells, boolean[] usable) {
        boolean[] out = new boolean[cellCount];
        for (int i = 0; i < cells.length; i++)
            if (usable[i] && cells[i] >= 0 && cells[i] < cellCount)
                out[cells[i]] = true;
        return out;
    }
}
