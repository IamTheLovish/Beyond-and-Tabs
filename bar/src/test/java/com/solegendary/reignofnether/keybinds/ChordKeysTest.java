package com.solegendary.reignofnether.keybinds;

import com.solegendary.reignofnether.keybinds.ChordKeys.Chord;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** BAR's Ctrl selection chords (ChordKeys): the table and the same-type rule. */
class ChordKeysTest {

    @Test
    void theLegacyChords() {
        assertEquals(Chord.SELECT_COMMANDER, ChordKeys.of(GLFW.GLFW_KEY_C, true, false, false));
        assertEquals(Chord.IDLE_BUILDER, ChordKeys.of(GLFW.GLFW_KEY_TAB, true, false, false));
        assertEquals(Chord.IDLE_T2_BUILDER, ChordKeys.of(GLFW.GLFW_KEY_TAB, true, true, false));
        // Ctrl+B is BAR's selfd, which BAT does not copy: B stays Build
        assertEquals(Chord.NONE, ChordKeys.of(GLFW.GLFW_KEY_B, true, false, false));
        // BAR Grid: Ctrl+E every unit, Ctrl+W every unit of the selected types (BarGridDefaults)
        assertEquals(Chord.SELECT_ALL_UNITS, ChordKeys.of(GLFW.GLFW_KEY_E, true, false, false));
        assertEquals(Chord.SELECT_SAME_TYPE, ChordKeys.of(GLFW.GLFW_KEY_W, true, false, false));
    }

    @Test
    void theLegacyChordsAreGoneAndNoArmyChordRemains() {
        assertEquals(Chord.NONE, ChordKeys.of(GLFW.GLFW_KEY_A, true, false, false));
        assertEquals(Chord.NONE, ChordKeys.of(GLFW.GLFW_KEY_Z, true, false, false));
        for (Chord c : Chord.values())
            assertNotEquals("SELECT_ARMY", c.name(), "the select-army chord is removed (the - key keeps the button)");
    }

    @Test
    void noCtrlOrAltMeansNoChord() {
        for (ChordKeys.Def d : ChordKeys.ALL) {
            assertEquals(Chord.NONE, ChordKeys.of(d.key(), false, false, false), d.label());
            assertEquals(Chord.NONE, ChordKeys.of(d.key(), true, false, true), d.label() + " with Alt");
        }
        assertEquals(Chord.NONE, ChordKeys.of(-1, true, false, false));
    }

    @Test
    void controlGroupsKeepCtrlDigits() {
        for (int d = GLFW.GLFW_KEY_0; d <= GLFW.GLFW_KEY_9; d++)
            assertEquals(Chord.NONE, ChordKeys.of(d, true, false, false));
    }

    @Test
    void labels() {
        assertEquals("Ctrl + C", ChordKeys.label(Chord.SELECT_COMMANDER));
        assertEquals("^C", ChordKeys.shortLabel(Chord.SELECT_COMMANDER));
        assertEquals("^T", ChordKeys.shortLabel(Chord.IDLE_BUILDER));
        assertEquals("^ST", ChordKeys.shortLabel(Chord.IDLE_T2_BUILDER));
        assertEquals("", ChordKeys.label(Chord.NONE));
    }

    @Test
    void sameTypesPicksEveryOwnedUnitOfTheSelectedTypes() {
        List<String> owned = List.of("a1", "b1", "c1", "a2");
        assertEquals(List.of("a1", "a2"), ChordKeys.sameTypes(owned, List.of("a9"), s -> s.charAt(0)));
        assertEquals(List.of("b1", "c1"), ChordKeys.sameTypes(owned, List.of("c0", "b0"), s -> s.charAt(0)));
        assertEquals(List.of(), ChordKeys.sameTypes(owned, List.of(), s -> s.charAt(0)));
    }

    @Test
    void theMovedButtonsAreOffHomeAndEnd() {
        assertEquals(GLFW.GLFW_KEY_TAB, Keybindings.selectCommander.getDefaultKey());
        assertEquals(GLFW.GLFW_KEY_UNKNOWN, Keybindings.idleT2Builder.getDefaultKey());
        assertEquals(Chord.SELECT_COMMANDER, Keybindings.selectCommander.getChord());
        assertEquals(Chord.IDLE_T2_BUILDER, Keybindings.idleT2Builder.getChord());
    }
}
