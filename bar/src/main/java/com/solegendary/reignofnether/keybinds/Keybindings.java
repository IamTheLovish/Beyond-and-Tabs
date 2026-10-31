package com.solegendary.reignofnether.keybinds;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Beyond and Tabs default rebinds (all still rebindable in Controls): WASD now pans the camera, so the
// RoN defaults that sat on W/A/S/D moved: ability slot 2 W->Z, open shop S->Space (Stop is back on S as a tap, see
// the order keys below; Attack moved to X on Oct 21),
// return resources (hotkey 5) D->;, observer/diplomacy toggle (hotkey 8) Z->., hotkey 9 X->,
// A changed default does NOT reach an install that already saved the old one (Minecraft keeps every mapping in
// options.txt): add the old key to KeybindMigration.STALE and bump its LAYOUT_VERSION whenever a default moves.
public class Keybindings {

    public static final String CATEGORY_CAMERA = "key.categories.reignofnether.camera";
    public static final String CATEGORY_RTS    = "key.categories.reignofnether.rts";

    // Raw GLFW bindings (not rebindable, not surfaced in vanilla Controls UI).
    // - pause: ESC must stay raw or it fights vanilla's pause screen
    // - shiftMod/ctrlMod/altMod: held-state qualifiers, not action keys
    public static final Keybinding pause    = new Keybinding(GLFW.GLFW_KEY_ESCAPE,        "ESC", "Pause");
    public static final Keybinding shiftMod = new Keybinding(GLFW.GLFW_KEY_LEFT_SHIFT,    "",    "Shift");
    public static final Keybinding ctrlMod  = new Keybinding(GLFW.GLFW_KEY_LEFT_CONTROL,  "LC",  "Ctrl");
    public static final Keybinding altMod   = new Keybinding(GLFW.GLFW_KEY_LEFT_ALT,      "ALT", "Alt");

