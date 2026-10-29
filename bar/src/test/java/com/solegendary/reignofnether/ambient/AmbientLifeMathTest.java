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

    // --- birds (lovish Oct 28: "butterflies, fireflies, birds"): a flock crosses the view high over the ground ---

    @Test
    void aFlockCrossesTheWholeViewThroughTheFocus() {
        // heading +x (angle 0): starts 1.4 r before the focus, ends 1.4 r past it, on the same line
        double[] p = AmbientLifeMath.flockPath(100, 50, 40, 0);
        assertEquals(100 - 56, p[0], 1e-6);
        assertEquals(50, p[1], 1e-6);
        assertEquals(100 + 56, p[2], 1e-6);
        assertEquals(50, p[3], 1e-6);
        // heading +z (angle pi/2)
        p = AmbientLifeMath.flockPath(0, 0, 20, Math.PI / 2);
        assertEquals(0, p[0], 1e-6);
        assertEquals(-28, p[1], 1e-6);
        assertEquals(28, p[3], 1e-6);
    }

    @Test
    void theFlockFliesInAVBehindItsLeader() {
        double[] lead = AmbientLifeMath.vSlot(0);
        assertEquals(0, lead[0], 1e-6);
        assertEquals(0, lead[1], 1e-6);
        double[] a = AmbientLifeMath.vSlot(1), b = AmbientLifeMath.vSlot(2), c = AmbientLifeMath.vSlot(3);
        assertTrue(a[0] > 0 && b[0] > 0, "the wings trail the leader");
        assertEquals(a[0], b[0], 1e-6);
        assertEquals(-a[1], b[1], 1e-6);                                   // one each side
        assertTrue(c[0] > a[0] && Math.abs(c[1]) > Math.abs(a[1]), "the V widens and trails further back");
        assertTrue(a[0] >= 1.0 && Math.abs(a[1]) >= 1.0, "no two birds share a pixel at RTS zoom");
    }

    @Test
    void wingsBeatThenGlide() {
        // a beat cycles the three frames (up, level, down, level); a glide holds the level frame
        java.util.Set<Integer> beat = new java.util.HashSet<>();
        int level = 0;
        for (int age = 0; age < AmbientLifeMath.FLAP_CYCLE; age++) {
            int f = AmbientLifeMath.wingFrame(age);
            assertTrue(f >= 0 && f <= 2);
            beat.add(f);
            if (f == 1)
                level++;
        }
        assertEquals(3, beat.size(), "all three wing frames show in a cycle");
        assertTrue(level > AmbientLifeMath.FLAP_CYCLE / 2, "most of the cycle is the glide");
        assertEquals(AmbientLifeMath.wingFrame(5), AmbientLifeMath.wingFrame(5 + AmbientLifeMath.FLAP_CYCLE));
    }

    @Test
    void aBirdTurnsOnScreenToItsHeading() {
        // camera yaw 0 faces +z: screen up is +z, screen right is -x (Minecraft: facing south, east is on the left);
        // the sprite's head is the texture top, and a positive particle roll turns it clockwise on screen
        assertEquals(0, AmbientLifeMath.screenRoll(0, 1, 0), 1e-6);
        assertEquals(Math.PI / 2, AmbientLifeMath.screenRoll(-1, 0, 0), 1e-6);
        assertEquals(-Math.PI / 2, AmbientLifeMath.screenRoll(1, 0, 0), 1e-6);
        assertEquals(Math.PI, Math.abs(AmbientLifeMath.screenRoll(0, -1, 0)), 1e-6);
        // camera yaw -90 faces +x: flying +x is straight up the screen
        assertEquals(0, AmbientLifeMath.screenRoll(1, 0, -90), 1e-6);
    }

    @Test
    void theShadowFallsAwayFromTheSun() {
        // noon: straight under; morning (sun in the east, +x): to the west; never further than 1.5x the height
        assertEquals(0, AmbientLifeMath.shadowOffsetX(20, 0), 1e-6);
        assertTrue(AmbientLifeMath.shadowOffsetX(20, -0.6) < 0);
        assertTrue(AmbientLifeMath.shadowOffsetX(20, 0.6) > 0);
        assertEquals(-30, AmbientLifeMath.shadowOffsetX(20, -1.4), 1e-6);
    }
}
