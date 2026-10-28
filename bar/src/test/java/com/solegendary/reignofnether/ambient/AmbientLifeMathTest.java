package com.solegendary.reignofnether.ambient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ambient life (butterflies by day, fireflies at night - lovish Oct 28, "butterflies, fireflies, birds" approved;
 * the ambient mods spawn round the player entity, which the RTS camera carries ~30 blocks above the ground, so their
 * critters barely show from the RTS view). The spawner anchors to the ground point the camera looks at. Tests first.
 */
class AmbientLifeMathTest {

    @Test
    void focusIsTheGroundPointTheCameraLooksAt() {
        // straight down: the point under the eye
        double[] f = AmbientLifeMath.focus(10, 100, -5, 0, 90, 64);
        assertEquals(10, f[0], 1e-6);
        assertEquals(64, f[1], 1e-6);
        assertEquals(-5, f[2], 1e-6);
        // 45 degrees down, facing +z (yaw 0): the ground point is (eye height - ground) ahead in +z
        f = AmbientLifeMath.focus(0, 100, 0, 0, 45, 64);
        assertEquals(0, f[0], 1e-6);
        assertEquals(36, f[2], 1e-6);
        // facing +x is yaw -90 in Minecraft's convention
        f = AmbientLifeMath.focus(0, 100, 0, -90, 45, 64);
        assertEquals(36, f[0], 1e-6);
        assertEquals(0, f[2], 1e-6);
    }

    @Test
    void radiusFollowsTheZoomWithinBounds() {
        assertEquals(12, AmbientLifeMath.radius(5), 1e-6);
        assertEquals(27, AmbientLifeMath.radius(30), 1e-6);
        assertEquals(48, AmbientLifeMath.radius(400), 1e-6);
    }

    @Test
    void butterfliesByDayFirefliesByNight() {
        assertTrue(AmbientLifeMath.isDay(6000));
        assertFalse(AmbientLifeMath.isDay(18000));
        assertTrue(AmbientLifeMath.isNight(18000));
        assertFalse(AmbientLifeMath.isNight(6000));
        assertTrue(AmbientLifeMath.isDay(24000 * 3 + 1000));   // day time wraps every 24000 ticks
        // dusk and dawn: neither (the critters change shift)
        assertFalse(AmbientLifeMath.isDay(12800) || AmbientLifeMath.isNight(12800));
    }

    @Test
    void spawnsStopAtTheCapAndNeverGoNegative() {
        assertEquals(3, AmbientLifeMath.toSpawn(0, 18, 3));
        assertEquals(2, AmbientLifeMath.toSpawn(16, 18, 3));
        assertEquals(0, AmbientLifeMath.toSpawn(18, 18, 3));
        assertEquals(0, AmbientLifeMath.toSpawn(40, 18, 3));
    }

    @Test
    void capsScaleDownWhenZoomedOut() {
        // close up all of them; zoomed far out (critters a pixel or two) far fewer
        assertEquals(18, AmbientLifeMath.cap(18, 20));
        assertTrue(AmbientLifeMath.cap(18, 90) < 18);
        assertEquals(0, AmbientLifeMath.cap(18, 160));
    }
}