    // Camera category ("Beyond and Tabs - Camera"): BAR's Spring camera. Shift never touches the camera - in BAR it
    // only queues orders (lovish: Shift+drag lines swung the view). Rotation/tilt is Alt + middle-drag, Alt + pan
    // keys and Ctrl + wheel. Full table: design/camera.md.
    // Zoom: Spring's PageUp / PageDown, the old numpad +/- kept as aliases.
    public static final Keybinding zoomIn    = new Keybinding("key.reignofnether.zoom_in",     GLFW.GLFW_KEY_PAGE_UP,     CATEGORY_CAMERA, "PgU").withAlias(GLFW.GLFW_KEY_KP_ADD);
    public static final Keybinding zoomOut   = new Keybinding("key.reignofnether.zoom_out",    GLFW.GLFW_KEY_PAGE_DOWN,   CATEGORY_CAMERA, "PgD").withAlias(GLFW.GLFW_KEY_KP_SUBTRACT);
    public static final Keybinding panPlusX  = new Keybinding("key.reignofnether.pan_plus_x",  GLFW.GLFW_KEY_LEFT,        CATEGORY_CAMERA, RtsConflictContext.PAN,    "X+");
    public static final Keybinding panMinusX = new Keybinding("key.reignofnether.pan_minus_x", GLFW.GLFW_KEY_RIGHT,       CATEGORY_CAMERA, RtsConflictContext.PAN,    "X-");
    public static final Keybinding panPlusZ  = new Keybinding("key.reignofnether.pan_plus_z",  GLFW.GLFW_KEY_UP,          CATEGORY_CAMERA, RtsConflictContext.PAN,    "Z+");
    public static final Keybinding panMinusZ = new Keybinding("key.reignofnether.pan_minus_z", GLFW.GLFW_KEY_DOWN,        CATEGORY_CAMERA, RtsConflictContext.PAN,    "Z-");
    // WASD panning (60% boards have no arrows). Alt + a pan key rotates / tilts (Spring's "movetilt" modifier).
    public static final Keybinding panUp     = new Keybinding("key.reignofnether.pan_up",      GLFW.GLFW_KEY_W,           CATEGORY_CAMERA, RtsConflictContext.PAN,    "W");
    public static final Keybinding panLeft   = new Keybinding("key.reignofnether.pan_left",    GLFW.GLFW_KEY_A,           CATEGORY_CAMERA, RtsConflictContext.PAN,    "A");
    public static final Keybinding panDown   = new Keybinding("key.reignofnether.pan_down",    GLFW.GLFW_KEY_S,           CATEGORY_CAMERA, RtsConflictContext.PAN,    "S");
    public static final Keybinding panRight  = new Keybinding("key.reignofnether.pan_right",   GLFW.GLFW_KEY_D,           CATEGORY_CAMERA, RtsConflictContext.PAN,    "D");
    // 90-degree snaps (the minimap's rotate buttons)
    public static final Keybinding rotCW     = new Keybinding("key.reignofnether.rot_cw",      GLFW.GLFW_KEY_RIGHT,       CATEGORY_CAMERA, RtsConflictContext.ROTATE, "");
    public static final Keybinding rotCCW    = new Keybinding("key.reignofnether.rot_ccw",     GLFW.GLFW_KEY_LEFT,        CATEGORY_CAMERA, RtsConflictContext.ROTATE, "");
    // back to the default angle (the diagonal the diamond minimap is drawn for), default zoom and tilt
    public static final Keybinding reset     = new Keybinding("key.reignofnether.reset",       GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY_CAMERA, "RC");
    // BAR's Tab overview: zoom out to the whole map; Tab again, a left click or releasing a held Tab flies back in at
    // the cursor. Own conflict context: Ctrl+Tab still cycles the selection (tab, below) on the same key.
    public static final Keybinding overview  = new Keybinding("key.reignofnether.overview",    GLFW.GLFW_KEY_TAB,         CATEGORY_CAMERA, RtsConflictContext.OVERVIEW, "Tab");
    // BAR camera anchors: press to fly back (position, zoom, rotation, tilt), Ctrl+press to store; kept per world for
    // the session (OrthoviewClientEvents). BAR's widget puts them on F1-F4, which here stay hide HUD (F1, vanilla's
    // hideGui too), screenshot (F2), debug / block info (F3) and the stats-table alias (F4), so they ship on F5-F8
    // (tester request, Oct 26): F5 is vanilla's perspective key, idle in the RTS view; F6 / F7 answer an open resign
    // vote and F8 cycles a spectator's view - while those are live the anchor on the key stays quiet
    // (KeybindsClientEvents.fKeyOwner). Own conflict context, so Controls does not flag fn_5..fn_8 on the same keys.
    // Layout 3 (KeybindMigration) moves a saved "unbound" from the older builds onto F5-F8.
    public static final Keybinding[] cameraAnchors = {
        new Keybinding("key.reignofnether.camera_anchor_1", GLFW.GLFW_KEY_F5, CATEGORY_CAMERA, RtsConflictContext.CAMERA_ANCHOR, "F5"),
        new Keybinding("key.reignofnether.camera_anchor_2", GLFW.GLFW_KEY_F6, CATEGORY_CAMERA, RtsConflictContext.CAMERA_ANCHOR, "F6"),
        new Keybinding("key.reignofnether.camera_anchor_3", GLFW.GLFW_KEY_F7, CATEGORY_CAMERA, RtsConflictContext.CAMERA_ANCHOR, "F7"),
        new Keybinding("key.reignofnether.camera_anchor_4", GLFW.GLFW_KEY_F8, CATEGORY_CAMERA, RtsConflictContext.CAMERA_ANCHOR, "F8")
    };
    // BAR rotates a building ghost with [ and ] (the wheel now always zooms). "[" is also hotkey11's build slot:
    // while a ghost is out the rotation wins (HudClientEvents.onKeyRelease), so its own context avoids a red
    // conflict in Controls.
    public static final Keybinding rotateBuildingCCW = new Keybinding("key.reignofnether.rotate_building_ccw", GLFW.GLFW_KEY_LEFT_BRACKET,  CATEGORY_CAMERA, RtsConflictContext.BUILD_GHOST, "[");
    public static final Keybinding rotateBuildingCW  = new Keybinding("key.reignofnether.rotate_building_cw",  GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY_CAMERA, RtsConflictContext.BUILD_GHOST, "]");

