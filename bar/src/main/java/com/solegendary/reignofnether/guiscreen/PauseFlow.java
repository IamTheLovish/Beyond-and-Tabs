package com.solegendary.reignofnether.guiscreen;

/**
 * When Esc opens the match menu, kept free of client classes so PauseFlowGameTests can check it headless: only from
 * the RTS screen (TopdownGui). From any other screen, the match menu included, Esc just closes that screen.
 */
public final class PauseFlow {

    private PauseFlow() {}

    public static boolean escOpensMenu(boolean onRtsScreen) {
        return onRtsScreen;
    }
}
