package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 14 critic: an opaque hammer floated horizontally in mid-air over the roof with no worker under it, and a soft
 * orb sat on a ghost face. The tools now belong to the builders who are actually at work: each worker's nanolathe
 * report (its hands, the block it aims at) keeps a slot alive; the tool sits 0.3 block in front of the hands, at hand
 * height, toward the block - and only while that worker is building.
 */
class ConstructionCrewTest {

    @Test
    void noWorkerNoTool() {
        ConstructionCrew c = new ConstructionCrew();
        float[] out = new float[5];
        for (int i = 0; i < ConstructionCrew.SLOTS; i++)
            assertTrue(!c.anchor(i, 10f, out), "nobody reported: no tool " + i);
    }

    @Test
    void theToolIsAtTheWorkersHandsTowardTheBlock() {
        ConstructionCrew c = new ConstructionCrew();
        c.report(10f, 65.2f, 20f, 13f, 66.5f, 24f, 5f);   // hands at (10, 65.2, 20), aiming 3 east, 4 south
        float[] out = new float[5];
        assertTrue(c.anchor(0, 5.1f, out));
        assertEquals(10f + 0.3f * 0.6f, out[0], 1e-4, "0.3 block in front of the hands");
        assertEquals(65.2f, out[1], 1e-4, "at hand height, never up at the block");
        assertEquals(20f + 0.3f * 0.8f, out[2], 1e-4);
        assertEquals(0.6f, out[3], 1e-4, "the horizontal direction to the block");
        assertEquals(0.8f, out[4], 1e-4);
        // aimed straight down: still a direction, never NaN
        c.report(30f, 70f, 30f, 30f, 60f, 30f, 5f);
        assertTrue(c.anchor(1, 5.1f, out));
        assertTrue(!Float.isNaN(out[0]) && Math.abs(out[3] * out[3] + out[4] * out[4] - 1) < 1e-4);
    }

    @Test
    void theToolGoesWhenTheWorkerStops() {
        ConstructionCrew c = new ConstructionCrew();
        c.report(10f, 65f, 20f, 11f, 65f, 20f, 5f);
        float[] out = new float[5];
        assertTrue(c.anchor(0, 5f + ConstructionCrew.KEEP - 0.05f, out), "between two beams (every 0.4 s) it stays");
        assertTrue(!c.anchor(0, 5f + ConstructionCrew.KEEP + 0.05f, out), "no report for a while: the worker left");
    }

    @Test
    void oneWorkerKeepsOneSlotAndFollowsItsHands() {
        ConstructionCrew c = new ConstructionCrew();
        c.report(10f, 65f, 20f, 11f, 65f, 20f, 5f);
        c.report(10.4f, 65f, 20.3f, 11f, 65f, 21f, 5.4f);   // the same worker, a step on
        float[] out = new float[5];
        assertEquals(1, c.active(5.5f), "the same worker, not a second");
        assertTrue(c.anchor(0, 5.5f, out));
        assertEquals(65f, out[1], 1e-4);
        assertTrue(out[0] > 10.4f && out[2] > 20.3f, "it moved with the hands");
        c.report(14f, 65f, 20f, 13f, 65f, 20f, 5.5f);       // a second worker 3.6 blocks away
        assertEquals(2, c.active(5.6f));
        for (int k = 0; k < 5; k++)                          // more workers than slots: the stalest slot is reused
            c.report(20f + 3 * k, 65f, 20f, 21f + 3 * k, 65f, 20f, 6f + k * 0.01f);
        assertEquals(ConstructionCrew.SLOTS, c.active(6.1f));
        c.clear();
        assertEquals(0, c.active(6.1f));
    }
}
