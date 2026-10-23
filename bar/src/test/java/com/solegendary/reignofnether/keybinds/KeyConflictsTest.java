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
        // Stop is on S by design (tap = Stop, hold = pan), so a fresh install shows no red key
        assertEquals(GLFW.GLFW_KEY_S, Keybindings.stop.getDefaultKey());
        assertEquals(List.of(), overlaps(Map.of()));
    }

    @Test
    void fightOnAIsFlaggedAsPanLeft() {
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.fight", GLFW.GLFW_KEY_A));
        assertEquals(List.of(new KeyConflicts.Overlap("key.reignofnether.fight", "key.reignofnether.pan_left", GLFW.GLFW_KEY_A)), o);
    }

    @Test
    void stopMovedOntoAnotherPanKeyIsFlagged() {
        assertEquals(1, overlaps(Map.of("key.reignofnether.stop", GLFW.GLFW_KEY_A)).size());
    }

    @Test
    void aPanKeyMovedOntoAnOrderKeyIsFlaggedToo() {
        // pan-up moved to X, where only the builder's energy key ships now (Attack is merged into Fight, Oct 22): it did
        // not move, the pan key did - flagged. It is no longer an Attack key.
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.pan_up", GLFW.GLFW_KEY_X));
        assertEquals(1, o.size(), o.toString());
        assertTrue(o.contains(new KeyConflicts.Overlap("key.reignofnether.build_energy", "key.reignofnether.pan_up", GLFW.GLFW_KEY_X)), o.toString());
        // and on F, where Fight ships
        o = overlaps(Map.of("key.reignofnether.pan_up", GLFW.GLFW_KEY_F));
        // (F is also build slot 3 and inventory slot 3, so those are flagged too)
        assertEquals(3, o.size(), o.toString());
        assertTrue(o.contains(new KeyConflicts.Overlap("key.reignofnether.fight","key.reignofnether.pan_up", GLFW.GLFW_KEY_F)), o.toString());
    }

    @Test
    void anAbilityOnWIsFlagged() {
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.ability_2", GLFW.GLFW_KEY_W));
        assertEquals(1, o.size());
        assertEquals("key.reignofnether.pan_up", o.get(0).pan());
    }

    @Test
    void unboundAndModifiedKeysNeverConflict() {
        assertEquals(List.of(), overlaps(Map.of("key.reignofnether.fight", -1)));
        assertEquals(List.of(), overlaps(Map.of("key.reignofnether.fight", KeybindMigration.CUSTOM)));
    }

    @Test
    void cameraBindingsAreNotCommandKeys() {
        // zoom or rotate keys sharing a key with a pan key are the camera's own business (RtsConflictContext)
        assertFalse(KeyConflicts.isPanOverlap("key.reignofnether.rot_cw", Keybindings.CATEGORY_CAMERA, GLFW.GLFW_KEY_LEFT,
            GLFW.GLFW_KEY_RIGHT, "key.reignofnether.pan_plus_x", GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_LEFT));
    }
}
