package com.solegendary.reignofnether.keybinds;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The Controls warning for a command-card key that sits on a camera pan key. */
class KeyConflictsTest {

    private static List<KeyConflicts.Overlap> overlaps(Map<String, Integer> overrides) {
        return KeyConflicts.panOverlaps(name -> {
            Integer o = overrides.get(name);
            if (o != null)
                return o;
            Keybinding kb = KeybindMigration.bindings().get(name);
            return kb == null ? KeybindMigration.CUSTOM : kb.getDefaultKey();
        });
    }

    @Test
    void theShippedDefaultsHaveNoWarning() {
        // Stop is G (BAR Grid) and the only camera pan keys are the arrows, so no order shares a pan key
        assertEquals(GLFW.GLFW_KEY_G, Keybindings.stop.getDefaultKey());
        assertEquals(List.of(), overlaps(Map.of()));
    }

    @Test
    void attackOnAnArrowIsFlagged() {
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.attack", GLFW.GLFW_KEY_LEFT));
        assertEquals(List.of(new KeyConflicts.Overlap("key.reignofnether.attack", "key.reignofnether.pan_plus_x", GLFW.GLFW_KEY_LEFT)), o);
    }

    @Test
    void stopMovedOntoAnotherPanKeyIsFlagged() {
        assertEquals(1, overlaps(Map.of("key.reignofnether.stop", GLFW.GLFW_KEY_UP)).size());
    }

    @Test
    void aPanKeyMovedOntoAnOrderKeyIsFlaggedToo() {
        // pan-up moved to X, where the Military tab key and grid cell 10 ship: they did not move, the pan key did -
        // one key, three jobs, both card bindings flagged (Attack is on A now, not X)
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.pan_plus_z", GLFW.GLFW_KEY_X));
        assertEquals(2, o.size(), o.toString());
        assertTrue(o.contains(new KeyConflicts.Overlap("key.reignofnether.build_tab_military", "key.reignofnether.pan_plus_z", GLFW.GLFW_KEY_X)), o.toString());
        assertTrue(o.contains(new KeyConflicts.Overlap("key.reignofnether.build_grid_10", "key.reignofnether.pan_plus_z", GLFW.GLFW_KEY_X)), o.toString());
        // pan moved to F, where the order Fight ships: an order key under a moved pan key is flagged too
        List<KeyConflicts.Overlap> f = overlaps(Map.of("key.reignofnether.pan_plus_z", GLFW.GLFW_KEY_F));
        assertTrue(f.contains(new KeyConflicts.Overlap("key.reignofnether.fight", "key.reignofnether.pan_plus_z", GLFW.GLFW_KEY_F)), f.toString());
    }

    @Test
    void anAbilityOnAnArrowIsFlagged() {
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.ability_2", GLFW.GLFW_KEY_UP));
        assertEquals(1, o.size());
        assertEquals("key.reignofnether.pan_plus_z", o.get(0).pan());
    }

    @Test
    void wasdIsNotAPanKeyAnyMore() {
        assertEquals(4, KeyConflicts.PAN_BINDINGS.size());
        assertEquals(List.of(), overlaps(Map.of("key.reignofnether.attack", GLFW.GLFW_KEY_W)));
    }

    @Test
    void unboundAndModifiedKeysNeverConflict() {
        assertEquals(List.of(), overlaps(Map.of("key.reignofnether.attack", -1)));
        assertEquals(List.of(), overlaps(Map.of("key.reignofnether.attack", KeybindMigration.CUSTOM)));
    }

    @Test
    void cameraBindingsAreNotCommandKeys() {
        // zoom or rotate keys sharing a key with a pan key are the camera's own business (RtsConflictContext)
        assertFalse(KeyConflicts.isPanOverlap("key.reignofnether.rot_cw", Keybindings.CATEGORY_CAMERA, GLFW.GLFW_KEY_LEFT,
            GLFW.GLFW_KEY_RIGHT, "key.reignofnether.pan_plus_x", GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_LEFT));
    }
}
