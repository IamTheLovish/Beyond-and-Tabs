package com.solegendary.reignofnether.matchstart;

import java.util.function.ToIntFunction;

/**
 * The lobby screen's layout rules, kept free of client classes so GameTests can check them headless
 * (LobbyLayoutGameTests). Joisco's playtest, Oct 20: on a large GUI scale the right column ran under the pinned Hide
 * button ("Role: Commander" drawn over "Hide"), long names ran under the faction icon and long faction names ran into
 * the Ready / Host tag. Both panels now scroll with the mouse wheel and text is cut to its slot with "...".
 */
public final class LobbyLayout {

    private LobbyLayout() {}

    static final String ELLIPSIS = "...";

    /** The scroll offset kept inside [0, content - view]; 0 when everything fits. */
    public static int clampScroll(int scroll, int contentH, int viewH) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
    }

    /** A widget is shown only when it fits whole inside the view, so a half-hidden button can never take a click. */
    public static boolean inView(int y, int h, int viewTop, int viewBottom) {
        return y >= viewTop && y + h <= viewBottom;
    }

    /** {@code s} cut to {@code maxW} pixels with "..." when it is too long; empty when not even "..." fits. */
    public static String fit(String s, int maxW, ToIntFunction<String> width) {
        if (width.applyAsInt(s) <= maxW)
            return s;
        int room = maxW - width.applyAsInt(ELLIPSIS);
        if (room <= 0)
            return "";
        int n = s.length();
        while (n > 0 && width.applyAsInt(s.substring(0, n)) > room)
            n--;
        return s.substring(0, n).stripTrailing() + ELLIPSIS;
    }

    /** A name and its suffix (" (you)", " [Hero]") in {@code maxW}: the name is cut first so the suffix stays readable. */
    public static String fitName(String name, String suffix, int maxW, ToIntFunction<String> width) {
        if (width.applyAsInt(name + suffix) <= maxW)
            return name + suffix;
        int room = maxW - width.applyAsInt(suffix);
        if (room < width.applyAsInt(ELLIPSIS) + width.applyAsInt("WW"))
            return fit(name + suffix, maxW, width);
        return fit(name, room, width) + suffix;
    }

    /**
     * The skirmish settings column (tester, Oct 26: at a large GUI scale Start Battle covered the "Starts" row): the map
     * preview's height and the row step for {@code rows} setting rows in {@code avail} px between the column top and
     * Start Battle. Rows keep their 22 px step while the preview can stay 36 px or more; past that the rows tighten
     * (down to 16) before the preview shrinks further, so preview + rows * step never exceeds {@code avail} while
     * {@code avail >= rows * 16}. Returns {previewH, rowStep}.
     */
    public static int[] settingsColumn(int avail, int rows) {
        int step = 22;
        int preview = Math.min(92, avail - rows * step);
        if (preview < 36) {
            step = Math.max(16, Math.min(22, (avail - 36) / rows));
            preview = Math.max(0, Math.min(92, avail - rows * step));
        }
        return new int[]{ preview, step };
    }
}
