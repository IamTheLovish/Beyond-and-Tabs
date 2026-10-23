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

    private static final String FIGHT = "key.reignofnether.fight";

    @Test
    void fightIsTheOnlyAttackOrderAndSitsOnF() {
        // Oct 22: Attack (A, then X) was merged into Fight
        assertNull(KeybindMigration.bindings().get(ATTACK));
        assertEquals(GLFW.GLFW_KEY_F, Keybindings.fight.getDefaultKey());
    }

    @Test
    void attackSavedOnAnOldDefaultMovesNothing() {
        // A (Oct 11-21) and X (Oct 21-22) were Attack's shipped keys: not a choice, Fight keeps F
        for (int old : new int[] {GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_X})
            assertEquals(List.of(), plan(0, Map.of(ATTACK, old)), "attack saved on " + old);
    }

    @Test
    void anAttackKeyThePlayerChoseNowStartsFight() {
        List<KeybindMigration.Move> moves = plan(2, Map.of(ATTACK, GLFW.GLFW_KEY_Z));
        assertEquals(List.of(new KeybindMigration.Move(FIGHT, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_Z)), moves);
    }

    @Test
    void aFightKeyThePlayerAlreadyChoseIsLeftAlone() {
        assertEquals(List.of(), plan(2, Map.of(ATTACK, GLFW.GLFW_KEY_Z, FIGHT, GLFW.GLFW_KEY_R)));
    }

    @Test
    void anUnboundOrComboAttackKeyMovesNothing() {
        // unbound, a modifier combination / mouse button (the glue passes CUSTOM for those), or no line in options.txt
        for (int key : new int[] {-1, KeybindMigration.CUSTOM})
            assertEquals(List.of(), plan(0, Map.of(ATTACK, key)), "attack on " + key);
    }

    @Test
    void anAlreadyMigratedInstallIsNotCarriedAgain() {
        // layout 3 ran once; a later launch must not move Fight again
        assertEquals(List.of(), plan(KeybindMigration.LAYOUT_VERSION, Map.of(ATTACK, GLFW.GLFW_KEY_Z)));
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
        for (KeybindMigration.Carry row : KeybindMigration.CARRY) {
            assertNull(all.get(row.retired()), row.retired() + " is retired but still a binding");
            assertNotNull(all.get(row.successor()), row.successor() + " is not a binding in Keybindings");
        }
    }
}
