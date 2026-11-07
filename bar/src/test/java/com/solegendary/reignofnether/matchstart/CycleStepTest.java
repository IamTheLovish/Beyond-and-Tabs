package com.solegendary.reignofnether.matchstart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tester, Oct 26: cycling buttons go forward on left click and backward on right click, wrapping both ways; and the
 * skirmish settings column never runs under Start Battle at a large GUI scale.
 */
class CycleStepTest {

    @Test
    void forwardWraps() {
        assertEquals(1, CycleStep.step(0, 4, false));
        assertEquals(0, CycleStep.step(3, 4, false));
    }

    @Test
    void backWraps() {
        assertEquals(2, CycleStep.step(3, 4, true));
        assertEquals(3, CycleStep.step(0, 4, true));
    }

    @Test
    void forwardThenBackReturns() {
        for (int n = 1; n <= 8; n++)
            for (int i = 0; i < n; i++)
                assertEquals(i, CycleStep.step(CycleStep.step(i, n, false), n, true));
    }

    @Test
    void emptyListStaysAtZero() {
        assertEquals(0, CycleStep.step(0, 0, true));
    }

    @Test
    void settingsColumnNeverReachesStartBattle() {
        for (int avail = 7 * 16; avail <= 400; avail++) {
            int[] c = LobbyLayout.settingsColumn(avail, 7);
            assertTrue(c[0] + 7 * c[1] <= avail, "avail " + avail + ": preview " + c[0] + " + rows " + c[1]);
            assertTrue(c[1] >= 16 && c[1] <= 22 && c[0] >= 0 && c[0] <= 92);
        }
        int[] roomy = LobbyLayout.settingsColumn(300, 7);
        assertEquals(22, roomy[1], "rows keep 22 px while there is room");
    }
}
