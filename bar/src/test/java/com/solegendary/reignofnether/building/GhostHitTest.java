package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blueprint-ghost cursor hit test (lane C bug "you cannot click on blueprint ghosts to assist production"): the
 * ray goes through a ghost to the ground behind it, so the ghost must be found by its box, not the picked block.
 * Ghost used below: corners (10,65,10)..(16,72,16), standing on ground blocks at y=64.
 */
class GhostHitTest {

    private static final int MINX = 10, MINY = 65, MINZ = 10, MAXX = 16, MAXY = 72, MAXZ = 16;

    /** The RTS camera looks down at 45 degrees along +z. */
    private static final double DY = -Math.sqrt(0.5), DZ = Math.sqrt(0.5);

    private static double entry(double ox, double oy, double oz) {
        return GhostHit.rayEntry(ox, oy, oz, 0, DY, DZ, MINX, MINY, MINZ, MAXX, MAXY, MAXZ);
    }

    @Test
    void aRayAimedAtTheGhostsWallEntersItsBoxBeforeTheGroundBehindIt() {
        // aimed at the front wall at y=70, z=10: it passes through the ghost and lands on the ground at z=16+
        double t = entry(13, 80, 0);
        assertTrue(t > 0, "the ray enters the ghost box");
        // ground hit behind it: y=65 (top of the y=64 blocks) is reached at t=(80-65)/sqrt(0.5)
        double groundT = (80 - 65) / Math.sqrt(0.5);
        assertTrue(t < groundT);
        assertTrue(GhostHit.hits(t, groundT, false));
    }

    @Test
    void aRayBesideTheGhostMissesIt() {
        assertEquals(-1, entry(30, 80, 0));
        assertFalse(GhostHit.hits(-1, 100, false));
    }

    @Test
    void aGhostBehindAHillDoesNotStealTheClick() {
        // the ray hit a hill at t=5, the ghost box is entered much later
        double t = entry(13, 80, 0);
        assertFalse(GhostHit.hits(t, 5, false));
    }

    @Test
    void theGroundBlockUnderTheFootprintCounts() {
        // the picked block is the ground one below minCorner (y=64), inside the columns
        assertTrue(GhostHit.footprintHolds(MINX, MINY, MINZ, MAXX, MAXY, MAXZ, 13, 64, 13));
        assertTrue(GhostHit.footprintHolds(MINX, MINY, MINZ, MAXX, MAXY, MAXZ, 10, 63, 16));
        assertTrue(GhostHit.hits(-1, 100, true));
    }

    @Test
    void groundOutsideTheFootprintOrFarBelowDoesNot() {
        assertFalse(GhostHit.footprintHolds(MINX, MINY, MINZ, MAXX, MAXY, MAXZ, 17, 64, 13));
        assertFalse(GhostHit.footprintHolds(MINX, MINY, MINZ, MAXX, MAXY, MAXZ, 13, 64, 9));
        assertFalse(GhostHit.footprintHolds(MINX, MINY, MINZ, MAXX, MAXY, MAXZ, 13, 50, 13));
    }

    @Test
    void aStraightDownRayInsideTheColumnsHitsAndOutsideMisses() {
        assertTrue(GhostHit.rayEntry(12.5, 90, 12.5, 0, -1, 0, MINX, MINY, MINZ, MAXX, MAXY, MAXZ) >= 0);
        assertEquals(-1, GhostHit.rayEntry(9.5, 90, 12.5, 0, -1, 0, MINX, MINY, MINZ, MAXX, MAXY, MAXZ));
    }
}
