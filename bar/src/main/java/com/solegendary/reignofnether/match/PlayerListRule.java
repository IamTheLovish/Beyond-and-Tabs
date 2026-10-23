package com.solegendary.reignofnether.match;

/**
 * Whether the player list (the stats strip / table in the HUD's top-right corner,
 * hud.playerdisplay.PlayerPanelClientEvents) is drawn. Tester Joisco: "we need to minimize elements that block the
 * screen". So a match starts with the list hidden unless the host switched "Player list" on in the lobby (game rule
 * showPlayerList, synced to every client); the list key (I / F4) still brings it up for one player and takes it away
 * again. Pure rule, no Minecraft classes, so a GameTest on the dedicated server can check it.
 */
public final class PlayerListRule {
    private PlayerListRule() {
    }

    /** The host's lobby choice when nothing sets it: off. */
    public static final boolean DEFAULT = false;

    /** Drawn when the match shows it for everyone, or this player brought it up with the key. */
    public static boolean visible(boolean matchShowsIt, boolean playerOpenedIt) {
        return matchShowsIt || playerOpenedIt;
    }

    /** What "this player opened it" becomes after the list key: with the match option on, the key only folds the table. */
    public static boolean afterKey(boolean matchShowsIt, boolean playerOpenedIt) {
        return !matchShowsIt && !playerOpenedIt;
    }
}
