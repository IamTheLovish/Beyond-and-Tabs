package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The animal rules measure "near the building" from its floor, not its box centre (#522 review N1): the box centre of
 * a 15 x 36 x 15 Heartwood Hall stands 17 over the ground, so a 10-block sphere round it reached no ground.
 */
class GroundCentreTest {

    // the hall at y64: minCorner (0, 64, 0), maxCorner (14, 99, 14), its box centre as BuildingPlacement computes it
    private static final BlockPos MIN = new BlockPos(0, 64, 0);
    private static final BlockPos CENTRE = new BlockPos(7, (64 + 99) / 2, 7);

    @Test
    void theGroundCentreIsTheCentreColumnAtTheFloor() {
        assertEquals(new BlockPos(7, 64, 7), BuildingPlacement.groundCentre(MIN, CENTRE));
    }

    @Test
    void anAnimalOnTheFootprintIsNearTheGroundCentreButNotTheBoxCentre() {
        // a sheep on the hall's kerb, 5 blocks from the centre column: moveAnimalsAwayFromBuildingFoundations looks
        // within 10 blocks
        BlockPos sheep = new BlockPos(12, 64, 7);
        assertTrue(BuildingPlacement.groundCentre(MIN, CENTRE).getCenter().distanceTo(sheep.getCenter()) <= 10);
        assertFalse(CENTRE.getCenter().distanceTo(sheep.getCenter()) <= 10, "the box centre misses it: the bug");
    }

    @Test
    void aLowBuildingKeepsItsReach() {
        // a 3-high hut: its box centre is one block up, so the ground centre changes its reach by under a block
        BlockPos min = new BlockPos(0, 64, 0);
        BlockPos centre = new BlockPos(2, 65, 2);
        assertEquals(64, BuildingPlacement.groundCentre(min, centre).getY());
        assertEquals(2, BuildingPlacement.groundCentre(min, centre).getX());
    }
}
