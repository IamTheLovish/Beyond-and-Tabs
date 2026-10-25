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
    void attackOnAIsFlaggedAsPanLeft() {
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.attack", GLFW.GLFW_KEY_A));
        assertEquals(List.of(new KeyConflicts.Overlap("key.reignofnether.attack", "key.reignofnether.pan_left", GLFW.GLFW_KEY_A)), o);
    }

    @Test
    void stopMovedOntoAnotherPanKeyIsFlagged() {
        assertEquals(1, overlaps(Map.of("key.reignofnether.stop", GLFW.GLFW_KEY_A)).size());
    }

    @Test
    void aPanKeyMovedOntoAnOrderKeyIsFlaggedToo() {
        // pan-up moved to X, where Attack ships: Attack did not move, the pan key did - still one key, two jobs
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.pan_up", GLFW.GLFW_KEY_X));
        assertEquals(List.of(new KeyConflicts.Overlap("key.reignofnether.attack", "key.reignofnether.pan_up", GLFW.GLFW_KEY_X)), o);
    }

    @Test
    void anAbilityOnWIsFlagged() {
        List<KeyConflicts.Overlap> o = overlaps(Map.of("key.reignofnether.ability_2", GLFW.GLFW_KEY_W));
        assertEquals(1, o.size());
        assertEquals("key.reignofnether.pan_up", o.get(0).pan());
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
