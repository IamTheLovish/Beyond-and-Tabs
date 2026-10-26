package com.solegendary.reignofnether.matchstart;

/** Layout rules for {@link MatchEndScreen} that need no client classes (GameTested on the server). */
public final class MatchEndLayout {
    private MatchEndLayout() { }

    /**
     * Compact party-total line, widths in px, {@code room} = from the name column to just before the units total:
     * 0 = "Party total" + full resources text, 1 = "Party total" + short text, 2 = short text alone (clipped if needed).
     */
    public static int compactTotalForm(int labelW, int fullW, int shortW, int room) {
        if (labelW + 8 + fullW <= room)
            return 0;
        if (labelW + 8 + shortW <= room)
            return 1;
        return 2;
    }

    /** A party of one gets no "Party total" row (it repeats the player's row), only its resources line. */
    public static boolean showsPartyTotal(int members) {
        return members > 1;
    }
}
