package com.solegendary.reignofnether.postfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The canopy test from the meshing region's light (Oct 31, lovish's crash 03:07): {@link CanopyShade#covered}, and the
 * shade it gives against the old leaf test's for a crown.
 */
class CanopyColumnTest {

    private static final int GRASS = 0x91BD59;

    @Test
    void fullSkyIsOpenAnythingOverItIsCover() {
        assertFalse(CanopyShade.covered(15), "open sky");
        assertTrue(CanopyShade.covered(14), "under leaves (one level off)");
        assertTrue(CanopyShade.covered(1), "deep under a roof");
        // #466 follow-up: 0 is the cell over the ground inside a block - a wall or a step beside the grass, not
        // something over it; counting it drew a dark band at the foot of every wall and step on a lawn
        assertFalse(CanopyShade.covered(0), "inside a wall or a step");
    }

    @Test
    void aCrownShadesAsTheLeafTestDid() {
        // a round crown of radius 3 over (0, 0), 5 up: the leaf test saw leaves topping each column under it, the
        // light test sees 14 there and 15 elsewhere - the same taps, so the same colours all the way out
        CanopyShade.Canopy byLeaves = (x, y, z) -> CanopyShade.leavesOver(x * x + z * z <= 9 ? y + 6 : 0, x * x + z * z <= 9, y);
        CanopyShade.Canopy byLight = (x, y, z) -> CanopyShade.covered(x * x + z * z <= 9 ? 14 : 15);
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++)
                assertEquals(CanopyShade.apply(GRASS, byLeaves, x, 64, z), CanopyShade.apply(GRASS, byLight, x, 64, z),
                        "at " + x + ", " + z);
    }
}
