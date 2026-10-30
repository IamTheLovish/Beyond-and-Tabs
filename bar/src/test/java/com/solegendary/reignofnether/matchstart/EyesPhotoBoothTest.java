package com.solegendary.reignofnether.matchstart;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eyes structure photo booth (EyesDirector scene 15) without the world: where the site goes and how far its pad
 * keeps from the arena wall, where the close still's camera stands and what it looks at, and the switch that keeps
 * ambient life out of the stills. Round r34 (run 40791786727) is the worked case: the capitol at (-216, 10), the wall's
 * ring round (3, 10) at radius 280. Its site (-261, 55) laid the 72 x 72 pad of a 56 x 56 sampler across the ring and
 * cleared the wall there - the dark diagonal smears on the ring's footprint in the shader stills.
 */
class EyesPhotoBoothTest {

    private static final int CAP_X = -216, CAP_Z = 10;
    private static final double RING_X = 3, RING_Z = 10, RING_R = 280;

    private static int[] site(int capX, int capZ, int[] axis) {
        return new int[] { capX + axis[0] * EyesPhotoBooth.SITE_OFFSET, capZ + axis[1] * EyesPhotoBooth.SITE_OFFSET };
    }

    // ------------------------------------------------------------------------------------------------ the site

    @Test
    void theSiteGoesTowardTheArenaCentre() {
        int[] first = EyesPhotoBooth.siteAxes(CAP_X, CAP_Z, RING_X, RING_Z)[0];
        assertEquals(1, first[0], "a capitol west of the arena's centre puts the site east of it");
        int[] s = site(CAP_X, CAP_Z, first);
        assertTrue(Math.hypot(s[0] - RING_X, s[1] - RING_Z) < Math.hypot(CAP_X - RING_X, CAP_Z - RING_Z),
            "the site lies nearer the centre than the capitol");
    }

    @Test
    void theSiteFollowsTheCentreFromEverySide() {
        assertArrayEquals(new int[] { -1, 1 }, EyesPhotoBooth.siteAxes(220, -15, RING_X, RING_Z)[0], "capitol east and north");
        assertArrayEquals(new int[] { 1, -1 }, EyesPhotoBooth.siteAxes(-30, 250, 0, 0)[0], "capitol south");
        assertArrayEquals(new int[] { -1, -1 }, EyesPhotoBooth.siteAxes(180, 170, 0, 0)[0], "capitol south-east");
    }

    @Test
    void theAxesAreTheFourDiagonalsNearestTheCentreFirst() {
        int[][] caps = { { CAP_X, CAP_Z }, { 220, -15 }, { -30, 250 }, { 180, 170 }, { 0, 0 } };
        for (int[] c : caps) {
            int[][] axes = EyesPhotoBooth.siteAxes(c[0], c[1], RING_X, RING_Z);
            assertEquals(4, axes.length);
            Set<String> seen = new HashSet<>();
            double last = -1;
            for (int[] a : axes) {
                assertTrue(Math.abs(a[0]) == 1 && Math.abs(a[1]) == 1, "a diagonal axis");
                assertTrue(seen.add(a[0] + "," + a[1]), "each axis once");
                int[] s = site(c[0], c[1], a);
                double d = Math.hypot(s[0] - RING_X, s[1] - RING_Z);
                assertTrue(d >= last - 1e-9, "nearest the centre first, from " + c[0] + ", " + c[1]);
                last = d;
            }
        }
    }

    @Test
    void thePadIsWhatPlaceStructureLays() {
        // r34: a 56 x 56 x 28 ground sampler "placed at -289, 70, 27 (pad centre -261, 70, 55)", 8 blocks of pad round it
        assertArrayEquals(new int[] { -297, 19, -226, 90 }, EyesPhotoBooth.padBox(-261, 55, 56, 56));
        // the 42 x 22 tree sampler of the same run: "placed at -282, 70, 44"
        assertArrayEquals(new int[] { -290, 36, -233, 73 }, EyesPhotoBooth.padBox(-261, 55, 42, 22));
    }

