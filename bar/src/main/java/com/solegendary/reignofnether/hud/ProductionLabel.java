package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The "N% name" line over a building's production queue. The name is the item start button's tooltip title, which is
 * already translated (Component.translatable), instead of the internal English item name.
 */
public final class ProductionLabel {

    private ProductionLabel() {}

    public static String text(int percentDone, String fallbackName, List<FormattedCharSequence> tooltipLines) {
        String title = tooltipLines == null || tooltipLines.isEmpty() ? "" : plain(tooltipLines.get(0)).trim();
        return percentDone + "% " + (title.isEmpty() ? fallbackName : title);
    }

    /**
     * The translated title of a queued item: the first tooltip line of its start button. The queue shows cancel buttons,
     * which carry no tooltip, so the title comes from the item, not from the button on screen. Null if there is none.
     */
    public static List<FormattedCharSequence> title(ProductionItem item, ProductionPlacement building) {
        StartProductionButton start = item.getStartButton(building, null);
        return start == null ? null : start.tooltipLines;
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
