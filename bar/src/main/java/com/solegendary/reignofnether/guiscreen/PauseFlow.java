package com.solegendary.reignofnether.guiscreen;

/**
 * What Esc does in the RTS view, kept free of client classes so PauseFlowGameTests can check it headless.
 * <p>
 * The match menu opens only from the RTS screen (TopdownGui). From any other screen, the match menu included, Esc just
 * closes that screen.
 * <p>
 * On the RTS screen Esc is BAR's "cancel" key (lovish, Oct 21: "esc clears unit selection ... so esc doesn't prompt
 * the menu"): it drops the building cursor, then an armed order, then the selection, and otherwise does nothing. The
 * menu has its own HUD button (top right) and Shift+Esc.
 */
public final class PauseFlow {

    private PauseFlow() {}

    public enum EscAction { DROP_BUILDING, CANCEL_ORDER, DESELECT, OPEN_MENU, NONE }

    public static boolean escOpensMenu(boolean onRtsScreen) {
        return onRtsScreen;
    }

    /** Esc pressed on the RTS screen: the first thing to cancel, else the menu with Shift, else nothing. */
    public static EscAction onRtsEsc(boolean placingBuilding, boolean orderArmed, boolean hasSelection, boolean shift) {
        if (shift)
            return EscAction.OPEN_MENU;
        if (placingBuilding)
            return EscAction.DROP_BUILDING;
        if (orderArmed)
            return EscAction.CANCEL_ORDER;
        if (hasSelection)
            return EscAction.DESELECT;
        return EscAction.NONE;
    }
}
