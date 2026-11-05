package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * buildingDecor spiked to 13.7 ms (lovish's log, Oct 23): the banner handler worked out a building's mount by walking
 * every block of the placement, for every new building in the same frame. FrameBudget caps that kind of one-off work
 * per frame so it spreads over the next frames instead of landing in one.
 */
class FrameBudgetTest {

    @Test
    void grantsAtMostTheBudgetPerFrame() {
        FrameBudget b = new FrameBudget(2);
        b.newFrame();
        assertTrue(b.tryTake());
        assertTrue(b.tryTake());
        assertFalse(b.tryTake());
        assertFalse(b.tryTake());
    }

    @Test
    void aNewFrameRestoresTheBudget() {
        FrameBudget b = new FrameBudget(1);
        b.newFrame();
        assertTrue(b.tryTake());
        assertFalse(b.tryTake());
        b.newFrame();
        assertTrue(b.tryTake());
    }

    @Test
    void aBacklogDrainsOneBudgetPerFrame() {
        FrameBudget b = new FrameBudget(1);
        int backlog = 40, frames = 0;
        while (backlog > 0 && frames < 1000) {
            b.newFrame();
            while (backlog > 0 && b.tryTake())
                backlog--;
            frames++;
        }
        assertEquals(40, frames, "40 jobs at one a frame take 40 frames, none lands twice in a frame");
    }

    @Test
    void aZeroBudgetGrantsNothing() {
        FrameBudget b = new FrameBudget(0);
        b.newFrame();
        assertFalse(b.tryTake());
    }
}