    // RTS category
    public static final Keybinding cancelBuild = new Keybinding("key.reignofnether.cancel_build", GLFW.GLFW_KEY_DELETE,        CATEGORY_RTS, "DEL");
    public static final Keybinding abilitySlot1        = new Keybinding("key.reignofnether.ability_1",    GLFW.GLFW_KEY_Q,             CATEGORY_RTS, "Q");
    public static final Keybinding abilitySlot2        = new Keybinding("key.reignofnether.ability_2",    GLFW.GLFW_KEY_Z,             CATEGORY_RTS, "Z");
    public static final Keybinding abilitySlot3        = new Keybinding("key.reignofnether.ability_3",    GLFW.GLFW_KEY_E,             CATEGORY_RTS, "E");
    public static final Keybinding abilitySlot4        = new Keybinding("key.reignofnether.ability_4",    GLFW.GLFW_KEY_R,             CATEGORY_RTS, "R");
    public static final Keybinding abilitySlot5        = new Keybinding("key.reignofnether.ability_5",    GLFW.GLFW_KEY_T,             CATEGORY_RTS, "T");
    public static final Keybinding abilitySlot6        = new Keybinding("key.reignofnether.ability_6",    GLFW.GLFW_KEY_Y,             CATEGORY_RTS, "Y");
    public static final Keybinding abilitySlot7        = new Keybinding("key.reignofnether.ability_7",    GLFW.GLFW_KEY_U,             CATEGORY_RTS, "U");
    public static final Keybinding abilitySlot8        = new Keybinding("key.reignofnether.ability_8",    GLFW.GLFW_KEY_I,             CATEGORY_RTS, "I");
    public static final Keybinding abilitySlot9        = new Keybinding("key.reignofnether.ability_9",    GLFW.GLFW_KEY_O,             CATEGORY_RTS, "O");
    public static final Keybinding abilitySlot10       = new Keybinding("key.reignofnether.ability_10",   GLFW.GLFW_KEY_P,             CATEGORY_RTS, "P");
    // BAR's gridmenu (luaui/Widgets/gui_gridmenu.lua, keybind_defaults.json "Grid" profile): with the build menu on the
    // card and no tab open, Z / X / C / V open the Economy / Military / Defence / Tech tab (BAR's gridmenu_category 1-4)
    // instead of ability slot 2, Attack, hotkey 4 and hotkey 1. They share the keys on purpose: own conflict context.
    public static final Keybinding buildTabEconomy  = new Keybinding("key.reignofnether.build_tab_economy",  GLFW.GLFW_KEY_Z, CATEGORY_RTS, RtsConflictContext.BUILDER, "Z");
    public static final Keybinding buildTabMilitary = new Keybinding("key.reignofnether.build_tab_military", GLFW.GLFW_KEY_X, CATEGORY_RTS, RtsConflictContext.BUILDER, "X");
    public static final Keybinding buildTabDefence  = new Keybinding("key.reignofnether.build_tab_defence",  GLFW.GLFW_KEY_C, CATEGORY_RTS, RtsConflictContext.BUILDER, "C");
    public static final Keybinding buildTabTech     = new Keybinding("key.reignofnether.build_tab_tech",     GLFW.GLFW_KEY_V, CATEGORY_RTS, RtsConflictContext.BUILDER, "V");
    // BAR's grid keys (gridmenu_key row col): the same twelve keys in every tab, in reading order, one per cell of the
    // 4-wide build grid - Q W E R / A S D F / Z X C V. Live only while a tab is open (BuildTabs.onKey); then they win
    // over the orders on the same keys (Stop S, Attack X, Fight F), and on W / A / S / D a tap picks, a hold pans.
    public static final Keybinding[] buildGrid = {
        new Keybinding("key.reignofnether.build_grid_1",  GLFW.GLFW_KEY_Q, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "Q"),
        new Keybinding("key.reignofnether.build_grid_2",  GLFW.GLFW_KEY_W, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "W"),
        new Keybinding("key.reignofnether.build_grid_3",  GLFW.GLFW_KEY_E, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "E"),
        new Keybinding("key.reignofnether.build_grid_4",  GLFW.GLFW_KEY_R, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "R"),
        new Keybinding("key.reignofnether.build_grid_5",  GLFW.GLFW_KEY_A, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "A"),
        new Keybinding("key.reignofnether.build_grid_6",  GLFW.GLFW_KEY_S, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "S"),
        new Keybinding("key.reignofnether.build_grid_7",  GLFW.GLFW_KEY_D, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "D"),
        new Keybinding("key.reignofnether.build_grid_8",  GLFW.GLFW_KEY_F, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "F"),
        new Keybinding("key.reignofnether.build_grid_9",  GLFW.GLFW_KEY_Z, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "Z"),
        new Keybinding("key.reignofnether.build_grid_10", GLFW.GLFW_KEY_X, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "X"),
        new Keybinding("key.reignofnether.build_grid_11", GLFW.GLFW_KEY_C, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "C"),
        new Keybinding("key.reignofnether.build_grid_12", GLFW.GLFW_KEY_V, CATEGORY_RTS, RtsConflictContext.BUILD_GRID, "V")
    };
    public static final Keybinding hotkey1        = new Keybinding("key.reignofnether.hotkey_1",        GLFW.GLFW_KEY_V,             CATEGORY_RTS, "V");
    public static final Keybinding hotkey2        = new Keybinding("key.reignofnether.hotkey_2",        GLFW.GLFW_KEY_L,             CATEGORY_RTS, "L");
    public static final Keybinding minimapToggle  = new Keybinding("key.reignofnether.minimap_toggle",        GLFW.GLFW_KEY_M,             CATEGORY_RTS, "M");
    public static final Keybinding hotkey3        = new Keybinding("key.reignofnether.hotkey_3",        GLFW.GLFW_KEY_F,             CATEGORY_RTS, "F");
    public static final Keybinding hotkey4        = new Keybinding("key.reignofnether.hotkey_4",        GLFW.GLFW_KEY_C,             CATEGORY_RTS, "C");
    public static final Keybinding hotkey5        = new Keybinding("key.reignofnether.hotkey_5",        GLFW.GLFW_KEY_SEMICOLON,     CATEGORY_RTS, ";");
    public static final Keybinding hotkey6        = new Keybinding("key.reignofnether.hotkey_6",        GLFW.GLFW_KEY_J,             CATEGORY_RTS, "J");
    public static final Keybinding hotkey7        = new Keybinding("key.reignofnether.hotkey_7",        GLFW.GLFW_KEY_K,             CATEGORY_RTS, "K");
    public static final Keybinding hotkey8        = new Keybinding("key.reignofnether.hotkey_8",        GLFW.GLFW_KEY_PERIOD,        CATEGORY_RTS, ".");
    public static final Keybinding hotkey9        = new Keybinding("key.reignofnether.hotkey_9",        GLFW.GLFW_KEY_COMMA,         CATEGORY_RTS, ",");
    public static final Keybinding hotkey10       = new Keybinding("key.reignofnether.hotkey_10",       GLFW.GLFW_KEY_H,             CATEGORY_RTS, "H");
    // BAR's idle-constructor cycle. "." is on a 60% board and free of the orders card except the storage vault's
    // build slot (hotkey8): with a worker selected that button wins (HudClientEvents.onKeyRelease); F10 always cycles.
    public static final Keybinding idleWorker     = new Keybinding("key.reignofnether.idle_worker",     GLFW.GLFW_KEY_PERIOD,        CATEGORY_RTS, ".");
    // The idle T2 builder and commander buttons beside it run on fixed Ctrl chords (ChordKeys, BAR's Legacy profile):
    // Ctrl+C selects the commander and centres on it (selectcomm focus), Ctrl+Shift+B cycles the idle T2 builders
    // (Ctrl+B, BAR's idle builder, is the idle-worker cycle). They used to sit on Home / End, which the production
    // monitor (Home) and pause unit production (End, #228) took, so their own keys start unbound (Controls to add one;
    // KeybindMigration layout 2 frees a saved Home / End). The button corner shows the chord while unbound.
    public static final Keybinding idleT2Builder   = new Keybinding("key.reignofnether.idle_t2_builder",   GLFW.GLFW_KEY_UNKNOWN, CATEGORY_RTS, "").withChord(ChordKeys.Chord.IDLE_T2_BUILDER);
    public static final Keybinding selectCommander = new Keybinding("key.reignofnether.select_commander", GLFW.GLFW_KEY_UNKNOWN, CATEGORY_RTS, "").withChord(ChordKeys.Chord.SELECT_COMMANDER);
    // "Select all military units" had build slot 7 (K) as its key, so K both picked a building and selected the army
    // (Lane C tester, Oct 21: "k is for a building and for selecting all combat units"). Its own key now; every letter
    // is already a default, so "-" beside the control-group numbers. KeybindGameTests keeps it off the card slots.
    public static final Keybinding selectArmy     = new Keybinding("key.reignofnether.select_army",     GLFW.GLFW_KEY_MINUS,         CATEGORY_RTS, "-");
    // The worker's "Return resources" button was on hotkey5 (;), the metal extractor's build slot on all seven factions
    // (Lane C tester, Oct 21: the extractor key does nothing). It has its own key now: "]" is on no worker card (every
    // letter, the punctuation slots and the abilities are used), and while a building ghost is out "]" rotates it
    // (BuildingClientEvents) before the HUD sees the key. ReturnResourcesKeyGameTests checks this key against each faction's worker.
    public static final Keybinding returnResources = new Keybinding("key.reignofnether.return_resources", GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY_RTS, "]");
    // Oct 21 (lovish: the order on A blocks the WASD camera): Attack moved off A to X so A is pure pan-left. Fight stays
    // on F. S (Stop) still shares the tap/hold rule below. Installs that saved the old A were moved to X once
    // (KeybindMigration, layout 1). Oct 26: kept on X (BAR Legacy has A, but #39 took it off A on purpose: a hold of A
    // waited before panning).
    // BAR's order keys on the command card (lovish: "I can't use formation when using attack command, which I don't
    // even see an attack command" - the card read X / B / G / N / Y / P / "Spc"). X attack, S stop, M move, H hold,
    // P patrol, G guard (BAR's Attack is A, which is camera pan-left here). S is also WASD panning: a TAP
    // (< KeyTaps.TAP_MS, lag allowed for) fires the order when the card has one on that key, a hold pans (KeyTaps,
    // OrthoviewClientEvents). Space jumps to the last alert (lastAlert), as in BAR.
    public static final Keybinding attack      = new Keybinding("key.reignofnether.attack",       GLFW.GLFW_KEY_X,             CATEGORY_RTS, RtsConflictContext.COMMAND, "X");
    // BAR's Fight (F): attack-move, armed - a click fights to the spot, a drag lays a fight line (lovish, Oct 14 02:38:
    // "add a fight command on F"). Fighters only: a worker's F is its build menu slot (hotkey3) and the commander is both.
    public static final Keybinding fight       = new Keybinding("key.reignofnether.fight",        GLFW.GLFW_KEY_F,             CATEGORY_RTS, RtsConflictContext.COMMAND, "F");
    public static final Keybinding stop        = new Keybinding("key.reignofnether.stop",         GLFW.GLFW_KEY_S,             CATEGORY_RTS, RtsConflictContext.COMMAND, "S");
    public static final Keybinding move        = new Keybinding("key.reignofnether.move",         GLFW.GLFW_KEY_M,             CATEGORY_RTS, RtsConflictContext.COMMAND, "M");
    public static final Keybinding hold        = new Keybinding("key.reignofnether.hold",         GLFW.GLFW_KEY_H,             CATEGORY_RTS, RtsConflictContext.COMMAND, "H");
    public static final Keybinding build       = new Keybinding("key.reignofnether.build",        GLFW.GLFW_KEY_B,             CATEGORY_RTS, "B");
    // the gather-target toggle lost its key to Guard (G, BAR): it is a click toggle in a metal/energy economy
    public static final Keybinding gather      = new Keybinding("key.reignofnether.gather",       GLFW.GLFW_KEY_UNKNOWN,       CATEGORY_RTS, "");
    public static final Keybinding patrol      = new Keybinding("key.reignofnether.patrol",       GLFW.GLFW_KEY_P,             CATEGORY_RTS, RtsConflictContext.COMMAND, "P");
    public static final Keybinding guard       = new Keybinding("key.reignofnether.guard",        GLFW.GLFW_KEY_G,             CATEGORY_RTS, RtsConflictContext.COMMAND, "G");
    public static final Keybinding garrison    = new Keybinding("key.reignofnether.garrison",     GLFW.GLFW_KEY_N,             CATEGORY_RTS, "N");
    // BAR: jump the camera to the last "under attack" alert (AttackWarningClientEvents.jumpToLastAlert)
    public static final Keybinding lastAlert   = new Keybinding("key.reignofnether.last_alert",   GLFW.GLFW_KEY_SPACE,         CATEGORY_RTS, "Spc");
    // a build-menu slot clear of every order key: the Library / Laboratory used P (abilitySlot10), now Patrol's
    public static final Keybinding hotkey11    = new Keybinding("key.reignofnether.hotkey_11",    GLFW.GLFW_KEY_LEFT_BRACKET,  CATEGORY_RTS, "[");
    // and one for the Royal Architect's Holy Bell, whose default slot (Z) is the Kingdom's Oak Stockpile
    public static final Keybinding hotkey12    = new Keybinding("key.reignofnether.hotkey_12",    GLFW.GLFW_KEY_APOSTROPHE,    CATEGORY_RTS, "'");
    public static final Keybinding chat        = new Keybinding("key.reignofnether.chat",         GLFW.GLFW_KEY_ENTER,         CATEGORY_RTS, "");
    // the command bar (claude/voice-commands.md layer 2): "/" opens the chat with a ">" prompt; a line that starts
    // with ">" is an order in the command language, parsed and executed on the client, never sent as chat
    public static final Keybinding commandBar  = new Keybinding("key.reignofnether.command_bar",  GLFW.GLFW_KEY_SLASH,         CATEGORY_RTS, "/");
    public static final Keybinding deselect    = new Keybinding("key.reignofnether.deselect",     GLFW.GLFW_KEY_GRAVE_ACCENT,  CATEGORY_RTS, "~");
    // Ctrl+Tab cycles the selection's unit types (Shift reverses): plain Tab is BAR's overview (overview, above)
    public static final Keybinding tab         = new Keybinding("key.reignofnether.tab",          GLFW.GLFW_KEY_TAB,           CATEGORY_RTS, "");
    // Stormcallers Overcharge toggle (ManaClientEvents; ignored for every other faction). Backslash: every letter
    // near the left hand is taken by abilities, hotkeys and WASD panning
    public static final Keybinding overcharge  = new Keybinding("key.reignofnether.overcharge",   GLFW.GLFW_KEY_BACKSLASH,     CATEGORY_RTS, "\\");
    // HUD declutter (design/hud.md): I opens / closes the tabbed stats table that replaced the always-on player list
    // (ZeroSpace's "INCOME [I]"; F4 stays as a legacy alias for anyone with an F-row). I is ability slot 8 too: a
    // selected card with a button on I keeps it (HudClientEvents.isCardKey), like M for the minimap.
    public static final Keybinding statsTable  = new Keybinding("key.reignofnether.stats_table",  GLFW.GLFW_KEY_I,             CATEGORY_RTS, RtsConflictContext.HUD_PANEL, "I").withAlias(GLFW.GLFW_KEY_F4);
    // Clean view: hides everything but the economy bar, the minimap and the command card. "=" is on every 60% board
    // without Fn, unbound by vanilla and by every card (letters are all taken; backslash is Overcharge)
    public static final Keybinding cleanView   = new Keybinding("key.reignofnether.clean_view",   GLFW.GLFW_KEY_EQUAL,         CATEGORY_RTS, "=");
    public static final Keybinding openShop    = new Keybinding("key.reignofnether.open_shop",         GLFW.GLFW_KEY_SPACE,         CATEGORY_RTS, "SPC");
    // RTS camera <-> Minecraft POV toggle. RoN hard-wired this to F12, which a 60% keyboard doesn't have (lovish's
    // clip: "minecraft pov doesn't work on my 60 keyboard"). Backspace is on every board, unused by vanilla and by
    // every hotkey above (every letter is taken by abilities, hotkeys and WASD panning). F12 still works as a legacy
    // alias (OrthoviewClientEvents.onInput) so nobody's muscle memory breaks.
    public static final Keybinding toggleCamera = new Keybinding("key.reignofnether.toggle_camera", GLFW.GLFW_KEY_BACKSPACE, CATEGORY_CAMERA, "BSP");

