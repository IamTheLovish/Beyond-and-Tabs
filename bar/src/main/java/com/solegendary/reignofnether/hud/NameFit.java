package com.solegendary.reignofnether.hud;

import java.util.function.ToIntFunction;

/**
 * Fits a HUD title to a pixel width: cut and ended with an ellipsis, never drawn in a smaller font (BAC-008: es_es
 * "Centro Urbano (Capital)" was clipped to "Centro Urbano (Capita"). Pure, so JUnit can test it with a width stub.
 */
final class NameFit {
    private NameFit() { }

    static final String ELLIPSIS = "…";

    /**
     * @param width pixel width of a string (the font's, in game)
     * @return s if it fits; else the longest prefix plus an ellipsis that fits, with no dangling space, "(" or ","
     *         before the ellipsis; "" when not even the ellipsis fits
     */
    static String fit(String s, int maxPx, ToIntFunction<String> width) {
        if (s == null || width.applyAsInt(s) <= maxPx)
            return s;
        for (int end = s.length() - 1; end >= 0; end--) {
            String t = s.substring(0, end).replaceAll("[\\s(,]+$", "") + ELLIPSIS;
            if (width.applyAsInt(t) <= maxPx)
                return t;
        }
        return "";
    }
}