    @Test
    void ringDistanceInsideOutsideAndAcross() {
        // inside: the radius less the farthest corner (block columns 0..9 span 0..10)
        assertEquals(100 - Math.hypot(10, 10), EyesPhotoBooth.ringDistance(new int[] { 0, 0, 9, 9 }, 0, 0, 100), 1e-9);
        // outside: the nearest point less the radius
        assertEquals(100, EyesPhotoBooth.ringDistance(new int[] { 200, 0, 209, 9 }, 0, 0, 100), 1e-9);
        assertEquals(0, EyesPhotoBooth.ringDistance(new int[] { 95, 0, 104, 0 }, 0, 0, 100), 1e-9);
        // a box round the centre that the ring passes through on every side
        assertEquals(0, EyesPhotoBooth.ringDistance(new int[] { -150, -150, 150, 150 }, 0, 0, 100), 1e-9);
    }

    @Test
    void r34sSiteLaidItsPadAcrossTheWall() {
        int[] pad = EyesPhotoBooth.padBox(-261, 55, 56, 56);
        assertEquals(0, EyesPhotoBooth.ringDistance(pad, RING_X, RING_Z, RING_R), 1e-9);
        assertFalse(EyesPhotoBooth.clearOfRing(-261, 55, RING_X, RING_Z, RING_R));
    }

    @Test
    void theChosenSiteKeepsItsPadClearOfTheWall() {
        int[] axis = EyesPhotoBooth.chooseAxis(CAP_X, CAP_Z, RING_X, RING_Z, RING_R, a -> true);
        assertNotNull(axis);
        int[] s = site(CAP_X, CAP_Z, axis);
        for (int size : new int[] { 22, 48, 56, EyesPhotoBooth.PLANNED_FOOTPRINT }) {
            double gap = EyesPhotoBooth.ringDistance(EyesPhotoBooth.padBox(s[0], s[1], size, size), RING_X, RING_Z, RING_R);
            assertTrue(gap >= EyesPhotoBooth.MIN_RING_CLEARANCE, "a " + size + "-wide pad keeps " + gap + " blocks from the ring");
            // inside the arena, not beyond the wall
            assertTrue(Math.hypot(s[0] - RING_X, s[1] - RING_Z) < RING_R);
        }
    }

    @Test
    void aWetAxisFallsToTheOtherInteriorAxisAndNeverAcrossTheWall() {
        int[] next = EyesPhotoBooth.chooseAxis(CAP_X, CAP_Z, RING_X, RING_Z, RING_R, a -> !(a[0] == 1 && a[1] == 1));
        assertArrayEquals(new int[] { 1, -1 }, next);
        // both interior axes wet: the outer two cross the ring, so none is chosen (the director then logs it)
        assertNull(EyesPhotoBooth.chooseAxis(CAP_X, CAP_Z, RING_X, RING_Z, RING_R, a -> a[0] == -1));
    }

    @Test
    void noWallMeansNoRingCheck() {
        assertArrayEquals(new int[] { -1, 1 }, EyesPhotoBooth.chooseAxis(CAP_X, CAP_Z, RING_X, RING_Z, 0, a -> a[0] == -1));
    }

    // ------------------------------------------------------------------------------------------ the close still

    @Test
    void theCloseCameraStandsFourToSixBlocksFromTheMiddle() {
        for (int[] dir : EyesPhotoBooth.CLOSE_DIRECTIONS) {
            double[] spot = EyesPhotoBooth.closeSpot(-261, 55, dir);
            double d = Math.hypot(spot[0] + 261, spot[1] - 55);
            assertTrue(d >= 4 && d <= 6, "direction " + dir[0] + ", " + dir[1] + ": " + d + " blocks");
        }
        // south-west first, the side the RTS camera and the 3/4 still see
        double[] first = EyesPhotoBooth.closeSpot(0, 0, EyesPhotoBooth.CLOSE_DIRECTIONS[0]);
        assertTrue(first[0] < 0 && first[1] > 0);
    }

