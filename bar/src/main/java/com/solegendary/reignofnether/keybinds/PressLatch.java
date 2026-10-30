package com.solegendary.reignofnether.keybinds;

/**
 * A key whose action runs on its release but is decided by the modifiers held at its press. Tab is BAR's select
 * commander only as a plain press: Ctrl+Tab cycles the selection and Shift+Tab opens the overview on the press, and a
 * player who lets go of Ctrl or Shift before Tab must not also select the commander on the release (Codex adversarial
 * review of BAC-007b, HudClientEvents.onKeyRelease read the modifiers at the release). No Minecraft types: PressLatchTest.
 */
public final class PressLatch {

    private boolean armed;

    /** The key went down; {@code plain} = no Ctrl, Shift or Alt held at that moment. */
    public void press(boolean plain) {
        armed = plain;
    }

    /** The key came up: true once if its press was plain. A release without a press seen (the screen opened mid-press) is false. */
    public boolean release() {
        boolean run = armed;
        armed = false;
        return run;
    }
}
