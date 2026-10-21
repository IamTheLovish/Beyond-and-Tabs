package com.solegendary.reignofnether.guiscreen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopdownGuiScaleTest {

    @Test
    void autoIsCappedByWhatFitsTheWindow() {
        assertEquals(2, TopdownGuiScale.autoScale(2)); // 854x480 with unicode rounding
        assertEquals(1, TopdownGuiScale.autoScale(1));
    }

    @Test
    void autoNeverExceedsThree() {
        assertEquals(3, TopdownGuiScale.autoScale(3));
        assertEquals(3, TopdownGuiScale.autoScale(4));
        assertEquals(3, TopdownGuiScale.autoScale(6));
    }
}
