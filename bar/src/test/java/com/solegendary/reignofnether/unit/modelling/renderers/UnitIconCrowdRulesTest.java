package com.solegendary.reignofnether.unit.modelling.renderers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Late-game frame rate (lovish's log, Oct 23 2026): at 450 to 575 units the client fell from 120 to 55-75 fps because
 * the unit models of a crowd were still drawn at zoom 50 to 70, just under the strategic-icon switch. The icon switch
 * now comes in earlier as the crowd grows (UnitLodRules.iconEnterZoom), never below the point where a unit is a few
 * pixels and never touching close zoom, and the model skip is one pure rule (UnitLodRules.skipUnitModel) shared by the
 * render event and the entity dispatcher.
 */
class UnitIconCrowdRulesTest {

    /** GeneratedBodies.DETAIL_ZOOM: below this the bodies draw their small detail parts - close-up quality, never cut. */
    private static final float DETAIL_ZOOM = 36f;
    private static final float DEFAULT_ENTER = 70f;
    private static final float HYSTERESIS = 8f;   // StrategicViewClientEvents: ENTER 70 - EXIT 62

    @Test
    void smallArmiesKeepTheDefaultSwitch() {
        for (int units = 0; units <= UnitLodRules.CROWD_START_UNITS; units += 10)
            assertEquals(DEFAULT_ENTER, UnitLodRules.iconEnterZoom(DEFAULT_ENTER, units), 1e-4,
                    units + " units: nothing changes for a normal game");
    }

    @Test
    void aBigCrowdSwitchesToIconsEarlier() {
        float at575 = UnitLodRules.iconEnterZoom(DEFAULT_ENTER, 575);
        assertTrue(at575 < 60f, "575 units must switch well before zoom 70, got " + at575);
        assertEquals(DEFAULT_ENTER * UnitLodRules.CROWD_MIN_FACTOR,
                UnitLodRules.iconEnterZoom(DEFAULT_ENTER, 5000), 1e-3, "the factor stops falling at its floor");
    }

    @Test
    void theSwitchOnlyMovesEarlierAsTheCrowdGrows() {
        float last = Float.MAX_VALUE;
        for (int units = 0; units <= 1000; units++) {
            float enter = UnitLodRules.iconEnterZoom(DEFAULT_ENTER, units);
            assertTrue(enter <= last + 1e-4f, "enter zoom rose at " + units + " units");
            last = enter;
        }
    }

    @Test
    void closeZoomIsNeverCut() {
        for (int units = 0; units <= 5000; units += 25) {
            float enter = UnitLodRules.iconEnterZoom(DEFAULT_ENTER, units);
            assertTrue(enter >= DETAIL_ZOOM * 1.3f, "at " + units + " units icons would start at zoom " + enter
                    + ", too close to the detail zoom " + DETAIL_ZOOM);
        }
        // a player who set a closer icon threshold than the crowd floor keeps exactly that
        assertEquals(40f, UnitLodRules.iconEnterZoom(40f, 900), 1e-4);
    }

    @Test
    void oneStepOfTheCrowdFactorStaysInsideTheHysteresisBand() {
        // the unit count jitters every tick; a step of the (quantised) factor must not flip icons and models
        float worst = 0;
        float last = UnitLodRules.iconEnterZoom(DEFAULT_ENTER, 0);
        for (int units = 1; units <= 1000; units++) {
            float enter = UnitLodRules.iconEnterZoom(DEFAULT_ENTER, units);
            worst = Math.max(worst, Math.abs(enter - last));
            last = enter;
        }
        assertTrue(worst < HYSTERESIS, "one step moved the switch by " + worst + ", the hysteresis is " + HYSTERESIS);
    }

    @Test
    void modelSkipRuleMatchesTheOldEvent() {
        // args: strategic, strategicBig, isUnit, isBig, isHudSelected
        assertFalse(UnitLodRules.skipUnitModel(false, false, true, false, false), "not strategic: every model");
        assertTrue(UnitLodRules.skipUnitModel(true, false, true, false, false), "strategic: a unit's model is skipped");
        assertFalse(UnitLodRules.skipUnitModel(true, false, true, false, true), "...except the HUD portrait's unit");
        assertFalse(UnitLodRules.skipUnitModel(true, false, true, true, false), "...and a big unit until the big switch");
        assertTrue(UnitLodRules.skipUnitModel(true, true, true, true, false), "past the big switch it goes too");
        assertFalse(UnitLodRules.skipUnitModel(true, true, false, false, false), "never a non-unit");
    }
}
