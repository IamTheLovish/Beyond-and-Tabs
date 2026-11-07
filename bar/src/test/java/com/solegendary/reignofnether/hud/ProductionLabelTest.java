package com.solegendary.reignofnether.hud;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The production label shows the translated tooltip title, never the internal item name, unless there is no title. */
class ProductionLabelTest {

    @Test
    void usesTheTooltipTitleWithoutItsStyle() {
        List<FormattedCharSequence> tooltip = List.of(
            FormattedCharSequence.forward("Aldeano", Style.EMPTY.withBold(true)),
            FormattedCharSequence.forward("50 food", Style.EMPTY));
        assertEquals("40% Aldeano", ProductionLabel.text(40, "Villager", tooltip));
    }

    @Test
    void fallsBackToTheItemNameWhenThereIsNoTitle() {
        assertEquals("0% Villager", ProductionLabel.text(0, "Villager", null));
        assertEquals("7% Villager", ProductionLabel.text(7, "Villager", List.of()));
        assertEquals("100% Villager", ProductionLabel.text(100, "Villager",
            List.of(FormattedCharSequence.forward("  ", Style.EMPTY))));
    }

    @Test
    void keepsCharactersOutsideTheBasicPlane() {
        List<FormattedCharSequence> tooltip = List.of(FormattedCharSequence.forward("龍 🐉", Style.EMPTY));
        assertEquals("1% 龍 🐉", ProductionLabel.text(1, "Dragon", tooltip));
    }
}
