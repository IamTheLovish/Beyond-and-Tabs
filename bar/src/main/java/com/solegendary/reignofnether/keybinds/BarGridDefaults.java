package com.solegendary.reignofnether.keybinds;

import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

/**
 * BAR's default hotkeys that BAT copies, as data: profile "Grid" of BAR's keybind_defaults.json at commit 002dee75
 * (https://github.com/beyond-all-reason/Beyond-All-Reason/blob/002dee7549c4413a5e70028abfec7bc17894c36c/common/configs/keybind_defaults.json;
 * the binds as a tab-separated copy: src/test/resources/bar-grid-keybinds-002dee75.tsv). No Minecraft types: the
 * tests (BarGridDefaultsTest, CardContextKeysTest, the key GameTests) read it headless. It is the one list that says
 * which BAT default is BAR's: a default that moves must move here too (BarGridDefaultsTest fails otherwise).
 * BAC-007b increment 1: orders and selection. Lane decision (user 10-29: "copy bars hotkeys then build whatever else
 * you need"): everything not a Row is an extra with its reason.
 */
public final class BarGridDefaults {

    private BarGridDefaults() { }

    /**
     * One copied default. {@code bindingOrChord} is a Keybindings translation key, or a ChordKeys.Chord name for a
     * Ctrl chord; {@code tsvLine} is the BAR line, "keyset", a tab, "action", as in the TSV copy.
     */
    public record Row(String bindingOrChord, int glfwKey, boolean ctrl, boolean shift, String tsvLine) { }

    public static final List<Row> ROWS = List.of(
        new Row("key.reignofnether.attack",          GLFW.GLFW_KEY_A,         false, false, "sc_a\tattack"),
        new Row("key.reignofnether.fight",           GLFW.GLFW_KEY_F,         false, false, "sc_f\tfight"),
        new Row("key.reignofnether.stop",            GLFW.GLFW_KEY_G,         false, false, "sc_g\tstop"),
        new Row("key.reignofnether.patrol",          GLFW.GLFW_KEY_H,         false, false, "sc_h\tpatrol"),
        new Row("key.reignofnether.guard",           GLFW.GLFW_KEY_O,         false, false, "sc_o\tguard"),
        // BAT has one Hold state: BAR's movestate 2 (hold position)
        new Row("key.reignofnether.hold",            GLFW.GLFW_KEY_SEMICOLON, false, false, "sc_;\tmovestate 2"),
        // a press cycles the stance like a click on the stance button
        new Row("key.reignofnether.fire_state",      GLFW.GLFW_KEY_L,         false, false, "sc_l\tfirestate 2"),
        new Row("key.reignofnether.select_commander", GLFW.GLFW_KEY_TAB,     false, false, "tab\tselectcomm focus"),
        new Row("key.reignofnether.queue_skip",      GLFW.GLFW_KEY_N,         false, false, "sc_n\tcommand_skip_current"),
        new Row("key.reignofnether.queue_insert",    GLFW.GLFW_KEY_SPACE,     false, false, "Any+space\tcommandinsert prepend_between"),
        // F5 jumps to the last alert (Space is the queue-insert modifier); "." / "," cycle the selected unit types
        new Row("key.reignofnether.last_alert",      GLFW.GLFW_KEY_F5,        false, false, "Any+f5\tlastmsgpos"),
        new Row("key.reignofnether.tab",             GLFW.GLFW_KEY_PERIOD,    false, false, "sc_.\tcycleselected next"),
        new Row("key.reignofnether.cycle_prev",      GLFW.GLFW_KEY_COMMA,     false, false, "sc_comma\tcycleselected prev"),
        new Row("IDLE_BUILDER",                      GLFW.GLFW_KEY_TAB,       true,  false, "Ctrl+tab\tselect AllMap+_Builder_Idle+_ClearSelection_SelectOne+"),
        new Row("SELECT_ALL_UNITS",                  GLFW.GLFW_KEY_E,         true,  false, "Ctrl+sc_e\tselect AllMap++_ClearSelection_SelectAll+"),
        new Row("SELECT_SAME_TYPE",                  GLFW.GLFW_KEY_W,         true,  false, "Ctrl+sc_w\tselect AllMap+_InPrevSel+_ClearSelection_SelectAll+")
    );

    /** The order bindings of {@link #ROWS}: the keys no ability, build tab or other card button may use on a unit card. */
    public static final List<String> ORDERS = List.of(
        "key.reignofnether.attack", "key.reignofnether.fight", "key.reignofnether.stop", "key.reignofnether.patrol",
        "key.reignofnether.guard", "key.reignofnether.hold", "key.reignofnether.fire_state");

    /** True when {@code glfwKey} is the plain (no modifier) key of a BAR order in {@link #ROWS}. */
    public static boolean isOrderKey(int glfwKey) {
        for (Row r : ROWS)
            if (!r.ctrl() && !r.shift() && r.glfwKey() == glfwKey && ORDERS.contains(r.bindingOrChord()))
                return true;
        return false;
    }