    // Spectators only (match.Spectators, orthoview.SpectatorFreeFly): RTS view <-> free flight as a vanilla spectator. Caps Lock:
    // on every 60% board, unbound by vanilla and by every hotkey (all the letters are taken; A must stay free for
    // camera pan-left and F is Fight). Rebindable in Controls like every other key.
    public static final Keybinding toggleFreeFly = new Keybinding("key.reignofnether.toggle_free_fly", GLFW.GLFW_KEY_CAPS_LOCK, CATEGORY_CAMERA, "Cap");

    public static final Keybinding item1 = new Keybinding("key.reignofnether.item1", GLFW.GLFW_KEY_T, CATEGORY_RTS, "T");
    public static final Keybinding item2 = new Keybinding("key.reignofnether.item2", GLFW.GLFW_KEY_Y, CATEGORY_RTS, "Y");
    public static final Keybinding item3 = new Keybinding("key.reignofnether.item3", GLFW.GLFW_KEY_F, CATEGORY_RTS, "F");
    public static final Keybinding item4 = new Keybinding("key.reignofnether.item4", GLFW.GLFW_KEY_G, CATEGORY_RTS, "G");
    public static final Keybinding item5 = new Keybinding("key.reignofnether.item5", GLFW.GLFW_KEY_C, CATEGORY_RTS, "C");
    public static final Keybinding item6 = new Keybinding("key.reignofnether.item6", GLFW.GLFW_KEY_B, CATEGORY_RTS, "B");

