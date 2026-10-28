package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exit arrow's pure math (tester request 2026-10-26): which way it points and its outline, for each of a
 * building's four rotations. Footprint used: a 7 x 5 lab whose min corner is (10, 20) unrotated; rotated 90/270 it
 * is 5 x 7. Its default outdoor spawn point (ProductionBuilding) is the min corner minus (1, 1).
 */
class ExitArrowTest {

    private static final double EPS = 1e-4;

    /** {minX, minZ, maxX, maxZ} of the footprint at rotation r (0..3, quarter turns) about its min corner. */
    private static int[] footprint(int r) {
        boolean wide = r % 2 == 0;
        return new int[] { 10, 20, 10 + (wide ? 6 : 4), 20 + (wide ? 4 : 6) };
    }

    private static double cx(int[] f) { return (f[0] + f[2] + 1) / 2.0; }
    private static double cz(int[] f) { return (f[1] + f[3] + 1) / 2.0; }

    @Test
    void anIndoorSpawnPointsFromTheCentreTowardsTheDefaultRallyAtEveryRotation() {
        float[] d = new float[2];
        for (int r = 0; r < 4; r++) {
            int[] f = footprint(r);
            double sx = cx(f), sz = cz(f);                      // indoor spawn: the centre
            double tx = f[0] - 1 + 0.5, tz = f[1] - 1 + 0.5;    // default rally: min corner - (1, 1)
            ExitArrow.walkDir(sx, sz, tx, tz, cx(f), cz(f), d);
            double len = Math.hypot(tx - sx, tz - sz);
            assertEquals((tx - sx) / len, d[0], EPS, "rotation " + r + " x");
            assertEquals((tz - sz) / len, d[1], EPS, "rotation " + r + " z");
            assertTrue(d[0] < 0 && d[1] < 0, "rotation " + r + ": towards the min corner");
        }
    }

    @Test
    void anOutdoorSpawnStandsOnItsRallySoItPointsAwayFromTheBuilding() {
        float[] d = new float[2];
        for (int r = 0; r < 4; r++) {
            int[] f = footprint(r);
            double sx = f[0] - 1 + 0.5, sz = f[1] - 1 + 0.5;    // outdoor spawn = default rally
            ExitArrow.walkDir(sx, sz, sx, sz, cx(f), cz(f), d);
            double len = Math.hypot(sx - cx(f), sz - cz(f));
            assertEquals((sx - cx(f)) / len, d[0], EPS, "rotation " + r + " x");
            assertEquals((sz - cz(f)) / len, d[1], EPS, "rotation " + r + " z");
            assertEquals(1.0, Math.hypot(d[0], d[1]), EPS);
        }
    }

    @Test
    void aSpawnOnTheCentreWithNowhereToWalkPointsTowardsTheDefaultSpawnSide() {
        float[] d = new float[2];
        ExitArrow.walkDir(5, 5, 5, 5, 5, 5, d);
        assertEquals(-Math.sqrt(0.5), d[0], EPS);
        assertEquals(-Math.sqrt(0.5), d[1], EPS);
    }

    @Test
    void theOutlineSitsOnTheSpawnSpotAndItsTipLeadsTheWayForEveryQuarterTurn() {
        float[][] dirs = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };
        float[] p = new float[14];
        for (float[] dir : dirs) {
            ExitArrow.outline(100.5f, 50.5f, dir[0], dir[1], 0f, p);
            // the tip is LENGTH - TAIL ahead of the spot, on the centre line
            assertEquals(100.5f + dir[0] * (ExitArrow.LENGTH - ExitArrow.TAIL), p[6], EPS);
            assertEquals(50.5f + dir[1] * (ExitArrow.LENGTH - ExitArrow.TAIL), p[7], EPS);
            // the tail is TAIL behind it: the spot itself is on the shaft
            double tailMidX = (p[0] + p[12]) / 2, tailMidZ = (p[1] + p[13]) / 2;
            assertEquals(100.5f - dir[0] * ExitArrow.TAIL, tailMidX, EPS);
            assertEquals(50.5f - dir[1] * ExitArrow.TAIL, tailMidZ, EPS);
            // a few blocks long, head wider than the shaft
            assertEquals(ExitArrow.LENGTH, Math.hypot(p[6] - tailMidX, p[7] - tailMidZ), EPS);
            assertTrue(Math.hypot(p[4] - p[8], p[5] - p[9]) > Math.hypot(p[2] - p[10], p[3] - p[11]));
        }
    }

    /**
     * When the client asks the server for exit-arrow cells (ExitArrow.shouldAsk): never with nothing to show, at most
     * once a second while nothing changed, at most 4 a second (every 5 ticks) when something new needs an answer.
     * Args: anythingShown, somethingNew, ticksSinceLastAsk.
     */
    @Test
    void theClientAsksOncePerSecondAtRestAndBurstsAtMostFourPerSecondWhenSomethingIsNew() {
        assertFalse(ExitArrow.shouldAsk(false, false, 1000));
        assertFalse(ExitArrow.shouldAsk(false, true, 1000));
        assertFalse(ExitArrow.shouldAsk(true, false, 19));
        assertTrue(ExitArrow.shouldAsk(true, false, 20));
        assertFalse(ExitArrow.shouldAsk(true, true, 4));
        assertTrue(ExitArrow.shouldAsk(true, true, 5));
        assertTrue(ExitArrow.shouldAsk(true, true, 20));
    }

    @Test
    void theDarkEdgeIsLargerOnEverySide() {
        float[] in = new float[14], out = new float[14];
        ExitArrow.outline(0, 0, 1, 0, 0f, in);
        ExitArrow.outline(0, 0, 1, 0, 0.12f, out);
        assertTrue(out[0] < in[0], "longer at the tail");
        assertTrue(out[6] > in[6], "longer at the tip");
        assertTrue(Math.abs(out[1]) > Math.abs(in[1]), "wider shaft");
        assertTrue(Math.abs(out[5]) > Math.abs(in[5]), "wider head");
    }
}
