package com.solegendary.reignofnether.keybinds;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The saved-keybind migration (Oct 21 playtest: lovish's Attack card still read "A" because #39 changed only the
 * default and Minecraft keeps the saved binding in options.txt). Pure rules - no window, no options.txt.
 */
class KeybindMigrationTest {

    private static final String ATTACK = "key.reignofnether.attack";
    private static final String STOP = "key.reignofnether.stop";

    /** A player's bindings: everything on its default, except what the test overrides. */
    private static int saved(Map<String, Integer> overrides, String name) {
        Integer o = overrides.get(name);
        if (o != null)
            return o;
        Keybinding kb = KeybindMigration.bindings().get(name);
        return kb == null ? KeybindMigration.CUSTOM : kb.getDefaultKey();
    }

    // what layout 4 shipped and layout 5 (BAC-007b 2a) moves: Space alert, F5 unbound, "." idle worker / observer toggle, "," Mass Burial, Ctrl+Tab cycle
    private static final Map<String, Integer> OLD_LAYOUT_4 = Map.of(
        "key.reignofnether.last_alert", GLFW.GLFW_KEY_SPACE, "key.reignofnether.fn_5", GLFW.GLFW_KEY_F5,
        "key.reignofnether.idle_worker", GLFW.GLFW_KEY_PERIOD, "key.reignofnether.hotkey_8", GLFW.GLFW_KEY_PERIOD,
        "key.reignofnether.hotkey_9", GLFW.GLFW_KEY_COMMA, "key.reignofnether.tab", GLFW.GLFW_KEY_TAB);

    private static List<KeybindMigration.Move> plan(int version, Map<String, Integer> overrides) {
        return KeybindMigration.plan(version, name -> saved(overrides, name));
    }

    @Test
    void attackDefaultIsA() {
        // BAR Grid (BarGridDefaults, BAC-007b): WASD panning is gone, so A is Attack again
        assertEquals(GLFW.GLFW_KEY_A, Keybindings.attack.getDefaultKey());
        assertEquals(5, KeybindMigration.LAYOUT_VERSION);
    }

    @Test
    void attackSavedOnTheOldDefaultXMovesToA() {
        List<KeybindMigration.Move> moves = plan(0, Map.of(ATTACK, GLFW.GLFW_KEY_X));
        assertEquals(1, moves.size(), moves.toString());
        assertEquals(new KeybindMigration.Move(ATTACK, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_A), moves.get(0));
    }

    @Test
    void aLayoutTwoInstallWithEveryOldOrderKeyGetsExactlyFourteenMoves() {
        // layout 2 shipped Attack X, Stop S, Patrol P, Guard G, Hold H, Move M and an unbound commander key; the
        // overview stays on Tab (it needs Shift now, no key moves). Layout 3 (main's Ctrl+S Stop) moves Stop S to unbound,
        // layout 4 moves the order keys and Stop S to G, layout 5 adds the six keys layout 4 shipped (OLD_LAYOUT_4).
        Map<String, Integer> old = new HashMap<>(OLD_LAYOUT_4);
        old.putAll(Map.of(
            ATTACK, GLFW.GLFW_KEY_X, STOP, GLFW.GLFW_KEY_S, "key.reignofnether.patrol", GLFW.GLFW_KEY_P,
            "key.reignofnether.guard", GLFW.GLFW_KEY_G, "key.reignofnether.hold", GLFW.GLFW_KEY_H,
            "key.reignofnether.move", GLFW.GLFW_KEY_M, "key.reignofnether.select_commander", -1));
        List<KeybindMigration.Move> moves = plan(2, old);
        assertEquals(14, moves.size(), moves.toString());
        // Stop matches two rows from S (layouts 3 and 4); plan() sends every move to the binding's current default, so
        // both are S -> G. Every binding lands on its BarGridDefaults row.
        Map<String, Integer> landed = new HashMap<>();
        for (KeybindMigration.Move m : moves)
            landed.put(m.binding(), m.to());
        for (Map.Entry<String, Integer> e : landed.entrySet())
            assertEquals(KeybindMigration.bindings().get(e.getKey()).getDefaultKey(), e.getValue(), e.getKey());
        assertEquals(2, moves.stream().filter(m -> m.equals(new KeybindMigration.Move(STOP, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_G))).count(), moves.toString());
        assertTrue(moves.contains(new KeybindMigration.Move("key.reignofnether.move", GLFW.GLFW_KEY_M, -1)), moves.toString());
        assertTrue(moves.contains(new KeybindMigration.Move("key.reignofnether.select_commander", -1, GLFW.GLFW_KEY_TAB)), moves.toString());
        assertEquals(List.of(), plan(5, old), "a layout 5 install is left alone");
    }