    // cursor diagnostics overlay (cursor/CursorDiagClient); no default key - set one in Controls, or use /rts-cursordiag
    public static final Keybinding cursorDiag = new Keybinding("key.reignofnether.cursor_diag", GLFW.GLFW_KEY_UNKNOWN, CATEGORY_CAMERA, "");

    // BAR queue editing (unit/command/QueueEditKeys, Legacy layout): N skips the current order (Ctrl+N cancels the
    // last one); N stays the card's Garrison while nothing selected has an order waiting. Space held while giving an
    // order inserts it at the front of the queue; a plain Space tap keeps lastAlert / openShop.
    public static final Keybinding queueSkip   = new Keybinding("key.reignofnether.queue_skip",   GLFW.GLFW_KEY_N,             CATEGORY_RTS, "N");
    public static final Keybinding queueInsert = new Keybinding("key.reignofnether.queue_insert", GLFW.GLFW_KEY_SPACE,         CATEGORY_RTS, "Spc");

    // the living army's A/B switch for the parade ground (unit/modelling/motion/LivingArmyToggle; F8 is fog/spectator)
    public static final Keybinding livingArmyToggle = new Keybinding("key.reignofnether.living_army_toggle", GLFW.GLFW_KEY_KP_0, CATEGORY_CAMERA, "N0");

