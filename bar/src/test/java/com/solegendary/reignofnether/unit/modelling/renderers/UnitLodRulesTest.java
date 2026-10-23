package com.solegendary.reignofnether.unit.modelling.renderers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit render LOD's decisions (UnitLodRules): the size rule must reproduce the old 40-block switch on the
 * reference screen (so nothing changes for a 1080p player at FOV 70), keep detail further out where a unit is bigger
 * on screen, and only ever skip overlay passes in a shader pack's shadow map.
 */
class UnitLodRulesTest {

    private static double fp(double screenH, double fov, double distance) {
        return UnitLodRules.pxPerBlockPerspective(screenH, fov, distance);
    }

    @Test
    void referenceScreenSwitchesWhereTheOldRuleDid() {
        // 1080 / (2 * 40 * tan 35deg) = 19.28 px per block
        assertEquals(19.28, UnitLodRules.DETAIL_MIN_PX_PER_BLOCK, 0.01);
        for (double d = 1; d < 80; d += 0.25) {
            if (Math.abs(d - UnitLodRules.LEGACY_DETAIL_DISTANCE) < 1e-6)
                continue;   // the boundary itself: one is < and the other >=; both sides agree everywhere else
            assertEquals(UnitLodRules.legacyFirstPersonDetail(d), UnitLodRules.firstPersonDetail(fp(1080, 70, d)),
                    "at " + d + " blocks the size rule must match the old distance rule on the reference screen");
        }
    }

    @Test
    void biggerOnScreenKeepsDetailFurther() {
        // 1440 px tall: the same on-screen size is reached at 40 * 1440/1080 = 53.3 blocks
        assertTrue(UnitLodRules.firstPersonDetail(fp(1440, 70, 53.0)));
        assertFalse(UnitLodRules.firstPersonDetail(fp(1440, 70, 54.0)));
        // a narrower FOV (spyglass, possession zoom) magnifies: detail kept far beyond 40 blocks
        assertTrue(UnitLodRules.firstPersonDetail(fp(1080, 30, 90.0)));
        // a small window drops it sooner, where the parts are smaller than on the reference screen
        assertFalse(UnitLodRules.firstPersonDetail(fp(720, 70, 30.0)));
    }

    @Test
    void sizeFallsSmoothlyWithDistance() {
        double last = Double.MAX_VALUE;
        for (double d = 0.5; d < 200; d += 0.5) {
            double px = fp(1080, 70, d);
            assertTrue(px < last, "pixels per block must fall as the unit moves away");
            last = px;
        }
        // orthographic: pixels per block is the screen over the view height, the same for every unit
        assertEquals(30.0, UnitLodRules.pxPerBlockOrtho(1080, 36), 1e-9);
    }

    @Test
    void overlaysSkippedOnlyInTheShadowPassOfAUnitWithTheLodOn() {
        assertTrue(UnitLodRules.skipOverlayLayer(true, true, true));
        assertFalse(UnitLodRules.skipOverlayLayer(true, true, false), "the view always draws every overlay");
        assertFalse(UnitLodRules.skipOverlayLayer(true, false, true), "non-units are never touched");
        assertFalse(UnitLodRules.skipOverlayLayer(false, true, true), "LOD off = the old behaviour");
    }

    @Test
    void degenerateInputsStayFinite() {
        assertTrue(Double.isFinite(fp(1080, 70, 0)));
        assertTrue(Double.isFinite(fp(1080, 180, 10)));   // the RTS camera reports FOV 180
        assertTrue(Double.isFinite(UnitLodRules.pxPerBlockOrtho(1080, 0)));
    }
}
