package com.solegendary.reignofnether.matchstart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Construction round 16 critic: the eyes 06b / 06c camera aimed 60% of the way toward the Commander (a non-worker on the
 * castle edge), none of the five workers was in frame, and the close shot was cut off (4,297 wall px under the top HUD
 * band; the ghost's upper courses and the selection box off screen). Now the building is fitted first - its box plus a
 * block of ghost headroom inside screen x 0.08-0.92, y 0.12-0.88 - and the camera pans toward a worker only if that
 * worker builds THIS site, stands within 12 blocks, and the whole building stays in the safe area.
 */
class ConsFramingTest {

    private static final float YAW = 45, PITCH = 60, ASPECT = 16 / 9f;
    /** A 10 x 8 x 10 site at (100, 64, 200). */
    private static final double[] BOX = { 100, 64, 200, 110, 72, 210 };

    /** Every corner of {@code box} (with the headroom) on screen at (zoom, aim), as {x, y} fractions (y from the top). */
    private static boolean allInSafe(double[] box, float zoom, double[] aim) {
        double[] s = new double[2];
        for (int c = 0; c < 8; c++) {
            double x = (c & 1) == 0 ? box[0] : box[3], y = (c & 2) == 0 ? box[1] : box[4] + ConsFraming.HEADROOM;
            double z = (c & 4) == 0 ? box[2] : box[5];
            ConsFraming.screen(x, y, z, aim, YAW, PITCH, ASPECT, zoom, s);
            if (s[0] < ConsFraming.SAFE_X0 - 1e-6 || s[0] > ConsFraming.SAFE_X1 + 1e-6
                    || s[1] < ConsFraming.SAFE_Y0 - 1e-6 || s[1] > ConsFraming.SAFE_Y1 + 1e-6)
                return false;
        }
        return true;
    }

    @Test
    void theZoomFitsTheWholeBuildingWithHeadroom() {
        float z = ConsFraming.fitZoom(BOX, YAW, PITCH, ASPECT, 16);
        assertTrue(z > 16, "a 10 x 8 x 10 site does not fit 16 blocks of view height with its headroom: " + z);
        double[] aim = new double[3];
        ConsFraming.aim(BOX, YAW, PITCH, ASPECT, z, null, aim);
        assertTrue(allInSafe(BOX, z, aim), "fitted, every corner is inside the safe area");
        assertTrue(!allInSafe(BOX, z * 0.95f, aim), "and the fit is tight (not a blanket zoom-out)");
        // a small site keeps the asked zoom
        double[] hut = { 0, 64, 0, 3, 66, 3 };
        assertEquals(30f, ConsFraming.fitZoom(hut, YAW, PITCH, ASPECT, 30), 1e-4f);
    }

    @Test
    void noWorkerAimsAtTheSitesCentre() {
        double[] aim = new double[3];
        assertFalse(ConsFraming.aim(BOX, YAW, PITCH, ASPECT, 30, null, aim));
        assertEquals(105, aim[0], 1e-6);
        assertEquals(68.5, aim[1], 1e-6, "the box's middle with its block of headroom");
        assertEquals(205, aim[2], 1e-6);
    }

    @Test
    void aWorkerAlreadyInViewMovesNothing() {
        double[] aim = new double[3];
        double[] w = { 104, 64, 199 };
        assertTrue(ConsFraming.aim(BOX, YAW, PITCH, ASPECT, 30, w, aim));
        assertEquals(105, aim[0], 1e-6);
        assertEquals(205, aim[2], 1e-6);
    }

    @Test
    void aWorkerOutsidePullsTheCameraOnlyAsFarAsTheBuildingAllows() {
        float z = ConsFraming.fitZoom(BOX, YAW, PITCH, ASPECT, 16);
        double[] aim = new double[3];
        double[] w = { 113, 64, 197 };   // 3 blocks off the near corner: below the bottom of the tight close-up
        boolean in = ConsFraming.aim(BOX, YAW, PITCH, ASPECT, z, w, aim);
        assertFalse(in, "fitted tight there is no room for the worker below the building");
        assertTrue(allInSafe(BOX, z, aim), "so the whole building wins: it stays in the safe area");
        // at the normal zoom there is room: the worker comes into view, the building still whole
        z = 30;
        assertTrue(ConsFraming.aim(BOX, YAW, PITCH, ASPECT, z, w, aim), "at 30 blocks the worker is in view");
        assertTrue(allInSafe(BOX, z, aim));
        double[] s = new double[2];
        ConsFraming.screen(w[0], w[1], w[2], aim, YAW, PITCH, ASPECT, z, s);
        assertTrue(s[1] <= ConsFraming.SAFE_Y1 + 1e-6, "the worker in the safe area: " + s[1]);
        assertTrue(s[1] > ConsFraming.WORKER_Y1, "brought only as far as the building allows (its top at the safe edge)");
    }

    @Test
    void onlyAWorkerOfThisSiteWithinTwelveBlocks() {
        double[][] ws = {
            { 107, 64, 211 },   // the Commander: standing at the edge, not building this site
            { 120, 64, 205 },   // 10 blocks east of the box: on this site
            { 104, 64, 199 },   // 1 block north of the box: on this site - the nearest
            { 130, 64, 230 },   // on this site, 28 blocks off
        };
        boolean[] onSite = { false, true, true, true };
        assertEquals(2, ConsFraming.pickWorker(BOX, ws, onSite, ws.length));
        onSite[2] = false;
        assertEquals(1, ConsFraming.pickWorker(BOX, ws, onSite, ws.length));
        onSite[1] = false;
        assertEquals(-1, ConsFraming.pickWorker(BOX, ws, onSite, ws.length), "only one 28 blocks off: none");
        assertEquals(1.0, ConsFraming.distance(BOX, 104, 199), 1e-9);
        assertEquals(0.0, ConsFraming.distance(BOX, 105, 205), 1e-9, "inside the footprint");
    }

    /**
     * Construction round 17 critic: no builder in either still - the log had four within 0.6 blocks, but on the FAR side
     * of the site, behind the walls and the ghost (pink smears through it). The crew is first sent to a spot
     * {@code dist} blocks off the footprint on the camera's side ({@link ConsFraming#approach}), and a builder counts
     * as in view only on that side ({@link ConsFraming#nearSide}).
     */
    @Test
    void theCrewIsSentToTheCamerasSideOfTheSite() {
        double[] p = new double[3];
        // yaw 0: the camera looks toward +z, so it stands at -z
        ConsFraming.approach(BOX, 0, PITCH, 2, p);
        assertEquals(105, p[0], 1e-6);
        assertEquals(64, p[1], 1e-6);
        assertEquals(198, p[2], 1e-6);
        assertTrue(ConsFraming.nearSide(BOX, 0, PITCH, 105, 199));
        assertFalse(ConsFraming.nearSide(BOX, 0, PITCH, 105, 211), "behind the site");
        // the tour's yaw: 2 blocks off the footprint, on the camera's side
        ConsFraming.approach(BOX, YAW, PITCH, 2, p);
        assertEquals(2, ConsFraming.distance(BOX, p[0], p[2]), 0.3);
        assertTrue(ConsFraming.nearSide(BOX, YAW, PITCH, p[0], p[2]));
        double[] b = new double[9];
        ConsFraming.basis(YAW, PITCH, b);
        double fx = b[0], fz = b[2];
        assertTrue((p[0] - 105) * fx + (p[2] - 205) * fz < 0, "toward the camera from the centre");
        assertFalse(ConsFraming.nearSide(BOX, YAW, PITCH, 105 + fx * 8, 205 + fz * 8));
    }

    /** Spot {x, z} (a ground cell) on screen at {@code zoom} with the building centred: foot and head both outside the
     *  info panel's rectangle. */
    private static boolean clearOfPanel(int[] c, float zoom) {
        double[] aim = new double[3], s = new double[2];
        ConsFraming.aim(BOX, YAW, PITCH, ASPECT, zoom, null, aim);
        for (double up = 0; up <= ConsFraming.BUILDER_H + 1e-9; up += ConsFraming.BUILDER_H / 2) {
            ConsFraming.screen(c[0] + 0.5, BOX[1] + up, c[1] + 0.5, aim, YAW, PITCH, ASPECT, zoom, s);
            if (ConsFraming.inPanel(s[0], s[1]))
                return false;
        }
        return true;
    }

    /**
     * Construction round 18 critic: "the ~3 workers stand piled on top of each other outside the south corner, a brown
     * blob, no tool, not facing the work, not at the front" (06b), and under the info panel in 06c. The crew is spread
     * round the course: each on its own ground cell just off the footprint (a block out, never a corner), on a face
     * turned to the camera, at least BUILDER_GAP (>= 1.5) apart, the first beside the flashing block (the cell straight
     * out from it), the rest the nearest cells to it that keep the gap - and none whose feet or head fall in the info
     * panel's rectangle (bottom-left, x 455-870, y 705-900 of 1600 x 900) at either zoom of the two stills.
     */
    @Test
    void theBuildersSpreadRoundTheCourseBesideTheFlash() {
        float rts = ConsFraming.fitZoom(BOX, YAW, PITCH, ASPECT, 30), close = ConsFraming.fitZoom(BOX, YAW, PITCH, ASPECT, 18);
        float[] zooms = { rts, close };
        int[][] out = new int[4][2];
        // the camera (yaw 45) stands at +x -z: the +x face (x 110) and the -z face (z 199) are the ones it sees
        int[] flash = { 109, 66, 204 };   // on the +x wall, mid-face
        int n = ConsFraming.builderSpots(BOX, flash, YAW, PITCH, ASPECT, zooms, null, 4, out);
        assertEquals(4, n, "four builders, four spots");
        assertEquals(110, out[0][0], "the first beside the flash: the cell straight out from it");
        assertEquals(204, out[0][1]);
        for (int i = 0; i < n; i++) {
            double cx = out[i][0] + 0.5, cz = out[i][1] + 0.5;
            assertEquals(0.5, ConsFraming.distance(BOX, cx, cz), 1e-9, "a block off the footprint, never a corner: " + i);
            assertTrue(ConsFraming.nearSide(BOX, YAW, PITCH, cx, cz), "on the camera's side: " + i);
            for (float z : zooms)
                assertTrue(clearOfPanel(out[i], z), "out of the info panel at zoom " + z + ": " + i);
            for (int j = 0; j < i; j++) {
                double d = Math.hypot(out[i][0] - out[j][0], out[i][1] - out[j][1]);
                assertTrue(d >= ConsFraming.BUILDER_GAP && ConsFraming.BUILDER_GAP >= 1.5,
                        "never stacked: " + i + " and " + j + " are " + d + " apart");
            }
            assertTrue(Math.hypot(cx - 109.5, cz - 204.5) < 8, "round the work, not across the site: " + i);
        }

        // the flash at the near corner: the cells beside it project into the info panel - the nearest clear one instead
        int[] corner = { 109, 66, 200 };
        n = ConsFraming.builderSpots(BOX, corner, YAW, PITCH, ASPECT, zooms, null, 3, out);
        assertEquals(3, n);
        assertFalse(clearOfPanel(new int[] { 110, 200 }, close) && clearOfPanel(new int[] { 109, 199 }, close),
                "(the test's premise: beside the near corner is under the panel in the close still)");
        for (int i = 0; i < n; i++)
            for (float z : zooms)
                assertTrue(clearOfPanel(out[i], z), "out of the info panel: " + i);

        // a blocked cell (another building, water) is never chosen
        n = ConsFraming.builderSpots(BOX, flash, YAW, PITCH, ASPECT, zooms, (x, z) -> x == 110 && z == 204, 4, out);
        for (int i = 0; i < n; i++)
            assertFalse(out[i][0] == 110 && out[i][1] == 204, "blocked: " + i);
        assertTrue(n >= 3);

        // no flash known yet: still spread, on the camera's side, out of the panel - favouring the higher cells on screen
        n = ConsFraming.builderSpots(BOX, null, YAW, PITCH, ASPECT, zooms, null, 4, out);
        assertEquals(4, n);
        double[] aim = new double[3], s = new double[2], t = new double[2];
        ConsFraming.aim(BOX, YAW, PITCH, ASPECT, rts, null, aim);
        ConsFraming.screen(out[0][0] + 0.5, BOX[1], out[0][1] + 0.5, aim, YAW, PITCH, ASPECT, rts, s);
        ConsFraming.screen(110.5, BOX[1], 199.5, aim, YAW, PITCH, ASPECT, rts, t);
        assertTrue(s[1] < t[1], "the first is higher on screen than the near corner");
    }
}
