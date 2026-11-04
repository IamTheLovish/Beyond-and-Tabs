package com.solegendary.reignofnether.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** N5 (BAC-019, HANDOFF-lane-c C2): huge particle bursts no longer cover whole groups of units or drop frames. */
class ParticleBudgetTest {

    @Test
    void theCapIsStated() {
        assertEquals(16, ParticleBudget.CELL_BLOCKS);
        assertEquals(48, ParticleBudget.PER_CELL_PER_TICK);
        assertEquals(400, ParticleBudget.PER_TICK);
    }

    @Test
    void oneSmallBurstIsUntouched() {
        ParticleBudget b = new ParticleBudget();
        assertEquals(20, b.grant(100, 5, 5, 20));
    }

    @Test
    void burstsInOneCellShareThePerCellCapEachTick() {
        ParticleBudget b = new ParticleBudget();
        assertEquals(40, b.grant(100, 1, 1, 40));
        assertEquals(8, b.grant(100, 15, 15, 40), "same 16-block cell, same tick");
        assertEquals(0, b.grant(100, 3, 9, 10), "the cell is spent this tick");
        assertEquals(40, b.grant(101, 3, 9, 40), "a new tick refills it");
    }

    @Test
    void negativeCoordinatesFallInTheirOwnCell() {
        ParticleBudget b = new ParticleBudget();
        assertEquals(48, b.grant(7, -1, -1, 100));
        assertEquals(48, b.grant(7, 0, 0, 100), "x=-1 and x=0 are different cells");
    }

    @Test
    void theWholeTickHasACeilingAcrossCells() {
        ParticleBudget b = new ParticleBudget();
        int total = 0;
        for (int i = 0; i < 20; i++)
            total += b.grant(50, i * 16 + 1, 0, 48);
        assertEquals(400, total);
    }

    @Test
    void zeroOrNegativeWantsNothing() {
        ParticleBudget b = new ParticleBudget();
        assertEquals(0, b.grant(1, 0, 0, 0));
        assertEquals(0, b.grant(1, 0, 0, -5));
    }
}
