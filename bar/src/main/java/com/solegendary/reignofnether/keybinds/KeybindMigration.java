package com.solegendary.reignofnether.keybinds;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Moves saved key bindings to the defaults we changed, once per layout version. No Minecraft types: the rule is
 * unit-tested (KeybindMigrationTest); KeybindsClientEvents applies it to the real options.
 *
 * Why it exists: Minecraft writes EVERY key mapping to options.txt, defaults included. An install that played before
 * a default changed keeps the old one for ever, so the new default never reaches it. Found Oct 21 2026: #39 moved
 * Attack from A to X so A is pure camera pan-left, but lovish's card still read "A" (playtest clips) - his options.txt
 * held key_key.reignofnether.attack:key.keyboard.a from before the change, and "the attack click is bugged with WASD".
 *
 * The rule: a binding whose saved key is still the OLD default of a {@link Stale} row moves to its current default; a
 * key the player chose (anything else - another letter, a modifier, a mouse button, unbound) is never touched. The
 * installed layout version lives in the client config (keybind_layout), so a player who goes back to the old key
 * afterwards keeps it. A player who had picked the old default on purpose before the first run can't be told from one
 * who never changed it; the toast names every move so it is easy to undo in Controls.
 *
 * To move another default later: change the binding's default in {@link Keybindings}, bump {@link #LAYOUT_VERSION}
 * and add a row here with the key the OLD builds shipped (the target is read from Keybindings, never typed twice).
 */
public final class KeybindMigration {

    /** The key layout the saved bindings are moved to; stored in the client config as keybind_layout. */
    public static final int LAYOUT_VERSION = 3;

    /** What {@code savedKey} returns for a binding that must not move: a modifier combo, a mouse button, not found. */
    public static final int CUSTOM = Integer.MIN_VALUE;

    /**
     * A binding that older builds shipped on {@code oldKey} (a GLFW key code) and whose default has moved since.
     * {@code version} is the layout version that moves it.
     */
    public record Stale(int version, String binding, int oldKey, String history) { }

    /** One binding to move: its translation key, the key it sits on and the key it goes to (-1 = unbound). */
    public record Move(String binding, int from, int to) { }

    static final List<Stale> STALE = List.of(
        // (Attack A -> X, layout 1, is gone with the Attack binding itself: see CARRY below, layout 3.)
        // Oct 8 to Oct 11: the first WASD builds. Stop sat on Space, Patrol on Y and Guard on P; c4d0b47 moved them to
        // BAR's S / P / G and the gather toggle gave G up, but an install of those days keeps the old keys.
        new Stale(1, "key.reignofnether.stop", GLFW.GLFW_KEY_SPACE, "Stop Space -> S (BAR)"),
        new Stale(1, "key.reignofnether.patrol", GLFW.GLFW_KEY_Y, "Patrol Y -> P (BAR)"),
        new Stale(1, "key.reignofnether.guard", GLFW.GLFW_KEY_P, "Guard P -> G (BAR)"),
        new Stale(1, "key.reignofnether.gather", GLFW.GLFW_KEY_G, "Gather target G -> unbound (Guard has G)"),
        // Reign of Nether's own defaults that sat on W/A/S/D, moved off the camera keys when WASD panning arrived
        // (Oct 8, e99a006): an install that played RoN or the first fork build keeps them, and a card button on a pan
        // key fires on a short tap of the pan key.
        new Stale(1, "key.reignofnether.ability_2", GLFW.GLFW_KEY_W, "Ability slot 2 W -> Z (W pans up)"),
        new Stale(1, "key.reignofnether.hotkey_5", GLFW.GLFW_KEY_D, "Return resources D -> ; (D pans right)"),
        new Stale(1, "key.reignofnether.hotkey_8", GLFW.GLFW_KEY_Z, "Build slot 8 Z -> . (Z is ability slot 2)"),
        new Stale(1, "key.reignofnether.hotkey_9", GLFW.GLFW_KEY_X, "Build slot 9 X -> , (X is Attack)"),
        new Stale(1, "key.reignofnether.open_shop", GLFW.GLFW_KEY_S, "Open shop S -> Space (S pans down)"),
        // Oct 21 (b6e85bad): Select all military units moved K -> "-" because K is build slot 7. Layout 1 forgot the row,
        // so every install that saved K kept it, and K picked the building instead (Lane C tester, TESTER-ASKS 16).
        new Stale(2, "key.reignofnether.select_army", GLFW.GLFW_KEY_K, "Select army K -> - (K is build slot 7)")
    );

    /**
     * A binding that no longer exists, whose saved key goes to its successor if the player chose it. Oct 22 (lovish:
     * "we dont need f and x, only f"): the Attack order was merged into Fight, so an install that rebound Attack to a
     * key of its own keeps that key, now on Fight. {@code oldDefaults} are the keys older builds shipped Attack on (A,
     * then X): saved on one of them it is the default of a binding that is gone, not a choice, and Fight keeps F.
     * The saved key of the retired binding is read from options.txt by KeybindsClientEvents (no mapping exists any more).
     */
    public record Carry(int version, String retired, String successor, List<Integer> oldDefaults, String history) { }

    static final List<Carry> CARRY = List.of(
        new Carry(3, "key.reignofnether.attack", "key.reignofnether.fight", List.of(GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_X),
            "Attack merged into Fight: your Attack key now starts Fight / Attack-move")
    );

    private KeybindMigration() { }

    /** Every Forge-backed binding by translation key, from {@link Keybindings} (client mappings are not needed). */
    static Map<String, Keybinding> bindings() {
        Map<String, Keybinding> map = new HashMap<>();
        for (Keybinding kb : Keybindings.all())
            if (kb.getTranslationKey() != null)
                map.put(kb.getTranslationKey(), kb);
        return map;
    }

    /**
     * The moves for an install whose saved bindings are at layout {@code savedVersion}.
     *
     * @param savedKey the key the player has now on a binding (translation key to GLFW key code, -1 when unbound),
     *                 or {@link #CUSTOM} for one that has a modifier, is not a keyboard key or does not exist
     */
    public static List<Move> plan(int savedVersion, ToIntFunction<String> savedKey) {
        List<Move> moves = new ArrayList<>();
        Map<String, Keybinding> all = bindings();
        for (Stale row : STALE) {
            if (row.version() <= savedVersion || row.version() > LAYOUT_VERSION)
                continue;
            Keybinding kb = all.get(row.binding());
            if (kb == null)
                continue;
            int now = savedKey.applyAsInt(row.binding());
            int target = kb.getDefaultKey();
            if (now == row.oldKey() && now != target)
                moves.add(new Move(row.binding(), now, target));
        }
        for (Carry row : CARRY) {
            if (row.version() <= savedVersion || row.version() > LAYOUT_VERSION)
                continue;
            Keybinding successor = all.get(row.successor());
            if (successor == null)
                continue;
            int theirs = savedKey.applyAsInt(row.retired());
            int now = savedKey.applyAsInt(row.successor());
            // only a real keyboard key the player chose, onto a successor still on its default
            if (theirs >= 0 && theirs != CUSTOM && !row.oldDefaults().contains(theirs)
                    && now == successor.getDefaultKey() && theirs != now)
                moves.add(new Move(row.successor(), now, theirs));
        }
        return moves;
    }

    /** The one-line history of a move, for the log and the toast ("Attack A -> X, ..."). */
    public static String history(String binding) {
        for (Stale row : STALE)
            if (row.binding().equals(binding))
                return row.history();
        for (Carry row : CARRY)
            if (row.successor().equals(binding))
                return row.history();
        return binding;
    }
}
