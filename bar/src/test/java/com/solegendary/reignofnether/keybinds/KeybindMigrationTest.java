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

    /** A player's bindings: everything on its default, except what the test overrides. */
    private static int saved(Map<String, Integer> overrides, String name) {
        Integer o = overrides.get(name);
        if (o != null)
            return o;
        Keybinding kb = KeybindMigration.bindings().get(name);
        return kb == null ? KeybindMigration.CUSTOM : kb.getDefaultKey();
    }

    private static List<KeybindMigration.Move> plan(int version, Map<String, Integer> overrides) {
        return KeybindMigration.plan(version, name -> saved(overrides, name));
    }

    @Test
    void attackDefaultIsX() {
        // #39: A is pure camera pan-left
        assertEquals(GLFW.GLFW_KEY_X, Keybindings.attack.getDefaultKey());
    }

    @Test
    void attackSavedOnTheOldDefaultAMovesToX() {
        List<KeybindMigration.Move> moves = plan(0, Map.of(ATTACK, GLFW.GLFW_KEY_A));
        assertEquals(1, moves.size(), moves.toString());
        assertEquals(new KeybindMigration.Move(ATTACK, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_X), moves.get(0));
    }

    @Test
    void aKeyThePlayerChoseIsLeftAlone() {
        // another letter, unbound, a modifier combination / mouse button (the glue passes CUSTOM for those)
        for (int key : new int[] {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_F, -1, KeybindMigration.CUSTOM})
            assertEquals(List.of(), plan(0, Map.of(ATTACK, key)), "attack on " + key);
    }

    @Test
    void anAlreadyMigratedInstallKeepsAnAChosenAfterwards() {
        // layout 1 ran once; the player then put Attack back on A on purpose - the next launch must not undo that
        assertEquals(List.of(), plan(KeybindMigration.LAYOUT_VERSION, Map.of(ATTACK, GLFW.GLFW_KEY_A)));
    }

    @Test
    void aFreshInstallMovesNothing() {
        assertEquals(List.of(), plan(0, Map.of()));
    }

    @Test
    void anInstallFromTheFirstWasdBuildsGetsEveryStaleKeyBack() {
        // an options.txt written by the Oct 8-11 builds / Reign of Nether itself: every old default saved
        Map<String, Integer> old = new HashMap<>();
        for (KeybindMigration.Stale row : KeybindMigration.STALE)
            old.put(row.binding(), row.oldKey());
        List<KeybindMigration.Move> moves = plan(0, old);
        assertEquals(KeybindMigration.STALE.size(), moves.size());
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
