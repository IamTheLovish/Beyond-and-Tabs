package com.solegendary.reignofnether.postfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The canopy shade as {@link CanopyRegion} reads it: one sky-light read per tap, at the cell over the ground (#466
 * follow-up). A wall or a step beside the grass puts that cell inside a block (sky light 0): not cover, so no dark band
 * at the foot of every wall and step on a lawn. A read that throws leaves plain grass and is said once, not per sample.
 */
class CanopyRegionTest {

    private static final int GRASS = 0x91BD59;
    private static final int Y = 64;

    @Test
    void aWallBesideTheGrassCastsNoBandAtItsFoot() {
        // a wall from x = 1 east, open sky elsewhere: the cells at y + 1 in the wall's columns are inside it
        CanopyRegion.SkyLight wall = (x, y, z) -> x >= 1 && y >= Y + 1 ? 0 : 15;
        for (int x = -3; x <= 0; x++)
            assertEquals(GRASS, CanopyRegion.shade(wall, x, Y, 0, GRASS), "grass at x = " + x + " beside the wall");
    }

    @Test
    void aStepUpBesideTheGrassCastsNoBandAtItsFoot() {
        // the ground one block higher from x = 1 east: the cell at y + 1 there is the step's grass block
        CanopyRegion.SkyLight step = (x, y, z) -> x >= 1 && y <= Y + 1 ? 0 : 15;
        assertEquals(GRASS, CanopyRegion.shade(step, 0, Y, 0, GRASS), "grass at the foot of the step");
    }

    @Test
    void aCrownStillShadesTheGrassUnderIt() {
        // leaves take one level off the sky light under them
        CanopyRegion.SkyLight crown = (x, y, z) -> x * x + z * z <= 9 ? 14 : 15;
        int under = CanopyRegion.shade(crown, 0, Y, 0, GRASS);
        assertTrue(((under >> 8) & 0xFF) < ((GRASS >> 8) & 0xFF), "under the crown is darker: " + Integer.toHexString(under));
        assertEquals(GRASS, CanopyRegion.shade(crown, 8, Y, 0, GRASS), "open meadow beyond the crown");
    }

    @Test
    void aFailedReadLeavesPlainGrassAndIsSaidOnce() {
        CanopyRegion.SkyLight broken = (x, y, z) -> {
            throw new IllegalStateException("a region torn down under the meshing task");
        };
        assertEquals(GRASS, CanopyRegion.shade(broken, 0, Y, 0, GRASS));
        assertEquals(GRASS, CanopyRegion.shade(broken, 1, Y, 0, GRASS));
        assertEquals(1, CanopyRegion.warnings(), "a failed read is logged once a session, not per colour sample");
    }
}
