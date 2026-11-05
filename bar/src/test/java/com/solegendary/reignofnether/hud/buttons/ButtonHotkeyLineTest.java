package com.solegendary.reignofnether.hud.buttons;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The tooltip's "Hotkey: Q" line: label to text, and when it is skipped. */
class ButtonHotkeyLineTest {

    private static final String PATTERN = "Hotkey: %s";

    @Test
    void labelBecomesLine() {
        assertEquals("Hotkey: Q", Button.hotkeyLineText("Q", PATTERN, List.of("Grunt")));
    }

    @Test
    void blankOrUnboundLabelHasNoLine() {
        assertNull(Button.hotkeyLineText("", PATTERN, List.of()));
        assertNull(Button.hotkeyLineText("  ", PATTERN, List.of()));
        assertNull(Button.hotkeyLineText(null, PATTERN, List.of()));
    }

    @Test
    void tooltipAlreadyNamingTheKeyIsSkipped() {
        assertNull(Button.hotkeyLineText("Q", PATTERN, List.of("Grunt [Q]", "Cheap")));
        assertNull(Button.hotkeyLineText("Q", PATTERN, List.of("Hotkey: Q")));
    }

    @Test
    void otherKeyNamedStillGetsLine() {
        assertEquals("Hotkey: Q", Button.hotkeyLineText("Q", PATTERN, List.of("Grunt [W]")));
    }
}