    public static final Keybinding[] nums = {
        new Keybinding("key.reignofnether.control_group_0", GLFW.GLFW_KEY_0, CATEGORY_RTS, "0"),
        new Keybinding("key.reignofnether.control_group_1", GLFW.GLFW_KEY_1, CATEGORY_RTS, "1"),
        new Keybinding("key.reignofnether.control_group_2", GLFW.GLFW_KEY_2, CATEGORY_RTS, "2"),
        new Keybinding("key.reignofnether.control_group_3", GLFW.GLFW_KEY_3, CATEGORY_RTS, "3"),
        new Keybinding("key.reignofnether.control_group_4", GLFW.GLFW_KEY_4, CATEGORY_RTS, "4"),
        new Keybinding("key.reignofnether.control_group_5", GLFW.GLFW_KEY_5, CATEGORY_RTS, "5"),
        new Keybinding("key.reignofnether.control_group_6", GLFW.GLFW_KEY_6, CATEGORY_RTS, "6"),
        new Keybinding("key.reignofnether.control_group_7", GLFW.GLFW_KEY_7, CATEGORY_RTS, "7"),
        new Keybinding("key.reignofnether.control_group_8", GLFW.GLFW_KEY_8, CATEGORY_RTS, "8"),
        new Keybinding("key.reignofnether.control_group_9", GLFW.GLFW_KEY_9, CATEGORY_RTS, "9")
    };
    public static final Keybinding[] fnums = {
        new Keybinding("key.reignofnether.fn_1",  GLFW.GLFW_KEY_F1,  CATEGORY_RTS, "F1"),
        new Keybinding("key.reignofnether.fn_2",  GLFW.GLFW_KEY_F2,  CATEGORY_RTS, "F2"),
        new Keybinding("key.reignofnether.fn_3",  GLFW.GLFW_KEY_F3,  CATEGORY_RTS, "F3"),
        new Keybinding("key.reignofnether.fn_4",  GLFW.GLFW_KEY_F4,  CATEGORY_RTS, "F4"),
        new Keybinding("key.reignofnether.fn_5",  GLFW.GLFW_KEY_F5,  CATEGORY_RTS, "F5"),
        new Keybinding("key.reignofnether.fn_6",  GLFW.GLFW_KEY_F6,  CATEGORY_RTS, "F6"),
        new Keybinding("key.reignofnether.fn_7",  GLFW.GLFW_KEY_F7,  CATEGORY_RTS, "F7"),
        new Keybinding("key.reignofnether.fn_8",  GLFW.GLFW_KEY_F8,  CATEGORY_RTS, "F8"),
        new Keybinding("key.reignofnether.fn_9",  GLFW.GLFW_KEY_F9,  CATEGORY_RTS, "F9"),
        new Keybinding("key.reignofnether.fn_10", GLFW.GLFW_KEY_F10, CATEGORY_RTS, "F10"),
        new Keybinding("key.reignofnether.fn_11", GLFW.GLFW_KEY_F11, CATEGORY_RTS, "F11"),
        new Keybinding("key.reignofnether.fn_12", GLFW.GLFW_KEY_F12, CATEGORY_RTS, "F12")
    };

