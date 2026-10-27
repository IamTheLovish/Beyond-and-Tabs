package com.solegendary.reignofnether.onboarding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstLaunchGuiScaleTest {

    @Test
    void autoScaleBecomesThreeOnceOnARealInstall() {
        assertEquals(3, FirstLaunch.DEFAULT_GUI_SCALE);
        assertTrue(FirstLaunch.setDefaultGuiScale(true, false, 0));
    }

    @Test
    void aChosenScaleIsNeverOverridden() {
        assertFalse(FirstLaunch.setDefaultGuiScale(true, false, 1));
        assertFalse(FirstLaunch.setDefaultGuiScale(true, false, 2));
        assertFalse(FirstLaunch.setDefaultGuiScale(true, false, 4));
    }

    @Test
    void neverTwiceAndNeverInADevRun() {
        assertFalse(FirstLaunch.setDefaultGuiScale(true, true, 0));
        assertFalse(FirstLaunch.setDefaultGuiScale(false, false, 0));
    }
}
