package com.solegendary.reignofnether.keybinds;

import com.solegendary.reignofnether.keybinds.ChordKeys.Chord;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tester's BAR selection / camera keys: Ctrl+Q half, Alt+Q damaged, Ctrl+. / Ctrl+, cycle, camera anchors F5-F8. */
class SelectCameraRulesTest {

    @Test
    void ac1_ctrlQKeepsEveryOtherUnitAtLeastOne() {
        assertEquals(Chord.SELECT_HALF, ChordKeys.of(GLFW.GLFW_KEY_Q, true, false, false));
        assertEquals(List.of("a", "c", "e"), ChordKeys.half(List.of("a", "b", "c", "d", "e")));
        assertEquals(List.of("a", "c"), ChordKeys.half(List.of("a", "b", "c", "d")));
        assertEquals(List.of("a"), ChordKeys.half(List.of("a")));
        assertEquals(List.of(), ChordKeys.half(List.<String>of()));
    }

    @Test
    void ac2_altQKeepsTheDamagedBelowSixtyPercent() {
        assertEquals(Chord.SELECT_DAMAGED, ChordKeys.of(GLFW.GLFW_KEY_Q, false, false, true));
        assertEquals(Chord.NONE, ChordKeys.of(GLFW.GLFW_KEY_Q, true, false, true), "Ctrl+Alt+Q is no chord");
        assertEquals(Chord.NONE, ChordKeys.of(GLFW.GLFW_KEY_Q, false, false, false), "plain Q stays ability slot 1");
        Map<String, Double> hp = new HashMap<>(Map.of("full", 1.0, "edge", 0.6, "hurt", 0.59, "dying", 0.1));
        assertEquals(List.of("hurt", "dying"), ChordKeys.damaged(List.of("full", "hurt", "edge", "dying"), hp::get));
        assertEquals(List.of(), ChordKeys.damaged(List.of("full", "edge"), hp::get), "nothing damaged: caller keeps the selection");
        assertEquals("Alt + Q", ChordKeys.label(Chord.SELECT_DAMAGED));
        assertEquals("!Q", ChordKeys.shortLabel(Chord.SELECT_DAMAGED));
    }

    @Test
    void ac3_ctrlPeriodAndCommaCycleTheSelection() {
        assertEquals(Chord.CYCLE_SELECTED_NEXT, ChordKeys.of(GLFW.GLFW_KEY_PERIOD, true, false, false));
        assertEquals(Chord.CYCLE_SELECTED_PREV, ChordKeys.of(GLFW.GLFW_KEY_COMMA, true, false, false));
        assertEquals(Chord.NONE, ChordKeys.of(GLFW.GLFW_KEY_PERIOD, false, false, false), "plain . stays the idle-worker cycle");
        assertEquals(Chord.NONE, ChordKeys.of(GLFW.GLFW_KEY_COMMA, false, false, false), "plain , stays build slot 9");
        assertEquals(0, ChordKeys.cycleIndex(-1, 3, 1));
        assertEquals(1, ChordKeys.cycleIndex(0, 3, 1));
        assertEquals(0, ChordKeys.cycleIndex(2, 3, 1));
        assertEquals(2, ChordKeys.cycleIndex(0, 3, -1));
        assertEquals(2, ChordKeys.cycleIndex(-1, 3, -1));
        assertEquals(0, ChordKeys.cycleIndex(5, 2, 1), "selection shrank: start over");
        assertEquals(-1, ChordKeys.cycleIndex(0, 0, 1));
    }

    @Test
    void ac4_cameraAnchorsOnF5ToF8AndYieldToLiveFKeys() {
        int[] want = { GLFW.GLFW_KEY_F5, GLFW.GLFW_KEY_F6, GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F8 };
        for (int i = 0; i < 4; i++)
            assertEquals(want[i], Keybindings.cameraAnchors[i].getDefaultKey());
        // F1-F4 keep hide HUD, screenshot, debug and the stats alias
        assertEquals(GLFW.GLFW_KEY_F1, Keybindings.getFnum(1).getDefaultKey());
        assertEquals(GLFW.GLFW_KEY_F4, Keybindings.statsTable.getAliasKey());
        int f6 = GLFW.GLFW_KEY_F6, f7 = GLFW.GLFW_KEY_F7, f8 = GLFW.GLFW_KEY_F8;
        assertTrue(KeyConflicts.anchorYields(f6, true, f6, f7, false, f8));
        assertTrue(KeyConflicts.anchorYields(f7, true, f6, f7, false, f8));
        assertFalse(KeyConflicts.anchorYields(f6, false, f6, f7, false, f8));
        assertFalse(KeyConflicts.anchorYields(GLFW.GLFW_KEY_F5, true, f6, f7, true, f8));
        assertTrue(KeyConflicts.anchorYields(f8, false, f6, f7, true, f8));
        assertFalse(KeyConflicts.anchorYields(f8, false, f6, f7, false, f8));
        assertFalse(KeyConflicts.anchorYields(-1, true, -1, -1, true, -1));
        // an install from before saved the anchors unbound: layout 3 moves them onto F5-F8
        List<KeybindMigration.Move> moves = KeybindMigration.plan(2, name -> name.startsWith("key.reignofnether.camera_anchor_")
            ? GLFW.GLFW_KEY_UNKNOWN : KeybindMigration.CUSTOM);
        assertEquals(4, moves.size(), moves.toString());
        for (KeybindMigration.Move m : moves)
            assertEquals(Keybindings.cameraAnchors[Integer.parseInt(m.binding().substring(m.binding().length() - 1)) - 1].getDefaultKey(), m.to());
        // the new keys stay off the pan keys
        assertEquals(List.of(), KeyConflicts.panOverlaps(name -> {
            for (Keybinding kb : Keybindings.all())
                if (name.equals(kb.getTranslationKey()))
                    return kb.getDefaultKey();
            return KeybindMigration.CUSTOM;
        }));
    }
}
