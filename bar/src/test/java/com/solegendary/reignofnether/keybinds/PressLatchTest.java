package com.solegendary.reignofnether.keybinds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tab selects the commander on a plain press only, whatever is held when it comes up (Codex review of BAC-007b). */
class PressLatchTest {

    @Test
    void aPlainTapRunsOnce() {
        PressLatch l = new PressLatch();
        l.press(true);
        assertTrue(l.release());
        assertFalse(l.release(), "a second release without a press runs nothing");
    }

    @Test
    void ctrlOrShiftLetGoBeforeTabRunsNothing() {
        // Ctrl+Tab (cycle) or Shift+Tab (overview): the modifier comes up first, so the release looks plain
        PressLatch l = new PressLatch();
        l.press(false);
        assertFalse(l.release());
    }

    @Test
    void aReleaseWithoutAPressRunsNothing() {
        assertFalse(new PressLatch().release());
    }

    @Test
    void aRepeatAfterTheModifierCameUpDoesNotRearm() {
        // Ctrl+Tab held, Ctrl let go, Tab still down and repeating: still the cycle gesture
        PressLatch l = new PressLatch();
        l.press(false);
        l.press(true);
        l.press(true);
        assertFalse(l.release());
    }

    @Test
    void thePressReportedTwiceStaysPlain() {
        // the RTS screen's key event and the raw input event both report one plain press
        PressLatch l = new PressLatch();
        l.press(true);
        l.press(true);
        assertTrue(l.release());
        l.press(true);
        assertTrue(l.release(), "the next tap after a release starts fresh");
    }

    @Test
    void aPlainTapRightAfterACtrlTabChordRuns() {
        // Ctrl+Tab (idle builder) then a plain Tab: the chord's release must end the latch (HudClientEvents.onKeyRelease
        // releases it before the chord can return), or the plain press stays disarmed (Codex review of BAC-007b 2a)
        PressLatch l = new PressLatch();
        l.press(false);
        assertFalse(l.release());
        l.press(true);
        assertTrue(l.release());
        l.press(false);
        l.press(true);   // without that release the plain press would only keep it disarmed
        assertFalse(l.release());
    }

    @Test
    void aModifiedPressAfterAPlainOneDisarms() {
        // key repeat or a second press with Ctrl held: the latest press decides
        PressLatch l = new PressLatch();
        l.press(true);
        l.press(false);
        assertFalse(l.release());
    }
}