    private static final List<Keybinding> ALL;
    static {
        List<Keybinding> list = new ArrayList<>(List.of(
            pause, shiftMod, ctrlMod, altMod,
            zoomIn, zoomOut,
            panPlusX, panMinusX, panPlusZ, panMinusZ,
            panUp, panLeft, panDown, panRight,
            rotCW, rotCCW, reset, toggleCamera, toggleFreeFly, overview,
            cameraAnchors[0], cameraAnchors[1], cameraAnchors[2], cameraAnchors[3],
            rotateBuildingCCW, rotateBuildingCW,
            cancelBuild,
            abilitySlot1, abilitySlot2, abilitySlot3, abilitySlot4, abilitySlot5,
            abilitySlot6, abilitySlot7, abilitySlot8, abilitySlot9, abilitySlot10,
            buildTabEconomy, buildTabMilitary, buildTabDefence, buildTabTech,
            hotkey1, hotkey2, minimapToggle, hotkey3, hotkey4, hotkey5, hotkey6, hotkey7, hotkey8, hotkey9, idleWorker, idleT2Builder, selectCommander, selectArmy, returnResources,
            attack, fight, stop, move, hold, patrol, guard, lastAlert, hotkey11, hotkey12,
            build, gather, garrison,
            chat, commandBar, deselect, tab, openShop, overcharge, statsTable, cleanView,
            item1, item2, item3, item4, item5, item6,
            cursorDiag,
            queueSkip, queueInsert, livingArmyToggle
        ));
        Collections.addAll(list, buildGrid);
        Collections.addAll(list, nums);
        Collections.addAll(list, fnums);
        ALL = Collections.unmodifiableList(list);
    }

    public static List<Keybinding> all() {
        return ALL;
    }

    public static Keybinding getNum(int i) {
        return nums[i];
    }
    public static Keybinding getFnum(int i) {
        return fnums[i-1];
    }
}