    @Test
    void theCloseCameraIsAtEyeHeightAndLooksSlightlyDown() {
        double[] spot = EyesPhotoBooth.closeSpot(-261, 55, EyesPhotoBooth.CLOSE_DIRECTIONS[0]);
        double[] cam = EyesPhotoBooth.closeCamera(spot[0], spot[1], 70, -261, 70, 55);
        assertEquals(70 + EyesPhotoBooth.EYE_HEIGHT, cam[1], 1e-9, "a person's eye over the ground it stands on");
        assertEquals(spot[0], cam[0], 1e-9);
        assertEquals(spot[1], cam[2], 1e-9);
        assertTrue(cam[4] > 5 && cam[4] < 30, "pitch " + cam[4] + " degrees: slightly down");
    }

    @Test
    void theCloseCameraLooksAtTheMiddle() {
        for (int[] dir : EyesPhotoBooth.CLOSE_DIRECTIONS)
            for (double groundAtCamera : new double[] { 68, 70, 72.5 }) {
                double[] spot = EyesPhotoBooth.closeSpot(-261, 55, dir);
                double[] cam = EyesPhotoBooth.closeCamera(spot[0], spot[1], groundAtCamera, -261, 70, 55);
                double[] look = EyesPhotoBooth.lookPoint(cam[0], cam[1], cam[2], cam[3], cam[4], 70);
                // the logged "looks at" must land on the "aimed at" point: eyes_judge allows 4 blocks
                assertEquals(-261, look[0], 0.05, "x, direction " + dir[0] + ", " + dir[1] + ", ground " + groundAtCamera);
                assertEquals(55, look[1], 0.05, "z, direction " + dir[0] + ", " + dir[1] + ", ground " + groundAtCamera);
                assertTrue(cam[4] > 0, "looks down");
            }
    }

    @Test
    void lookPointFollowsMinecraftsYawAndPitch() {
        // yaw 0 looks south (+z), 90 west (-x); pitch 45 down from 10 above the plane: 10 blocks out
        double[] south = EyesPhotoBooth.lookPoint(0, 10, 0, 0, 45, 0);
        assertEquals(0, south[0], 1e-9);
        assertEquals(10, south[1], 1e-9);
        double[] west = EyesPhotoBooth.lookPoint(0, 10, 0, 90, 45, 0);
        assertEquals(-10, west[0], 1e-9);
        assertEquals(0, west[1], 1e-9);
        double[] level = EyesPhotoBooth.lookPoint(3, 10, 4, 30, 0, 0);
        assertEquals(3, level[0], 1e-9);
        assertEquals(4, level[1], 1e-9);
    }

    // ------------------------------------------------------------------------------------------- ambient life

    @Test
    void ambientLifeIsOnUnlessThePhotoBoothSwitchesItOff() {
        try {
            assertFalse(EyesPhotoBooth.ambientLifeOff(), "normal play: never off");
            EyesPhotoBooth.setAmbientLifeOff(true);
            assertTrue(EyesPhotoBooth.ambientLifeOff());
        } finally {
            EyesPhotoBooth.setAmbientLifeOff(false);
        }
        assertFalse(EyesPhotoBooth.ambientLifeOff());
    }

    // ------------------------------------------------------------------------------------------- golden hour

    @Test
    void theSunElevationFollowsMinecraftsDayClock() {
        assertEquals(90, EyesPhotoBooth.sunElevationDeg(6000), 0.5, "noon: overhead");
        assertEquals(-90, EyesPhotoBooth.sunElevationDeg(18000), 0.5, "midnight: straight down");
        assertTrue(EyesPhotoBooth.sunElevationDeg(13000) < 0, "the dusk still (13000): the sun is set");
        assertTrue(EyesPhotoBooth.sunElevationDeg(1000) > 0, "morning: up");
        assertEquals(EyesPhotoBooth.sunElevationDeg(1000), EyesPhotoBooth.sunElevationDeg(25000), 1e-9, "a day wraps");
    }

    @Test
    void theGoldenStillHasALowAfternoonSun() {
        // lovish's villa reference (Oct 28): a low warm sun, long soft shadows, lit walls against dark shade. The RTS
        // camera faces north-east, so it sees the south and west faces: an afternoon sun (in the west) lights them.
        long t = EyesPhotoBooth.GOLDEN_TIME;
        assertTrue(t > 6000 && t < 12000, "afternoon: after noon, before sunset");
        double el = EyesPhotoBooth.sunElevationDeg(t);
        assertTrue(el >= 8 && el <= 20, "low but up: " + el + " degrees");
    }
}
