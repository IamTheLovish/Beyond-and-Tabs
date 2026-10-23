package com.solegendary.reignofnether.hud;

import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The "N% name" line over a building's production queue. The name comes from the button's tooltip title, which is
 * already translated (Component.translatable), instead of the button's internal English item name.
 */
public final class ProductionLabel {

    private ProductionLabel() {}

    public static String text(int percentDone, String fallbackName, List<FormattedCharSequence> tooltipLines) {
        String title = tooltipLines == null || tooltipLines.isEmpty() ? "" : plain(tooltipLines.get(0)).trim();
        return percentDone + "% " + (title.isEmpty() ? fallbackName : title);
    }

    /** The characters of a sequence without its style (the tooltip title is bold; the label is not). */
    static String plain(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