    @Test
    void aLayoutFourInstallWithEveryOldKeyGetsExactlySixMoves() {
        List<KeybindMigration.Move> moves = plan(4, OLD_LAYOUT_4);
        assertEquals(6, moves.size(), moves.toString());
        for (KeybindMigration.Move m : moves)
            assertEquals(KeybindMigration.bindings().get(m.binding()).getDefaultKey(), m.to(), m.binding());
        assertTrue(moves.contains(new KeybindMigration.Move("key.reignofnether.last_alert", GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_F5)), moves.toString());
        assertTrue(moves.contains(new KeybindMigration.Move("key.reignofnether.tab", GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_PERIOD)), moves.toString());
        assertTrue(moves.contains(new KeybindMigration.Move("key.reignofnether.fn_5", GLFW.GLFW_KEY_F5, -1)), moves.toString());
        assertEquals(List.of(), plan(5, OLD_LAYOUT_4), "a layout 5 install is left alone");
    }

    @Test
    void stopSavedOnTheOldDefaultSEndsOnG() {
        // Nov 3 (Nick): a short tap of S while WASD-panning fired Stop on the selected workers. Every install of the
        // Oct 11 - Nov 3 builds saved Stop on S. Rows of layouts 3 (main: unbound, Ctrl+S runs it) and 4 (G) both match S;
        // plan() moves to the binding's current default, so both moves are S -> G (Ctrl+S still stops).
        List<KeybindMigration.Move> moves = plan(2, Map.of(STOP, GLFW.GLFW_KEY_S));
        assertEquals(List.of(
            new KeybindMigration.Move(STOP, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_G),
            new KeybindMigration.Move(STOP, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_G)), moves);
        // the glue applies the moves in order, so the last one is where Stop ends
        assertEquals(GLFW.GLFW_KEY_G, moves.get(moves.size() - 1).to());
        // history() shows the newest layout's row; the layout-4 row from S is listed first
        assertEquals("Stop S -> G (BAR Grid)", KeybindMigration.history(STOP));
    }

    @Test
    void aLayoutThreeInstallWithStopUnboundGetsStopToG() {
        // installs that ran main's layout 3: Stop was moved off S to unbound (Ctrl+S runs it) and must reach G now
        assertEquals(List.of(new KeybindMigration.Move(STOP, GLFW.GLFW_KEY_UNKNOWN, GLFW.GLFW_KEY_G)),
            plan(3, Map.of(STOP, GLFW.GLFW_KEY_UNKNOWN)));
    }

    @Test
    void stopThePlayerPutOnAnotherKeyIsLeftAlone() {
        assertEquals(List.of(), plan(2, Map.of(STOP, GLFW.GLFW_KEY_K)));
    }

    @Test
    void aKeyThePlayerChoseIsLeftAlone() {
        // another letter, unbound, a modifier combination / mouse button (the glue passes CUSTOM for those)
        for (int key : new int[] {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_F, -1, KeybindMigration.CUSTOM})
            assertEquals(List.of(), plan(0, Map.of(ATTACK, key)), "attack on " + key);
    }

    @Test
    void anAlreadyMigratedInstallKeepsAnAChosenAfterwards() {
        // the layout ran once; the player then put Attack back on X on purpose - the next launch must not undo that
        assertEquals(List.of(), plan(KeybindMigration.LAYOUT_VERSION, Map.of(ATTACK, GLFW.GLFW_KEY_X)));
    }

    @Test
    void aFreshInstallMovesNothing() {
        assertEquals(List.of(), plan(0, Map.of()));
    }

    @Test
    void anInstallFromTheFirstWasdBuildsGetsEveryStaleKeyBack() {
        // an options.txt written by the Oct 8-11 builds / Reign of Nether itself: every old default saved. A binding
        // that moved twice (Stop: Space -> S -> unbound) was saved on its OLDEST key, the first row that names it.
        Map<String, Integer> old = new HashMap<>();
        for (KeybindMigration.Stale row : KeybindMigration.STALE)
            old.putIfAbsent(row.binding(), row.oldKey());
        List<KeybindMigration.Move> moves = plan(0, old);
        assertEquals(old.size(), moves.size(), moves.toString());
        // applying them leaves every binding on its default and nothing on a camera key it does not ship on
        Map<String, Integer> after = new HashMap<>(old);
        for (KeybindMigration.Move m : moves)
            after.put(m.binding(), m.to());
        for (KeybindMigration.Move m : moves)
            assertEquals(KeybindMigration.bindings().get(m.binding()).getDefaultKey(), (int) after.get(m.binding()));
        assertEquals(List.of(), KeyConflicts.panOverlaps(name -> saved(after, name)));
    }

    @Test
    void everyRowIsConsistentWithTheLiveDefaults() {
        Map<String, Keybinding> all = KeybindMigration.bindings();
        for (KeybindMigration.Stale row : KeybindMigration.STALE) {
            Keybinding kb = all.get(row.binding());
            assertNotNull(kb, row.binding() + " is not a binding in Keybindings");
            assertNotEquals(row.oldKey(), kb.getDefaultKey(), row.binding() + ": the old key equals today's default, nothing to move");
            assertTrue(row.version() >= 1 && row.version() <= KeybindMigration.LAYOUT_VERSION,
                row.binding() + " is for layout " + row.version() + " but LAYOUT_VERSION is " + KeybindMigration.LAYOUT_VERSION);
        }
    }
}