    /**
     * Every other binding, and why it is not BAR's. A reason starting "shares:" is a binding that sits on the plain key
     * of a {@link #ROWS} binding on purpose (never on the same card state, or live only in a context that wins, as the
     * tab and grid keys do): BarGridDefaultsTest allows an overlap only with that word.
     */
    public static final Map<String, String> EXTRAS = Map.ofEntries(
        Map.entry("key.reignofnether.ability_1", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_10", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_2", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_3", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_4", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_5", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_6", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_7", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_8", "ability slot, fills by position"),
        Map.entry("key.reignofnether.ability_9", "shares: O, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.build", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.build_grid_1", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_10", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_11", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_12", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_2", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_3", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_4", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_5", "shares: A, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.build_grid_6", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_7", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_grid_8", "shares: F, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.build_grid_9", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_tab_defence", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_tab_economy", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_tab_military", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.build_tab_tech", "BAR gridmenu keys"),
        Map.entry("key.reignofnether.camera_anchor_1", "BAT camera"),
        Map.entry("key.reignofnether.camera_anchor_2", "BAT camera"),
        Map.entry("key.reignofnether.camera_anchor_3", "BAT camera"),
        Map.entry("key.reignofnether.camera_anchor_4", "BAT camera"),
        Map.entry("key.reignofnether.cancel_build", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.chat", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.clean_view", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.command_bar", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.control_group_0", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_1", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_2", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_3", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_4", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_5", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_6", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_7", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_8", "BAR control group / function row"),
        Map.entry("key.reignofnether.control_group_9", "BAR control group / function row"),
        Map.entry("key.reignofnether.cursor_diag", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.deselect", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.fn_1", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_10", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_11", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_12", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_2", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_3", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_4", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_5", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_6", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_7", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_8", "BAR control group / function row"),
        Map.entry("key.reignofnether.fn_9", "BAR control group / function row"),
        Map.entry("key.reignofnether.garrison", "shares: N, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.gather", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.hotkey_1", "build menu slot"),
        Map.entry("key.reignofnether.hotkey_11", "build menu slot"),
        Map.entry("key.reignofnether.hotkey_12", "build menu slot"),
        Map.entry("key.reignofnether.hotkey_2", "shares: L, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.hotkey_3", "shares: F, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.hotkey_4", "build menu slot"),
        Map.entry("key.reignofnether.hotkey_5", "shares: ;, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.hotkey_6", "build menu slot"),
        Map.entry("key.reignofnether.hotkey_7", "build menu slot"),
        Map.entry("key.reignofnether.hotkey_8", "build menu slot"),
        Map.entry("key.reignofnether.hotkey_9", "build menu slot"),
        Map.entry("key.reignofnether.idle_t2_builder", "BAT extra Ctrl+Shift+Tab, no BAR line"),
        Map.entry("key.reignofnether.idle_worker", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.item1", "hero item slot (increment 2)"),
        Map.entry("key.reignofnether.item2", "hero item slot (increment 2)"),
        Map.entry("key.reignofnether.item3", "shares: F, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.item4", "shares: G, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.item5", "hero item slot (increment 2)"),
        Map.entry("key.reignofnether.item6", "hero item slot (increment 2)"),
        Map.entry("key.reignofnether.living_army_toggle", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.minimap_toggle", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.move", "BAR has no move key"),
        Map.entry("key.reignofnether.open_shop", "shares: Space, never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.possess_hop", "shares: Tab, BAT hero, Tab in possession only (the RTS view is not shown then)"),
        Map.entry("key.reignofnether.overcharge", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.overview", "shares: Tab (with Shift), never shown together with or live over the BAR order on the key (see class comment)"),
        Map.entry("key.reignofnether.pan_minus_x", "BAT camera"),
        Map.entry("key.reignofnether.pan_minus_z", "BAT camera"),
        Map.entry("key.reignofnether.pan_plus_x", "BAT camera"),
        Map.entry("key.reignofnether.pan_plus_z", "BAT camera"),
        Map.entry("key.reignofnether.reset", "BAT camera"),
        Map.entry("key.reignofnether.return_resources", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.rot_ccw", "BAT camera"),
        Map.entry("key.reignofnether.rot_cw", "BAT camera"),
        Map.entry("key.reignofnether.rotate_building_ccw", "BAT camera"),
        Map.entry("key.reignofnether.rotate_building_cw", "BAT camera"),
        Map.entry("key.reignofnether.select_army", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.stats_table", "BAT extra, no BAR default in increment 1"),
        Map.entry("key.reignofnether.toggle_camera", "BAT camera"),
        Map.entry("key.reignofnether.toggle_free_fly", "BAT camera"),
        Map.entry("key.reignofnether.zoom_in", "BAT camera"),
        Map.entry("key.reignofnether.zoom_out", "BAT camera")
    );

    /** BAR keys (the TSV line) whose action BAT lacks or keeps for later: the key stays free of other BAT defaults. */
    public static final Map<String, String> DORMANT = Map.ofEntries(
        Map.entry("Shift+tab\tselectcomm append", "BAT has one commander, nothing to append; Shift+Tab is the camera overview"),
        Map.entry("sc_-\tsnd_volume_decrease", "BAT's select-army button keeps -, BAR's sound volume is not copied"),
        Map.entry("sc_d\tmanualfire", "the commander D-gun key, increment 2 (needs lovish)"),
        Map.entry("Ctrl+sc_b\tselfd", "BAR self-destruct: BAT has no such order, B stays Build / item slot 6"),
        Map.entry("Ctrl+Shift+sc_b\tselfd queued", "BAR self-destruct queued: as above"),
        Map.entry("sc_.\tgridmenu_cycle_builder", "BAR cycles builders in the grid menu on the same key as the selection cycle; BAT keeps the one cycle"),
        Map.entry("Ctrl+Shift+esc\tquitforce", "BAR force-quit: Esc belongs to the pause flow (PauseFlow), Minecraft has its own quit"),
        Map.entry("Alt+Shift+esc\treloadforce", "BAR force-reload: not applicable to BAT")
    );
}
